package app.morphe.manager.di

import app.morphe.manager.data.platform.Filesystem
import app.morphe.manager.data.platform.NetworkInfo
import app.morphe.manager.domain.repository.*
import app.morphe.manager.domain.worker.WorkerRepository
import app.morphe.manager.network.api.MorpheAPI
import org.koin.core.module.dsl.createdAtStart
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val repositoryModule = module {
    singleOf(::MorpheAPI)
    singleOf(::Filesystem) {
        createdAtStart()
    }
    singleOf(::NetworkInfo)
    singleOf(::PatchSelectionRepository)
    singleOf(::PatchOptionsRepository)
    singleOf(::PatchBundleRepository) {
        // It is best to load patch bundles ASAP
        createdAtStart()
    }
    singleOf(::WorkerRepository)
    single {
        InstalledAppRepository(
            db = get(),
            patchBundleRepository = get()
        )
    }
    singleOf(::OriginalApkRepository)
}
