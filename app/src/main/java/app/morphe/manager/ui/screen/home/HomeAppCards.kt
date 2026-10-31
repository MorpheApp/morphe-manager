/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import android.content.pm.PackageInfo
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import app.morphe.manager.R
import app.morphe.manager.data.room.apps.installed.InstalledApp
import app.morphe.manager.ui.model.HomeAppItem
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.util.*
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

// A verdict answered from cache lands within a frame, so the badge waits rather than flashing
private const val INSTALL_VERIFICATION_BADGE_DELAY_MS = 400L

/**
 * Shared icon + text content for [AppCardLayout] rows.
 *
 * @param packageName    Package name used for icon lookup when [packageInfo] is null;
 *   null renders the glass placeholder without resolving an icon.
 * @param packageInfo    Resolved [PackageInfo]; when non-null [packageName] is ignored for the icon.
 * @param displayName    Primary label shown in bold.
 * @param subtitle       Secondary line shown below [displayName]; null → not rendered.
 * @param gradientColors Gradient palette forwarded to [AppIcon] placeholder, unless the user
 *   picked fixed card colors in the appearance settings.
 * @param subtitleBadges Badges following [subtitle] on its line, for marks meant to stand out of it.
 */
@Composable
internal fun RowScope.AppCardContent(
    packageName: String?,
    packageInfo: PackageInfo?,
    displayName: String,
    subtitle: String?,
    gradientColors: List<Color>,
    subtitleBadges: @Composable RowScope.() -> Unit = {}
) {
    AppCardContent(
        displayName = displayName,
        subtitle = subtitle,
        gradientColors = gradientColors,
        subtitleBadges = subtitleBadges
    ) { placeholderColors ->
        AppIcon(
            packageInfo = packageInfo,
            packageName = if (packageInfo == null) packageName else null,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            preferredSource = AppDataSource.PATCHED_APK,
            placeholderGradientColors = placeholderColors
        )
    }
}

/**
 * [AppCardContent] with [icon] in the slot of the app's icon, handed the card colors a placeholder
 * wears. A glyph drawn there takes the card's text color, which is picked to read on its gradient.
 */
@Composable
internal fun RowScope.AppCardContent(
    displayName: String,
    subtitle: String?,
    gradientColors: List<Color>,
    subtitleBadges: @Composable RowScope.() -> Unit = {},
    icon: @Composable (placeholderColors: List<Color>) -> Unit
) {
    val cardStyle = glassCardStyle().onCard(gradientColors)

    Box(modifier = Modifier.size(cardStyle.iconSize)) {
        CompositionLocalProvider(LocalContentColor provides cardStyle.titleColor) {
            icon(cardStyle.cardColors(gradientColors))
        }
    }

    Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(cardStyle.textSpacing)
    ) {
        Text(
            text = displayName,
            style = cardStyle.titleStyle,
            color = cardStyle.titleColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (subtitle != null) {
            Row(
                // Matches the badge-height subtitle row of the installed cards, so names line up
                // across every card in the list
                modifier = Modifier.height(statusBadgeHeight),
                horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceSmall),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    // Gives way to the badges, which keep their words whole
                    modifier = Modifier.weight(1f, fill = false),
                    text = subtitle,
                    style = cardStyle.subtitleStyle,
                    color = cardStyle.subtitleColor,
                    // The row is one badge tall, so a subtitle that wraps would be cut in half
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                subtitleBadges()
            }
        }
    }
}

/**
 * One home screen card for [item]: the build Morphe keeps a record of, or the not-patched-yet
 * button when there is no record to show.
 *
 * @param showStatusBadges Whether the badges pinned to the end of the card may show.
 * @param selected Whether the card is picked, null outside a selection.
 */
@Composable
internal fun HomeAppCard(
    item: HomeAppItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showStatusBadges: Boolean = true,
    selected: Boolean? = null,
    onLongClick: (() -> Unit)? = null
) {
    val installedApp = item.installedApp
    if (installedApp != null) {
        InstalledAppCard(
            item = item,
            installedApp = installedApp,
            showStatusBadges = showStatusBadges,
            selected = selected,
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier
        )
    } else {
        NotPatchedAppCard(
            item = item,
            selected = selected,
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier
        )
    }
}

/**
 * Installed app card with gradient background.
 *
 * [installedApp] is [HomeAppItem.installedApp] handed over separately, so the card is written
 * against a record that is there rather than around one that might not be.
 */
