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
 * Known app configuration: display name, gradient colors, download button color.
 * Used for rendering home screen buttons for packages that have patches.
 */
data class AppConfig(
    val displayNameResId: Int,
    val gradientColors: List<Color>,
    val downloadColor: Color,
    /** Whether this app is pinned by default on the home screen for first-time users */
    val isPinnedByDefault: Boolean = false
)

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

    // Default gradient colors for unknown packages
    val DEFAULT_COLORS = listOf(
        Color(0xFF6C63FF),
        Color(0xFF1E5AA8),
        Color(0xFF00AFAE)
    )

    // Download button colors
    val YOUTUBE_DOWNLOAD_COLOR = Color(0xFFFF0034)
    val YOUTUBE_MUSIC_DOWNLOAD_COLOR = Color(0xFFFF0034)
    val REDDIT_DOWNLOAD_COLOR = Color(0xFFFF4400)
    val DEFAULT_DOWNLOAD_COLOR = Color(0xFF6C63FF)

    /**
     * Registry of known app configurations.
     * Add new entries here when supporting additional apps.
     */
    private val knownApps: Map<String, AppConfig> = mapOf(
        YOUTUBE to AppConfig(
            displayNameResId = R.string.home_youtube,
            gradientColors = YOUTUBE_COLORS,
            downloadColor = YOUTUBE_DOWNLOAD_COLOR,
            isPinnedByDefault = true
        ),
        YOUTUBE_MUSIC to AppConfig(
            displayNameResId = R.string.home_youtube_music,
            gradientColors = YOUTUBE_MUSIC_COLORS,
            downloadColor = YOUTUBE_MUSIC_DOWNLOAD_COLOR,
            isPinnedByDefault = true
        ),
        REDDIT to AppConfig(
            displayNameResId = R.string.home_reddit,
            gradientColors = REDDIT_COLORS,
            downloadColor = REDDIT_DOWNLOAD_COLOR,
            isPinnedByDefault = true
        )
    )

    /**
     * Default pinned packages for first-time users — derived from knownApps registry.
     * To change which apps are pinned by default, update isPinnedByDefault in knownApps above.
     */
    val DEFAULT_PINNED_PACKAGES: Set<String> by lazy {
        knownApps.filter { it.value.isPinnedByDefault }.keys
    }

    /**
     * Ordered list of gradient colors for cold-start shimmer placeholders.
     * Matches the default pinned apps order so shimmer looks correct before data loads.
     */
    val DEFAULT_SHIMMER_GRADIENTS: List<List<Color>> by lazy {
        knownApps.filter { it.value.isPinnedByDefault }.values.map { it.gradientColors }
    }

    /**
     * Get app config for a package, or null if not in the known registry
     */
    fun getAppConfig(packageName: String): AppConfig? = knownApps[packageName]

    /**
     * Get gradient colors for a package
     */
    fun getGradientColors(packageName: String): List<Color> =
        knownApps[packageName]?.gradientColors ?: DEFAULT_COLORS

    /**
     * Get download button color for a package
     */
    fun getDownloadColor(packageName: String): Color =
        knownApps[packageName]?.downloadColor ?: DEFAULT_DOWNLOAD_COLOR

    /**
     * Get localized app name for a package.
     * Returns the hardcoded display name for known apps,
     * or the raw package name for unknown apps.
     */
    fun getAppName(context: Context, packageName: String): String {
        val config = knownApps[packageName]
        return if (config != null) {
            context.getString(config.displayNameResId)
        } else {
            packageName
        }
    }

    /**
     * Check if a package is in the known registry
     */
    fun isKnown(packageName: String): Boolean = knownApps.containsKey(packageName)

    /**
     * Get all known package names
     */
    fun allKnownPackages(): Set<String> = knownApps.keys
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
