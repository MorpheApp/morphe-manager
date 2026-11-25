/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import android.os.Build

/** Devices with fewer cores than this are slow at patching, see [isSlowPatchingDevice]. */
private const val FAST_DEVICE_CORES = 8

/**
 * Whether patching takes this device long enough that nothing else should compete with it for CPU
 * time: every device that runs no 64-bit code, and those with fewer than 8 cores.
 */
val isSlowPatchingDevice: Boolean by lazy {
    isSlowPatchingDevice(Build.SUPPORTED_64_BIT_ABIS, Runtime.getRuntime().availableProcessors())
}

internal fun isSlowPatchingDevice(supported64BitAbis: Array<String>, cores: Int) =
    supported64BitAbis.isEmpty() || cores < FAST_DEVICE_CORES
