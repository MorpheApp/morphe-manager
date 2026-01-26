package app.revanced.manager.ui.component.morphe.home

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.revanced.manager.patcher.patch.PatchBundleInfo
import app.revanced.manager.patcher.patch.PatchInfo
import app.revanced.manager.ui.component.morphe.settings.ColorPickerDialog
import app.revanced.manager.ui.component.morphe.settings.ColorPreviewDot
import app.revanced.manager.ui.component.morphe.shared.*
import app.revanced.manager.ui.component.morphe.utils.rememberFolderPickerWithPermission
import app.revanced.manager.util.Options
import app.revanced.manager.util.PatchSelection

/**
 * Advanced patch selection and configuration dialog
 * Shown before patching when expert mode is enabled
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpertModeDialog(
    bundles: List<PatchBundleInfo.Scoped>,
    selectedPatches: PatchSelection,
    options: Options,
    onPatchToggle: (bundleUid: Int, patchName: String) -> Unit,
    onOptionChange: (bundleUid: Int, patchName: String, optionKey: String, value: Any?) -> Unit,
    onResetOptions: (bundleUid: Int, patchName: String) -> Unit,
    onDismiss: () -> Unit,
    onProceed: () -> Unit,
    allowIncompatible: Boolean = false
) {
    var selectedPatchForOptions by remember { mutableStateOf<Pair<Int, PatchInfo>?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    // Create local mutable state from incoming selectedPatches
    var localSelectedPatches by remember(selectedPatches) {
        mutableStateOf(selectedPatches.toMap())
    }

    // Get all patches with their enabled state
    val allPatchesInfo = remember(bundles, localSelectedPatches, allowIncompatible) {
        bundles.map { bundle ->
            val selected = localSelectedPatches[bundle.uid] ?: emptySet()
            // In Expert mode, always show all patches (force allowIncompatible = true)
            val patches = bundle.patchSequence(true)
                .map { patch -> patch to (patch.name in selected) }
                .toList()

            bundle to patches
        }.filter { it.second.isNotEmpty() }
    }

    // Filter patches based on search query
    val filteredPatchesInfo = remember(allPatchesInfo, searchQuery, localSelectedPatches) {
        if (searchQuery.isBlank()) {
            allPatchesInfo
        } else {
            allPatchesInfo.mapNotNull { (bundle, patches) ->
                val filtered = patches.filter { (patch, _) ->
                    patch.name.contains(searchQuery, ignoreCase = true) ||
                            patch.description?.contains(searchQuery, ignoreCase = true) == true
                }
                if (filtered.isEmpty()) null else bundle to filtered
            }
        }
    }

    val totalSelectedCount = localSelectedPatches.values.sumOf { it.size }
    val totalPatchesCount = allPatchesInfo.sumOf { it.second.size }

    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.morphe_expert_mode_title),
        dismissOnClickOutside = false,
        footer = {
            MorpheDialogButton(
                text = stringResource(R.string.morphe_expert_mode_proceed),
                onClick = {
                    // Sync all local changes back before proceeding
                    localSelectedPatches.forEach { (bundleUid, patches) ->
                        val originalPatches = selectedPatches[bundleUid] ?: emptySet()
                        patches.forEach { patchName ->
                            if (patchName !in originalPatches) {
                                onPatchToggle(bundleUid, patchName)
                            }
                        }
                        originalPatches.forEach { patchName ->
                            if (patchName !in patches) {
                                onPatchToggle(bundleUid, patchName)
                            }
                        }
                    }
                    // Handle removed bundles
                    selectedPatches.forEach { (bundleUid, patches) ->
                        if (bundleUid !in localSelectedPatches) {
                            patches.forEach { patchName ->
                                onPatchToggle(bundleUid, patchName)
                            }
                        }
                    }
                    onProceed()
                },
                enabled = totalSelectedCount > 0,
                icon = Icons.Outlined.AutoFixHigh,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Subtitle with count
            Text(
                text = stringResource(R.string.morphe_expert_mode_subtitle_extended, totalSelectedCount, totalPatchesCount),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalDialogSecondaryTextColor.current,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            // Search bar
            MorpheDialogTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = {
                    Text(stringResource(R.string.morphe_expert_mode_search))
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = stringResource(R.string.morphe_expert_mode_search)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.clear)
                            )
                        }
                    }
                }
            )

            // Content
            if (filteredPatchesInfo.isEmpty()) {
                EmptyStateContent(
                    hasSearch = searchQuery.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    filteredPatchesInfo.forEach { (bundle, patches) ->
                        val enabledCount = patches.count { it.second }
                        val totalCount = patches.size

                        BundleHeader(
                            bundleName = bundle.name,
                            enabledCount = enabledCount,
                            totalCount = totalCount,
                            onToggleAll = {
                                val allEnabled = enabledCount == totalCount
                                // If all enabled -> disable all enabled patches
                                // If any disabled -> enable all disabled patches

                                val currentPatches = localSelectedPatches.toMutableMap()
                                val bundlePatches = currentPatches[bundle.uid]?.toMutableSet() ?: mutableSetOf()

                                patches.forEach { (patch, isEnabled) ->
                                    if (allEnabled) {
                                        // Disable all currently enabled patches
                                        if (isEnabled) {
                                            bundlePatches.remove(patch.name)
                                        }
                                    } else {
                                        // Enable all currently disabled patches
                                        if (!isEnabled) {
                                            bundlePatches.add(patch.name)
                                        }
                                    }
                                }

                                if (bundlePatches.isEmpty()) {
                                    currentPatches.remove(bundle.uid)
                                } else {
                                    currentPatches[bundle.uid] = bundlePatches
                                }

                                localSelectedPatches = currentPatches
                            }
                        )

                        patches.forEach { (patch, isEnabled) ->
                            PatchCard(
                                patch = patch,
                                isEnabled = isEnabled,
                                onToggle = {
                                    val currentPatches = localSelectedPatches.toMutableMap()
                                    val bundlePatches = currentPatches[bundle.uid]?.toMutableSet() ?: mutableSetOf()

                                    if (patch.name in bundlePatches) {
                                        bundlePatches.remove(patch.name)
                                    } else {
                                        bundlePatches.add(patch.name)
                                    }

                                    if (bundlePatches.isEmpty()) {
                                        currentPatches.remove(bundle.uid)
                                    } else {
                                        currentPatches[bundle.uid] = bundlePatches
                                    }

                                    localSelectedPatches = currentPatches
                                },
                                onConfigureOptions = {
                                    if (!patch.options.isNullOrEmpty()) {
                                        selectedPatchForOptions = bundle.uid to patch
                                    }
                                },
                                hasOptions = !patch.options.isNullOrEmpty()
                            )
                        }
                    }
                }
            }
        }
    }

    // Options dialog
    selectedPatchForOptions?.let { (bundleUid, patch) ->
        PatchOptionsDialog(
            patch = patch,
            values = options[bundleUid]?.get(patch.name),
            onValueChange = { key, value ->
                onOptionChange(bundleUid, patch.name, key, value)
            },
            onReset = {
                onResetOptions(bundleUid, patch.name)
            },
            onDismiss = { selectedPatchForOptions = null }
        )
    }
}

/**
 * Bundle header showing bundle name, patch count, and toggle all button
 */
