/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.appearance

import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.domain.manager.AppIcon
import app.morphe.manager.domain.manager.AppIconManager
import app.morphe.manager.domain.manager.IconBackground
import app.morphe.manager.domain.manager.IconMark
import app.morphe.manager.domain.manager.of
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.util.htmlAnnotatedString
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Settings row showing the launcher icon in use, opening a picker on tap where the icon is built
 * from a background and a logo. The result is confirmed before the launcher icon is swapped.
 */
@Composable
fun AppIconSettingsItem() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val iconManager = remember { AppIconManager(context) }

    val currentIcon = remember { mutableStateOf(iconManager.getCurrentIcon()) }
    val showPicker = remember { mutableStateOf(false) }
    val showConfirmDialog = remember { mutableStateOf<AppIcon?>(null) }

    SettingsItem(
        onClick = { showPicker.value = true },
        title = stringResource(R.string.settings_appearance_app_icon_selector_title),
        subtitle = appIconName(currentIcon.value),
        leadingContent = { AppIconPreview(icon = currentIcon.value, size = Defaults.IconSize) }
    )

    if (showPicker.value) {
        // The icon being built, only applied once confirmed
        var pending by remember { mutableStateOf(currentIcon.value) }

        AppDialog(
            onDismissRequest = { showPicker.value = false },
            title = stringResource(R.string.settings_appearance_app_icon_selector_title),
            description = stringResource(R.string.settings_appearance_app_icon_selector_description),
            footer = {
                AppDialogButtonRow(
                    primaryText = stringResource(R.string.apply),
                    onPrimaryClick = { showConfirmDialog.value = pending },
                    primaryEnabled = pending != currentIcon.value,
                    secondaryText = stringResource(R.string.close),
                    onSecondaryClick = { showPicker.value = false }
                )
            }
        ) {
            IconBuilder(pending = pending, onPick = { pending = it })
        }
    }

    // Confirmation dialog
    showConfirmDialog.value?.let { selectedIcon ->
        AppIconChangeDialog(
            icon = selectedIcon,
            onConfirm = {
                scope.launch {
                    iconManager.setIcon(selectedIcon)
                    currentIcon.value = selectedIcon
                }
                showConfirmDialog.value = null
                showPicker.value = false
            },
            onDismiss = { showConfirmDialog.value = null }
        )
    }
}

/** A launcher icon drawn at [size] in the shape the launcher gives it. */
@Composable
private fun AppIconPreview(icon: AppIcon, size: Dp) {
    val context = LocalContext.current
    val iconPainter = rememberDrawablePainter(
        drawable = remember(icon) { AppCompatResources.getDrawable(context, icon.launcherIconResId) }
    )
    Image(
        painter = iconPainter,
        contentDescription = null,
        modifier = Modifier
            .size(size)
            .clip(AppIconShape)
    )
}

@Composable
private fun PickerLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface
    )
}

/**
 * Builds a launcher icon: a large preview on top, a carousel of backgrounds below it that a swipe
 * on either one turns, and the logos under that. A logo that does not read on the background is
 * off, and turning to such a background switches to the logo it comes with.
 */
@Composable
private fun IconBuilder(
    pending: AppIcon,
    onPick: (AppIcon) -> Unit
) {
    val backgrounds = IconBackground.entries
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = pending.background.ordinal) { backgrounds.size }
    val currentPending by rememberUpdatedState(pending)

    // The carousel is the source of the background, so a swipe on it or on the preview, or a tap
    // on a swatch, all land here
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            val background = backgrounds[page]
            if (background == currentPending.background) return@collect
            val icon = AppIcon.of(background, currentPending.mark)
                ?: AppIcon.of(background, background.baseMark)
                ?: return@collect
            onPick(icon)
        }
    }

    fun turnBy(step: Int) {
        val target = (pagerState.currentPage + step).coerceIn(0, backgrounds.lastIndex)
        scope.launch { pagerState.animateScrollToPage(target) }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        LayeredIconPreview(
            background = pending.background,
            mark = pending.mark,
            size = 132.dp,
            modifier = Modifier.pointerInput(Unit) {
                var drag = 0f
                detectHorizontalDragGestures(
                    onDragStart = { drag = 0f },
                    onDragEnd = {
                        if (abs(drag) > 48.dp.toPx()) turnBy(if (drag < 0f) 1 else -1)
                    }
                ) { change, amount ->
                    change.consume()
                    drag += amount
                }
            }
        )

        AnimatedContent(
            targetState = appIconName(pending),
            transitionSpec = Animations.fadeCrossfade(200),
            label = "icon_name"
        ) { name ->
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        PickerLabel(stringResource(R.string.settings_appearance_app_icon_background))
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val swatch = 56.dp
            HorizontalPager(
                state = pagerState,
                pageSize = PageSize.Fixed(swatch + 16.dp),
                // Keeps the current swatch in the middle, with its neighbors peeking in at the sides
                contentPadding = PaddingValues(horizontal = (maxWidth - swatch - 16.dp) / 2),
                modifier = Modifier.fillMaxWidth()
            ) { page ->
                val background = backgrounds[page]
                val distance = abs(pagerState.getOffsetDistanceInPages(page)).coerceAtMost(2f)
                // Shown with the logo picked so far, or with its own where that one does not read
                val mark = if (AppIcon.of(background, pending.mark) != null) {
                    pending.mark
                } else {
                    background.baseMark
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            val scale = 1f - 0.18f * distance
                            scaleX = scale
                            scaleY = scale
                            alpha = 1f - 0.25f * distance
                        }
                        .clickable(
                            interactionSource = null,
                            indication = null,
                            onClickLabel = stringResource(background.displayNameResId)
                        ) { scope.launch { pagerState.animateScrollToPage(page) } },
                    contentAlignment = Alignment.Center
                ) {
                    LayeredIconPreview(background = background, mark = mark, size = swatch)
                }
            }
        }

        PickerLabel(stringResource(R.string.settings_appearance_app_icon_mark))
        OptionGrid(items = IconMark.entries, columns = 3) { mark, itemModifier ->
            // A logo that does not read on the background has no icon, and stays off, still showing
            // what it would look like there
            val icon = AppIcon.of(pending.background, mark)
            AppIconCard(
                background = pending.background,
                mark = mark,
                label = stringResource(mark.displayNameResId),
                isSelected = pending.mark == mark,
                enabled = icon != null,
                onClick = { icon?.let(onPick) },
                modifier = itemModifier
            )
        }
    }
}

