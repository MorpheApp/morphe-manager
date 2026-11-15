/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import android.content.pm.PackageInfo
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.model.HomeAppItem
import app.morphe.manager.ui.model.HomeAppStatus
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.util.*
import app.morphe.patcher.patch.FormFactor
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.*
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

// A verdict answered from cache lands within a frame, so the card waits rather than flashing it
private const val INSTALL_VERIFICATION_DELAY_MS = 400L

/** Size of the glyph leading the line under an app card's name. */
private val SubtitleIconSize = 16.dp

/**
 * What an app card's content is laid out in: its [row], the [style] the card settled on for the
 * colors it is drawn in, and the [placeholderColors] an icon with nothing to show takes.
 *
 * Holds the row rather than being one, so the content keeps reaching the card's style from inside
 * the columns and rows it nests, which a layout scope's marker would cut it off from.
 */
@Stable
internal class AppCardScope(
    val row: RowScope,
    val style: GlassCardStyle,
    val placeholderColors: List<Color>
)

/**
 * The line under an app card's name: a few words and the glyph they go by. An [emphasized] line
 * asks for something, and takes the name's color to stand out of the others.
 */
@Immutable
internal data class AppCardLine(
    val text: String,
    val icon: ImageVector? = null,
    val emphasized: Boolean = false
)

/**
 * Icon, name and [subtitle] of an app card, the icon resolved for [packageName] unless
 * [packageInfo] is at hand.
 *
 * @param packageName Package the icon is looked up by, null for the glass placeholder alone.
 * @param preferredSource Where the icon is looked for first.
 * @param tags See the [AppCardContent] taking an icon of its own.
 */
@Composable
internal fun AppCardScope.AppCardContent(
    packageName: String?,
    packageInfo: PackageInfo?,
    displayName: String,
    subtitle: AppCardLine?,
    preferredSource: AppDataSource = AppDataSource.PATCHED_APK,
    tags: @Composable RowScope.() -> Unit = {}
) {
    AppCardContent(displayName = displayName, subtitle = subtitle, tags = tags) {
        AppIcon(
            packageInfo = packageInfo,
            packageName = packageName,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            preferredSource = preferredSource,
            placeholderGradientColors = placeholderColors
        )
    }
}

/**
 * Icon, name and [subtitle] of an app card, with [icon] in the slot of the app's icon. A glyph
 * drawn there takes the card's text color, which is picked to read on its colors.
 *
 * @param tags Marks following the name that tell the app apart, such as a clone or a TV app. The
 *   name gives way to them, as they are a word or a glyph each.
 */
@Composable
internal fun AppCardScope.AppCardContent(
    displayName: String,
    subtitle: AppCardLine?,
    tags: @Composable RowScope.() -> Unit = {},
    icon: @Composable () -> Unit
) {
    Box(modifier = Modifier.size(style.iconSize)) {
        CompositionLocalProvider(LocalContentColor provides style.titleColor, content = icon)
    }

    Column(
        modifier = with(row) { Modifier.weight(1f) },
        verticalArrangement = Arrangement.spacedBy(style.textSpacing)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceSmall),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                modifier = Modifier.weight(1f, fill = false),
                text = displayName,
                style = style.titleStyle,
                color = style.titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            tags()
        }

        // The line fades through to the next state rather than snapping to it, as states settle
        // one after another while the app is read and checked
        AnimatedContent(
            targetState = subtitle,
            transitionSpec = Animations.fadeThrough(),
            contentAlignment = Alignment.CenterStart,
            label = "app_card_subtitle"
        ) { line ->
            line?.let { AppCardSubtitle(it) }
        }
    }
}

