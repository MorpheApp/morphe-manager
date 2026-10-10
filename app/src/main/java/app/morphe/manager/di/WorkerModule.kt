/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 *
 * Original hard forked code:
 * https://github.com/Jman-Github/Universal-ReVanced-Manager/blob/597b3173a004f5a9aae54326046dd7fd4c5b7777/app/src/main/java/app/revanced/manager/di/WorkerModule.kt
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.manager.di

import app.morphe.manager.patcher.worker.PatcherWorker
import app.morphe.manager.worker.UpdateCheckWorker
import org.koin.androidx.workmanager.dsl.workerOf
import org.koin.dsl.module

val workerModule = module {
    workerOf(::PatcherWorker)
    workerOf(::UpdateCheckWorker)
}
