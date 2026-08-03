/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build

/**
 * Public intent contract for optional third-party APK download helpers.
 *
 * Morphe only describes the original APK it needs. The helper owns provider lookup,
 * download UI, and file sharing, then returns the downloaded archive.
 *
 * A helper declares an exported activity with an intent filter for
 * `ACTION_DOWNLOAD_ORIGINAL_APK` and [Intent.CATEGORY_DEFAULT]. It answers with
 * [android.app.Activity.RESULT_OK] and the archive in `Intent.setData`, granting read access via
 * [Intent.FLAG_GRANT_READ_URI_PERMISSION]. Without that flag Morphe cannot open the file and
 * reports the selection as unreadable.
 *
 * Requests go to the explicit component the user picked, and everything a helper returns still
 * runs through the normal package, version and signature checks. A helper is a download
 * convenience, never a trusted source.
 */
object ApkDownloadHelperContract {
    const val ACTION_DOWNLOAD_ORIGINAL_APK = "app.morphe.manager.action.DOWNLOAD_ORIGINAL_APK"

    /** Bumped whenever the extras below change in a way helpers have to react to. */
    const val PROTOCOL_VERSION = 1

    const val EXTRA_PROTOCOL_VERSION = "app.morphe.manager.extra.PROTOCOL_VERSION"
    const val EXTRA_CALLER_PACKAGE = "app.morphe.manager.extra.CALLER_PACKAGE"
    const val EXTRA_PACKAGE_NAME = "app.morphe.manager.extra.PACKAGE_NAME"
    const val EXTRA_APP_NAME = "app.morphe.manager.extra.APP_NAME"

    /** Version the user asked for. Absent when any compatible version will do. */
    const val EXTRA_VERSION_NAME = "app.morphe.manager.extra.VERSION_NAME"

    /** Build codes of [EXTRA_VERSION_NAME] as a `long[]`. Empty when the bundle declares none. */
    const val EXTRA_VERSION_CODES = "app.morphe.manager.extra.VERSION_CODES"

    /** Other version names Morphe can patch, for when the requested one is no longer available. */
    const val EXTRA_COMPATIBLE_VERSION_NAMES = "app.morphe.manager.extra.COMPATIBLE_VERSION_NAMES"

    /** Device ABIs in preference order, mirroring [Build.SUPPORTED_ABIS]. */
    const val EXTRA_SUPPORTED_ABIS = "app.morphe.manager.extra.SUPPORTED_ABIS"

    /** Archive format the patch bundle expects, one of the `FILE_TYPE_` constants. */
    const val EXTRA_FILE_TYPE = "app.morphe.manager.extra.FILE_TYPE"

    /** Whether a split archive is acceptable. False when the bundle requires a plain APK. */
    const val EXTRA_ALLOW_SPLIT_ARCHIVE = "app.morphe.manager.extra.ALLOW_SPLIT_ARCHIVE"

    /**
     * Informational hint that the unpatched app still has to be installed for a mount install.
     * Morphe performs that installation itself; helpers must not install anything.
     */
    const val EXTRA_STOCK_INSTALL_REQUIRED = "app.morphe.manager.extra.STOCK_INSTALL_REQUIRED"

    /** Web search Morphe would have opened, so a helper can fall back to it. */
    const val EXTRA_FALLBACK_WEB_URL = "app.morphe.manager.extra.FALLBACK_WEB_URL"

    // Archive formats for EXTRA_FILE_TYPE. Kept separate from APK_EXTENSIONS because helpers are
    // built against these strings, so they must stay stable even if Morphe accepts new extensions
    const val FILE_TYPE_APK = "apk"
    const val FILE_TYPE_APKM = "apkm"
    const val FILE_TYPE_APKS = "apks"
    const val FILE_TYPE_XAPK = "xapk"

    /** An installed activity that can serve [ACTION_DOWNLOAD_ORIGINAL_APK]. */
    data class Helper(
        val componentName: ComponentName,
        val label: String
    )

    /**
     * Installed helpers, sorted by label so the picker order stays stable.
     *
     * Queries PackageManager, so call it off the main thread.
     */
    fun findHelpers(context: Context): List<Helper> {
        val intent = Intent(ACTION_DOWNLOAD_ORIGINAL_APK).addCategory(Intent.CATEGORY_DEFAULT)
        val packageManager = context.packageManager

        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }

        return resolved
            .mapNotNull { info ->
                val activity = info.activityInfo ?: return@mapNotNull null
                Helper(
                    componentName = ComponentName(activity.packageName, activity.name),
                    label = info.loadLabel(packageManager).toString()
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    fun createRequestIntent(
        component: ComponentName,
        callerPackage: String,
        packageName: String,
        appName: String,
        versionName: String?,
        versionCodes: LongArray,
        compatibleVersionNames: List<String>,
        supportedAbis: Array<String>,
        fileType: String?,
        allowSplitArchive: Boolean,
        stockInstallRequired: Boolean,
        fallbackWebUrl: String
    ) = Intent(ACTION_DOWNLOAD_ORIGINAL_APK).apply {
        setComponent(component)
        addCategory(Intent.CATEGORY_DEFAULT)
        putExtra(EXTRA_PROTOCOL_VERSION, PROTOCOL_VERSION)
        putExtra(EXTRA_CALLER_PACKAGE, callerPackage)
        putExtra(EXTRA_PACKAGE_NAME, packageName)
        putExtra(EXTRA_APP_NAME, appName)
        versionName?.let { putExtra(EXTRA_VERSION_NAME, it) }
        putExtra(EXTRA_VERSION_CODES, versionCodes)
        putStringArrayListExtra(EXTRA_COMPATIBLE_VERSION_NAMES, ArrayList(compatibleVersionNames))
        putExtra(EXTRA_SUPPORTED_ABIS, supportedAbis)
        fileType?.let { putExtra(EXTRA_FILE_TYPE, it) }
        putExtra(EXTRA_ALLOW_SPLIT_ARCHIVE, allowSplitArchive)
        putExtra(EXTRA_STOCK_INSTALL_REQUIRED, stockInstallRequired)
        putExtra(EXTRA_FALLBACK_WEB_URL, fallbackWebUrl)
    }

    /**
     * The archive a helper handed back, or null when it answered with anything else.
     *
     * Only `content://` is accepted. A `file://` answer would make Morphe read a path under its
     * own UID on behalf of the helper, which the contract never asks for.
     */
    fun resultUri(intent: Intent?): Uri? =
        intent?.data?.takeIf { it.scheme == ContentResolver.SCHEME_CONTENT }

    /**
     * Whether the helper granted read access to what it returned.
     *
     * Reported separately from [resultUri] so a helper that forgets the flag gets a message
     * naming the real cause, instead of a generic read failure the user cannot act on.
     */
    fun grantsReadAccess(intent: Intent?): Boolean =
        intent != null && intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0
}
