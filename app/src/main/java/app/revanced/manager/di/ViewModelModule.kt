package app.revanced.manager.di

import app.revanced.manager.ui.viewmodel.*
import org.koin.androidx.viewmodel.dsl.viewModelOf
import org.koin.dsl.module

val viewModelModule = module {
    viewModelOf(::MainViewModel)
    viewModelOf(::DashboardViewModel)
    viewModelOf(::HomeViewModel)
    viewModelOf(::MorpheThemeSettingsViewModel)
    viewModelOf(::AdvancedSettingsViewModel)
    viewModelOf(::AppSelectorViewModel)
    viewModelOf(::PatcherViewModel)
    viewModelOf(::MorpheInstallViewModel)
    viewModelOf(::UpdateViewModel)
    viewModelOf(::ImportExportViewModel)
    viewModelOf(::AboutViewModel)
    viewModelOf(::DeveloperOptionsViewModel)
    viewModelOf(::ContributorViewModel)
    viewModelOf(::InstalledAppsViewModel)
    viewModelOf(::InstalledAppInfoViewModel)
    viewModelOf(::UpdatesSettingsViewModel)
    viewModelOf(::BundleListViewModel)
    viewModelOf(::ChangelogsViewModel)
    viewModelOf(::PatchOptionsViewModel)
}
