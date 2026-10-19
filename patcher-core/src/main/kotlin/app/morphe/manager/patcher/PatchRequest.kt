/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.patcher

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Everything a run needs, settled by the app before it starts. Serializable, as it crosses into
 * the JVM that patches when the run happens there.
 *
 * @param mergedApkFile Where a split archive merged into one APK is left, for the app to keep it
 *                      for repatching once the run is over.
 * @param temporaryDir Scratch space of the run, cleared by the app.
 * @param workspaceDir Scratch space patches write their own files to.
 */
@Serializable
data class PatchRequest(
    val packageName: String,
    val inputFile: String,
    val outputFile: String,
    val mergedApkFile: String,
    val temporaryDir: String,
    val workspaceDir: String,
    val bundles: List<Bundle>,
    val stripUnusedNativeLibs: Boolean,
    val device: DeviceConfig
) {
    /**
     * @param uid The bundle as the app knows it.
     * @param patches Keys of the selected patches, as [app.morphe.manager.patcher.patch.keyedFor] names them.
     * @param options Option values by patch key and option key.
     */
    @Serializable
    data class Bundle(
        val uid: Int,
        val patchesFile: String,
        val patches: Set<String>,
        val options: Map<String, Map<String, JsonElement>>
    )
}
