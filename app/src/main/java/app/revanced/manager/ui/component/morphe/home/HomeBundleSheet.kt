package app.revanced.manager.ui.component.morphe.home

import android.annotation.SuppressLint
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.revanced.manager.domain.bundles.PatchBundleSource
import app.revanced.manager.domain.manager.PreferencesManager.PatchBundleConstants.BUNDLE_URL_RELEASES
import app.revanced.manager.domain.repository.PatchBundleRepository
import app.revanced.manager.ui.component.morphe.utils.getRelativeTimeString
import app.revanced.manager.util.toast
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Bottom sheet displaying bundle information and update controls
 */
@SuppressLint("LocalContextGetResourceValueCall")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeBundleSheet(
    apiBundle: PatchBundleSource?,
    patchCounts: Map<Int, Int>,
    manualUpdateInfo: Map<Int, PatchBundleRepository.ManualBundleUpdateInfo?>,
    isRefreshing: Boolean,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onPatchesClick: () -> Unit,
    onVersionClick: () -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollState = rememberScrollState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentWindowInsets = { WindowInsets.systemBars },
        scrimColor = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(start = 24.dp, bottom = 24.dp, end = 24.dp)
        ) {
            // Content
            if (apiBundle != null) {
                BundleContent(
                    bundle = apiBundle,
                    patchCount = patchCounts[apiBundle.uid] ?: 0,
                    updateInfo = manualUpdateInfo[apiBundle.uid],
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    onOpenInBrowser = {
                        val pageUrl = manualUpdateInfo[apiBundle.uid]?.pageUrl
                            ?: BUNDLE_URL_RELEASES
                        try {
                            uriHandler.openUri(pageUrl)
                        } catch (_: Exception) {
                            context.toast(context.getString(R.string.morphe_home_failed_to_open_url))
                        }
                    },
                    onPatchesClick = onPatchesClick,
                    onVersionClick = onVersionClick,
                )
            }
        }
    }
}

/**
 * Content of the bundle sheet
 */
@Composable
private fun BundleContent(
    bundle: PatchBundleSource,
    patchCount: Int,
    updateInfo: PatchBundleRepository.ManualBundleUpdateInfo?,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onOpenInBrowser: () -> Unit,
    onPatchesClick: () -> Unit,
    onVersionClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header with bundle info
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.Source,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = bundle.displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.morphe_home_bundle_type_api),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("•", style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = bundle.updatedAt?.let { getRelativeTimeString(it) }
                            ?: stringResource(R.string.morphe_home_unknown),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            IconButton(onClick = onOpenInBrowser) {
                Icon(
                    Icons.AutoMirrored.Outlined.OpenInNew,
                    contentDescription = stringResource(R.string.morphe_home_open_in_browser),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Stats Row - Patches and Version
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatChip(
                icon = Icons.Outlined.Info,
                label = stringResource(R.string.patches),
                value = patchCount.toString(),
                modifier = Modifier.weight(1f),
                onClick = onPatchesClick
            )

            StatChip(
                icon = Icons.Outlined.Update,
                label = stringResource(R.string.version),
                value = updateInfo?.latestVersion?.removePrefix("v")
                    ?: bundle.patchBundle?.manifestAttributes?.version?.removePrefix("v")
                    ?: "N/A",
                modifier = Modifier.weight(1f),
                onClick = onVersionClick
            )
        }

        // Expandable timeline section
        TimelineSection(
            bundle = bundle,
            modifier = Modifier.fillMaxWidth()
        )

        // Update button
        Button(
            onClick = onRefresh,
            enabled = !isRefreshing,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (updateInfo != null) stringResource(R.string.update)
                else stringResource(R.string.morphe_home_check_updates),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

/**
 * Stat chip showing icon, label, and value with haptic feedback
 * Used for displaying patch count and version
 */
@Composable
private fun StatChip(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val view = LocalView.current

    Surface(
        modifier = modifier,
        onClick = {
            // Trigger haptic feedback on click
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            onClick()
        },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/**
 * Expandable timeline section showing bundle dates
 */
@Composable
private fun TimelineSection(
    bundle: PatchBundleSource,
    modifier: Modifier = Modifier
) {
    var showDates by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.clickable { showDates = !showDates },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.morphe_home_timeline),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = if (showDates)
                        Icons.Default.KeyboardArrowUp
                    else
                        Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            AnimatedVisibility(visible = showDates) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TimelineItem(
                        icon = Icons.Outlined.CalendarToday,
                        label = stringResource(R.string.morphe_home_date_added),
                        time = bundle.createdAt ?: 0L,
                    )

                    TimelineItem(
                        icon = Icons.Outlined.Refresh,
                        label = stringResource(R.string.morphe_home_date_updated),
                        time = bundle.updatedAt ?: 0L,
                        isLast = true
                    )
                }
            }
        }
    }
}

/**
 * Timeline item showing icon, label, and formatted date
 */
@Composable
private fun TimelineItem(
    icon: ImageVector,
    label: String,
    time: Long?,
    isLast: Boolean = false
) {
    val dateTimeFormatter = remember { SimpleDateFormat("dd.MM.yy HH:mm", Locale.getDefault()) }

    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(24.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = time?.let { dateTimeFormatter.format(it) }
                    ?: stringResource(R.string.morphe_home_unknown),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = time?.let { getRelativeTimeString(it) }
                    ?: stringResource(R.string.morphe_home_unknown),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
