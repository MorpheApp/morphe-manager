/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import kotlinx.coroutines.launch

private val ButtonSize: Dp = 44.dp
private val ButtonPadding: Dp = 16.dp
private val DefaultScrollThresholdDp: Dp = 600.dp

/**
 * Floating "scroll to top" button for a LazyColumn.
 * Auto-hides when the list is near the top; place inside a Box that overlays the list.
 */
@Composable
fun BoxScope.ScrollToTopButton(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    firstVisibleItemThreshold: Int = 3,
    extraBottomPadding: Dp = 0.dp
) {
    val scope = rememberCoroutineScope()
    val visible by remember(listState, firstVisibleItemThreshold) {
        derivedStateOf { listState.firstVisibleItemIndex >= firstVisibleItemThreshold }
    }
    ScrollToTopButtonImpl(
        visible = visible,
        onClick = { scope.launch { listState.animateScrollToItem(0) } },
        modifier = modifier.align(Alignment.BottomEnd),
        extraBottomPadding = extraBottomPadding
    )
}

/**
 * Floating "scroll to top" button for a Column with verticalScroll.
 * Auto-hides when content is near the top; place inside a Box that overlays the content.
 */
@Composable
fun BoxScope.ScrollToTopButton(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    scrollThreshold: Dp = DefaultScrollThresholdDp,
    extraBottomPadding: Dp = 0.dp
) {
    val scope = rememberCoroutineScope()
    val thresholdPx = with(LocalDensity.current) { scrollThreshold.toPx().toInt() }
    val visible by remember(scrollState, thresholdPx) {
        derivedStateOf { scrollState.value >= thresholdPx }
    }
    ScrollToTopButtonImpl(
        visible = visible,
        onClick = { scope.launch { scrollState.animateScrollTo(0) } },
        modifier = modifier.align(Alignment.BottomEnd),
        extraBottomPadding = extraBottomPadding
    )
}

@Composable
private fun ScrollToTopButtonImpl(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    extraBottomPadding: Dp = 0.dp
) {
    val a11y = rememberAccessibilityEnabled()
    val view = LocalView.current
    val label = stringResource(R.string.accessibility_scroll_to_top)

    AnimatedVisibility(
        visible = visible,
        enter = if (a11y) MorpheAnimations.fadeIn else MorpheAnimations.fabEnter,
        exit = if (a11y) MorpheAnimations.fadeOut else MorpheAnimations.fabExit,
        modifier = modifier.padding(
            end = ButtonPadding,
            bottom = ButtonPadding + extraBottomPadding
        )
    ) {
        val background = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
        val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(ButtonSize)
                .clip(CircleShape)
                .background(background)
                .border(1.dp, borderColor, CircleShape)
                .semantics { role = Role.Button }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true),
                    onClickLabel = label
                ) {
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    onClick()
                }
        ) {
            MorpheIcon(
                icon = Icons.Rounded.KeyboardArrowUp,
                contentDescription = label
            )
        }
    }
}
