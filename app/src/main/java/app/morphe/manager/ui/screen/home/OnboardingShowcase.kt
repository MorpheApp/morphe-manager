/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.MorpheAnimations
import kotlin.math.roundToInt

/** Set to true to show onboarding every launch regardless of firstLaunch preference. */
// TODO: Set to false before release
internal val ONBOARDING_TESTING_MODE: Boolean get() = true

/** One coach-mark step: resource IDs for text, a lambda that returns the current target bounds,
 *  and an optional callback fired when this step becomes active (e.g. to navigate a pager). */
data class StepDef(
    val titleRes: Int,
    val descRes: Int,
    val getBounds: () -> Rect?,
    val onShow: (() -> Unit)? = null
)

/** Holds the window-space bounds for each home-screen spotlight target. */
class OnboardingState {
    var sourcesButtonBounds by mutableStateOf<Rect?>(null)
    var firstAppCardBounds by mutableStateOf<Rect?>(null)
    var settingsButtonBounds by mutableStateOf<Rect?>(null)
    var swipeActive by mutableStateOf(false)
}

/** Holds window-space bounds and nav callbacks for Settings-screen spotlight targets. */
class GlobalOnboardingState {
    // Sources sheet
    var sourcesPatchesBounds by mutableStateOf<Rect?>(null)
    var sourcesVersionBounds by mutableStateOf<Rect?>(null)
    var sourcesPrereleaseBounds by mutableStateOf<Rect?>(null)
    var sheetOnboardingActive by mutableStateOf(false)
    // Settings screen
    var appearanceTabBounds by mutableStateOf<Rect?>(null)
    var themeSelectorBounds by mutableStateOf<Rect?>(null)
    var expertModeBounds by mutableStateOf<Rect?>(null)
    var systemTabBounds by mutableStateOf<Rect?>(null)
    var installerSectionBounds by mutableStateOf<Rect?>(null)
    var processRuntimeBounds by mutableStateOf<Rect?>(null)
    var filePickerBounds by mutableStateOf<Rect?>(null)
    var onNavigateToAppearanceTab: (() -> Unit)? = null
    var onNavigateToAdvancedTab: (() -> Unit)? = null
    var onNavigateToSystemTab: (() -> Unit)? = null
    var onScrollToThemeSelector: (() -> Unit)? = null
    var onScrollToInstaller: (() -> Unit)? = null
    var onScrollToProcessRuntime: (() -> Unit)? = null
    var onScrollToFilePicker: (() -> Unit)? = null
    var onScrollToFirstSource: (() -> Unit)? = null
}

/**
 * Generic full-screen coach-marks overlay.
 * Accepts any list of [StepDef] so it can be reused across different screens and sheets.
 */
