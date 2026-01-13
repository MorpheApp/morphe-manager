package app.revanced.manager.ui.component.morphe.home

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.revanced.manager.domain.bundles.PatchBundleSource
import app.revanced.manager.domain.bundles.PatchBundleSource.Extensions.isDefault
import app.revanced.manager.domain.bundles.RemotePatchBundle
import app.revanced.manager.domain.manager.PreferencesManager.PatchBundleConstants.BUNDLE_URL_RELEASES
import app.revanced.manager.domain.repository.PatchBundleRepository
import app.revanced.manager.ui.component.morphe.utils.getRelativeTimeString
import app.revanced.manager.util.toast
import org.koin.compose.koinInject

/**
 * Bottom sheet for managing patch bundles
 */
@SuppressLint("LocalContextGetResourceValueCall")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeBundleManagementSheet(
    onDismissRequest: () -> Unit,
    onAddBundle: () -> Unit,
    onDelete: (PatchBundleSource) -> Unit,
    onDisable: (PatchBundleSource) -> Unit,
    onUpdate: (PatchBundleSource) -> Unit,
    onRename: (PatchBundleSource) -> Unit,
    onPatchesClick: (PatchBundleSource) -> Unit,
    onVersionClick: (PatchBundleSource) -> Unit
) {
    val patchBundleRepository: PatchBundleRepository = koinInject()

    val sources by patchBundleRepository.sources.collectAsStateWithLifecycle(emptyList())
    val patchCounts by patchBundleRepository.patchCountsFlow.collectAsStateWithLifecycle(emptyMap())
    val manualUpdateInfo by patchBundleRepository.manualUpdateInfo.collectAsStateWithLifecycle(emptyMap())

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val lazyListState = rememberLazyListState()

    var bundleToDelete by remember { mutableStateOf<PatchBundleSource?>(null) }

    // Check if only default bundle exists
    val isSingleDefaultBundle = sources.size == 1

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        scrimColor = Color.Transparent
    ) {
        val context = LocalContext.current
        val uriHandler = LocalUriHandler.current

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.tab_patches),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(
                            R.string.morphe_bundle_management_subtitle,
                            sources.size
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                FilledIconButton(
                    onClick = onAddBundle,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.add)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bundle cards
            LazyColumn(
                state = lazyListState,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(sources, key = { bundle -> bundle.uid }) { bundle ->
                    BundleManagementCard(
                        bundle = bundle,
                        patchCount = patchCounts[bundle.uid] ?: 0,
                        updateInfo = manualUpdateInfo[bundle.uid],
                        onDelete = { bundleToDelete = bundle },
                        onDisable = { onDisable(bundle) },
                        onUpdate = { onUpdate(bundle) },
                        onRename = { onRename(bundle) },
                        onPatchesClick = { onPatchesClick(bundle) },
                        onVersionClick = { onVersionClick(bundle) },
                        onOpenInBrowser = {
                            val pageUrl = manualUpdateInfo[bundle.uid]?.pageUrl
                                ?: BUNDLE_URL_RELEASES
                            try {
                                uriHandler.openUri(pageUrl)
                            } catch (_: Exception) {
                                context.toast(context.getString(R.string.morphe_home_failed_to_open_url))
                            }
                        },
                        forceExpanded = isSingleDefaultBundle
                    )
                }
            }
        }
    }

    // Delete confirmation dialog
    if (bundleToDelete != null) {
        MorpheBundleDeleteConfirmDialog(
            bundle = bundleToDelete!!,
            onDismiss = { bundleToDelete = null },
            onConfirm = {
                onDelete(bundleToDelete!!)
                bundleToDelete = null
            }
        )
    }
}

/**
 * Card for individual bundle management
 */
