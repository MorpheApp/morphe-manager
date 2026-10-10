/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 *
 * Original hard forked code:
 * https://github.com/Jman-Github/Universal-ReVanced-Manager/blob/597b3173a004f5a9aae54326046dd7fd4c5b7777/app/src/main/java/app/revanced/manager/di/ManagerModule.kt
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.manager.di

import app.morphe.manager.domain.apk.ApkSignatureCache
import app.morphe.manager.domain.apk.LocalApkSources
import app.morphe.manager.domain.batch.BatchPatchCoordinator
import app.morphe.manager.domain.batch.BatchPlanResolver
import app.morphe.manager.domain.bundles.AppVersionCatalog
import app.morphe.manager.domain.installer.InstallerManager
import app.morphe.manager.domain.installer.RootInstaller
import app.morphe.manager.domain.installer.SessionInstaller
import app.morphe.manager.domain.links.AppLinksManager
import app.morphe.manager.domain.manager.*
import app.morphe.manager.ui.screen.shared.ContentTranslation
import app.morphe.manager.util.AppCoroutineScope
import app.morphe.manager.util.ContentTranslator
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
    singleOf(::ContentTranslator)
    singleOf(::ContentTranslation)
    singleOf(::AppLinksManager)
}
