/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.patcher

import kotlinx.serialization.Serializable

/**
 * The device a run patches for, which decides the native libraries and split modules it keeps.
 * Read by the app and handed over, since the JVM that may run the patcher cannot ask the system.
 *
 * @param supportedAbis The ABIs the device runs, most preferred first.
 * @param localeTags The system locales as BCP 47 tags, regardless of any in-app language.
 * @param densityDpi The screen density of the system, regardless of any in-app scaling.
 */
@Serializable
data class DeviceConfig(
    val supportedAbis: List<String>,
    val localeTags: List<String>,
    val densityDpi: Int
)
