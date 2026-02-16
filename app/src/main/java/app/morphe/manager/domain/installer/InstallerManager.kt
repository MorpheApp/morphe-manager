package app.morphe.manager.domain.installer

import android.Manifest
import android.app.Application
import android.content.ClipData
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.StringRes
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.domain.manager.InstallerPreferenceTokens
import app.morphe.manager.R
import java.io.File
import java.io.IOException
import java.util.LinkedHashMap
import java.util.Locale
import java.util.UUID

class InstallerManager(
    private val app: Application,
    private val prefs: PreferencesManager,
    private val rootInstaller: RootInstaller,
    private val shizukuInstaller: ShizukuInstaller
) {
    private val packageManager: PackageManager = app.packageManager
    private val authority = InstallerFileProvider.authority(app)
    private val shareDir: File = File(app.cacheDir, SHARE_DIR).apply { mkdirs() }
    private val dummyUri: Uri = InstallerFileProvider.buildUri(app, "dummy.apk")
    private val defaultInstallerComponent: ComponentName? by lazy { resolveDefaultInstallerComponent() }
    private val defaultInstallerPackage: String? get() = defaultInstallerComponent?.packageName
    private val hiddenInstallerPackages: Set<String>
        get() = prefs.installerHiddenComponents.getBlocking()
            .mapNotNull(ComponentName::unflattenFromString)
            .map { it.packageName }
            .toSet()

    fun listEntries(target: InstallTarget, includeNone: Boolean): List<Entry> {
        val hiddenPackages = hiddenInstallerPackages
        val entries = mutableListOf<Entry>()

        entryFor(Token.Internal, target, checkRoot = false)?.let(entries::add)
        entryFor(Token.AutoSaved, target, checkRoot = false)?.let(entries::add)
        entryFor(Token.Shizuku, target, checkRoot = false)?.let(entries::add)

        val activityEntries = queryInstallerActivities()
            .filter(::isInstallerCandidate)
            .distinctBy { it.activityInfo.packageName }
            .mapNotNull { info ->
                val component = ComponentName(info.activityInfo.packageName, info.activityInfo.name)
                if (isDefaultComponent(component)) return@mapNotNull null
                if (component.packageName in hiddenPackages) return@mapNotNull null
                if (isExcludedDuplicate(component.packageName, info.loadLabel(packageManager).toString())) {
                    return@mapNotNull null
                }
                entryFor(Token.Component(component), target, checkRoot = false)
            }
            .sortedBy { it.label.lowercase() }

        entries += activityEntries

        val customEntries = readCustomInstallerTokens()
            .mapNotNull { token ->
                entryFor(token, target, checkRoot = false)
            }
            .filterNot { entry ->
                val componentToken = entry.token as? Token.Component ?: return@filterNot false
                componentToken.componentName.packageName in hiddenPackages
            }
            .filterNot { customEntry ->
                entries.any { tokensEqual(it.token, customEntry.token) }
            }
            .sortedBy { it.label.lowercase() }

        entries += customEntries

        if (includeNone) {
            entryFor(Token.None, target, checkRoot = false)?.let(entries::add)
        }

        return entries
    }

    fun describeEntry(token: Token, target: InstallTarget): Entry? = entryFor(token, target)

    fun parseToken(value: String?): Token {
        val token = when (value) {
            InstallerPreferenceTokens.AUTO_SAVED,
            InstallerPreferenceTokens.ROOT -> Token.AutoSaved
            InstallerPreferenceTokens.SYSTEM -> Token.Internal
            InstallerPreferenceTokens.NONE -> Token.None
            InstallerPreferenceTokens.SHIZUKU -> Token.Shizuku
            InstallerPreferenceTokens.INTERNAL, null, "" -> Token.Internal
            else -> ComponentName.unflattenFromString(value)?.let { component ->
                if (isDefaultComponent(component)) Token.Internal else Token.Component(component)
            } ?: Token.Internal
        }
        Log.d(TAG, "parseToken($value) -> ${token.describe()}")
        return token
    }

    fun tokenToPreference(token: Token): String = when (token) {
        Token.Internal -> InstallerPreferenceTokens.INTERNAL
        Token.AutoSaved -> InstallerPreferenceTokens.AUTO_SAVED
        Token.None -> InstallerPreferenceTokens.NONE
        Token.Shizuku -> InstallerPreferenceTokens.SHIZUKU
        is Token.Component -> token.componentName.flattenToString()
    }

    fun getPrimaryToken(): Token = parseToken(prefs.installerPrimary.getBlocking())

    suspend fun updatePrimaryToken(token: Token) {
        Log.d(TAG, "updatePrimaryToken -> ${token.describe()}")
        prefs.installerPrimary.update(tokenToPreference(token))
    }

    fun storedCustomInstallerTokens(): List<Token.Component> = readCustomInstallerTokens()

    suspend fun addCustomInstaller(component: ComponentName): Boolean {
        val flattened = component.flattenToString()
        var added = false
        prefs.edit {
            val current = prefs.installerCustomComponents.value
            if (flattened !in current) {
                prefs.installerCustomComponents.value = current + flattened
                added = true
            }
        }
        return added
    }

    suspend fun removeCustomInstaller(component: ComponentName): Boolean {
        val flattened = component.flattenToString()
        var removed = false
        prefs.edit {
            val current = prefs.installerCustomComponents.value
            if (flattened in current) {
                prefs.installerCustomComponents.value = current - flattened
                removed = true
            }
        }
        return removed
    }

    fun suggestInstallerPackages(
        query: String,
        limit: Int = DEFAULT_PACKAGE_SUGGESTION_LIMIT
    ): List<PackageSuggestion> {
        val normalized = query.trim()
        if (normalized.isEmpty()) return emptyList()
        val lower = normalized.lowercase()
        val packages = runCatching {
            getInstalledPackagesCompat(PackageManager.GET_ACTIVITIES)
        }.getOrElse { error ->
            Log.w(TAG, "Failed to query installed packages for suggestions", error)
            return emptyList()
        }

        val results = mutableListOf<PackageSuggestion>()
        packages.forEach { info ->
            val packageName = info.packageName
            val applicationInfo = info.applicationInfo
            val label = applicationInfo?.loadLabel(packageManager)?.toString().orEmpty()
            val matches = packageName.contains(lower, ignoreCase = true) ||
                    label.contains(lower, ignoreCase = true)
            if (!matches) return@forEach

            val activities = info.activities?.asSequence() ?: emptySequence()
            val hasInstallerActivity = activities
                .map { ComponentName(packageName, it.name) }
                .any { isComponentAvailable(it) }

            if (!hasInstallerActivity) return@forEach

            results += PackageSuggestion(
                packageName = packageName,
                label = label.takeIf { it.isNotBlank() && it != packageName }
            )
            if (results.size >= limit) return@forEach
        }

        return results.sortedBy { it.packageName.lowercase() }.take(limit)
    }

    fun findInstallerEntriesForPackage(
        packageName: String,
        target: InstallTarget
    ): List<Entry> {
        val normalized = packageName.trim()
        if (normalized.isEmpty()) return emptyList()

        val resolveInfos = queryInstallerActivities()
            .filter { it.activityInfo.packageName.equals(normalized, ignoreCase = true) }

        val entries = resolveInfos
            .mapNotNull { info ->
                val component = ComponentName(info.activityInfo.packageName, info.activityInfo.name)
                entryFor(Token.Component(component), target, checkRoot = false)
            }
            .sortedBy { it.label.lowercase() }

        if (entries.isNotEmpty()) return entries

        return readCustomInstallerTokens()
            .filter { it.componentName.packageName.equals(normalized, ignoreCase = true) }
            .mapNotNull { entryFor(it, target, checkRoot = false) }
    }

    fun searchInstallerEntries(
        query: String,
        target: InstallTarget
    ): List<Entry> {
        val normalized = query.trim()
        val results = LinkedHashMap<ComponentName, Entry>()

        fun add(entry: Entry) {
            val component = (entry.token as? Token.Component)?.componentName ?: return
            results.putIfAbsent(component, entry)
        }

        val customTokens = readCustomInstallerTokens()
        if (normalized.isEmpty()) {
            customTokens.forEach { token ->
                entryFor(token, target, checkRoot = false)?.let(::add)
            }
            return results.values.sortedBy { it.label.lowercase() }
        }

        val lower = normalized.lowercase()

        customTokens.forEach { token ->
            val entry = entryFor(token, target, checkRoot = false) ?: return@forEach
            val packageMatch = token.componentName.packageName.contains(lower, ignoreCase = true)
            val classMatch = token.componentName.className.contains(lower, ignoreCase = true)
            val labelMatch = entry.label.contains(lower, ignoreCase = true)
            if (packageMatch || classMatch || labelMatch) add(entry)
        }

        queryInstallerActivities()
            .filter(::isInstallerCandidate)
            .forEach { info ->
                val component = ComponentName(info.activityInfo.packageName, info.activityInfo.name)
                val entry = entryFor(Token.Component(component), target, checkRoot = false) ?: return@forEach
                val label = entry.label.lowercase()
                val description = entry.description?.lowercase().orEmpty()
                val packageMatch = component.packageName.contains(lower, ignoreCase = true)
                val classMatch = component.className.contains(lower, ignoreCase = true)
                val labelMatch = label.contains(lower)
                val descriptionMatch = description.contains(lower)
                if (packageMatch || classMatch || labelMatch || descriptionMatch) {
                    add(entry)
                }
            }

        suggestInstallerPackages(normalized, SEARCH_PACKAGE_SUGGESTION_LIMIT).forEach { suggestion ->
            findInstallerEntriesForPackage(suggestion.packageName, target).forEach(::add)
        }

        findInstallerEntriesForPackage(normalized, target).forEach(::add)

        return results.values.sortedBy { it.label.lowercase() }
    }

    /**
     * Resolves the installation plan based on user's preferred installer.
     * Returns a [ResolvedPlan] which includes the plan and information about
     * whether the primary installer was unavailable.
     */
    fun resolvePlanWithStatus(
        target: InstallTarget,
        sourceFile: File,
        expectedPackage: String,
        sourceLabel: String?
    ): ResolvedPlan {
        val primaryToken = getPrimaryToken()
        val primaryAvailability = availabilityFor(primaryToken, target, checkRoot = true)

        // If primary is available, use it
        if (primaryAvailability.available) {
            val plan = createPlan(primaryToken, target, sourceFile, expectedPackage, sourceLabel)
            if (plan != null) {
                return ResolvedPlan(
                    plan = plan,
                    primaryUnavailable = false,
                    primaryToken = primaryToken,
                    unavailabilityReason = null
                )
            }
        }

        // Primary is unavailable - check if it's Shizuku or AutoSaved (special cases)
        val isSpecialInstaller = primaryToken == Token.Shizuku || primaryToken == Token.AutoSaved

        if (isSpecialInstaller) {
            // Return info about unavailability so UI can show appropriate dialog
            return ResolvedPlan(
                plan = InstallPlan.Internal(target), // Fallback
                primaryUnavailable = true,
                primaryToken = primaryToken,
                unavailabilityReason = primaryAvailability.reason
            )
        }

        // For other installers, try fallback sequence
        val sequence = buildSequence(target)
        sequence.forEach { token ->
            createPlan(token, target, sourceFile, expectedPackage, sourceLabel)?.let { plan ->
                return ResolvedPlan(
                    plan = plan,
                    primaryUnavailable = primaryToken != token,
                    primaryToken = primaryToken,
                    unavailabilityReason = if (primaryToken != token) primaryAvailability.reason else null
                )
            }
        }

        // Fallback to internal install
        return ResolvedPlan(
            plan = InstallPlan.Internal(target),
            primaryUnavailable = primaryToken != Token.Internal,
            primaryToken = primaryToken,
            unavailabilityReason = primaryAvailability.reason
        )
    }

    /**
     * Original resolvePlan method for backward compatibility.
     * Use [resolvePlanWithStatus] for more detailed information.
     */
    fun resolvePlan(
        target: InstallTarget,
        sourceFile: File,
        expectedPackage: String,
        sourceLabel: String?
    ): InstallPlan {
        return resolvePlanWithStatus(target, sourceFile, expectedPackage, sourceLabel).plan
    }

    /**
     * Get current availability status for Shizuku installer.
     */
    fun getShizukuAvailability(target: InstallTarget): Availability {
        return availabilityFor(Token.Shizuku, target, checkRoot = true)
    }

    /**
     * Get current availability status for root/mount installer.
     */
    fun getRootAvailability(target: InstallTarget): Availability {
        return availabilityFor(Token.AutoSaved, target, checkRoot = true)
    }

    fun cleanup(plan: InstallPlan.External) {
        runCatching {
            app.revokeUriPermission(plan.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun readCustomInstallerTokens(): List<Token.Component> =
        prefs.installerCustomComponents.getBlocking()
            .mapNotNull { ComponentName.unflattenFromString(it) }
            .distinct()
            .map { Token.Component(it) }

    private fun createPlan(
        token: Token,
        target: InstallTarget,
        sourceFile: File,
        expectedPackage: String,
        sourceLabel: String?
    ): InstallPlan? {
        return when (token) {
            Token.Internal -> InstallPlan.Internal(target)
            Token.None -> null
            Token.AutoSaved -> if (availabilityFor(Token.AutoSaved, target, checkRoot = true).available) {
                InstallPlan.Mount(target)
            } else null

            Token.Shizuku -> if (availabilityFor(Token.Shizuku, target, checkRoot = true).available) {
                InstallPlan.Shizuku(target)
            } else null

            is Token.Component -> {
                if (!availabilityFor(token, target).available) {
                    null
                } else {
                    val shared = copyToShareDir(sourceFile)
                    val uri = InstallerFileProvider.buildUri(app, shared)
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, APK_MIME)
                        addFlags(
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                                    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION or
                                    Intent.FLAG_ACTIVITY_NEW_TASK
                        )
                        clipData = ClipData.newRawUri("APK", uri)
                        putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                        putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, app.packageName)
                        component = token.componentName
                    }
                    app.grantUriPermission(
                        token.componentName.packageName,
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                                Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
                    )
                    InstallPlan.External(
                        target = target,
                        intent = intent,
                        sharedFile = shared,
                        uri = uri,
                        expectedPackage = expectedPackage,
                        installerLabel = resolveLabel(token.componentName),
                        sourceLabel = sourceLabel,
                        token = token
                    )
                }
            }
        }
    }

    private fun tokensEqual(a: Token, b: Token): Boolean = when {
        a === b -> true
        a is Token.Component && b is Token.Component -> a.componentName == b.componentName
        else -> false
    }

    @Suppress("DEPRECATION")
    private fun getInstalledPackagesCompat(flags: Int): List<PackageInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            packageManager.getInstalledPackages(flags)
        }

    private fun resolveLabel(componentName: ComponentName): String =
        runCatching {
            val activityInfo: ActivityInfo = packageManager.getActivityInfo(componentName, 0)
            activityInfo.loadLabel(packageManager).toString()
        }.getOrDefault(componentName.packageName)

    private fun entryFor(token: Token, target: InstallTarget, checkRoot: Boolean = true): Entry? = when (token) {
        Token.Internal -> Entry(
            token = Token.Internal,
            label = app.getString(R.string.installer_internal_name),
            description = app.getString(R.string.installer_internal_description),
            availability = Availability(true),
            icon = loadInstallerIcon(defaultInstallerPackage)
        )

        Token.None -> Entry(
            token = Token.None,
            label = app.getString(R.string.installer_option_none),
            description = app.getString(R.string.installer_none_description),
            availability = Availability(true),
            icon = null
        )

        Token.AutoSaved -> Entry(
            token = Token.AutoSaved,
            label = app.getString(R.string.installer_auto_saved_name),
            description = app.getString(R.string.installer_auto_saved_description),
            availability = availabilityFor(Token.AutoSaved, target, checkRoot),
            icon = null
        )

        Token.Shizuku -> Entry(
            token = Token.Shizuku,
            label = app.getString(R.string.installer_shizuku_name),
            description = app.getString(R.string.installer_shizuku_description),
            availability = availabilityFor(Token.Shizuku, target, checkRoot),
            icon = if (shizukuInstaller.isInstalled()) loadInstallerIcon(ShizukuInstaller.PACKAGE_NAME) else null
        )

        is Token.Component -> {
            val availability = availabilityFor(token, target, checkRoot)
            Entry(
                token = token,
                label = resolveLabel(token.componentName),
                description = token.componentName.packageName,
                availability = availability,
                icon = loadInstallerIcon(token.componentName)
            )
        }
    }

    private fun copyToShareDir(source: File): File {
        val target = File(shareDir, "${UUID.randomUUID()}.apk")
        try {
            source.inputStream().use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } catch (error: IOException) {
            target.delete()
            throw error
        }
        return target
    }

    private fun buildSequence(target: InstallTarget): List<Token> {
        val tokens = mutableListOf<Token>()
        val primary = getPrimaryToken()

        fun add(token: Token) {
            if (token == Token.None) return
            if (token in tokens) return
            // Use checkRoot = true here to ensure we only add available installers
            if (!availabilityFor(token, target, checkRoot = true).available) return
            tokens += token
        }

        add(primary)

        if (Token.Internal !in tokens) add(Token.Internal)

        return tokens
    }

    private fun availabilityFor(token: Token, target: InstallTarget, checkRoot: Boolean = true): Availability = when (token) {
        Token.Internal -> Availability(true)
        Token.None -> Availability(true)

        Token.AutoSaved -> if (!target.supportsRoot) {
            Availability(false, R.string.installer_status_not_supported)
        } else if (checkRoot) {
            // Expert mode: check root access
            if (!rootInstaller.hasRootAccess()) {
                Availability(false, R.string.installer_status_requires_root)
            } else {
                Availability(true)
            }
        } else {
            // Morphe mode: check if device is rooted without requesting access
            // This prevents showing root installer on non-rooted devices
            if (!rootInstaller.isDeviceRooted()) {
                Availability(false, R.string.installer_status_requires_root)
            } else {
                // Device is rooted, but don't verify access yet (avoid prompt)
                Availability(true)
            }
        }

        Token.Shizuku -> {
            if (!shizukuInstaller.isInstalled()) {
                Availability(false, R.string.installer_status_shizuku_not_installed)
            } else if (checkRoot) {
                // Full availability check
                shizukuInstaller.availability(target)
            } else {
                // Just verify Shizuku is installed (for UI display)
                Availability(true)
            }
        }

        is Token.Component -> {
            if (isComponentAvailable(token.componentName)) Availability(true)
            else Availability(false, R.string.installer_status_not_supported)
        }
    }

    fun isComponentAvailable(componentName: ComponentName): Boolean {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(dummyUri, APK_MIME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            component = componentName
        }
        return intent.resolveActivity(packageManager) != null
    }

    private fun queryInstallerActivities() =
        packageManager.queryIntentActivities(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(dummyUri, APK_MIME)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            PackageManager.MATCH_DEFAULT_ONLY
        )

    private fun resolveDefaultInstallerComponent(): ComponentName? {
        fun isSystemApp(packageName: String): Boolean {
            val info = runCatching { packageManager.getApplicationInfo(packageName, 0) }.getOrNull()
                ?: return false
            val flags = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
            return info.flags and flags != 0
        }

        val candidates = queryInstallerActivities()
            .filter(::isInstallerCandidate)
            .filter { isSystemApp(it.activityInfo.packageName) }

        if (candidates.isEmpty()) return null

        val preferredPackages = listOf(
            "com.google.android.packageinstaller",
            "com.android.packageinstaller"
        )

        val chosen = preferredPackages.firstNotNullOfOrNull { pkg ->
            candidates.firstOrNull { it.activityInfo.packageName == pkg }
        } ?: candidates.firstOrNull { info ->
            info.loadLabel(packageManager).toString()
                .equals(AOSP_INSTALLER_LABEL, ignoreCase = true)
        } ?: candidates.first()

        val activityInfo = chosen.activityInfo
        return ComponentName(activityInfo.packageName, activityInfo.name)
    }

    private fun isDefaultComponent(componentName: ComponentName): Boolean =
        defaultInstallerPackage == componentName.packageName

    private fun loadInstallerIcon(componentName: ComponentName): Drawable? =
        loadInstallerIcon(componentName.packageName)

    private fun loadInstallerIcon(packageName: String?): Drawable? =
        packageName?.let { runCatching { packageManager.getApplicationIcon(it) }.getOrNull() }

    private fun isExcludedDuplicate(packageName: String, label: String): Boolean =
        packageName == AOSP_INSTALLER_PACKAGE &&
                label.equals(AOSP_INSTALLER_LABEL, ignoreCase = true)

    private fun isInstallerCandidate(info: ResolveInfo): Boolean {
        if (!info.activityInfo.exported) return false
        val requestedPermissions = runCatching {
            packageManager.getPackageInfo(
                info.activityInfo.packageName,
                PackageManager.GET_PERMISSIONS
            ).requestedPermissions
        }.getOrNull() ?: return false

        return requestedPermissions.any {
            it == Manifest.permission.REQUEST_INSTALL_PACKAGES ||
                    it == Manifest.permission.INSTALL_PACKAGES
        }
    }

    data class Entry(
        val token: Token,
        val label: String,
        val description: String?,
        val availability: Availability,
        val icon: Drawable?
    )

    data class PackageSuggestion(
        val packageName: String,
        val label: String?
    )

    data class Availability(
        val available: Boolean,
        @StringRes val reason: Int? = null
    )

    /**
     * Result of [resolvePlanWithStatus] that includes information about
     * whether the user's preferred installer was unavailable.
     */
    data class ResolvedPlan(
        val plan: InstallPlan,
        val primaryUnavailable: Boolean,
        val primaryToken: Token,
        @StringRes val unavailabilityReason: Int?
    )

    sealed class Token {
        object Internal : Token()
        object AutoSaved : Token()
        object Shizuku : Token()
        object None : Token()
        data class Component(val componentName: ComponentName) : Token()
    }

    sealed class InstallPlan {
        data class Internal(val target: InstallTarget) : InstallPlan()
        data class Mount(val target: InstallTarget) : InstallPlan()
        data class Shizuku(val target: InstallTarget) : InstallPlan()
        data class External(
            val target: InstallTarget,
            val intent: Intent,
            val sharedFile: File,
            val uri: Uri,
            val expectedPackage: String,
            val installerLabel: String,
            val sourceLabel: String?,
            val token: Token
        ) : InstallPlan()
    }

    enum class InstallTarget(val supportsRoot: Boolean) {
        PATCHER(true),
        SAVED_APP(true),
        MANAGER_UPDATE(false)
    }

    companion object {
        private const val APK_MIME = "application/vnd.android.package-archive"
        internal const val SHARE_DIR = "installer_share"
        private const val AOSP_INSTALLER_PACKAGE = "com.google.android.packageinstaller"
        private const val AOSP_INSTALLER_LABEL = "Package installer"
        private const val SEARCH_PACKAGE_SUGGESTION_LIMIT = 24
        private const val DEFAULT_PACKAGE_SUGGESTION_LIMIT = 8
        private const val TAG = "InstallerManager"
    }

    fun openShizukuApp(): Boolean = shizukuInstaller.launchApp()

    fun formatFailureHint(status: Int, extraMessage: String?): String? {
        val normalizedExtra = extraMessage?.takeIf { it.isNotBlank() }
        val base = when (status) {
            PackageInstaller.STATUS_FAILURE -> app.getString(R.string.installer_hint_generic)
            PackageInstaller.STATUS_FAILURE_ABORTED -> app.getString(R.string.installer_hint_aborted)
            PackageInstaller.STATUS_FAILURE_BLOCKED -> app.getString(R.string.installer_hint_blocked)
            PackageInstaller.STATUS_FAILURE_CONFLICT -> app.getString(R.string.installer_hint_conflict)
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> app.getString(R.string.installer_hint_incompatible)
            PackageInstaller.STATUS_FAILURE_INVALID -> app.getString(R.string.installer_hint_invalid)
            PackageInstaller.STATUS_FAILURE_STORAGE -> app.getString(R.string.installer_hint_storage)
            PackageInstaller.STATUS_FAILURE_TIMEOUT -> app.getString(R.string.installer_hint_timeout)
            else -> null
        }

        return when {
            base == null -> normalizedExtra
            normalizedExtra == null -> base
            else -> app.getString(R.string.installer_hint_with_reason, base, normalizedExtra)
        }
    }

    fun isSignatureMismatch(message: String?): Boolean {
        val normalized = message?.lowercase(Locale.ROOT)?.trim().orEmpty()
        if (normalized.isEmpty()) return false
        return normalized.contains("install_failed_update_incompatible") ||
                normalized.contains("install_failed_signature_inconsistent") ||
                normalized.contains("signatures do not match") ||
                normalized.contains("signature mismatch")
    }
}

private fun InstallerManager.Token.describe(): String = when (this) {
    InstallerManager.Token.Internal -> "Internal"
    InstallerManager.Token.AutoSaved -> "AutoSaved"
    InstallerManager.Token.Shizuku -> "Shizuku"
    InstallerManager.Token.None -> "None"
    is InstallerManager.Token.Component -> "Component(${componentName.flattenToString()})"
}
