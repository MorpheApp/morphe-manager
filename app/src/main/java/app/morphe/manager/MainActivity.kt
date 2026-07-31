package app.morphe.manager

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.ui.model.navigation.BatchPatcher
import app.morphe.manager.ui.model.navigation.ComplexParameter
import app.morphe.manager.ui.model.navigation.HomeScreen
import app.morphe.manager.ui.model.navigation.Patcher
import app.morphe.manager.ui.model.navigation.Settings
import app.morphe.manager.ui.screen.BatchPatcherScreen
import app.morphe.manager.ui.screen.HomeScreen
import app.morphe.manager.ui.screen.PatcherScreen
import app.morphe.manager.ui.screen.SettingsScreen
import app.morphe.manager.ui.screen.home.ExternalBatchPatchDialog
import app.morphe.manager.ui.screen.home.GlobalOnboardingState
import app.morphe.manager.ui.screen.home.OnboardingShowcase
import app.morphe.manager.ui.screen.home.OnboardingState
import app.morphe.manager.ui.screen.home.StepDef
import app.morphe.manager.ui.screen.shared.AnimatedBackground
import app.morphe.manager.ui.screen.shared.BackgroundType
import app.morphe.manager.ui.screen.shared.MorpheAnimations
import app.morphe.manager.ui.theme.ManagerTheme
import app.morphe.manager.ui.theme.Theme
import app.morphe.manager.ui.theme.ThemeStyle
import app.morphe.manager.ui.theme.resolveThemeStyle
import app.morphe.manager.ui.viewmodel.HomeViewModel
import app.morphe.manager.ui.viewmodel.MainViewModel
import app.morphe.manager.ui.viewmodel.PatcherViewModel
import app.morphe.manager.ui.viewmodel.ThemeSettingsViewModel
import app.morphe.manager.util.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import org.koin.androidx.viewmodel.ext.android.getViewModel as getActivityViewModel

private enum class OnboardingPhase { HOME, SHEET, SETTINGS, DONE }

class MainActivity : AppCompatActivity() {

