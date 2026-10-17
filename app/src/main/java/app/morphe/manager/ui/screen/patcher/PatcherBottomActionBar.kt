/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.patcher

import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.ActionGlassButton
import app.morphe.manager.ui.screen.shared.BottomActionTone
import app.morphe.manager.ui.screen.shared.Defaults
import app.morphe.manager.ui.screen.shared.GlassButtonDefaults
import app.morphe.manager.ui.screen.shared.labelsFitSlots
import app.morphe.manager.ui.screen.shared.rememberAccessibilityEnabled
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/** Height of every patcher action, so the result's own row and the bar under it line up. */
internal val PatcherActionHeight = 56.dp

private const val EnterMillis = 200

/**
 * Patcher bottom action bar: a centered, width-capped row of equally weighted buttons, animated
 * as the set changes.
 *
 * Pass a zero [horizontalPadding] where the bar sits in a column that is inset already, so the
 * two insets do not stack and the buttons keep the edges of the content above them.
 *
 * A non-null [resultButton] leads back to the result screen in place of Cancel.
 */
@Composable
fun PatcherBottomActionBar(
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = Defaults.SpaceLarge,

    // Visibility control
    showCancelButton: Boolean = true,
    showHomeButton: Boolean = true,
    showCopyLogsButton: Boolean = false,
    showLogsButton: Boolean = false,
    resultButton: ResultButton? = null,

    // Actions, needed only for the buttons shown
    onCancelClick: () -> Unit = {},
    onHomeClick: () -> Unit = {},
    onCopyLogsClick: () -> Unit = {},
    onLogsClick: () -> Unit = {},
    onResultClick: () -> Unit = {}
) {
    // Tracks the brief "Copied!" feedback state on the copy button
    val copied = remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val reduceMotion = rememberAccessibilityEnabled()

    // Buttons present when the bar itself appears are drawn in place; only later arrivals fade
    val settled = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { settled.value = true }
    val animateEntry = settled.value && !reduceMotion

    // Only the buttons the current state calls for are emitted, and each one takes an equal
    // share of the row, so two actions split it in half and the third slots in between them
    val leadingLabel = when {
        resultButton != null -> stringResource(resultButton.label)
        showCancelButton -> stringResource(android.R.string.cancel)
        showLogsButton -> stringResource(R.string.logs)
        else -> ""
    }
    val homeLabel = if (showHomeButton) stringResource(R.string.home) else ""
    val trailingLabel = if (showCopyLogsButton) stringResource(android.R.string.copy) else ""
    val labels = remember(leadingLabel, homeLabel, trailingLabel) {
        listOf(leadingLabel, homeLabel, trailingLabel).filter { it.isNotEmpty() }
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val barWidth = maxWidth.coerceAtMost(Defaults.ContentMaxWidth)
        val showLabels = labelsFitSlots(
            labels = labels,
            style = GlassButtonDefaults.labelStyle,
            width = barWidth - horizontalPadding * 2,
            spacing = Defaults.SpaceMedium,
            // What a button spends on everything but the label
            inset = GlassButtonDefaults.HorizontalPadding * 2 + GlassButtonDefaults.IconSize +
                    GlassButtonDefaults.IconLabelSpacing
        )

        Row(
            modifier = Modifier
                .width(barWidth)
                .align(Alignment.Center)
                // The gap between buttons and the gap below them are one rhythm, so the
                // bar reads as evenly spaced rather than wider in one direction
                .padding(bottom = Defaults.SpaceMedium)
                .padding(horizontal = horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceMedium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Result / Cancel / Logs button
            if (resultButton != null) {
                BarButton(
                    onClick = onResultClick,
                    icon = resultButton.icon,
                    text = leadingLabel,
                    showLabel = showLabels,
                    animateEntry = animateEntry,
                    tone = resultButton.tone,
                    showProgress = resultButton.busy
                )
            } else if (showCancelButton) {
                BarButton(
                    onClick = onCancelClick,
                    icon = Icons.Default.Close,
                    text = leadingLabel,
                    showLabel = showLabels,
                    animateEntry = animateEntry,
                    tone = BottomActionTone.Destructive
                )
            } else if (showLogsButton) {
                BarButton(
                    onClick = onLogsClick,
                    icon = Icons.AutoMirrored.Outlined.Article,
                    text = leadingLabel,
                    showLabel = showLabels,
                    animateEntry = animateEntry
                )
            }

            // Center: Home button
            if (showHomeButton) {
                BarButton(
                    onClick = onHomeClick,
                    icon = Icons.Default.Home,
                    text = homeLabel,
                    showLabel = showLabels,
                    animateEntry = animateEntry
                )
            }

            // Right: Copy logs button
            if (showCopyLogsButton) {
                BarButton(
                    onClick = {
                        onCopyLogsClick()
                        scope.launch {
                            copied.value = true
                            delay(2.seconds)
                            copied.value = false
                        }
                    },
                    icon = Icons.Default.ContentCopy,
                    text = trailingLabel,
                    showLabel = showLabels,
                    animateEntry = animateEntry,
                    // The tone alone reports the copy, so the label keeps a stable width
                    tone = if (copied.value) BottomActionTone.Highlight else BottomActionTone.Neutral
                )
            }
        }
    }
}

/**
 * One button of [PatcherBottomActionBar]. A button arriving after the bar is up widens into its
 * share of the row rather than appearing at full width.
 */
@Composable
private fun RowScope.BarButton(
    onClick: () -> Unit,
    icon: ImageVector,
    text: String,
    showLabel: Boolean,
    animateEntry: Boolean,
    tone: BottomActionTone = BottomActionTone.Neutral,
    showProgress: Boolean = false
) {
    // Entry is animated through the weight, so the row genuinely reflows and neighbors can never
    // be drawn over each other. A button that arrives with the bar is laid out at full width
    val entering = remember { animateEntry }
    val entry = remember { Animatable(if (entering) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (entering) entry.animateTo(1f, tween(EnterMillis))
    }

    // Weight has to stay above zero, and the content is clipped while the slot is still
    // narrower than it
    Box(
        modifier = Modifier
            .weight(entry.value.coerceAtLeast(0.001f))
            .clipToBounds()
            .then(if (entering) Modifier.graphicsLayer { alpha = entry.value } else Modifier)
    ) {
        PatcherActionButton(
            onClick = onClick,
            icon = icon,
            text = text,
            showLabel = showLabel,
            tone = tone,
            showProgress = showProgress,
            modifier = Modifier
                .fillMaxWidth()
                .height(PatcherActionHeight)
        )
    }
}

/**
 * Glass action of the patcher screens, sized by [modifier]. A hidden label falls back to a
 * tooltip, which carries the accessible label too, so an icon alone still explains itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PatcherActionButton(
    onClick: () -> Unit,
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    showLabel: Boolean = false,
    tone: BottomActionTone = BottomActionTone.Neutral,
    enabled: Boolean = true,
    showProgress: Boolean = false,
    shape: Shape = Defaults.PillShape,
    interactionSource: MutableInteractionSource? = null
) {
    val button: @Composable () -> Unit = {
        ActionGlassButton(
            onClick = onClick,
            icon = icon,
            modifier = Modifier.fillMaxSize(),
            text = text,
            showLabel = showLabel,
            tone = tone,
            enabled = enabled,
            showProgress = showProgress,
            shape = shape,
            height = PatcherActionHeight,
            interactionSource = interactionSource
        )
    }

    // TooltipBox would bury the modifier in a wrapper a row can't read, so the Box carries it.
    // A tooltip only for a hidden label, otherwise the two would repeat
    Box(modifier = modifier, propagateMinConstraints = true) {
        if (showLabel) {
            button()
        } else {
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                tooltip = { PlainTooltip { Text(text) } },
                state = rememberTooltipState(),
                content = button
            )
        }
    }
}

private val TailWidth = 14.dp
private val TailHeight = 7.dp

/**
 * Callout that sits above a [PatcherBottomActionBar] and points down at the button at [slot].
 *
 * The tail is placed by a row shaped like the bar itself, so it lands on the button whatever
 * width the bar works out to.
 */
@Composable
internal fun PatcherActionCallout(
    text: String,
    slot: Int,
    slots: Int,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    horizontalPadding: Dp = Defaults.SpaceLarge
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
                shape = RoundedCornerShape(Defaults.CornerMedium),
                color = containerColor,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = Defaults.SpaceMedium, vertical = Defaults.SpaceSmall),
                    horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceSmall, Alignment.CenterHorizontally),
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
                horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceMedium)
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
 * The bar's way back to the result screen, named by where the run stands there so it never offers
 * an action the result screen has moved past. It only leads there, the actions stay on that screen.
 */
class ResultButton(
    @param:StringRes val label: Int,
    val icon: ImageVector,
    val tone: BottomActionTone = BottomActionTone.Accent,
    val busy: Boolean = false
) {
    companion object {
        /** For a result screen that has yet to install anything. */
        val Install = ResultButton(R.string.install, Icons.Outlined.InstallMobile)
    }
}
