/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

private const val TAG = "Morphe ApkExport"

/**
 * Copies a patched APK to a location the user picked through the system document picker.
 *
 * @return true when the file was written in full.
 */
suspend fun Context.exportApkTo(file: File, uri: Uri): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        contentResolver.openOutputStream(uri)
            ?.use { stream -> file.inputStream().use { it.copyTo(stream) } }
            ?: throw IOException("Could not open output stream for export")
    }.onFailure {
        Log.e(TAG, "Failed to export ${file.name}", it)
    }.isSuccess
}