@Composable
private fun InstalledAppCard(
    item: HomeAppItem,
    installedApp: InstalledApp,
    showStatusBadges: Boolean,
    selected: Boolean?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val cardStyle = glassCardStyle(subtitleAlpha = 0.85f).onCard(item.gradientColors)

    val versionLabel = stringResource(R.string.version)
    val cloneLabel = stringResource(R.string.clone)
    val installedLabel = stringResource(R.string.installed)
    val updateAvailableLabel = stringResource(R.string.update_available)
    val supportedVersionLabel = stringResource(R.string.home_app_info_newest_supported_version)
    val deletedLabel = stringResource(R.string.uninstalled)
    val replacementLabel = stringResource(R.string.home_unpatched_version_installed)
    val replacementBadgeLabel = stringResource(R.string.home_unpatched_badge)
    val unverifiedLabel = stringResource(R.string.home_unverified)
    val pendingLabel = stringResource(R.string.home_install_verification_pending)

    val showsPendingBadge = remember { mutableStateOf(false) }
    LaunchedEffect(item.isInstallStatePending) {
        if (!item.isInstallStatePending) {
            showsPendingBadge.value = false
            return@LaunchedEffect
        }
        delay(INSTALL_VERIFICATION_BADGE_DELAY_MS.milliseconds)
        showsPendingBadge.value = true
    }

    val version = remember(item) { item.version.withVersionPrefix() }
    // What tells the app from a namesake leads the row, ahead of the version, which runs on to the
    // one the sources support where there is a newer one. A long pair is cut at its end, leaving
    // the app's dialog to print both in full
    val subtitle = remember(item) {
        val supportedVersion = item.versionStatus?.takeIf { item.showsVersionBadge }?.supportedVersion
        val versions = if (supportedVersion != null) {
            "$version → ${supportedVersion.withVersionPrefix()}"
        } else {
            version
        }
        listOfNotNull(item.nameSuffix, versions.ifEmpty { null }).joinToString(" • ")
    }

    // Newer patches and a newer supported app version are both answered by rebuilding the app, so
    // one mark stands for either. Only the newer version asks for a newer APK, so it leads
    val updateMark = when {
        item.showsVersionBadge -> AppStateIcons.NewerVersion
        item.showsUpdateBadge -> AppStateIcons.NewerPatches
        else -> null
    }
    // Each corner keeps one meaning down the list, so copies and updates are found at a glance
    val cloneMark = AppStateIcons.Clone.takeIf { item.isClone }

    // The states the badges stand for are read out whether the badges themselves are showing or
    // not, so reordering does not quietly drop them from the card's description
    val contentDesc = remember(
        item,
        version,
        versionLabel,
        cloneLabel,
        installedLabel,
        updateAvailableLabel,
        supportedVersionLabel,
        deletedLabel,
        replacementLabel,
        unverifiedLabel,
        showsPendingBadge.value,
        pendingLabel
    ) {
        buildString {
            append(item.displayName)
            item.nameSuffix?.let { append(", $it") }
            if (item.isClone) append(", $cloneLabel")
            if (version.isNotEmpty()) {
                append(", $versionLabel $version")
            }
            append(", ")
            append(
                when {
                    showsPendingBadge.value -> pendingLabel
                    item.isInstallStateNotPatched -> replacementLabel
                    item.isDeleted -> deletedLabel
                    item.isInstallStateUnknown -> unverifiedLabel
                    else -> installedLabel
                }
            )
            if (item.showsUpdateBadge) append(", $updateAvailableLabel")
            // Read out in full even when the badge had no room to print it
            item.versionStatus?.takeIf { item.showsVersionBadge }?.let {
                append(", $supportedVersionLabel ${it.supportedVersion}")
            }
        }
    }

    AppCardLayout(
        gradientColors = item.gradientColors,
        onClick = onClick,
        onLongClick = onLongClick,
        selected = selected,
        modifier = modifier.semantics {
            role = Role.Button
            this.contentDescription = contentDesc
        }
    ) {
        // App icon
        // Frosted and as tall as the badges in the row, but capped so a large font can't bury the icon
        MarkedIcon(
            mark = updateMark,
            startMark = cloneMark,
            markColor = cardStyle.markContainerColor,
            markSize = cardStyle.markSize,
            markContentColor = cardStyle.chipContentColor,
            iconSize = cardStyle.iconSize,
            contentFraction = AppIconContentFraction
        ) {
            AppIcon(
                packageInfo = item.packageInfo,
                // Named after the package this record was installed under: a clone carries an icon
                // of its own, which is regularly the whole point of keeping several copies of an
                // app apart
                packageName = installedApp.currentPackageName,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                preferredSource = AppDataSource.INSTALLED,
                // A record can outlive every artifact carrying its icon, and the glass placeholder
                // is what the rest of the list shows in that case
                placeholderGradientColors = cardStyle.cardColors(item.gradientColors)
            )
        }

        // App info
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(cardStyle.textSpacing)
        ) {
            // App name
            Text(
                text = item.displayName,
                style = cardStyle.titleStyle,
                color = cardStyle.titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Version, with the install's state badges pinned to the card edge
            Row(
                // Badge height is reserved whether a badge is showing, otherwise the row
                // grows around it and nudges the app name above out of place
                modifier = Modifier.height(statusBadgeHeight),
                horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceSmall),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Fills the row so the chip is pinned to the card edge rather than trailing
                // a version string of whatever length
                Text(
                    modifier = Modifier.weight(1f),
                    text = subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = cardStyle.subtitleStyle,
                    color = cardStyle.subtitleColor
                )

                // The drag handle rides over this edge of the card while reordering, so the
                // badges pinned here stand down rather than crowd it
                if (showStatusBadges) {
                    // Frosted-glass colors: a white semi-transparent fill reads on any accent
                    // color the card's bundle brings, and on the user's dynamic theme
                    if (item.isDeleted && !item.isInstallStateNotPatched) {
                        StatusBadge(
                            text = deletedLabel,
                            icon = AppStateIcons.Uninstalled,
                            containerColor = cardStyle.chipContainerColor,
                            contentColor = cardStyle.chipContentColor
                        )
                    }

                    if (item.isInstallStateNotPatched) {
                        StatusBadge(
                            text = replacementBadgeLabel,
                            icon = AppStateIcons.NotPatched,
                            containerColor = cardStyle.chipContainerColor,
                            contentColor = cardStyle.chipContentColor
                        )
                    }

                    if (item.isInstallStateUnknown) {
                        StatusBadge(
                            text = unverifiedLabel,
                            icon = AppStateIcons.Unverified,
                            containerColor = cardStyle.chipContainerColor,
                            contentColor = cardStyle.chipContentColor
                        )
                    }

                    if (showsPendingBadge.value) {
                        StatusBadge(
                            text = pendingLabel,
                            icon = AppStateIcons.Pending,
                            containerColor = cardStyle.chipContainerColor,
                            contentColor = cardStyle.chipContentColor
                        )
                    }
                }
            }
        }
    }
}

