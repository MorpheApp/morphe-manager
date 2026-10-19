/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

private const val BYTES_PER_MEBIBYTE = 1024 * 1024

/**
 * Bytes as whole mebibytes: megabytes counted in binary, which is what a heap limit, the RAM of
 * a device and every memory reading in the log mean by a megabyte. Files never use this.
 */
fun bytesToMebibytes(bytes: Long): Long =
    if (bytes <= 0) 0L else bytes / BYTES_PER_MEBIBYTE
