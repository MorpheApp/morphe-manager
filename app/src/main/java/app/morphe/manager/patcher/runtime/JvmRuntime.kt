/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.patcher.runtime

import android.content.Context
import app.morphe.manager.data.platform.Filesystem
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.patcher.PatchRequest
import app.morphe.manager.patcher.PatchingListener
import app.morphe.manager.patcher.logger.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Patches in the JVM the app embeds, which [PatcherJvm] runs inside the app's process. The JVM
 * keeps its heap in native memory, so a run is limited by what the device can spare rather than
 * by the heap ART gives a process.
 */
class JvmRuntime(
    context: Context,
    private val fs: Filesystem,
    patchBundlesRepo: PatchBundleRepository,
    private val jvm: PatcherJvm
) : Runtime(context, fs, patchBundlesRepo) {
    override suspend fun patch(request: PatchRequest, logger: Logger, listener: PatchingListener) {
        // A file of its own, as a canceled run may still be reading its request when the next one starts
        val requestFile = withContext(Dispatchers.IO) {
            File.createTempFile("patch-request", ".json", fs.tempDir).apply {
                writeText(Json.encodeToString(PatchRequest.serializer(), request))
            }
        }
        try {
            val failure = jvm.run(requestFile, logger, listener) ?: return
            throw if (failure.outOfMemory) HeapExhaustedException(jvm.heapLimitMb, failure.stackTrace)
            else JvmFailureException(failure.stackTrace)
        } finally {
            requestFile.delete()
        }
    }
}