@Composable
private fun BundleManagementCard(
    bundle: PatchBundleSource,
    patchCount: Int,
    updateInfo: PatchBundleRepository.ManualBundleUpdateInfo?,
    onDelete: () -> Unit,
    onDisable: () -> Unit,
    onUpdate: () -> Unit,
    onRename: () -> Unit,
    onPatchesClick: () -> Unit,
    onVersionClick: () -> Unit,
    onOpenInBrowser: () -> Unit,
    forceExpanded: Boolean = false
) {
    var expanded by remember { mutableStateOf(forceExpanded) }
    val rotationAngle by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "arrow rotation"
    )

    // Update expanded state when forceExpanded changes
    LaunchedEffect(forceExpanded) {
        if (forceExpanded) {
            expanded = true
        }
    }

    Surface(
        onClick = { if (!forceExpanded) expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
        tonalElevation = 3.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Bundle icon
                Surface(
                    shape = CircleShape,
                    color = if (bundle.enabled) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (bundle.isDefault) {
                                Icons.Outlined.Stars
                            } else {
                                Icons.Outlined.Source
                            },
                            contentDescription = null,
                            tint = if (bundle.enabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Bundle info
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = bundle.displayTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (bundle.enabled) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.outline
                            }
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (bundle.isDefault) {
                                Text(
                                    text = stringResource(R.string.bundle_type_preinstalled),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                val type = when (bundle) {
                                    is RemotePatchBundle -> stringResource(R.string.bundle_type_remote)
                                    else -> stringResource(R.string.bundle_type_local)
                                }
                                Text(
                                    text = type,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (!bundle.enabled) {
                                Text("•", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = stringResource(R.string.morphe_disabled),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }

                    // Rename button (only for non-default bundles)
                    if (!bundle.isDefault) {
                        IconButton(
                            onClick = onRename,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = stringResource(R.string.morphe_rename),
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Expand arrow (hidden when forceExpanded)
                if (!forceExpanded) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.rotate(rotationAngle),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Expanded content
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Stats
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BundleStatChip(
                            icon = Icons.Outlined.Info,
                            label = stringResource(R.string.patches),
                            value = patchCount.toString(),
                            modifier = Modifier.weight(1f),
                            onClick = onPatchesClick
                        )

                        BundleStatChip(
                            icon = Icons.Outlined.Update,
                            label = stringResource(R.string.version),
                            value = bundle.version?.removePrefix("v") ?: "N/A",
                            modifier = Modifier.weight(1f),
                            onClick = onVersionClick
                        )
                    }

                    // Dates
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        bundle.createdAt?.let { timestamp ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CalendarToday,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = stringResource(
                                        R.string.morphe_bundle_added_at,
                                        getRelativeTimeString(timestamp)
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        bundle.updatedAt?.let { timestamp ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Schedule,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = stringResource(
                                        R.string.bundle_updated_at,
                                        getRelativeTimeString(timestamp)
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Open in browser button (for remote bundles)
                    if (bundle is RemotePatchBundle) {
                        OutlinedButton(
                            onClick = onOpenInBrowser,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.morphe_home_open_in_browser),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }

                    // Update notification
                    if (updateInfo != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Text(
                                    text = stringResource(R.string.morphe_bundle_update_available),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Update button (for remote bundles)
                        if (bundle is RemotePatchBundle) {
                            BundleActionButton(
                                icon = Icons.Outlined.Refresh,
                                text = stringResource(R.string.update),
                                onClick = onUpdate,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Enable/Disable button
                        BundleActionButton(
                            icon = if (bundle.enabled) {
                                Icons.Outlined.Block
                            } else {
                                Icons.Outlined.CheckCircle
                            },
                            text = if (bundle.enabled) {
                                stringResource(R.string.disable)
                            } else {
                                stringResource(R.string.enable)
                            },
                            onClick = onDisable,
                            modifier = Modifier.weight(1f)
                        )

                        // Delete button (only for non-default bundles)
                        if (!bundle.isDefault) {
                            BundleActionButton(
                                icon = Icons.Outlined.Delete,
                                text = stringResource(R.string.delete),
                                onClick = onDelete,
                                modifier = Modifier.weight(1f),
                                isDestructive = true
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BundleStatChip(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun BundleActionButton(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDestructive: Boolean = false
) {
    val borderColor = if (isDestructive) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.outline
    }

    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = if (isDestructive) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            }
        ),
        border = BorderStroke(1.dp, borderColor),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium
        )
    }
}
