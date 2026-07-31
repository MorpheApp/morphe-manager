/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri

/**
 * Public intent contract for optional third-party APK download helpers.
 *
 * Morphe only describes the original APK it needs. The helper owns provider lookup,
 * download UI, and file sharing, then returns a readable APK/APKS/XAPK Uri.
 */
object ApkDownloadHelperContract {
    const val ACTION_DOWNLOAD_ORIGINAL_APK = "app.morphe.manager.action.DOWNLOAD_ORIGINAL_APK"
    const val REFERENCE_HELPER_PACKAGE_NAME = "dev.rushi.apkdownloadhelper"

    const val PROTOCOL_VERSION = 1

    const val EXTRA_PROTOCOL_VERSION = "app.morphe.manager.extra.PROTOCOL_VERSION"
    const val EXTRA_CALLER_PACKAGE = "app.morphe.manager.extra.CALLER_PACKAGE"
    const val EXTRA_PACKAGE_NAME = "app.morphe.manager.extra.PACKAGE_NAME"
    const val EXTRA_APP_NAME = "app.morphe.manager.extra.APP_NAME"
    const val EXTRA_VERSION_NAME = "app.morphe.manager.extra.VERSION_NAME"
    const val EXTRA_VERSION_CODE = "app.morphe.manager.extra.VERSION_CODE"
    const val EXTRA_VERSION_CODES = "app.morphe.manager.extra.VERSION_CODES"
    const val EXTRA_COMPATIBLE_VERSION_NAMES = "app.morphe.manager.extra.COMPATIBLE_VERSION_NAMES"
    const val EXTRA_COMPATIBLE_VERSION_CODES = "app.morphe.manager.extra.COMPATIBLE_VERSION_CODES"
    const val EXTRA_SUPPORTED_ABIS = "app.morphe.manager.extra.SUPPORTED_ABIS"
    const val EXTRA_REQUESTED_FILE_TYPE = "app.morphe.manager.extra.REQUESTED_FILE_TYPE"
    const val EXTRA_ALLOW_SPLIT_ARCHIVE = "app.morphe.manager.extra.ALLOW_SPLIT_ARCHIVE"
    const val EXTRA_INSTALL_STOCK_AFTER_DOWNLOAD = "app.morphe.manager.extra.INSTALL_STOCK_AFTER_DOWNLOAD"
    const val EXTRA_FALLBACK_WEB_URL = "app.morphe.manager.extra.FALLBACK_WEB_URL"

    const val EXTRA_RESULT_PACKAGE_NAME = "app.morphe.manager.extra.RESULT_PACKAGE_NAME"
    const val EXTRA_RESULT_VERSION_NAME = "app.morphe.manager.extra.RESULT_VERSION_NAME"
    const val EXTRA_RESULT_SOURCE_NAME = "app.morphe.manager.extra.RESULT_SOURCE_NAME"
    const val EXTRA_RESULT_FILE_NAME = "app.morphe.manager.extra.RESULT_FILE_NAME"

    fun createProbeIntent() = Intent(ACTION_DOWNLOAD_ORIGINAL_APK)
        .addCategory(Intent.CATEGORY_DEFAULT)

    fun findHelpers(context: Context): List<ResolveInfo> {
        val intent = createProbeIntent()
        val packageManager = context.packageManager
        val helpers = if (android.os.Build.VERSION.SDK_INT >= 33) {
            packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }

        return helpers.filter { it.activityInfo?.exported == true }
    }

    fun hasHelper(context: Context): Boolean = findHelpers(context).isNotEmpty()

    fun createRequestIntent(
        callerPackage: String,
        packageName: String,
        appName: String,
        versionName: String?,
        versionCode: Long?,
        versionCodes: LongArray,
        compatibleVersionNames: List<String>,
        compatibleVersionCodes: LongArray,
        supportedAbis: Array<String>,
        requestedFileType: String?,
        allowSplitArchive: Boolean,
        installStockAfterDownload: Boolean,
        fallbackWebUrl: String
    ) = Intent(ACTION_DOWNLOAD_ORIGINAL_APK).apply {
        addCategory(Intent.CATEGORY_DEFAULT)
        putExtra(EXTRA_PROTOCOL_VERSION, PROTOCOL_VERSION)
        putExtra(EXTRA_CALLER_PACKAGE, callerPackage)
        putExtra(EXTRA_PACKAGE_NAME, packageName)
        putExtra(EXTRA_APP_NAME, appName)
        putExtra(EXTRA_VERSION_NAME, versionName)
        versionCode?.let { putExtra(EXTRA_VERSION_CODE, it) }
        putExtra(EXTRA_VERSION_CODES, versionCodes)
        putStringArrayListExtra(EXTRA_COMPATIBLE_VERSION_NAMES, ArrayList(compatibleVersionNames))
        putExtra(EXTRA_COMPATIBLE_VERSION_CODES, compatibleVersionCodes)
        putExtra(EXTRA_SUPPORTED_ABIS, supportedAbis)
        putExtra(EXTRA_REQUESTED_FILE_TYPE, requestedFileType)
        putExtra(EXTRA_ALLOW_SPLIT_ARCHIVE, allowSplitArchive)
        putExtra(EXTRA_INSTALL_STOCK_AFTER_DOWNLOAD, installStockAfterDownload)
        putExtra(EXTRA_FALLBACK_WEB_URL, fallbackWebUrl)
    }

    fun resultUri(intent: Intent?): Uri? = intent?.data
}