@Composable
fun OnboardingShowcase(
    steps: List<StepDef>,
    onComplete: () -> Unit,
    onSkip: () -> Unit = onComplete,
    stepOffset: Int = 0,
    totalStepsOverride: Int? = null
) {
    if (steps.isEmpty()) return
    var step by remember { mutableIntStateOf(0) }
    val stepDef = steps[step]
    val bounds = stepDef.getBounds()
    val displayStep = stepOffset + step + 1
    val displayTotal = totalStepsOverride ?: steps.size

    LaunchedEffect(step) { steps[step].onShow?.invoke() }

    val pulse = rememberInfiniteTransition(label = "obs_pulse")
    val pulseScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "obs_scale"
    )

    // Track the overlay's own position in the window so we can convert window-space
    // bounds (from boundsInWindow) to canvas-local coordinates. This is needed when
    // the overlay does not start at the window origin, e.g. inside a ModalBottomSheet.
    var selfOffset by remember { mutableStateOf(Offset.Zero) }

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(visible = visible, enter = MorpheAnimations.overlayEnter) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coords ->
                    val wb = coords.boundsInWindow()
                    selfOffset = Offset(wb.left, wb.top)
                }
                .clickable(remember { MutableInteractionSource() }, indication = null) {}
        ) {
            val density = LocalDensity.current
            val screenW = constraints.maxWidth
            val screenH = constraints.maxHeight
            // Card shows above the target when the target is in the lower half of the screen
            val isBottomHalf = bounds != null && (bounds.center.y - selfOffset.y) > screenH * 0.5f

            val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

            var cardSize by remember { mutableStateOf(IntSize.Zero) }

            val gap = with(density) { 16.dp.roundToPx() }
            val hPad = with(density) { 24.dp.roundToPx() }
            val navPx = with(density) { navBarPadding.roundToPx() }
            val statusPx = with(density) { statusBarPadding.roundToPx() }
            // Cap card width so it always fits within horizontal padding on any screen width
            val maxCardWidth = (screenW - 2 * hPad).coerceAtMost(with(density) { 380.dp.roundToPx() })
            val cw = cardSize.width.takeIf { it > 0 } ?: maxCardWidth
            val ch = cardSize.height.takeIf { it > 0 } ?: with(density) { 160.dp.roundToPx() }

            val targetX = if (bounds != null) {
                ((bounds.center.x - selfOffset.x) - cw / 2f).roundToInt()
                    .coerceIn(hPad, screenW - cw - hPad)
            } else (screenW - cw) / 2

            val targetY = if (bounds != null) {
                if (isBottomHalf)
                    // Show card just above the spotlight target
                    (bounds.top - selfOffset.y - ch - gap).roundToInt()
                        .coerceAtLeast(statusPx + gap)
                else
                    // Show card just below the spotlight target
                    (bounds.bottom - selfOffset.y + gap).roundToInt()
                        .coerceAtMost(screenH - ch - navPx - gap)
            } else ((screenH - ch) / 2).coerceAtLeast(statusPx)

            val cardOffsetX by animateIntAsState(
                targetValue = targetX,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "obs_card_x"
            )
            val cardOffsetY by animateIntAsState(
                targetValue = targetY,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "obs_card_y"
            )

            Canvas(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            ) {
                drawRect(Color.Black.copy(alpha = 0.72f))

                if (bounds != null) {
                    val pad = 12.dp.toPx()
                    val cx = bounds.center.x - selfOffset.x
                    val cy = bounds.center.y - selfOffset.y
                    val hw = (bounds.width / 2 + pad) * pulseScale
                    val hh = (bounds.height / 2 + pad) * pulseScale
                    drawRoundRect(
                        color = Color.Black,
                        topLeft = Offset(cx - hw, cy - hh),
                        size = Size(hw * 2, hh * 2),
                        cornerRadius = CornerRadius(20.dp.toPx()),
                        blendMode = BlendMode.Clear
                    )
                }
            }

            OnboardingCard(
                title = stringResource(stepDef.titleRes),
                description = stringResource(stepDef.descRes),
                step = displayStep,
                totalSteps = displayTotal,
                onNext = { if (step < steps.size - 1) step++ else onComplete() },
                onPrevious = if (step > 0) { { step-- } } else null,
                onSkip = onSkip,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset { IntOffset(cardOffsetX, cardOffsetY) }
                    .widthIn(max = with(density) { maxCardWidth.toDp() })
                    .onGloballyPositioned { cardSize = it.size }
            )
        }
    }
}

@Composable
private fun OnboardingCard(
    title: String,
    description: String,
    step: Int,
    totalSteps: Int,
    onNext: () -> Unit,
    onPrevious: (() -> Unit)?,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.widthIn(max = 380.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 8.dp,
        shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.onboarding_step_of, step, totalSteps),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.End)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onSkip) {
                    Text(stringResource(R.string.onboarding_skip), maxLines = 1)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onPrevious != null) {
                        OutlinedButton(onClick = onPrevious) {
                            Text(stringResource(R.string.onboarding_back), maxLines = 1)
                        }
                    }
                    Button(onClick = onNext) {
                        Text(
                            if (step == totalSteps) stringResource(R.string.onboarding_done)
                            else stringResource(R.string.onboarding_next),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
