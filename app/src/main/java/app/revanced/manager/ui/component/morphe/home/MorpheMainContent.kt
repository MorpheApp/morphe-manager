package app.revanced.manager.ui.component.morphe.home

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(modifier = Modifier.fillMaxSize()) {
        // Animated background circles
        AnimatedBackgroundCircles()

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
 * Animated background circles with infinite transitions
 * Creates subtle floating effect in background
 */
@Composable
private fun AnimatedBackgroundCircles() {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    val infiniteTransition = rememberInfiniteTransition(label = "circles")

    // Circle 1 - large top left
    val circle1X = infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "circle1X"
    )
    val circle1Y = infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(7000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "circle1Y"
    )

    // Circle 2 - medium top right
    val circle2X = infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 0.82f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "circle2X"
    )
    val circle2Y = infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(6500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "circle2Y"
    )

    // Circle 3 - small center right
    val circle3X = infiniteTransition.animateFloat(
        initialValue = 0.75f,
        targetValue = 0.68f,
        animationSpec = infiniteRepeatable(
            animation = tween(7500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "circle3X"
    )
    val circle3Y = infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.48f,
        animationSpec = infiniteRepeatable(
            animation = tween(8500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "circle3Y"
    )

    // Circle 4 - medium bottom right
    val circle4X = infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 0.78f,
        animationSpec = infiniteRepeatable(
            animation = tween(9500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "circle4X"
    )
    val circle4Y = infiniteTransition.animateFloat(
        initialValue = 0.75f,
        targetValue = 0.82f,
        animationSpec = infiniteRepeatable(
            animation = tween(7200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "circle4Y"
    )

    // Circle 5 - small bottom left
    val circle5X = infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(8200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "circle5X"
    )
    val circle5Y = infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.73f,
        animationSpec = infiniteRepeatable(
            animation = tween(6800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "circle5Y"
    )

    // Circle 6 - bottom center
    val circle6X = infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(8800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "circle6X"
    )
    val circle6Y = infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 0.87f,
        animationSpec = infiniteRepeatable(
            animation = tween(7800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "circle6Y"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        // Circle 1 - large top left
        drawCircle(
            color = primaryColor.copy(alpha = 0.05f),
            radius = 400f,
            center = Offset(size.width * circle1X.value, size.height * circle1Y.value)
        )

        // Circle 2 - medium top right
        drawCircle(
            color = tertiaryColor.copy(alpha = 0.035f),
            radius = 280f,
            center = Offset(size.width * circle2X.value, size.height * circle2Y.value)
        )

        // Circle 3 - small center right
        drawCircle(
            color = tertiaryColor.copy(alpha = 0.04f),
            radius = 200f,
            center = Offset(size.width * circle3X.value, size.height * circle3Y.value)
        )

        // Circle 4 - medium bottom right
        drawCircle(
            color = secondaryColor.copy(alpha = 0.035f),
            radius = 320f,
            center = Offset(size.width * circle4X.value, size.height * circle4Y.value)
        )

        // Circle 5 - small bottom left
        drawCircle(
            color = primaryColor.copy(alpha = 0.04f),
            radius = 180f,
            center = Offset(size.width * circle5X.value, size.height * circle5Y.value)
        )

        // Circle 6 - bottom center
        drawCircle(
            color = secondaryColor.copy(alpha = 0.04f),
            radius = 220f,
            center = Offset(size.width * circle6X.value, size.height * circle6Y.value)
        )
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
