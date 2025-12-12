package app.revanced.manager

import android.content.ActivityNotFoundException
import android.os.Bundle
import android.os.Parcelable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.runtime.mutableStateOf
import app.revanced.manager.domain.manager.PreferencesManager
import app.revanced.manager.ui.model.navigation.AppSelector
import app.revanced.manager.ui.model.navigation.ComplexParameter
import app.revanced.manager.ui.model.navigation.MorpheHomeScreen
import app.revanced.manager.ui.model.navigation.Dashboard
import app.revanced.manager.ui.model.navigation.InstalledApplicationInfo
import app.revanced.manager.ui.model.navigation.MorpheSettings
import app.revanced.manager.ui.model.navigation.Patcher
import app.revanced.manager.ui.model.navigation.SelectedApplicationInfo
import app.revanced.manager.ui.model.navigation.Settings
import app.revanced.manager.ui.model.navigation.Update
import app.revanced.manager.ui.model.SelectedApp
import app.revanced.manager.ui.screen.AppSelectorScreen
import app.revanced.manager.ui.screen.MorpheHomeScreen
import app.revanced.manager.ui.screen.DashboardScreen
import app.revanced.manager.ui.screen.InstalledAppInfoScreen
import app.revanced.manager.ui.screen.PatcherScreen
import app.revanced.manager.ui.screen.PatchesSelectorScreen
import app.revanced.manager.ui.screen.MorphePatcherScreen
import app.revanced.manager.ui.screen.MorpheSettingsScreen
import app.revanced.manager.ui.screen.RequiredOptionsScreen
import app.revanced.manager.ui.screen.SelectedAppInfoScreen
import app.revanced.manager.ui.screen.SettingsScreen
import app.revanced.manager.ui.screen.UpdateScreen
import app.revanced.manager.ui.screen.settings.AboutSettingsScreen
import app.revanced.manager.ui.screen.settings.AdvancedSettingsScreen
import app.revanced.manager.ui.screen.settings.ContributorSettingsScreen
import app.revanced.manager.ui.screen.settings.DeveloperSettingsScreen
import app.revanced.manager.ui.screen.settings.DownloadsSettingsScreen
import app.revanced.manager.ui.screen.settings.GeneralSettingsScreen
import app.revanced.manager.ui.screen.settings.ImportExportSettingsScreen
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
import org.koin.androidx.viewmodel.ext.android.getViewModel as getActivityViewModel

// AppCompatActivity has issues of briefly showing a black screen on a new install.
//class MainActivity : AppCompatActivity() {
class MainActivity : ComponentActivity() {
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

    EventEffect(vm.appSelectFlow) { params ->
        navController.navigateComplex(
            SelectedApplicationInfo,
            params
        )
    }

    NavHost(
        navController = navController,
        startDestination = startDest,
        enterTransition = { slideInHorizontally(initialOffsetX = { it }) },
        exitTransition = { slideOutHorizontally(targetOffsetX = { -it / 3 }) },
        popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 3 }) },
        popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) },
    ) {
        // Clunky work around to get a boolean calculated in the home screen
        val usingMountInstallState = mutableStateOf(false)

        composable<MorpheHomeScreen> {
            val dashboardViewModel = koinViewModel<DashboardViewModel>()
            val bundleUpdateProgress by dashboardViewModel.bundleUpdateProgress.collectAsStateWithLifecycle(null)

            MorpheHomeScreen(
                onMorpheSettingsClick = { navController.navigate(MorpheSettings) },
                onDownloaderPluginClick = { navController.navigate(Settings.Downloads) },
                onUpdateClick = { navController.navigate(Update()) },
                onStartQuickPatch = { params ->
                    // Immediately start patching with the received parameters
                    navController.navigateComplex(
                        Patcher,
                        Patcher.ViewModelParams(
                            selectedApp = params.selectedApp,
                            selectedPatches = params.patches,
                            options = params.options
                        )
                    )
                },
                dashboardViewModel = dashboardViewModel,
                usingMountInstallState = usingMountInstallState,
                bundleUpdateProgress = bundleUpdateProgress
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

            composable<Settings.General> {
                GeneralSettingsScreen(onBackClick = navController::popBackStack)
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
                    navigate = navController::navigate
                )
            }

            composable<Settings.Changelogs> {
                ChangelogsSettingsScreen(onBackClick = navController::popBackStack)
            }

            composable<Settings.Contributors> {
                ContributorSettingsScreen(onBackClick = navController::popBackStack)
            }

            // Morphe Simplified Settings Screen
            composable<MorpheSettings> {
                MorpheSettingsScreen(onBackClick = navController::popBackStack)
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
