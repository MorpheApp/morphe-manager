/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Source
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.AnimatedBackground
import app.morphe.manager.ui.screen.shared.BackgroundType
import com.google.accompanist.drawablepainter.rememberDrawablePainter

/**
 * Full-screen welcome splash shown on first launch before the onboarding tour begins.
 *
 * Renders on top of the [app.morphe.manager.ui.screen.HomeScreen] via an [androidx.compose.animation.AnimatedVisibility]
 * overlay in `MorpheManager`. The [androidx.compose.material3.Surface] root blocks all pointer events
 * so underlying UI cannot be interacted with while this screen is visible.
 *
 * @param onStartTour Called when the user taps "Start Tour" - advances to the [OnboardingShowcase] flow.
 * @param onSkip Called when the user taps "Skip" - marks first launch as complete and dismisses onboarding entirely.
 */
@Composable
fun WelcomeScreen(onStartTour: () -> Unit, onSkip: () -> Unit) {
    val context = LocalContext.current

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        AnimatedBackground(type = BackgroundType.CIRCLES, enableParallax = false)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(Modifier.height(48.dp))

            // Icon and title
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    modifier = Modifier.size(96.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = androidx.compose.ui.graphics.Color.White,
                    shadowElevation = 12.dp
                ) {
                    Image(
                        painter = rememberDrawablePainter(
                            AppCompatResources.getDrawable(context, R.drawable.ic_launcher_foreground)
                        ),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { scaleX = 1.5f; scaleY = 1.5f }
                    )
                }

                Spacer(Modifier.height(28.dp))

                Text(
                    text = stringResource(R.string.welcome_title, stringResource(R.string.app_name)),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.welcome_description),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            // Feature highlights
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                WelcomeFeatureItem(Icons.Outlined.Apps, R.string.welcome_feature_patches)
                WelcomeFeatureItem(Icons.Outlined.Refresh, R.string.welcome_feature_updates)
                WelcomeFeatureItem(Icons.Outlined.Source, R.string.welcome_feature_safe)
            }

            // Buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = onStartTour,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.welcome_start_tour),
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Spacer(Modifier.height(4.dp))

                TextButton(onClick = onSkip) {
                    Text(
                        text = stringResource(R.string.welcome_skip),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/**
 * Single feature highlight row displayed in the middle section of [WelcomeScreen].
 *
 * @param icon Icon representing the feature.
 * @param textRes String resource describing the feature in one short phrase.
 */
@Composable
private fun WelcomeFeatureItem(icon: ImageVector, textRes: Int) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = stringResource(textRes),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
