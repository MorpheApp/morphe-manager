/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * How the controls of a header or a panel are drawn: its title actions and the search field they
 * open, one size and one see-through glass on every header, so what lies under them shows through.
 */
object HeaderControlDefaults {
    /** Size of a title action, and the height of the search field it opens into. */
    val Size = 40.dp

    /**
     * Fill of a control in [accent] where the header has one and the theme's glass otherwise,
     * stepped up while it is [active], such as an engaged toggle.
     */
    @Composable
    fun container(accent: Color? = LocalAccent.current, active: Boolean = false): Color =
        accent?.copy(alpha = if (active) ACTIVE_ALPHA else AccentAlpha.STEP)
            ?: GlassButtonDefaults.containerColor(selected = active)

    /** Fill of a control that leads its header, such as an add or reset action. */
    @Composable
    fun leadContainer(accent: Color? = LocalAccent.current): Color =
        accent?.copy(alpha = AccentAlpha.LEAD) ?: GlassButtonDefaults.containerColor(selected = true)

    /** Edge of an [active] control, so its state reads without a solid fill. Null while idle. */
    @Composable
    fun activeBorder(active: Boolean, accent: Color? = LocalAccent.current): Color? = when {
        !active -> null
        accent != null -> accent.copy(alpha = ACTIVE_BORDER_ALPHA)
        else -> GlassButtonDefaults.borderColor(selected = true)
    }

    /** Content over a [container], the header's text over a tint and the theme's pairing otherwise. */
    @Composable
    fun content(container: Color, active: Boolean = false, accent: Color? = LocalAccent.current): Color =
        if (accent != null) appAccentContent(container) else GlassButtonDefaults.contentColor(selected = active)

    private const val ACTIVE_ALPHA = 0.45f
    private const val ACTIVE_BORDER_ALPHA = 0.6f
}
