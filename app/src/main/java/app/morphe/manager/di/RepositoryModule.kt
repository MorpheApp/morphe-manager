/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 *
 * Original hard forked code:
 * https://github.com/Jman-Github/Universal-ReVanced-Manager/blob/597b3173a004f5a9aae54326046dd7fd4c5b7777/app/src/main/java/app/revanced/manager/di/RepositoryModule.kt
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.manager.di

import app.morphe.manager.data.platform.Filesystem
import app.morphe.manager.data.platform.NetworkInfo
import app.morphe.manager.domain.repository.*
import app.morphe.manager.domain.worker.WorkerRepository
import app.morphe.manager.network.api.MorpheAPI
import app.morphe.manager.util.AppDataResolver
import org.koin.core.module.dsl.createdAtStart
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val repositoryModule = module {
    singleOf(::MorpheAPI)
    singleOf(::Filesystem) {
        createdAtStart()
    }
    singleOf(::NetworkInfo)
    singleOf(::ManagerUpdateRepository)
    singleOf(::PatchSelectionRepository)
    singleOf(::SourceMuteRepository)
    singleOf(::PatchOptionsRepository)
    singleOf(::BlocklistRepository)
    singleOf(::PatchBundleRepository)
    singleOf(::WorkerRepository)
    singleOf(::InstalledAppRepository)
    singleOf(::OriginalApkRepository)
    singleOf(::StorageStatsRepository)
    singleOf(::AppDataResolver)
}
