/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Heading band across the top of a card: an [icon] or [leading] mark, the [title] with an optional
 * [description], and [trailing] badges at the far edge.
 *
 * @param accentColor The card's edge color, which the band takes at [AccentAlpha.CARD]. Null for
 *   the neutral veil.
 * @param wrapTrailing Lets [trailing] drop under the title where the two do not fit one line, as
 *   a large font can make them, rather than squeezing the title onto several.
 */
@Composable
fun CardHeader(
    title: String?,
    modifier: Modifier = Modifier,
    description: String? = null,
    accentColor: Color? = LocalAccent.current,
    icon: ImageVector? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    wrapTrailing: Boolean = false
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(accentColor?.copy(alpha = AccentAlpha.CARD) ?: neutralVeil())
            .padding(horizontal = Defaults.SpaceLarge, vertical = Defaults.SpaceSmall),
        horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when {
            leading != null -> leading()
            icon != null -> Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        }
        when {
            title == null -> {
                Spacer(Modifier.weight(1f))
                trailing?.invoke(this)
            }

            // The far edge holds while both fit, and a line of its own takes over once they do not
            trailing != null && wrapTrailing -> FlowRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(Defaults.SpaceExtraSmall),
                itemVerticalAlignment = Alignment.CenterVertically
            ) {
                CardHeaderTitle(title, description)
                trailing()
            }

            else -> {
                CardHeaderTitle(title, description, Modifier.weight(1f))
                trailing?.invoke(this)
            }
        }
    }
}

@Composable
private fun CardHeaderTitle(title: String, description: String?, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        description?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = LocalContentColor.current.copy(alpha = 0.7f)
            )
        }
    }
}
