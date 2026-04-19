/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.morphe.manager.domain.bundles.APIPatchBundle
import app.morphe.manager.domain.bundles.JsonPatchBundle
import app.morphe.manager.domain.bundles.PatchBundleSource
import app.morphe.manager.domain.bundles.RemotePatchBundle
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.patcher.patch.PatchInfo
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.util.*
import kotlinx.coroutines.flow.mapNotNull
import org.koin.compose.koinInject

/**
 * Dialog for adding patch bundles.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AddSourceDialog(
    onDismiss: () -> Unit,
    onLocalSubmit: () -> Unit,
    onRemoteSubmit: (url: String) -> Unit,
    onLocalPick: () -> Unit,
    selectedLocalPath: String?
) {
    var remoteUrl by rememberSaveable { mutableStateOf("") }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) } // 0 = Remote, 1 = Local

    val isRemoteValid = remoteUrl.isNotBlank()
    val isLocalValid = selectedLocalPath != null

    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.sources_dialog_add_source),
        footer = {
            MorpheDialogButtonRow(
                primaryText = stringResource(R.string.add),
                onPrimaryClick = {
                    when (selectedTab) {
                        0 -> if (isRemoteValid) {
                            val normalizedUrl = normalizeUrl(remoteUrl)
                            onRemoteSubmit(normalizedUrl)
                        }
                        1 -> if (isLocalValid) onLocalSubmit()
                    }
                },
                primaryEnabled = if (selectedTab == 0) isRemoteValid else isLocalValid,
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Tabs
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(
                        stringResource(R.string.sources_dialog_remote),
                        stringResource(R.string.sources_dialog_local)
                    ).forEachIndexed { index, title ->
                        val isSelected = selectedTab == index

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clickable { selectedTab = index }
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected)
                                    MaterialTheme.colorScheme.primary
                                else
                                    Color.Transparent,
                                modifier = Modifier.fillMaxSize()
                            ) {}
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected)
                                        FontWeight.Bold
                                    else
                                        FontWeight.Normal,
                                    color = if (isSelected)
                                        MaterialTheme.colorScheme.onPrimary
                                    else
                                        LocalDialogTextColor.current
                                )
                            }
                        }
                    }
                }
            }

            // Tabs content
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    fadeIn(animationSpec = tween(200)).togetherWith(fadeOut(animationSpec = tween(200)))
                }
            ) { tab ->
                when (tab) {
                    0 -> RemoteTabContent(
                        remoteUrl = remoteUrl,
                        onUrlChange = { remoteUrl = it }
                    )
                    1 -> LocalTabContent(
                        selectedPath = selectedLocalPath,
                        onPickFile = onLocalPick
                    )
                }
            }
        }
    }
}

@Composable
private fun RemoteTabContent(
    remoteUrl: String,
    onUrlChange: (String) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // URL input
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MorpheDialogTextField(
                value = remoteUrl,
                onValueChange = onUrlChange,
                label = {
                    Text(stringResource(R.string.sources_dialog_remote_url))
                },
                placeholder = {
                    Text(text = "https://github.com/owner/repo")
                },
                showClearButton = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )
        }

        // Description
        InfoBadge(
            icon = Icons.Outlined.Info,
            text = stringResource(R.string.sources_dialog_remote_url_formats_title) +
                    stringResource(R.string.sources_dialog_remote_url_formats_list),
            style = InfoBadgeStyle.Default
        )
    }
}

@Composable
private fun LocalTabContent(
    selectedPath: String?,
    onPickFile: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // File picker button
        MorpheDialogButton(
            text = if (selectedPath == null) {
                stringResource(R.string.sources_dialog_local_file)
            } else {
                stringResource(R.string.sources_dialog_local_change_file)
            },
            onClick = onPickFile,
            icon = Icons.Outlined.FolderOpen,
            modifier = Modifier.fillMaxWidth()
        )

        // Selected file path
        if (selectedPath != null) {
            InfoBadge(
                icon = null,
                text = selectedPath.toUri().toFilePath(),
                style = InfoBadgeStyle.Default
            )
        }

        // Description
        InfoBadge(
            icon = Icons.Outlined.Info,
            text = stringResource(R.string.sources_dialog_local_file_description),
            style = InfoBadgeStyle.Success
        )
    }
}

@Composable
fun BundleDeleteConfirmDialog(
    bundle: PatchBundleSource,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.delete),
        footer = {
            MorpheDialogButtonRow(
                primaryText = stringResource(R.string.delete),
                onPrimaryClick = onConfirm,
                isPrimaryDestructive = true,
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        }
    ) {
        val secondaryColor = LocalDialogSecondaryTextColor.current

        Text(
            text = stringResource(
                R.string.sources_dialog_delete_confirm_message,
                bundle.displayTitle
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = secondaryColor,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Dialog for renaming a bundle.
 */
