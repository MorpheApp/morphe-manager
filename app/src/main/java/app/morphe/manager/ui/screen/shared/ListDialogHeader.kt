/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import app.morphe.manager.ui.theme.ThemeTraitsDefaults
import app.morphe.manager.ui.theme.headerTitle

/** How a dialog heads itself, shared by the list dialogs and the app details so they read alike. */
object DialogHeaderDefaults {
    val IconSize = 48.dp

    /** Rounding that keeps an app icon's own shape at [IconSize]. */
    val IconCornerRadius = 14.dp
    val IconSpacing = Defaults.SpaceMedium
    val TextSpacing = 2.dp
    val VerticalPadding = Defaults.SpaceMedium

    val titleStyle: TextStyle
        @Composable get() = MaterialTheme.typography.headerTitle
    val subtitleStyle: TextStyle
        @Composable get() = MaterialTheme.typography.bodyMedium
}

/**
 * Entrance a dialog header plays as it first shows: the icon springs in, the title and subtitle
 * slide in after it and the badges rise last. The header modifiers below read these clocks while
 * drawing, so the entrance never recomposes the header.
 */
@Stable
class DialogHeaderEntrance internal constructor(
    internal val icon: State<Float>,
    internal val text: State<Float>,
    internal val badges: State<Float>
)

@Composable
fun rememberDialogHeaderEntrance(): DialogHeaderEntrance {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val target = if (entered) 1f else 0f

    val icon = animateFloatAsState(
        targetValue = target,
        // Overshoots, as the first thing the eye lands on
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 320f),
        label = "header_icon_entrance"
    )
    val text = animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 260, delayMillis = 60, easing = EaseOutCubic),
        label = "header_text_entrance"
    )
    val badges = animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 240, delayMillis = 160, easing = EaseOutBack),
        label = "header_badges_entrance"
    )
    return remember(icon, text, badges) { DialogHeaderEntrance(icon, text, badges) }
}

fun Modifier.headerIconEntrance(entrance: DialogHeaderEntrance): Modifier = graphicsLayer {
    val progress = entrance.icon.value
    val scale = lerp(0.6f, 1f, progress)
    scaleX = scale
    scaleY = scale
    alpha = progress.coerceIn(0f, 1f)
}

fun Modifier.headerTitleEntrance(entrance: DialogHeaderEntrance): Modifier = graphicsLayer {
    slideIn(entrance.text.value)
}

/** Trails the title a little on the same clock. */
fun Modifier.headerSubtitleEntrance(entrance: DialogHeaderEntrance): Modifier = graphicsLayer {
    slideIn(entrance.text.value.startingAt(0.15f))
}

/**
 * For the badges and whatever else sits beside the title. Each of a row of badges starts a third
 * of the way into the one before it, by its [index], so they arrive in sequence.
 */
fun Modifier.headerBadgeEntrance(entrance: DialogHeaderEntrance, index: Int = 0): Modifier = graphicsLayer {
    val progress = entrance.badges.value.startingAt(index * 0.3f)
    translationY = lerp(20f, 0f, progress)
    alpha = progress
}

private fun GraphicsLayerScope.slideIn(progress: Float) {
    translationX = lerp(40f, 0f, progress)
    alpha = progress.coerceIn(0f, 1f)
}

/** This progress rescaled to run from [start] on, so a later element enters off the same clock. */
private fun Float.startingAt(start: Float): Float = ((this - start) / (1f - start)).coerceIn(0f, 1f)

