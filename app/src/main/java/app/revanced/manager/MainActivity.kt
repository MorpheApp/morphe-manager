package app.revanced.manager

import android.os.Bundle
import android.os.Parcelable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import app.revanced.manager.domain.manager.PreferencesManager
import app.revanced.manager.ui.model.SelectedApp
import app.revanced.manager.ui.model.navigation.*
import app.revanced.manager.ui.screen.HomeScreen
import app.revanced.manager.ui.screen.PatcherScreen
import app.revanced.manager.ui.screen.SettingsScreen
import app.revanced.manager.ui.screen.shared.AnimatedBackground
import app.revanced.manager.ui.theme.ReVancedManagerTheme
import app.revanced.manager.ui.theme.Theme
import app.revanced.manager.ui.viewmodel.DashboardViewModel
import app.revanced.manager.ui.viewmodel.MainViewModel
import app.revanced.manager.ui.viewmodel.PatcherViewModel
import app.revanced.manager.util.EventEffect
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

        setContent {
            val theme by vm.prefs.theme.getAsState()
            val dynamicColor by vm.prefs.dynamicColor.getAsState()
            val pureBlackTheme by vm.prefs.pureBlackTheme.getAsState()
            val customAccentColor by vm.prefs.customAccentColor.getAsState()
            val customThemeColor by vm.prefs.customThemeColor.getAsState()

            ReVancedManagerTheme(
                darkTheme = theme == Theme.SYSTEM && isSystemInDarkTheme() || theme == Theme.DARK,
                dynamicColor = dynamicColor,
                pureBlackTheme = pureBlackTheme,
                accentColorHex = customAccentColor.takeUnless { it.isBlank() },
                themeColorHex = customThemeColor.takeUnless { it.isBlank() }
            ) {
                ReVancedManager(vm)
            }
        }
    }
}

@Composable
private fun ReVancedManager(vm: MainViewModel) {
    val navController = rememberNavController()
    val prefs: PreferencesManager = koinInject()
    val backgroundType by prefs.backgroundType.getAsState()

    EventEffect(vm.appSelectFlow) { params ->
        navController.navigateComplex(
            SelectedApplicationInfo,
            params
        )
    }

    // Box with background at the highest level
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Show animated background
        AnimatedBackground(type = backgroundType)

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
            // Clunky work around to get a boolean calculated in the home screen
            val usingMountInstallState = mutableStateOf(false)

            composable<HomeScreen> { entry ->
                val dashboardViewModel = koinViewModel<DashboardViewModel>()
                val bundleUpdateProgress by dashboardViewModel.bundleUpdateProgress.collectAsStateWithLifecycle(null)
                val patchTriggerPackage by entry.savedStateHandle.getStateFlow<String?>("patch_trigger_package", null)
                    .collectAsStateWithLifecycle()

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
                    dashboardViewModel = dashboardViewModel,
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
                    onBackClick = navController::popBackStack,
                    viewModel = patcherViewModel,
                    usingMountInstall = usingMountInstallState.value
                )
                return@composable
            }

            composable<Settings> {
                SettingsScreen()
            }
        }
    }
}

@Composable
private fun NavController.navGraphEntry(entry: NavBackStackEntry) =
    remember(entry) { getBackStackEntry(entry.destination.parent!!.id) }

// Androidx Navigation does not support storing complex types in route objects, so we have to store them inside the saved state handle of the back stack entry instead.
private fun <T : Parcelable, R : ComplexParameter<T>> NavController.navigateComplex(
    route: R,
    data: T
) {
    navigate(route)
    getBackStackEntry(route).savedStateHandle["args"] = data
}

private fun <T : Parcelable> NavBackStackEntry.getComplexArg() = savedStateHandle.get<T>("args")!!
