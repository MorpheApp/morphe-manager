/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.morphe.manager.domain.bundles.APIPatchBundle
import app.morphe.manager.domain.bundles.PatchBundleSource
import app.morphe.manager.domain.bundles.PatchBundleSource.Extensions.usesPrerelease
import app.morphe.manager.domain.bundles.RemotePatchBundle
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.patcher.patch.PatchInfo
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.util.*
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Brands
import compose.icons.fontawesomeicons.brands.Github
import compose.icons.fontawesomeicons.brands.Gitlab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import com.mikepenz.markdown.model.State as MarkdownRenderState

private val ColorValid = Color(0xFF4CAF50)

/**
 * Dialog for adding patch bundles.
 */
@Composable
fun AddSourceDialog(
    onDismiss: () -> Unit,
    onLocalSubmit: () -> Unit,
    onRemoteSubmit: (url: String) -> Unit,
    onLocalPick: () -> Unit,
    selectedLocalPath: String?,
    selectedLocalUri: Uri?,
    onValidateUrl: (String) -> Boolean
) {
    var remoteUrl by rememberSaveable { mutableStateOf("") }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) } // 0 = Remote, 1 = Local

    val urlValidation = rememberUrlValidation(remoteUrl, onValidateUrl)
    val isRemoteValid = remoteUrl.isNotBlank() && urlValidation != FieldValidation.Invalid
    val localFileValidation = rememberLocalFileValidation(selectedLocalPath)
    val isLocalValid = localFileValidation == FieldValidation.Valid

    AppDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.sources_dialog_add_source),
        footer = {
            AppDialogButtonRow(
                primaryText = stringResource(R.string.add),
                onPrimaryClick = {
                    when (selectedTab) {
                        0 -> if (isRemoteValid) onRemoteSubmit(normalizeUrl(remoteUrl))
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
            verticalArrangement = Arrangement.spacedBy(Defaults.ContentPadding)
        ) {
            // Type selector cards
            CardSelectorRow(
                options = listOf(
                    CardSelectorOption(
                        label = stringResource(R.string.sources_dialog_remote),
                        icon = Icons.Outlined.Language
                    ),
                    CardSelectorOption(
                        label = stringResource(R.string.sources_dialog_local),
                        icon = Icons.AutoMirrored.Outlined.InsertDriveFile
                    )
                ),
                selectedIndex = selectedTab,
                onSelect = { selectedTab = it }
            )

            // Tab content
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = Animations.fadeCrossfade()
            ) { tab ->
                when (tab) {
                    0 -> RemoteTabContent(
                        remoteUrl = remoteUrl,
                        onUrlChange = { remoteUrl = it },
                        urlValidation = urlValidation
                    )
                    1 -> LocalTabContent(
                        selectedPath = selectedLocalPath,
                        selectedUri = selectedLocalUri,
                        onPickFile = onLocalPick,
                        validation = localFileValidation
                    )
                }
            }
        }
    }
}

private enum class FieldValidation { Empty, Valid, Invalid }

@Composable
private fun rememberUrlValidation(url: String, validate: (String) -> Boolean): FieldValidation =
    remember(url) {
        when {
            url.isBlank() -> FieldValidation.Empty
            validate(normalizeUrl(url)) -> FieldValidation.Valid
            else -> FieldValidation.Invalid
        }
    }

@Composable
private fun UrlFormatRow(
    icon: ImageVector,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp)
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
    }
}