@Composable
fun RenameBundleDialog(
    initialValue: String,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var textValue by remember { mutableStateOf(initialValue) }
    val keyboardController = LocalSoftwareKeyboardController.current

    MorpheDialog(
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.sources_dialog_display_name),
        dismissOnClickOutside = false,
        footer = {
            MorpheDialogButtonRow(
                primaryText = stringResource(android.R.string.ok),
                onPrimaryClick = {
                    keyboardController?.hide()
                    onConfirm(textValue)
                },
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = {
                    keyboardController?.hide()
                    onDismissRequest()
                }
            )
        }
    ) {
        val secondaryColor = LocalDialogSecondaryTextColor.current

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.sources_dialog_rename),
                style = MaterialTheme.typography.bodyLarge,
                color = secondaryColor,
                textAlign = TextAlign.Center
            )

            MorpheDialogTextField(
                value = textValue,
                onValueChange = { textValue = it },
                placeholder = {
                    Text(
                        text = stringResource(R.string.patch_option_enter_value),
                        color = secondaryColor.copy(alpha = 0.5f)
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = null,
                        tint = secondaryColor
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        keyboardController?.hide()
                        onConfirm(textValue)
                    }
                )
            )
        }
    }
}

/**
 * Dialog displaying patches from a bundle with search field and chips.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BundlePatchesDialog(
    onDismissRequest: () -> Unit,
    src: PatchBundleSource
) {
    val patchBundleRepository: PatchBundleRepository = koinInject()
    val patches by remember(src.uid) {
        patchBundleRepository.bundleInfoFlow.mapNotNull { it[src.uid]?.patches }
    }.collectAsStateWithLifecycle(emptyList())

    var searchQuery by remember { mutableStateOf("") }
    var selectedPackages by remember { mutableStateOf(emptySet<String>()) }
    val showFilterSheet = remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val isLoading = patches.isEmpty()

    // packageName -> display label (displayName ?: packageName)
    val appLabels: Map<String, String> = remember(patches) {
        patches
            .flatMap { it.compatiblePackages.orEmpty() }
            .distinctBy { it.packageName }
            .mapNotNull { pkg ->
                val name = pkg.packageName ?: return@mapNotNull null
                name to (pkg.displayName ?: name)
            }
            .toMap()
    }

    val hasMultiplePackages = appLabels.size > 1

    val filteredPatches: List<PatchInfo> = remember(patches, searchQuery, selectedPackages) {
        patches
            .filter { patch ->
                val packageMatch = selectedPackages.isEmpty() ||
                        patch.compatiblePackages
                            ?.any { it.packageName in selectedPackages } == true
                val queryMatch = searchQuery.isBlank() ||
                        patch.name.contains(searchQuery, ignoreCase = true) ||
                        patch.description?.contains(searchQuery, ignoreCase = true) == true
                packageMatch && queryMatch
            }
            .sortedBy { it.name }
    }

    val isFiltering = searchQuery.isNotBlank() || selectedPackages.isNotEmpty()

    MorpheDialog(
        onDismissRequest = onDismissRequest,
        title = null,
        footer = {
            MorpheDialogButtonRow(
                primaryText = stringResource(android.R.string.ok),
                onPrimaryClick = onDismissRequest
            )
        },
        compactPadding = true,
        scrollable = false
    ) {
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Bundle header
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                modifier = Modifier.size(56.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Outlined.Extension,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = src.displayTitle,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = LocalDialogTextColor.current,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Widgets,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    val countText = if (isFiltering)
                                        "${filteredPatches.size}/${patches.size}"
                                    else
                                        "${patches.size}"
                                    val patchesLabel = stringResource(R.string.patches).lowercase()
                                    AnimatedContent(
                                        targetState = countText,
                                        transitionSpec = {
                                            (fadeIn(tween(200)) + slideInVertically(tween(200)) { -it / 2 })
                                                .togetherWith(fadeOut(tween(150)) + slideOutVertically(tween(150)) { it / 2 })
                                        },
                                        label = "patch_count"
                                    ) { count ->
                                        Text(
                                            text = "$count $patchesLabel",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Search + filter button row
                stickyHeader {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            MorpheDialogTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                label = { Text(stringResource(R.string.expert_mode_search)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Search,
                                        contentDescription = null
                                    )
                                },
                                showClearButton = true,
                                modifier = Modifier.weight(1f)
                            )

                            if (hasMultiplePackages) {
                                FilledTonalIconButton(
                                    onClick = { showFilterSheet.value = true },
                                    modifier = Modifier.padding(bottom = 4.dp),
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = if (selectedPackages.isNotEmpty())
                                            MaterialTheme.colorScheme.primaryContainer
                                        else
                                            MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = if (selectedPackages.isNotEmpty())
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.FilterList,
                                        contentDescription = stringResource(R.string.filter),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Active filter badges + empty state
                item(key = "filter_badges_and_empty_state") {
                    Column {
                        AnimatedVisibility(
                            visible = selectedPackages.isNotEmpty(),
                            enter = expandVertically(tween(MorpheDefaults.ANIMATION_DURATION)) + fadeIn(tween(MorpheDefaults.ANIMATION_DURATION)),
                            exit = shrinkVertically(tween(MorpheDefaults.ANIMATION_DURATION)) + fadeOut(tween(MorpheDefaults.ANIMATION_DURATION))
                        ) {
                            FlowRow(
                                modifier = Modifier.padding(bottom = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                selectedPackages.forEach { pkg ->
                                    val label = appLabels[pkg] ?: pkg
                                    InputChip(
                                        selected = true,
                                        onClick = { selectedPackages = selectedPackages - pkg },
                                        label = { Text(label) },
                                        trailingIcon = {
                                            Icon(
                                                imageVector = Icons.Outlined.Close,
                                                contentDescription = stringResource(R.string.remove),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    )
                                }
                            }
                        }

                        AnimatedVisibility(
                            visible = filteredPatches.isEmpty(),
                            enter = fadeIn(tween(MorpheDefaults.ANIMATION_DURATION)) + scaleIn(tween(MorpheDefaults.ANIMATION_DURATION), initialScale = 0.92f),
                            exit = fadeOut(tween(MorpheDefaults.ANIMATION_DURATION)) + scaleOut(tween(MorpheDefaults.ANIMATION_DURATION), targetScale = 0.92f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillParentMaxHeight(0.5f),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.SearchOff,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = stringResource(R.string.expert_mode_no_results),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }

                // Filtered patches list
                items(
                    filteredPatches,
                    key = { patch ->
                        patch.name + (patch.compatiblePackages?.joinToString { it.packageName.orEmpty() }.orEmpty())
                    }
                ) { patch ->
                    val context = LocalContext.current
                    PatchItemCard(
                        patch = patch,
                        saveStateKey = "bundle_${src.uid}",
                        onExpertBadgeClick = if (!patch.include) {
                            { context.toast(context.getString(R.string.sources_patch_expert_badge_tooltip)) }
                        } else null,
                        modifier = Modifier.animateItem(
                            fadeInSpec = tween(220),
                            fadeOutSpec = tween(180),
                            placementSpec = spring(stiffness = 400f, dampingRatio = 0.8f)
                        )
                    )
                }
            }
        }
    }

    // App filter bottom sheet
    if (showFilterSheet.value) {
        MorpheBottomSheet(
            onDismissRequest = { showFilterSheet.value = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = stringResource(R.string.filter),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(8.dp))

                LazyColumn(Modifier.padding(bottom = 16.dp)) {
                    item {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // "All" chip
                            FilterChip(
                                selected = selectedPackages.isEmpty(),
                                onClick = { selectedPackages = emptySet() },
                                label = { Text(stringResource(R.string.all)) },
                                leadingIcon = if (selectedPackages.isEmpty()) {
                                    { Icon(Icons.Outlined.DoneAll, null, Modifier.size(16.dp)) }
                                } else null
                            )
                            // Per-app chips
                            appLabels.entries
                                .sortedBy { it.value }
                                .forEach { (pkg, label) ->
                                    val isSelected = pkg in selectedPackages
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            selectedPackages = if (isSelected)
                                                selectedPackages - pkg
                                            else
                                                selectedPackages + pkg
                                        },
                                        label = { Text(label) },
                                        leadingIcon = if (isSelected) {
                                            { Icon(Icons.Outlined.Done, null, Modifier.size(16.dp)) }
                                        } else null
                                    )
                                }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Patch item card.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PatchItemCard(
    patch: PatchInfo,
    saveStateKey: String,
    onExpertBadgeClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val textColor = LocalDialogTextColor.current
    val secondaryColor = LocalDialogSecondaryTextColor.current

    var expandVersions by rememberSaveable(saveStateKey, patch.name, "versions") {
        mutableStateOf(false)
    }
    var expandOptions by rememberSaveable(saveStateKey, patch.name, "options") {
        mutableStateOf(false)
    }

    val rotationAngle by animateFloatAsState(
        targetValue = if (expandOptions) 180f else 0f,
        animationSpec = tween(300),
        label = "expand_rotation"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .then(
                if (!patch.options.isNullOrEmpty()) {
                    Modifier.clickable { expandOptions = !expandOptions }
                } else Modifier
            ),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = patch.name,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                if (!patch.options.isNullOrEmpty()) {
                    Icon(
                        imageVector = Icons.Outlined.ExpandMore,
                        contentDescription = if (expandOptions)
                            stringResource(R.string.collapse)
                        else
                            stringResource(R.string.expand),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(24.dp)
                            .rotate(rotationAngle)
                    )
                }
            }

            // Description
            patch.description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor
                )
            }

            // Compatibility info
            if (patch.compatiblePackages.isNullOrEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    InfoBadge(
                        text = stringResource(R.string.sources_dialog_view_any_package),
                        icon = Icons.Outlined.Apps,
                        style = InfoBadgeStyle.Default,
                        isCompact = true
                    )
                    InfoBadge(
                        text = stringResource(R.string.sources_dialog_view_any_version),
                        icon = Icons.Outlined.Code,
                        style = InfoBadgeStyle.Default,
                        isCompact = true
                    )
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    patch.compatiblePackages.forEach { compatiblePackage ->
                        val anyString = stringResource(R.string.any_version)
                        val appName = compatiblePackage.displayName ?: compatiblePackage.packageName ?: anyString
                        val versions = compatiblePackage.versions.orEmpty()

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            InfoBadge(
                                text = appName,
                                icon = Icons.Outlined.Apps,
                                style = InfoBadgeStyle.Primary,
                                isCompact = true,
                                modifier = Modifier.align(Alignment.CenterVertically)
                            )

                            if (versions.isNotEmpty()) {
                                if (expandVersions) {
                                    versions.forEach { version ->
                                        val isExperimental =
                                            compatiblePackage.experimentalVersions?.contains(version) == true
                                        InfoBadge(
                                            text = version,
                                            icon = if (isExperimental) Icons.Outlined.Science else Icons.Outlined.Code,
                                            style = if (isExperimental) InfoBadgeStyle.Warning else InfoBadgeStyle.Default,
                                            isCompact = true,
                                            modifier = Modifier.align(Alignment.CenterVertically)
                                        )
                                    }
                                } else {
                                    val firstVersion = versions.first()
                                    val firstIsExperimental =
                                        compatiblePackage.experimentalVersions?.contains(firstVersion) == true
                                    InfoBadge(
                                        text = firstVersion,
                                        icon = if (firstIsExperimental) Icons.Outlined.Science else Icons.Outlined.Code,
                                        style = if (firstIsExperimental) InfoBadgeStyle.Warning else InfoBadgeStyle.Default,
                                        isCompact = true,
                                        modifier = Modifier.align(Alignment.CenterVertically)
                                    )
                                }

                                if (versions.size > 1) {
                                    InfoBadge(
                                        text = if (expandVersions)
                                            stringResource(R.string.less)
                                        else
                                            "+${versions.size - 1}",
                                        style = InfoBadgeStyle.Default,
                                        isCompact = true,
                                        modifier = Modifier
                                            .align(Alignment.CenterVertically)
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable { expandVersions = !expandVersions }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Expert badge - shown only for patches that are disabled by default
            if (!patch.include && onExpertBadgeClick != null) {
                InfoBadge(
                    text = stringResource(R.string.sources_patch_expert_badge),
                    icon = Icons.Outlined.Lock,
                    style = InfoBadgeStyle.Warning,
                    isCompact = true,
                    modifier = Modifier.clickable(onClick = onExpertBadgeClick)
                )
            }

            // Options
            if (!patch.options.isNullOrEmpty()) {
                AnimatedVisibility(
                    visible = expandOptions,
                    enter = expandVertically(tween(MorpheDefaults.ANIMATION_DURATION)) + fadeIn(tween(MorpheDefaults.ANIMATION_DURATION)),
                    exit = shrinkVertically(tween(MorpheDefaults.ANIMATION_DURATION)) + fadeOut(tween(MorpheDefaults.ANIMATION_DURATION))
                ) {
                    Column(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        patch.options.forEach { option ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = option.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = option.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = secondaryColor
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Changelog dialog for a bundle.
 *
 * Prerelease channel: entries from the last stable release onwards.
 * Stable: entries newer than the installed version, plus the installed version itself.
 *
 * Fetched once and cached; cache invalidated on channel switch.
 * Falls back to GitHub Release info if CHANGELOG.md is unavailable.
 */
