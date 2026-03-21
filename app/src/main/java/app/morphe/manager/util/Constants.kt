/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import android.content.Context
import androidx.compose.ui.graphics.Color

const val tag = "Morphe Manager"

const val SOURCE_NAME = "Morphe Patches"
const val MANAGER_REPO_URL = "https://github.com/MorpheApp/morphe-manager"
const val SOURCE_REPO_URL = "https://github.com/MorpheApp/morphe-patches"
const val MORPHE_API_URL = "https://api.morphe.software"

/**
 * Delay before showing a manager update notification to the user.
 * Gives time for the APK to be fully uploaded after app-release.json is published.
 */
const val MANAGER_UPDATE_SHOW_DELAY_SECONDS = 300L

/** Raw GitHub URL for the stable manager release JSON (main branch) */
const val MANAGER_RELEASE_JSON_URL = "https://raw.githubusercontent.com/MorpheApp/morphe-manager/refs/heads/main/app/app-release.json"

/** Raw GitHub URL for the pre-release manager release JSON (dev branch) */
const val MANAGER_PRERELEASE_JSON_URL = "https://raw.githubusercontent.com/MorpheApp/morphe-manager/refs/heads/dev/app/app-release.json"

/** Controls whether manager updates are fetched directly from JSON files in the repository instead of using the GitHub API */
const val USE_MANAGER_DIRECT_JSON = true

/** Controls whether patches are fetched directly from JSON files in the repository instead of using the Morphe API */
const val USE_PATCHES_DIRECT_JSON = true

/**
 * Registry of known patchable apps.
 */
object KnownApps {
    const val YOUTUBE       = "com.google.android.youtube"
    const val YOUTUBE_MUSIC = "com.google.android.apps.youtube.music"
    const val REDDIT        = "com.reddit.frontpage"
    const val X_TWITTER     = "com.twitter.android"

    // Shared Morphe brand gradient tail
    val GRADIENT_MID = Color(0xFF1E5AA8)
    val GRADIENT_END = Color(0xFF00AFAE)

    val DEFAULT_DOWNLOAD_COLOR = Color(0xFF0E3F6E)

    // Default gradient for packages with no bundle-declared color
    val DEFAULT_COLORS = listOf(DEFAULT_DOWNLOAD_COLOR, GRADIENT_MID, GRADIENT_END)

    /**
     * A known patchable app entry.
     *
     * @param packageName The app's package name.
     * @param isPinnedByDefault Whether this app appears pinned on the home screen by default.
     * @param brandColor App brand color used for the home screen button gradient start and
     *   shimmer placeholder. Should match the appIconColor value shipped in the bundle's
     *   Compatibility declaration. Null means fall back to [DEFAULT_COLORS].
     */
    data class Entry(
        val packageName: String,
        val isPinnedByDefault: Boolean = false,
        val brandColor: Color? = null,
    )

    /** All known app entries in display order. */
    val all: List<Entry> = listOf(
        Entry(REDDIT,        isPinnedByDefault = true, brandColor = Color(0xFFFF4500)),
        Entry(YOUTUBE,       isPinnedByDefault = true, brandColor = Color(0xFFFF0033)),
        Entry(YOUTUBE_MUSIC, isPinnedByDefault = true, brandColor = Color(0xFFFF0000)),
        // Entry(X_TWITTER, brandColor = Color(0xFF000000)),  // Uncomment when release
    )

    // Fast lookup map - built once at startup.
    private val byPackage: Map<String, Entry> = all.associateBy { it.packageName }

    /** Returns the [Entry] for [packageName], or null if not a known app. */
    fun fromPackage(packageName: String): Entry? = byPackage[packageName]

    /**
     * Ordered list of shimmer placeholder gradient colors shown during cold-start loading.
     * Uses each app's [Entry.brandColor] as the gradient start — actual bundle colors will
     * replace them once the bundle loads. Falls back to [DEFAULT_COLORS] if no brand color
     * is declared.
     */
    val DEFAULT_SHIMMER_GRADIENTS: List<List<Color>> by lazy {
        all.filter { it.isPinnedByDefault }.map { entry ->
            entry.brandColor?.let { color -> listOf(color, GRADIENT_MID, GRADIENT_END) }
                ?: DEFAULT_COLORS
        }
    }

    /**
     * Fallback display names for all packages - used only when bundle metadata and installed
     * app labels are both unavailable. Includes KnownApps entries so no separate localization
     * path is needed (bundle always provides the authoritative name anyway).
     */
    private val FALLBACK_NAMES = mapOf(
        YOUTUBE       to "YouTube",
        YOUTUBE_MUSIC to "YouTube Music",
        REDDIT        to "Reddit",
        X_TWITTER     to "X",
        "com.amazon.avod.thirdpartyclient" to "Amazon Prime Video",
        "com.avocards"                     to "Avocards",
        "me.mycake"                        to "Cake",
        "com.crunchyroll.crunchyroid"      to "Crunchyroll",
        "kr.co.yjteam.dailypay"            to "DAILY PAY",
        "com.duolingo"                     to "Duolingo",
        "kr.eggbun.eggconvo"               to "Eggbun",
        "jp.ne.ibis.ibispaintx.app"        to "IbisPaint X2",
        "org.languageapp.lingory"          to "Lingory2",
        "com.merriamwebster"               to "Merriam-Webster",
        "org.totschnig.myexpenses"         to "MyExpenses",
        "com.myfitnesspal.android"         to "MyFitnessPal",
        "com.pandora.android"              to "Pandora",
        "com.bambuna.podcastaddict"        to "Podcast Addict",
        "ch.protonvpn.android"             to "Proton VPN",
        "ginlemon.flowerfree"              to "Smart Launcher",
        "pl.solidexplorer2"                to "Solid Explorer",
        "net.teuida.teuida"                to "Teuida",
        "app.ttmikstories.android"         to "TTMIK Stories",
        "com.qbis.guessthecountry"         to "World Map Quiz",
        "cn.wps.moffice_eng"               to "WPS Office",
    )

    /**
     * Returns a display name for [packageName].
     * Priority: fallback table → raw package name.
     * Used as the last resort when bundle metadata and installed labels are unavailable.
     */
    fun getAppName(packageName: String): String =
        FALLBACK_NAMES[packageName] ?: packageName

    /**
     * Returns a fallback display name for [packageName], or null if not in the table.
     * Unlike [getAppName], does not fall back to the raw package name — null means unknown.
     * Used for transitional metadata fallbacks where absence should be preserved.
     */
    fun fallbackName(packageName: String): String? = FALLBACK_NAMES[packageName]
}

const val APK_MIMETYPE  = "application/vnd.android.package-archive"
const val JSON_MIMETYPE = "application/json"
const val BIN_MIMETYPE  = "application/octet-stream"

val APK_FILE_MIME_TYPES = arrayOf(
    BIN_MIMETYPE,
    APK_MIMETYPE,
    // ApkMirror split files of "app-whatever123_apkmirror.com.apk" regularly misclassify
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