@Composable
private fun RemoteTabContent(
    remoteUrl: String,
    onUrlChange: (String) -> Unit,
    urlValidation: FieldValidation,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppDialogTextField(
            value = remoteUrl,
            onValueChange = onUrlChange,
            label = { Text(stringResource(R.string.sources_dialog_remote_url)) },
            placeholder = { Text("https://github.com/owner/repo") },
            showClearButton = true,
            isError = urlValidation == FieldValidation.Invalid,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done
            )
        )

        // Live validation feedback
        AnimatedVisibility(visible = urlValidation != FieldValidation.Empty) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val (icon, color, text) = when (urlValidation) {
                    FieldValidation.Valid -> Triple(
                        Icons.Outlined.CheckCircle,
                        ColorValid,
                        stringResource(R.string.sources_dialog_url_valid)
                    )
                    FieldValidation.Invalid -> Triple(
                        Icons.Outlined.ErrorOutline,
                        MaterialTheme.colorScheme.error,
                        stringResource(R.string.sources_dialog_url_invalid)
                    )
                    FieldValidation.Empty -> Triple(Icons.Outlined.Info, Color.Transparent, "")
                }
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(15.dp))
                Text(text, style = MaterialTheme.typography.bodySmall, color = color)
            }
        }

        // URL format hint
        StatusBadge(
            icon = Icons.Outlined.Info,
            text = stringResource(R.string.sources_dialog_remote_url_formats_title),
            tone = SemanticTone.Neutral
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            UrlFormatRow(
                icon = FontAwesomeIcons.Brands.Github,
                text = "github.com/owner/repo"
            )
            UrlFormatRow(
                icon = FontAwesomeIcons.Brands.Gitlab,
                text = "gitlab.com/owner/repo"
            )
            UrlFormatRow(
                icon = Icons.Outlined.Link,
                text = "example.com/patches-bundle.json"
            )
        }
    }
}

@Composable
private fun rememberLocalFileValidation(path: String?): FieldValidation = remember(path) {
    if (path == null) return@remember FieldValidation.Empty
    if (!path.endsWith(".mpp", ignoreCase = true)) FieldValidation.Invalid
    else FieldValidation.Valid
}

