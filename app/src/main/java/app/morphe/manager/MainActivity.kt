/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 *
 * Original hard forked code:
 * https://github.com/Jman-Github/Universal-ReVanced-Manager/blob/597b3173a004f5a9aae54326046dd7fd4c5b7777/app/src/main/java/app/revanced/manager/MainActivity.kt
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.manager

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.morphe.manager.domain.batch.BatchTarget
import app.morphe.manager.domain.manager.HomeAppButtonPreferences
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.ui.model.SourceScope
import app.morphe.manager.ui.model.navigation.*
import app.morphe.manager.ui.screen.BatchPatcherScreen
import app.morphe.manager.ui.screen.HomeScreen
import app.morphe.manager.ui.screen.PatcherScreen
import app.morphe.manager.ui.screen.home.*
import app.morphe.manager.ui.screen.settings.SettingsNavigation
import app.morphe.manager.ui.screen.settings.SettingsSubpage
import app.morphe.manager.ui.screen.settings.SettingsSubpageScreen
import app.morphe.manager.ui.screen.settings.system.LicensesPage
import app.morphe.manager.ui.screen.settings.system.PatchSelectionsPage
import app.morphe.manager.ui.screen.shared.AnimatedBackground
import app.morphe.manager.ui.screen.shared.Animations
import app.morphe.manager.ui.screen.shared.LocalBackdropHaze
import app.morphe.manager.ui.screen.shared.BackgroundType
import app.morphe.manager.ui.screen.shared.DialogHost
import app.morphe.manager.ui.screen.shared.MainPage
import app.morphe.manager.ui.screen.shared.MessageHost
import app.morphe.manager.ui.screen.shared.RandomBackground
import app.morphe.manager.ui.screen.shared.UserMessages
import app.morphe.manager.ui.screen.shared.rememberRandomBackground
import app.morphe.manager.ui.screen.shared.sharedAxisZEnter
import app.morphe.manager.ui.screen.shared.sharedAxisZExit
import app.morphe.manager.ui.screen.sources.BundleChangelogHost
import app.morphe.manager.ui.screen.sources.BundleChangelogRequest
import app.morphe.manager.ui.theme.*
import app.morphe.manager.ui.viewmodel.HomeViewModel
import app.morphe.manager.ui.viewmodel.MainViewModel
import app.morphe.manager.ui.viewmodel.PatcherViewModel
import app.morphe.manager.util.*
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.context.GlobalContext
import org.koin.core.parameter.parametersOf
import org.koin.androidx.viewmodel.ext.android.getViewModel as getActivityViewModel

class MainActivity : AppCompatActivity() {

    /** Language the activity was attached in, to tell when it has to be recreated. */
    private var attachedLanguage = AppLocale.SYSTEM

