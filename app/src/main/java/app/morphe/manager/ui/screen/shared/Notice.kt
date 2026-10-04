/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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

/** Link under a [Notice], to where its message can be read in full or acted on. */
class NoticeAction(
    val text: String,
    val onClick: () -> Unit
)

/**
 * Full-width tinted block carrying a warning, a hint or a status line.
 *
 * @param text The message
 * @param icon Optional icon drawn before the message
 * @param tone Semantic color role
 * @param density How much room the block takes
 * @param isCentered Centers the content and shrinks the block to fit it
 * @param maxLines Lines the message may take before it is cut short with an ellipsis
 * @param overflowAction Offered under the message only once [maxLines] cut it short, so a
 *        message that fits never points at a longer version of itself
 * @param action Offered under the message whatever its length, ahead of [overflowAction]
 * @param modifier Modifier to be applied to the notice
 */
@Composable
fun Notice(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tone: SemanticTone = SemanticTone.Neutral,
    density: NoticeDensity = NoticeDensity.Comfortable,
    isCentered: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    overflowAction: NoticeAction? = null,
    action: NoticeAction? = null
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

    // Add zero-width space so long tokens can break at "/" and "." - cached per text value
    val breakableText = remember(text) {
        text.replace("/", "/​").replace(".", ".​")
    }

    var cutShort by remember(breakableText, maxLines) { mutableStateOf(false) }

    Surface(
        modifier = if (isCentered) modifier.wrapContentWidth() else modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Defaults.CompactCornerRadius),
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
                verticalAlignment = Alignment.CenterVertically
            ) {
                icon?.let {
                    ThemedIcon(icon = it, tint = contentColor, size = metrics.iconSize)
                }
                Text(
                    text = breakableText,
                    style = textStyle,
                    color = contentColor,
                    textAlign = if (isCentered) TextAlign.Center else TextAlign.Start,
                    maxLines = maxLines,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { cutShort = it.hasVisualOverflow }
                )
            }

            val shownAction = action ?: overflowAction?.takeIf { cutShort }
            if (shownAction != null) {
                HorizontalDivider(color = tone.accent.copy(alpha = 0.2f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button, onClick = shownAction.onClick)
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
