package app.morphe.manager.di

import app.morphe.manager.patcher.worker.PatcherWorker
import org.koin.androidx.workmanager.dsl.workerOf
import org.koin.dsl.module

val workerModule = module {
    workerOf(::PatcherWorker)
}