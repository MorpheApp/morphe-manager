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

    // GmsCore Support
    val gmsCoreVendorGroupId = stringPreference(
        "gmscore_vendor_group_id",
        "app.revanced"
    )

    // Theme - Dark
    val darkThemeBackgroundColor = stringPreference(
        "dark_theme_background_color",
        "@android:color/black"
    )

    // Theme - Light (YouTube only)
    val lightThemeBackgroundColor = stringPreference(
        "light_theme_background_color",
        "@android:color/white"
    )

    // Custom Branding
    val customAppName = stringPreference(
        "custom_app_name",
        ""
    )

    val customIconPath = stringPreference(
        "custom_icon_path",
        ""
    )

    // Custom Header (YouTube only)
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

    /**
     * Export all patch options as a map
     * This is used by the patcher to apply options to patches
     */
    suspend fun exportPatchOptions(): Map<Int, Map<String, Map<String, Any?>>> {
        return buildMap {
            // Note: Bundle UID 0 is the default Morphe bundle
            val bundleOptions = mutableMapOf<String, MutableMap<String, Any?>>()

            // GmsCore Support patch options
            bundleOptions["GmsCore support"] = mutableMapOf(
                "gmsCoreVendorGroupId" to gmsCoreVendorGroupId.get()
            )

            // Theme patch options
            val themeOptions = mutableMapOf<String, Any?>()
            darkThemeBackgroundColor.get().takeIf { it.isNotBlank() }?.let {
                themeOptions["darkThemeBackgroundColor"] = it
            }
            lightThemeBackgroundColor.get().takeIf { it.isNotBlank() }?.let {
                themeOptions["lightThemeBackgroundColor"] = it
            }
            if (themeOptions.isNotEmpty()) {
                bundleOptions["Theme"] = themeOptions
            }

            // Custom Branding patch options
            val brandingOptions = mutableMapOf<String, Any?>()
            customAppName.get().takeIf { it.isNotBlank() }?.let {
                brandingOptions["customName"] = it
            }
            customIconPath.get().takeIf { it.isNotBlank() }?.let {
                brandingOptions["customIcon"] = it
            }
            if (brandingOptions.isNotEmpty()) {
                bundleOptions["Custom branding"] = brandingOptions
            }

            // Change Header patch options (YouTube only)
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

            // Bundle ID 0 = default Morphe bundle (using Int key)
            if (bundleOptions.isNotEmpty()) {
                put(0, bundleOptions)
            }
        }
    }

    /**
     * Reset all patch options to defaults
     */
    suspend fun resetToDefaults() = edit {
        gmsCoreVendorGroupId.value = "app.revanced"
        darkThemeBackgroundColor.value = "@android:color/black"
        lightThemeBackgroundColor.value = "@android:color/white"
        customAppName.value = ""
        customIconPath.value = ""
        customHeaderPath.value = ""
        hideShortsAppShortcut.value = false
        hideShortsWidget.value = false
    }
}
