/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import android.annotation.SuppressLint
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.morphe.manager.domain.bundles.APIPatchBundle
import app.morphe.manager.domain.bundles.JsonPatchBundle
import app.morphe.manager.domain.bundles.PatchBundleSource
import app.morphe.manager.domain.bundles.PatchBundleSource.Extensions.bundleAvatarUrl
import app.morphe.manager.domain.bundles.PatchBundleSource.Extensions.githubAvatarUrl
import app.morphe.manager.domain.bundles.PatchBundleSource.Extensions.isDefault
import app.morphe.manager.domain.bundles.RemotePatchBundle
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.util.RemoteAvatar
import app.morphe.manager.util.SOURCE_REPO_URL
import app.morphe.manager.util.getRelativeTimeString
import app.morphe.manager.util.toast
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Bottom sheet for managing patch bundles.
 */
@SuppressLint("LocalContextGetResourceValueCall")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BundleManagementSheet(
    onDismissRequest: () -> Unit,
    onAddSource: () -> Unit,
    onDelete: (PatchBundleSource) -> Unit,
    onDisable: (PatchBundleSource) -> Unit,
    onUpdate: (PatchBundleSource) -> Unit,
    onRename: (PatchBundleSource) -> Unit,
    onReorder: (List<Int>) -> Unit
) {
    val patchBundleRepository: PatchBundleRepository = koinInject()
    val prefs: PreferencesManager = koinInject()
    val scope = rememberCoroutineScope()

    val sources by patchBundleRepository.sources.collectAsStateWithLifecycle(emptyList())
    val patchCounts by patchBundleRepository.patchCountsFlow.collectAsStateWithLifecycle(emptyMap())
    val manualUpdateInfo by patchBundleRepository.manualUpdateInfo.collectAsStateWithLifecycle(emptyMap())
    val activeUpdateUids by patchBundleRepository.activeUpdateUidsFlow.collectAsStateWithLifecycle(emptySet())
    val experimentalVersionsEnabled by prefs.bundleExperimentalVersionsEnabled.getAsState()
    val bundleInfo by patchBundleRepository.bundleInfoFlow.collectAsStateWithLifecycle(emptyMap())

    val bundleToDelete = remember { mutableStateOf<PatchBundleSource?>(null) }
    // Expanded state lifted out of LazyColumn so it survives scroll-off-screen recomposition
    var expandedBundleUids by remember { mutableStateOf<Set<Int>>(emptySet()) }

    // Drag-and-drop state
    val listState = rememberLazyListState()
    var localOrder by remember { mutableStateOf(sources.map { it.uid }) }
    var isDragging by remember { mutableStateOf(false) }
    LaunchedEffect(sources) {
        if (isDragging) return@LaunchedEffect
        val sourceUids = sources.map { it.uid }
        val existing = localOrder.filter { uid -> uid in sourceUids }
        val added = sourceUids.filter { it !in existing }
        val merged = existing + added
        if (merged != localOrder) localOrder = merged
    }
    val orderedSources = remember(localOrder, sources) {
        localOrder.mapNotNull { uid -> sources.find { it.uid == uid } }
    }
    val haptic = LocalHapticFeedback.current
    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        val newOrder = localOrder.toMutableList()
        val moved = newOrder.removeAt(from.index)
        newOrder.add(to.index, moved)
        localOrder = newOrder
    }

    val bundleToShowPatches = remember { mutableStateOf<PatchBundleSource?>(null) }
    var bundleToShowChangelogUid by remember { mutableStateOf<Int?>(null) }
    val bundleToShowChangelog = bundleToShowChangelogUid
        ?.let { uid -> sources.filterIsInstance<RemotePatchBundle>().find { it.uid == uid } }
    val bundleToShowChangelogKey = bundleToShowChangelog?.let {
        val usePrerelease = (it as? APIPatchBundle)?.usePrerelease == true
                || (it as? JsonPatchBundle)?.usePrerelease == true
        "${it.installedVersionSignature}|$usePrerelease"
    }

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

    MorpheBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        val context = LocalContext.current
        val uriHandler = LocalUriHandler.current

        Column(Modifier.fillMaxWidth()) {
            // Header - outside scrollable area
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
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
                            text = pluralStringResource(
                                R.plurals.sources_management_subtitle,
                                sources.size,
                                sources.size
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    FilledIconButton(
                        onClick = onAddSource,
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

                Spacer(Modifier.height(8.dp))
            }

            // Bundle cards
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 16.dp
                )
            ) {
                items(orderedSources, key = { bundle -> bundle.uid }) { bundle ->
                    val hasExperimentalVersions = remember(bundle.uid, bundleInfo) {
                        bundleInfo[bundle.uid]?.patches?.any { patch ->
                            patch.compatiblePackages?.any { pkg ->
                                pkg.experimentalVersions?.isNotEmpty() == true
                            } == true
                        } == true
                    }
                    val useExperimentalVersions = bundle.uid.toString() in experimentalVersionsEnabled

                    ReorderableItem(reorderableState, key = bundle.uid) { itemIsDragging ->
                        BundleManagementCard(
                            bundle = bundle,
                            patchCount = patchCounts[bundle.uid] ?: 0,
                            updateInfo = manualUpdateInfo[bundle.uid],
                            isUpdating = bundle.uid in activeUpdateUids,
                            expanded = isSingleDefaultBundle || bundle.uid in expandedBundleUids,
                            onToggleExpanded = {
                                expandedBundleUids = if (bundle.uid in expandedBundleUids) {
                                    expandedBundleUids - bundle.uid
                                } else {
                                    expandedBundleUids + bundle.uid
                                }
                            },
                            onDelete = { bundleToDelete.value = bundle },
                            onDisable = { onDisable(bundle) },
                            onUpdate = { onUpdate(bundle) },
                            onRename = { onRename(bundle) },
                            onPrereleasesToggle = when {
                                bundle is JsonPatchBundle && bundle.supportsPrerelease ||
                                        bundle is APIPatchBundle -> { usePrerelease ->
                                    if (bundle.uid == bundleToShowChangelogUid) {
                                        bundleToShowChangelogUid = null
                                    }
                                    bundle.clearChangelogCache()
                                    scope.launch { patchBundleRepository.setUsePrerelease(bundle.uid, usePrerelease) }
                                }
                                else -> null
                            },
                            onExperimentalVersionsToggle = if (hasExperimentalVersions) {
                                { useExperimental ->
                                    scope.launch {
                                        patchBundleRepository.setUseExperimentalVersions(bundle.uid, useExperimental)
                                    }
                                }
                            } else null,
                            hasExperimentalVersions = hasExperimentalVersions,
                            useExperimentalVersions = useExperimentalVersions,
                            onPatchesClick = { bundleToShowPatches.value = bundle },
                            onVersionClick = {
                                if (bundle is RemotePatchBundle) {
                                    bundleToShowChangelogUid = bundle.uid
                                }
                            },
                            onOpenInBrowser = {
                                val pageUrl = manualUpdateInfo[bundle.uid]?.pageUrl
                                    ?: (bundle as? RemotePatchBundle)?.let { remote ->
                                        RemotePatchBundle.inferPageUrlFromEndpoint(remote.endpoint)
                                    }
                                    ?: SOURCE_REPO_URL
                                try {
                                    uriHandler.openUri(pageUrl)
                                } catch (_: Exception) {
                                    context.toast(context.getString(R.string.sources_management_failed_to_open_url))
                                }
                            },
                            forceExpanded = isSingleDefaultBundle,
                            isDragging = itemIsDragging,
                            longPressModifier = Modifier.longPressDraggableHandle(
                                onDragStarted = {
                                    isDragging = true
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDragStopped = {
                                    isDragging = false
                                    onReorder(localOrder)
                                }
                            ),
                            modifier = Modifier.zIndex(if (itemIsDragging) 1f else 0f)
                        )
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    if (bundleToDelete.value != null) {
        BundleDeleteConfirmDialog(
            bundle = bundleToDelete.value!!,
            onDismiss = { bundleToDelete.value = null },
            onConfirm = {
                onDelete(bundleToDelete.value!!)
                bundleToDelete.value = null
            }
        )
    }

    // Patches dialog
    if (bundleToShowPatches.value != null) {
        BundlePatchesDialog(
            onDismissRequest = { bundleToShowPatches.value = null },
            src = bundleToShowPatches.value!!
        )
    }

    // Changelog dialog
    if (bundleToShowChangelog != null) {
        key(bundleToShowChangelogKey) {
            BundleChangelogDialog(
                src = bundleToShowChangelog,
                onDismissRequest = { bundleToShowChangelogUid = null }
            )
        }
    }
}

/**
 * Card for individual bundle management.
 */
@Composable
private fun BundleManagementCard(
    bundle: PatchBundleSource,
    modifier: Modifier = Modifier,
    patchCount: Int,
    updateInfo: PatchBundleRepository.ManualBundleUpdateInfo?,
    isUpdating: Boolean = false,
    isDragging: Boolean = false,
    longPressModifier: Modifier = Modifier,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onDelete: () -> Unit,
    onDisable: () -> Unit,
    onUpdate: () -> Unit,
    onRename: () -> Unit,
    onPrereleasesToggle: ((Boolean) -> Unit)?,
    onExperimentalVersionsToggle: ((Boolean) -> Unit)?,
    hasExperimentalVersions: Boolean,
    useExperimentalVersions: Boolean,
    onPatchesClick: () -> Unit,
    onVersionClick: () -> Unit,
    onOpenInBrowser: () -> Unit,
    forceExpanded: Boolean = false
) {
    // Localized strings for accessibility
    val expandedState = stringResource(R.string.expanded)
    val collapsedState = stringResource(R.string.collapsed)
    val enabledState = stringResource(R.string.enabled)
    val disabledState = stringResource(R.string.disabled)
    val openInBrowser = stringResource(R.string.sources_management_open_in_browser)

    val isEnabled = bundle.enabled

    val animatedColor by animateColorAsState(
        targetValue = if (isEnabled) {
            MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
        } else {
            // Disabled state - use error container with low opacity
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
        },
        label = "bundle_card_color"
    )

    val animatedBorderColor by animateColorAsState(
        targetValue = if (!isEnabled) {
            MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        },
        label = "bundle_card_border_color"
    )

    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.03f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "bundle_card_scale"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(longPressModifier),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = if (isDragging) 8.dp else 3.dp,
        color = animatedColor,
        border = BorderStroke(1.dp, animatedBorderColor)
    ) {
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
                if (!forceExpanded) onToggleExpanded()
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
                enabled = isEnabled
            )

            // Expanded content
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(MorpheDefaults.ANIMATION_DURATION)),
                exit = shrinkVertically(tween(MorpheDefaults.ANIMATION_DURATION))
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
                        enabled = isEnabled && !isUpdating
                    )

                    // Version
                    BundleInfoCard(
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Outlined.Update,
                        title = stringResource(R.string.version),
                        value = bundle.version?.removePrefix("v") ?: "N/A",
                        onClick = onVersionClick,
                        enabled = !isUpdating
                    )

                    // Open in browser button
                    if (bundle is RemotePatchBundle) {
                        FilledTonalButton(
                            onClick = onOpenInBrowser,
                            modifier = Modifier
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

                    // Resolve prerelease state once
                    val currentUsePrerelease = when (bundle) {
                        is JsonPatchBundle -> bundle.usePrerelease
                        is APIPatchBundle -> bundle.usePrerelease
                        else -> false
                    }

                    // Prerelease toggle (for JsonPatchBundle with GitHub endpoint or APIPatchBundle)
                    if (onPrereleasesToggle != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPrereleasesToggle(!currentUsePrerelease) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.sources_management_prerelease_toggle),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = stringResource(R.string.sources_management_prerelease_toggle_description),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(Modifier.width(8.dp))

                            Switch(
                                checked = currentUsePrerelease,
                                onCheckedChange = onPrereleasesToggle
                            )
                        }
                    }

                    // Experimental versions toggle - shown for any bundle type that has experimental app version targets.
                    // For remote bundles (prerelease supported) it additionally requires prereleases to be ON.
                    AnimatedVisibility(
                        visible = hasExperimentalVersions && onExperimentalVersionsToggle != null &&
                                (onPrereleasesToggle == null || currentUsePrerelease),
                        enter = expandVertically(tween(MorpheDefaults.ANIMATION_DURATION)) + fadeIn(tween(MorpheDefaults.ANIMATION_DURATION)),
                        exit = shrinkVertically(tween(MorpheDefaults.ANIMATION_DURATION)) + fadeOut(tween(MorpheDefaults.ANIMATION_DURATION))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onExperimentalVersionsToggle?.invoke(!useExperimentalVersions)
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.sources_management_experimental_versions_toggle),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = stringResource(R.string.sources_management_experimental_versions_toggle_description),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(Modifier.width(8.dp))

                            Switch(
                                checked = useExperimentalVersions,
                                onCheckedChange = { onExperimentalVersionsToggle?.invoke(it) }
                            )
                        }
                    }

                    if (onPrereleasesToggle != null || (hasExperimentalVersions && onExperimentalVersionsToggle != null)) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }

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

                                val disableIcon = if (bundle.enabled)
                                    Icons.Outlined.Block
                                else
                                    Icons.Outlined.CheckCircle

                                Crossfade(
                                    targetState = disableIcon,
                                    label = "disable_icon"
                                ) { icon ->
                                    // Disable button
                                    ActionPillButton(
                                        onClick = onDisable,
                                        icon = icon,
                                        contentDescription = disableEnableDesc
                                    )
                                }
                            }

                            if (bundle is RemotePatchBundle) {
                                // Update button
                                ActionPillButton(
                                    onClick = onUpdate,
                                    icon = Icons.Outlined.Refresh,
                                    contentDescription = stringResource(R.string.update) + " " + bundle.displayTitle
                                )
                            }

                            if (!bundle.isDefault) {
                                // Rename button
                                ActionPillButton(
                                    onClick = onRename,
                                    icon = Icons.Outlined.Edit,
                                    contentDescription = stringResource(R.string.rename) + " " + bundle.displayTitle
                                )

                                // Delete button
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

        // Title + badges + rename button
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = bundle.displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            // Version • date
            // When showChevron=false (single bundle): show only date, no version.
            // When showChevron=true (multiple bundles): show version • date.
            val timestamp = bundle.updatedAt ?: bundle.createdAt
            val versionText = if (showChevron) bundle.version?.removePrefix("v") else null
            val dateText = timestamp?.let { getRelativeTimeString(it) }

            if (versionText != null || dateText != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (versionText != null) {
                        Text(
                            text = versionText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (versionText != null && dateText != null) {
                        Text(
                            text = "  •  ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (dateText != null) {
                        Icon(
                            imageVector = if (bundle.updatedAt != null) Icons.Outlined.Schedule else Icons.Outlined.CalendarToday,
                            contentDescription = null,
                            modifier = Modifier.size(11.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = dateText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(Modifier.height(2.dp))

            FlowRow(
                modifier = Modifier.animateContentSize(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Bundle type badge
                BundleTypeBadge(bundle)

                // Disabled badge
                AnimatedVisibility(
                    visible = !enabled,
                    enter = fadeIn(tween(MorpheDefaults.ANIMATION_DURATION)) + expandHorizontally(tween(MorpheDefaults.ANIMATION_DURATION)),
                    exit = fadeOut(tween(MorpheDefaults.ANIMATION_DURATION)) + shrinkHorizontally(tween(MorpheDefaults.ANIMATION_DURATION))
                ) {
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
        color = if (enabled) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
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

            if (showChevron && enabled) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Outlined.TouchApp,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                    )
                    Text(
                        text = stringResource(R.string.details),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                    )
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
    val bundleAvatarUrl = bundle.bundleAvatarUrl
    val githubAvatarUrl = bundle.githubAvatarUrl

    val animatedColor by animateColorAsState(
        targetValue = when {
            bundle.isDefault -> Color.White
            enabled -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        label = "bundle_icon_color"
    )

    val animatedAlpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.5f,
        label = "bundle_icon_alpha"
    )

    Surface(
        modifier = modifier.graphicsLayer { alpha = animatedAlpha },
        shape = CircleShape,
        color = animatedColor
    ) {
        when {
            bundle.isDefault -> {
                val context = LocalContext.current
                Image(
                    painter = rememberDrawablePainter(
                        drawable = AppCompatResources.getDrawable(context, R.drawable.ic_launcher_foreground)
                    ),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = modifier
                        .graphicsLayer {
                            scaleX = 1.5f
                            scaleY = 1.5f
                        }
                )
            }

            bundleAvatarUrl != null || githubAvatarUrl != null -> {
                RemoteAvatar(
                    url = bundleAvatarUrl ?: githubAvatarUrl!!,
                    fallbackUrl = if (bundleAvatarUrl != null) githubAvatarUrl else null,
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
