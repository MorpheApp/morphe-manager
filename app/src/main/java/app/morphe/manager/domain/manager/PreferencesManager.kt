package app.morphe.manager.domain.manager

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import app.morphe.manager.BuildConfig
import app.morphe.manager.domain.manager.base.BasePreferencesManager
import app.morphe.manager.domain.manager.base.IntPreference
import app.morphe.manager.domain.manager.base.LongPreference
import app.morphe.manager.domain.repository.PatchBundleRepository.Companion.DEFAULT_SOURCE_UID
import app.morphe.manager.patcher.runtime.PROCESS_RUNTIME_MEMORY_NOT_SET
import app.morphe.manager.patcher.runtime.coerceMemoryLimit
import app.morphe.manager.patcher.runtime.initialMemoryLimit
import app.morphe.manager.ui.screen.shared.BackgroundType
import app.morphe.manager.ui.theme.Theme
import app.morphe.manager.ui.theme.ThemeStyle
import app.morphe.manager.ui.theme.UI_SCALE_DEFAULT
import app.morphe.manager.ui.theme.coerceToUiScale
import app.morphe.manager.ui.viewmodel.BundleSnapshot
import app.morphe.manager.ui.viewmodel.RandomInterval
import app.morphe.manager.util.ApkDownloadHelperContract
import app.morphe.manager.util.AppCardColorMode
import app.morphe.manager.util.isArmV7
import app.morphe.manager.util.tag
import app.morphe.manager.worker.UpdateCheckInterval
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable

/**
 * Parts of the manager settings a backup can carry or leave out. Each setting belongs to exactly
 * one, as [PreferencesManager.SettingsSnapshot.restrictedTo] spells out.
 */
enum class SettingsSection {
    APPEARANCE,
    HOME,
    PATCHING,
    UPDATES,
    SOURCES,

    /** Chosen patches and their options, carried next to the snapshot rather than in it. */
    PATCH_SELECTIONS
}

