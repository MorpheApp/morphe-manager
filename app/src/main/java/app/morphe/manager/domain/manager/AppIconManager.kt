/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.manager

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Switches the launcher icon between the [AppIcon]s generated from the catalog in
 * build.gradle.kts, each shown by an activity-alias of its own.
 */
class AppIconManager(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager

    /**
     * Get currently active app icon
     */
    fun getCurrentIcon(): AppIcon {
        return AppIcon.entries.firstOrNull { icon ->
            val state = packageManager.getComponentEnabledSetting(icon.componentName(context))
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
                otherIcon.componentName(context),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        }

        // Enable the selected icon
        packageManager.setComponentEnabledSetting(
            icon.componentName(context),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            0
        )
    }
}

fun AppIcon.componentName(context: Context) = ComponentName(context.packageName, aliasName)

/** The icon built from [background] and [mark], or null for a pair that does not read. */
fun AppIcon.Companion.of(background: IconBackground, mark: IconMark): AppIcon? =
    AppIcon.entries.firstOrNull { it.background == background && it.mark == mark }
