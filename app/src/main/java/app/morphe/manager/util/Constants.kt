package app.morphe.manager.util

import android.content.Context
import androidx.compose.ui.graphics.Color
import app.morphe.manager.R

const val tag = "Morphe Manager"

const val SOURCE_NAME = "Morphe Patches"
const val MANAGER_REPO_URL = "https://github.com/MorpheApp/morphe-manager"
const val SOURCE_REPO_URL = "https://github.com/MorpheApp/morphe-patches"
const val MORPHE_API_URL = "https://api.morphe.software"

/** Controls whether manager updates are fetched directly from JSON files in the repository instead of using the GitHub API */
const val USE_MANAGER_DIRECT_JSON = true

/** Controls whether patches are fetched directly from JSON files in the repository instead of using the Morphe API */
const val USE_PATCHES_DIRECT_JSON = true

/**
 * All configuration for a known app in one place.
 * Each app defines only its unique [accentColor] - the gradient and download color
 * are derived automatically. To add a new app - create a new data object and add it to [all].
 */
sealed class KnownApp(
    val packageName: String,
    val displayNameResId: Int,
    val accentColor: Color,
    val isPinnedByDefault: Boolean = false
) {
    companion object {
        // Package name constants
        const val YOUTUBE = "com.google.android.youtube"
        const val YOUTUBE_MUSIC = "com.google.android.apps.youtube.music"
        const val REDDIT = "com.reddit.frontpage"

        // Shared gradient tail colors used by all known apps
        val GRADIENT_MID = Color(0xFF1E5AA8)
        val GRADIENT_END = Color(0xFF00AFAE)

        val all: List<KnownApp> = listOf(YouTube, YouTubeMusic, Reddit)

        fun fromPackage(packageName: String): KnownApp? =
            all.firstOrNull { it.packageName == packageName }
    }

    // Gradient and download color are derived from accentColor automatically
    val gradientColors get() = listOf(accentColor, GRADIENT_MID, GRADIENT_END)
    val downloadColor get() = accentColor

    data object YouTube : KnownApp(
        packageName = YOUTUBE,
        displayNameResId = R.string.home_youtube,
        accentColor = Color(0xFFFF0033),
        isPinnedByDefault = true
    )

    data object YouTubeMusic : KnownApp(
        packageName = YOUTUBE_MUSIC,
        displayNameResId = R.string.home_youtube_music,
        accentColor = Color(0xFFFF8C3E),
        isPinnedByDefault = true
    )

    data object Reddit : KnownApp(
        packageName = REDDIT,
        displayNameResId = R.string.home_reddit,
        accentColor = Color(0xFFFF4500),
        isPinnedByDefault = true
    )
}

/**
 * Utility object for package-based lookups.
 */
object AppPackages {
    // Default colors for unknown packages
    val DEFAULT_COLORS = listOf(Color(0xFF6C63FF), KnownApp.GRADIENT_MID, KnownApp.GRADIENT_END)
    val DEFAULT_DOWNLOAD_COLOR = Color(0xFF6C63FF)

    /**
     * Default pinned packages for first-time users.
     * Derived from KnownApp registry - update isPinnedByDefault there to change this.
     */
    val DEFAULT_PINNED_PACKAGES: Set<String> by lazy {
        KnownApp.all.filter { it.isPinnedByDefault }.map { it.packageName }.toSet()
    }

    /**
     * Ordered list of gradient colors for cold-start shimmer placeholders.
     * Matches the default pinned apps order so shimmer looks correct before data loads.
     */
    val DEFAULT_SHIMMER_GRADIENTS: List<List<Color>> by lazy {
        KnownApp.all.filter { it.isPinnedByDefault }.map { it.gradientColors }
    }

    /** Get gradient colors for a package. */
    fun getGradientColors(packageName: String): List<Color> =
        KnownApp.fromPackage(packageName)?.gradientColors ?: DEFAULT_COLORS

    /** Get download button color for a package. */
    fun getDownloadColor(packageName: String): Color =
        KnownApp.fromPackage(packageName)?.downloadColor ?: DEFAULT_DOWNLOAD_COLOR

    /**
     * Get localized app name for a package.
     * Returns the hardcoded display name for known apps, or the raw package name for unknown apps.
     */
    fun getAppName(context: Context, packageName: String): String =
        KnownApp.fromPackage(packageName)?.let { context.getString(it.displayNameResId) } ?: packageName

    /** Check if a package is in the known registry. */
    fun isKnown(packageName: String): Boolean =
        KnownApp.fromPackage(packageName) != null
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
