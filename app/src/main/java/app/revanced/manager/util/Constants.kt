package app.revanced.manager.util

import androidx.compose.ui.graphics.Color

const val tag = "Morphe Manager"

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
}

//const val JAR_MIMETYPE = "application/java-archive"
const val APK_MIMETYPE = "application/vnd.android.package-archive"

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
const val JSON_MIMETYPE = "application/json"
const val BIN_MIMETYPE = "application/octet-stream"

val MPP_FILE_MIME_TYPES = arrayOf(
    BIN_MIMETYPE,
//    "application/x-zip-compressed"
    "*/*"
)
