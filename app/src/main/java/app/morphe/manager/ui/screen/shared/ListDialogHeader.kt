/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.theme.MonochromeThemeDefaults

/** How a dialog heads itself, shared by the list dialogs and the app details so they read alike. */
object DialogHeaderDefaults {
    val IconSize = 44.dp

    /** Rounding that keeps an app icon's own shape at [IconSize]. */
    val IconCornerRadius = 13.dp
    val IconSpacing = Defaults.ItemSpacing
    val TextSpacing = 2.dp
    val VerticalPadding = Defaults.ContentPadding

    val titleStyle: TextStyle
        @Composable get() = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
    val subtitleStyle: TextStyle
        @Composable get() = MaterialTheme.typography.bodySmall
}

/**
 * Head of a list dialog that names what the list belongs to: its [icon], [title] and a
 * [subtitle] summing the list up, with the toggle for the list's search field where it has one.
 *
 * @param subtitleLoading Shows a shimmer in place of the [subtitle] while the list it sums up
 *   loads, easing into the text once it is there.
 * @param search The list's search field, or null for a list short enough to go without one.
 * @param searchLabel What the field searches, as the toggle's description.
 * @param searchEnabled Whether there is anything to search yet. The toggle stays in place while
 *   there is not, so the title does not reflow when a loading list fills in.
 * The header sits on a band like the app details' one, which gives the list scrolling under it an
 * edge to stop at.
 *
 * @param accentColor Color of the app the list belongs to, which tints the band, or null for a
 *   neutral one.
 * @param badges What the whole list shares, in a row under the title as the app details keep theirs.
 * @param actions Title actions drawn ahead of the search toggle.
 */
@Composable
fun ListDialogHeader(
    icon: @Composable (Modifier) -> Unit,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    subtitleLoading: Boolean = false,
    search: SearchFieldState? = null,
    searchLabel: String? = null,
    searchEnabled: Boolean = true,
    accentColor: Color? = null,
    badges: (@Composable FlowRowScope.() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    // An accent read from a picture can land after the header is up, so the band eases into it
    val band by animateColorAsState(
        targetValue = appAccentFill(accentColor),
        animationSpec = tween(Defaults.ANIMATION_DURATION),
        label = "list_header_band"
    )
    val bleed = LocalDialogHorizontalInset.current
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .headerBand(band, bleed, statusBarHeight)
            .padding(vertical = DialogHeaderDefaults.VerticalPadding)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DialogHeaderDefaults.IconSpacing)
        ) {
            icon(Modifier.size(DialogHeaderDefaults.IconSize))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(DialogHeaderDefaults.TextSpacing)
            ) {
                // Two lines, so a list named in a phrase rather than a name keeps it whole beside
                // the header's actions
                Text(
                    text = title,
                    style = DialogHeaderDefaults.titleStyle,
                    color = LocalDialogTextColor.current,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val subtitleStyle = DialogHeaderDefaults.subtitleStyle
                Crossfade(
                    targetState = subtitleLoading,
                    animationSpec = tween(Defaults.ANIMATION_DURATION),
                    label = "list_header_subtitle"
                ) { loading ->
                    if (loading) {
                        // A line's height, so the header keeps its size as the text replaces it
                        ShimmerText(
                            widthFraction = 0.5f,
                            height = with(LocalDensity.current) { subtitleStyle.lineHeight.toDp() }
                        )
                    } else {
                        Text(
                            text = subtitle,
                            style = subtitleStyle,
                            color = LocalDialogSecondaryTextColor.current
                        )
                    }
                }
            }
            actions()
            if (search != null) {
                TitleAction(
                    icon = if (search.visible) Icons.Outlined.SearchOff else Icons.Outlined.Search,
                    contentDescription = searchLabel ?: stringResource(R.string.search),
                    onClick = { search.toggle() },
                    style = TitleActionStyle.Toggle,
                    active = search.visible,
                    enabled = searchEnabled
                )
            }
        }
        if (badges != null) {
            // A row that folds its chips out resizes on the list's own spring, as the list's rows do,
            // without the settle, which would dip below the chips and clip them
            StatusBadgeRow(
                modifier = Modifier
                    .padding(top = Defaults.ContentPaddingSmall)
                    .animateContentSize(Animations.listSpring(dampingRatio = Spring.DampingRatioNoBouncy)),
                content = badges
            )
        }
    }
}

/**
 * [ListDialogHeader] icon for a list that stands for no app or source of its own: [icon] on a disc
 * of [color], the color the header takes on too.
 */
@Composable
fun ListDialogHeaderIcon(icon: ImageVector, color: Color, modifier: Modifier = Modifier) {
    val accent = MonochromeThemeDefaults.accentColor(color)
    StatusCircleIcon(
        icon = icon,
        containerColor = accent.copy(alpha = 0.2f),
        contentColor = accent,
        modifier = modifier,
        size = DialogHeaderDefaults.IconSize
    )
}

/**
 * Tints the header out to the dialog's edges and up under the status bar, rounded off below like
 * the app details' header. Only the drawing reaches past the header, so its content stays in line
 * with the list under it.
 */
private fun Modifier.headerBand(color: Color, bleed: Dp, statusBarHeight: Dp): Modifier = drawBehind {
    val corner = CornerRadius(Defaults.CardCornerRadius.toPx())
    val band = RoundRect(
        left = -bleed.toPx(),
        top = -statusBarHeight.toPx(),
        right = size.width + bleed.toPx(),
        bottom = size.height,
        bottomLeftCornerRadius = corner,
        bottomRightCornerRadius = corner
    )
    drawPath(Path().apply { addRoundRect(band) }, color)
}
