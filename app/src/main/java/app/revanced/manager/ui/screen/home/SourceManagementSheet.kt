package app.revanced.manager.ui.screen.home

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.revanced.manager.domain.bundles.PatchBundleSource
import app.revanced.manager.domain.bundles.PatchBundleSource.Extensions.githubAvatarUrl
import app.revanced.manager.domain.bundles.PatchBundleSource.Extensions.isDefault
import app.revanced.manager.domain.bundles.RemotePatchBundle
import app.revanced.manager.domain.repository.PatchBundleRepository
import app.revanced.manager.ui.screen.shared.ActionPillButton
import app.revanced.manager.ui.screen.shared.InfoBadge
import app.revanced.manager.ui.screen.shared.InfoBadgeStyle
import app.revanced.manager.util.BUNDLE_URL_RELEASES
import app.revanced.manager.util.getRelativeTimeString
import app.revanced.manager.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import java.net.URL

/**
 * Bottom sheet for managing patch bundles
 */
@SuppressLint("LocalContextGetResourceValueCall")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BundleManagementSheet(
    onDismissRequest: () -> Unit,
    onAddBundle: () -> Unit,
    onDelete: (PatchBundleSource) -> Unit,
    onDisable: (PatchBundleSource) -> Unit,
    onUpdate: (PatchBundleSource) -> Unit,
    onRename: (PatchBundleSource) -> Unit
) {
    val patchBundleRepository: PatchBundleRepository = koinInject()

    val sources by patchBundleRepository.sources.collectAsStateWithLifecycle(emptyList())
    val patchCounts by patchBundleRepository.patchCountsFlow.collectAsStateWithLifecycle(emptyMap())
    val manualUpdateInfo by patchBundleRepository.manualUpdateInfo.collectAsStateWithLifecycle(emptyMap())

    var bundleToDelete by remember { mutableStateOf<PatchBundleSource?>(null) }
    var bundleToShowPatches by remember { mutableStateOf<PatchBundleSource?>(null) }
    var bundleToShowChangelog by remember { mutableStateOf<RemotePatchBundle?>(null) }

    // Check if only default bundle exists
    val isSingleDefaultBundle = sources.size == 1

    // Auto-enable the default bundle if it's the only one and disabled
    LaunchedEffect(sources) {
        if (sources.size == 1) {
            val singleBundle = sources.first()
            if (singleBundle.isDefault && !singleBundle.enabled) {
                onDisable(singleBundle) // This will toggle it to enabled
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
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
        ) {
            // Header - outside scrollable area
            Column(
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.sources_management_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(
                                R.string.sources_management_subtitle,
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
            }

            // Bundle cards - scrollable area with disabled overscroll
            CompositionLocalProvider(LocalOverscrollFactory provides null) {
                LazyColumn(
                    state = rememberLazyListState(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = 16.dp
                    )
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
                            onPatchesClick = { bundleToShowPatches = bundle },
                            onVersionClick = {
                                if (bundle is RemotePatchBundle) {
                                    bundleToShowChangelog = bundle
                                }
                            },
                            onOpenInBrowser = {
                                val pageUrl = manualUpdateInfo[bundle.uid]?.pageUrl
                                    ?: BUNDLE_URL_RELEASES
                                try {
                                    uriHandler.openUri(pageUrl)
                                } catch (_: Exception) {
                                    context.toast(context.getString(R.string.sources_management_failed_to_open_url))
                                }
                            },
                            forceExpanded = isSingleDefaultBundle
                        )
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    if (bundleToDelete != null) {
        BundleDeleteConfirmDialog(
            bundle = bundleToDelete!!,
            onDismiss = { bundleToDelete = null },
            onConfirm = {
                onDelete(bundleToDelete!!)
                bundleToDelete = null
            }
        )
    }

    // Patches dialog
    if (bundleToShowPatches != null) {
        BundlePatchesDialog(
            onDismissRequest = { bundleToShowPatches = null },
            src = bundleToShowPatches!!
        )
    }

    // Changelog dialog
    if (bundleToShowChangelog != null) {
        BundleChangelogDialog(
            src = bundleToShowChangelog!!,
            onDismissRequest = { bundleToShowChangelog = null }
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

    // Update expanded state when forceExpanded changes
    LaunchedEffect(forceExpanded) {
        if (forceExpanded) expanded = true
    }

    // Localized strings for accessibility
    val expandedState = stringResource(R.string.expanded)
    val collapsedState = stringResource(R.string.collapsed)
    val enabledState = stringResource(R.string.enabled)
    val disabledState = stringResource(R.string.disabled)
    val openInBrowser = stringResource(R.string.sources_management_open_in_browser)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
    ) {
        val isEnabled = bundle.enabled

        // Build content description
        val contentDesc = buildString {
            append(bundle.displayTitle)
            append(", ")
            if (isEnabled) {
                append(enabledState)
            } else {
                append(disabledState)
            }
            if (!forceExpanded) {
                append(", ")
                append(if (expanded) expandedState else collapsedState)
            }
            updateInfo?.let {
                append(", ")
                append(stringResource(R.string.update))
                append(" ")
                append(stringResource(R.string.available))
            }
        }

        Column(modifier = Modifier
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                if (!forceExpanded) expanded = !expanded
            }
            .semantics {
                if (!forceExpanded) {
                    role = Role.Button
                    stateDescription = if (expanded) expandedState else collapsedState
                }
                this.contentDescription = contentDesc
            }
            .padding(16.dp)) {
            // Header
            BundleCardHeader(
                bundle = bundle,
                updateInfo = updateInfo,
                expanded = expanded,
                showChevron = !forceExpanded,
                onRename = onRename,
                enabled = isEnabled
            )

            // Expanded content
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Patches
                    BundleInfoCard(
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Outlined.Info,
                        title = stringResource(R.string.patches),
                        value = patchCount.toString(),
                        onClick = onPatchesClick,
                        enabled = isEnabled
                    )

                    // Version
                    BundleInfoCard(
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Outlined.Update,
                        title = stringResource(R.string.version),
                        value = bundle.version?.removePrefix("v") ?: "N/A",
                        onClick = onVersionClick,
                        enabled = isEnabled
                    )

                    // Metadata section
                    if (bundle.createdAt != null || bundle.updatedAt != null) {
                        BundleMetaCard(
                            createdAt = bundle.createdAt,
                            updatedAt = bundle.updatedAt
                        )
                    }

                    // Open in browser button
                    if (bundle is RemotePatchBundle) {
                        FilledTonalButton(
                            onClick = onOpenInBrowser,
                            enabled = isEnabled,
                            modifier = Modifier
                                .alpha(if (isEnabled) 1f else 0.5f)
                                .fillMaxWidth()
                                .height(48.dp)
                                .semantics {
                                    contentDescription = openInBrowser
                                },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Outlined.OpenInNew,
                                contentDescription = null
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(openInBrowser)
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Action bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (!forceExpanded) {
                                val disableEnableDesc = if (bundle.enabled) {
                                    stringResource(R.string.disable) + " " + bundle.displayTitle
                                } else {
                                    stringResource(R.string.enable) + " " + bundle.displayTitle
                                }

                                ActionPillButton(
                                    onClick = onDisable,
                                    icon = if (bundle.enabled)
                                        Icons.Outlined.Block
                                    else
                                        Icons.Outlined.CheckCircle,
                                    contentDescription = disableEnableDesc
                                )
                            }

                            if (bundle is RemotePatchBundle) {
                                ActionPillButton(
                                    onClick = onUpdate,
                                    icon = Icons.Outlined.Refresh,
                                    contentDescription = stringResource(R.string.update) + " " + bundle.displayTitle
                                )
                            }

                            if (!bundle.isDefault) {
                                ActionPillButton(
                                    onClick = onDelete,
                                    icon = Icons.Outlined.Delete,
                                    contentDescription = stringResource(R.string.delete) + " " + bundle.displayTitle,
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BundleCardHeader(
    bundle: PatchBundleSource,
    updateInfo: PatchBundleRepository.ManualBundleUpdateInfo?,
    expanded: Boolean,
    showChevron: Boolean,
    onRename: () -> Unit,
    enabled: Boolean = true
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "expand_chevron"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Bundle icon with GitHub avatar support
        BundleIcon(
            bundle = bundle,
            enabled = enabled,
            modifier = Modifier.size(44.dp)
        )

        // Title + badges
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = bundle.displayTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Version
            if (showChevron) {
                bundle.version?.let { version ->
                    Text(
                        text = version.removePrefix("v"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(2.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Bundle type badge
                BundleTypeBadge(bundle)

                // Disabled badge
                if (!enabled) {
                    InfoBadge(
                        text = stringResource(R.string.disabled),
                        style = InfoBadgeStyle.Error,
                        icon = null,
                        isCompact = true
                    )
                }

                // Update badge
                if (updateInfo != null) {
                    InfoBadge(
                        text = stringResource(R.string.update),
                        style = InfoBadgeStyle.Warning,
                        icon = null,
                        isCompact = true
                    )
                }
            }
        }

        // Rename button (only for non-default bundles)
        if (!bundle.isDefault) {
            IconButton(onClick = onRename) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = stringResource(R.string.rename),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Chevron
        if (showChevron) {
            Icon(
                imageVector = Icons.Outlined.ExpandMore,
                contentDescription = null,
                modifier = Modifier.rotate(rotation),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BundleInfoCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit,
    showChevron: Boolean = true,
    enabled: Boolean = true
) {
    val contentDesc = "$title: $value"

    Surface(
        modifier = modifier.semantics {
            contentDescription = contentDesc
            role = Role.Button
        },
        shape = RoundedCornerShape(12.dp),
        color = if (enabled) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        onClick = onClick,
        enabled = enabled
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                    maxLines = 1,
                )
                if (value.isNotEmpty()) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                    )
                }
            }

            if (showChevron) {
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
private fun BundleMetaCard(
    modifier: Modifier = Modifier,
    createdAt: Long? = null,
    updatedAt: Long? = null
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Added
            createdAt?.let { timestamp ->
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CalendarToday,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.sources_management_date_added),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Text(
                            text = getRelativeTimeString(timestamp),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Updated
            updatedAt?.let { timestamp ->
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.sources_management_date_updated),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Text(
                            text = getRelativeTimeString(timestamp),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BundleTypeBadge(bundle: PatchBundleSource) {
    val text = when {
        bundle.isDefault -> stringResource(R.string.sources_dialog_preinstalled)
        bundle is RemotePatchBundle -> stringResource(R.string.sources_dialog_remote)
        else -> stringResource(R.string.sources_dialog_local)
    }

    InfoBadge(
        text = text,
        isCompact = true
    )
}

@Composable
fun BundleIcon(
    bundle: PatchBundleSource,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val githubAvatarUrl = bundle.githubAvatarUrl

    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = when {
            bundle.isDefault -> Color.White
            enabled -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        }
    ) {
        when {
            bundle.isDefault -> {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = modifier
                        .graphicsLayer {
                            scaleX = 1.5f
                            scaleY = 1.5f
                        }
                )
            }

            githubAvatarUrl != null -> {
                RemoteAvatar(
                    url = githubAvatarUrl,
                    modifier = Modifier.fillMaxSize()
                )
            }

            else -> {
                Icon(
                    imageVector = Icons.Outlined.Source,
                    contentDescription = null,
                    tint = if (enabled)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }
    }
}

@Composable
private fun RemoteAvatar(
    url: String,
    modifier: Modifier = Modifier
) {
    var bitmap by remember(url) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(url) {
        bitmap = loadGitHubAvatar(url)
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    }
}

/**
 * Load GitHub avatar image from URL
 */
private suspend fun loadGitHubAvatar(url: String): Bitmap? = withContext(Dispatchers.IO) {
    try {
        val connection = URL(url).openConnection()
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        connection.connect()

        connection.getInputStream().use { input ->
            BitmapFactory.decodeStream(input)
        }
    } catch (_: Exception) {
        null
    }
}
