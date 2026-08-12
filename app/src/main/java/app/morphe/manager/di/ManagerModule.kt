package app.morphe.manager.di

import app.morphe.manager.domain.apk.ApkSignatureCache
import app.morphe.manager.domain.apk.LocalApkSources
import app.morphe.manager.domain.batch.BatchPatchCoordinator
import app.morphe.manager.domain.batch.BatchPlanResolver
import app.morphe.manager.domain.bundles.AppVersionCatalog
import app.morphe.manager.domain.installer.InstallerManager
import app.morphe.manager.domain.installer.RootInstaller
import app.morphe.manager.domain.installer.SessionInstaller
import app.morphe.manager.domain.manager.*
import app.morphe.manager.util.AppCoroutineScope
import app.morphe.manager.util.PM
import app.morphe.manager.util.UpdateNotificationManager
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val managerModule = module {
    singleOf(::KeystoreManager)
    singleOf(::ApkSignatureCache)
    singleOf(::PM)
    singleOf(::RootInstaller)
    singleOf(::SessionInstaller)
    singleOf(::InstallerManager)
    singleOf(::PatchOptionsPreferencesManager)
    singleOf(::AppIconManager)
    singleOf(::UpdateNotificationManager)
    singleOf(::DownloadUrlResolver)
    singleOf(::AppVersionCatalog)
    singleOf(::LocalApkSources)
    singleOf(::HomeAppButtonPreferences)
    singleOf(::AppCoroutineScope)
    singleOf(::BatchPlanResolver)
    singleOf(::BatchPatchCoordinator)
}
