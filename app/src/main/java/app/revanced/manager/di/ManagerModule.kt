package app.revanced.manager.di

import app.revanced.manager.domain.installer.InstallerManager
import app.revanced.manager.domain.installer.RootInstaller
import app.revanced.manager.domain.installer.ShizukuInstaller
import app.revanced.manager.domain.manager.KeystoreManager
import app.revanced.manager.util.PM
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val managerModule = module {
    singleOf(::KeystoreManager)
    singleOf(::PM)
    singleOf(::RootInstaller)
    singleOf(::ShizukuInstaller)
    singleOf(::InstallerManager)
}