/**
 * Head of a list dialog that names what the list belongs to: its [icon], [title] and a
 * [subtitle] summing the list up, with the list's search field and its toggle where it has one.
 *
 * The header floats over its [ListDialog]'s list, bare while the list rests at its start unless
 * [alwaysShowBand] keeps its band down. Once the list scrolls on under it, it lays down a band like
 * the app details' one, reaching the screen's edges and blurring the rows under it, see [HeaderBand].
 *
 * @param subtitleLoading Shows a shimmer in place of the [subtitle] while the list it sums uploads,
 *   easing into the text once it is there.
 * @param search The list's search field, or null for a list short enough to go without one.
 * @param searchLabel What the field searches, as its label and the toggle's description.
 * @param searchEnabled Whether there is anything to search yet. The toggle stays in place while
 *   there is not, so the title does not reflow when a loading list fills in.
 *
 * @param accentColor Color of the app the list belongs to, which tints the band and the title
 *   actions and badges on it, or null for a neutral band and actions in the theme's palette. The
 *   dialog's own by default, see [ListDialog].
 * @param badges What the whole list shares, in a row under the title as the app details keep theirs,
 *   folding away with the [collapsible] part.
 * @param content More of the list's head, under the title, that stays in place however far the
 *   list scrolls, such as where a file picker is in the folder tree or the tabs between sources.
 * @param collapsible What heads the list but is wanted only now and then, such as actions over
 *   the whole of it. Under the [content], it folds away under the rest of the header together with
 *   the [badges] as the list scrolls on, and comes back as the list scrolls back, see
 *   [DialogHeaderCollapse].
 * @param alwaysShowBand Keeps the band down while the list rests too, for a dialog about one thing,
 *   such as an app's details or a patch's options, rather than a list to browse.
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
    accentColor: Color? = LocalAccent.current,
    badges: (@Composable FlowRowScope.() -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
    collapsible: (@Composable ColumnScope.() -> Unit)? = null,
    alwaysShowBand: Boolean = false,
    actions: (@Composable RowScope.() -> Unit)? = null
) {
    // An accent read from a picture can land after the header is up, so the band eases into it
    val band by animateColorAsState(
        targetValue = appAccentFill(accentColor),
        animationSpec = tween(Defaults.ANIMATION_DURATION),
        label = "list_header_band"
    )
    Box(modifier = modifier.fillMaxWidth()) {
        // Bare at rest unless always shown, the band laid down once the list scrolls on under the header
        val backdrop = LocalHeaderBackdrop.current
        HeaderBand(band, HeaderBandShape, scrolledUnder = { alwaysShowBand || backdrop?.scrolledUnder ?: true })
        HeaderContent(
            icon = icon,
            title = title,
            subtitle = subtitle,
            subtitleLoading = subtitleLoading,
            search = search,
            searchLabel = searchLabel,
            searchEnabled = searchEnabled,
            accentColor = accentColor,
            badges = badges,
            actions = actions,
            content = content,
            collapsible = collapsible,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .widthIn(max = Defaults.ContentMaxWidth)
                .fillMaxWidth()
                .padding(horizontal = LocalDialogHorizontalInset.current)
        )
    }
}

/** What a [ListDialogHeader] holds over its band. */
@Composable
private fun HeaderContent(
    icon: @Composable (Modifier) -> Unit,
    title: String,
    subtitle: String,
    subtitleLoading: Boolean,
    search: SearchFieldState?,
    searchLabel: String?,
    searchEnabled: Boolean,
    accentColor: Color?,
    badges: (@Composable FlowRowScope.() -> Unit)?,
    actions: (@Composable RowScope.() -> Unit)?,
    content: (@Composable ColumnScope.() -> Unit)?,
    collapsible: (@Composable ColumnScope.() -> Unit)?,
    modifier: Modifier
) {
    val entrance = rememberDialogHeaderEntrance()

    Column(modifier = modifier.padding(vertical = DialogHeaderDefaults.VerticalPadding)) {
        ProvideAccent(accentColor) {
            HeaderBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(DialogHeaderDefaults.IconSpacing)
                    ) {
                        icon(Modifier.size(DialogHeaderDefaults.IconSize).headerIconEntrance(entrance))
                        HeaderTitleText(
                            title = title,
                            subtitle = subtitle,
                            subtitleLoading = subtitleLoading,
                            entrance = entrance,
                            modifier = Modifier.weight(1f)
                        )
                    }
                },
                actions = actions,
                search = search,
                searchLabel = searchLabel,
                searchEnabled = searchEnabled,
                controlsModifier = Modifier.headerBadgeEntrance(entrance)
            )
        }
        content?.invoke(this)
        if (badges != null || collapsible != null) {
            val collapse = LocalDialogHeaderCollapse.current
            if (collapse != null) {
                // Leaving the header, as on a page without it, leaves no room held for it folded
                DisposableEffect(collapse) { onDispose { collapse.measured(0) } }
            }
            Column(modifier = if (collapse != null) Modifier.foldsWith(collapse) else Modifier) {
                if (badges != null) {
                    // A row that folds its chips out resizes on the list's own spring, as the list's
                    // rows do, without the settle, which would dip below the chips and clip them
                    ProvideAccent(accentColor) {
                        StatusBadgeRow(
                            modifier = Modifier
                                .padding(top = Defaults.SpaceSmall)
                                .animateContentSize(Animations.listSpring(dampingRatio = Spring.DampingRatioNoBouncy))
                                .headerBadgeEntrance(entrance),
                            content = badges
                        )
                    }
                }
                if (collapsible != null) {
                    Column(modifier = Modifier.padding(top = Defaults.SpaceSmall), content = collapsible)
                }
            }
        }
    }
}

/** Title of a [ListDialogHeader] over the [subtitle] summing its list up. */
@Composable
private fun HeaderTitleText(
    title: String,
    subtitle: String,
    subtitleLoading: Boolean,
    entrance: DialogHeaderEntrance,
    modifier: Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(DialogHeaderDefaults.TextSpacing)
    ) {
        // Two lines, so a list named in a phrase rather than a name keeps it whole beside
        // the header's actions
        Text(
            text = title,
            style = DialogHeaderDefaults.titleStyle,
            color = dialogTextColor(),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.headerTitleEntrance(entrance)
        )
        val subtitleStyle = DialogHeaderDefaults.subtitleStyle
        // Eases from the shimmer into the text and from one text to the next as the list it
        // sums up changes, the header resizing along when the new one takes more lines
        AnimatedContent(
            targetState = subtitle.takeUnless { subtitleLoading },
            transitionSpec = Animations.fadeCrossfade(),
            contentAlignment = Alignment.TopStart,
            label = "list_header_subtitle",
            modifier = Modifier.headerSubtitleEntrance(entrance)
        ) { text ->
            if (text == null) {
                // A line's height, the size of the shortest subtitle it stands in for
                ShimmerText(
                    widthFraction = 0.5f,
                    height = with(LocalDensity.current) { subtitleStyle.lineHeight.toDp() }
                )
            } else {
                Text(
                    text = text,
                    style = subtitleStyle,
                    color = dialogSecondaryTextColor()
                )
            }
        }
    }
}

/**
 * [ListDialogHeader] icon for a list that stands for no app or source of its own: [icon] on a disc
 * of [color], the color the header takes on too.
 */
@Composable
fun ListDialogHeaderIcon(icon: ImageVector, color: Color, modifier: Modifier = Modifier) {
    val accent = ThemeTraitsDefaults.accentColor(color)
    StatusCircleIcon(
        icon = icon,
        containerColor = accent.copy(alpha = 0.2f),
        contentColor = accent,
        modifier = modifier,
        size = DialogHeaderDefaults.IconSize
    )
}

/** Outline of a header's band, rounded off below like the app details' header. */
internal val HeaderBandShape = RoundedCornerShape(bottomStart = Defaults.CornerLarge, bottomEnd = Defaults.CornerLarge)
