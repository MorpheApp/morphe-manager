/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 *
 * Original hard forked code:
 * https://github.com/Jman-Github/Universal-ReVanced-Manager/blob/597b3173a004f5a9aae54326046dd7fd4c5b7777/app/src/main/java/app/revanced/manager/patcher/runtime/process/Parameters.kt
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.manager.patcher.runtime.process

import android.os.Parcelable
import app.morphe.manager.patcher.patch.PatchBundle
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

@Parcelize
data class Parameters(
    val cacheDir: String,
    val frameworkDir: String,
    val packageName: String,
    val inputFile: String,
    val outputFile: String,
    val configurations: List<PatchConfiguration>,
    val stripUnusedNativeLibs: Boolean = false,
    // If non-null, PatcherProcess moves the merged mono-APK here after prepareIfNeeded and patches
    // it from this path. ProcessRuntime reads it back so the main process knows the merged file location
    val mergedInputFile: String? = null
) : Parcelable

@Parcelize
data class PatchConfiguration(
    val bundle: PatchBundle,
    val patches: Set<String>,
    val options: @RawValue Map<String, Map<String, Any?>>
) : Parcelable
