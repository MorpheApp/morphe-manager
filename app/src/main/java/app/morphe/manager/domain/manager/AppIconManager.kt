package app.morphe.manager.domain.manager

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
            aliasName = "app.morphe.manager.MainActivity_Default",
            displayNameResId = R.string.settings_appearance_app_icon_default,
            previewIconResId = R.mipmap.ic_launcher
        ),

        // Light variant 2 (Sky theme)
        LIGHT_2(
            aliasName = "app.morphe.manager.MainActivity_Light_2",
            displayNameResId = R.string.settings_appearance_app_icon_light_2,
            previewIconResId = R.mipmap.ic_launcher_light_2
        ),

        // Light variant 3 (Sunset theme)
        LIGHT_3(
            aliasName = "app.morphe.manager.MainActivity_Light_3",
            displayNameResId = R.string.settings_appearance_app_icon_light_3,
            previewIconResId = R.mipmap.ic_launcher_light_3
        ),

        // Dark variant 1 (Ocean theme)
        DARK_1(
            aliasName = "app.morphe.manager.MainActivity_Dark_1",
            displayNameResId = R.string.settings_appearance_app_icon_dark_1,
            previewIconResId = R.mipmap.ic_launcher_dark_1
        ),

        // Dark variant 2 (Void theme)
        DARK_2(
            aliasName = "app.morphe.manager.MainActivity_Dark_2",
            displayNameResId = R.string.settings_appearance_app_icon_dark_2,
            previewIconResId = R.mipmap.ic_launcher_dark_2
        ),

        // Dark variant 3 (Indigo theme)
        DARK_3(
            aliasName = "app.morphe.manager.MainActivity_Dark_3",
            displayNameResId = R.string.settings_appearance_app_icon_dark_3,
            previewIconResId = R.mipmap.ic_launcher_dark_3
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
        // Disable all icons
        AppIcon.entries.forEach { otherIcon ->
            packageManager.setComponentEnabledSetting(
                otherIcon.getComponentName(context),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        }

        // Enable the selected icon
        packageManager.setComponentEnabledSetting(
            icon.getComponentName(context),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            0
        )
    }
}
