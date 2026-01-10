package app.revanced.manager.domain.manager

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import app.morphe.manager.R

/**
 * Manager for changing app launcher icons
 */
class AppIconManager(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager

    /**
     * Available app icon variants
     */
    enum class AppIcon(
        val aliasName: String,
        val displayNameResId: Int,
        @DrawableRes val previewIconResId: Int
    ) {
        DEFAULT(
            aliasName = "app.revanced.manager.MainActivityDefault",
            displayNameResId = R.string.morphe_app_icon_default,
            previewIconResId = R.mipmap.ic_launcher
        ),
        SKY(
            aliasName = "app.revanced.manager.MainActivitySky",
            displayNameResId = R.string.morphe_app_icon_sky,
            previewIconResId = R.mipmap.ic_launcher_sky
        ),
        FROST(
            aliasName = "app.revanced.manager.MainActivityFrost",
            displayNameResId = R.string.morphe_app_icon_frost,
            previewIconResId = R.mipmap.ic_launcher_frost
        ),

        OCEAN(
            aliasName = "app.revanced.manager.MainActivityOcean",
            displayNameResId = R.string.morphe_app_icon_ocean,
            previewIconResId = R.mipmap.ic_launcher_ocean
        ),
        VOID(
            aliasName = "app.revanced.manager.MainActivityVoid",
            displayNameResId = R.string.morphe_app_icon_void,
            previewIconResId = R.mipmap.ic_launcher_void
        ),
        ABYSS(
            aliasName = "app.revanced.manager.MainActivityAbyss",
            displayNameResId = R.string.morphe_app_icon_abyss,
            previewIconResId = R.mipmap.ic_launcher_abyss
        );

        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context.packageName, aliasName)
        }
    }

    /**
     * Get currently active app icon
     */
    fun getCurrentIcon(): AppIcon {
        return AppIcon.entries.firstOrNull { icon ->
            val componentName = icon.getComponentName(context)
            val state = packageManager.getComponentEnabledSetting(componentName)
            state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } ?: AppIcon.DEFAULT
    }

    /**
     * Set active app icon
     * Note: This will restart the app to apply changes
     */
    fun setIcon(icon: AppIcon) {
        // Disable all other icons
        AppIcon.entries.forEach { otherIcon ->
            if (otherIcon != icon) {
                packageManager.setComponentEnabledSetting(
                    otherIcon.getComponentName(context),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
        }

        // Enable selected icon
        packageManager.setComponentEnabledSetting(
            icon.getComponentName(context),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    /**
     * Check if an icon is currently active
     */
    fun isIconActive(icon: AppIcon): Boolean {
        val componentName = icon.getComponentName(context)
        val state = packageManager.getComponentEnabledSetting(componentName)
        return state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                (state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && icon == AppIcon.DEFAULT)
    }
}
