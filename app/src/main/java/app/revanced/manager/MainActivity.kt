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
import androidx.navigation.toRoute
import app.revanced.manager.domain.manager.PreferencesManager
import app.revanced.manager.ui.component.morphe.shared.AnimatedBackground
import app.revanced.manager.ui.model.SelectedApp
import app.revanced.manager.ui.model.navigation.*
import app.revanced.manager.ui.screen.*
import app.revanced.manager.ui.screen.settings.*
import app.revanced.manager.ui.screen.settings.update.ChangelogsSettingsScreen
import app.revanced.manager.ui.screen.settings.update.UpdatesSettingsScreen
import app.revanced.manager.ui.theme.ReVancedManagerTheme
import app.revanced.manager.ui.theme.Theme
import app.revanced.manager.ui.viewmodel.DashboardViewModel
import app.revanced.manager.ui.viewmodel.MainViewModel
import app.revanced.manager.ui.viewmodel.PatcherViewModel
import app.revanced.manager.ui.viewmodel.SelectedAppInfoViewModel
import app.revanced.manager.util.EventEffect
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.androidx.compose.navigation.koinNavViewModel
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
//            val launcher = rememberLauncherForActivityResult(
//                ActivityResultContracts.StartActivityForResult(),
//                onResult = vm::applyLegacySettings
//            )
            val theme by vm.prefs.theme.getAsState()
            val dynamicColor by vm.prefs.dynamicColor.getAsState()
            val pureBlackTheme by vm.prefs.pureBlackTheme.getAsState()
            val customAccentColor by vm.prefs.customAccentColor.getAsState()
            val customThemeColor by vm.prefs.customThemeColor.getAsState()

