/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.*

/**
 * How much room a notice takes. [Comfortable] is the standalone warning a dialog is built
 * around, [Compact] the aside that sits between other content.
 */
enum class NoticeDensity {
    Comfortable,
    Compact
}

/** Sizing for a single [NoticeDensity]. */
private data class NoticeMetrics(
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
    val iconSize: Dp,
    val itemSpacing: Dp
)

private val ComfortableMetrics = NoticeMetrics(
    horizontalPadding = 16.dp,
    verticalPadding = 16.dp,
    iconSize = 24.dp,
    itemSpacing = 12.dp
)

private val CompactMetrics = NoticeMetrics(
    horizontalPadding = 12.dp,
    verticalPadding = 8.dp,
    iconSize = 20.dp,
    itemSpacing = 8.dp
)

/**
 * A remark following what it is about, a hint, a caveat or what a choice leads to, set as a line
 * of text rather than on a surface of its own as a [Notice] is: an icon in the column of the icons
 * of the cards and rows above, and its [text] where their titles start. A [tone] other than neutral
 * marks one that warns, its icon and text taking the role's color, and an [icon] of its own one
 * that says more than that.
 */
@Composable
fun Footnote(
    text: String,
    modifier: Modifier = Modifier,
    tone: SemanticTone = SemanticTone.Neutral,
    icon: ImageVector = footnoteIcon(tone)
) {
    val color = tone.accent
    val textStyle = MaterialTheme.typography.bodyMedium
    // The icon and the first line are centered on each other, whichever is taller
    val lineHeight = with(LocalDensity.current) { textStyle.lineHeight.toDp() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Defaults.SpaceLarge),
        horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceMedium)
    ) {
        Box(
            modifier = Modifier
                .padding(top = ((lineHeight - Defaults.IconSize) / 2).coerceAtLeast(0.dp))
                .size(Defaults.IconSize),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = text,
            style = textStyle,
            color = color,
            modifier = Modifier
                .weight(1f)
                .padding(top = ((Defaults.IconSize - lineHeight) / 2).coerceAtLeast(0.dp))
        )
    }
}

/** Icon a [Footnote] in [tone] takes unless it brings its own. */
internal fun footnoteIcon(tone: SemanticTone): ImageVector = when (tone) {
    SemanticTone.Neutral, SemanticTone.Primary -> MaterialSymbols.Rounded.Info
    SemanticTone.Error -> MaterialSymbols.Rounded.Error
    else -> MaterialSymbols.Rounded.Warning
}

/** Shape of a [Notice], which its action row takes too, so its press and focus fit the corners. */
private val NoticeShape = RoundedCornerShape(Defaults.CornerMedium)

/** Link under a [Notice], to where its message can be read in full or acted on. */
class NoticeAction(
    val text: String,
    val onClick: () -> Unit
)

/**
 * Full-width tinted block carrying what stands on its own: a dialog's warning, the state of what it
 * is about, a banner heading a list or a message with an action. A remark following what it is
 * about is a [Footnote] instead.
 *
 * @param text The message
 * @param title Optional headline set above the message, not counted against [maxLines]
 * @param icon Optional icon drawn before the message
 * @param tone Semantic color role
 * @param density How much room the block takes
 * @param isCentered Centers the content and shrinks the block to fit it
 * @param maxLines Lines the message may take before it is cut short with an ellipsis
 * @param overflowAction Offered under the message only once [maxLines] cut it short, so a
 *        message that fits never points at a longer version of itself
 * @param action Offered under the message whatever its length, ahead of [overflowAction]
 * @param onClick Makes the whole block a button, for a notice that opens what it tells of
 * @param modifier Modifier to be applied to the notice
 */
@Composable
fun Notice(
    text: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: ImageVector? = null,
    tone: SemanticTone = SemanticTone.Neutral,
    density: NoticeDensity = NoticeDensity.Comfortable,
    isCentered: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    overflowAction: NoticeAction? = null,
    action: NoticeAction? = null,
    onClick: (() -> Unit)? = null
) {
    val metrics = when (density) {
        NoticeDensity.Comfortable -> ComfortableMetrics
        NoticeDensity.Compact -> CompactMetrics
    }
    val textStyle = when (density) {
        NoticeDensity.Comfortable -> MaterialTheme.typography.bodyMedium
        NoticeDensity.Compact -> MaterialTheme.typography.bodySmall
    }
    val contentColor = tone.content
    val textAlign = if (isCentered) TextAlign.Center else TextAlign.Start

    // Add zero-width space so long tokens can break at "/" and "." - cached per text value
    val breakableText = remember(text) {
        text.replace("/", "/​").replace(".", ".​")
    }

    var cutShort by remember(breakableText, maxLines) { mutableStateOf(false) }

    // The icon heads the first line rather than the middle of the block, which a long message or a
    // large font would leave out of view. The two are centered on each other, whichever is taller
    val firstLineHeight = with(LocalDensity.current) { textStyle.lineHeight.toDp() }
    val iconTopInset = ((firstLineHeight - metrics.iconSize) / 2).coerceAtLeast(0.dp)
    val textTopInset = ((metrics.iconSize - firstLineHeight) / 2).coerceAtLeast(0.dp)

    Surface(
        modifier = (if (isCentered) modifier.wrapContentWidth() else modifier.fillMaxWidth())
            .then(if (onClick != null) Modifier.shapedClickable(NoticeShape, role = Role.Button, onClick = onClick) else Modifier),
        shape = NoticeShape,
        color = tone.container,
        border = CardBorder.tinted(tone.accent)
    ) {
        Column {
            Row(
                modifier = Modifier.padding(
                    horizontal = metrics.horizontalPadding,
                    vertical = metrics.verticalPadding
                ),
                horizontalArrangement = if (isCentered) {
                    Arrangement.spacedBy(metrics.itemSpacing, Alignment.CenterHorizontally)
                } else {
                    Arrangement.spacedBy(metrics.itemSpacing)
                },
                verticalAlignment = Alignment.Top
            ) {
                icon?.let {
                    ThemedIcon(
                        icon = it,
                        tint = contentColor,
                        size = metrics.iconSize,
                        modifier = Modifier.padding(top = iconTopInset)
                    )
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.padding(top = if (icon != null) textTopInset else 0.dp)
                ) {
                    title?.let {
                        Text(
                            text = it,
                            style = textStyle,
                            fontWeight = FontWeight.SemiBold,
                            color = contentColor,
                            textAlign = textAlign
                        )
                    }
                    Text(
                        text = breakableText,
                        style = textStyle,
                        color = contentColor,
                        textAlign = textAlign,
                        maxLines = maxLines,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { cutShort = it.hasVisualOverflow }
                    )
                }
            }

            val shownAction = action ?: overflowAction?.takeIf { cutShort }
            if (shownAction != null) {
                HorizontalDivider(color = tone.accent.copy(alpha = 0.2f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shapedClickable(NoticeShape, role = Role.Button, onClick = shownAction.onClick)
                        .heightIn(min = 44.dp)
                        // Lined up with the message rather than the icon, as the rest of its text
                        .padding(
                            start = metrics.horizontalPadding +
                                    if (icon != null) metrics.iconSize + metrics.itemSpacing else 0.dp,
                            end = metrics.horizontalPadding
                        ),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = shownAction.text,
                        style = textStyle,
                        fontWeight = FontWeight.Medium,
                        color = contentColor
                    )
                    ForwardChevronIcon(size = Defaults.IconSizeSmall, tint = contentColor)
                }
            }
        }
    }
}
