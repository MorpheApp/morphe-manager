package app.revanced.manager.ui.screen.shared

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

/**
 * Adaptive layout that automatically switches between single and two-column layouts
 *
 * @param modifier Modifier for the root layout
 * @param windowSize Window size configuration
 * @param leftContent Content for left column (or full width in compact)
 * @param rightContent Optional content for right column (only shown in medium/expanded)
 */
@Composable
fun AdaptiveLayout(
    modifier: Modifier = Modifier,
    windowSize: WindowSize = rememberWindowSize(),
    leftContent: @Composable ColumnScope.() -> Unit,
    rightContent: (@Composable ColumnScope.() -> Unit)? = null
) {
    val useTwoColumns = windowSize.useTwoColumnLayout && rightContent != null
    val contentPadding = windowSize.contentPadding
    val columnSpacing = windowSize.itemSpacing

    if (useTwoColumns) {
        // Two-column layout for medium/expanded windows
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = contentPadding, vertical = contentPadding / 2),
            horizontalArrangement = Arrangement.spacedBy(columnSpacing * 2)
        ) {
            // Left column
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(columnSpacing)
            ) {
                leftContent()
            }

            // Right column
            rightContent.let {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(columnSpacing)
                ) {
                    it()
                }
            }
        }
    } else {
        // Single-column layout for compact windows
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = contentPadding / 2),
            verticalArrangement = Arrangement.spacedBy(columnSpacing)
        ) {
            leftContent()

            // In single column, show right content below left if provided
            rightContent?.invoke(this)
        }
    }
}

/**
 * Adaptive content layout for centered content with max width
 * Used for dialogs and centered screens
 */
@Composable
fun AdaptiveCenteredLayout(
    modifier: Modifier = Modifier,
    windowSize: WindowSize = rememberWindowSize(),
    content: @Composable ColumnScope.() -> Unit
) {
    val maxWidth = when (windowSize.widthSizeClass) {
        WindowWidthSizeClass.Compact -> 450.dp
        WindowWidthSizeClass.Medium -> 600.dp
        WindowWidthSizeClass.Expanded -> 800.dp
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .padding(horizontal = windowSize.contentPadding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(windowSize.itemSpacing),
            content = content
        )
    }
}

/**
 * Check if current orientation is landscape
 */
@Composable
fun isLandscape(): Boolean {
    val configuration = LocalConfiguration.current
    return configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
}
