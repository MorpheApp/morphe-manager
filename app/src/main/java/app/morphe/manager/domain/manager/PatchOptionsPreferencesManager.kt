package app.morphe.manager.domain.manager

import android.content.Context
import app.morphe.manager.domain.manager.base.BasePreferencesManager
import app.morphe.manager.util.KnownApp

/**
 * Manages patch-specific option values that are applied during patching.
 * This manager only stores the values - the available options are fetched
 * dynamically from the patch bundle repository.
 *
 * Storage keys follow the pattern: {package}_{patchName}_{optionKey}
 */
class PatchOptionsPreferencesManager(
    context: Context
) : BasePreferencesManager(context, "patch_options") {

    companion object {
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

    // Theme - Dark
    fun darkThemeColor(packageName: String) = stringPreference(
        "${packageName}_${PATCH_THEME}_${KEY_DARK_THEME_COLOR}",
        DEFAULT_DARK_THEME
    )

    // Theme - Light
    fun lightThemeColor(packageName: String) = stringPreference(
        "${packageName}_${PATCH_THEME}_${KEY_LIGHT_THEME_COLOR}",
        DEFAULT_LIGHT_THEME
    )

    // Custom Branding - App Name
    fun customAppName(packageName: String) = stringPreference(
        "${packageName}_${PATCH_CUSTOM_BRANDING}_${KEY_CUSTOM_NAME}",
        ""
    )

    // Custom Branding - Icon Path
    fun customIconPath(packageName: String) = stringPreference(
        "${packageName}_${PATCH_CUSTOM_BRANDING}_${KEY_CUSTOM_ICON}",
        ""
    )

    // Change Header - Custom Header Path (YouTube only)
    val customHeaderPath = stringPreference(
        "${KnownApp.YOUTUBE}_${PATCH_CHANGE_HEADER}_${KEY_CUSTOM_HEADER}",
        ""
    )

    // Hide Shorts - App Shortcut (YouTube only)
    val hideShortsAppShortcut = booleanPreference(
        "${KnownApp.YOUTUBE}_${PATCH_HIDE_SHORTS}_${KEY_HIDE_SHORTS_APP_SHORTCUT}",
        false
    )

    // Hide Shorts - Widget (YouTube only)
    val hideShortsWidget = booleanPreference(
        "${KnownApp.YOUTUBE}_${PATCH_HIDE_SHORTS}_${KEY_HIDE_SHORTS_WIDGET}",
        false
    )

    /**
     * Export patch options for a given package.
     * Format: Map<BundleUid, Map<PatchName, Map<OptionKey, Value>>>
     */
    suspend fun exportPatchOptions(packageName: String): Map<Int, Map<String, Map<String, Any?>>> {
        return buildMap {
            val bundleOptions = mutableMapOf<String, MutableMap<String, Any?>>()

            // Theme patch options
            val themeOptions = mutableMapOf<String, Any?>()
            darkThemeColor(packageName).get()
                .takeIf { it.isNotBlank() && it != DEFAULT_DARK_THEME }
                ?.let { themeOptions[KEY_DARK_THEME_COLOR] = it }

            // Light theme — YouTube only
            if (packageName == KnownApp.YOUTUBE) {
                lightThemeColor(packageName).get()
                    .takeIf { it.isNotBlank() && it != DEFAULT_LIGHT_THEME }
                    ?.let { themeOptions[KEY_LIGHT_THEME_COLOR] = it }
            }
            if (themeOptions.isNotEmpty()) bundleOptions[PATCH_THEME] = themeOptions

            // Custom Branding patch options
            val brandingOptions = mutableMapOf<String, Any?>()
            customAppName(packageName).get()
                .takeIf { it.isNotBlank() }
                ?.let { brandingOptions[KEY_CUSTOM_NAME] = it }
            customIconPath(packageName).get()
                .takeIf { it.isNotBlank() }
                ?.let { brandingOptions[KEY_CUSTOM_ICON] = it }
            if (brandingOptions.isNotEmpty()) bundleOptions[PATCH_CUSTOM_BRANDING] = brandingOptions

            // Change Header + Hide Shorts — YouTube only
            if (packageName == KnownApp.YOUTUBE) {
                customHeaderPath.get()
                    .takeIf { it.isNotBlank() }
                    ?.let { bundleOptions[PATCH_CHANGE_HEADER] = mutableMapOf(KEY_CUSTOM_HEADER to it) }

                val shortsOptions = mutableMapOf<String, Any?>()
                if (hideShortsAppShortcut.get()) shortsOptions[KEY_HIDE_SHORTS_APP_SHORTCUT] = true
                if (hideShortsWidget.get()) shortsOptions[KEY_HIDE_SHORTS_WIDGET] = true
                if (shortsOptions.isNotEmpty()) bundleOptions[PATCH_HIDE_SHORTS] = shortsOptions
            }

            // Bundle ID 0 = default Morphe bundle
            if (bundleOptions.isNotEmpty()) put(0, bundleOptions)
        }
    }
}

/**
 * Gets localized text if it matches original English text, otherwise returns the custom text from patch.
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
