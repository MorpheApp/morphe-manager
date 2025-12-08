package app.revanced.manager.di

import app.revanced.manager.ui.viewmodel.*
import app.revanced.manager.ui.model.navigation.SelectedApplicationInfo
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.androidx.viewmodel.dsl.viewModelOf
import org.koin.dsl.module

val viewModelModule = module {
    viewModelOf(::MainViewModel)
    viewModelOf(::DashboardViewModel)
    viewModel { (params: SelectedApplicationInfo.ViewModelParams) ->
        SelectedAppInfoViewModel(params)
    }
    viewModel { (params: SelectedApplicationInfo.PatchesSelector.ViewModelParams) ->
        PatchesSelectorViewModel(params)
    }
    viewModelOf(::GeneralSettingsViewModel)
    viewModelOf(::AdvancedSettingsViewModel)
    viewModelOf(::AppSelectorViewModel)
    viewModelOf(::PatcherViewModel)
    viewModelOf(::UpdateViewModel)
    viewModelOf(::ImportExportViewModel)
    viewModelOf(::AboutViewModel)
    viewModelOf(::DeveloperOptionsViewModel)
    viewModelOf(::ContributorViewModel)
    viewModelOf(::DownloadsViewModel)
    viewModelOf(::InstalledAppsViewModel)
    viewModelOf(::InstalledAppInfoViewModel)
    viewModelOf(::UpdatesSettingsViewModel)
    viewModelOf(::BundleListViewModel)
    viewModelOf(::ChangelogsViewModel)
    viewModelOf(::PatchProfilesViewModel)
}
