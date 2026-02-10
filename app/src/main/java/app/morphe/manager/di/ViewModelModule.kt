package app.morphe.manager.di

import app.morphe.manager.ui.viewmodel.*
import org.koin.androidx.viewmodel.dsl.viewModelOf
import org.koin.dsl.module

val viewModelModule = module {
    viewModelOf(::MainViewModel)
    viewModelOf(::HomeViewModel)
    viewModelOf(::ThemeSettingsViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::PatcherViewModel)
    viewModelOf(::InstallViewModel)
    viewModelOf(::UpdateViewModel)
    viewModelOf(::ImportExportViewModel)
    viewModelOf(::AboutViewModel)
    viewModelOf(::InstalledAppInfoViewModel)
    viewModelOf(::BundleListViewModel)
    viewModelOf(::ChangelogsViewModel)
    viewModelOf(::PatchOptionsViewModel)
}
