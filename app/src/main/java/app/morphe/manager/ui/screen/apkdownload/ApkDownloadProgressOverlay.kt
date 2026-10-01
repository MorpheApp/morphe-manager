/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.apkdownload

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.AppDialogButton
import app.morphe.manager.ui.screen.shared.Overlay
import app.morphe.manager.ui.screen.shared.PulsingLogoIndicator
import app.morphe.manager.ui.screen.shared.SemanticTone
import app.morphe.manager.ui.screen.shared.StatusBadge
import app.morphe.manager.ui.screen.shared.WavyProgressBar
import app.morphe.manager.util.formatBytes

/**
 * Shows the captured APK being fetched, and lets it be stopped.
 *
 * Separate from the dialog that starts the download on purpose: that dialog is dismissed as soon
 * as the file leaves the page, while the transfer it began runs on for minutes on a slow
 * connection. This is drawn over whatever screen the user moved on to.
 */
@Composable
fun ApkDownloadProgressOverlay(onCancel: () -> Unit) {
    val progress by ApkCaptureDownloader.progress.collectAsStateWithLifecycle()
    val current = progress ?: return

    val context = LocalContext.current
    val downloaded = remember(current.bytesDownloaded) {
        context.formatBytes(current.bytesDownloaded)
    }
    val total = remember(current.totalBytes) {
        current.totalBytes?.let { context.formatBytes(it) }
    }
    val speed = remember(current.bytesPerSecond) {
        // Below a kilobyte a second the rate is noise, and "0 B/s" reads as stalled
        current.bytesPerSecond.takeIf { it > 1024 }?.let { context.formatBytes(it) + "/s" }
    }

    // Fully opaque: nothing of the screen behind shows through. `Overlay` defaults to a partial
    // scrim, which leaves the app legible underneath and reads as a dialog over it; the download
    // takes over instead, so the theme background is drawn solid.
    Overlay(visible = true, backgroundAlpha = 1f) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PulsingLogoIndicator(
                size = 96.dp,
                contentDescription = stringResource(R.string.apk_download_in_progress),
            )

            Text(
                text = stringResource(R.string.apk_download_title, current.appName),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )

            // A percentage needs a total, and a server that sends none has not given one
            current.fraction?.let { fraction ->
                StatusBadge(
                    text = stringResource(R.string.patcher_percentage, (fraction * 100).toInt()),
                    tone = SemanticTone.Primary,
                )

                WavyProgressBar(
                    progress = { fraction },
                    accentColor = null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Text(
                text = when {
                    total != null && speed != null -> stringResource(
                        R.string.apk_download_of_total_with_speed,
                        downloaded,
                        total,
                        speed
                    )
                    total != null -> stringResource(R.string.apk_download_of_total, downloaded, total)
                    else -> downloaded
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            current.remainingSeconds?.takeIf { it > 0 }?.let { seconds ->
                Text(
                    text = stringResource(R.string.apk_download_remaining, formatRemaining(seconds)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AppDialogButton(
                text = stringResource(R.string.apk_capture_cancel),
                onClick = onCancel,
                icon = Icons.Default.Close,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Time left as a rounded reading rather than a precise one. A download's rate moves constantly,
 * and "1m 47s" would claim an accuracy the estimate does not have.
 */
@Composable
private fun formatRemaining(seconds: Long): String = when {
    seconds < 60 -> stringResource(R.string.apk_download_remaining_seconds, seconds)
    seconds < 3600 -> stringResource(R.string.apk_download_remaining_minutes, (seconds + 30) / 60)
    else -> stringResource(R.string.apk_download_remaining_hours, (seconds + 1800) / 3600)
}