//            EventEffect(vm.legacyImportActivityFlow) {
//                try {
//                    launcher.launch(it)
//                } catch (_: ActivityNotFoundException) {
//                }
//            }

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
    val useMorpheHomeScreen by prefs.useMorpheHomeScreen.getAsState()
    val startDest = if (useMorpheHomeScreen) MorpheHomeScreen else Dashboard
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
        if (useMorpheHomeScreen) {
            AnimatedBackground(type = backgroundType)
        }

        // All content on top of background
        NavHost(
            navController = navController,
            startDestination = startDest,
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

            composable<MorpheHomeScreen> { entry ->
                val dashboardViewModel = koinViewModel<DashboardViewModel>()
                val bundleUpdateProgress by dashboardViewModel.bundleUpdateProgress.collectAsStateWithLifecycle(null)
                val patchTriggerPackage by entry.savedStateHandle.getStateFlow<String?>("patch_trigger_package", null)
                    .collectAsStateWithLifecycle()

                MorpheHomeScreen(
                    onMorpheSettingsClick = { navController.navigate(MorpheSettings) },
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

            composable<Dashboard> {
                DashboardScreen(
                    onSettingsClick = { navController.navigate(Settings) },
                    onAppSelectorClick = {
                        navController.navigate(AppSelector())
                    },
                    onStorageSelect = { saved -> vm.selectApp(saved) },
                    onUpdateClick = {
                        navController.navigate(Update())
                    },
                    onDownloaderPluginClick = {
                        navController.navigate(Settings.Downloads)
                    },
                    onAppClick = { packageName ->
                        navController.navigate(InstalledApplicationInfo(packageName))
                    },
                    onProfileLaunch = { launchData ->
                        navController.navigateComplex(
                            SelectedApplicationInfo,
                            SelectedApplicationInfo.ViewModelParams(
                                app = SelectedApp.Search(
                                    launchData.profile.packageName,
                                    launchData.profile.appVersion
                                ),
                                patches = null,
                                profileId = launchData.profile.uid,
                                requiresSourceSelection = true
                            )
                        )
                    }
                )
            }

            composable<InstalledApplicationInfo> {
                val data = it.toRoute<InstalledApplicationInfo>()

                InstalledAppInfoScreen(
                    onPatchClick = { packageName, selection ->
                        vm.selectApp(packageName, selection)
                    },
                    onBackClick = navController::popBackStack,
                    viewModel = koinViewModel { parametersOf(data.packageName) }
                )
            }

            composable<AppSelector> {
                val args = it.toRoute<AppSelector>()
                AppSelectorScreen(
                    onSelect = vm::selectApp,
                    onStorageSelect = vm::selectApp,
                    onBackClick = navController::popBackStack,
                    autoOpenStorage = args.autoStorage,
                    returnToDashboardOnStorage = args.autoStorageReturn
                )
            }

            composable<Patcher> {
                val params = it.getComplexArg<Patcher.ViewModelParams>()
                val patcherViewModel: PatcherViewModel = koinViewModel { parametersOf(params) }

                // Morphe changes begin
                val prefs: PreferencesManager = koinInject()
                val useMorpheHomeScreen by prefs.useMorpheHomeScreen.getAsState()
                if (useMorpheHomeScreen) {
                    MorphePatcherScreen(
                        onBackClick = navController::popBackStack,
                        viewModel = patcherViewModel,
                        usingMountInstall = usingMountInstallState.value
                    )
                    return@composable
                }
                // Morphe changes end

                PatcherScreen(
                    onBackClick = navController::popBackStack,
                    onReviewSelection = { app, selection, options, missing ->
                        val appWithVersion = when (app) {
                            is SelectedApp.Search -> app.copy(version = app.version ?: params.selectedApp.version)
                            is SelectedApp.Download -> if (app.version.isNullOrBlank()) app.copy(version = params.selectedApp.version) else app
                            else -> app
                        }
                        navController.navigateComplex(
                            SelectedApplicationInfo.PatchesSelector,
                            SelectedApplicationInfo.PatchesSelector.ViewModelParams(
                                app = appWithVersion,
                                currentSelection = selection,
                                options = options,
                                missingPatchNames = missing,
                                preferredAppVersion = app.version,
                                preferredBundleVersion = null,
                                preferredBundleUid = selection.keys.firstOrNull(),
                                preferredBundleOverride = null,
                                preferredBundleTargetsAllVersions = false
                            )
                        )
                    },
                    viewModel = koinViewModel { parametersOf(params) }
                )
            }

            composable<Update> {
                val data = it.toRoute<Update>()

                UpdateScreen(
                    onBackClick = navController::popBackStack,
                    vm = koinViewModel { parametersOf(data.downloadOnScreenEntry) }
                )
            }

            navigation<SelectedApplicationInfo>(startDestination = SelectedApplicationInfo.Main) {
                composable<SelectedApplicationInfo.Main> {
                    val parentBackStackEntry = navController.navGraphEntry(it)
                    val data =
                        parentBackStackEntry.getComplexArg<SelectedApplicationInfo.ViewModelParams>()
                    val viewModel =
                        koinNavViewModel<SelectedAppInfoViewModel>(viewModelStoreOwner = parentBackStackEntry) {
                            parametersOf(data)
                        }

                    SelectedAppInfoScreen(
                        onBackClick = navController::popBackStack,
                        onPatchClick = {
                            it.lifecycleScope.launch {
                                navController.navigateComplex(
                                    Patcher,
                                    viewModel.getPatcherParams()
                                )
                            }
                        },
                        onPatchSelectorClick = { app, patches, options ->
                            val versionHint = viewModel.selectedAppInfo?.versionName?.takeUnless { it.isNullOrBlank() }
                                ?: app.version?.takeUnless { it.isNullOrBlank() }
                                ?: viewModel.preferredBundleVersion?.takeUnless { it.isNullOrBlank() }
                                ?: viewModel.desiredVersion
                            val appWithVersion = when (app) {
                                is SelectedApp.Search -> app.copy(version = versionHint)
                                is SelectedApp.Download -> if (app.version.isNullOrBlank()) app.copy(version = versionHint) else app
                                else -> app
                            }
                            navController.navigateComplex(
                                SelectedApplicationInfo.PatchesSelector,
                                SelectedApplicationInfo.PatchesSelector.ViewModelParams(
                                    appWithVersion,
                                    patches,
                                    options,
                                    preferredAppVersion = versionHint,
                                    preferredBundleVersion = viewModel.preferredBundleVersion,
                                    preferredBundleUid = viewModel.selectedBundleUidFlow.value,
                                    preferredBundleOverride = viewModel.selectedBundleVersionOverrideFlow.value,
                                    preferredBundleTargetsAllVersions = viewModel.preferredBundleTargetsAllVersionsFlow.value
                                )
                            )
                        },
                        onRequiredOptions = { app, patches, options ->
                            val versionHint = viewModel.selectedAppInfo?.versionName?.takeUnless { it.isNullOrBlank() }
                                ?: app.version?.takeUnless { it.isNullOrBlank() }
                                ?: viewModel.preferredBundleVersion?.takeUnless { it.isNullOrBlank() }
                                ?: viewModel.desiredVersion
                            val appWithVersion = when (app) {
                                is SelectedApp.Search -> app.copy(version = versionHint)
                                is SelectedApp.Download -> if (app.version.isNullOrBlank()) app.copy(version = versionHint) else app
                                else -> app
                            }
                            navController.navigateComplex(
                                SelectedApplicationInfo.RequiredOptions,
                                SelectedApplicationInfo.PatchesSelector.ViewModelParams(
                                    appWithVersion,
                                    patches,
                                    options,
                                    preferredAppVersion = versionHint,
                                    preferredBundleVersion = viewModel.preferredBundleVersion,
                                    preferredBundleUid = viewModel.selectedBundleUidFlow.value,
                                    preferredBundleOverride = viewModel.selectedBundleVersionOverrideFlow.value,
                                    preferredBundleTargetsAllVersions = viewModel.preferredBundleTargetsAllVersionsFlow.value
                                )
                            )
                        },
                        vm = viewModel
                    )
                }

                composable<SelectedApplicationInfo.PatchesSelector> {
                    val data =
                        it.getComplexArg<SelectedApplicationInfo.PatchesSelector.ViewModelParams>()
                    val parentEntry = navController.navGraphEntry(it)
                    val parentArgs =
                        parentEntry.getComplexArg<SelectedApplicationInfo.ViewModelParams>()
                    val selectedAppInfoVm = koinNavViewModel<SelectedAppInfoViewModel>(
                        viewModelStoreOwner = parentEntry
                    ) {
                        parametersOf(parentArgs)
                    }

                    PatchesSelectorScreen(
                        onBackClick = navController::popBackStack,
                        onSave = { patches, options ->
                            selectedAppInfoVm.updateConfiguration(patches, options)
                            navController.popBackStack()
                        },
                        viewModel = koinViewModel { parametersOf(data) }
                    )
                }

                composable<SelectedApplicationInfo.RequiredOptions> {
                    val data =
                        it.getComplexArg<SelectedApplicationInfo.PatchesSelector.ViewModelParams>()
                    val parentEntry = navController.navGraphEntry(it)
                    val parentArgs =
                        parentEntry.getComplexArg<SelectedApplicationInfo.ViewModelParams>()
                    val selectedAppInfoVm = koinNavViewModel<SelectedAppInfoViewModel>(
                        viewModelStoreOwner = parentEntry
                    ) {
                        parametersOf(parentArgs)
                    }

                    RequiredOptionsScreen(
                        onBackClick = navController::popBackStack,
                        onContinue = { patches, options ->
                            selectedAppInfoVm.updateConfiguration(patches, options)
                            it.lifecycleScope.launch {
                                navController.navigateComplex(
                                    Patcher,
                                    selectedAppInfoVm.getPatcherParams()
                                )
                            }
                        },
                        vm = koinViewModel { parametersOf(data) }
                    )
                }
            }

            navigation<Settings>(startDestination = Settings.Main) {
                composable<Settings.Main> {
                    SettingsScreen(
                        onBackClick = navController::popBackStack,
                        navigate = navController::navigate
                    )
                }

                composable<Settings.Theme> {
                    MorpheThemeSettingsScreen(onBackClick = navController::popBackStack)
                }

                composable<Settings.Advanced> {
                    AdvancedSettingsScreen(onBackClick = navController::popBackStack)
                }

                composable<Settings.Developer> {
                    DeveloperSettingsScreen(onBackClick = navController::popBackStack)
                }

                composable<Settings.Updates> {
                    UpdatesSettingsScreen(
                        onBackClick = navController::popBackStack,
                        onChangelogClick = { navController.navigate(Settings.Changelogs) },
                        onUpdateClick = { navController.navigate(Update()) }
                    )
                }

                composable<Settings.Downloads> {
                    DownloadsSettingsScreen(onBackClick = navController::popBackStack)
                }

                composable<Settings.ImportExport> {
                    ImportExportSettingsScreen(onBackClick = navController::popBackStack)
                }

                composable<Settings.About> {
                    AboutSettingsScreen(
                        onBackClick = navController::popBackStack,
                        //                    navigate = navController::navigate
                    )
                }

                composable<Settings.Changelogs> {
                    ChangelogsSettingsScreen(onBackClick = navController::popBackStack)
                }

                composable<Settings.Contributors> {
                    ContributorSettingsScreen(onBackClick = navController::popBackStack)
                }

                // Morphe Settings Screen
                composable<MorpheSettings> {
                    MorpheSettingsScreen()
                }
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
