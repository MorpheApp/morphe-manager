package app.morphe.manager

import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
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
import app.morphe.manager.ui.model.SelectedApp
import app.morphe.manager.ui.model.navigation.ComplexParameter
import app.morphe.manager.ui.model.navigation.HomeScreen
import app.morphe.manager.ui.model.navigation.Patcher
import app.morphe.manager.ui.model.navigation.Settings
import app.morphe.manager.ui.screen.HomeScreen
import app.morphe.manager.ui.screen.PatcherScreen
import app.morphe.manager.ui.screen.SettingsScreen
import app.morphe.manager.ui.screen.shared.AnimatedBackground
import app.morphe.manager.ui.theme.ManagerTheme
import app.morphe.manager.ui.theme.Theme
import app.morphe.manager.ui.viewmodel.HomeViewModel
import app.morphe.manager.ui.viewmodel.MainViewModel
import app.morphe.manager.ui.viewmodel.PatcherViewModel
import app.morphe.manager.util.UpdateNotificationManager
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import java.io.File
import org.koin.androidx.viewmodel.ext.android.getViewModel as getActivityViewModel

class MainActivity : AppCompatActivity() {
    @ExperimentalAnimationApi
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
            val dynamicColor by vm.prefs.dynamicColor.getAsState()
            val pureBlackTheme by vm.prefs.pureBlackTheme.getAsState()
            val customAccentColor by vm.prefs.customAccentColor.getAsState()
            val customThemeColor by vm.prefs.customThemeColor.getAsState()

            ManagerTheme(
                darkTheme = theme == Theme.SYSTEM && isSystemInDarkTheme() || theme == Theme.DARK,
                dynamicColor = dynamicColor,
                pureBlackTheme = pureBlackTheme,
                accentColorHex = customAccentColor.takeUnless { it.isBlank() },
                themeColorHex = customThemeColor.takeUnless { it.isBlank() }
            ) {
                MorpheManager(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val vm: MainViewModel = getActivityViewModel()
        if (intent.getBooleanExtra(UpdateNotificationManager.EXTRA_TRIGGER_UPDATE_CHECK, false)) {
            vm.pendingUpdateCheck = true
        }
        handleDeepLinkIntent(intent, vm)
    }

    /**
     * Handles deep links for adding patch sources.
     * Format: https://morphe.software/add-source?github=owner/repo[&name=Display+Name]
     * Only GitHub URLs are accepted for safety.
     */
    private fun handleDeepLinkIntent(intent: Intent?, vm: MainViewModel) {
        val data = intent?.data ?: return
        val isAddSource = data.scheme == "https" &&
                data.host == "morphe.software" &&
                data.path?.startsWith("/add-source") == true
        if (!isAddSource) return

        val repo = data.getQueryParameter("github")?.takeIf { it.isNotBlank() } ?: return
        val url = "https://github.com/$repo"
        val name = data.getQueryParameter("name")?.takeIf { it.isNotBlank() }
        vm.pendingDeepLinkSource = MainViewModel.DeepLinkSource(url = url, name = name)
    }
}

@Composable
private fun MorpheManager(vm: MainViewModel) {
    val navController = rememberNavController()
    val prefs: PreferencesManager = koinInject()
    val backgroundType by prefs.backgroundType.getAsState()
    val enableParallax by prefs.enableBackgroundParallax.getAsState()

    // Patcher background speed — driven by PatcherViewModel when on patcher screen.
    // Exposed as a top-level mutable state so PatcherScreen can write into it.
    val patcherBackgroundSpeed = androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    val patchingCompleted = androidx.compose.runtime.remember { mutableStateOf(false) }

    // HomeViewModel must be scoped to the Activity, not to a NavBackStackEntry
    val homeViewModel: HomeViewModel = koinViewModel(
        viewModelStoreOwner = LocalActivity.current as ComponentActivity
    )

    // Box with background at the highest level
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Show animated background
        AnimatedBackground(
            type = backgroundType,
            enableParallax = enableParallax,
            speedMultiplier = patcherBackgroundSpeed.floatValue,
            patchingCompleted = patchingCompleted.value
        )

        // All content on top of background
        NavHost(
            navController = navController,
            startDestination = HomeScreen,
            enterTransition = {
                slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(400, easing = FastOutSlowInEasing)
                ) + fadeIn(
                    animationSpec = tween(400, delayMillis = 100)
                )
            },
            exitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { -it / 3 },
                    animationSpec = tween(400, easing = FastOutSlowInEasing)
                ) + fadeOut(
                    animationSpec = tween(300)
                )
            },
            popEnterTransition = {
                slideInHorizontally(
                    initialOffsetX = { -it / 3 },
                    animationSpec = tween(400, easing = FastOutSlowInEasing)
                ) + fadeIn(
                    animationSpec = tween(400, delayMillis = 100)
                )
            },
            popExitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(400, easing = FastOutSlowInEasing)
                ) + fadeOut(
                    animationSpec = tween(300)
                )
            },
        ) {
            // Shared state between HomeScreen and PatcherScreen for mount install mode.
            // Set by HomeViewModel.resolvePrePatchInstallerChoice()
            val usingMountInstallState = mutableStateOf(false)

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

                // Handle deep link source
                LaunchedEffect(vm.pendingDeepLinkSource) {
                    vm.pendingDeepLinkSource?.let { bundle ->
                        homeViewModel.handleDeepLinkAddSource(bundle.url, bundle.name)
                        vm.pendingDeepLinkSource = null
                    }
                }

                HomeScreen(
                    onSettingsClick = { navController.navigate(Settings) },
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
                    onNavigateToPatcher = { packageName, version, filePath, patches, options ->
                        entry.lifecycleScope.launch {
                            navController.navigateComplex(
                                Patcher,
                                Patcher.ViewModelParams(
                                    selectedApp = SelectedApp.Local(
                                        packageName = packageName,
                                        version = version,
                                        file = File(filePath),
                                        temporary = false
                                    ),
                                    selectedPatches = patches,
                                    options = options
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

            composable<Patcher> {
                val params = it.getComplexArg<Patcher.ViewModelParams>()
                val patcherViewModel: PatcherViewModel = koinViewModel { parametersOf(params) }
                PatcherScreen(
                    onBackClick = {
                        patcherBackgroundSpeed.floatValue = 1f
                        patchingCompleted.value = false
                        navController.popBackStack()
                    },
                    patcherViewModel = patcherViewModel,
                    usingMountInstall = usingMountInstallState.value,
                    onBackgroundSpeedChange = { patcherBackgroundSpeed.floatValue = it },
                    onPatchingCompleted = { patchingCompleted.value = true }
                )
                return@composable
            }

            composable<Settings> {
                SettingsScreen(homeViewModel = homeViewModel)
            }
        }
    }
}

// Androidx Navigation does not support storing complex types in route objects, so we have to store them inside the saved state handle of the back stack entry instead.
private fun <T : Parcelable, R : ComplexParameter<T>> NavController.navigateComplex(
    route: R,
    data: T
) {
    navigate(route)
    getBackStackEntry(route).savedStateHandle["args"] = data
}

private fun <T : Parcelable> NavBackStackEntry.getComplexArg() = savedStateHandle.get<T>("args")!!
