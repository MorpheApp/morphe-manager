/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import android.content.Context
import java.io.File

private val ABIS_BY_INSTRUCTION_SET = mapOf(
    "arm64" to "arm64-v8a",
    "arm" to "armeabi-v7a",
    "x86_64" to "x86_64",
    "x86" to "x86"
)

/**
 * The ABI this app's processes run as, null for an instruction set it does not know. Can differ
 * from the device's first ABI, as a 64 bit device can run the app as a 32 bit one and an x86
 * device can run ARM code through translation. Android names the folder of the native libraries
 * the process runs after its instruction set.
 */
fun Context.processAbi(): String? = ABIS_BY_INSTRUCTION_SET[File(applicationInfo.nativeLibraryDir).name]
