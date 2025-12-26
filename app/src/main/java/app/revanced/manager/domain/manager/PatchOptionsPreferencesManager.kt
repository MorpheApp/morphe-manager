package app.revanced.manager.domain.manager

import android.content.Context
import app.revanced.manager.domain.manager.base.BasePreferencesManager

/**
 * Manages patch-specific options that are applied during patching
 * These options are read by the patcher and applied to patches
 */
class PatchOptionsPreferencesManager(
    context: Context
) : BasePreferencesManager(context, "patch_options") {

    // ==================== YouTube Options ====================

    // Theme - Dark
    val darkThemeBackgroundColorYouTube = stringPreference(
        "dark_theme_background_color_youtube",
        "@android:color/black"
    )

    // Theme - Light
    val lightThemeBackgroundColorYouTube = stringPreference(
        "light_theme_background_color_youtube",
        "@android:color/white"
    )

    // Custom Branding
    val customAppNameYouTube = stringPreference(
        "custom_app_name_youtube",
        ""
    )

    val customIconPathYouTube = stringPreference(
        "custom_icon_path_youtube",
        ""
    )

    // Custom Header
    val customHeaderPath = stringPreference(
        "custom_header_path",
        ""
    )

    // Hide Shorts
    val hideShortsAppShortcut = booleanPreference(
        "hide_shorts_app_shortcut",
        false
    )

    val hideShortsWidget = booleanPreference(
        "hide_shorts_widget",
        false
    )

    // ==================== YouTube Music Options ====================

    // Theme - Dark
    val darkThemeBackgroundColorYouTubeMusic = stringPreference(
        "dark_theme_background_color_youtube_music",
        "@android:color/black"
    )

    // Custom Branding
    val customAppNameYouTubeMusic = stringPreference(
        "custom_app_name_youtube_music",
        ""
    )

    val customIconPathYouTubeMusic = stringPreference(
        "custom_icon_path_youtube_music",
        ""
    )

    /**
     * Export all patch options as a map
     * This is used by the patcher to apply options to patches
     */
    suspend fun exportPatchOptions(): Map<Int, Map<String, Map<String, Any?>>> {
        return buildMap {
            // Note: Bundle UID 0 is the default Morphe bundle
            val bundleOptions = mutableMapOf<String, MutableMap<String, Any?>>()

            // Theme patch options
            val themeOptionsYouTube = mutableMapOf<String, Any?>()
            darkThemeBackgroundColorYouTube.get().takeIf { it.isNotBlank() }?.let {
                themeOptionsYouTube["darkThemeBackgroundColor"] = it
            }
            lightThemeBackgroundColorYouTube.get().takeIf { it.isNotBlank() }?.let {
                themeOptionsYouTube["lightThemeBackgroundColor"] = it
            }
            if (themeOptionsYouTube.isNotEmpty()) {
                bundleOptions["Theme"] = themeOptionsYouTube
            }

            // Custom Branding patch options
            val brandingOptionsYouTube = mutableMapOf<String, Any?>()
            customAppNameYouTube.get().takeIf { it.isNotBlank() }?.let {
                brandingOptionsYouTube["customName"] = it
            }
            customIconPathYouTube.get().takeIf { it.isNotBlank() }?.let {
                brandingOptionsYouTube["customIcon"] = it
            }
            if (brandingOptionsYouTube.isNotEmpty()) {
                bundleOptions["Custom branding"] = brandingOptionsYouTube
            }

            // Change Header patch options
            customHeaderPath.get().takeIf { it.isNotBlank() }?.let {
                bundleOptions["Change header"] = mutableMapOf(
                    "custom" to it
                )
            }

            // Hide Shorts patch options
            val shortsOptions = mutableMapOf<String, Any?>()
            if (hideShortsAppShortcut.get()) {
                shortsOptions["hideShortsAppShortcut"] = true
            }
            if (hideShortsWidget.get()) {
                shortsOptions["hideShortsWidget"] = true
            }
            if (shortsOptions.isNotEmpty()) {
                bundleOptions["Hide Shorts components"] = shortsOptions
            }

            // Bundle ID 0 = default Morphe bundle
            if (bundleOptions.isNotEmpty()) {
                put(0, bundleOptions)
            }
        }
    }

    /**
     * Export YouTube Music specific patch options
     */
    suspend fun exportYouTubeMusicPatchOptions(): Map<Int, Map<String, Map<String, Any?>>> {
        return buildMap {
            val bundleOptions = mutableMapOf<String, MutableMap<String, Any?>>()

            // Theme patch options
            val themeOptionsYouTubeMusic = mutableMapOf<String, Any?>()
            darkThemeBackgroundColorYouTubeMusic.get().takeIf { it.isNotBlank() }?.let {
                themeOptionsYouTubeMusic["darkThemeBackgroundColor"] = it
            }
            // YouTube Music doesn't have light theme option in patches
            if (themeOptionsYouTubeMusic.isNotEmpty()) {
                bundleOptions["Theme"] = themeOptionsYouTubeMusic
            }

            // Custom Branding patch options
            val brandingOptionsYouTubeMusic = mutableMapOf<String, Any?>()
            customAppNameYouTubeMusic.get().takeIf { it.isNotBlank() }?.let {
                brandingOptionsYouTubeMusic["customName"] = it
            }
            customIconPathYouTubeMusic.get().takeIf { it.isNotBlank() }?.let {
                brandingOptionsYouTubeMusic["customIcon"] = it
            }
            if (brandingOptionsYouTubeMusic.isNotEmpty()) {
                bundleOptions["Custom branding"] = brandingOptionsYouTubeMusic
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
        darkThemeBackgroundColorYouTube.value = "@android:color/black"
        lightThemeBackgroundColorYouTube.value = "@android:color/white"
        customAppNameYouTube.value = ""
        customIconPathYouTube.value = ""
        customHeaderPath.value = ""
        hideShortsAppShortcut.value = false
        hideShortsWidget.value = false

        // YouTube Music
        darkThemeBackgroundColorYouTubeMusic.value = "@android:color/black"
        customAppNameYouTubeMusic.value = ""
        customIconPathYouTubeMusic.value = ""
    }
}
