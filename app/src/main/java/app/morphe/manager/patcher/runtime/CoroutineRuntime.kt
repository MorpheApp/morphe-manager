/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 *
 * Original hard forked code:
 * https://github.com/Jman-Github/Universal-ReVanced-Manager/blob/597b3173a004f5a9aae54326046dd7fd4c5b7777/app/src/main/java/app/revanced/manager/patcher/runtime/CoroutineRuntime.kt
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.manager.patcher.runtime

import android.content.Context
import app.morphe.manager.data.platform.Filesystem
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.patcher.PatchPipeline
import app.morphe.manager.patcher.PatchRequest
import app.morphe.manager.patcher.PatchingListener
import app.morphe.manager.patcher.logger.Logger
import app.morphe.manager.patcher.patch.PatchBundle
import java.io.File

/**
 * Patches inside the app's own process, within the heap Android gives it.
 */
class CoroutineRuntime(
    context: Context,
    fs: Filesystem,
    patchBundlesRepo: PatchBundleRepository
) : Runtime(context, fs, patchBundlesRepo) {
    override suspend fun patch(request: PatchRequest, logger: Logger, listener: PatchingListener) =
        PatchPipeline(request, logger, listener) { bundle ->
            // Read one at a time, so a native death inside a bundle is attributed to it instead of
            // leaving the next launch to crash the same way
            bundleLoadGuard.read(bundle.uid, File(bundle.patchesFile)) {
                PatchBundle.Loader.patches(PatchBundle(bundle.patchesFile))
            }
        }.run()
}