@Composable
private fun BundleHeader(
    bundleName: String,
    enabledCount: Int,
    totalCount: Int,
    onToggleAll: () -> Unit
) {
    val allEnabled = enabledCount == totalCount

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Surface(
                modifier = Modifier.size(32.dp),
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Source,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Text(
                text = bundleName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = LocalDialogTextColor.current
            )
            Text(
                text = "($enabledCount/$totalCount)",
                style = MaterialTheme.typography.bodyMedium,
                color = LocalDialogSecondaryTextColor.current
            )
        }

        // All patches selection button
        FilledTonalIconButton(
            onClick = onToggleAll,
            modifier = Modifier.size(32.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = if (allEnabled)
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                else
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                contentColor = if (allEnabled)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                imageVector = if (allEnabled) Icons.Outlined.ClearAll else Icons.Outlined.DoneAll,
                contentDescription = stringResource(
                    if (allEnabled) R.string.morphe_expert_mode_disable_all
                    else R.string.morphe_expert_mode_enable_all
                ),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Individual patch card with toggle and options button
 */
@Composable
private fun PatchCard(
    patch: PatchInfo,
    isEnabled: Boolean,
    onToggle: () -> Unit,
    onConfigureOptions: () -> Unit,
    hasOptions: Boolean
) {
    var isExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { isExpanded = !isExpanded },
        shape = RoundedCornerShape(14.dp),
        color = if (isEnabled) {
            MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
        } else {
            MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp).copy(alpha = 0.5f)
        },
        contentColor = if (isEnabled) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        },
        tonalElevation = if (isEnabled) 2.dp else 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header with patch name and controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = patch.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isEnabled)
                            LocalDialogTextColor.current
                        else
                            LocalDialogSecondaryTextColor.current.copy(alpha = 0.5f),
                        maxLines = if (isExpanded) Int.MAX_VALUE else 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (!patch.description.isNullOrBlank()) {
                        Text(
                            text = patch.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isEnabled)
                                LocalDialogSecondaryTextColor.current
                            else
                                LocalDialogSecondaryTextColor.current.copy(alpha = 0.4f),
                            maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Options button (only enabled if patch is enabled)
                    if (hasOptions) {
                        FilledTonalIconButton(
                            onClick = onConfigureOptions,
                            modifier = Modifier.size(36.dp),
                            enabled = isEnabled,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = stringResource(R.string.morphe_patch_options),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Toggle button
                    FilledTonalIconButton(
                        onClick = onToggle,
                        modifier = Modifier.size(36.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = if (isEnabled)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                            else
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            contentColor = if (isEnabled)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    ) {
                        Icon(
                            imageVector = if (isEnabled) Icons.Filled.Remove else Icons.Filled.Add,
                            contentDescription = stringResource(
                                if (isEnabled) R.string.disable
                                else R.string.enable
                            ),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Options indicator (only show if patch is enabled)
            if (hasOptions && isEnabled) {
                InfoBadge(
                    text = stringResource(R.string.morphe_expert_mode_has_options),
                    style = InfoBadgeStyle.Primary,
                    icon = Icons.Outlined.Tune,
                    isCompact = true
                )
            }
        }
    }
}

/**
 * Empty state content when no patches match search or none selected
 */
@Composable
private fun EmptyStateContent(
    hasSearch: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = if (hasSearch) Icons.Outlined.SearchOff else Icons.Outlined.Info,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = LocalDialogSecondaryTextColor.current
            )
            Text(
                text = stringResource(
                    if (hasSearch)
                        R.string.morphe_expert_mode_no_results
                    else
                        R.string.morphe_expert_mode_no_patches
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = LocalDialogSecondaryTextColor.current,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Options dialog for configuring patch options
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PatchOptionsDialog(
    patch: PatchInfo,
    values: Map<String, Any?>?,
    onValueChange: (String, Any?) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    var showColorPicker by remember { mutableStateOf<Pair<String, String>?>(null) }

    MorpheDialog(
        onDismissRequest = onDismiss,
        title = patch.name,
        titleTrailingContent = {
            IconButton(onClick = onReset) {
                Icon(
                    imageVector = Icons.Outlined.Restore,
                    contentDescription = stringResource(R.string.reset),
                    tint = LocalDialogTextColor.current
                )
            }
        },
        footer = {
            MorpheDialogButton(
                text = stringResource(android.R.string.ok),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!patch.description.isNullOrBlank()) {
                Text(
                    text = patch.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalDialogSecondaryTextColor.current
                )
            }

            if (patch.options == null) return@Column

            patch.options.forEach { option ->
                val key = option.key
                val value = if (values == null || !values.contains(key)) {
                    option.default
                } else {
                    values[key]
                }

                val typeName = option.type.toString()

                when {
                    // Color option
                    typeName.contains("String") && !typeName.contains("Array") &&
                            (option.title.contains("color", ignoreCase = true) ||
                                    option.key.contains("color", ignoreCase = true) ||
                                    (value is String && (value.startsWith("#") || value.startsWith("@android:color/")))) -> {
                        ColorOptionWithPresets(
                            title = option.title,
                            description = option.description,
                            value = value as? String ?: "#000000",
                            presets = option.presets,
                            onPresetSelect = { onValueChange(key, it) },
                            onCustomColorClick = {
                                showColorPicker = key to (value as? String ?: "#000000")
                            }
                        )
                    }

                    // Path/folder option
                    typeName.contains("String") && !typeName.contains("Array") &&
                            option.key != "customName" &&
                            (option.key.contains("icon", ignoreCase = true) ||
                                    option.key.contains("header", ignoreCase = true) ||
                                    option.key.contains("custom", ignoreCase = true) ||
                                    option.description.contains("folder", ignoreCase = true) ||
                                    option.description.contains("image", ignoreCase = true) ||
                                    option.description.contains("mipmap", ignoreCase = true) ||
                                    option.description.contains("drawable", ignoreCase = true)) -> {
                        PathInputOption(
                            title = option.title,
                            description = option.description,
                            value = value?.toString() ?: "",
//                            required = option.required,
                            onValueChange = { onValueChange(key, it) }
                        )
                    }

                    // String input field
                    typeName.contains("String") && !typeName.contains("Array") -> {
                        TextInputOption(
                            title = option.title,
//                            description = option.description,
                            value = value?.toString() ?: "",
//                            required = option.required,
                            keyboardType = KeyboardType.Text,
                            onValueChange = { onValueChange(key, it) }
                        )
                    }

                    // Boolean switch
                    typeName.contains("Boolean") -> {
                        BooleanOptionItem(
                            title = option.title,
                            description = option.description,
                            value = value as? Boolean == true,
                            onValueChange = { onValueChange(key, it) }
                        )
                    }

                    // Number input (Int/Long)
                    (typeName.contains("Int") || typeName.contains("Long")) && !typeName.contains("Array") -> {
                        TextInputOption(
                            title = option.title,
//                            description = option.description,
                            value = (value as? Number)?.toLong()?.toString() ?: "",
//                            required = option.required,
                            keyboardType = KeyboardType.Number,
                            onValueChange = { it.toLongOrNull()?.let { num -> onValueChange(key, num) } }
                        )
                    }

                    // Decimal input (Float/Double)
                    (typeName.contains("Float") || typeName.contains("Double")) && !typeName.contains("Array") -> {
                        TextInputOption(
                            title = option.title,
//                            description = option.description,
                            value = (value as? Number)?.toFloat()?.toString() ?: "",
//                            required = option.required,
                            keyboardType = KeyboardType.Decimal,
                            onValueChange = { it.toFloatOrNull()?.let { num -> onValueChange(key, num) } }
                        )
                    }

                    // Dropdown lists
                    typeName.contains("Array") -> {
                        val choices = option.presets?.keys?.toList() ?: emptyList()
                        DropdownOptionItem(
                            title = option.title,
                            description = option.description,
                            value = value?.toString() ?: "",
                            choices = choices,
                            onValueChange = { selectedKey ->
                                val selectedValue = option.presets?.get(selectedKey)
                                onValueChange(key, selectedValue)
                            }
                        )
                    }
                }
            }
        }
    }

    // Color picker dialog
    showColorPicker?.let { (key, currentColor) ->
        ColorPickerDialog(
            title = patch.options?.find { it.key == key }?.title ?: key,
            currentColor = currentColor,
            onColorSelected = { newColor ->
                onValueChange(key, newColor)
                showColorPicker = null
            },
            onDismiss = { showColorPicker = null }
        )
    }
}

@Composable
private fun ColorOptionWithPresets(
    title: String,
    description: String,
    value: String,
    presets: Map<String, *>?,
    onPresetSelect: (String) -> Unit,
    onCustomColorClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Title and description
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = LocalDialogTextColor.current
            )
            if (description.isNotBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalDialogSecondaryTextColor.current
                )
            }
        }

        // Presets
        if (!presets.isNullOrEmpty()) {
            presets.forEach { (label, presetValue) ->
                val colorValue = presetValue?.toString() ?: return@forEach
                ColorPresetItem(
                    label = label,
                    colorValue = colorValue,
                    isSelected = value == colorValue,
                    onClick = { onPresetSelect(colorValue) }
                )
            }
        }

        val isValueInPresets = presets?.values?.any { it.toString() == value } == true
        val isCustomSelected = !isValueInPresets

        // Custom color button
        ColorPresetItem(
            label = stringResource(R.string.morphe_custom_color),
            colorValue = value,
            isSelected = isCustomSelected,
            isCustom = true,
            onClick = onCustomColorClick
        )
    }
}

@Composable
fun ColorPresetItem(
    label: String,
    colorValue: String,
    isSelected: Boolean,
    isCustom: Boolean = false,
    enabled: Boolean = true,
    cornerRadius: Dp = 12.dp,
    elevation: Dp = 0.dp,
    borderWidth: Dp = 0.dp,
    onClick: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerRadius))
            .then(
                if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick)
                else Modifier
            ),
        shape = RoundedCornerShape(cornerRadius),
        color = if (isSelected)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else
            MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = elevation,
        border = if (isSelected && borderWidth > 0.dp) {
            BorderStroke(borderWidth, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        } else if (!isSelected && borderWidth > 0.dp) {
            BorderStroke(borderWidth, MaterialTheme.colorScheme.outlineVariant)
        } else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isCustom && !isSelected) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Palette,
                        contentDescription = null,
                        tint = LocalDialogTextColor.current.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                ColorPreviewDot(
                    colorValue = colorValue,
                    size = 32
                )
            }

            Text(
                text = if (isCustom) stringResource(R.string.morphe_custom_color) else label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected)
                    MaterialTheme.colorScheme.primary
                else
                    LocalDialogTextColor.current,
                modifier = Modifier.weight(1f)
            )

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun PathInputOption(
    title: String,
    description: String,
    value: String,
//    required: Boolean,
    onValueChange: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
//        Text(
//            text = title + if (required) " *" else "",
//            style = MaterialTheme.typography.bodyMedium,
//            fontWeight = FontWeight.Medium,
//            color = LocalDialogTextColor.current
//        )

        MorpheDialogTextField(
            value = value,
            onValueChange = onValueChange,
            label = {
                Text(title)
            },
            placeholder = {
                Text("/storage/emulated/0/folder")
            },
            trailingIcon = {
                Row(
                    modifier = Modifier.width(88.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Reset button
                    Box(
                        modifier = Modifier.size(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (value.isNotEmpty()) {
                            IconButton(
                                onClick = { onValueChange("") },
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Clear,
                                    contentDescription = stringResource(R.string.reset),
                                    tint = LocalDialogTextColor.current.copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // Folder picker button
                    val folderPicker = rememberFolderPickerWithPermission { uri ->
                        onValueChange(uri)
                    }
                    IconButton(
                        onClick = { folderPicker() },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FolderOpen,
                            contentDescription = stringResource(R.string.morphe_patch_option_pick_folder),
                            tint = LocalDialogTextColor.current.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        )

        // Instructions
        if (description.isNotBlank()) {
            ExpandableSurface(
                title = stringResource(R.string.morphe_patch_option_instructions),
                content = {
                    ScrollableInstruction(
                        description = description,
                        maxHeight = 280.dp
                    )
                }
            )
        }
    }
}

@Composable
private fun TextInputOption(
    title: String,
//    description: String,
    value: String,
//    required: Boolean,
    keyboardType: KeyboardType,
    onValueChange: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
//        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
//            Text(
//                text = title + if (required) " *" else "",
//                style = MaterialTheme.typography.bodyMedium,
//                fontWeight = FontWeight.Medium,
//                color = LocalDialogTextColor.current
//            )
//            if (description.isNotBlank()) {
//                Text(
//                    text = description,
//                    style = MaterialTheme.typography.bodySmall,
//                    color = LocalDialogSecondaryTextColor.current
//                )
//            }
//        }

        MorpheDialogTextField(
            value = value,
            onValueChange = onValueChange,
            label = {
                Text(title)
            },
            placeholder = {
                Text(
                    stringResource(
                        when (keyboardType) {
                            KeyboardType.Number -> R.string.morphe_patch_option_enter_number
                            KeyboardType.Decimal -> R.string.morphe_patch_option_enter_decimal
                            else -> R.string.morphe_patch_option_enter_value
                        }
                    )
                )
            },
            trailingIcon = {
                // Reset button
                if (value.isNotEmpty()) {
                    IconButton(
                        onClick = { onValueChange("") },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Clear,
                            contentDescription = stringResource(R.string.reset),
                            tint = LocalDialogTextColor.current.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
        )
    }
}

@Composable
private fun BooleanOptionItem(
    title: String,
    description: String,
    value: Boolean,
    onValueChange: (Boolean) -> Unit
) {
    RichSettingsItem(
        onClick = {},
        title = title,
        subtitle = if (description.isNotBlank()) description else null,
        trailingContent = {
            Switch(
                checked = value,
                onCheckedChange = onValueChange
            )
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownOptionItem(
    title: String,
    description: String,
    value: String,
    choices: List<String>,
    onValueChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = LocalDialogTextColor.current
            )
            if (description.isNotBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalDialogSecondaryTextColor.current
                )
            }
        }

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            MorpheDialogTextField(
                value = value,
                onValueChange = {},
                enabled = false,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                choices.forEach { choice ->
                    DropdownMenuItem(
                        text = { Text(choice) },
                        onClick = {
                            onValueChange(choice)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ExpandableSurface(
    title: String,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.Info,
    initialExpanded: Boolean = false,
    headerTint: Color = LocalDialogTextColor.current
) {
    var expanded by remember { mutableStateOf(initialExpanded) }
    val rotationAngle by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(300),
        label = "rotation"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(12.dp),
        color = headerTint.copy(alpha = 0.05f)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = headerTint,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = headerTint
                    )
                }

                Icon(
                    imageVector = Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded)
                        stringResource(R.string.collapse)
                    else
                        stringResource(R.string.expand),
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(rotationAngle),
                    tint = LocalDialogTextColor.current.copy(alpha = 0.7f)
                )
            }

            // Expandable content
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = tween(300)) + fadeIn(),
                exit = shrinkVertically(animationSpec = tween(300)) + fadeOut()
            ) {
                content()
            }
        }
    }
}

/**
 * Scrollable instructions box with fade at bottom
 */
@Composable
fun ScrollableInstruction(
    description: String,
    modifier: Modifier = Modifier,
    maxHeight: Dp = 300.dp
) {
    val scrollState = rememberScrollState()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MorpheSettingsDivider(fullWidth = true)

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.4f
            )
        }

        // Fade at bottom
        val showFade by remember {
            derivedStateOf { scrollState.value < scrollState.maxValue }
        }

        if (showFade) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(24.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    )
            )
        }
    }
}
