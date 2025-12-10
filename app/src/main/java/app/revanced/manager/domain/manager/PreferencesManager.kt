package app.revanced.manager.domain.manager

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log
import app.revanced.manager.domain.manager.base.BasePreferencesManager
import app.revanced.manager.ui.theme.Theme
import app.revanced.manager.util.ExportNameFormatter
import app.revanced.manager.util.tag
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable

class PreferencesManager(
    context: Context
) : BasePreferencesManager(context, "settings") {
    val dynamicColor = booleanPreference("dynamic_color", true)
    val pureBlackTheme = booleanPreference("pure_black_theme", false)
    val customAccentColor = stringPreference("custom_accent_color", "")
    val customThemeColor = stringPreference("custom_theme_color", "")
    val theme = enumPreference("theme", Theme.SYSTEM)

    val patchesBundleJsonUrl = stringPreference(
        "patches_bundle_json_url",
        PatchBundleConstants.BUNDLE_URL_STABLE
    )

    val useProcessRuntime = booleanPreference(
        "use_process_runtime",
        // Use process runtime fails for Android 10 and lower
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    )
    val stripUnusedNativeLibs = booleanPreference("strip_unused_native_libs", false)
    val patcherProcessMemoryLimit = intPreference("process_runtime_memory_limit", 1500)
    val patchedAppExportFormat = stringPreference(
        "patched_app_export_format",
        ExportNameFormatter.DEFAULT_TEMPLATE
    )
    val officialBundleRemoved = booleanPreference("official_bundle_removed", false)
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
    val disableSelectionWarning = booleanPreference("disable_selection_warning", false)
    val disableUniversalPatchCheck = booleanPreference("disable_patch_universal_check", true)
    val suggestedVersionSafeguard = booleanPreference("suggested_version_safeguard", true)
    val disablePatchSelectionConfirmations = booleanPreference("disable_patch_selection_confirmations", false)

    val acknowledgedDownloaderPlugins = stringSetPreference("acknowledged_downloader_plugins", emptySet())

    val useMorpheHomeScreen = booleanPreference("use_morphe_home_screen", true)

    val useRootMode = booleanPreference("use_root_mode", false)

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
        const val BUNDLE_URL_STABLE = "https://raw.githubusercontent.com/HundEdFeteTree/HappyFunTest/refs/heads/main/bundles/test-stable-patches-bundle.json"
        const val BUNDLE_URL_LATEST = "https://raw.githubusercontent.com/HundEdFeteTree/HappyFunTest/refs/heads/main/bundles/test-latest-patches-bundle.json"

        fun getBundleUrl(usePrerelease: Boolean): String {
            // Latest will always be pre-release, or the latest stable
            // which may contain additional changes not published in dev release
            return if (usePrerelease) BUNDLE_URL_LATEST else BUNDLE_URL_STABLE
        }
    }

    @Serializable
    data class SettingsSnapshot(
        val dynamicColor: Boolean? = null,
        val pureBlackTheme: Boolean? = null,
        val customAccentColor: String? = null,
        val customThemeColor: String? = null,
        val stripUnusedNativeLibs: Boolean? = null,
        val theme: Theme? = null,
        val patchesBundleJsonUrl: String? = null,
        val useProcessRuntime: Boolean? = null,
        val patcherProcessMemoryLimit: Int? = null,
        val patchedAppExportFormat: String? = null,
        val officialBundleRemoved: Boolean? = null,
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
        val acknowledgedDownloaderPlugins: Set<String>? = null
    )

    suspend fun exportSettings() = SettingsSnapshot(
        dynamicColor = dynamicColor.get(),
        pureBlackTheme = pureBlackTheme.get(),
        customAccentColor = customAccentColor.get(),
        customThemeColor = customThemeColor.get(),
        stripUnusedNativeLibs = stripUnusedNativeLibs.get(),
        theme = theme.get(),
        patchesBundleJsonUrl = patchesBundleJsonUrl.get(),
        useProcessRuntime = useProcessRuntime.get(),
        patcherProcessMemoryLimit = patcherProcessMemoryLimit.get(),
        patchedAppExportFormat = patchedAppExportFormat.get(),
        officialBundleRemoved = officialBundleRemoved.get(),
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
        acknowledgedDownloaderPlugins = acknowledgedDownloaderPlugins.get()
    )

    suspend fun importSettings(snapshot: SettingsSnapshot) = edit {
        snapshot.dynamicColor?.let { dynamicColor.value = it }
        snapshot.pureBlackTheme?.let { pureBlackTheme.value = it }
        snapshot.customAccentColor?.let { customAccentColor.value = it }
        snapshot.customThemeColor?.let { customThemeColor.value = it }
        snapshot.stripUnusedNativeLibs?.let { stripUnusedNativeLibs.value = it }
        snapshot.theme?.let { theme.value = it }
        snapshot.patchesBundleJsonUrl?.let { patchesBundleJsonUrl.value = it }
        snapshot.useProcessRuntime?.let { useProcessRuntime.value = it }
        snapshot.patcherProcessMemoryLimit?.let { patcherProcessMemoryLimit.value = it }
        snapshot.patchedAppExportFormat?.let { patchedAppExportFormat.value = it }
        snapshot.officialBundleRemoved?.let { officialBundleRemoved.value = it }
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
        snapshot.acknowledgedDownloaderPlugins?.let { acknowledgedDownloaderPlugins.value = it }
    }

}

object InstallerPreferenceTokens {
    const val INTERNAL = ":internal:"
    const val SYSTEM = ":system:"
    const val ROOT = ":root:"
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