@Composable
private fun AppCardScope.AppCardSubtitle(line: AppCardLine) {
    val color = if (line.emphasized) style.titleColor else style.subtitleColor
    Row(
        horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceExtraSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        line.icon?.let { ThemedIcon(icon = it, tint = color, size = SubtitleIconSize) }
        Text(
            text = line.text,
            style = style.subtitleStyle,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** A word or a glyph following an app card's name, in the card's chip colors. */
@Composable
internal fun AppCardScope.AppCardTag(
    text: String?,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    StatusBadge(
        text = text,
        modifier = modifier,
        icon = icon,
        containerColor = style.chipContainerColor,
        contentColor = style.chipContentColor
    )
}

/**
 * Marks an app built for a device other than a phone by its glyph, named for a screen reader. Only
 * marked, never hidden, as a TV app is often patched on a phone and carried over.
 */
@Composable
internal fun AppCardScope.FormFactorTag(formFactor: FormFactor?) {
    val mark = formFactor.mark() ?: return
    val label = stringResource(mark.label)
    AppCardTag(
        text = null,
        icon = mark.icon,
        modifier = Modifier.semantics { contentDescription = label }
    )
}

/** The line a home card says [this] state in, null for a build of no known version. */
@Composable
private fun HomeAppStatus.line(): AppCardLine? = when (this) {
    HomeAppStatus.Pending -> AppCardLine(
        stringResource(R.string.home_install_verification_pending),
        AppStateIcons.Pending
    )
    HomeAppStatus.UnpatchedInstall -> AppCardLine(
        stringResource(R.string.home_unpatched_version_installed),
        AppStateIcons.NotPatched,
        emphasized = true
    )
    HomeAppStatus.Uninstalled -> AppCardLine(stringResource(R.string.uninstalled), AppStateIcons.Uninstalled)
    HomeAppStatus.Unverified -> AppCardLine(stringResource(R.string.home_unverified), AppStateIcons.Unverified)
    is HomeAppStatus.NewerVersion -> AppCardLine(
        stringResource(R.string.home_app_status_newer_version, version.withVersionPrefix()),
        AppStateIcons.NewerVersion,
        emphasized = true
    )
    HomeAppStatus.NewerPatches -> AppCardLine(
        stringResource(R.string.home_app_status_newer_patches),
        AppStateIcons.NewerPatches,
        emphasized = true
    )
    is HomeAppStatus.Patched -> version.takeIf { it.isNotEmpty() }?.let { AppCardLine(it.withVersionPrefix()) }
    HomeAppStatus.NotPatchedYet -> AppCardLine(stringResource(R.string.home_not_patched_yet))
}

/**
 * [HomeAppItem.status] as the card shows it. A pending check reads as the settled build until it
 * has taken a moment, as most are answered from cache before it would be worth saying.
 */
@Composable
private fun HomeAppItem.shownStatus(): HomeAppStatus {
    val status = this.status
    val checking = status == HomeAppStatus.Pending
    val showsChecking by produceState(false, checking) {
        value = false
        if (checking) {
            delay(INSTALL_VERIFICATION_DELAY_MS.milliseconds)
            value = true
        }
    }
    return if (checking && !showsChecking) HomeAppStatus.Patched(version) else status
}

/**
 * One home screen card for [item]: its icon, its name with the marks telling it apart, and the one
 * state [HomeAppItem.status] picks for it.
 *
 * @param onUpdate Rebuilds the app, offered at the end of the card while its state asks for that.
 * @param selected Whether the card is picked, null outside a selection.
 * @param dragHandleModifier Set while reordering, which puts the drag handle at the end of the card.
 */
@Composable
internal fun HomeAppCard(
    item: HomeAppItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onUpdate: (() -> Unit)? = null,
    selected: Boolean? = null,
    onLongClick: (() -> Unit)? = null,
    dragHandleModifier: Modifier? = null
) {
    val installedApp = item.installedApp
    val status = item.shownStatus()

    val trailing: (@Composable AppCardScope.() -> Unit)? = when {
        dragHandleModifier != null -> {
            { DragHandle(modifier = dragHandleModifier, tint = style.subtitleColor) }
        }
        onUpdate != null && status.offersRebuild -> {
            { AppCardUpdateButton(appName = item.displayName, onClick = onUpdate) }
        }
        else -> null
    }

    AppCardLayout(
        gradientColors = item.gradientColors,
        onClick = onClick,
        onLongClick = onLongClick,
        selected = selected,
        trailing = trailing,
        modifier = modifier.semantics { role = Role.Button }
    ) {
        AppCardContent(
            // Named after the package the record was installed under: a clone carries an icon of
            // its own, which is regularly the whole point of keeping several copies of an app apart
            packageName = installedApp?.currentPackageName ?: item.packageName,
            packageInfo = item.packageInfo,
            displayName = item.displayName,
            subtitle = status.line(),
            preferredSource = if (installedApp != null) AppDataSource.INSTALLED else AppDataSource.PATCHED_APK
        ) {
            item.nameSuffix?.let {
                Text(
                    text = it,
                    style = style.subtitleStyle,
                    color = style.subtitleColor,
                    maxLines = 1
                )
            }
            if (item.isClone) AppCardTag(stringResource(R.string.clone))
            FormFactorTag(item.formFactor)
        }
    }
}

/**
 * Rebuilds the app from its card, as the patch button of its dialog does. A glyph without a label,
 * as the line beside it already says what the rebuild brings, and a label in some languages would
 * take the room that line needs. The label is in the tooltip and read out instead.
 */
@Composable
internal fun AppCardScope.AppCardUpdateButton(appName: String, onClick: () -> Unit) {
    val label = stringResource(R.string.update)
    val interactionSource = remember { MutableInteractionSource() }
    IconTooltip(text = label) {
        FilledTonalIconButton(
            onClick = onClick,
            modifier = Modifier
                .size(Defaults.PillHeightLarge)
                .pressScale(interactionSource = interactionSource, label = "update_button_press_scale"),
            interactionSource = interactionSource,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = style.actionContainerColor,
                contentColor = style.actionContentColor
            )
        ) {
            Icon(
                imageVector = MaterialSymbols.Rounded.Auto_fix_high,
                contentDescription = "$label $appName",
                modifier = Modifier.size(Defaults.IconSizeSmall)
            )
        }
    }
}

/**
 * Shared layout of app cards and buttons.
 *
 * Uses a frosted glass effect built from two passes:
 * - a diagonal sweep carrying the card colors from the bottom-start tint to the top-end accent
 * - a gradient border
 *
 * @param selected Whether the card is picked, null outside a selection. Marked at the end of the row.
 * @param onLongClickLabel What a long press does, announced rather than left as an unlabeled
 *   action the screen reader cannot describe. On an app card it starts a selection.
 * @param trailing What the end of the row holds outside a selection, whose mark takes its place.
 *   It pops in and out as it comes and goes, which a state settling while the list loads does.
 */
@Composable
internal fun AppCardLayout(
    modifier: Modifier = Modifier,
    gradientColors: List<Color>,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String = stringResource(R.string.accessibility_select_app),
    selected: Boolean? = null,
    trailing: (@Composable AppCardScope.() -> Unit)? = null,
    content: @Composable AppCardScope.() -> Unit
) {
    // The line under the name carries the app's state, so it reads stronger than on other glass cards
    val baseStyle = glassCardStyle(subtitleAlpha = 0.85f)
    val cardStyle = baseStyle.onCard(gradientColors)
    val shape = RoundedCornerShape(cardStyle.cardRadius)
    val view = LocalView.current
    val longClickLabel = onLongClickLabel.takeIf { onLongClick != null }

    val colors = cardStyle.cardColors(gradientColors)
    val baseColor = colors.firstOrNull() ?: Color.White
    val midColor = colors.getOrElse(1) { baseColor }
    val endColor = colors.lastOrNull() ?: baseColor

    // Press scale animation
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(cardStyle.cardHeight)
            .pressScale(
                interactionSource = interactionSource,
                label = "card_press_scale"
            )
            .clip(shape)
            // Brushes are rebuilt only when the size or the palette changes, so scrolling a list
            // of cards does not reallocate them on every frame
            .drawWithCache {
                val w  = size.width
                val h  = size.height
                val cr = CornerRadius(cardStyle.cardRadius.toPx())
                val rtl = layoutDirection.isRtl

                // One sweep from the bottom-start tint through to the top-end accent. Every
                // translucent layer costs the GPU a full blend pass over the card, and a list of
                // them scrolling is what pushed the frame past its budget
                val fill = if (cardStyle.monochrome) {
                    SolidColor(cardStyle.cardColor)
                } else {
                    Brush.linearGradient(
                        colors = listOf(
                            baseColor.copy(alpha = 0.70f),
                            midColor.copy(alpha = 0.58f),
                            endColor.copy(alpha = 0.64f)
                        ),
                        start = Offset(startEdgeX(w, rtl), h),
                        end   = Offset(endEdgeX(w, rtl), 0f)
                    )
                }

                // Border: bright top-start → faded bottom-end. The highlight borrows its middle from
                // the card colors, which on a single dark color fades into the background and leaves
                // only the corners lit, so a card in one color gets an even edge instead
                val border = when {
                    cardStyle.monochrome || !cardStyle.outlines -> null
                    colors.all { it == baseColor } -> SolidColor(cardStyle.flatEdgeColor)
                    else -> Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.65f),
                            midColor.copy(alpha = 0.30f),
                            endColor.copy(alpha = 0.15f),
                            Color.White.copy(alpha = 0.20f)
                        ),
                        start = Offset(startEdgeX(w, rtl), 0f),
                        end   = Offset(endEdgeX(w, rtl), h)
                    )
                }
                val borderStroke = Stroke(width = CardBorder.Width.toPx())

                onDrawWithContent {
                    drawRoundRect(brush = fill, cornerRadius = cr)

                    drawContent()

                    if (border != null) {
                        drawRoundRect(
                            brush = border,
                            cornerRadius = cr,
                            style = borderStroke
                        )
                    }
                }
            }
            .combinedClickable(
                interactionSource = interactionSource,
                // A press scales the card instead
                indication = shapedIndication(shape, press = false),
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onClick()
                },
                onLongClickLabel = longClickLabel,
                onLongClick = if (onLongClick != null) {
                    {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        onLongClick()
                    }
                } else null
            )
            .semantics { if (selected != null) this.selected = selected }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = cardStyle.contentPadding),
            horizontalArrangement = Arrangement.spacedBy(cardStyle.contentSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val scope = remember(cardStyle, colors) { AppCardScope(this, cardStyle, colors) }
            scope.content()
            val shownTrailing = rememberLatest(trailing)
            AnimatedVisibility(
                visible = trailing != null && selected == null,
                enter = Animations.headerActionEnter,
                exit = Animations.headerActionExit
            ) {
                shownTrailing?.invoke(scope)
            }
            // In the card's own text color, which is picked to read on its gradient
            SelectionMark(
                selected = selected,
                ink = cardStyle.titleColor.takeIf { !cardStyle.monochrome }
            )
        }
    }
}

