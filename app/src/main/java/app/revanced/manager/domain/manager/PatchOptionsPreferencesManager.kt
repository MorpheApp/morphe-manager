package app.revanced.manager.domain.manager

import android.content.Context
import app.revanced.manager.domain.manager.base.BasePreferencesManager

/**
 * App type enum for patch options
 */
enum class AppType {
    YOUTUBE,
    YOUTUBE_MUSIC;

    /**
     * Get the package name for this app type
     */
    val packageName: String
        get() = when (this) {
            YOUTUBE -> PatchOptionsPreferencesManager.PACKAGE_YOUTUBE
            YOUTUBE_MUSIC -> PatchOptionsPreferencesManager.PACKAGE_YOUTUBE_MUSIC
        }
}

/**
 * Manages patch-specific option values that are applied during patching.
 * This manager only stores the values - the available options are fetched
 * dynamically from the patch bundle repository.
 *
 * Storage keys follow the pattern: {package}_{patchName}_{optionKey}
 * where package is "youtube" or "youtube_music"
 */
class PatchOptionsPreferencesManager(
    context: Context
) : BasePreferencesManager(context, "patch_options") {

    companion object {
        // Package identifiers
        const val PACKAGE_YOUTUBE = "com.google.android.youtube"
        const val PACKAGE_YOUTUBE_MUSIC = "com.google.android.apps.youtube.music"
        const val PACKAGE_REDDIT = "com.reddit.frontpage"

        // Patch names (must match exactly with bundle)
        const val PATCH_THEME = "Theme"
        const val PATCH_CUSTOM_BRANDING = "Custom branding"
        const val PATCH_CHANGE_HEADER = "Change header"
        const val PATCH_HIDE_SHORTS = "Hide Shorts components"

        // Option keys (must match exactly with bundle)
        const val KEY_DARK_THEME_COLOR = "darkThemeBackgroundColor"
        const val KEY_LIGHT_THEME_COLOR = "lightThemeBackgroundColor"
        const val KEY_CUSTOM_NAME = "customName"
        const val KEY_CUSTOM_ICON = "customIcon"
        const val KEY_CUSTOM_HEADER = "custom"
        const val KEY_HIDE_SHORTS_APP_SHORTCUT = "hideShortsAppShortcut"
        const val KEY_HIDE_SHORTS_WIDGET = "hideShortsWidget"

        // Default values
        const val DEFAULT_DARK_THEME = "@android:color/black"
        const val DEFAULT_LIGHT_THEME = "@android:color/white"

        // Hide Shorts options
        const val HIDE_SHORTS_APP_SHORTCUT_TITLE = "Hide Shorts app shortcut"
        const val HIDE_SHORTS_APP_SHORTCUT_DESC = "Permanently hides the shortcut to open Shorts when long pressing the app icon in your launcher."
        const val HIDE_SHORTS_WIDGET_TITLE = "Hide Shorts widget"
        const val HIDE_SHORTS_WIDGET_DESC = "Permanently hides the launcher widget Shorts button."

        // Theme options
        const val DARK_THEME_COLOR_TITLE = "Dark theme background color"
        const val DARK_THEME_COLOR_DESC = "Can be a hex color (#RRGGBB) or a color resource reference."
        const val LIGHT_THEME_COLOR_TITLE = "Light theme background color"
        const val LIGHT_THEME_COLOR_DESC = "Can be a hex color (#RRGGBB) or a color resource reference."

        // Custom branding icon instructions
        const val CUSTOM_ICON_INSTRUCTION = """Folder with images to use as a custom icon.

The folder must contain one or more of the following folders, depending on the DPI of the device:
- mipmap-mdpi
- mipmap-hdpi
- mipmap-xhdpi
- mipmap-xxhdpi
- mipmap-xxxhdpi

Each of the folders must contain all of the following files:
morphe_adaptive_background_custom.png
morphe_adaptive_foreground_custom.png

The image dimensions must be as follows:
- mipmap-mdpi: 108x108 px
- mipmap-hdpi: 162x162 px
- mipmap-xhdpi: 216x216 px
- mipmap-xxhdpi: 324x324 px
- mipmap-xxxhdpi: 432x432 px

Optionally, the path contains a 'drawable' folder with any of the monochrome icon files:
morphe_adaptive_monochrome_custom.xml
morphe_notification_icon_custom.xml"""

        // Custom header instructions
        const val CUSTOM_HEADER_INSTRUCTION = """Folder with images to use as a custom header logo.

The folder must contain one or more of the following folders, depending on the DPI of the device:
- drawable-hdpi
- drawable-xhdpi
- drawable-xxhdpi
- drawable-xxxhdpi

Each of the folders must contain all of the following files:
morphe_header_custom_light.png
morphe_header_custom_dark.png 

The image dimensions must be as follows:
- drawable-hdpi: 194x72 px
- drawable-xhdpi: 258x96 px
- drawable-xxhdpi: 387x144 px
- drawable-xxxhdpi: 512x192 px"""
    }

    // ==================== YouTube Options ====================

    // Theme - Dark
    val darkThemeBackgroundColorYouTube = stringPreference(
        "${PACKAGE_YOUTUBE}_${PATCH_THEME}_${KEY_DARK_THEME_COLOR}",
        DEFAULT_DARK_THEME
    )

    // Theme - Light
    val lightThemeBackgroundColorYouTube = stringPreference(
        "${PACKAGE_YOUTUBE}_${PATCH_THEME}_${KEY_LIGHT_THEME_COLOR}",
        DEFAULT_LIGHT_THEME
    )

    // Custom Branding - App Name
    val customAppNameYouTube = stringPreference(
        "${PACKAGE_YOUTUBE}_${PATCH_CUSTOM_BRANDING}_${KEY_CUSTOM_NAME}",
        ""
    )

    // Custom Branding - Icon Path
    val customIconPathYouTube = stringPreference(
        "${PACKAGE_YOUTUBE}_${PATCH_CUSTOM_BRANDING}_${KEY_CUSTOM_ICON}",
        ""
    )

    // Change Header - Custom Header Path
    val customHeaderPath = stringPreference(
        "${PACKAGE_YOUTUBE}_${PATCH_CHANGE_HEADER}_${KEY_CUSTOM_HEADER}",
        ""
    )

    // Hide Shorts - App Shortcut
    val hideShortsAppShortcut = booleanPreference(
        "${PACKAGE_YOUTUBE}_${PATCH_HIDE_SHORTS}_${KEY_HIDE_SHORTS_APP_SHORTCUT}",
        false
    )

    // Hide Shorts - Widget
    val hideShortsWidget = booleanPreference(
        "${PACKAGE_YOUTUBE}_${PATCH_HIDE_SHORTS}_${KEY_HIDE_SHORTS_WIDGET}",
        false
    )

    // ==================== YouTube Music Options ====================

    // Theme - Dark
    val darkThemeBackgroundColorYouTubeMusic = stringPreference(
        "${PACKAGE_YOUTUBE_MUSIC}_${PATCH_THEME}_${KEY_DARK_THEME_COLOR}",
        DEFAULT_DARK_THEME
    )

    // Custom Branding - App Name
    val customAppNameYouTubeMusic = stringPreference(
        "${PACKAGE_YOUTUBE_MUSIC}_${PATCH_CUSTOM_BRANDING}_${KEY_CUSTOM_NAME}",
        ""
    )

    // Custom Branding - Icon Path
    val customIconPathYouTubeMusic = stringPreference(
        "${PACKAGE_YOUTUBE_MUSIC}_${PATCH_CUSTOM_BRANDING}_${KEY_CUSTOM_ICON}",
        ""
    )

    /**
     * Get the preference value for a specific option.
     * Returns null if the option is not stored.
     */
    suspend fun getOptionValue(
        packageType: String,
        patchName: String,
        optionKey: String
    ): Any? {
        val prefKey = "${packageType}_${patchName}_${optionKey}"

        return when (// YouTube Theme
            prefKey) {
            "${PACKAGE_YOUTUBE}_${PATCH_THEME}_${KEY_DARK_THEME_COLOR}" -> darkThemeBackgroundColorYouTube.get().takeIf { it.isNotBlank() }
            "${PACKAGE_YOUTUBE}_${PATCH_THEME}_${KEY_LIGHT_THEME_COLOR}" -> lightThemeBackgroundColorYouTube.get().takeIf { it.isNotBlank() }

            // YouTube Custom Branding
            "${PACKAGE_YOUTUBE}_${PATCH_CUSTOM_BRANDING}_${KEY_CUSTOM_NAME}" -> customAppNameYouTube.get().takeIf { it.isNotBlank() }
            "${PACKAGE_YOUTUBE}_${PATCH_CUSTOM_BRANDING}_${KEY_CUSTOM_ICON}" -> customIconPathYouTube.get().takeIf { it.isNotBlank() }

            // YouTube Change Header
            "${PACKAGE_YOUTUBE}_${PATCH_CHANGE_HEADER}_${KEY_CUSTOM_HEADER}" -> customHeaderPath.get().takeIf { it.isNotBlank() }

            // YouTube Hide Shorts
            "${PACKAGE_YOUTUBE}_${PATCH_HIDE_SHORTS}_${KEY_HIDE_SHORTS_APP_SHORTCUT}" -> hideShortsAppShortcut.get().takeIf { it }
            "${PACKAGE_YOUTUBE}_${PATCH_HIDE_SHORTS}_${KEY_HIDE_SHORTS_WIDGET}" -> hideShortsWidget.get().takeIf { it }

            // YouTube Music Theme
            "${PACKAGE_YOUTUBE_MUSIC}_${PATCH_THEME}_${KEY_DARK_THEME_COLOR}" -> darkThemeBackgroundColorYouTubeMusic.get().takeIf { it.isNotBlank() }

            // YouTube Music Custom Branding
            "${PACKAGE_YOUTUBE_MUSIC}_${PATCH_CUSTOM_BRANDING}_${KEY_CUSTOM_NAME}" -> customAppNameYouTubeMusic.get().takeIf { it.isNotBlank() }
            "${PACKAGE_YOUTUBE_MUSIC}_${PATCH_CUSTOM_BRANDING}_${KEY_CUSTOM_ICON}" -> customIconPathYouTubeMusic.get().takeIf { it.isNotBlank() }
            else -> null
        }
    }

    /**
     * Export YouTube specific patch options
     * Format: Map<BundleUid, Map<PatchName, Map<OptionKey, Value>>>
     */
    suspend fun exportYouTubePatchOptions(): Map<Int, Map<String, Map<String, Any?>>> {
        return buildMap {
            // Note: Bundle UID 0 is the default Morphe bundle
            val bundleOptions = mutableMapOf<String, MutableMap<String, Any?>>()

            // Theme patch options for YouTube
            val themeOptionsYouTube = mutableMapOf<String, Any?>()
            darkThemeBackgroundColorYouTube.get().takeIf { it.isNotBlank() && it != DEFAULT_DARK_THEME }?.let {
                themeOptionsYouTube[KEY_DARK_THEME_COLOR] = it
            }
            lightThemeBackgroundColorYouTube.get().takeIf { it.isNotBlank() && it != DEFAULT_LIGHT_THEME }?.let {
                themeOptionsYouTube[KEY_LIGHT_THEME_COLOR] = it
            }
            if (themeOptionsYouTube.isNotEmpty()) {
                bundleOptions[PATCH_THEME] = themeOptionsYouTube
            }

            // Custom Branding patch options for YouTube
            val brandingOptionsYouTube = mutableMapOf<String, Any?>()
            customAppNameYouTube.get().takeIf { it.isNotBlank() }?.let {
                brandingOptionsYouTube[KEY_CUSTOM_NAME] = it
            }
            customIconPathYouTube.get().takeIf { it.isNotBlank() }?.let {
                brandingOptionsYouTube[KEY_CUSTOM_ICON] = it
            }
            if (brandingOptionsYouTube.isNotEmpty()) {
                bundleOptions[PATCH_CUSTOM_BRANDING] = brandingOptionsYouTube
            }

            // Change Header patch options
            customHeaderPath.get().takeIf { it.isNotBlank() }?.let {
                bundleOptions[PATCH_CHANGE_HEADER] = mutableMapOf(
                    KEY_CUSTOM_HEADER to it
                )
            }

            // Hide Shorts patch options
            val shortsOptions = mutableMapOf<String, Any?>()
            if (hideShortsAppShortcut.get()) {
                shortsOptions[KEY_HIDE_SHORTS_APP_SHORTCUT] = true
            }
            if (hideShortsWidget.get()) {
                shortsOptions[KEY_HIDE_SHORTS_WIDGET] = true
            }
            if (shortsOptions.isNotEmpty()) {
                bundleOptions[PATCH_HIDE_SHORTS] = shortsOptions
            }

            // Bundle ID 0 = default Morphe bundle
            if (bundleOptions.isNotEmpty()) {
                put(0, bundleOptions)
            }
        }
    }

    /**
     * Export YouTube Music specific patch options
     * Format: Map<BundleUid, Map<PatchName, Map<OptionKey, Value>>>
     */
    suspend fun exportYouTubeMusicPatchOptions(): Map<Int, Map<String, Map<String, Any?>>> {
        return buildMap {
            val bundleOptions = mutableMapOf<String, MutableMap<String, Any?>>()

            // Theme patch options for YouTube Music
            val themeOptionsYouTubeMusic = mutableMapOf<String, Any?>()
            darkThemeBackgroundColorYouTubeMusic.get().takeIf { it.isNotBlank() && it != DEFAULT_DARK_THEME }?.let {
                themeOptionsYouTubeMusic[KEY_DARK_THEME_COLOR] = it
            }
            if (themeOptionsYouTubeMusic.isNotEmpty()) {
                bundleOptions[PATCH_THEME] = themeOptionsYouTubeMusic
            }

            // Custom Branding patch options for YouTube Music
            val brandingOptionsYouTubeMusic = mutableMapOf<String, Any?>()
            customAppNameYouTubeMusic.get().takeIf { it.isNotBlank() }?.let {
                brandingOptionsYouTubeMusic[KEY_CUSTOM_NAME] = it
            }
            customIconPathYouTubeMusic.get().takeIf { it.isNotBlank() }?.let {
                brandingOptionsYouTubeMusic[KEY_CUSTOM_ICON] = it
            }
            if (brandingOptionsYouTubeMusic.isNotEmpty()) {
                bundleOptions[PATCH_CUSTOM_BRANDING] = brandingOptionsYouTubeMusic
            }

            // Bundle ID 0 = default Morphe bundle
            if (bundleOptions.isNotEmpty()) {
                put(0, bundleOptions)
            }
        }
    }

    /**
     * Reset all patch options to defaults
     */
    suspend fun resetToDefaults() = edit {
        // YouTube
        darkThemeBackgroundColorYouTube.value = DEFAULT_DARK_THEME
        lightThemeBackgroundColorYouTube.value = DEFAULT_LIGHT_THEME
        customAppNameYouTube.value = ""
        customIconPathYouTube.value = ""
        customHeaderPath.value = ""
        hideShortsAppShortcut.value = false
        hideShortsWidget.value = false

        // YouTube Music
        darkThemeBackgroundColorYouTubeMusic.value = DEFAULT_DARK_THEME
        customAppNameYouTubeMusic.value = ""
        customIconPathYouTubeMusic.value = ""
    }
}

/**
 * Gets localized text if it matches original English text,
 * otherwise returns the custom text from patch
 */
fun getLocalizedOrCustomText(
    context: Context,
    currentText: String,
    originalEnglishText: String,
    localizedResId: Int
): String {
    return if (currentText == originalEnglishText) {
        context.getString(localizedResId)
    } else {
        currentText
    }
}
