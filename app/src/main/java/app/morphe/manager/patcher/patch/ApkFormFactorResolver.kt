/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.patcher.patch

import android.util.Log
import app.morphe.manager.ui.model.SelectedApp
import app.morphe.manager.util.LOG_TAG
import app.morphe.manager.util.PM
import app.morphe.patcher.apk.ApkFormFactor
import app.morphe.patcher.patch.FormFactor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The form factor patches are offered for, read out of the APK a run starts from rather than the
 * device, so a TV build patched on a phone still gets its TV patches.
 *
 * Null when the APK cannot be read, which narrows nothing.
 */
object ApkFormFactorResolver {
    /** Form factor of the APK [selectedApp] will be patched from. */
    suspend fun resolve(selectedApp: SelectedApp, pm: PM): FormFactor? = when (selectedApp) {
        is SelectedApp.Local -> resolve(selectedApp.file)
        is SelectedApp.Installed -> withContext(Dispatchers.IO) {
            pm.getPackageInfo(selectedApp.packageName)?.applicationInfo?.sourceDir
        }?.let { resolve(File(it)) }
    }

    /** Form factor of a single APK or split archive, or of the base APK of an installed app. */
    suspend fun resolve(file: File): FormFactor? = withContext(Dispatchers.IO) {
        try {
            ApkFormFactor.of(file)
        } catch (e: Exception) {
            Log.w(LOG_TAG, "Failed to read the form factor of ${file.name}", e)
            null
        }
    }
}
