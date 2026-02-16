package app.morphe.manager.domain.manager

import android.content.Context
import android.os.Build
import android.util.Log
import app.morphe.manager.domain.manager.base.BasePreferencesManager
import app.morphe.manager.patcher.runtime.PROCESS_RUNTIME_MEMORY_DEFAULT
import app.morphe.manager.ui.screen.shared.BackgroundType
import app.morphe.manager.ui.theme.Theme
import app.morphe.manager.util.ExportNameFormatter
import app.morphe.manager.util.isArmV7
import app.morphe.manager.util.tag
import app.morphe.manager.BuildConfig
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable

class PreferencesManager(
    context: Context
) : BasePreferencesManager(context, "settings") {

    // Appearance tab
    val backgroundType = enumPreference("background_type", BackgroundType.CIRCLES)
    val enableBackgroundParallax = booleanPreference("enable_background_parallax", true)

    val dynamicColor = booleanPreference("dynamic_color", true)
    val pureBlackTheme = booleanPreference("pure_black_theme", false)
    val themePresetSelectionEnabled = booleanPreference("theme_preset_selection_enabled", true)
    val themePresetSelectionName = stringPreference("theme_preset_selection_name", "DEFAULT")
    val customAccentColor = stringPreference("custom_accent_color", "")
    val customThemeColor = stringPreference("custom_theme_color", "")
    val theme = enumPreference("theme", Theme.SYSTEM)

    val appLanguage = stringPreference("app_language", "system")

    // Advanced tab
    val useManagerPrereleases = booleanPreference("manager_prereleases", false)
    val usePatchesPrereleases = booleanPreference("patches_prereleases", false)

    val useExpertMode = booleanPreference("use_expert_mode", false)

    val stripUnusedNativeLibs = booleanPreference("strip_unused_native_libs", false)

    // System tab
    val installerPrimary = stringPreference("installer_primary", InstallerPreferenceTokens.INTERNAL)
    val promptInstallerOnInstall = booleanPreference("prompt_installer_on_install",false)
    val installerCustomComponents = stringSetPreference("installer_custom_components", emptySet())
    val installerHiddenComponents = stringSetPreference("installer_hidden_components", emptySet())

    val useProcessRuntime = booleanPreference(
        "use_process_runtime",
        // Use process runtime fails for Android 10 and lower.
        // Armv7 silently fails and nobody has researched why yet.
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !isArmV7()
    )
    val patcherProcessMemoryLimit = intPreference("use_process_runtime_memory_limit", PROCESS_RUNTIME_MEMORY_DEFAULT)

    val keystoreAlias = stringPreference("keystore_alias", KeystoreManager.DEFAULT)
    val keystorePass = stringPreference("keystore_pass", KeystoreManager.DEFAULT)

    // Others hidden settings
    val gitHubPat = stringPreference("github_pat", "")
    val includeGitHubPatInExports = booleanPreference("include_github_pat_in_exports", false)

    val patchedAppExportFormat = stringPreference(
        "patched_app_export_format",
        ExportNameFormatter.DEFAULT_TEMPLATE
    )
    val allowMeteredUpdates = booleanPreference("allow_metered_updates", true)
    val firstLaunch = booleanPreference("first_launch", true)
    val managerAutoUpdates = booleanPreference("manager_auto_updates", true)
    val installationTime = longPreference("manager_installation_time", 0)
    val disablePatchVersionCompatCheck = booleanPreference("disable_patch_version_compatibility_check", false)

    // Hidden preference to track if prerelease was auto-enabled
    private val prereleaseAutoEnabled = booleanPreference("prerelease_auto_enabled", false)

    init {
        runBlocking {
            if (installationTime.get() == 0L) {
                val now = System.currentTimeMillis()
                installationTime.update(now)
                Log.d(tag, "Installation time set to $now")
            }

            // Auto-enable prereleases for dev versions
            if (isDevVersion() && !prereleaseAutoEnabled.get()) {
                Log.d(tag, "Dev version detected (${BuildConfig.VERSION_NAME}), auto-enabling prereleases")
                useManagerPrereleases.update(true)
                usePatchesPrereleases.update(true)
                prereleaseAutoEnabled.update(true)
            }
        }
    }

    @Serializable
    data class SettingsSnapshot(
        val dynamicColor: Boolean? = null,
        val pureBlackTheme: Boolean? = null,
        val customAccentColor: String? = null,
        val customThemeColor: String? = null,
        val themePresetSelectionName: String? = null,
        val themePresetSelectionEnabled: Boolean? = null,
        val stripUnusedNativeLibs: Boolean? = null,
        val theme: Theme? = null,
        val appLanguage: String? = null,
        val api: String? = null,
        val gitHubPat: String? = null,
        val includeGitHubPatInExports: Boolean? = null,
        val useProcessRuntime: Boolean? = null,
        val patcherProcessMemoryLimit: Int? = null,
        val autoCollapsePatcherSteps: Boolean? = null,
        val patchedAppExportFormat: String? = null,
        val officialBundleRemoved: Boolean? = null,
        val officialBundleCustomDisplayName: String? = null,
        val allowMeteredUpdates: Boolean? = null,
        val installerPrimary: String? = null,
        val installerCustomComponents: Set<String>? = null,
        val installerHiddenComponents: Set<String>? = null,
        val keystoreAlias: String? = null,
        val keystorePass: String? = null,
        val firstLaunch: Boolean? = null,
        val managerAutoUpdates: Boolean? = null,
        val showManagerUpdateDialogOnLaunch: Boolean? = null,
        val useManagerPrereleases: Boolean? = null,
        val usePatchesPrereleases: Boolean? = null,
        val disablePatchVersionCompatCheck: Boolean? = null,
        val disableSelectionWarning: Boolean? = null,
        val disableUniversalPatchCheck: Boolean? = null,
        val suggestedVersionSafeguard: Boolean? = null,
        val disablePatchSelectionConfirmations: Boolean? = null,
        val collapsePatchActionsOnSelection: Boolean? = null,
        val patchSelectionFilterFlags: Int? = null,
        val patchSelectionSortAlphabetical: Boolean? = null,
        val patchSelectionSortSettingsMode: String? = null,
        val patchSelectionActionOrder: String? = null,
        val patchSelectionHiddenActions: Set<String>? = null,
        val acknowledgedDownloaderPlugins: Set<String>? = null,
        val autoSaveDownloaderApks: Boolean? = null,
        val backgroundType: BackgroundType? = null,
        val useExpertMode: Boolean? = null,
    )

    suspend fun exportSettings() = SettingsSnapshot(
        dynamicColor = dynamicColor.get(),
        pureBlackTheme = pureBlackTheme.get(),
        customAccentColor = customAccentColor.get(),
        customThemeColor = customThemeColor.get(),
        themePresetSelectionName = themePresetSelectionName.get(),
        themePresetSelectionEnabled = themePresetSelectionEnabled.get(),
        stripUnusedNativeLibs = stripUnusedNativeLibs.get(),
        theme = theme.get(),
        appLanguage = appLanguage.get(),
        gitHubPat = gitHubPat.get().takeIf { includeGitHubPatInExports.get() },
        includeGitHubPatInExports = includeGitHubPatInExports.get(),
        useProcessRuntime = useProcessRuntime.get(),
        patcherProcessMemoryLimit = patcherProcessMemoryLimit.get(),
        patchedAppExportFormat = patchedAppExportFormat.get(),
        allowMeteredUpdates = allowMeteredUpdates.get(),
        installerPrimary = installerPrimary.get(),
        installerCustomComponents = installerCustomComponents.get(),
        installerHiddenComponents = installerHiddenComponents.get(),
        keystoreAlias = keystoreAlias.get(),
        keystorePass = keystorePass.get(),
        firstLaunch = firstLaunch.get(),
        managerAutoUpdates = managerAutoUpdates.get(),
        useManagerPrereleases = useManagerPrereleases.get(),
        usePatchesPrereleases = usePatchesPrereleases.get(),
        disablePatchVersionCompatCheck = disablePatchVersionCompatCheck.get(),
        backgroundType = backgroundType.get(),
        useExpertMode = useExpertMode.get()
    )

    suspend fun importSettings(snapshot: SettingsSnapshot) = edit {
        snapshot.dynamicColor?.let { dynamicColor.value = it }
        snapshot.pureBlackTheme?.let { pureBlackTheme.value = it }
        snapshot.customAccentColor?.let { customAccentColor.value = it }
        snapshot.customThemeColor?.let { customThemeColor.value = it }
        snapshot.themePresetSelectionName?.let { themePresetSelectionName.value = it }
        snapshot.themePresetSelectionEnabled?.let { themePresetSelectionEnabled.value = it }
        snapshot.stripUnusedNativeLibs?.let { stripUnusedNativeLibs.value = it }
        snapshot.theme?.let { theme.value = it }
        snapshot.appLanguage?.let { appLanguage.value = it }
        snapshot.gitHubPat?.let { gitHubPat.value = it }
        snapshot.includeGitHubPatInExports?.let { includeGitHubPatInExports.value = it }
        snapshot.useProcessRuntime?.let { useProcessRuntime.value = it }
        snapshot.patcherProcessMemoryLimit?.let { patcherProcessMemoryLimit.value = it }
        snapshot.patchedAppExportFormat?.let { patchedAppExportFormat.value = it }
        snapshot.allowMeteredUpdates?.let { allowMeteredUpdates.value = it }
        snapshot.installerPrimary?.let { installerPrimary.value = it }
        snapshot.installerCustomComponents?.let { installerCustomComponents.value = it }
        snapshot.installerHiddenComponents?.let { installerHiddenComponents.value = it }
        snapshot.keystoreAlias?.let { keystoreAlias.value = it }
        snapshot.keystorePass?.let { keystorePass.value = it }
        snapshot.firstLaunch?.let { firstLaunch.value = it }
        snapshot.managerAutoUpdates?.let { managerAutoUpdates.value = it }
        snapshot.useManagerPrereleases?.let { useManagerPrereleases.value = it }
        snapshot.usePatchesPrereleases?.let { usePatchesPrereleases.value = it }
        snapshot.disablePatchVersionCompatCheck?.let { disablePatchVersionCompatCheck.value = it }
        snapshot.backgroundType?.let { backgroundType.value = it }
        snapshot.useExpertMode?.let { useExpertMode.value = it }
    }

    companion object {
        /**
         * Check if current version is a development/prerelease version
         */
        fun isDevVersion(): Boolean {
            return BuildConfig.VERSION_NAME.contains("-dev", ignoreCase = true)
        }
    }
}

object InstallerPreferenceTokens {
    const val INTERNAL = ":internal:"
    const val SYSTEM = ":system:"
    const val ROOT = ":root:" // Legacy value, mapped to AUTO_SAVED.
    const val AUTO_SAVED = ":auto_saved:"
    const val SHIZUKU = ":shizuku:"
    const val NONE = ":none:"
}
