/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import android.app.ActivityManager
import android.content.Context
import android.os.StatFs

/**
 * Free and total RAM and app storage, the numbers behind most patching failures.
 */
data class DeviceStats(
    val ramAvailable: Long,
    val ramTotal: Long,
    val lowMemory: Boolean,
    val lowMemoryThreshold: Long,
    val storageAvailable: Long,
    val storageTotal: Long
) {
    /** Free against total, the form the patch log and the error dialog both report. */
    val ram get() = "${formatBytes(ramAvailable)} / ${formatBytes(ramTotal)}"

    /** Storage is measured on the volume patching stages its APKs on. */
    val storage get() = "${formatBytes(storageAvailable)} / ${formatBytes(storageTotal)}"
}

/** Reads the device stats, or null on a platform that refuses to report them. */
fun Context.deviceStats(): DeviceStats? = runCatching {
    val memory = ActivityManager.MemoryInfo()
    getSystemService(ActivityManager::class.java).getMemoryInfo(memory)
    val storage = StatFs(filesDir.absolutePath)

    DeviceStats(
        ramAvailable = memory.availMem,
        ramTotal = memory.totalMem,
        lowMemory = memory.lowMemory,
        lowMemoryThreshold = memory.threshold,
        storageAvailable = storage.availableBytes,
        storageTotal = storage.totalBytes
    )
}.getOrNull()
