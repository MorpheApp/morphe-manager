/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.apk

import android.os.Build

/** How an APK picked for patching stands against the certificates its sources declare. */
enum class ApkSignatureCheck {
    /** Signed as declared, or there is nothing it can be checked against. */
    ACCEPTED,

    /** Signed, but by a certificate the sources do not declare, as a modified APK is. */
    OTHER_SIGNER,

    /**
     * Carries no signature that verifies. Almost always a damaged file, or one picked before its
     * download finished, which another download fixes.
     */
    BROKEN,
}

/**
 * Checks an APK against the certificates the sources [declared] for its app, reading its
 * certificate fingerprints through [hashes] only when there is something to check.
 *
 * Answered permissively wherever it cannot be answered at all: [sdkInt] 29 and below cannot read
 * certificates out of an archive, an app no source declares certificates for has nothing to be
 * checked against, and null hashes mean the archive would not open, which says nothing either
 * way. Empty hashes are the opposite case: the archive opened and no signature in it verified.
 */
internal inline fun apkSignatureCheck(
    sdkInt: Int,
    declared: Set<String>?,
    hashes: () -> Set<String>?
): ApkSignatureCheck {
    if (sdkInt <= Build.VERSION_CODES.Q || declared.isNullOrEmpty()) return ApkSignatureCheck.ACCEPTED
    val read = hashes() ?: return ApkSignatureCheck.ACCEPTED
    return when {
        read.isEmpty() -> ApkSignatureCheck.BROKEN
        read.any { it in declared } -> ApkSignatureCheck.ACCEPTED
        else -> ApkSignatureCheck.OTHER_SIGNER
    }
}