/**
 * An icon drawn from its two layers rather than as one picture, so a new background flows in
 * under the logo and a new logo pops in over the background. It is cut to the launcher's own icon
 * shape, so the preview looks the way the icon will on the home screen.
 */
@Composable
private fun LayeredIconPreview(
    background: IconBackground,
    mark: IconMark,
    size: Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(AppIconShape),
        contentAlignment = Alignment.Center
    ) {
        Crossfade(targetState = background, animationSpec = tween(350), label = "icon_background") { background ->
            IconLayer(drawableResId = background.drawableResId, size = size)
        }
        AnimatedContent(
            targetState = mark,
            transitionSpec = {
                (scaleIn(initialScale = 0.6f, animationSpec = spring(dampingRatio = 0.55f, stiffness = 400f)) +
                    fadeIn(tween(200))) togetherWith (scaleOut(targetScale = 1.2f) + fadeOut(tween(150)))
            },
            label = "icon_mark"
        ) { mark ->
            IconLayer(drawableResId = mark.drawableResId, size = size)
        }
    }
}

/**
 * One layer of an adaptive icon. A layer is 108 dp across with the icon in its middle 72, so it is
 * drawn half again as large as [size] and cropped to it, as a launcher does.
 */
@Composable
private fun IconLayer(@DrawableRes drawableResId: Int, size: Dp) {
    val context = LocalContext.current
    val painter = rememberDrawablePainter(
        drawable = remember(drawableResId) { AppCompatResources.getDrawable(context, drawableResId) }
    )
    Image(
        painter = painter,
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = Modifier.requiredSize(size * 1.5f)
    )
}

/**
 * Name of [icon]: its background's, with the logo added where it is not the one that background
 * comes with in its preset.
 */
@Composable
private fun appIconName(icon: AppIcon): String {
    val background = stringResource(icon.background.displayNameResId)
    if (icon.mark == icon.background.baseMark) return background
    return stringResource(
        R.string.settings_appearance_app_icon_combined,
        background,
        stringResource(icon.mark.displayNameResId)
    )
}

/**
 * Single app icon card.
 */
@Composable
private fun AppIconCard(
    background: IconBackground,
    mark: IconMark,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val windowSize = rememberWindowSize()
    val iconSize = when (windowSize.widthSizeClass) {
        WindowWidthSizeClass.Compact -> 48.dp
        WindowWidthSizeClass.Medium -> 52.dp
        WindowWidthSizeClass.Expanded -> 56.dp
    }

    // Increase height in landscape for better text display
    val cardHeight = if (isLandscape()) 108.dp else 96.dp

    SelectionTile(
        selected = isSelected,
        onClick = onClick,
        enabled = enabled,
        stateDescription = stringResource(
            if (isSelected) R.string.selected else R.string.not_selected
        ),
        modifier = modifier.heightIn(min = cardHeight)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Defaults.ItemSpacing),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(modifier = Modifier.alpha(if (enabled) 1f else 0.35f)) {
                LayeredIconPreview(background = background, mark = mark, size = iconSize)
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Icon name
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) {
                    LocalContentColor.current
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                textAlign = TextAlign.Center,
                maxLines = 2, // Allow 2 lines for longer names
                overflow = TextOverflow.Ellipsis,
                lineHeight = MaterialTheme.typography.bodySmall.fontSize * 1.2
            )
        }
    }
}

/**
 * Confirmation dialog for icon change.
 */
@Composable
private fun AppIconChangeDialog(
    icon: AppIcon,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_appearance_app_icon_change_dialog_title),
        description = htmlAnnotatedString(stringResource(
            R.string.settings_appearance_app_icon_change_dialog_message,
            appIconName(icon)
        )),
        footer = {
            AppDialogButtonRow(
                primaryText = stringResource(R.string.settings_appearance_app_icon_change_dialog_confirm),
                onPrimaryClick = onConfirm,
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        }
    )
}
