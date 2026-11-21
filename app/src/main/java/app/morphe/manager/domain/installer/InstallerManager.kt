/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 *
 * Original hard forked code:
 * https://github.com/Jman-Github/Universal-ReVanced-Manager/blob/597b3173a004f5a9aae54326046dd7fd4c5b7777/app/src/main/java/app/revanced/manager/domain/installer/InstallerManager.kt
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.manager.domain.installer

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.ClipData
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.StringRes
import app.morphe.manager.R
import app.morphe.manager.data.room.apps.installed.InstallType
import app.morphe.manager.domain.manager.AfterPatching
import app.morphe.manager.domain.manager.InstallerPreferenceTokens
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.domain.manager.RootPatchMode
import app.morphe.manager.util.AOSP_INSTALLER_LABEL
import app.morphe.manager.util.AOSP_INSTALLER_PACKAGE
import app.morphe.manager.util.AOSP_INSTALLER_PACKAGE_LEGACY
import app.morphe.manager.util.APK_MIMETYPE
import app.morphe.manager.util.PLAY_STORE_INSTALLER_PACKAGE
import java.io.File

private const val TAG = "Morphe InstallerManager"

class InstallerManager(
    private val app: Application,
    private val prefs: PreferencesManager,
    private val rootInstaller: RootInstaller,
    private val sessionInstaller: SessionInstaller
) {
    private val packageManager: PackageManager = app.packageManager
    private val dummyUri: Uri = InstallerFileProvider.buildUri(app, "dummy.apk")
    private val defaultInstallerComponent: ComponentName? by lazy { resolveDefaultInstallerComponent() }
    private val defaultInstallerPackage: String? get() = defaultInstallerComponent?.packageName
    private val hiddenInstallerPackages: Set<String>
        get() = prefs.installerHiddenComponents.getBlocking()
            .mapNotNull(ComponentName::unflattenFromString)
            .map { it.packageName }
            .toSet()

    /**
     * The installers to pick from for [target], [selected] among them even once the device no
     * longer offers it, so a choice made earlier never drops out of the list.
     */
    fun listEntries(target: InstallTarget, selected: Token): List<Entry> {
        val hiddenPackages = hiddenInstallerPackages
        val entries = listOf(
            Token.Internal,
            Token.PlayStore,
            Token.Root,
            Token.RootPlayStore,
            Token.Shizuku,
            Token.ShizukuPlayStore
        ).mapTo(mutableListOf()) { entryFor(it, target) }

        entries += queryInstallerActivities()
            .asSequence()
            .filter(::isInstallerCandidate)
            .distinctBy { it.activityInfo.packageName }
            .map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }
            .filterNot { component ->
                isDefaultComponent(component) ||
                        component.packageName in hiddenPackages ||
                        isExcludedDuplicate(component)
            }
            .map { entryFor(Token.Component(it), target) }
            .sortedBy { it.label.lowercase() }

        entries += readCustomInstallerTokens()
            .filterNot { token ->
                token.componentName.packageName in hiddenPackages || entries.any { it.token == token }
            }
            .map { entryFor(it, target) }
            .sortedBy { it.label.lowercase() }

        if (entries.none { it.token == selected }) entries += entryFor(selected, target)

        return entries
    }

    fun describeEntry(token: Token, target: InstallTarget): Entry = entryFor(token, target)

    fun parseToken(value: String?): Token {
        val token = when (value) {
            InstallerPreferenceTokens.SYSTEM -> Token.Internal
            InstallerPreferenceTokens.PLAY_STORE -> Token.PlayStore
            InstallerPreferenceTokens.ROOT -> Token.Root
            InstallerPreferenceTokens.ROOT_PLAY_STORE -> Token.RootPlayStore
            InstallerPreferenceTokens.NONE -> Token.None
            InstallerPreferenceTokens.SHIZUKU -> Token.Shizuku
            InstallerPreferenceTokens.SHIZUKU_PLAY_STORE -> Token.ShizukuPlayStore
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
        Token.PlayStore -> InstallerPreferenceTokens.PLAY_STORE
        Token.Root -> InstallerPreferenceTokens.ROOT
        Token.RootPlayStore -> InstallerPreferenceTokens.ROOT_PLAY_STORE
        Token.None -> InstallerPreferenceTokens.NONE
        Token.Shizuku -> InstallerPreferenceTokens.SHIZUKU
        Token.ShizukuPlayStore -> InstallerPreferenceTokens.SHIZUKU_PLAY_STORE
        is Token.Component -> token.componentName.flattenToString()
    }

    fun getPreferredToken(): Token = parseToken(prefs.installerPrimary.getBlocking())

    /** Whether apps are patched for root mount without asking first. */
    fun mountsByDefault(): Boolean =
        rootInstaller.isDeviceRooted() && prefs.rootPatchMode.getBlocking() == RootPatchMode.MOUNT

    /** Whether a rooted device asks before patching whether to patch for root mount. */
    suspend fun promptsBeforePatching(): Boolean =
        rootInstaller.isDeviceRooted() && prefs.rootPatchMode.get() == RootPatchMode.ASK

    /**
     * Whether an install started right after patching reaches the user on its own, either
     * silently or through a confirmation the installer knows how to put in front of them.
     *
     * @param mount Whether the build is mounted rather than installed, which root does without
     *   any installer, so the setting alone decides whether it starts on its own.
     */
    suspend fun autoInstallAllowed(targetPackageName: String, mount: Boolean): Boolean {
        if (prefs.afterPatching.get() != AfterPatching.INSTALL) return false
        if (mount) return true

        return when (val token = parseToken(prefs.installerPrimary.get())) {
            // A system session only updates an app silently in some cases
            Token.Internal -> sessionInstaller.canUpdateSilently(targetPackageName)
            else -> token.supportsAutoInstall
        }
    }

    suspend fun updatePreferredToken(token: Token) {
        Log.d(TAG, "updatePreferredToken -> ${token.describe()}")
        prefs.installerPrimary.update(tokenToPreference(token))
    }

    suspend fun uninstallPackage(packageName: String, installType: InstallType?) {
        if (installType == InstallType.MOUNT) {
            rootInstaller.uninstall(packageName)
            return
        }

        when (val preferredToken = getPreferredToken()) {
            Token.Shizuku,
            Token.ShizukuPlayStore -> {
                if (availabilityFor(preferredToken, InstallTarget.PATCHER, checkRoot = true).available) {
                    when (val result = sessionInstaller.uninstallShizuku(packageName)) {
                        UninstallResult.Success -> return
                        is UninstallResult.Failure -> throw Exception(
                            result.message ?: app.getString(R.string.installer_hint_generic)
                        )
                    }
                }
                sessionInstaller.uninstall(packageName)
            }

            Token.Root,
            Token.RootPlayStore -> {
                if (availabilityFor(preferredToken, InstallTarget.PATCHER, checkRoot = true).available) {
                    rootInstaller.uninstallPackage(packageName)
                } else {
                    sessionInstaller.uninstall(packageName)
                }
            }

            else -> sessionInstaller.uninstall(packageName)
        }
    }

    /**
     * Resolves the installation plan based on user's preferred installer.
     * Returns a [ResolvedPlan] which includes the plan and information about
     * whether the preferred installer was unavailable.
     */
    fun resolvePlanWithStatus(
        target: InstallTarget,
        sourceFile: File,
        expectedPackage: String,
        sourceLabel: String?,
        tokenOverride: Token? = null
    ): ResolvedPlan {
        val preferredToken = tokenOverride ?: getPreferredToken()
        val preferredAvailability = availabilityFor(preferredToken, target, checkRoot = true)

        // If the preferred installer is available, use it
        if (preferredAvailability.available) {
            val plan = createPlan(preferredToken, target, sourceFile, expectedPackage, sourceLabel)
            if (plan != null) {
                return ResolvedPlan(
                    plan = plan,
                    preferredUnavailable = false,
                    preferredToken = preferredToken,
                    unavailabilityReason = null
                )
            }
        }

        // The preferred installer is unavailable - check if it's Shizuku or root (special cases)
        val isSpecialInstaller = preferredToken == Token.Shizuku ||
                preferredToken == Token.ShizukuPlayStore ||
                preferredToken.usesRoot

        if (isSpecialInstaller) {
            // Return info about unavailability so UI can show appropriate dialog
            return ResolvedPlan(
                plan = InstallPlan.Internal(target), // Fallback
                preferredUnavailable = true,
                preferredToken = preferredToken,
                unavailabilityReason = preferredAvailability.reason
            )
        }

        // For other installers, try fallback sequence
        val sequence = buildSequence(target)
        sequence.forEach { token ->
            createPlan(token, target, sourceFile, expectedPackage, sourceLabel)?.let { plan ->
                return ResolvedPlan(
                    plan = plan,
                    preferredUnavailable = preferredToken != token,
                    preferredToken = preferredToken,
                    unavailabilityReason = if (preferredToken != token) preferredAvailability.reason else null
                )
            }
        }

        // Fallback to internal install
        return ResolvedPlan(
            plan = InstallPlan.Internal(target),
            preferredUnavailable = preferredToken != Token.Internal,
            preferredToken = preferredToken,
            unavailabilityReason = preferredAvailability.reason
        )
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

    /** The plan for [token], whose availability callers have already checked. */
    private fun createPlan(
        token: Token,
        target: InstallTarget,
        sourceFile: File,
        expectedPackage: String,
        sourceLabel: String?
    ): InstallPlan? {
        return when (token) {
            Token.Internal -> InstallPlan.Internal(target)
            Token.PlayStore -> InstallPlan.PlayStore(target)
            Token.Root -> InstallPlan.Root(target)
            Token.RootPlayStore -> InstallPlan.RootPlayStore(target)
            Token.None -> null
            Token.Shizuku -> InstallPlan.Shizuku(target)
            Token.ShizukuPlayStore -> InstallPlan.ShizukuPlayStore(target)
            is Token.Component -> {
                val uri = InstallerFileProvider.getUriForFile(app, sourceFile)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, APK_MIMETYPE)
                    @SuppressLint("WrongConstant")
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
                @SuppressLint("WrongConstant")
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
                    sharedFile = File(app.cacheDir, "${InstallerFileProvider.SHARE_DIR}/${sourceFile.name}"),
                    uri = uri,
                    expectedPackage = expectedPackage,
                    installerLabel = resolveLabel(token.componentName),
                    sourceLabel = sourceLabel,
                    token = token
                )
            }
        }
    }

    private fun resolveLabel(componentName: ComponentName): String =
        runCatching {
            val activityInfo: ActivityInfo = packageManager.getActivityInfo(componentName, 0)
            activityInfo.loadLabel(packageManager).toString()
        }.getOrDefault(componentName.packageName)

    /**
     * How [token] is offered for [target]. Availability is only what can be told without asking for
     * root, since entries are read again and again while an installer choice is on screen.
     */
    private fun entryFor(token: Token, target: InstallTarget): Entry {
        val (label, description) = when (token) {
            Token.Internal ->
                R.string.home_app_info_install_type_system_installer to R.string.installer_internal_description
            Token.None -> R.string.installer_option_none to R.string.installer_none_description
            Token.PlayStore ->
                R.string.home_app_info_install_type_play_store to R.string.installer_play_store_description
            Token.Root -> R.string.home_app_info_install_type_root to R.string.installer_root_description
            Token.RootPlayStore ->
                R.string.home_app_info_install_type_root_play_store to R.string.installer_root_play_store_description
            Token.Shizuku -> R.string.home_app_info_install_type_shizuku to R.string.installer_shizuku_description
            Token.ShizukuPlayStore ->
                R.string.home_app_info_install_type_shizuku_play_store to R.string.installer_shizuku_play_store_description
            is Token.Component -> return Entry(
                token = token,
                label = resolveLabel(token.componentName),
                description = token.componentName.packageName,
                availability = availabilityFor(token, target, checkRoot = false),
                iconPackageName = token.componentName.packageName
            )
        }
        return Entry(
            token = token,
            label = app.getString(label),
            description = app.getString(description),
            availability = availabilityFor(token, target, checkRoot = false),
            iconPackageName = when (token) {
                Token.PlayStore, Token.RootPlayStore -> PLAY_STORE_INSTALLER_PACKAGE
                Token.Shizuku, Token.ShizukuPlayStore -> sessionInstaller.shizukuPackageName()
                else -> null
            }
        )
    }

    private fun buildSequence(target: InstallTarget): List<Token> {
        val tokens = mutableListOf<Token>()
        val preferred = getPreferredToken()

        fun add(token: Token) {
            if (token == Token.None) return
            if (token in tokens) return
            // Use checkRoot = true here to ensure we only add available installers
            if (!availabilityFor(token, target, checkRoot = true).available) return
            tokens += token
        }

        add(preferred)

        if (Token.Internal !in tokens) add(Token.Internal)

        return tokens
    }

    private fun availabilityFor(token: Token, target: InstallTarget, checkRoot: Boolean = true): Availability = when (token) {
        Token.Internal -> Availability(true)
        Token.PlayStore -> Availability(true)
        Token.Root,
        Token.RootPlayStore -> if (!target.supportsRoot) {
            Availability(false, R.string.installer_status_not_supported)
        } else if (checkRoot) {
            if (!rootInstaller.hasRootAccess()) {
                Availability(false, R.string.installer_status_requires_root)
            } else {
                Availability(true)
            }
        } else {
            if (!rootInstaller.isDeviceRooted()) {
                Availability(false, R.string.installer_status_requires_root)
            } else {
                Availability(true)
            }
        }
        Token.None -> Availability(true)

        Token.Shizuku -> {
            if (!sessionInstaller.isShizukuInstalled()) {
                Availability(false, R.string.installer_status_shizuku_not_installed)
            } else if (checkRoot) {
                // Full availability check
                sessionInstaller.shizukuAvailability()
            } else {
                // Just verify Shizuku is installed (for UI display)
                Availability(true)
            }
        }

        Token.ShizukuPlayStore -> {
            if (target != InstallTarget.PATCHER) {
                Availability(false, R.string.installer_status_not_supported)
            } else if (!sessionInstaller.isShizukuInstalled()) {
                Availability(false, R.string.installer_status_shizuku_not_installed)
            } else if (checkRoot) {
                sessionInstaller.shizukuAvailability()
            } else {
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
            setDataAndType(dummyUri, APK_MIMETYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            component = componentName
        }
        return intent.resolveActivity(packageManager) != null
    }

    private fun queryInstallerActivities() =
        packageManager.queryIntentActivities(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(dummyUri, APK_MIMETYPE)
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
            AOSP_INSTALLER_PACKAGE,
            AOSP_INSTALLER_PACKAGE_LEGACY
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

    private fun isExcludedDuplicate(component: ComponentName): Boolean =
        component.packageName == AOSP_INSTALLER_PACKAGE &&
                resolveLabel(component).equals(AOSP_INSTALLER_LABEL, ignoreCase = true)

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

    /**
     * An installer as it is offered. Holds no drawable, so two reads that found the same compare
     * equal and polling them recomposes nothing until something changes.
     *
     * @param iconPackageName The app whose icon stands for the installer, null for a generic one.
     */
    data class Entry(
        val token: Token,
        val label: String,
        val description: String?,
        val availability: Availability,
        val iconPackageName: String?
    )

    data class Availability(
        val available: Boolean,
        @param:StringRes val reason: Int? = null
    )

    /**
     * Result of [resolvePlanWithStatus] that includes information about
     * whether the user's preferred installer was unavailable.
     */
    data class ResolvedPlan(
        val plan: InstallPlan,
        val preferredUnavailable: Boolean,
        val preferredToken: Token,
        @param:StringRes val unavailabilityReason: Int?
    )

    sealed class Token {
        /**
         * Whether an install through it can go ahead right after patching, which is what the
         * auto-install setting is offered for. The system installer needs Android 12+ to update
         * silently.
         */
        val supportsAutoInstall: Boolean
            get() = when (this) {
                Shizuku, ShizukuPlayStore, Root, RootPlayStore -> true
                Internal -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                else -> false
            }

        object Internal : Token()
        object PlayStore : Token()
        /** Whether it installs with root, with or without Google Play as the install source. */
        val usesRoot: Boolean
            get() = this == Root || this == RootPlayStore

        object Root : Token()
        object RootPlayStore : Token()
        object Shizuku : Token()
        object ShizukuPlayStore : Token()
        object None : Token()
        data class Component(val componentName: ComponentName) : Token()
    }

    sealed class InstallPlan {
        data class Internal(val target: InstallTarget) : InstallPlan()
        data class PlayStore(val target: InstallTarget) : InstallPlan()
        data class Root(val target: InstallTarget) : InstallPlan()
        data class RootPlayStore(val target: InstallTarget) : InstallPlan()
        data class Shizuku(val target: InstallTarget) : InstallPlan()
        data class ShizukuPlayStore(val target: InstallTarget) : InstallPlan()
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
        MANAGER_UPDATE(false)
    }

    fun openShizukuApp(): Boolean = sessionInstaller.launchShizukuApp()

    fun shizukuStatus(): SessionInstaller.ShizukuStatus = sessionInstaller.shizukuStatus()

    fun requestShizukuPermission(): Boolean = sessionInstaller.requestShizukuPermission()
}

private fun InstallerManager.Token.describe(): String = when (this) {
    InstallerManager.Token.Internal -> "Internal"
    InstallerManager.Token.PlayStore -> "PlayStore"
    InstallerManager.Token.Root -> "Root"
    InstallerManager.Token.RootPlayStore -> "RootPlayStore"
    InstallerManager.Token.Shizuku -> "Shizuku"
    InstallerManager.Token.ShizukuPlayStore -> "ShizukuPlayStore"
    InstallerManager.Token.None -> "None"
    is InstallerManager.Token.Component -> "Component(${componentName.flattenToString()})"
}
