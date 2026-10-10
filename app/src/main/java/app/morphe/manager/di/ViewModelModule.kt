/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 *
 * Original hard forked code:
 * https://github.com/Jman-Github/Universal-ReVanced-Manager/blob/597b3173a004f5a9aae54326046dd7fd4c5b7777/app/src/main/java/app/revanced/manager/di/ViewModelModule.kt
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.manager.di

import app.morphe.manager.ui.viewmodel.*
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val viewModelModule = module {
    viewModelOf(::MainViewModel)
    viewModelOf(::HomeViewModel)
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
