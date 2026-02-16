package app.morphe.manager.di

import app.morphe.manager.domain.installer.InstallerManager
import app.morphe.manager.domain.installer.RootInstaller
import app.morphe.manager.domain.installer.ShizukuInstaller
import app.morphe.manager.domain.manager.AppIconManager
import app.morphe.manager.domain.manager.KeystoreManager
import app.morphe.manager.domain.manager.PatchOptionsPreferencesManager
import app.morphe.manager.util.PM
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val managerModule = module {
    singleOf(::KeystoreManager)
    singleOf(::PM)
    singleOf(::RootInstaller)
    singleOf(::ShizukuInstaller)
    singleOf(::InstallerManager)
    singleOf(::PatchOptionsPreferencesManager)
    singleOf(::AppIconManager)
}