/**
 * Card for an app Morphe has no build of yet.
 */
@Composable
private fun NotPatchedAppCard(
    item: HomeAppItem,
    selected: Boolean?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val notPatchedText = stringResource(R.string.home_not_patched_yet)

    // Only for an app the device actually has: other cards are described by an APK Morphe kept
    // rather than by an install, and that version answers a different question
    val subtitle = remember(item, notPatchedText) {
        val version = item.version.takeIf { item.isInstalledOnDevice && it.isNotEmpty() }
        listOfNotNull(item.nameSuffix, version?.withVersionPrefix(), notPatchedText).joinToString(" • ")
    }

    val contentDesc = remember(item.displayName, subtitle) {
        "${item.displayName}, $subtitle"
    }

    AppCardLayout(
        gradientColors = item.gradientColors,
        onClick = onClick,
        onLongClick = onLongClick,
        selected = selected,
        modifier = modifier.semantics {
            role = Role.Button
            this.contentDescription = contentDesc
        }
    ) {
        AppCardContent(
            packageName = item.packageName,
            packageInfo = item.packageInfo,
            displayName = item.displayName,
            subtitle = subtitle,
            gradientColors = item.gradientColors,
        )
    }
}

/**
 * Shared content layout for app cards and buttons.
 *
 * Uses a frosted glass effect built from two passes:
 * - a diagonal sweep carrying the card colors from the bottom-start tint to the top-end accent
 * - a gradient border
 *
 * @param selected Whether the card is picked, null outside a selection. Marked at the end of the row.
 * @param onLongClickLabel What a long press does, announced rather than left as an unlabeled
 *   action the screen reader cannot describe. On an app card it starts a selection.
 */
@Composable
internal fun AppCardLayout(
    modifier: Modifier = Modifier,
    gradientColors: List<Color>,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String = stringResource(R.string.accessibility_select_app),
    selected: Boolean? = null,
    content: @Composable RowScope.() -> Unit
) {
    val cardStyle = glassCardStyle().onCard(gradientColors)
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

                if (cardStyle.monochrome) {
                    return@drawWithCache onDrawWithContent {
                        drawRoundRect(
                            color = cardStyle.cardColor,
                            cornerRadius = cr
                        )

                        drawContent()
                    }
                }

                // One sweep from the bottom-start tint through to the top-end accent. Every
                // translucent layer costs the GPU a full blend pass over the card, and a list of
                // them scrolling is what pushed the frame past its budget
                val glass = Brush.linearGradient(
                    colors = listOf(
                        baseColor.copy(alpha = 0.70f),
                        midColor.copy(alpha = 0.58f),
                        endColor.copy(alpha = 0.64f)
                    ),
                    start = Offset(startEdgeX(w, rtl), h),
                    end   = Offset(endEdgeX(w, rtl), 0f)
                )

                // Border: bright top-start → faded bottom-end. The highlight borrows its middle from
                // the card colors, which on a single dark color fades into the background and leaves
                // only the corners lit, so a flat card gets an even edge instead
                val border = if (colors.all { it == baseColor }) {
                    SolidColor(cardStyle.flatEdgeColor)
                } else {
                    Brush.linearGradient(
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
                    drawRoundRect(brush = glass, cornerRadius = cr)

                    drawContent()

                    if (cardStyle.outlines) {
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
            content()
            // In the card's own text color, which is picked to read on its gradient
            SelectionMark(selected, ink = cardStyle.titleColor.takeIf { !cardStyle.monochrome })
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
    val titleRowHeight = with(LocalDensity.current) { cardStyle.titleStyle.lineHeight.toDp() }

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
                    height = statusBadgeHeight,
                    widthFraction = 0.4f,
                    baseColor = skeletonColor.copy(alpha = 0.15f)
                )
            }
        }
    }
}
