/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morphe.manager.R

private const val EnterMillis = 200

/** Emphasis of a [BottomActionButton], resolved through [GlassButtonDefaults]. */
enum class BottomActionTone {
    Neutral,
    Accent,
    Highlight,
    Destructive
}

/** Content receiver of [BottomActionBar]: a [RowScope] plus the state its buttons need. */
class BottomActionBarScope internal constructor(
    rowScope: RowScope,
    val showLabels: Boolean,
    internal val animateEntry: Boolean
) : RowScope by rowScope

/**
 * Centered, width-capped row of [BottomActionButton]s, weighted equally and animated as the set
 * changes. [labels] lists one entry per button so the bar decides once whether they all fit.
 *
 * [horizontalPadding] is what the content above the bar is inset by, so the buttons line up with
 * it. The default suits a bar nested in a column that carries the screen padding already.
 */
@Composable
fun BottomActionBar(
    modifier: Modifier = Modifier,
    labels: List<String> = emptyList(),
    horizontalPadding: Dp = Defaults.ContentPadding,
    content: @Composable BottomActionBarScope.() -> Unit
) {
    val reduceMotion = rememberAccessibilityEnabled()

    // Buttons present when the bar itself appears are drawn in place; only later arrivals fade
    val settled = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { settled.value = true }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val barWidth = maxWidth.coerceAtMost(Defaults.ContentMaxWidth)
        val showLabels = labelsFit(labels, barWidth, horizontalPadding)

        Row(
            modifier = Modifier
                .width(barWidth)
                .align(Alignment.Center)
                // The gap between buttons and the gap below them are one rhythm, so the
                // bar reads as evenly spaced rather than wider in one direction
                .padding(bottom = Defaults.ItemSpacing)
                .padding(horizontal = horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(Defaults.ItemSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomActionBarScope(
                rowScope = this,
                showLabels = showLabels,
                animateEntry = settled.value && !reduceMotion
            ).content()
        }
    }
}

private val TailWidth = 14.dp
private val TailHeight = 7.dp

/**
 * Callout that sits above a [BottomActionBar] and points down at the button at [slot].
 *
 * The tail is placed by a row shaped like the bar itself, so it lands on the button whatever
 * width the bar works out to.
 */
@Composable
fun BottomActionCallout(
    text: String,
    slot: Int,
    slots: Int,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    horizontalPadding: Dp = Defaults.ContentPadding
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val barWidth = maxWidth.coerceAtMost(Defaults.ContentMaxWidth)

        Column(
            modifier = Modifier
                .width(barWidth)
                .align(Alignment.Center)
                .padding(horizontal = horizontalPadding)
        ) {
            Surface(
                shape = RoundedCornerShape(Defaults.CompactCornerRadius),
                color = containerColor,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(Defaults.IconSizeSmall)
                        )
                    }
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Defaults.ItemSpacing)
            ) {
                repeat(slots) { index ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                        if (index == slot) CalloutTail(color = containerColor)
                    }
                }
            }
        }
    }
}

@Composable
private fun CalloutTail(color: Color) {
    Canvas(modifier = Modifier.size(width = TailWidth, height = TailHeight)) {
        drawPath(
            path = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width / 2f, size.height)
                close()
            },
            color = color
        )
    }
}

