/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.model

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import app.morphe.manager.domain.bundles.BundledAppTarget
import app.morphe.manager.domain.bundles.offered
import app.morphe.manager.patcher.patch.BundleAppMetadata
import app.morphe.manager.util.ApkDownloadHelperContract
import app.morphe.patcher.patch.ApkFileType

/**
 * The patching flow an APK download helper is asked on behalf of.
 *
 * Single-app patching and the queue both offer the same helper picker, each for the app it has a
 * download open for. What a helper may and may not do is settled by [ApkDownloadHelperContract];
 * what is left here is the part only the flow itself can answer, which is which app is being
 * patched and where the result goes.
 */
interface ApkDownloadHelperHost {
    /**
     * Whether the APK about to arrive will have its signature checked, which is what the picker
     * warns about. False on Android 8-10, where signatures cannot be read from an archive, and
     * for an app no bundle declares signatures for.
     */
    val helperSignatureCheckAvailable: Boolean

    /**
     * Describes the original APK for the helper the user picked, or null once the flow has moved
     * on and there is no app left to ask about.
     *
     * @param component The helper activity the user picked, addressed explicitly.
     */
    fun createApkDownloadHelperIntent(component: ComponentName): Intent?

    /** Takes the archive a helper downloaded, which still runs through the usual checks. */
    fun onHelperApkReceived(uri: Uri)

    /** Takes a helper's answer that the user installed the app from an app store instead. */
    fun onHelperInstalledAppChosen(packageName: String)

    /** Called when a helper returned without a usable answer, the user backing out of it included. */
    fun onHelperDismissed() = Unit
}

/**
 * Whether a helper's APK for an app the bundle describes with [metadata] will have its signature
 * checked. Android 8-10 cannot read signatures from an archive at all.
 */
internal fun helperSignatureCheckAvailable(metadata: BundleAppMetadata?): Boolean =
    Build.VERSION.SDK_INT > Build.VERSION_CODES.Q && !metadata?.signatures.isNullOrEmpty()

/**
 * The request both patching flows send a helper, derived from what the bundle declares.
 *
 * @param versionName Version the user asked for, or null when any compatible one will do.
 * @param compatible Versions the bundle can patch, before narrowing to the offered ones.
 */
internal fun createApkDownloadHelperRequest(
    component: ComponentName,
    callerPackage: String,
    packageName: String,
    appName: String,
    versionName: String?,
    compatible: List<BundledAppTarget>,
    metadata: BundleAppMetadata?,
    stockInstallRequired: Boolean,
    fallbackWebUrl: String
): Intent {
    val apkFileType = metadata?.apkFileType

    val versionCodes = compatible
        .filter { it.target.version == versionName }
        .flatMap { it.buildCodes.orEmpty() }
        .distinct()
        .map(Int::toLong)
        .toLongArray()

    return ApkDownloadHelperContract.createRequestIntent(
        component = component,
        callerPackage = callerPackage,
        packageName = packageName,
        appName = appName,
        versionName = versionName,
        versionCodes = versionCodes,
        // Narrowed the same way the picker is: a helper told an experimental version is
        // acceptable would hand back the very one the user chose to hide
        compatibleVersionNames = compatible.offered()
            .mapNotNull { it.target.version }
            .distinct(),
        supportedAbis = Build.SUPPORTED_ABIS,
        fileType = apkFileType?.toHelperFileType(),
        // Mirrors processSelectedApp - only a required plain APK rules split archives out
        allowSplitArchive = !(apkFileType?.isApk == true && apkFileType.isRequired),
        stockInstallRequired = stockInstallRequired,
        fallbackWebUrl = fallbackWebUrl
    )
}

/** The archive format a bundle asks for, named the way the helper protocol spells it. */
private fun ApkFileType.toHelperFileType() = when {
    isApk -> ApkDownloadHelperContract.FILE_TYPE_APK
    isApkM -> ApkDownloadHelperContract.FILE_TYPE_APKM
    isApkS -> ApkDownloadHelperContract.FILE_TYPE_APKS
    isXApk -> ApkDownloadHelperContract.FILE_TYPE_XAPK
    else -> null
}
