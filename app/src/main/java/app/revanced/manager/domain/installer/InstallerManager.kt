package app.revanced.manager.domain.installer

import android.Manifest
import android.app.Application
import android.content.ClipData
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.StringRes
import app.morphe.manager.BuildConfig
import app.revanced.manager.domain.manager.PreferencesManager
import app.revanced.manager.domain.manager.InstallerPreferenceTokens
import app.morphe.manager.R
import java.io.File
import java.io.IOException
import java.util.LinkedHashMap
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

    fun listEntries(target: InstallTarget, includeNone: Boolean): List<Entry> {
        val entries = mutableListOf<Entry>()

        entryFor(Token.Internal, target)?.let(entries::add)
        entryFor(Token.Shizuku, target)?.let(entries::add)

        val activityEntries = queryInstallerActivities()
            .filter(::isInstallerCandidate)
            .distinctBy { it.activityInfo.packageName }
            .mapNotNull { info ->
                val component = ComponentName(info.activityInfo.packageName, info.activityInfo.name)
                if (isDefaultComponent(component)) return@mapNotNull null
                if (isExcludedDuplicate(component.packageName, info.loadLabel(packageManager)?.toString() ?: info.activityInfo.packageName)) {
                    return@mapNotNull null
                }
                entryFor(Token.Component(component), target)
            }
            .sortedBy { it.label.lowercase() }

        entries += activityEntries

        val customEntries = readCustomInstallerTokens()
            .mapNotNull { token ->
                entryFor(token, target)
            }
            .filterNot { customEntry ->
                entries.any { tokensEqual(it.token, customEntry.token) }
            }
            .sortedBy { it.label.lowercase() }

        entries += customEntries

        if (includeNone) {
            entryFor(Token.None, target)?.let(entries::add)
        }

        return entries
    }

    fun describeEntry(token: Token, target: InstallTarget): Entry? = entryFor(token, target)

    fun parseToken(value: String?): Token {
        val token = when (value) {
            InstallerPreferenceTokens.ROOT,
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
        Token.Root -> InstallerPreferenceTokens.INTERNAL
        Token.None -> InstallerPreferenceTokens.NONE
        Token.Shizuku -> InstallerPreferenceTokens.SHIZUKU
        is Token.Component -> token.componentName.flattenToString()
    }

    fun getPrimaryToken(): Token = parseToken(prefs.installerPrimary.getBlocking())

    fun getFallbackToken(): Token = parseToken(prefs.installerFallback.getBlocking())

    suspend fun updatePrimaryToken(token: Token) {
        Log.d(TAG, "updatePrimaryToken -> ${token.describe()}")
        prefs.installerPrimary.update(tokenToPreference(token))
    }

    suspend fun updateFallbackToken(token: Token) {
        Log.d(TAG, "updateFallbackToken -> ${token.describe()}")
        prefs.installerFallback.update(tokenToPreference(token))
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
            val packageName = info.packageName ?: return@forEach
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
                entryFor(Token.Component(component), target)
            }
            .sortedBy { it.label.lowercase() }

        if (entries.isNotEmpty()) return entries

        return readCustomInstallerTokens()
            .filter { it.componentName.packageName.equals(normalized, ignoreCase = true) }
            .mapNotNull { entryFor(it, target) }
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
                entryFor(token, target)?.let(::add)
            }
            return results.values.sortedBy { it.label.lowercase() }
        }

        val lower = normalized.lowercase()

        customTokens.forEach { token ->
            val entry = entryFor(token, target) ?: return@forEach
            val packageMatch = token.componentName.packageName.contains(lower, ignoreCase = true)
            val classMatch = token.componentName.className.contains(lower, ignoreCase = true)
            val labelMatch = entry.label.contains(lower, ignoreCase = true)
            if (packageMatch || classMatch || labelMatch) add(entry)
        }

        queryInstallerActivities()
            .filter(::isInstallerCandidate)
            .forEach { info ->
                val component = ComponentName(info.activityInfo.packageName, info.activityInfo.name)
                val entry = entryFor(Token.Component(component), target) ?: return@forEach
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

    fun resolvePlan(
        target: InstallTarget,
        sourceFile: File,
        expectedPackage: String,
        sourceLabel: String?
    ): InstallPlan {
        val sequence = buildSequence(target)
        sequence.forEach { token ->
            createPlan(token, target, sourceFile, expectedPackage, sourceLabel)?.let { return it }
        }

        // Should never happen, fallback to internal install.
        return InstallPlan.Internal(target)
    }

    fun cleanup(plan: InstallPlan.External) {
        runCatching {
            app.revokeUriPermission(plan.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        plan.sharedFile.delete()
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
            Token.Root -> if (availabilityFor(Token.Root, target).available) {
                InstallPlan.Root(target)
            } else null

            Token.Shizuku -> if (availabilityFor(Token.Shizuku, target).available) {
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
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                        clipData = ClipData.newRawUri("APK", uri)
                        putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                        putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, app.packageName)
                        component = token.componentName
                    }
                    app.grantUriPermission(
                        token.componentName.packageName,
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
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
            activityInfo.loadLabel(packageManager)?.toString() ?: componentName.packageName
        }.getOrDefault(componentName.packageName)

    private fun entryFor(token: Token, target: InstallTarget): Entry? = when (token) {
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

        Token.Shizuku -> Entry(
            token = Token.Shizuku,
            label = app.getString(R.string.installer_shizuku_name),
            description = app.getString(R.string.installer_shizuku_description),
            availability = availabilityFor(Token.Shizuku, target),
            icon = if (shizukuInstaller.isInstalled()) loadInstallerIcon(ShizukuInstaller.PACKAGE_NAME) else null
        )

        is Token.Component -> {
            val availability = availabilityFor(token, target)
            Entry(
                token = token,
                label = resolveLabel(token.componentName),
                description = token.componentName.packageName,
                availability = availability,
                icon = loadInstallerIcon(token.componentName)
            )
        }

        Token.Root -> null
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
        val primary = parseToken(prefs.installerPrimary.getBlocking())
        val fallback = parseToken(prefs.installerFallback.getBlocking())

        fun add(token: Token) {
            if (token == Token.None) return
            if (token in tokens) return
            if (!availabilityFor(token, target).available) return
            tokens += token
        }

        add(primary)

        if (Token.Internal !in tokens) add(Token.Internal)

        add(fallback)

        return tokens
    }

    private fun availabilityFor(token: Token, target: InstallTarget): Availability = when (token) {
        Token.Internal -> Availability(true)
        Token.None -> Availability(true)

        Token.Root -> if (!target.supportsRoot) {
            Availability(false, R.string.installer_status_not_supported)
        } else if (!rootInstaller.hasRootAccess()) {
            Availability(false, R.string.installer_status_requires_root)
        } else Availability(true)

        Token.Shizuku -> {
            if (!shizukuInstaller.isInstalled()) {
                Availability(false, R.string.installer_status_shizuku_not_installed)
            } else {
                shizukuInstaller.availability(target)
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
        val resolveInfo = packageManager.resolveActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(dummyUri, APK_MIME)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            PackageManager.MATCH_DEFAULT_ONLY
        ) ?: return null
        val activityInfo = resolveInfo.activityInfo ?: return null
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

    sealed class Token {
        object Internal : Token()
        object Root : Token()
        object Shizuku : Token()
        object None : Token()
        data class Component(val componentName: ComponentName) : Token()
    }

    sealed class InstallPlan {
        data class Internal(val target: InstallTarget) : InstallPlan()
        data class Root(val target: InstallTarget) : InstallPlan()
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
}

private fun InstallerManager.Token.describe(): String = when (this) {
    InstallerManager.Token.Internal -> "Internal"
    InstallerManager.Token.Root -> "Root"
    InstallerManager.Token.Shizuku -> "Shizuku"
    InstallerManager.Token.None -> "None"
    is InstallerManager.Token.Component -> "Component(${componentName.flattenToString()})"
}