class PreferencesManager(
    private val context: Context
) : BasePreferencesManager(context, "settings") {

    // Appearance tab
    val backgroundType = enumPreference("background_type", BackgroundType.CIRCLES)
    val enableBackgroundParallax = booleanPreference("enable_background_parallax", true)
    val randomBackgroundInterval = enumPreference("random_background_interval", RandomInterval.ON_LAUNCH)

    /** Whether the hidden Matrix background has been found and taken. */
    val matrixBackgroundUnlocked = booleanPreference("matrix_background_unlocked", false)

    val pureBlackTheme = booleanPreference("pure_black_theme", false)
    val showGreetingPhrases = booleanPreference("show_greeting_phrases", true)

    /** The per-app badges carry the same news, so the banner is worth turning off. */
    val showRepatchNotice = booleanPreference("show_repatch_notice", true)
    val customAccentColor = stringPreference("custom_accent_color", "")
    val customThemeColor = stringPreference("custom_theme_color", "")
    val appCardColorMode = enumPreference("app_card_color_mode", AppCardColorMode.DEFAULT)
    val customAppCardColors = stringPreference("custom_app_card_colors", "")
    val theme = enumPreference("theme", Theme.SYSTEM)
    val themeStyle = enumPreference("theme_style", ThemeStyle.MORPHE)

    /** Display scale of the whole interface, applied on top of the system screen zoom. */
    val uiScale = floatPreference("ui_scale", UI_SCALE_DEFAULT)

    /** Guards the one-shot migration that folds the retired `dynamic_color` toggle into [themeStyle]. */
    private val themeStyleMigrated = booleanPreference("theme_style_migrated_v1", false)

    // Advanced tab
    val useManagerPrereleases = booleanPreference("manager_prereleases", false)

    /** UIDs of bundles that have prereleases (dev branch) enabled. Stored as strings. */
    val bundlePrereleasesEnabled = stringSetPreference("bundle_prereleases_enabled", emptySet())

    /** UIDs of bundles for which experimental app versions are preferred as the recommended target. */
    val bundleExperimentalVersionsEnabled = stringSetPreference("bundle_experimental_versions_enabled", emptySet())

    /**  Whether to send Android system notifications when updates are available in the background. */
    val backgroundUpdateNotifications = booleanPreference("background_update_notifications", false)

    /**  How often the background update check should run. */
    val updateCheckInterval = enumPreference("update_check_interval", UpdateCheckInterval.DAILY)

    /** Whether other apps may start a batch patch run through an intent. */
    val externalBatchPatchEnabled = booleanPreference("external_batch_patch_enabled", false)

    /** Packages allowed to start a batch patch run without a confirmation dialog. */
    val externalBatchPatchAllowlist = stringSetPreference("external_batch_patch_allowlist", emptySet())

    /** Tracks whether the POST_NOTIFICATIONS runtime permission dialog has already been shown at least once on first launch (Android 13+). */
    val notificationPermissionRequested = booleanPreference("notification_permission_requested", false)

    /** Tracks whether the battery optimization exclusion dialog has been shown at least once. */
    val batteryOptimizationRequested = booleanPreference("battery_optimization_requested", false)

    val useExpertMode = booleanPreference("use_expert_mode", false)

    val stripUnusedNativeLibs = booleanPreference("strip_unused_native_libs", false)

    // System tab
    val installerPrimary = stringPreference("installer_primary", InstallerPreferenceTokens.INTERNAL)
    val promptInstallerOnInstall = booleanPreference("prompt_installer_on_install", false)
    val installerCustomComponents = stringSetPreference("installer_custom_components", emptySet())
    val installerHiddenComponents = stringSetPreference("installer_hidden_components", emptySet())

    /** Installs the patched APK as soon as patching completes. */
    val autoInstallAfterPatching = booleanPreference(
        "auto_install_with_shizuku", // Old key from when Shizuku was the only installer that could
        false
    )
    val autoUninstallWithShizuku = booleanPreference("auto_uninstall_with_shizuku", false)

    val useProcessRuntime = booleanPreference(
        "process_runtime", // Old key was 'use_process_runtime' and may have the wrong default for some devices.
        // Process runtime fails for Android 10 and lower.
        // ARMv7 silently fails and nobody has researched why.
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !isArmV7()
    )
    val patcherProcessMemoryLimit = IntPreference(dataStore, "use_process_runtime_memory_limit", PROCESS_RUNTIME_MEMORY_NOT_SET)

    /** Whether the last patcher process came up without the heap limit it asked for. Tied to the device, so never exported. */
    val patcherHeapLimitIgnored = booleanPreference("patcher_heap_limit_ignored", false)

    /**
     * Whether changelogs and patch descriptions show in the app language. Needs the translation
     * model on this device, so never exported.
     */
    val translateContent = booleanPreference("translate_content", false)

    val keystoreAlias = stringPreference("keystore_alias", KeystoreManager.DEFAULT)
    val keystorePass = stringPreference("keystore_pass", KeystoreManager.DEFAULT)
    val keystorePassword = stringPreference("keystore_password", "")

    // Other hidden settings
    val gitHubPat = stringPreference("github_pat", "")
    val includeGitHubPatInExports = booleanPreference("include_github_pat_in_exports", false)

    val allowMeteredUpdates = booleanPreference("allow_metered_updates", true)
    val firstLaunch = booleanPreference("first_launch", true)

    val installationTime = LongPreference(dataStore, "manager_installation_time", 0L)
    val disablePatchVersionCompatCheck = booleanPreference("disable_patch_version_compatibility_check", false)

    val useCustomFilePicker = booleanPreference("use_custom_file_picker", false)

    /** Packages of the third-party APK download helpers the user trusts to be offered. */
    val trustedApkDownloadHelpers = stringSetPreference("trusted_apk_download_helpers", emptySet())

    val lastFilePickerPath = stringPreference("last_file_picker_path", "")
    val filePickerSortMode = stringPreference("file_picker_sort_mode", "NAME_ASC")
    val filePickerShowHiddenFiles = booleanPreference("file_picker_show_hidden_files", false)

    /** Persist the pre-patch APK so it can be reused for repatching without asking the user to reselect it. */
    val saveOriginalApks = booleanPreference("save_original_apks", true)

    /** Persist the post-patch APK so it can be exported or reinstalled after the patcher screen is closed. */
    val savePatchedApks = booleanPreference("save_patched_apks", true)

    /** Play a distinct notification sound when patching finishes. */
    val patcherCompletionSound = booleanPreference("patcher_completion_sound", true)

    /** URI of a user-picked success tone. Empty falls back to the bundled `R.raw.success`. */
    val patcherSuccessSoundUri = stringPreference("patcher_success_sound_uri", "")

    /** URI of a user-picked failure tone. Empty falls back to the bundled `R.raw.error`. */
    val patcherErrorSoundUri = stringPreference("patcher_error_sound_uri", "")

    /** Patch source list ordering mode */
    val sourceBundleSortMode = stringPreference("source_bundle_sort_mode", SourceBundleSortMode.MANUAL.name)

    /** On-disk cache of the remote source blocklist (JSON), so offline launches still enforce it. */
    val blocklistCache = stringPreference("blocklist_cache", "")

    /** Tracks whether the user has explicitly toggled the custom file picker preference. */
    val customFilePickerUserConfigured = booleanPreference("custom_file_picker_user_configured", false)

    // Mini-game high scores
    val miniGame2048HighScore   = intPreference("mini_game_2048_high_score", 0)
    val miniGameFlappyHighScore = intPreference("mini_game_flappy_high_score", 0)
    val miniGameSnakeHighScore  = intPreference("mini_game_snake_high_score", 0)
    val miniGameDinoHighScore   = intPreference("mini_game_dino_high_score", 0)
    val miniGameBlocksHighScore = intPreference("mini_game_blocks_high_score", 0)
    val miniGameBricksHighScore = intPreference("mini_game_bricks_high_score", 0)
    val miniGameMinerHighScore  = intPreference("mini_game_miner_high_score", 0)
    val miniGamePairsHighScore  = intPreference("mini_game_pairs_high_score", 0)

    /** Set once the user has found the way back to a mini-game, which retires the hint for it. */
    val backToGameHintSeen = booleanPreference("back_to_game_hint_seen", false)

    /**  Hidden preference to track if prerelease was auto-enabled. */
    private val prereleaseAutoEnabled = booleanPreference("prerelease_auto_enabled", false)

    init {
        runBlocking {
            if (installationTime.get() == 0L) {
                val now = System.currentTimeMillis()
                installationTime.update(now)
                Log.d(tag, "Installation time set to $now")
            }

            // Initialize process memory limit adaptively on first launch
            if (patcherProcessMemoryLimit.get() == PROCESS_RUNTIME_MEMORY_NOT_SET) {
                val adaptive = initialMemoryLimit(context)
                Log.d(tag, "Initializing process memory limit to $adaptive MB (device RAM-based)")
                patcherProcessMemoryLimit.update(adaptive)
            }

            // Existing installs stored their Material You preference in `dynamic_color`;
            // fold it into [themeStyle] once so the new selector reflects the user's choice
            if (!themeStyleMigrated.get()) {
                val raw = dataStore.data.first()
                val legacyDynamicColor = raw[booleanPreferencesKey("dynamic_color")] ?: true
                if (legacyDynamicColor && themeStyle.get() == ThemeStyle.MORPHE) {
                    themeStyle.update(ThemeStyle.MATERIAL_YOU)
                }
                themeStyleMigrated.update(true)
            }

            // Helpers used to share a single switch; whoever had it on keeps every helper it let in
            val legacyHelperKey = booleanPreferencesKey("use_apk_download_helper")
            dataStore.data.first()[legacyHelperKey]?.let { legacyEnabled ->
                if (legacyEnabled) trustedApkDownloadHelpers.update(installedApkDownloadHelpers())
                dataStore.edit { it.remove(legacyHelperKey) }
            }

            // Auto-enable prereleases for dev versions
            if (isDevVersion() && !prereleaseAutoEnabled.get()) {
                Log.d(tag, "Dev version detected (${BuildConfig.VERSION_NAME}), auto-enabling prereleases")
                edit {
                    useManagerPrereleases.value = true
                    bundlePrereleasesEnabled += DEFAULT_SOURCE_UID.toString()
                    prereleaseAutoEnabled.value = true
                }
            }
        }
    }

    @Serializable
    data class SettingsSnapshot(
        val dynamicColor: Boolean? = null,
        val pureBlackTheme: Boolean? = null,
        val customAccentColor: String? = null,
        val customThemeColor: String? = null,
        val appCardColorMode: AppCardColorMode? = null,
        val customAppCardColors: String? = null,
        val stripUnusedNativeLibs: Boolean? = null,
        val theme: Theme? = null,
        val themeStyle: ThemeStyle? = null,
        val uiScale: Float? = null,
        val appLanguage: String? = null,
        val gitHubPat: String? = null,
        val includeGitHubPatInExports: Boolean? = null,
        val useProcessRuntime: Boolean? = null,
        val patcherProcessMemoryLimit: Int? = null,
        val allowMeteredUpdates: Boolean? = null,
        val installerPrimary: String? = null,
        val installerCustomComponents: Set<String>? = null,
        val installerHiddenComponents: Set<String>? = null,
        val keystoreAlias: String? = null,
        val keystorePass: String? = null,
        val keystorePassword: String? = null,
        val firstLaunch: Boolean? = null,
        val useManagerPrereleases: Boolean? = null,
        val officialBundlePrerelease: Boolean? = null,
        val officialBundleExperimentalVersions: Boolean? = null,
        val bundlePrereleasesEnabled: Set<String>? = null,
        val bundleExperimentalVersionsEnabled: Set<String>? = null,
        val disablePatchVersionCompatCheck: Boolean? = null,
        val showGreetingPhrases: Boolean? = null,
        val showRepatchNotice: Boolean? = null,
        val backgroundType: BackgroundType? = null,
        val randomBackgroundInterval: RandomInterval? = null,
        val matrixBackgroundUnlocked: Boolean? = null,
        val useExpertMode: Boolean? = null,
        val updateCheckInterval: UpdateCheckInterval? = null,
        val externalBatchPatchEnabled: Boolean? = null,
        val externalBatchPatchAllowlist: Set<String>? = null,
        val customBundles: List<BundleSnapshot>? = null,
        val filePickerSortMode: String? = null,
        val filePickerShowHiddenFiles: Boolean? = null,
        val useCustomFilePicker: Boolean? = null,
        val customFilePickerUserConfigured: Boolean? = null,
        /** Legacy all-or-nothing helper switch, read only from older exports. */
        val useApkDownloadHelper: Boolean? = null,
        val trustedApkDownloadHelpers: Set<String>? = null,
        val sourceBundleSortMode: String? = null,
        val saveOriginalApks: Boolean? = null,
        val savePatchedApks: Boolean? = null,
        val patcherCompletionSound: Boolean? = null,
        val patcherSuccessSoundUri: String? = null,
        val patcherErrorSoundUri: String? = null,
        val homeAppButtons: HomeAppButtonSnapshot? = null
    ) {
        /**
         * This snapshot with only the settings of [sections] left in it. Import skips whatever is
         * null, so the same cut serves an export that leaves a section out and an import that
         * declines one the file carries.
         */
        fun restrictedTo(sections: Set<SettingsSection>): SettingsSnapshot {
            val appearance = SettingsSection.APPEARANCE in sections
            val home = SettingsSection.HOME in sections
            val patching = SettingsSection.PATCHING in sections
            val updates = SettingsSection.UPDATES in sections
            val sources = SettingsSection.SOURCES in sections
            return SettingsSnapshot(
                dynamicColor = dynamicColor.takeIf { appearance },
                pureBlackTheme = pureBlackTheme.takeIf { appearance },
                customAccentColor = customAccentColor.takeIf { appearance },
                customThemeColor = customThemeColor.takeIf { appearance },
                appCardColorMode = appCardColorMode.takeIf { appearance },
                customAppCardColors = customAppCardColors.takeIf { appearance },
                stripUnusedNativeLibs = stripUnusedNativeLibs.takeIf { patching },
                theme = theme.takeIf { appearance },
                themeStyle = themeStyle.takeIf { appearance },
                uiScale = uiScale.takeIf { appearance },
                appLanguage = appLanguage.takeIf { appearance },
                gitHubPat = gitHubPat.takeIf { sources },
                includeGitHubPatInExports = includeGitHubPatInExports.takeIf { sources },
                useProcessRuntime = useProcessRuntime.takeIf { patching },
                patcherProcessMemoryLimit = patcherProcessMemoryLimit.takeIf { patching },
                allowMeteredUpdates = allowMeteredUpdates.takeIf { updates },
                installerPrimary = installerPrimary.takeIf { patching },
                installerCustomComponents = installerCustomComponents.takeIf { patching },
                installerHiddenComponents = installerHiddenComponents.takeIf { patching },
                keystoreAlias = keystoreAlias.takeIf { patching },
                keystorePass = keystorePass.takeIf { patching },
                keystorePassword = keystorePassword.takeIf { patching },
                firstLaunch = firstLaunch.takeIf { home },
                useManagerPrereleases = useManagerPrereleases.takeIf { updates },
                officialBundlePrerelease = officialBundlePrerelease.takeIf { sources },
                officialBundleExperimentalVersions = officialBundleExperimentalVersions.takeIf { sources },
                bundlePrereleasesEnabled = bundlePrereleasesEnabled.takeIf { sources },
                bundleExperimentalVersionsEnabled = bundleExperimentalVersionsEnabled.takeIf { sources },
                disablePatchVersionCompatCheck = disablePatchVersionCompatCheck.takeIf { patching },
                showGreetingPhrases = showGreetingPhrases.takeIf { home },
                showRepatchNotice = showRepatchNotice.takeIf { home },
                backgroundType = backgroundType.takeIf { appearance },
                randomBackgroundInterval = randomBackgroundInterval.takeIf { appearance },
                matrixBackgroundUnlocked = matrixBackgroundUnlocked.takeIf { appearance },
                useExpertMode = useExpertMode.takeIf { patching },
                updateCheckInterval = updateCheckInterval.takeIf { updates },
                externalBatchPatchEnabled = externalBatchPatchEnabled.takeIf { patching },
                externalBatchPatchAllowlist = externalBatchPatchAllowlist.takeIf { patching },
                customBundles = customBundles.takeIf { sources },
                filePickerSortMode = filePickerSortMode.takeIf { patching },
                filePickerShowHiddenFiles = filePickerShowHiddenFiles.takeIf { patching },
                useCustomFilePicker = useCustomFilePicker.takeIf { patching },
                customFilePickerUserConfigured = customFilePickerUserConfigured.takeIf { patching },
                useApkDownloadHelper = useApkDownloadHelper.takeIf { patching },
                trustedApkDownloadHelpers = trustedApkDownloadHelpers.takeIf { patching },
                sourceBundleSortMode = sourceBundleSortMode.takeIf { sources },
                saveOriginalApks = saveOriginalApks.takeIf { patching },
                savePatchedApks = savePatchedApks.takeIf { patching },
                patcherCompletionSound = patcherCompletionSound.takeIf { patching },
                patcherSuccessSoundUri = patcherSuccessSoundUri.takeIf { patching },
                patcherErrorSoundUri = patcherErrorSoundUri.takeIf { patching },
                homeAppButtons = homeAppButtons.takeIf { home }
            )
        }
    }

    suspend fun exportSettings() = SettingsSnapshot(
        dynamicColor = themeStyle.get() == ThemeStyle.MATERIAL_YOU,
        pureBlackTheme = pureBlackTheme.get(),
        customAccentColor = customAccentColor.get(),
        customThemeColor = customThemeColor.get(),
        appCardColorMode = appCardColorMode.get(),
        customAppCardColors = customAppCardColors.get(),
        stripUnusedNativeLibs = stripUnusedNativeLibs.get(),
        theme = theme.get(),
        themeStyle = themeStyle.get(),
        uiScale = uiScale.get(),
        gitHubPat = gitHubPat.get().takeIf { includeGitHubPatInExports.get() },
        includeGitHubPatInExports = includeGitHubPatInExports.get(),
        useProcessRuntime = useProcessRuntime.get(),
        patcherProcessMemoryLimit = patcherProcessMemoryLimit.get(),
        allowMeteredUpdates = allowMeteredUpdates.get(),
        installerPrimary = installerPrimary.get(),
        installerCustomComponents = installerCustomComponents.get(),
        installerHiddenComponents = installerHiddenComponents.get(),
        firstLaunch = firstLaunch.get(),
        useManagerPrereleases = useManagerPrereleases.get(),
        // Custom sources carry prerelease/experimental toggles in customBundles, and the official
        // source (UID 0) gets readable booleans below - so raw UID sets are no longer exported
        officialBundlePrerelease = bundlePrereleasesEnabled.get().contains(DEFAULT_SOURCE_UID.toString()),
        officialBundleExperimentalVersions =
            bundleExperimentalVersionsEnabled.get().contains(DEFAULT_SOURCE_UID.toString()),
        disablePatchVersionCompatCheck = disablePatchVersionCompatCheck.get(),
        showGreetingPhrases = showGreetingPhrases.get(),
        showRepatchNotice = showRepatchNotice.get(),
        backgroundType = backgroundType.get(),
        randomBackgroundInterval = randomBackgroundInterval.get(),
        matrixBackgroundUnlocked = matrixBackgroundUnlocked.get(),
        useExpertMode = useExpertMode.get(),
        updateCheckInterval = updateCheckInterval.get(),
        externalBatchPatchEnabled = externalBatchPatchEnabled.get(),
        externalBatchPatchAllowlist = externalBatchPatchAllowlist.get(),
        filePickerSortMode = filePickerSortMode.get(),
        filePickerShowHiddenFiles = filePickerShowHiddenFiles.get(),
        useCustomFilePicker = useCustomFilePicker.get(),
        customFilePickerUserConfigured = customFilePickerUserConfigured.get(),
        trustedApkDownloadHelpers = trustedApkDownloadHelpers.get(),
        sourceBundleSortMode = sourceBundleSortMode.get(),
        saveOriginalApks = saveOriginalApks.get(),
        savePatchedApks = savePatchedApks.get(),
        patcherCompletionSound = patcherCompletionSound.get(),
        patcherSuccessSoundUri = patcherSuccessSoundUri.get(),
        patcherErrorSoundUri = patcherErrorSoundUri.get()
    )

    suspend fun importSettings(snapshot: SettingsSnapshot) = edit {
        snapshot.pureBlackTheme?.let { pureBlackTheme.value = it }
        snapshot.customAccentColor?.let { customAccentColor.value = it }
        snapshot.customThemeColor?.let { customThemeColor.value = it }
        snapshot.appCardColorMode?.let { appCardColorMode.value = it }
        snapshot.customAppCardColors?.let { customAppCardColors.value = it }
        snapshot.stripUnusedNativeLibs?.let { stripUnusedNativeLibs.value = it }
        snapshot.theme?.let { theme.value = it }
        snapshot.themeStyle?.let { themeStyle.value = it }
            ?: run {
                // Pre-migration snapshots only carry `dynamicColor`; treat it as the missing style
                if (snapshot.dynamicColor == true) themeStyle.value = ThemeStyle.MATERIAL_YOU
            }
        // Snapped rather than taken as-is, so a scale from a build with a different range still fits
        snapshot.uiScale?.let { uiScale.value = it.coerceToUiScale() }
        snapshot.gitHubPat?.let { gitHubPat.value = it }
        snapshot.includeGitHubPatInExports?.let { includeGitHubPatInExports.value = it }
        snapshot.useProcessRuntime?.let { useProcessRuntime.value = it }
        // Clamped rather than taken as-is, so a limit exported from a roomier device still fits
        snapshot.patcherProcessMemoryLimit?.let {
            patcherProcessMemoryLimit.value = coerceMemoryLimit(context, it)
        }
        snapshot.allowMeteredUpdates?.let { allowMeteredUpdates.value = it }
        snapshot.installerPrimary?.let { installerPrimary.value = it }
        snapshot.installerCustomComponents?.let { installerCustomComponents.value = it }
        snapshot.installerHiddenComponents?.let { installerHiddenComponents.value = it }
        snapshot.keystoreAlias?.let { keystoreAlias.value = it }
        snapshot.keystorePass?.let { keystorePass.value = it }
        snapshot.keystorePassword?.let { keystorePassword.value = it }
        snapshot.firstLaunch?.let { firstLaunch.value = it }
        snapshot.useManagerPrereleases?.let { useManagerPrereleases.value = it }
        // Exports carry the official source's toggles as readable booleans; older ones only have
        // the UID sets, where the official source is the one UID that is stable across devices
        val officialUid = DEFAULT_SOURCE_UID.toString()
        val officialPrerelease = snapshot.officialBundlePrerelease
            ?: snapshot.bundlePrereleasesEnabled?.contains(officialUid)
        val officialExperimental = snapshot.officialBundleExperimentalVersions
            ?: snapshot.bundleExperimentalVersionsEnabled?.contains(officialUid)
        officialPrerelease?.let { enabled ->
            val current = bundlePrereleasesEnabled.value.toMutableSet()
            if (enabled) current.add(officialUid) else current.remove(officialUid)
            bundlePrereleasesEnabled.value = current
        }
        officialExperimental?.let { enabled ->
            val current = bundleExperimentalVersionsEnabled.value.toMutableSet()
            if (enabled) current.add(officialUid) else current.remove(officialUid)
            bundleExperimentalVersionsEnabled.value = current
        }
        snapshot.disablePatchVersionCompatCheck?.let { disablePatchVersionCompatCheck.value = it }
        snapshot.showGreetingPhrases?.let { showGreetingPhrases.value = it }
        snapshot.showRepatchNotice?.let { showRepatchNotice.value = it }
        snapshot.backgroundType?.let { backgroundType.value = it }
        snapshot.randomBackgroundInterval?.let { randomBackgroundInterval.value = it }
        snapshot.matrixBackgroundUnlocked?.let { matrixBackgroundUnlocked.value = it }
        snapshot.useExpertMode?.let { useExpertMode.value = it }
        snapshot.updateCheckInterval?.let { updateCheckInterval.value = it }
        snapshot.externalBatchPatchEnabled?.let { externalBatchPatchEnabled.value = it }
        snapshot.externalBatchPatchAllowlist?.let { externalBatchPatchAllowlist.value = it }
        snapshot.filePickerSortMode?.let { filePickerSortMode.value = it }
        snapshot.filePickerShowHiddenFiles?.let { filePickerShowHiddenFiles.value = it }
        snapshot.useCustomFilePicker?.let { useCustomFilePicker.value = it }
        snapshot.customFilePickerUserConfigured?.let { customFilePickerUserConfigured.value = it }
        snapshot.trustedApkDownloadHelpers?.let { trustedApkDownloadHelpers.value = it }
            ?: run {
                if (snapshot.useApkDownloadHelper == true) {
                    trustedApkDownloadHelpers.value = installedApkDownloadHelpers()
                }
            }
        snapshot.sourceBundleSortMode?.let { sourceBundleSortMode.value = it }
        snapshot.saveOriginalApks?.let { saveOriginalApks.value = it }
        snapshot.savePatchedApks?.let { savePatchedApks.value = it }
        snapshot.patcherCompletionSound?.let { patcherCompletionSound.value = it }
        snapshot.patcherSuccessSoundUri?.let { patcherSuccessSoundUri.value = it }
        snapshot.patcherErrorSoundUri?.let { patcherErrorSoundUri.value = it }
    }

    private fun installedApkDownloadHelpers() =
        ApkDownloadHelperContract.findHelpers(context).mapTo(mutableSetOf()) { it.componentName.packageName }

    companion object {
        /** Check if current version is a development/prerelease version. */
        fun isDevVersion(): Boolean {
            return BuildConfig.VERSION_NAME.contains("-dev", ignoreCase = true)
        }
    }
}

object InstallerPreferenceTokens {
    const val INTERNAL = ":internal:"
    const val SYSTEM = ":system:"
    const val ROOT = ":root:" // Legacy value, mapped to AUTO_SAVED
    const val AUTO_SAVED = ":auto_saved:"
    const val PLAY_STORE = ":play_store:"
    const val ROOT_PLAY_STORE = ":root_play_store:"
    const val SHIZUKU = ":shizuku:"
    const val SHIZUKU_PLAY_STORE = ":shizuku_play_store:"
    const val NONE = ":none:"
}