/**
 * Shimmer loading animation for app cards.
 */
@Composable
fun AppLoadingCard(
    gradientColors: List<Color>,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")

    // Pulse animation for gradient background
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    // Shimmer animation
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_offset"
    )

    val cardStyle = glassCardStyle().onCard(gradientColors)
    val shape = RoundedCornerShape(cardStyle.cardRadius)
    val rtl = isRtl()

    // Skeleton rows carry the height of the text they stand in for, so the card does not
    // re-lay-out its content the moment the real app resolves
    val density = LocalDensity.current
    val titleRowHeight = with(density) { cardStyle.titleStyle.lineHeight.toDp() }
    val subtitleRowHeight = with(density) { cardStyle.subtitleStyle.lineHeight.toDp() }

    // Follows the content the card settled on, so the skeleton stays visible on a light gradient
    val skeletonColor = cardStyle.titleColor

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(cardStyle.cardHeight)
    ) {
        // Base gradient background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .then(
                    if (cardStyle.monochrome) {
                        Modifier.background(cardStyle.cardColor)
                    } else {
                        Modifier.background(
                            brush = startToEndGradient(
                                colors = cardStyle.cardColors(gradientColors)
                                    .map { it.copy(alpha = pulseAlpha) },
                                rtl = rtl
                            )
                        )
                    }
                )
        )

        // Shimmer overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .drawBehind {
                    drawDiagonalShimmer(
                        progress = (shimmerOffset + 1f) / 3f,
                        color = skeletonColor.copy(alpha = 0.3f)
                    )
                }
        )

        // Content skeleton
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = cardStyle.contentPadding),
            horizontalArrangement = Arrangement.spacedBy(cardStyle.contentSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon skeleton, sized and shaped like the glass placeholder
            ShimmerBox(
                modifier = Modifier
                    .size(cardStyle.iconSize)
                    .wrapContentSize()
                    .fillMaxSize(AppIconContentFraction),
                shape = AppIconShape,
                baseColor = skeletonColor.copy(alpha = 0.2f)
            )

            // Text skeleton
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(cardStyle.textSpacing)
            ) {
                ShimmerLine(
                    height = titleRowHeight,
                    widthFraction = 0.6f,
                    baseColor = skeletonColor.copy(alpha = 0.25f)
                )
                ShimmerLine(
                    height = subtitleRowHeight,
                    widthFraction = 0.4f,
                    baseColor = skeletonColor.copy(alpha = 0.15f)
                )
            }
        }
    }
}
