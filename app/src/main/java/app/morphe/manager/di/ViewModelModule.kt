package app.morphe.manager.di

import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.ui.viewmodel.*
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import org.koin.androidx.viewmodel.dsl.viewModel

val viewModelModule = module {
    viewModelOf(::MainViewModel)
    viewModel {
        HomeViewModel(
            get(),
            lazy(LazyThreadSafetyMode.SYNCHRONIZED) { get<PatchBundleRepository>() },
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get()
        )
    }
    viewModelOf(::ThemeSettingsViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::PatcherViewModel)
    viewModelOf(::BatchPatcherViewModel)
    viewModelOf(::InstallViewModel)
    viewModelOf(::UpdateViewModel)
    viewModelOf(::ImportExportViewModel)
    viewModelOf(::AboutViewModel)
    viewModelOf(::InstalledAppInfoViewModel)
    viewModelOf(::PatchOptionsViewModel)
    viewModelOf(::StorageManagementViewModel)
}
