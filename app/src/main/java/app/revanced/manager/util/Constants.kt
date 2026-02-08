package app.revanced.manager.util

import android.content.Context
import androidx.compose.ui.graphics.Color
import app.morphe.manager.R

const val tag = "Morphe Manager"

const val MANAGER_REPO_URL = "https://github.com/MorpheApp/morphe-manager"
const val BUNDLE_URL_RELEASES = "https://github.com/MorpheApp/morphe-patches/releases/latest"
internal const val MORPHE_API_URL = "https://api.morphe.software"

// Package identifiers with their associated colors
object AppPackages {
    const val YOUTUBE = "com.google.android.youtube"
    const val YOUTUBE_MUSIC = "com.google.android.apps.youtube.music"
    const val REDDIT = "com.reddit.frontpage"

    // Gradient colors for each package
    val YOUTUBE_COLORS = listOf(
        Color(0xFFFF0033),
        Color(0xFF1E5AA8),
        Color(0xFF00AFAE)
    )

    val YOUTUBE_MUSIC_COLORS = listOf(
        Color(0xFFFF8C3E),
        Color(0xFF1E5AA8),
        Color(0xFF00AFAE)
    )

    val REDDIT_COLORS = listOf(
        Color(0xFFFF4500),
        Color(0xFF1E5AA8),
        Color(0xFF00AFAE)
    )

    // Download button colors
    val YOUTUBE_DOWNLOAD_COLOR = Color(0xFFFF0034)
    val YOUTUBE_MUSIC_DOWNLOAD_COLOR = Color(0xFFFF0034)
    val REDDIT_DOWNLOAD_COLOR = Color(0xFFFF4400)

    /**
     * Get download button color for a package
     */
    fun getDownloadColor(packageName: String): Color = when (packageName) {
        YOUTUBE -> YOUTUBE_DOWNLOAD_COLOR
        YOUTUBE_MUSIC -> YOUTUBE_MUSIC_DOWNLOAD_COLOR
        REDDIT -> REDDIT_DOWNLOAD_COLOR
        else -> YOUTUBE_DOWNLOAD_COLOR // Default to YouTube color
    }

    /**
     * Get localized app name for a package
     */
    fun getAppName(context: Context, packageName: String): String = when (packageName) {
        YOUTUBE -> context.getString(R.string.home_youtube)
        YOUTUBE_MUSIC -> context.getString(R.string.home_youtube_music)
        REDDIT -> context.getString(R.string.home_reddit)
        else -> packageName
    }
}

const val APK_MIMETYPE  = "application/vnd.android.package-archive"
const val JSON_MIMETYPE = "application/json"
const val BIN_MIMETYPE  = "application/octet-stream"

val APK_FILE_MIME_TYPES = arrayOf(
    BIN_MIMETYPE,
    APK_MIMETYPE,
    // ApkMirror split files of "app-whatever123_apkmirror.com.apk" regularly Android to misclassify
    // the file as an application or something incorrect. Renaming the file and
    // removing "apkmirror.com" from the file name fixes the issue, but that's not something the
    // end user will know or should have to do. Instead, show all files to ensure the user can
    // always select no matter what file naming ApkMirror uses.
    "application/*",
//    "application/zip",
//    "application/x-zip-compressed",
//    "application/x-apkm",
//    "application/x-apks",
//    "application/x-xapk",
//    "application/xapk",
//    "application/vnd.android.xapk",
//    "application/vnd.android.apkm",
//    "application/apkm",
//    "application/vnd.android.apks",
//    "application/apks",
)
val APK_FILE_EXTENSIONS = setOf(
    "apk",
    "apkm",
    "apks",
    "xapk",
    "zip"
)

val MPP_FILE_MIME_TYPES = arrayOf(
    BIN_MIMETYPE,
//    "application/x-zip-compressed"
    "*/*"
)
