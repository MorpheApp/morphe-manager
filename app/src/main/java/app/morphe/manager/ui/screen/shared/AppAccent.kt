/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.ui.theme.MonochromeThemeDefaults
import app.morphe.manager.util.isExtremeAccent
import org.koin.compose.koinInject

/**
 * Fill of a surface that carries an app's own color, the way the app details and the patch lists
 * head themselves.
 */
@Composable
fun appAccentFill(accentColor: Color?): Color =
    appAccentTint(accentColor, alpha = 0.15f, neutral = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f))

/**
 * Border of a card that carries an app's own color. The fill behind the card's text stays muted
 * for the text's sake, so the edge is where the color shows.
 */
@Composable
fun appAccentBorder(accentColor: Color?): Color =
    appAccentTint(accentColor, alpha = 0.4f, neutral = MaterialTheme.colorScheme.outlineVariant)

/**
 * A [StatusBadge] laid on a surface in an app's own color, a step stronger than [appAccentFill] so
 * it stands out of it. Without a color it keeps [tone]'s own look.
 */
@Composable
fun AppAccentBadge(
    text: String,
    accentColor: Color?,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tone: SemanticTone = SemanticTone.Neutral,
    onClick: (() -> Unit)? = null
) {
    StatusBadge(
        text = text,
        modifier = modifier,
        icon = icon,
        tone = tone,
        containerColor = if (accentColor != null) {
            appAccentTint(accentColor, alpha = 0.18f, neutral = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f))
        } else {
            tone.container
        },
        contentColor = if (accentColor != null) MaterialTheme.colorScheme.onBackground else tone.content,
        onClick = onClick
    )
}

/** Color the sources declare [packageName] with, or null where none of them does. */
@Composable
fun rememberAppColor(packageName: String): Color? {
    val patchBundleRepository: PatchBundleRepository = koinInject()
    val metadata by patchBundleRepository.allAppMetadata.collectAsStateWithLifecycle()
    return metadata[packageName]?.downloadColor
}

/**
 * [accentColor] at [alpha], or [neutral] where there is none. A near-black or near-white color
 * reads as a stain rather than a tint, so it goes neutral too, and the monochrome theme swaps the
 * color for its own accent.
 */
@Composable
private fun appAccentTint(accentColor: Color?, alpha: Float, neutral: Color): Color {
    val accent = accentColor?.let { MonochromeThemeDefaults.accentColor(it) }
    return if (accent == null || accent.isExtremeAccent()) neutral else accent.copy(alpha = alpha)
}
