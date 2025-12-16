package app.revanced.manager.ui.component.morphe.home

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.revanced.manager.ui.viewmodel.HomeAndPatcherMessages

/**
 * Main content area for MorpheHomeScreen
 * Displays animated background, greeting message, and app selection buttons
 * Adapts layout based on device orientation
 */
@Composable
fun MorpheMainContent(
    onYouTubeClick: () -> Unit,
    onYouTubeMusicClick: () -> Unit,
    backgroundType: BackgroundType = BackgroundType.CIRCLES
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(modifier = Modifier.fillMaxSize()) {
        // Animated background
        AnimatedBackground(type = backgroundType)

        // Adaptive layout based on orientation
        if (isLandscape) {
            LandscapeLayout(
                onYouTubeClick = onYouTubeClick,
                onYouTubeMusicClick = onYouTubeMusicClick
            )
        } else {
            PortraitLayout(
                onYouTubeClick = onYouTubeClick,
                onYouTubeMusicClick = onYouTubeMusicClick
            )
        }
    }
}

/**
 * Portrait layout - vertical arrangement
 * Greeting message on top, buttons below
 */
@Composable
private fun PortraitLayout(
    onYouTubeClick: () -> Unit,
    onYouTubeMusicClick: () -> Unit
) {
    val scrollState = rememberScrollState()
    val greeting = HomeAndPatcherMessages.getHomeMessage(LocalContext.current)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(32.dp)
            .padding(bottom = 120.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Greeting message
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedGreeting(greeting)
        }

        Spacer(Modifier.height(32.dp))

        Column(
            modifier = Modifier.widthIn(max = 500.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // YouTube button
            MorpheAppButton(
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
            MorpheAppButton(
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

/**
 * Landscape layout - horizontal arrangement
 * Greeting message on left, buttons on right
 */
@Composable
private fun LandscapeLayout(
    onYouTubeClick: () -> Unit,
    onYouTubeMusicClick: () -> Unit
) {
    val scrollState = rememberScrollState()
    val greeting = HomeAndPatcherMessages.getHomeMessage(LocalContext.current)

    Row(
        modifier = Modifier
            .fillMaxSize()
            .horizontalScroll(scrollState)
            .padding(24.dp)
            .padding(end = 100.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Greeting message on the left
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(end = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(greeting),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .widthIn(max = 500.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
        ) {
            // YouTube button
            MorpheAppButton(
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
            MorpheAppButton(
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

/**
 * Animated greeting message with fade transitions
 * Changes every 10 seconds
 */
@Composable
private fun AnimatedGreeting(greeting: Int) {
    AnimatedContent(
        targetState = stringResource(greeting),
        transitionSpec = {
            fadeIn(animationSpec = tween(1000)) togetherWith
                    fadeOut(animationSpec = tween(1000))
        },
        label = "message_animation"
    ) { messageText ->
        Text(
            text = messageText,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