@Composable
private fun LocalTabContent(
    selectedPath: String?,
    selectedUri: Uri?,
    onPickFile: () -> Unit,
    validation: FieldValidation
) {
    val textColor = LocalDialogTextColor.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (selectedPath == null) {
            // Drop zone
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPickFile() },
                shape = RoundedCornerShape(16.dp),
                color = Color.Transparent,
                border = BorderStroke(
                    1.dp, textColor.copy(alpha = 0.15f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Upload,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = textColor.copy(alpha = 0.4f)
                    )
                    Text(
                        text = stringResource(R.string.sources_dialog_local_file),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = textColor
                    )
                    Text(
                        text = stringResource(R.string.sources_dialog_local_file_tap_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor.copy(alpha = 0.4f)
                    )
                }
            }
        } else {
            // Selected file
            val isValid = validation == FieldValidation.Valid
            Surface(
                shape = RoundedCornerShape(Defaults.CompactCornerRadius),
                color = if (isValid)
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                else
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = if (isValid) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        modifier = Modifier.size(Defaults.IconSizeSmall),
                        tint = if (isValid) ColorValid else MaterialTheme.colorScheme.error
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selectedPath,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = textColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = when (validation) {
                                FieldValidation.Invalid -> stringResource(R.string.sources_dialog_local_invalid_extension)
                                else -> selectedUri?.toFilePath()?.takeIf { it.startsWith("/") }?.substringBeforeLast("/") ?: ""
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isValid) textColor.copy(alpha = 0.5f) else MaterialTheme.colorScheme.error,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = onPickFile,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = stringResource(R.string.sources_dialog_local_change_file),
                            modifier = Modifier.size(24.dp),
                            tint = textColor.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }

        // Description
        UrlFormatRow(
            icon = Icons.Outlined.Info,
            text = stringResource(R.string.sources_dialog_local_file_description)
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

    AppDialog(
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.sources_dialog_display_name),
        dismissOnClickOutside = false,
        footer = {
            AppDialogButtonRow(
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
            verticalArrangement = Arrangement.spacedBy(Defaults.ContentPadding)
        ) {
            Text(
                text = stringResource(R.string.sources_dialog_rename),
                style = MaterialTheme.typography.bodyLarge,
                color = secondaryColor,
                textAlign = TextAlign.Center
            )

            AppDialogTextField(
                value = textValue,
                onValueChange = { textValue = it },
                placeholder = {
                    Text(
                        text = stringResource(R.string.patch_option_enter_value),
                        color = secondaryColor.copy(alpha = 0.5f)
                    )
                },
                leadingIcon = {
                    ThemedIcon(
                        icon = Icons.Outlined.Edit,
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

    // Carries each patch's position in the unfiltered list: a bundle may declare several patches
    // under one name and compatibility, so nothing derived from the patch itself is a unique key
    val filteredPatches: List<IndexedValue<PatchInfo>> = remember(patches, searchQuery, selectedPackages) {
        patches.withIndex()
            .filter { (_, patch) ->
                val packageMatch = selectedPackages.isEmpty() ||
                        patch.compatiblePackages
                            ?.any { it.packageName in selectedPackages } == true
                val queryMatch = searchQuery.isBlank() ||
                        patch.displayName.contains(searchQuery, ignoreCase = true) ||
                        patch.description?.contains(searchQuery, ignoreCase = true) == true
                packageMatch && queryMatch
            }
            .sortedBy { (_, patch) -> patch.displayName }
    }

    // Per-patch accent color: first non-null appIconColor across all compatible packages,
    // converted from 0xRRGGBB to a full-opacity Compose Color. Null falls back to surfaceVariant.
    val patchAccentColors: Map<String, Color> = remember(patches) {
        patches.associate { patch ->
            val rgb = patch.compatiblePackages
                ?.firstNotNullOfOrNull { it.appIconColor }
            patch.name to if (rgb != null) Color(rgb or (0xFF shl 24)) else Color.Unspecified
        }
    }

    val isFiltering = searchQuery.isNotBlank() || selectedPackages.isNotEmpty()

    AppDialog(
        onDismissRequest = {
            when {
                searchQuery.isNotBlank() -> searchQuery = ""
                selectedPackages.isNotEmpty() -> selectedPackages = emptySet()
                else -> onDismissRequest()
            }
        },
        title = null,
        footer = {
            AppDialogOutlinedButton(
                text = stringResource(R.string.close),
                onClick = onDismissRequest,
                modifier = Modifier.fillMaxWidth()
            )
        },
        padding = DialogPadding.Compact,
        scrollable = false,
        contentArrangement = Arrangement.Top
    ) {
        AnimatedContent(
            targetState = isLoading,
            transitionSpec = Animations.fadeCrossfade(),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            label = "bundlePatches"
        ) { loading ->
            if (loading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    PulsingLogoIndicator()
                }

                return@AnimatedContent
            }

            val listState = rememberLazyListState()
            var displayedPackages by remember { mutableStateOf(emptySet<String>()) }
            LaunchedEffect(selectedPackages) {
                if (selectedPackages.isNotEmpty()) displayedPackages = selectedPackages
            }

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(Defaults.ItemSpacing)
            ) {
                PatchesListSearchRow(
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    showFilterButton = hasMultiplePackages,
                    isFilterActive = selectedPackages.isNotEmpty(),
                    onFilterClick = { showFilterSheet.value = true }
                )

                AnimatedVisibility(
                    visible = selectedPackages.isNotEmpty(),
                    enter = Animations.expandFadeEnter,
                    exit = Animations.shrinkFadeExit
                ) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        displayedPackages.forEach { pkg ->
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

                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(Defaults.ItemSpacing)
                    ) {
                        // Bundle header
                        item {
                            PatchesListHeaderCard(
                                title = src.displayTitle,
                                totalCount = patches.size,
                                filteredCount = filteredPatches.size,
                                isFiltering = isFiltering
                            )
                        }

                        if (filteredPatches.isEmpty()) {
                            item(key = "empty_state") {
                                PatchesListEmptyState(
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }

                        // Filtered patches list
                        items(
                            filteredPatches,
                            key = { (index, _) -> index }
                        ) { (_, patch) ->
                            val context = LocalContext.current
                            val expertBadgeTooltip = stringResource(R.string.sources_patch_expert_badge_tooltip)
                            val accentColor = patchAccentColors[patch.name]
                                ?.takeIf { it != Color.Unspecified }
                            PatchItemCard(
                                patch = patch,
                                saveStateKey = "bundle_${src.uid}",
                                onExpertBadgeClick = if (!patch.include) {
                                    { context.toast(expertBadgeTooltip) }
                                } else null,
                                accentColor = accentColor,
                                modifier = Modifier.animateItem(
                                    fadeInSpec = tween(Defaults.ANIMATION_DURATION),
                                    fadeOutSpec = tween(Defaults.ANIMATION_DURATION_SHORT),
                                    placementSpec = spring(stiffness = 400f, dampingRatio = 0.8f)
                                )
                            )
                        }
                    }

                    ListScrollbar(
                        listState = listState,
                        modifier = Modifier.offset(x = LocalDialogHorizontalInset.current)
                    )

                    ScrollToTopButton(
                        listState = listState,
                        modifier = Modifier.offset(x = LocalDialogHorizontalInset.current)
                    )
                }
            }
        }
    }

    // App filter bottom sheet
    if (showFilterSheet.value) {
        AppBottomSheet(
            onDismissRequest = { showFilterSheet.value = false }
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
                            AppFilterChip(
                                selected = selectedPackages.isEmpty(),
                                onClick = { selectedPackages = emptySet() },
                                label = stringResource(R.string.all),
                                selectedIcon = Icons.Outlined.DoneAll
                            )
                            // Per-app chips
                            appLabels.entries
                                .sortedBy { it.value }
                                .forEach { (pkg, label) ->
                                    val isSelected = pkg in selectedPackages
                                    AppFilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            selectedPackages = if (isSelected)
                                                selectedPackages - pkg
                                            else
                                                selectedPackages + pkg
                                        },
                                        label = label
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
@Composable
fun PatchItemCard(
    modifier: Modifier = Modifier,
    patch: PatchInfo,
    saveStateKey: String,
    onExpertBadgeClick: (() -> Unit)? = null,
    accentColor: Color? = null
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
        animationSpec = tween(Defaults.ANIMATION_DURATION),
        label = "expand_rotation"
    )

    val cardColor = rememberAccentCardColor(accentColor)

    val effectiveCardColor = cardColor ?: MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    // Card colors come from the app's own icon, so no fixed badge fill can be counted on to show
    val cardBackground = effectiveCardColor.compositeOver(MaterialTheme.colorScheme.background)

    SettingsItemCard(
        onClick = if (!patch.options.isNullOrEmpty()) {
            { expandOptions = !expandOptions }
        } else null,
        modifier = modifier,
        borderWidth = 1.dp,
        color = effectiveCardColor
    ) {
        CompositionLocalProvider(LocalCardBackground provides cardBackground) {
            Column(
                modifier = Modifier.padding(Defaults.ContentPadding),
                verticalArrangement = Arrangement.spacedBy(Defaults.ItemSpacing),
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = patch.displayName,
                        color = textColor,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )

                    if (!patch.options.isNullOrEmpty()) {
                        ThemedIcon(
                            icon = Icons.Outlined.ExpandMore,
                            contentDescription = if (expandOptions)
                                stringResource(R.string.collapse)
                            else
                                stringResource(R.string.expand),
                            tint = secondaryColor,
                            modifier = Modifier.rotate(rotationAngle)
                        )
                    }
                }

                // Description
                patch.description?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondaryColor
                    )
                }

                // Compatibility info
                if (patch.isUniversal) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusBadge(
                            text = stringResource(R.string.sources_dialog_view_any_package),
                            icon = Icons.Outlined.Apps
                        )
                        StatusBadge(
                            text = stringResource(R.string.sources_dialog_view_any_version),
                            icon = Icons.Outlined.Code
                        )
                    }
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        patch.compatiblePackages.orEmpty().forEach { compatiblePackage ->
                            val anyString = stringResource(R.string.any_version)
                            val appName = compatiblePackage.displayName ?: compatiblePackage.packageName ?: anyString
                            val versions = compatiblePackage.versions.orEmpty()

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                StatusBadge(
                                    text = appName,
                                    icon = Icons.Outlined.Apps,
                                    tone = SemanticTone.Primary,
                                    modifier = Modifier.align(Alignment.CenterVertically)
                                )

                                if (versions.isNotEmpty()) {
                                    val shownVersions =
                                        if (expandVersions) versions else versions.take(1)
                                    shownVersions.forEach { version ->
                                        PatchVersionBadge(
                                            version = version,
                                            isExperimental = compatiblePackage.experimentalVersions
                                                ?.contains(version) == true,
                                            modifier = Modifier.align(Alignment.CenterVertically)
                                        )
                                    }

                                    if (versions.size > 1) {
                                        StatusBadge(
                                            text = if (expandVersions)
                                                stringResource(R.string.less)
                                            else
                                                "+${versions.size - 1}",
                                            modifier = Modifier.align(Alignment.CenterVertically),
                                            onClick = { expandVersions = !expandVersions }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Expert badge - shown only for patches that are disabled by default
                if (!patch.include && onExpertBadgeClick != null) {
                    StatusBadge(
                        text = stringResource(R.string.sources_patch_expert_badge),
                        icon = Icons.Outlined.Lock,
                        tone = SemanticTone.Warning,
                        onClick = onExpertBadgeClick
                    )
                }

                // Options
                if (!patch.options.isNullOrEmpty()) {
                    AnimatedVisibility(
                        visible = expandOptions,
                        enter = Animations.expandFadeEnter,
                        exit = Animations.shrinkFadeExit
                    ) {
                        Column(
                            modifier = Modifier.padding(top = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            patch.options.forEach { option ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(Defaults.CompactCornerRadius),
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
                                            color = textColor
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
}

/**
 * One version a patch declares support for, tagged the way every version list tags it.
 */
@Composable
private fun PatchVersionBadge(
    version: String,
    isExperimental: Boolean,
    modifier: Modifier = Modifier
) {
    StatusBadge(
        modifier = modifier,
        text = version,
        icon = if (isExperimental) VersionTag.Experimental.icon else Icons.Outlined.Code,
        tone = if (isExperimental) VersionTag.Experimental.tone else SemanticTone.Neutral
    )
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
    var olderState: OlderBundleState by remember { mutableStateOf(OlderBundleState.Collapsed) }
    val scope = rememberCoroutineScope()
    // 0 = waiting for dialog enter; incremented to trigger fetch, again on retry
    var fetchTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(fetchTrigger) {
        if (fetchTrigger == 0) return@LaunchedEffect
        state = BundleChangelogState.Loading
        state = withContext(Dispatchers.Default) {
            try {
                val usePrerelease = src.usesPrerelease

                val allEntries = src.fetchChangelogEntries(sinceVersion = null)

                val entries = if (usePrerelease) {
                    // Prerelease: from the last stable release onwards
                    val lastStable = allEntries.firstOrNull { !it.version.contains("-") }
                    if (lastStable != null)
                        ChangelogParser.entriesNewerThan(allEntries, lastStable.version) + lastStable
                    else allEntries.take(30)
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
                    repoUrl?.let { releasePageUrl(it, version) }
                }

                if (entries.isNotEmpty()) {
                    BundleChangelogState.Entries(
                        entries = entries,
                        parsedMarkdown = preParseChangelogEntries(entries),
                        latestPageUrl = latestPageUrl
                    )
                } else {
                    // Fallback: CHANGELOG.md unavailable - use latest release info from API
                    val asset = src.fetchLatestReleaseInfo()
                    val fallbackEntries = listOf(
                        ChangelogEntry(
                            version = asset.version,
                            date = null,
                            content = asset.description.sanitizePatchChangelogMarkdown()
                        )
                    )
                    BundleChangelogState.Entries(
                        entries = fallbackEntries,
                        parsedMarkdown = preParseChangelogEntries(fallbackEntries),
                        latestPageUrl = asset.pageUrl
                    )
                }
            } catch (t: Throwable) {
                BundleChangelogState.Error(t)
            }
        }
    }

    val loadOlder: () -> Unit = load@{
        if (olderState !is OlderBundleState.Collapsed) return@load
        val shownVersions = (state as? BundleChangelogState.Entries)
            ?.entries
            ?.map { it.version.removePrefix("v").trim() }
            ?.toSet()
            .orEmpty()
        olderState = OlderBundleState.Loading
        scope.launch {
            olderState = withContext(Dispatchers.Default) {
                runCatching {
                    val all = src.fetchFullChangelogEntries()
                    val filtered = all.filter {
                        // Skip versions already shown above and any pre-release leftovers;
                        // history is meaningful only as the stable timeline
                        it.version.removePrefix("v").trim() !in shownVersions
                                && !it.version.contains("-")
                    }
                    OlderBundleState.Loaded(filtered)
                }.getOrElse {
                    // Surface failure as collapsed so a retry click re-triggers the fetch
                    OlderBundleState.Collapsed
                }
            }
        }
    }

    AppDialog(
        onDismissRequest = onDismissRequest,
        // Start fetch only after the dialog enter animation completes so the shimmer
        // is always visible first, even when data is cached and would resolve instantly
        onEntered = { if (fetchTrigger == 0) fetchTrigger = 1 },
        scrollable = false,
        title = when (state) {
            is BundleChangelogState.Entries -> null
            is BundleChangelogState.Error -> stringResource(R.string.changelog)
            BundleChangelogState.Loading -> stringResource(R.string.changelog)
        },
        footer = {
            when (val current = state) {
                is BundleChangelogState.Entries -> {
                    AppDialogActions(
                        actions = listOfNotNull(
                            changelogAction(current.latestPageUrl),
                            DialogAction(
                                text = stringResource(R.string.close),
                                onClick = onDismissRequest,
                                emphasis = DialogActionEmphasis.Outlined
                            )
                        ),
                        layout = DialogButtonLayout.Vertical
                    )
                }
                is BundleChangelogState.Error -> {
                    AppDialogActions(
                        actions = listOf(
                            DialogAction(
                                text = stringResource(R.string.retry),
                                onClick = { fetchTrigger++ }
                            ),
                            DialogAction(
                                text = stringResource(R.string.close),
                                onClick = onDismissRequest,
                                emphasis = DialogActionEmphasis.Outlined
                            )
                        ),
                        layout = DialogButtonLayout.Vertical
                    )
                }
                BundleChangelogState.Loading -> {
                    AppDialogOutlinedButton(
                        text = stringResource(R.string.close),
                        onClick = onDismissRequest,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    ) {
        BundleChangelogContent(
            state = state,
            olderState = olderState,
            onExpandOlder = loadOlder
        )
    }

    Overlay(visible = olderState is OlderBundleState.Loading) {
        PulsingLogoWithCaption(caption = stringResource(R.string.loading_older_releases))
    }
}

@Composable
private fun BundleChangelogContent(
    state: BundleChangelogState,
    olderState: OlderBundleState,
    onExpandOlder: () -> Unit,
) {
    Crossfade(
        targetState = state,
        animationSpec = tween(Defaults.ANIMATION_DURATION),
        modifier = Modifier.fillMaxWidth(),
        label = "changelog_state"
    ) { current ->
        when (current) {
            BundleChangelogState.Loading -> ChangelogSectionLoading()
            is BundleChangelogState.Error -> BundleChangelogError(error = current.throwable)
            is BundleChangelogState.Entries -> {
                if (current.entries.isEmpty()) {
                    Text(
                        text = stringResource(R.string.changelog_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    val listState = rememberLazyListState()
                    Box(modifier = Modifier.fillMaxWidth()) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            itemsIndexed(current.entries) { index, entry ->
                                if (index > 0) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(
                                            top = Defaults.ContentPaddingSmall,
                                            bottom = Defaults.ContentPadding
                                        ),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                    )
                                }
                                ChangelogEntrySection(
                                    entry = entry,
                                    headerIcon = Icons.Outlined.History,
                                    precomputedMarkdown = current.parsedMarkdown.getOrNull(index)
                                )
                            }
                            changelogOlderItems(
                                entries = (olderState as? OlderBundleState.Loaded)?.entries,
                                isLoading = olderState is OlderBundleState.Loading,
                                onExpand = onExpandOlder
                            )
                        }

                        ListScrollbar(
                            listState = listState,
                            modifier = Modifier.offset(x = LocalDialogHorizontalInset.current)
                        )

                        ScrollToTopButton(
                            listState = listState,
                            modifier = Modifier.offset(x = LocalDialogHorizontalInset.current)
                        )
                    }
                }
            }
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
        val parsedMarkdown: List<MarkdownRenderState?>,
        val latestPageUrl: String?
    ) : BundleChangelogState
    data class Error(val throwable: Throwable) : BundleChangelogState
}

private sealed interface OlderBundleState {
    data object Collapsed : OlderBundleState
    data object Loading : OlderBundleState
    /** [entries] are full-history stable entries, already filtered to exclude what's shown above. */
    data class Loaded(val entries: List<ChangelogEntry>) : OlderBundleState
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
