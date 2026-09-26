/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morphe.manager.patcher.patch.withoutSourceIndent

/**
 * Scrollable instructions box, fading out at whichever end has more text past it.
 */
@Composable
fun ScrollableInstruction(
    description: String,
    modifier: Modifier = Modifier,
    maxHeight: Dp = 300.dp
) {
    val scrollState = rememberScrollState()

    // Localized instructions reach this box straight from strings.xml, unlike the bundle text
    // PatchInfo unindents on the way in
    val instructions = remember(description) { description.withoutSourceIndent() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            // The box is dropped on surfaces of their own tint, which the fade leaves showing
            .verticalScrollFade(scrollState)
            .verticalScroll(scrollState)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SettingsDivider(fullWidth = true)

        Text(
            text = instructions,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.4f
        )
    }
}
