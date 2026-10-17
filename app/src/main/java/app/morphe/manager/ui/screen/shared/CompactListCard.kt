/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Size of the picture leading a [CompactListCard]. */
val CompactCardIconSize = 36.dp

/** Size of a glyph drawn on a [CompactCardIconTile]. */
val CompactCardGlyphSize = 20.dp

/** Gap between the [CompactListCard]s of a list, closer than full cards as they are short. */
val CompactCardSpacing = Defaults.SpaceSmall

private val CompactCardPadding = PaddingValues(Defaults.SpaceMedium)

/**
 * Card of a long list to pick one entry from, the one patch cards sit on, laying out a picture
 * and the entry's name over its details in a row. Such a list can run to hundreds of entries, a
 * folder or the installed apps, so the card is set tighter than the app's other cards to keep as
 * many in view as plain rows would.
 */
@Composable
fun CompactListCard(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    SettingsItemCard(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
        showBorder = true,
        borderColor = MaterialTheme.colorScheme.outlineVariant,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(CompactCardPadding),
            horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceMedium),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

/**
 * Tinted tile leading a [CompactListCard] whose entry has no picture of its own, holding a glyph
 * of [CompactCardGlyphSize] drawn in [contentColor].
 */
@Composable
fun CompactCardIconTile(
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .size(CompactCardIconSize)
            .background(containerColor, RoundedCornerShape(Defaults.CornerMedium)),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
    }
}
