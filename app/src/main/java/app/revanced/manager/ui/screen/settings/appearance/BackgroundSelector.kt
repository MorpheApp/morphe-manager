package app.revanced.manager.ui.screen.settings.appearance

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.revanced.manager.ui.screen.shared.BackgroundType
import app.revanced.manager.ui.screen.shared.SectionCard
import app.revanced.manager.ui.screen.shared.WindowWidthSizeClass
import app.revanced.manager.ui.screen.shared.rememberWindowSize

/**
 * Background animation selector with adaptive grid
 */
@Composable
fun BackgroundSelector(
    selectedBackground: BackgroundType,
    onBackgroundSelected: (BackgroundType) -> Unit
) {
    val windowSize = rememberWindowSize()
    val columns = when (windowSize.widthSizeClass) {
        WindowWidthSizeClass.Compact -> 3
        WindowWidthSizeClass.Medium -> 4
        WindowWidthSizeClass.Expanded -> 5
    }

    SectionCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BackgroundType.entries.chunked(columns).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { bgType ->
                        val icon = getBackgroundIcon(bgType)

                        ModernIconOptionCard(
                            selected = selectedBackground == bgType,
                            onClick = { onBackgroundSelected(bgType) },
                            icon = icon,
                            label = stringResource(bgType.displayNameResId),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // Fill remaining space if row is incomplete
                    repeat(columns - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * Get icon for background type
 */
private fun getBackgroundIcon(type: BackgroundType): ImageVector = when (type) {
    BackgroundType.CIRCLES -> Icons.Outlined.Circle
    BackgroundType.RINGS -> Icons.Outlined.RadioButtonUnchecked
    BackgroundType.MESH -> Icons.Outlined.Grid3x3
    BackgroundType.SPACE -> Icons.Outlined.AutoAwesome
    BackgroundType.SHAPES -> Icons.Outlined.Pentagon
    BackgroundType.SNOW -> Icons.Outlined.AcUnit
    BackgroundType.NONE -> Icons.Outlined.VisibilityOff
}