    /**
     * Applies the interface scale to the activity context, so every window it opens is drawn at
     * that scale rather than only the composition inside [setContent].
     *
     * On Android 12 and lower the app language is applied here too, since Android has no per-app
     * language there. Both go into one configuration delta, because a context created from
     * another does not keep the overrides the first one was created with.
     */
    override fun attachBaseContext(newBase: Context) {
        // Koin is started in Application.onCreate, which has already run by the time an activity
        // attaches. A scale that cannot be read must not take the launch down with it
        val scale = runCatching {
            GlobalContext.get().get<PreferencesManager>().uiScale.getBlocking()
        }.getOrDefault(UI_SCALE_DEFAULT)

        attachedLanguage = AppLocale.selected.value
        val overrides = Configuration().apply {
            AppLocale.applyTo(this)
            applyUiScale(newBase.resources.configuration.densityDpi, scale)
        }

        super.attachBaseContext(
            if (overrides == Configuration()) newBase else newBase.createConfigurationContext(overrides)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge()
        installSplashScreen()

        // Android 13+ recreates the activity by itself when the app language changes
        if (!AppLocale.appliedBySystem) {
            lifecycleScope.launch {
                AppLocale.selected.first { it != attachedLanguage }
                recreate()
            }
        }

        val vm: MainViewModel = getActivityViewModel()

        // A recreated activity keeps the background RANDOM drew, only a fresh launch draws again
        if (savedInstanceState == null) RandomBackground.newLaunch()

        // Handle deep link on cold start. The task keeps the intent that started it, so a restore
        // after the process was reclaimed - or any recreate - would replay a link already acted on
        if (savedInstanceState == null) {
            handleDeepLinkIntent(intent, vm)
        }

        setContent {
            val theme by vm.prefs.theme.getAsState()
            val themeStyle by vm.prefs.themeStyle.getAsState()
            val pureBlackTheme by vm.prefs.pureBlackTheme.getAsState()
            val colorAccents by vm.prefs.colorAccents.getAsState()
            val outlines by vm.prefs.outlines.getAsState()
            val blur by vm.prefs.blur.getAsState()
            val customAccentColor by vm.prefs.customAccentColor.getAsState()
            val customThemeColor by vm.prefs.customThemeColor.getAsState()
            val appCardColorMode by vm.prefs.appCardColorMode.getAsState()
            val customAppCardColors by vm.prefs.customAppCardColors.getAsState()
            val appCardColorValues = remember(customAppCardColors) {
                AppCardColorDefaults.decodeColorValues(customAppCardColors)
            }
            val supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            val effectiveThemeStyle = resolveThemeStyle(themeStyle, supportsDynamicColor)
            val darkTheme = when (theme) {
                Theme.LIGHT -> false
                Theme.DARK -> true
                Theme.SYSTEM -> isSystemInDarkTheme()
            }

            ManagerTheme(
                darkTheme = darkTheme,
                dynamicColor = effectiveThemeStyle == ThemeStyle.MATERIAL_YOU,
                pureBlackTheme = pureBlackTheme,
                traits = ThemeTraits(
                    monochrome = effectiveThemeStyle == ThemeStyle.MONOCHROME,
                    colorAccents = colorAccents,
                    outlines = outlines,
                    blur = blur
                ),
                accentColorHex = customAccentColor.takeUnless { it.isBlank() },
                themeColorHex = customThemeColor.takeUnless { it.isBlank() },
                appCardColorMode = appCardColorMode,
                appCardColorValues = appCardColorValues
            ) {
                MessageHost(modifier = Modifier.fillMaxSize()) {
                    MorpheManager(vm)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Replaces the launch intent, so a later recreate restores this one rather than the
        // link the task was originally started from
        setIntent(intent)
        handleDeepLinkIntent(intent, getActivityViewModel())
    }

    /**
     * Tells the launcher a shortcut was used, so it can rank the list by use. The id is dropped
     * once reported, or the same activation counts again when the activity is recreated.
     */
    private fun reportShortcutUsage(intent: Intent?) {
        val shortcutId = intent?.getStringExtra(EXTRA_SHORTCUT_ID) ?: return
        intent.removeExtra(EXTRA_SHORTCUT_ID)
        runCatching { ShortcutManagerCompat.reportShortcutUsed(this, shortcutId) }
            .onFailure { Log.w(tag, "Failed to report shortcut usage", it) }
    }

    /**
     * Handles add-source deep links from an explicit-package `intent://` fired by the website.
     * Format: https://morphe.software/add-source?<github|gitlab>=owner/repo(&name=…)(&app=package&appName=…)
     * Only GitHub and GitLab URLs are accepted.
     */
    private fun handleDeepLinkIntent(intent: Intent?, vm: MainViewModel) {
        reportShortcutUsage(intent)

        // Handled here rather than in onNewIntent so a cold start from a notification or a
        // launcher shortcut triggers the check as well
        if (intent?.getBooleanExtra(UpdateNotificationManager.EXTRA_TRIGGER_UPDATE_CHECK, false) == true) {
            vm.pendingUpdateCheck = true
            return
        }

        // Changelog action of an update notification: checks for nothing on the way in, so the
        // notes are read against the installed version
        if (intent?.action == ACTION_SHOW_BUNDLE_CHANGELOG) {
            vm.pendingBundleChangelogUid = intent.getIntExtra(
                EXTRA_CHANGELOG_BUNDLE_UID,
                PatchBundleRepository.DEFAULT_SOURCE_UID
            )
            return
        }

        // Same action on a manager update notification. This one does resolve the release on
        // the way in, because the notes it shows belong to a version that is not installed yet
        if (intent?.action == ACTION_SHOW_MANAGER_CHANGELOG) {
            vm.pendingManagerChangelog = true
            return
        }

        // Batch result notification: reopens the queue it reports about
        if (intent?.action == ACTION_SHOW_BATCH_RESULT) {
            vm.pendingBatchResult = true
            return
        }

        // Launcher shortcut for one app: opens the usual patch dialog for it
        if (intent?.action == ACTION_PATCH_APP) {
            intent.getStringExtra(EXTRA_PATCH_PACKAGE)
                ?.takeIf { it.isNotBlank() }
                ?.let { vm.pendingPatchPackage = it }
            return
        }

        // Notification opening an app's info dialog (e.g. mount replaced)
        if (intent?.action == ACTION_SHOW_APP_INFO) {
            intent.getStringExtra(EXTRA_APP_INFO_PACKAGE)
                ?.takeIf { it.isNotBlank() }
                ?.let { vm.pendingAppInfoPackage = it }
            return
        }

        // Batch patch requested by another app, for example an automation tool.
        // The request is only recorded here; MorpheManager gates it before anything runs
        if (intent?.action == ACTION_BATCH_PATCH) {
            val packageNames = intent.getStringArrayExtra(EXTRA_BATCH_PACKAGES)?.toList()
                ?: intent.getStringExtra(EXTRA_BATCH_PACKAGES)
                    ?.split(',')
                    ?.map(String::trim)
                    ?.filter(String::isNotBlank)

            if (packageNames.isNullOrEmpty()) {
                // No list means the launcher shortcut or the re-patch notification, which
                // ask the app to work out what is outdated. Either only opens the preflight
                // list, so neither needs a caller gate
                vm.pendingOutdatedBatch = true
            } else {
                vm.pendingBatchPatch = MainViewModel.BatchPatchRequest(
                    // An external request names apps, so it rebuilds each one's own install
                    targets = packageNames.map { BatchTarget(it) },
                    // Only the system-supplied caller is trustworthy here. getReferrer() reads
                    // EXTRA_REFERRER, which the sender fills in itself, so it could name any
                    // package and both skip the confirmation and misname it in the dialog
                    callerPackage = callingPackage
                )
            }
            return
        }

        // Handle APK-family file shared via system share sheet (.apk/.apks/.xapk/.apkm).
        // The name is not checked: senders often drop or change the extension, and the file is
        // read for what it is anyway, with a message when it turns out not to be an APK
        if (intent?.action == Intent.ACTION_SEND) {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            uri?.let { vm.pendingExternalApkUri = it }
            return
        }

        val data = intent?.data ?: return

        // Handle .mpp file open from file manager
        if (intent.action == Intent.ACTION_VIEW && data.scheme in listOf("file", "content")) {
            if (data.hasMppExtension(contentResolver)) {
                vm.pendingMppUri = data
            }
            // Not .mpp - don't process further regardless
            return
        }

        val isAddSource = data.scheme == "https" &&
                data.host == "morphe.software" &&
                data.path?.startsWith(ADD_SOURCE_PATH) == true
        if (!isAddSource) return

        fun param(key: String) = data.getQueryParameter(key)?.takeIf { it.isNotBlank() }

        val url = param("github")?.let { "https://github.com/$it" }
            ?: param("gitlab")?.let { "https://gitlab.com/$it" }
            ?: return
        vm.pendingDeepLinkSource = MainViewModel.DeepLinkSource(
            url = url,
            name = param("name"),
            scope = SourceScope.fromLink(app = param("app"), appName = param("appName"))
        )
    }

    companion object {
        /** Action other apps use to queue a batch patch run. */
        const val ACTION_BATCH_PATCH = "app.morphe.manager.action.BATCH_PATCH"

        /** Package names to patch, either a string array or a comma-separated string. */
        const val EXTRA_BATCH_PACKAGES = "packages"

        /** Identifies the launcher shortcut an intent came from, for usage reporting. */
        const val EXTRA_SHORTCUT_ID = "shortcut_id"

        /** Action behind the per-app launcher shortcuts. */
        const val ACTION_PATCH_APP = "app.morphe.manager.action.PATCH_APP"

        /** Action behind the update check shortcut. Shortcut intents must carry one. */
        const val ACTION_CHECK_UPDATES = "app.morphe.manager.action.CHECK_UPDATES"

        /** Action that reopens the batch queue from an automatic re-patch notification. */
        const val ACTION_SHOW_BATCH_RESULT = "app.morphe.manager.action.SHOW_BATCH_RESULT"

        /** Action behind the changelog button of an update notification. */
        const val ACTION_SHOW_BUNDLE_CHANGELOG = "app.morphe.manager.action.SHOW_BUNDLE_CHANGELOG"

        /** Source whose changelog the update notification opens. */
        const val EXTRA_CHANGELOG_BUNDLE_UID = "changelog_bundle_uid"

        /** Action behind the changelog button of a manager update notification. */
        const val ACTION_SHOW_MANAGER_CHANGELOG = "app.morphe.manager.action.SHOW_MANAGER_CHANGELOG"

        /** Package the per-app shortcut opens the patch dialog for. */
        const val EXTRA_PATCH_PACKAGE = "patch_package"

        /** Action behind a notification opening an app's info dialog. */
        const val ACTION_SHOW_APP_INFO = "app.morphe.manager.action.SHOW_APP_INFO"

        /** Package the notification opens the info dialog for. */
        const val EXTRA_APP_INFO_PACKAGE = "app_info_package"
    }
}

@Composable
private fun MorpheManager(vm: MainViewModel) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val prefs: PreferencesManager = koinInject()
    val homeAppButtonPrefs: HomeAppButtonPreferences = koinInject()
    val backgroundType by prefs.backgroundType.getAsState()
    val enableParallax by prefs.enableBackgroundParallax.getAsState()
    val resolvedRandomBackground =
        if (backgroundType == BackgroundType.RANDOM) rememberRandomBackground(prefs) else null
    val seasonalThemes by prefs.seasonalThemes.getAsState()
    // Read once per launch, so an event that begins while the app is open waits for the next one
    val seasonalEvent = remember { SeasonalEvent.on() }

    // Patcher background speed - driven by PatcherViewModel when on patcher screen.
    // Exposed as top-level mutable state so PatcherScreen can write into it
    val patcherBackgroundSpeed = remember { mutableFloatStateOf(1f) }
    val patchingCompleted = remember { mutableStateOf(false) }

    // HomeViewModel must be scoped to the Activity, not to a NavBackStackEntry
    val homeViewModel: HomeViewModel = koinViewModel(
        viewModelStoreOwner = LocalActivity.current as ComponentActivity
    )

    // Handle deep link source
    LaunchedEffect(vm.pendingDeepLinkSource) {
        vm.pendingDeepLinkSource?.let { source ->
            navController.popBackStack(HomeScreen, false)
            homeViewModel.handleDeepLinkAddSource(source)
            vm.pendingDeepLinkSource = null
        }
    }

    // Handle .mpp file opened from file manager
    LaunchedEffect(vm.pendingMppUri) {
        vm.pendingMppUri?.let { uri ->
            navController.popBackStack(HomeScreen, false)
            homeViewModel.setPendingMpp(uri)
            vm.pendingMppUri = null
        }
    }

    // Gate an incoming external batch request, then run it once approved
    LaunchedEffect(vm.pendingBatchPatch) {
        vm.pendingBatchPatch?.let(vm::onExternalBatchRequest)
    }

    LaunchedEffect(vm.pendingOutdatedBatch) {
        if (vm.pendingOutdatedBatch) vm.onShortcutBatchRequest()
    }

    LaunchedEffect(vm.pendingBatchResult) {
        if (vm.pendingBatchResult) vm.onShowBatchResult()
    }

    // Changelog asked for from an update notification, shown over whichever screen is open
    val patchSources by homeViewModel.patchBundleRepository.sources.collectAsStateWithLifecycle()
    BundleChangelogHost(
        request = vm.pendingBundleChangelogUid?.let { BundleChangelogRequest(it) },
        sources = patchSources,
        onDismissRequest = { vm.pendingBundleChangelogUid = null }
    )

    // The manager has no changelog view of its own for a release it has not installed, so the
    // update dialog is the answer to "what's new": it lists exactly the entries being offered
    DialogHost(visible = vm.pendingManagerChangelog) {
        ManagerChangelogDialog(
            onDismiss = { vm.pendingManagerChangelog = false },
            expectsUpdate = true
        )
    }

    // Per-app shortcut reuses the trigger the installed-app dialog already goes through
    LaunchedEffect(vm.pendingPatchPackage) {
        vm.pendingPatchPackage?.let { packageName ->
            vm.pendingPatchPackage = null
            navController.popBackStack(HomeScreen, false)
            navController.getBackStackEntry(HomeScreen)
                .savedStateHandle["patch_trigger_package"] = packageName
        }
    }

    LaunchedEffect(vm.pendingAppInfoPackage) {
        vm.pendingAppInfoPackage?.let { packageName ->
            vm.pendingAppInfoPackage = null
            navController.popBackStack(HomeScreen, false)
            navController.getBackStackEntry(HomeScreen)
                .savedStateHandle["app_info_trigger_package"] = packageName
        }
    }

    val messages = koinInject<UserMessages>()
    val nothingToRepatchText = stringResource(R.string.batch_patch_nothing_outdated)
    LaunchedEffect(vm.nothingToRepatch) {
        if (vm.nothingToRepatch) {
            messages.show(nothingToRepatchText)
            vm.consumeNothingToRepatch()
        }
    }

    LaunchedEffect(vm.approvedBatchPatch) {
        vm.approvedBatchPatch?.let { request ->
            vm.consumeApprovedBatch()
            navController.popBackStack(HomeScreen, false)
            navController.navigateComplex(
                BatchPatcher,
                BatchPatcher.ViewModelParams(targets = request.targets)
            )
        }
    }

    DialogHost(target = vm.batchPatchConfirmation) { request ->
        ExternalBatchPatchDialog(
            callerPackage = request.callerPackage,
            packageCount = request.targets.size,
            onConfirm = { trustCaller -> vm.approveExternalBatch(trustCaller) },
            onDismiss = vm::dismissExternalBatch
        )
    }

    // Handle .apk file shared via share sheet
    LaunchedEffect(vm.pendingExternalApkUri) {
        vm.pendingExternalApkUri?.let { uri ->
            navController.popBackStack(HomeScreen, false)
            vm.pendingExternalApkUri = null
            homeViewModel.handleExternalApkUri(uri)
        }
    }

    // Onboarding tour, run over the main screen from its first step
    var showOnboarding by remember { mutableStateOf(false) }
    val onboardingState = remember { OnboardingState() }

    val onboardingSteps = remember(onboardingState) {
        // Only the first step plays the swipe on the card, so every other one stops it
        fun endSwipeHint() {
            onboardingState.swipeActive = false
            homeViewModel.apps.markSwipeGestureHintShown()
        }
        // Each turns to its page and row, so stepping back from another page lands on it too
        fun targetStep(titleRes: Int, descRes: Int, page: MainPage, target: TourTarget) = StepDef(
            titleRes, descRes,
            getBounds = { onboardingState.targetBounds[target] },
            onShow = {
                endSwipeHint()
                onboardingState.page = page
                onboardingState.target = target
            }
        )
        listOf(
            StepDef(
                R.string.onboarding_swipe_title, R.string.onboarding_swipe_desc,
                getBounds = { onboardingState.firstAppCardBounds },
                onShow = {
                    onboardingState.page = MainPage.APPS
                    onboardingState.swipeActive = true
                    homeViewModel.apps.triggerSwipeGestureHint()
                }
            ),
            targetStep(R.string.home_other_apps_action, R.string.onboarding_other_apps_desc, MainPage.APPS, TourTarget.OTHER_APPS),
            StepDef(
                R.string.sources_management_title, R.string.onboarding_sources_desc,
                getBounds = { onboardingState.navigationBounds[MainPage.SOURCES] },
                onShow = {
                    onboardingState.page = MainPage.APPS
                    endSwipeHint()
                }
            ),
            targetStep(R.string.sources_dialog_add_source, R.string.onboarding_add_source_desc, MainPage.SOURCES, TourTarget.ADD_SOURCE),
            targetStep(R.string.patches, R.string.onboarding_sources_patches_desc, MainPage.SOURCES, TourTarget.SOURCE_PATCHES),
            targetStep(R.string.version, R.string.onboarding_sources_version_desc, MainPage.SOURCES, TourTarget.SOURCE_VERSION),
            targetStep(R.string.sources_apps, R.string.onboarding_sources_apps_desc, MainPage.SOURCES, TourTarget.SOURCE_APPS),
            targetStep(
                R.string.sources_management_prerelease_toggle, R.string.onboarding_sources_prerelease_desc,
                MainPage.SOURCES, TourTarget.SOURCE_PRERELEASE
            ),
            StepDef(
                R.string.settings, R.string.onboarding_settings_desc,
                getBounds = { onboardingState.navigationBounds[MainPage.SETTINGS] }
            ),
            // In the order the settings page lists them, so the tour only ever moves down it
            targetStep(R.string.appearance, R.string.onboarding_appearance_tab_desc, MainPage.SETTINGS, TourTarget.APPEARANCE),
            targetStep(R.string.settings_advanced_expert_mode, R.string.onboarding_expert_mode_desc, MainPage.SETTINGS, TourTarget.EXPERT_MODE),
            targetStep(R.string.installer, R.string.onboarding_system_installer_desc, MainPage.SETTINGS, TourTarget.INSTALLER),
            targetStep(R.string.settings_system_custom_file_picker, R.string.onboarding_system_file_picker_desc, MainPage.SETTINGS, TourTarget.FILE_PICKER),
            targetStep(R.string.settings_system_signing_key, R.string.onboarding_signing_key_desc, MainPage.SETTINGS, TourTarget.SIGNING_KEY)
        )
    }

    // Every entry point starts the tour from its first step on the apps page
    val startOnboardingTour: () -> Unit = {
        // The tour points at app cards, which a saved filter could leave out of the list
        homeAppButtonPrefs.setFilterMode(null)
        showOnboarding = true
    }
    // Finishing and skipping both end the tour back on the apps
    val finishOnboardingTour: () -> Unit = {
        onboardingState.swipeActive = false
        homeViewModel.apps.markSwipeGestureHintShown()
        onboardingState.page = MainPage.APPS
        onboardingState.target = null
        showOnboarding = false
        scope.launch { prefs.firstLaunch.update(false) }
    }
    val declineOnboardingTour: () -> Unit = {
        scope.launch { prefs.firstLaunch.update(false) }
    }

    // Box with background at the highest level
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Show animated background
        AnimatedBackground(
            type = seasonalEvent.backgroundOver(backgroundType, seasonalThemes),
            resolvedType = resolvedRandomBackground,
            enableParallax = enableParallax,
            speedMultiplier = { patcherBackgroundSpeed.floatValue },
            patchingCompleted = { patchingCompleted.value }
        )

        // What the tour's card blurs, recorded only while the tour runs
        val tourHaze = rememberHazeState()

        // All content on top of background
        NavHost(
            navController = navController,
            startDestination = HomeScreen,
            modifier = if (showOnboarding) Modifier.hazeSource(tourHaze) else Modifier,
            // Every screen opens deeper than the main one along Material's shared Z axis, rather than
            // sideways, which the main screen's own pages already move. Back runs it in reverse, and
            // under the predictive gesture the screen leaving shrinks away instead
            enterTransition = { sharedAxisZEnter(forward = true) },
            exitTransition = { sharedAxisZExit(forward = true) },
            popEnterTransition = { sharedAxisZEnter(forward = false) },
            popExitTransition = { sharedAxisZExit(forward = false) },
            predictivePopEnterTransition = { sharedAxisZEnter(forward = false) },
            predictivePopExitTransition = { Animations.predictiveBackExit }
        ) {
            composable<HomeScreen> { entry ->
                val bundleUpdateProgress by homeViewModel.bundleUpdateProgress.collectAsStateWithLifecycle(null)
                val patchTriggerPackage by entry.savedStateHandle.getStateFlow<String?>("patch_trigger_package", null)
                    .collectAsStateWithLifecycle()
                val appInfoTriggerPackage by entry.savedStateHandle.getStateFlow<String?>("app_info_trigger_package", null)
                    .collectAsStateWithLifecycle()

                // If opened from an FCM notification, trigger an update check.
                // vm.pendingUpdateCheck is set in onNewIntent() and reset here after handling.
                LaunchedEffect(vm.pendingUpdateCheck) {
                    if (vm.pendingUpdateCheck) {
                        homeViewModel.patchBundleRepository.updateCheck()
                        homeViewModel.checkForManagerUpdates()
                        vm.pendingUpdateCheck = false
                    }
                }

                HomeScreen(
                    settingsNavigation = remember(navController, showOnboarding) {
                        navController.settingsNavigation(startTour = startOnboardingTour.takeUnless { showOnboarding })
                    },
                    onboardingState = onboardingState.takeIf { showOnboarding },
                    onStartQuickPatch = { params ->
                        entry.lifecycleScope.launch {
                            navController.navigateComplex(Patcher, params)
                        }
                    },
                    onStartBatchPatch = { targets ->
                        entry.lifecycleScope.launch {
                            navController.navigateComplex(BatchPatcher, BatchPatcher.ViewModelParams(targets))
                        }
                    },
                    homeViewModel = homeViewModel,
                    bundleUpdateProgress = bundleUpdateProgress,
                    patchTriggerPackage = patchTriggerPackage,
                    onPatchTriggerHandled = {
                        entry.savedStateHandle["patch_trigger_package"] = null
                    },
                    appInfoTriggerPackage = appInfoTriggerPackage,
                    onAppInfoTriggerHandled = {
                        entry.savedStateHandle["app_info_trigger_package"] = null
                    }
                )
            }

            composable<BatchPatcher> { entry ->
                val params = entry.getComplexArg<BatchPatcher.ViewModelParams>() ?: return@composable
                BatchPatcherScreen(
                    targets = params.targets,
                    onBackClick = {
                        patchingCompleted.value = false
                        navController.popBackStack()
                    },
                    onStartTour = startOnboardingTour,
                    onDeclineTour = declineOnboardingTour,
                    onAppStateChanged = homeViewModel.apps::notifyAppStateChanged,
                    onBackgroundSpeedChange = { patcherBackgroundSpeed.floatValue = it },
                    onPatchingCompleted = { patchingCompleted.value = true }
                )
            }

            composable<Patcher> { entry ->
                val params = entry.getComplexArg<Patcher.ViewModelParams>() ?: return@composable
                val patcherViewModel: PatcherViewModel = koinViewModel { parametersOf(params) }
                val leavePatcher: () -> Unit = {
                    patcherViewModel.stopCompletionSound()
                    patcherBackgroundSpeed.floatValue = 1f
                    patchingCompleted.value = false
                    navController.popBackStack()
                }
                PatcherScreen(
                    onBackClick = leavePatcher,
                    patcherViewModel = patcherViewModel,
                    onBackgroundSpeedChange = { patcherBackgroundSpeed.floatValue = it },
                    onPatchingCompleted = { patchingCompleted.value = true },
                    onStartTour = startOnboardingTour,
                    onDeclineTour = declineOnboardingTour,
                    onChangePatches = {
                        leavePatcher()
                        homeViewModel.patchFlow.reopenPatchSelection(params)
                    }
                )
            }

            composable<SettingsDetail> { entry ->
                SettingsSubpageScreen(
                    subpage = SettingsSubpage.valueOf(entry.toRoute<SettingsDetail>().subpage),
                    homeViewModel = homeViewModel,
                    navigation = remember(navController) {
                        // The tour runs over the main screen, so it starts once back there
                        navController.settingsNavigation(startTour = {
                            navController.popBackStack(HomeScreen, false)
                            startOnboardingTour()
                        })
                    },
                    // Back to the main screen however it was asked for, so a second tap never leaves it
                    onBack = { navController.popBackStack(HomeScreen, false) }
                )
            }

            composable<PatchSelections> {
                PatchSelectionsPage(onBack = { navController.popBackStack(HomeScreen, false) })
            }

            composable<Licenses> {
                LicensesPage(onBack = { navController.popBackStack() })
            }
        }

        if (showOnboarding) {
            CompositionLocalProvider(LocalBackdropHaze provides tourHaze) {
                OnboardingShowcase(steps = onboardingSteps, onComplete = finishOnboardingTour)
            }
        }
    }
}

/** Where the settings lead out to, as routes of this navigation, see [SettingsNavigation]. */
private fun NavController.settingsNavigation(startTour: (() -> Unit)?) = SettingsNavigation(
    openSubpage = { subpage -> navigate(SettingsDetail(subpage.name)) { launchSingleTop = true } },
    openPatchSelections = { navigate(PatchSelections) { launchSingleTop = true } },
    openLicenses = { navigate(Licenses) { launchSingleTop = true } },
    startTour = startTour
)

// Androidx Navigation does not support storing complex types in route objects, so we have
// to store them inside the saved state handle of the back stack entry instead
private fun <T : Parcelable, R : ComplexParameter<T>> NavController.navigateComplex(
    route: R,
    data: T
) {
    navigate(route)
    getBackStackEntry(route).savedStateHandle["args"] = data
}

private fun <T : Parcelable> NavBackStackEntry.getComplexArg(): T? = savedStateHandle["args"]