/**
 * Single button of a [BottomActionBar]. A hidden label falls back to a tooltip;
 * [showProgress] swaps the icon for a spinner.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomActionBarScope.BottomActionButton(
    onClick: () -> Unit,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    text: String? = null,
    showLabel: Boolean = false,
    tone: BottomActionTone = BottomActionTone.Neutral,
    enabled: Boolean = true,
    showProgress: Boolean = false,
    contentDescription: String? = null,
    stateDescription: String? = null
) {
    val colors = tone.colors()
    val loadingLabel = stringResource(R.string.loading)

    val label = contentDescription ?: text
    val accessibleLabel = remember(label, showProgress, loadingLabel) {
        when {
            label == null -> null
            showProgress -> "$label, $loadingLabel"
            else -> label
        }
    }

    // Entry is animated through the weight, so the row genuinely reflows and neighbors can never
    // be drawn over each other. A button that arrives with the bar is laid out at full width
    val entering = remember { animateEntry }
    val entry = remember { Animatable(if (entering) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (entering) entry.animateTo(1f, tween(EnterMillis))
    }

    val button: @Composable (Modifier) -> Unit = { outerModifier ->
        GlassButton(
            label = text.orEmpty(),
            selected = false,
            onClick = onClick,
            modifier = outerModifier
                .fillMaxWidth()
                .semantics {
                    if (stateDescription != null) {
                        this.stateDescription = stateDescription
                    }
                    if (showProgress) {
                        liveRegion = LiveRegionMode.Polite
                    }
                },
            icon = icon,
            enabled = enabled,
            showProgress = showProgress,
            contentDescription = accessibleLabel,
            containerColor = colors.container.dim(enabled),
            contentColor = colors.content.dim(enabled),
            border = BorderStroke(1.dp, colors.border.dim(enabled)),
            role = Role.Button,
            pressScale = true,
            hapticFeedback = true,
            showLabel = showLabel
        )
    }

    // The weight must land on this Box so Row still sees it as the direct child; TooltipBox
    // applies its own modifier to an inner wrapper, which Row can't see. Weight has to stay
    // above zero, and the content is clipped while the slot is still narrower than it
    Box(
        modifier = Modifier
            .weight(entry.value.coerceAtLeast(0.001f))
            .clipToBounds()
            .then(if (entering) Modifier.graphicsLayer { alpha = entry.value } else Modifier)
            .then(modifier)
    ) {
        // Only surface a tooltip when the label itself is hidden; otherwise the two would repeat.
        // It carries the accessible label, so a qualified icon explains itself without a reader
        if (!showLabel && label != null) {
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                tooltip = { PlainTooltip { Text(label) } },
                state = rememberTooltipState(),
                modifier = Modifier.fillMaxWidth()
            ) {
                button(Modifier)
            }
        } else {
            button(Modifier.fillMaxWidth())
        }
    }
}

/** Whether every one of [labels] fits its slot of a bar [barWidth] wide. */
@Composable
private fun labelsFit(labels: List<String>, barWidth: Dp, horizontalPadding: Dp): Boolean {
    if (labels.isEmpty()) return false

    val measurer = rememberTextMeasurer()
    val style = GlassButtonDefaults.labelStyle
    val density = LocalDensity.current

    return remember(labels, barWidth, horizontalPadding, style, density, measurer) {
        val slotWidth = (barWidth - horizontalPadding * 2 -
                Defaults.ItemSpacing * (labels.size - 1)) / labels.size
        // What a button spends on everything but the label
        val inset = GlassButtonDefaults.HorizontalPadding * 2 +
                GlassButtonDefaults.IconSize + GlassButtonDefaults.IconLabelSpacing
        val labelWidth = slotWidth - inset
        labelWidth > 0.dp && labels.all { label ->
            val measured = with(density) { measurer.measure(label, style).size.width.toDp() }
            measured <= labelWidth
        }
    }
}

@Immutable
private data class BottomActionColors(
    val container: Color,
    val content: Color,
    val border: Color
)

@Composable
private fun BottomActionTone.colors(): BottomActionColors {
    val scheme = MaterialTheme.colorScheme
    // Every tone but Neutral borrows the selected treatment of the tab bar, so an emphasized
    // action reads at the same weight as the active settings tab
    return when (this) {
        BottomActionTone.Neutral -> BottomActionColors(
            container = GlassButtonDefaults.containerColor(),
            content = GlassButtonDefaults.contentColor(),
            border = GlassButtonDefaults.borderColor()
        )

        BottomActionTone.Accent -> BottomActionColors(
            container = GlassButtonDefaults.containerColor(selected = true),
            content = GlassButtonDefaults.contentColor(selected = true),
            border = GlassButtonDefaults.borderColor(selected = true)
        )

        BottomActionTone.Highlight -> BottomActionColors(
            container = GlassButtonDefaults.containerColor(scheme.tertiaryContainer, selected = true),
            content = GlassButtonDefaults.contentColor(scheme.onTertiaryContainer, selected = true),
            border = GlassButtonDefaults.borderColor(scheme.tertiary, selected = true)
        )

        BottomActionTone.Destructive -> BottomActionColors(
            container = GlassButtonDefaults.containerColor(scheme.errorContainer, selected = true),
            content = GlassButtonDefaults.contentColor(scheme.onErrorContainer, selected = true),
            border = GlassButtonDefaults.borderColor(scheme.error, selected = true)
        )
    }
}

/** Halves the alpha of a resolved glass color while the button is disabled. */
private fun Color.dim(enabled: Boolean): Color =
    if (enabled) this else copy(alpha = alpha * 0.5f)
