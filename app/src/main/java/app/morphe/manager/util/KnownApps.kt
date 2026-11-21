/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import androidx.compose.ui.graphics.Color

/**
 * Registry of known patchable apps, pinned on the home screen by default.
 */
object KnownApps {
    const val YOUTUBE       = "com.google.android.youtube"
    const val YOUTUBE_MUSIC = "com.google.android.apps.youtube.music"
    const val REDDIT        = "com.reddit.frontpage"

    /**
     * A known patchable app entry.
     *
     * @param packageName The app's package name.
     * @param fallbackName Display name used only when bundle metadata and the installed app label
     *   are both unavailable. The bundle always provides the authoritative name.
     * @param brandColor App brand color for the shimmer placeholder shown before any bundle loads.
     *   Should match the appIconColor value shipped in the bundle's Compatibility declaration.
     *   Null means fall back to [AppCardColorDefaults.defaultGradientColors].
     */
    data class Entry(
        val packageName: String,
        val fallbackName: String,
        val brandColor: Color? = null,
    )

    /** All known app entries in display order. */
    val all: List<Entry> = listOf(
        Entry(REDDIT,        "Reddit",        brandColor = Color(0xFFFF4500)),
        Entry(YOUTUBE,       "YouTube",       brandColor = Color(0xFFFF0033)),
        Entry(YOUTUBE_MUSIC, "YouTube Music", brandColor = Color(0xFFFF0000)),
    )

    // Fast lookup map, built once at startup
    private val byPackage: Map<String, Entry> = all.associateBy { it.packageName }

    /** Returns the [Entry] for [packageName], or null if not a known app. */
    fun fromPackage(packageName: String): Entry? = byPackage[packageName]

    /**
     * Ordered list of shimmer placeholder gradient colors shown during cold-start loading.
     * Uses each app's [Entry.brandColor] as the gradient start, and the actual bundle colors
     * replace them once the bundle loads. Falls back to
     * [AppCardColorDefaults.defaultGradientColors] if no brand color is declared.
     */
    val defaultShimmerGradients: List<List<Color>> by lazy {
        all.map { entry ->
            entry.brandColor?.let(AppCardColorDefaults::brandGradient)
                ?: AppCardColorDefaults.defaultGradientColors
        }
    }

    /**
     * Returns a display name for [packageName]: its [Entry.fallbackName], else the raw package
     * name. Used as the last resort when bundle metadata and installed labels are unavailable.
     */
    fun getAppName(packageName: String): String = fallbackName(packageName) ?: packageName

    /**
     * Returns the [Entry.fallbackName] of [packageName], or null if it is not a known app.
     * Unlike [getAppName], does not fall back to the raw package name, null means unknown.
     * Used for transitional metadata fallbacks where absence should be preserved.
     */
    fun fallbackName(packageName: String): String? = byPackage[packageName]?.fallbackName
}
