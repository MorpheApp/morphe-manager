package app.revanced.manager.ui.component.morphe.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.revanced.manager.ui.component.morphe.shared.*
import app.revanced.manager.ui.viewmodel.HomeAndPatcherMessages

/**
 * Main content area for MorpheHomeScreen with adaptive layout
 * Displays animated background, greeting message, and app selection buttons
 */
@Composable
fun HomeMainContent(
    onYouTubeClick: () -> Unit,
    onYouTubeMusicClick: () -> Unit,
    backgroundType: BackgroundType = BackgroundType.CIRCLES
) {
    val windowSize = rememberWindowSize()
    val greeting = HomeAndPatcherMessages.getHomeMessage(LocalContext.current)

    Box(modifier = Modifier.fillMaxSize()) {
        // Animated background
        AnimatedBackground(type = backgroundType)

        // Adaptive content layout
        AdaptiveCenteredLayout(windowSize = windowSize) {
            // Greeting message
            Text(
                text = stringResource(greeting),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = windowSize.itemSpacing * 2)
            )

            // App buttons
            Column(
                modifier = Modifier.widthIn(max = 500.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(windowSize.itemSpacing)
            ) {
                // YouTube button
                HomeAppButton(
                    text = stringResource(R.string.morphe_home_youtube),
                    backgroundColor = Color(0xFFFF0033),
                    contentColor = Color.White,
                    gradientColors = listOf(
                        Color(0xFFFF0033), // YouTube red
                        Color(0xFF1E5AA8), // Brand blue
                        Color(0xFF00AFAE)  // Brand teal
                    ),
                    onClick = onYouTubeClick
                )

                // YouTube Music button
                HomeAppButton(
                    text = stringResource(R.string.morphe_home_youtube_music),
                    backgroundColor = Color(0xFFFF8C3E),
                    contentColor = Color.White,
                    gradientColors = listOf(
                        Color(0xFFFF8C3E), // Orange
                        Color(0xFF1E5AA8), // Brand blue
                        Color(0xFF00AFAE)  // Brand teal
                    ),
                    onClick = onYouTubeMusicClick
                )
            }
        }
    }
}
