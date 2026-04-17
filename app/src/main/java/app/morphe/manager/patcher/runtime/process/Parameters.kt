package app.morphe.manager.patcher.runtime.process

import android.os.Parcelable
import app.morphe.manager.patcher.patch.PatchBundle
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

@Parcelize
data class Parameters(
    val cacheDir: String,
    val aaptPath: String,
    val frameworkDir: String,
    val packageName: String,
    val inputFile: String,
    val outputFile: String,
    val configurations: List<PatchConfiguration>,
    val stripNativeLibs: Boolean,
    // If non-null, PatcherProcess writes the merged mono-APK to this path after prepareIfNeeded.
    // ProcessRuntime reads it back so the main process knows the merged file location.
    val mergedInputFile: String? = null,
) : Parcelable

@Parcelize
data class PatchConfiguration(
    val bundle: PatchBundle,
    val patches: Set<String>,
    val options: @RawValue Map<String, Map<String, Any?>>
) : Parcelable
