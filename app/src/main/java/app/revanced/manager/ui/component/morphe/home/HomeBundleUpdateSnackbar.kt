package app.revanced.manager.ui.component.morphe.home

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.revanced.manager.domain.repository.PatchBundleRepository

/**
 * Snackbar showing bundle update progress and status
 */
@Composable
fun HomeBundleUpdateSnackbar(
    visible: Boolean,
    status: BundleUpdateStatus,
    progress: PatchBundleRepository.BundleUpdateProgress?,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { -it },
            animationSpec = tween(durationMillis = 500)
        ) + fadeIn(animationSpec = tween(durationMillis = 500)),
        exit = slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = tween(durationMillis = 500)
        ) + fadeOut(animationSpec = tween(durationMillis = 500)),
        modifier = modifier
    ) {
        BundleUpdateSnackbarContent(
            status = status,
            progress = progress
        )
    }
}

/**
 * Snackbar content with icon, text, and optional progress bar
 */
@Composable
private fun BundleUpdateSnackbarContent(
    status: BundleUpdateStatus,
    progress: PatchBundleRepository.BundleUpdateProgress?
) {
    val fraction = if (progress?.total == 0 || progress == null) {
        0f
    } else {
        progress.completed.toFloat() / progress.total
    }

    val containerColor = when (status) {
        BundleUpdateStatus.Success -> MaterialTheme.colorScheme.primaryContainer
        BundleUpdateStatus.Error   -> MaterialTheme.colorScheme.errorContainer
        BundleUpdateStatus.Updating -> MaterialTheme.colorScheme.surfaceVariant
    }

    val contentColor = when (status) {
        BundleUpdateStatus.Success -> MaterialTheme.colorScheme.onPrimaryContainer
        BundleUpdateStatus.Error   -> MaterialTheme.colorScheme.onErrorContainer
        BundleUpdateStatus.Updating -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon based on status
                when (status) {
                    BundleUpdateStatus.Success -> {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    BundleUpdateStatus.Error -> {
                        Icon(
                            imageVector = Icons.Outlined.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    BundleUpdateStatus.Updating -> {
                        CircularProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 3.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Text content
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when (status) {
                            BundleUpdateStatus.Updating -> stringResource(R.string.morphe_home_updating_patches)
                            BundleUpdateStatus.Success -> stringResource(R.string.morphe_home_update_success)
                            BundleUpdateStatus.Error -> stringResource(R.string.morphe_home_update_error)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                    Text(
                        text = when (status) {
                            BundleUpdateStatus.Updating -> {
                                if (progress != null && progress.total > 0) {
                                    stringResource(
                                        R.string.bundle_update_progress,
                                        progress.completed,
                                        progress.total
                                    )
                                } else {
                                    stringResource(R.string.morphe_home_please_wait)
                                }
                            }
                            BundleUpdateStatus.Success -> stringResource(R.string.morphe_home_patches_updated)
                            BundleUpdateStatus.Error -> stringResource(R.string.morphe_home_update_error_subtitle)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor.copy(alpha = 0.8f)
                    )
                }
            }

            // Progress bar only for updating status
            if (status == BundleUpdateStatus.Updating) {
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}
