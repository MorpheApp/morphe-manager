/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import app.morphe.manager.ui.theme.ThemeTraitsDefaults
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur

/**
 * What the headers, bars, panels and buttons floating over a screen or a [ListDialog] blur,
 * handed down from the content they float over. Null where nothing is, which leaves them solid.
 *
 * Provided only to what floats over the content marked as its source, never inside that content,
 * where a surface would blur the layer it is drawn into. A dialog or sheet resets it to null, as
 * its window cannot draw the one it was opened from.
 */
val LocalBackdropHaze = staticCompositionLocalOf<HazeState?> { null }

/**
 * A surface floating over content that scrolls on under it: the content blurred and laid under
 * [tint], thin enough to hint at what passes beneath. Where nothing blurs, the surface is as solid
 * as one drawn without a blur, so the content stays hidden either way.
 */
fun glassBlurStyle(tint: Color, background: Color): HazeBlurStyle {
    val solid = tint.compositeOver(background)
    return HazeBlurStyle {
        backgroundColor(background)
        colorEffects(listOf(HazeColorEffect.tint(solid.copy(alpha = 0.55f))))
        fallbackColorEffect(HazeColorEffect.tint(solid))
    }
}

/**
 * Fills a floating surface with what scrolls under it blurred under its [glass] tint, see
 * [glassBlurStyle], or with the tint laid on the background where there is no [haze] to blur or
 * the appearance settings turn blurring off.
 */
@Composable
fun Modifier.glassFill(
    glass: Color,
    shape: Shape,
    haze: HazeState? = LocalBackdropHaze.current
): Modifier {
    val screen = MaterialTheme.colorScheme.background
    if (haze == null || !ThemeTraitsDefaults.blur()) return background(glass.compositeOver(screen), shape)
    val style = remember(glass, screen) { glassBlurStyle(glass, screen) }
    return clip(shape).hazeBlur(HazeInput.Sources(haze), style)
}
