/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.apk

import java.io.File

/** The inexpensive disk identity used to bind cached APK metadata to one file revision. */
internal data class ApkFileStamp(
    val path: String,
    val size: Long,
    val lastModified: Long
) {
    val cacheKey: String get() = "$path:$size:$lastModified"
}

internal fun File.apkFileStampOrNull(): ApkFileStamp? {
    if (!isFile) return null
    return ApkFileStamp(
        path = absolutePath,
        size = length(),
        lastModified = lastModified()
    )
}
