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
import androidx.compose.ui.BiasAlignment
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.MorpheAnimations

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
    var onScrollToInstaller: (() -> Unit)? = null
    var onScrollToProcessRuntime: (() -> Unit)? = null
    var onScrollSystemToBottom: (() -> Unit)? = null
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
            val screenH = constraints.maxHeight.toFloat()
            val isBottomHalf = bounds != null && bounds.top - selfOffset.y > screenH * 0.55f

            val cardBias by animateFloatAsState(
                targetValue = if (isBottomHalf) -1f else 1f,
                animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                label = "obs_card_bias"
            )
            // When the card is at the bottom we must clear the system nav bar AND the app's bottom action bar
            val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val cardVertPadding by animateDpAsState(
                targetValue = if (isBottomHalf) 64.dp else navBarPadding + 72.dp,
                animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                label = "obs_card_pad"
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
                onSkip = onSkip,
                modifier = Modifier
                    .align(BiasAlignment(horizontalBias = 0f, verticalBias = cardBias))
                    .padding(horizontal = 24.dp)
                    .padding(vertical = cardVertPadding)
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
                    Text(stringResource(R.string.onboarding_skip))
                }
                Button(onClick = onNext) {
                    Text(
                        if (step == totalSteps) stringResource(R.string.onboarding_done)
                        else stringResource(R.string.onboarding_next)
                    )
                }
            }
        }
    }
}