    /**
     * On Android < 13, AppCompatDelegate.setApplicationLocales() is unreliable on some
     * devices and OEMs - the locale is saved correctly but never applied on cold start.
     * Wrap the base context manually to guarantee the correct locale is always applied.
     */
    override fun attachBaseContext(newBase: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            val storedLang = readLanguageFromPrefs(newBase)
            val locale = parseLocaleCode(storedLang)
            if (locale != null) {
                val config = newBase.resources.configuration
                config.setLocale(locale)
                super.attachBaseContext(newBase.createConfigurationContext(config))
                return
            }
        }
        super.attachBaseContext(newBase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge()
        installSplashScreen()

        val vm: MainViewModel = getActivityViewModel()

        // Handle deep link on cold start
        handleDeepLinkIntent(intent, vm)

        setContent {
            val theme by vm.prefs.theme.getAsState()
            val themeStyle by vm.prefs.themeStyle.getAsState()
            val pureBlackTheme by vm.prefs.pureBlackTheme.getAsState()
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
                monochromeTheme = effectiveThemeStyle == ThemeStyle.MONOCHROME,
                accentColorHex = customAccentColor.takeUnless { it.isBlank() },
                themeColorHex = customThemeColor.takeUnless { it.isBlank() },
                appCardColorMode = appCardColorMode,
                appCardColorValues = appCardColorValues
            ) {
                MorpheManager(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLinkIntent(intent, getActivityViewModel())
    }

    /**
     * Handles add-source deep links from an explicit-package `intent://` fired by the website.
     * Format: https://morphe.software/add-source?<github|gitlab>=owner/repo(&name=…)
     * Only GitHub and GitLab URLs are accepted.
     */
    private fun handleDeepLinkIntent(intent: Intent?, vm: MainViewModel) {
        // Handled here rather than in onNewIntent so a cold start from a notification or a
        // launcher shortcut triggers the check as well
        if (intent?.getBooleanExtra(UpdateNotificationManager.EXTRA_TRIGGER_UPDATE_CHECK, false) == true) {
            vm.pendingUpdateCheck = true
            return
        }

        // Automatic re-patch notification: reopens the queue it reports about
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

        // Batch patch requested by another app, for example an automation tool.
        // The request is only recorded here; MorpheManager gates it before anything runs
        if (intent?.action == ACTION_BATCH_PATCH) {
            val packageNames = intent.getStringArrayExtra(EXTRA_BATCH_PACKAGES)?.toList()
                ?: intent.getStringExtra(EXTRA_BATCH_PACKAGES)
                    ?.split(',')
                    ?.map(String::trim)
                    ?.filter(String::isNotBlank)

            if (packageNames.isNullOrEmpty()) {
                // No list means the launcher shortcut, which asks the app to work out what is
                // outdated. It only opens the preflight list, so it needs no caller gate
                vm.pendingOutdatedBatch = true
            } else {
                vm.pendingBatchPatch = MainViewModel.BatchPatchRequest(
                    packageNames = packageNames,
                    // Only the system-supplied caller is trustworthy here. getReferrer() reads
                    // EXTRA_REFERRER, which the sender fills in itself, so it could name any
                    // package and both skip the confirmation and misname it in the dialog
                    callerPackage = callingPackage
                )
            }
            return
        }

        // Handle APK-family file shared via system share sheet (.apk/.apks/.xapk/.apkm)
        if (intent?.action == Intent.ACTION_SEND) {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            if (uri != null && uri.hasApkExtension(contentResolver)) {
                vm.pendingExternalApkUri = uri
            }
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
                data.path?.startsWith("/add-source") == true
        if (!isAddSource) return

        val name = data.getQueryParameter("name")?.takeIf { it.isNotBlank() }

        val githubRepo = data.getQueryParameter("github")?.takeIf { it.isNotBlank() }
        if (githubRepo != null) {
            val url = "https://github.com/$githubRepo"
            vm.pendingDeepLinkSource = MainViewModel.DeepLinkSource(url = url, name = name)
            return
        }

        val gitlabRepo = data.getQueryParameter("gitlab")?.takeIf { it.isNotBlank() }
        if (gitlabRepo != null) {
            val url = "https://gitlab.com/$gitlabRepo"
            vm.pendingDeepLinkSource = MainViewModel.DeepLinkSource(url = url, name = name)
            return
        }
    }

    companion object {
        /** Action other apps use to queue a batch patch run. */
        const val ACTION_BATCH_PATCH = "app.morphe.manager.action.BATCH_PATCH"

        /** Package names to patch, either a string array or a comma-separated string. */
        const val EXTRA_BATCH_PACKAGES = "packages"

        /** Action behind the per-app launcher shortcuts. */
        const val ACTION_PATCH_APP = "app.morphe.manager.action.PATCH_APP"

        /** Action behind the update check shortcut. Shortcut intents must carry one. */
        const val ACTION_CHECK_UPDATES = "app.morphe.manager.action.CHECK_UPDATES"

        /** Action that reopens the batch queue from an automatic re-patch notification. */
        const val ACTION_SHOW_BATCH_RESULT = "app.morphe.manager.action.SHOW_BATCH_RESULT"

        /** Package the per-app shortcut opens the patch dialog for. */
        const val EXTRA_PATCH_PACKAGE = "patch_package"
    }
}

@Composable
private fun MorpheManager(vm: MainViewModel) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val prefs: PreferencesManager = koinInject()
    val themeViewModel: ThemeSettingsViewModel = koinViewModel()
    val backgroundType by prefs.backgroundType.getAsState()
    val enableParallax by prefs.enableBackgroundParallax.getAsState()
    val randomInterval by prefs.randomBackgroundInterval.getAsState()
    val resolvedRandomBackground by themeViewModel.resolvedRandomBackground.collectAsStateWithLifecycle()

    // Resolve which background to show whenever RANDOM mode is active or the interval changes
    LaunchedEffect(backgroundType, randomInterval) {
        if (backgroundType == BackgroundType.RANDOM) {
            themeViewModel.resolveRandomBackground(randomInterval)
        }
    }

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
        vm.pendingDeepLinkSource?.let { bundle ->
            navController.popBackStack(HomeScreen, false)
            homeViewModel.handleDeepLinkAddSource(bundle.url, bundle.name)
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

    // Per-app shortcut reuses the trigger the installed-app dialog already goes through
    LaunchedEffect(vm.pendingPatchPackage) {
        vm.pendingPatchPackage?.let { packageName ->
            vm.pendingPatchPackage = null
            navController.popBackStack(HomeScreen, false)
            navController.getBackStackEntry(HomeScreen)
                .savedStateHandle["patch_trigger_package"] = packageName
        }
    }

    val context = LocalContext.current
    val nothingToRepatchText = stringResource(R.string.batch_patch_nothing_outdated)
    LaunchedEffect(vm.nothingToRepatch) {
        if (vm.nothingToRepatch) {
            context.toast(nothingToRepatchText)
            vm.consumeNothingToRepatch()
        }
    }

    LaunchedEffect(vm.approvedBatchPatch) {
        vm.approvedBatchPatch?.let { request ->
            vm.consumeApprovedBatch()
            navController.popBackStack(HomeScreen, false)
            navController.navigateComplex(
                BatchPatcher,
                BatchPatcher.ViewModelParams(
                    packageNames = request.packageNames,
                    useMount = false
                )
            )
        }
    }

    vm.batchPatchConfirmation?.let { request ->
        ExternalBatchPatchDialog(
            callerPackage = request.callerPackage,
            packageCount = request.packageNames.size,
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

    // Shared state between HomeScreen and PatcherScreen for mount install mode.
    // Set by HomeViewModel.resolvePrePatchInstallerChoice()
    val usingMountInstallState = remember { mutableStateOf(false) }

    // Unified onboarding
    val wantsOnboardingTour = remember { mutableStateOf(false) }
    var onboardingPhase by remember { mutableStateOf(OnboardingPhase.HOME) }
    var phaseInitialStep by remember { mutableIntStateOf(0) }
    val showOnboarding = wantsOnboardingTour.value && onboardingPhase != OnboardingPhase.DONE
    var showOnboardingOverlay by remember { mutableStateOf(true) }
    val homeOnboardingState = remember { OnboardingState() }
    val globalOnboardingState = remember { GlobalOnboardingState() }

    val homeSteps = remember(homeOnboardingState) {
        listOf(
            StepDef(
                R.string.onboarding_swipe_title, R.string.onboarding_swipe_desc,
                getBounds = { homeOnboardingState.firstAppCardBounds },
                onShow = {
                    homeOnboardingState.swipeActive = true
                    homeViewModel.triggerSwipeGestureHint()
                }
            ),
            StepDef(
                R.string.sources_management_title, R.string.onboarding_sources_desc,
                getBounds = { homeOnboardingState.sourcesButtonBounds },
                onShow = {
                    homeOnboardingState.swipeActive = false
                    homeViewModel.markSwipeGestureHintShown()
                }
            )
        )
    }

    val settingsSteps = remember(homeOnboardingState, globalOnboardingState) {
        listOf(
            StepDef(
                R.string.settings, R.string.onboarding_settings_desc,
                getBounds = { homeOnboardingState.settingsButtonBounds },
                onShow = { navController.popBackStack(HomeScreen, false) }
            ),
            StepDef(
                R.string.appearance, R.string.onboarding_appearance_tab_desc,
                getBounds = { globalOnboardingState.appearanceTabBounds },
                onShow = {
                    scope.launch {
                        navController.navigate(Settings) { launchSingleTop = true }
                        // Wait for SettingsScreen to compose and register callbacks (skipped if already there)
                        withTimeoutOrNull(2.seconds) {
                            while (globalOnboardingState.onNavigateToAppearanceTab == null) {
                                delay(16.milliseconds)
                            }
                        }
                        globalOnboardingState.onNavigateToAppearanceTab?.invoke()
                    }
                }
            ),
            StepDef(
                R.string.settings_appearance_theme, R.string.onboarding_appearance_theme_desc,
                getBounds = { globalOnboardingState.themeSelectorBounds },
                onShow = {
                    globalOnboardingState.onNavigateToAppearanceTab?.invoke()
                    globalOnboardingState.onScrollToThemeSelector?.invoke()
                }
            ),
            StepDef(
                R.string.settings_advanced_expert_mode, R.string.onboarding_expert_mode_desc,
                getBounds = { globalOnboardingState.expertModeBounds },
                onShow = { globalOnboardingState.onScrollToExpertMode?.invoke() }
            ),
            StepDef(
                R.string.settings_system_process_runtime, R.string.onboarding_system_process_runtime_desc,
                getBounds = { globalOnboardingState.processRuntimeBounds },
                onShow = { globalOnboardingState.onScrollToProcessRuntime?.invoke() }
            ),
            StepDef(
                R.string.onboarding_system_tab_title, R.string.onboarding_system_tab_desc,
                getBounds = { globalOnboardingState.systemTabBounds },
                onShow = { globalOnboardingState.onNavigateToSystemTab?.invoke() }
            ),
            StepDef(
                R.string.installer, R.string.onboarding_system_installer_desc,
                getBounds = { globalOnboardingState.installerSectionBounds },
                onShow = { globalOnboardingState.onScrollToInstaller?.invoke() }
            ),
            StepDef(
                R.string.settings_system_custom_file_picker, R.string.onboarding_system_file_picker_desc,
                getBounds = { globalOnboardingState.filePickerBounds },
                onShow = { globalOnboardingState.onScrollToFilePicker?.invoke() }
            )
        )
    }

    val sheetSteps = remember(globalOnboardingState) {
        listOf(
            StepDef(
                titleRes = R.string.patches,
                descRes = R.string.onboarding_sources_patches_desc,
                getBounds = { globalOnboardingState.sourcesPatchesBounds },
                onShow = { globalOnboardingState.onScrollToFirstSource?.invoke() }
            ),
            StepDef(
                titleRes = R.string.changelog,
                descRes = R.string.onboarding_sources_version_desc,
                getBounds = { globalOnboardingState.sourcesVersionBounds }
            ),
            StepDef(
                titleRes = R.string.sources_management_prerelease_toggle,
                descRes = R.string.onboarding_sources_prerelease_desc,
                getBounds = { globalOnboardingState.sourcesPrereleaseBounds },
                onShow = { globalOnboardingState.onScrollToPrerelease?.invoke() }
            )
        )
    }

    val totalOnboardingSteps = homeSteps.size + sheetSteps.size + settingsSteps.size

    // Box with background at the highest level
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Show animated background
        AnimatedBackground(
            type = backgroundType,
            resolvedType = resolvedRandomBackground,
            enableParallax = enableParallax,
            speedMultiplier = { patcherBackgroundSpeed.floatValue },
            patchingCompleted = { patchingCompleted.value }
        )

        // All content on top of background
        NavHost(
            navController = navController,
            startDestination = HomeScreen,
            enterTransition = { MorpheAnimations.screenEnter },
            exitTransition = { MorpheAnimations.screenExit },
            popEnterTransition = { MorpheAnimations.screenEnter },
            popExitTransition = { MorpheAnimations.screenExit }
        ) {
            composable<HomeScreen> { entry ->
                val bundleUpdateProgress by homeViewModel.bundleUpdateProgress.collectAsStateWithLifecycle(null)
                val patchTriggerPackage by entry.savedStateHandle.getStateFlow<String?>("patch_trigger_package", null)
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
                    onSettingsClick = { navController.navigate(Settings) },
                    onboardingState = if (showOnboarding && onboardingPhase == OnboardingPhase.HOME) homeOnboardingState else null,
                    globalOnboardingState = if (showOnboarding) globalOnboardingState else null,
                    onStartQuickPatch = { params ->
                        entry.lifecycleScope.launch {
                            navController.navigateComplex(
                                Patcher,
                                Patcher.ViewModelParams(
                                    selectedApp = params.selectedApp,
                                    selectedPatches = params.patches,
                                    options = params.options
                                )
                            )
                        }
                    },
                    onStartBatchPatch = { packageNames, useMount ->
                        entry.lifecycleScope.launch {
                            navController.navigateComplex(
                                BatchPatcher,
                                BatchPatcher.ViewModelParams(
                                    packageNames = packageNames,
                                    useMount = useMount
                                )
                            )
                        }
                    },
                    homeViewModel = homeViewModel,
                    usingMountInstallState = usingMountInstallState,
                    bundleUpdateProgress = bundleUpdateProgress,
                    patchTriggerPackage = patchTriggerPackage,
                    onPatchTriggerHandled = {
                        entry.savedStateHandle["patch_trigger_package"] = null
                    }
                )
            }

            composable<BatchPatcher> { entry ->
                val params = entry.getComplexArg<BatchPatcher.ViewModelParams>() ?: return@composable
                BatchPatcherScreen(
                    packageNames = params.packageNames,
                    useMount = params.useMount,
                    onBackClick = { navController.popBackStack() },
                    onAppStateChanged = homeViewModel::notifyAppStateChanged
                )
            }

            composable<Patcher> { it ->
                val params = it.getComplexArg<Patcher.ViewModelParams>() ?: return@composable
                val patcherViewModel: PatcherViewModel = koinViewModel { parametersOf(params) }
                PatcherScreen(
                    onBackClick = {
                        patcherViewModel.stopCompletionSound()
                        patcherBackgroundSpeed.floatValue = 1f
                        patchingCompleted.value = false
                        navController.popBackStack()
                    },
                    patcherViewModel = patcherViewModel,
                    usingMountInstall = usingMountInstallState.value,
                    onBackgroundSpeedChange = { patcherBackgroundSpeed.floatValue = it },
                    onPatchingCompleted = { patchingCompleted.value = true },
                    onStartTour = {
                        phaseInitialStep = 0
                        onboardingPhase = OnboardingPhase.HOME
                        wantsOnboardingTour.value = true
                    },
                    onDeclineTour = {
                        scope.launch { prefs.firstLaunch.update(false) }
                    }
                )
            }

            composable<Settings>(
                enterTransition = { MorpheAnimations.pushEnter },
                popExitTransition = { MorpheAnimations.pushExit }
            ) {
                SettingsScreen(
                    homeViewModel = homeViewModel,
                    globalOnboardingState = if (showOnboarding) globalOnboardingState else null,
                    onStartTour = if (!showOnboarding) {
                        {
                            onboardingPhase = OnboardingPhase.HOME
                            phaseInitialStep = 0
                            showOnboardingOverlay = true
                            wantsOnboardingTour.value = true
                            navController.popBackStack(HomeScreen, false)
                        }
                    } else null
                )
            }
        }

        if (showOnboarding) {
            val currentSteps = when (onboardingPhase) {
                OnboardingPhase.HOME -> homeSteps
                OnboardingPhase.SHEET -> sheetSteps
                OnboardingPhase.SETTINGS -> settingsSteps
                else -> null
            }
            if (currentSteps != null && showOnboardingOverlay) {
                val phaseOffset = when (onboardingPhase) {
                    OnboardingPhase.HOME -> 0
                    OnboardingPhase.SHEET -> homeSteps.size
                    OnboardingPhase.SETTINGS -> homeSteps.size + sheetSteps.size
                    else -> 0
                }
                val onComplete: () -> Unit = {
                    when (onboardingPhase) {
                        OnboardingPhase.HOME -> {
                            phaseInitialStep = 0
                            homeOnboardingState.swipeActive = false
                            homeViewModel.markSwipeGestureHintShown()
                            homeViewModel.showBundleManagementSheet = true
                            globalOnboardingState.sheetOnboardingActive = true
                            scope.launch {
                                withTimeoutOrNull(3.seconds) {
                                    while (globalOnboardingState.sourcesPatchesBounds == null) {
                                        delay(16.milliseconds)
                                    }
                                }
                                onboardingPhase = OnboardingPhase.SHEET
                            }
                        }
                        OnboardingPhase.SHEET -> {
                            phaseInitialStep = 0
                            globalOnboardingState.sheetOnboardingActive = false
                            homeViewModel.showBundleManagementSheet = false
                            onboardingPhase = OnboardingPhase.SETTINGS
                        }
                        OnboardingPhase.SETTINGS -> {
                            onboardingPhase = OnboardingPhase.DONE
                            scope.launch {
                                prefs.firstLaunch.update(false)
                                navController.popBackStack(HomeScreen, false)
                            }
                        }
                        else -> {}
                    }
                }
                val phaseBack: (() -> Unit)? = when (onboardingPhase) {
                    OnboardingPhase.SHEET -> {
                        {
                            scope.launch {
                                showOnboardingOverlay = false
                                globalOnboardingState.sheetOnboardingActive = false
                                homeViewModel.showBundleManagementSheet = false
                                delay(300.milliseconds)
                                phaseInitialStep = homeSteps.size - 1
                                onboardingPhase = OnboardingPhase.HOME
                                showOnboardingOverlay = true
                            }
                        }
                    }
                    OnboardingPhase.SETTINGS -> {
                        {
                            scope.launch {
                                showOnboardingOverlay = false
                                navController.popBackStack(HomeScreen, false)
                                delay(300.milliseconds)
                                globalOnboardingState.sourcesPatchesBounds = null
                                homeViewModel.showBundleManagementSheet = true
                                globalOnboardingState.sheetOnboardingActive = true
                                withTimeoutOrNull(3.seconds) {
                                    while (globalOnboardingState.sourcesPatchesBounds == null) {
                                        delay(16.milliseconds)
                                    }
                                }
                                phaseInitialStep = sheetSteps.size - 1
                                onboardingPhase = OnboardingPhase.SHEET
                                showOnboardingOverlay = true
                            }
                        }
                    }
                    else -> null
                }
                val onSkip: () -> Unit = {
                    homeOnboardingState.swipeActive = false
                    homeViewModel.markSwipeGestureHintShown()
                    globalOnboardingState.sheetOnboardingActive = false
                    homeViewModel.showBundleManagementSheet = false
                    onboardingPhase = OnboardingPhase.DONE
                    scope.launch {
                        prefs.firstLaunch.update(false)
                        navController.popBackStack(HomeScreen, false)
                    }
                }

                if (onboardingPhase == OnboardingPhase.SHEET) {
                    // Render in a Dialog window so the overlay appears above the sheet's popup window
                    Dialog(
                        onDismissRequest = {},
                        properties = DialogProperties(
                            usePlatformDefaultWidth = false,
                            dismissOnBackPress = false,
                            dismissOnClickOutside = false,
                            decorFitsSystemWindows = false
                        )
                    ) {
                        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
                        SideEffect {
                            dialogWindow?.setDimAmount(0f)
                            dialogWindow?.setBackgroundDrawableResource(android.R.color.transparent)
                        }
                        OnboardingShowcase(
                            steps = currentSteps,
                            stepOffset = phaseOffset,
                            totalStepsOverride = totalOnboardingSteps,
                            initialStep = phaseInitialStep,
                            onComplete = onComplete,
                            onSkip = onSkip,
                            onPhaseBack = phaseBack
                        )
                    }
                } else {
                    key(onboardingPhase) {
                        OnboardingShowcase(
                            steps = currentSteps,
                            stepOffset = phaseOffset,
                            totalStepsOverride = totalOnboardingSteps,
                            initialStep = phaseInitialStep,
                            onComplete = onComplete,
                            onSkip = onSkip,
                            onPhaseBack = phaseBack
                        )
                    }
                }
            }
        }
    }
}

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
