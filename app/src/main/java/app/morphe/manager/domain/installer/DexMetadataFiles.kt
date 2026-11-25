/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.installer

import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import app.morphe.patcher.profile.DexMetadata
import java.io.File

private const val TAG = "Morphe DexMetadata"

/** The name of the dex metadata in an install session, which PackageManager pairs with `base.apk`. */
internal const val DEX_METADATA_NAME = "base.dm"

/** Set once the platform has rejected dex metadata, so later installs leave it out. */
@Volatile
private var dexMetadataRejected = false

/**
 * The dex metadata of [apk] for this device, see [DexMetadata], or null if it has none or the platform
 * rejected it before. Without it the app still installs, only uncompiled, so a failure to build it is
 * logged rather than thrown.
 */
internal fun dexMetadataOf(apk: File): ByteArray? {
    if (dexMetadataRejected) return null
    return try {
        DexMetadata.of(apk, Build.VERSION.SDK_INT)
    } catch (e: Exception) {
        Log.w(TAG, "No dex metadata for ${apk.name}", e)
        null
    }
}

/** Writes [metadata] to the session as [DEX_METADATA_NAME]. */
internal fun PackageInstaller.Session.writeDexMetadata(metadata: ByteArray) {
    openWrite(DEX_METADATA_NAME, 0, metadata.size.toLong()).use { out ->
        out.write(metadata)
        fsync(out)
    }
}

/**
 * Whether an install failed with [message] on its dex metadata, which [dexMetadataOf] then leaves out, so
 * the install can be repeated as it is. Some systems require dex metadata to carry fs-verity or a manifest.
 */
internal fun isDexMetadataRejection(message: String?): Boolean {
    if (message?.contains("INSTALL_FAILED_BAD_DEX_METADATA") != true) return false
    dexMetadataRejected = true
    Log.w(TAG, "Dex metadata rejected by the platform ($message), installing without it")
    return true
}