@Composable
fun BundleChangelogDialog(
    src: RemotePatchBundle,
    onDismissRequest: () -> Unit
) {
    var state: BundleChangelogState by remember { mutableStateOf(BundleChangelogState.Loading) }

    LaunchedEffect(Unit) {
        state = BundleChangelogState.Loading
        state = try {
            val usePrerelease = (src as? APIPatchBundle)?.usePrerelease == true
                    || (src as? JsonPatchBundle)?.usePrerelease == true

            val allEntries = src.fetchChangelogEntries(sinceVersion = null)

            val entries = if (usePrerelease) {
                // Prerelease: from the last stable release onwards
                val lastStable = allEntries.firstOrNull { !it.version.contains("-") }
                if (lastStable != null)
                    ChangelogParser.entriesNewerThan(allEntries, lastStable.version) + lastStable
                else allEntries
            } else {
                // Stable: from the installed version onwards
                val installed = src.installedVersionSignature
                val installedEntry = installed?.let {
                    ChangelogParser.findVersion(allEntries, it)
                }
                val newer = if (installed != null)
                    ChangelogParser.entriesNewerThan(allEntries, installed)
                else allEntries
                if (installedEntry != null) newer + installedEntry else newer
            }

            // APIPatchBundle has endpoint="api" - use SOURCE_REPO_URL directly
            val repoUrl = when (src) {
                is APIPatchBundle -> SOURCE_REPO_URL
                else -> RemotePatchBundle.inferPageUrlFromEndpoint(src.endpoint)
            }
            val latestPageUrl = entries.firstOrNull()?.version?.let { version ->
                val tag = if (version.startsWith("v")) version else "v$version"
                val url = repoUrl?.let { "$it/releases/tag/$tag" }
                url
            }

            if (entries.isNotEmpty()) {
                BundleChangelogState.Entries(entries, latestPageUrl = latestPageUrl)
            } else {
                // Fallback: CHANGELOG.md unavailable - use latest release info from API
                val asset = src.fetchLatestReleaseInfo()
                BundleChangelogState.Entries(
                    entries = listOf(
                        ChangelogEntry(
                            version = asset.version,
                            date = null,
                            content = asset.description.sanitizePatchChangelogMarkdown()
                        )
                    ),
                    latestPageUrl = asset.pageUrl
                )
            }
        } catch (t: Throwable) {
            BundleChangelogState.Error(t)
        }
    }

    MorpheDialog(
        onDismissRequest = onDismissRequest,
        title = when (state) {
            is BundleChangelogState.Entries -> null
            is BundleChangelogState.Error -> stringResource(R.string.changelog)
            BundleChangelogState.Loading -> stringResource(R.string.changelog)
        },
        footer = {
            when (val current = state) {
                is BundleChangelogState.Entries -> {
                    MorpheDialogButtonColumn {
                        current.latestPageUrl?.let { url ->
                            ChangelogButton(
                                pageUrl = url,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        MorpheDialogButton(
                            text = stringResource(android.R.string.ok),
                            onClick = onDismissRequest,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                is BundleChangelogState.Error -> {
                    MorpheDialogButtonColumn {
                        MorpheDialogButton(
                            text = stringResource(R.string.changelog_retry),
                            onClick = { state = BundleChangelogState.Loading },
                            modifier = Modifier.fillMaxWidth()
                        )
                        MorpheDialogButton(
                            text = stringResource(android.R.string.ok),
                            onClick = onDismissRequest,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                BundleChangelogState.Loading -> {
                    MorpheDialogButton(
                        text = stringResource(android.R.string.ok),
                        onClick = onDismissRequest,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    ) {
        when (val current = state) {
            BundleChangelogState.Loading -> ChangelogSectionLoading()
            is BundleChangelogState.Error -> BundleChangelogError(error = current.throwable)
            is BundleChangelogState.Entries -> ChangelogEntriesList(
                entries = current.entries,
                headerIcon = Icons.Outlined.History,
                emptyText = stringResource(R.string.changelog_empty),
                textColor = LocalDialogTextColor.current
            )
        }
    }
}

@Composable
private fun BundleChangelogError(
    error: Throwable
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Error icon with circular background
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                modifier = Modifier.size(80.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            // Error details
            Text(
                text = stringResource(
                    R.string.changelog_download_fail,
                    error.simpleMessage().orEmpty()
                ),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = LocalDialogTextColor.current
            )
        }
    }
}

private sealed interface BundleChangelogState {
    data object Loading : BundleChangelogState
    /** [entries] are already filtered to "missed" versions, newest-first. */
    data class Entries(
        val entries: List<ChangelogEntry>,
        val latestPageUrl: String?
    ) : BundleChangelogState
    data class Error(val throwable: Throwable) : BundleChangelogState
}

private val doubleBracketLinkRegex = Regex("""\[\[([^]]+)]\(([^)]+)\)]""")

private fun String.sanitizePatchChangelogMarkdown(): String =
    doubleBracketLinkRegex.replace(this) { match ->
        val label = match.groupValues[1]
        val link = match.groupValues[2]
        "[\\[$label\\]]($link)"
    }

/**
 * Normalizes a URL by adding https:// if no protocol is specified.
 */
private fun normalizeUrl(url: String): String {
    val trimmed = url.trim()

    return when {
        // Already has protocol
        trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) -> trimmed

        // Add https:// by default
        else -> "https://$trimmed"
    }
}
