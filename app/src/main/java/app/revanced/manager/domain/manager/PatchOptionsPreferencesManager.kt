package app.revanced.manager.domain.manager

import android.content.Context
import app.revanced.manager.domain.manager.base.BasePreferencesManager

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
        // Package identifiers for storage keys
        const val YOUTUBE = "youtube"
        const val YOUTUBE_MUSIC = "youtube_music"

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
    }

    // ==================== YouTube Options ====================

    // Theme - Dark
    val darkThemeBackgroundColorYouTube = stringPreference(
        "${YOUTUBE}_${PATCH_THEME}_${KEY_DARK_THEME_COLOR}",
        DEFAULT_DARK_THEME
    )

    // Theme - Light
    val lightThemeBackgroundColorYouTube = stringPreference(
        "${YOUTUBE}_${PATCH_THEME}_${KEY_LIGHT_THEME_COLOR}",
        DEFAULT_LIGHT_THEME
    )

    // Custom Branding - App Name
    val customAppNameYouTube = stringPreference(
        "${YOUTUBE}_${PATCH_CUSTOM_BRANDING}_${KEY_CUSTOM_NAME}",
        ""
    )

    // Custom Branding - Icon Path
    val customIconPathYouTube = stringPreference(
        "${YOUTUBE}_${PATCH_CUSTOM_BRANDING}_${KEY_CUSTOM_ICON}",
        ""
    )

    // Change Header - Custom Header Path
    val customHeaderPath = stringPreference(
        "${YOUTUBE}_${PATCH_CHANGE_HEADER}_${KEY_CUSTOM_HEADER}",
        ""
    )

    // Hide Shorts - App Shortcut
    val hideShortsAppShortcut = booleanPreference(
        "${YOUTUBE}_${PATCH_HIDE_SHORTS}_${KEY_HIDE_SHORTS_APP_SHORTCUT}",
        false
    )

    // Hide Shorts - Widget
    val hideShortsWidget = booleanPreference(
        "${YOUTUBE}_${PATCH_HIDE_SHORTS}_${KEY_HIDE_SHORTS_WIDGET}",
        false
    )

    // ==================== YouTube Music Options ====================

    // Theme - Dark
    val darkThemeBackgroundColorYouTubeMusic = stringPreference(
        "${YOUTUBE_MUSIC}_${PATCH_THEME}_${KEY_DARK_THEME_COLOR}",
        DEFAULT_DARK_THEME
    )

    // Custom Branding - App Name
    val customAppNameYouTubeMusic = stringPreference(
        "${YOUTUBE_MUSIC}_${PATCH_CUSTOM_BRANDING}_${KEY_CUSTOM_NAME}",
        ""
    )

    // Custom Branding - Icon Path
    val customIconPathYouTubeMusic = stringPreference(
        "${YOUTUBE_MUSIC}_${PATCH_CUSTOM_BRANDING}_${KEY_CUSTOM_ICON}",
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

        return when {
            // YouTube Theme
            prefKey == "${YOUTUBE}_${PATCH_THEME}_${KEY_DARK_THEME_COLOR}" ->
                darkThemeBackgroundColorYouTube.get().takeIf { it.isNotBlank() }
            prefKey == "${YOUTUBE}_${PATCH_THEME}_${KEY_LIGHT_THEME_COLOR}" ->
                lightThemeBackgroundColorYouTube.get().takeIf { it.isNotBlank() }

            // YouTube Custom Branding
            prefKey == "${YOUTUBE}_${PATCH_CUSTOM_BRANDING}_${KEY_CUSTOM_NAME}" ->
                customAppNameYouTube.get().takeIf { it.isNotBlank() }
            prefKey == "${YOUTUBE}_${PATCH_CUSTOM_BRANDING}_${KEY_CUSTOM_ICON}" ->
                customIconPathYouTube.get().takeIf { it.isNotBlank() }

            // YouTube Change Header
            prefKey == "${YOUTUBE}_${PATCH_CHANGE_HEADER}_${KEY_CUSTOM_HEADER}" ->
                customHeaderPath.get().takeIf { it.isNotBlank() }

            // YouTube Hide Shorts
            prefKey == "${YOUTUBE}_${PATCH_HIDE_SHORTS}_${KEY_HIDE_SHORTS_APP_SHORTCUT}" ->
                hideShortsAppShortcut.get().takeIf { it }
            prefKey == "${YOUTUBE}_${PATCH_HIDE_SHORTS}_${KEY_HIDE_SHORTS_WIDGET}" ->
                hideShortsWidget.get().takeIf { it }

            // YouTube Music Theme
            prefKey == "${YOUTUBE_MUSIC}_${PATCH_THEME}_${KEY_DARK_THEME_COLOR}" ->
                darkThemeBackgroundColorYouTubeMusic.get().takeIf { it.isNotBlank() }

            // YouTube Music Custom Branding
            prefKey == "${YOUTUBE_MUSIC}_${PATCH_CUSTOM_BRANDING}_${KEY_CUSTOM_NAME}" ->
                customAppNameYouTubeMusic.get().takeIf { it.isNotBlank() }
            prefKey == "${YOUTUBE_MUSIC}_${PATCH_CUSTOM_BRANDING}_${KEY_CUSTOM_ICON}" ->
                customIconPathYouTubeMusic.get().takeIf { it.isNotBlank() }

            else -> null
        }
    }

    /**
     * Export all patch options as a map for the patcher.
     * Format: Map<BundleUid, Map<PatchName, Map<OptionKey, Value>>>
     */
    suspend fun exportPatchOptions(): Map<Int, Map<String, Map<String, Any?>>> {
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
