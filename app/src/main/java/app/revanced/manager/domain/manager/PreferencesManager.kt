package app.revanced.manager.domain.manager

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log
import app.revanced.manager.domain.manager.base.BasePreferencesManager
import app.revanced.manager.patcher.runtime.PROCESS_RUNTIME_MEMORY_DEFAULT
import app.revanced.manager.ui.component.morphe.shared.BackgroundType
import app.revanced.manager.ui.model.PatchSelectionActionKey
import app.revanced.manager.ui.theme.Theme
import app.revanced.manager.util.ExportNameFormatter
import app.revanced.manager.util.isArmV7
import app.revanced.manager.util.tag
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable

class PreferencesManager(
    context: Context
) : BasePreferencesManager(context, "settings") {
    companion object {
        private val PATCH_ACTION_ORDER_DEFAULT =
            PatchSelectionActionKey.DefaultOrder.joinToString(",") { it.storageId }
    }
    val dynamicColor = booleanPreference("dynamic_color", true)
    val pureBlackTheme = booleanPreference("pure_black_theme", false)
    val themePresetSelectionEnabled = booleanPreference("theme_preset_selection_enabled", true)
    val themePresetSelectionName = stringPreference("theme_preset_selection_name", "DEFAULT")
    val customAccentColor = stringPreference("custom_accent_color", "")
    val customThemeColor = stringPreference("custom_theme_color", "")
    val theme = enumPreference("theme", Theme.SYSTEM)
    val appLanguage = stringPreference("app_language", "system")

    // Morphe: API preference is currently hidden
    val api = stringPreference("api_url", "https://api.morphe.software")
    val gitHubPat = stringPreference("github_pat", "")
    val includeGitHubPatInExports = booleanPreference("include_github_pat_in_exports", false)

    // Morphe
    val useProcessRuntime = booleanPreference(
        "use_process_runtime",
        // Use process runtime fails for Android 10 and lower.
        // Armv7 silently fails and nobody has researched why yet.
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !isArmV7()
    )
    val stripUnusedNativeLibs = booleanPreference("strip_unused_native_libs", false)
    val patcherProcessMemoryLimit = intPreference("use_process_runtime_memory_limit", PROCESS_RUNTIME_MEMORY_DEFAULT)
    val patchedAppExportFormat = stringPreference(
        "patched_app_export_format",
        ExportNameFormatter.DEFAULT_TEMPLATE
    )
    val officialBundleRemoved = booleanPreference("official_bundle_removed", false)
    val officialBundleSortOrder = intPreference("official_bundle_sort_order", -1)
    val officialBundleCustomDisplayName = stringPreference("official_bundle_custom_display_name", "")
    val autoCollapsePatcherSteps = booleanPreference("auto_collapse_patcher_steps", false)

    val allowMeteredUpdates = booleanPreference("allow_metered_updates", true)
    val installerPrimary = stringPreference("installer_primary", InstallerPreferenceTokens.INTERNAL)
    val installerFallback = stringPreference("installer_fallback", InstallerPreferenceTokens.NONE)
    val installerCustomComponents = stringSetPreference("installer_custom_components", emptySet())
    val installerHiddenComponents = stringSetPreference("installer_hidden_components", emptySet())

    val keystoreAlias = stringPreference("keystore_alias", KeystoreManager.DEFAULT)
    val keystorePass = stringPreference("keystore_pass", KeystoreManager.DEFAULT)

    val firstLaunch = booleanPreference("first_launch", true)
    val managerAutoUpdates = booleanPreference("manager_auto_updates", true)
    val showManagerUpdateDialogOnLaunch = booleanPreference("show_manager_update_dialog_on_launch", true)
    val useManagerPrereleases = booleanPreference("manager_prereleases", false)
    val usePatchesPrereleases = booleanPreference("patches_prereleases", false)

    val installationTime = longPreference("manager_installation_time", 0)

    val disablePatchVersionCompatCheck = booleanPreference("disable_patch_version_compatibility_check", false)
    val disableSelectionWarning = booleanPreference("disable_selection_warning", true)
    val disableUniversalPatchCheck = booleanPreference("disable_patch_universal_check", false)
    val suggestedVersionSafeguard = booleanPreference("suggested_version_safeguard", false)
    val disablePatchSelectionConfirmations = booleanPreference("disable_patch_selection_confirmations", false)
    val collapsePatchActionsOnSelection = booleanPreference("collapse_patch_actions_on_selection", true)
    val patchSelectionFilterFlags = intPreference("patch_selection_filter_flags", -1)
    val patchSelectionSortAlphabetical = booleanPreference("patch_selection_sort_alphabetical", false)
    val patchSelectionSortSettingsMode = stringPreference("patch_selection_sort_settings_mode", "None")
    val patchSelectionActionOrder =
        stringPreference("patch_selection_action_order", PATCH_ACTION_ORDER_DEFAULT)
    val patchSelectionHiddenActions =
        stringSetPreference("patch_selection_hidden_actions", emptySet())

    val acknowledgedDownloaderPlugins = stringSetPreference("acknowledged_downloader_plugins", emptySet())
    val autoSaveDownloaderApks = booleanPreference("auto_save_downloader_apks", true)

    val useMorpheHomeScreen = booleanPreference("use_morphe_home_screen", true)
    val useExpertMode = booleanPreference("use_expert_mode", false)
    val backgroundType = enumPreference("background_type", BackgroundType.CIRCLES)

    init {
        runBlocking {
            if (installationTime.get() == 0L) {
                val now = System.currentTimeMillis()
                installationTime.update(now)
                Log.d(tag, "Installation time set to $now")
            }
        }
    }

    object PatchBundleConstants {
        const val BUNDLE_URL_RELEASES = "https://github.com/MorpheApp/morphe-patches/releases/latest"
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
        val installerFallback: String? = null,
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
        val backgroundType: BackgroundType? = null, // Morphe
        val useMorpheHomeScreen: Boolean? = null, // Morphe
        val useExpertMode: Boolean? = null, // Morphe
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
        api = api.get(),
        gitHubPat = gitHubPat.get().takeIf { includeGitHubPatInExports.get() },
        includeGitHubPatInExports = includeGitHubPatInExports.get(),
        useProcessRuntime = useProcessRuntime.get(),
        patcherProcessMemoryLimit = patcherProcessMemoryLimit.get(),
        autoCollapsePatcherSteps = autoCollapsePatcherSteps.get(),
        patchedAppExportFormat = patchedAppExportFormat.get(),
        officialBundleRemoved = officialBundleRemoved.get(),
        officialBundleCustomDisplayName = officialBundleCustomDisplayName.get(),
        allowMeteredUpdates = allowMeteredUpdates.get(),
        installerPrimary = installerPrimary.get(),
        installerFallback = installerFallback.get(),
        installerCustomComponents = installerCustomComponents.get(),
        installerHiddenComponents = installerHiddenComponents.get(),
        keystoreAlias = keystoreAlias.get(),
        keystorePass = keystorePass.get(),
        firstLaunch = firstLaunch.get(),
        managerAutoUpdates = managerAutoUpdates.get(),
        showManagerUpdateDialogOnLaunch = showManagerUpdateDialogOnLaunch.get(),
        useManagerPrereleases = useManagerPrereleases.get(),
        usePatchesPrereleases = usePatchesPrereleases.get(),
        disablePatchVersionCompatCheck = disablePatchVersionCompatCheck.get(),
        disableSelectionWarning = disableSelectionWarning.get(),
        disableUniversalPatchCheck = disableUniversalPatchCheck.get(),
        suggestedVersionSafeguard = suggestedVersionSafeguard.get(),
        disablePatchSelectionConfirmations = disablePatchSelectionConfirmations.get(),
        collapsePatchActionsOnSelection = collapsePatchActionsOnSelection.get(),
        patchSelectionFilterFlags = patchSelectionFilterFlags.get(),
        patchSelectionSortAlphabetical = patchSelectionSortAlphabetical.get(),
        patchSelectionSortSettingsMode = patchSelectionSortSettingsMode.get(),
        patchSelectionActionOrder = patchSelectionActionOrder.get(),
        patchSelectionHiddenActions = patchSelectionHiddenActions.get(),
        acknowledgedDownloaderPlugins = acknowledgedDownloaderPlugins.get(),
        autoSaveDownloaderApks = autoSaveDownloaderApks.get(),
        backgroundType = backgroundType.get(), // Morphe
        useMorpheHomeScreen = useMorpheHomeScreen.get(), // Morphe
        useExpertMode = useExpertMode.get() // Morphe
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
        snapshot.api?.let { api.value = it }
        snapshot.gitHubPat?.let { gitHubPat.value = it }
        snapshot.includeGitHubPatInExports?.let { includeGitHubPatInExports.value = it }
        snapshot.useProcessRuntime?.let { useProcessRuntime.value = it }
        snapshot.patcherProcessMemoryLimit?.let { patcherProcessMemoryLimit.value = it }
        snapshot.autoCollapsePatcherSteps?.let { autoCollapsePatcherSteps.value = it }
        snapshot.patchedAppExportFormat?.let { patchedAppExportFormat.value = it }
        snapshot.officialBundleRemoved?.let { officialBundleRemoved.value = it }
        snapshot.officialBundleCustomDisplayName?.let { officialBundleCustomDisplayName.value = it }
        snapshot.allowMeteredUpdates?.let { allowMeteredUpdates.value = it }
        snapshot.installerPrimary?.let { installerPrimary.value = it }
        snapshot.installerFallback?.let { installerFallback.value = it }
        snapshot.installerCustomComponents?.let { installerCustomComponents.value = it }
        snapshot.installerHiddenComponents?.let { installerHiddenComponents.value = it }
        snapshot.keystoreAlias?.let { keystoreAlias.value = it }
        snapshot.keystorePass?.let { keystorePass.value = it }
        snapshot.firstLaunch?.let { firstLaunch.value = it }
        snapshot.managerAutoUpdates?.let { managerAutoUpdates.value = it }
        snapshot.showManagerUpdateDialogOnLaunch?.let {
            showManagerUpdateDialogOnLaunch.value = it
        }
        snapshot.useManagerPrereleases?.let { useManagerPrereleases.value = it }
        snapshot.usePatchesPrereleases?.let { usePatchesPrereleases.value = it }
        snapshot.disablePatchVersionCompatCheck?.let { disablePatchVersionCompatCheck.value = it }
        snapshot.disableSelectionWarning?.let { disableSelectionWarning.value = it }
        snapshot.disableUniversalPatchCheck?.let { disableUniversalPatchCheck.value = it }
        snapshot.suggestedVersionSafeguard?.let { suggestedVersionSafeguard.value = it }
        snapshot.disablePatchSelectionConfirmations?.let { disablePatchSelectionConfirmations.value = it }
        snapshot.collapsePatchActionsOnSelection?.let { collapsePatchActionsOnSelection.value = it }
        snapshot.patchSelectionFilterFlags?.let { patchSelectionFilterFlags.value = it }
        snapshot.patchSelectionSortAlphabetical?.let { patchSelectionSortAlphabetical.value = it }
        snapshot.patchSelectionSortSettingsMode?.let { patchSelectionSortSettingsMode.value = it }
        snapshot.patchSelectionActionOrder?.let { patchSelectionActionOrder.value = it }
        snapshot.patchSelectionHiddenActions?.let { patchSelectionHiddenActions.value = it }
        snapshot.acknowledgedDownloaderPlugins?.let { acknowledgedDownloaderPlugins.value = it }
        snapshot.autoSaveDownloaderApks?.let { autoSaveDownloaderApks.value = it }
        snapshot.backgroundType?.let { backgroundType.value = it }
        snapshot.useMorpheHomeScreen?.let { useMorpheHomeScreen.value = it }
        snapshot.useExpertMode?.let { useExpertMode.value = it }
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

suspend fun PreferencesManager.hideInstallerComponent(component: ComponentName) = edit {
    val flattened = component.flattenToString()
    installerHiddenComponents.value = installerHiddenComponents.value + flattened
}

suspend fun PreferencesManager.showInstallerComponent(component: ComponentName) = edit {
    val flattened = component.flattenToString()
    installerHiddenComponents.value = installerHiddenComponents.value - flattened
}
