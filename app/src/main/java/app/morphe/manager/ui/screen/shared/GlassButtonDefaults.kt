/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Shared color tokens for the frosted-glass button/row family used across the home
 * surface (pill buttons, category headers, segmented tabs) and the settings tab bar.
 * Retune once here to shift the palette everywhere consistently.
 */
object GlassButtonDefaults {
    @Composable
    fun containerColor(selected: Boolean = false): Color {
        val isDark = isSystemInDarkTheme()
        val backgroundAlpha = if (isDark) 0.35f else 0.6f
        return if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isDark) 0.55f else 0.72f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = backgroundAlpha)
        }
    }

    @Composable
    fun contentColor(selected: Boolean = false): Color =
        if (selected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant

    @Composable
    fun borderColor(selected: Boolean = false): Color {
        val isDark = isSystemInDarkTheme()
        val borderAlpha = if (isDark) 0.4f else 0.6f
        return if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.55f else 0.45f)
        } else {
            MaterialTheme.colorScheme.outline.copy(alpha = borderAlpha)
        }
    }
}
