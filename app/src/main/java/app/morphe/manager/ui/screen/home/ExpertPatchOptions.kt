/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import android.annotation.SuppressLint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.patcher.patch.ExplicitOptionKind
import app.morphe.manager.patcher.patch.ImageSize
import app.morphe.manager.patcher.patch.Option
import app.morphe.manager.patcher.patch.PatchInfo
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.util.IMAGE_MIMETYPE
import app.morphe.manager.util.WILDCARD_MIMETYPE
import app.morphe.manager.util.rememberAdaptiveFilePicker
import app.morphe.manager.util.rememberFolderPickerWithPermission
import app.morphe.manager.util.toColorOrNull
import app.morphe.manager.util.toFilePath
import kotlinx.collections.immutable.ImmutableList

/**
 * Represents the resolved UI kind of patch option.
 * Used to drive an exhaustive when-expression in [PatchOptionsDialog].
 */
private sealed interface OptionKind {
    data object StringList      : OptionKind
    data object Color           : OptionKind
    data object PathWithPresets : OptionKind
    data object StringDropdown  : OptionKind
    /** Folder path detected by heuristics on an untyped string option. */
    data object Path            : OptionKind
    /** File path detected by heuristics on an untyped string option. */
    data object FilePath        : OptionKind
    /** Folder picker for a typed folder option. */
    data object FolderPicker    : OptionKind
    /** File picker for a typed single-file option. */
    data object FilePicker      : OptionKind
    /** Image picker for a typed image option. */
    data object Image           : OptionKind
    data object StringText      : OptionKind
    data object BooleanToggle   : OptionKind
    data object IntLong         : OptionKind
    data object FloatDouble     : OptionKind
    data object ArrayDropdown   : OptionKind
}

/**
 * Resolves the [OptionKind] for a given [option] and its current [value].
 * All type-detection heuristics live here, keeping the UI when-expression clean and exhaustive.
 */
private fun resolveOptionKind(option: Option<*>, value: Any?): OptionKind {
    // Typed options dispatch to their dedicated picker Kind. Untyped string options
    // fall through to the heuristics below and render with the classic text field.
    option.explicitKind?.let { kind ->
        return when (kind) {
            ExplicitOptionKind.Folder   -> OptionKind.FolderPicker
            ExplicitOptionKind.FilePath -> OptionKind.FilePicker
            ExplicitOptionKind.Files    -> OptionKind.StringList
            ExplicitOptionKind.Image    -> OptionKind.Image
            ExplicitOptionKind.Color    -> OptionKind.Color
        }
    }

    val t        = option.type.toString()
    val isArray  = t.contains("Array")
    val isString = t.contains("String") && !isArray

    return when {
        // List<String> free-form comma-separated input
        t.contains("List") && t.contains("String") -> OptionKind.StringList

        // Color: string whose key/title hints "color" or value looks like a color literal
        isString && (
                option.title.contains("color", ignoreCase = true) ||
                        option.key.contains("color", ignoreCase = true) ||
                        (value is String && (value.startsWith("#") || value.startsWith("@android:color/")))
                ) -> OptionKind.Color

        // Path/folder string with presets: combined dropdown + path picker
        isString && option.presets?.isNotEmpty() == true && (
                option.description.contains("folder",   ignoreCase = true) ||
                        option.description.contains("mipmap",   ignoreCase = true) ||
                        option.description.contains("drawable", ignoreCase = true)
                ) -> OptionKind.PathWithPresets

        // String with presets: pure dropdown
        isString && option.presets?.isNotEmpty() == true -> OptionKind.StringDropdown

        // Individual file path string: file picker (not a folder)
        isString && option.presets == null &&
                option.description.contains("file path", ignoreCase = true) -> OptionKind.FilePath

        // Path/folder string without presets: folder picker + optional creator buttons
        isString && option.key != "customName" && (
                option.key.contains("icon",   ignoreCase = true) ||
                        option.key.contains("header", ignoreCase = true) ||
                        option.key.contains("custom", ignoreCase = true) ||
                        option.description.contains("folder",    ignoreCase = true) ||
                        option.description.contains("image",     ignoreCase = true) ||
                        option.description.contains("mipmap",    ignoreCase = true) ||
                        option.description.contains("drawable",  ignoreCase = true)
                ) -> OptionKind.Path

        // Comma-separated string: detected by value content or explicit description hint
        isString && option.presets == null && (
                (value is String && value.contains(",")) ||
                        option.description.contains("separated by commas", ignoreCase = true) ||
                        option.description.contains("comma-separated",     ignoreCase = true)
                ) -> OptionKind.StringList

        // Plain string text field
        isString -> OptionKind.StringText

        // Boolean toggle
        t.contains("Boolean") -> OptionKind.BooleanToggle

        // Integer / Long numeric input
        (t.contains("Int") || t.contains("Long")) && !isArray -> OptionKind.IntLong

        // Float / Double decimal input
        (t.contains("Float") || t.contains("Double")) && !isArray -> OptionKind.FloatDouble

        // Array: dropdown driven by presets
        isArray -> OptionKind.ArrayDropdown

        // Safe fallback
        else -> OptionKind.StringText
    }
}

/**
 * Options dialog for configuring patch options.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PatchOptionsDialog(
    patch: PatchInfo,
    isDefaultBundle: Boolean,
    values: Map<String, Any?>?,
    onValueChange: (String, Any?) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    // Derive the target package from the patch's compatible packages list
    val packageName = patch.compatiblePackages?.firstOrNull()?.packageName.orEmpty()

    val showColorPicker = remember { mutableStateOf<Pair<String, String>?>(null) }

    MorpheDialog(
        onDismissRequest = onDismiss,
        title = patch.name,
        titleTrailingContent = {
            DialogTitleAction(
                icon = Icons.Outlined.Restore,
                contentDescription = stringResource(R.string.reset),
                onClick = onReset
            )
        },
        footer = {
            MorpheDialogOutlinedButton(
                text = stringResource(R.string.close),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Patch description
            if (!patch.description.isNullOrBlank()) {
                Text(
                    text = patch.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalDialogSecondaryTextColor.current
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = MorpheDefaults.ItemSpacing),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    thickness = 0.5.dp
                )
            }

            if (patch.options == null) return@Column

            // Patch options
            patch.options.forEachIndexed { index, option ->
                val key   = option.key
                val value = if (values == null || key !in values) option.default else values[key]

                if (index > 0) {
                    val prevOption = patch.options[index - 1]
                    val prevValue = if (values == null || prevOption.key !in values) prevOption.default else values[prevOption.key]
                    val bothBooleans = resolveOptionKind(option, value) == OptionKind.BooleanToggle &&
                            resolveOptionKind(prevOption, prevValue) == OptionKind.BooleanToggle
                    // Consecutive boolean toggles get a small spacer instead of a divider.
                    // Dividers between toggles look redundant since each toggle is already a distinct row
                    if (bothBooleans) {
                        Spacer(modifier = Modifier.height(MorpheDefaults.ContentPaddingSmall))
                    } else {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = MorpheDefaults.ItemSpacing),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            thickness = 0.5.dp
                        )
                    }
                }

                when (resolveOptionKind(option, value)) {
                    OptionKind.StringList -> ListStringInputOption(
                        title = option.title,
                        description = option.description,
                        value = when (value) {
                            is List<*> -> value.filterIsInstance<String>()
                            is String  -> value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                            else       -> emptyList()
                        },
                        onValueChange = { newList ->
                            // Check the KType classifier to determine how the patcher expects the value.
                            // List<String> options need a real List<String>, while plain String options expect a comma-separated String.
                            if (option.type.classifier == List::class) {
                                onValueChange(key, newList.ifEmpty { null })
                            } else {
                                onValueChange(key, newList.joinToString(", ").ifBlank { null })
                            }
                        }
                    )

                    OptionKind.Color -> ColorOptionWithPresets(
                        title = option.title,
                        description = option.description,
                        value = value as? String ?: "#000000",
                        presets = option.presets,
                        onPresetSelect = { onValueChange(key, it) },
                        onCustomColorClick = {
                            showColorPicker.value = key to (value as? String ?: "#000000")
                        }
                    )

                    OptionKind.PathWithPresets -> {
                        val presets = option.presets as Map<String, Any?>
                        PathWithPresetsOption(
                            title = option.title,
                            description = option.description,
                            value = value?.toString() ?: "",
                            presets = presets,
                            packageName = packageName,
                            isDefaultBundle = isDefaultBundle,
                            required = option.required,
                            onValueChange = { onValueChange(key, it) }
                        )
                    }

                    OptionKind.StringDropdown -> {
                        val presets = option.presets as Map<String, Any?>
                        DropdownOptionItem(
                            title = option.title,
                            description = option.description,
                            value = value?.toString() ?: "",
                            presets = presets,
                            onValueChange = { onValueChange(key, it) }
                        )
                    }

                    OptionKind.Path -> PathInputOption(
                        title = option.title,
                        description = option.description,
                        value = value?.toString() ?: "",
                        packageName = packageName,
                        isDefaultBundle = isDefaultBundle,
                        required = option.required,
                        onValueChange = { onValueChange(key, it) }
                    )

                    OptionKind.FilePath -> FilePathInputOption(
                        title = option.title,
                        description = option.description,
                        value = value?.toString() ?: "",
                        required = option.required,
                        onValueChange = { onValueChange(key, it) }
                    )

                    OptionKind.FolderPicker -> FolderPickerOption(
                        title = option.title,
                        description = option.description,
                        value = value?.toString() ?: "",
                        packageName = packageName,
                        isDefaultBundle = isDefaultBundle,
                        required = option.required,
                        onValueChange = { onValueChange(key, it) }
                    )

                    OptionKind.FilePicker -> FilePickerOption(
                        title = option.title,
                        description = option.description,
                        value = value?.toString() ?: "",
                        required = option.required,
                        allowedExtensions = option.allowedExtensions,
                        onValueChange = { onValueChange(key, it) }
                    )

                    OptionKind.Image -> ImageInputOption(
                        title = option.title,
                        description = option.description,
                        value = value?.toString() ?: "",
                        required = option.required,
                        allowedExtensions = option.allowedExtensions,
                        recommendedSize = option.recommendedSize,
                        onValueChange = { onValueChange(key, it) }
                    )

                    OptionKind.StringText -> TextInputOption(
                        title = option.title,
                        description = option.description,
                        value = value?.toString() ?: "",
                        required = option.required,
                        keyboardType = KeyboardType.Text,
                        // Pass "" explicitly so the field stays visually cleared after
                        // the user taps ✕. updateOption stores "" as a valid value (key
                        // is kept in the map), which prevents the repository from re-injecting
                        // the bundled default on the next load.
                        // "" is stripped back to null (→ patcher default) in
                        // Options.sanitizeForPatcher() before being sent to the patcher.
                        onValueChange = { onValueChange(key, it) }
                    )

                    OptionKind.BooleanToggle -> BooleanOptionItem(
                        title = option.title,
                        description = option.description,
                        value = value as? Boolean == true,
                        onValueChange = { onValueChange(key, it) }
                    )

                    OptionKind.IntLong -> TextInputOption(
                        title = option.title,
                        description = option.description,
                        value = (value as? Number)?.toLong()?.toString() ?: "",
                        required = option.required,
                        keyboardType = KeyboardType.Number,
                        onValueChange = { it.toLongOrNull()?.let { num -> onValueChange(key, num) } }
                    )

                    OptionKind.FloatDouble -> TextInputOption(
                        title = option.title,
                        description = option.description,
                        value = (value as? Number)?.toFloat()?.toString() ?: "",
                        required = option.required,
                        keyboardType = KeyboardType.Decimal,
                        onValueChange = { it.toFloatOrNull()?.let { num -> onValueChange(key, num) } }
                    )

                    OptionKind.ArrayDropdown -> DropdownOptionItem(
                        title = option.title,
                        description = option.description,
                        value = value?.toString() ?: "",
                        presets = option.presets ?: emptyMap(),
                        onValueChange = { onValueChange(key, it) }
                    )
                }
            }
        }
    }

    // Color picker dialog
    showColorPicker.value?.let { (key, currentColor) ->
        ColorPickerDialog(
            title = patch.options?.find { it.key == key }?.title ?: key,
            currentColor = currentColor,
            onColorSelected = { newColor ->
                onValueChange(key, newColor)
                showColorPicker.value = null
            },
            onDismiss = { showColorPicker.value = null }
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
        verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPaddingSmall)
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
            label = stringResource(R.string.custom_color),
            colorValue = value,
            isSelected = isCustomSelected,
            isCustom = true,
            onClick = onCustomColorClick
        )
    }
}

/** Color preset item for the color picker option. */
@Composable
fun ColorPresetItem(
    label: String,
    colorValue: String,
    isSelected: Boolean,
    isCustom: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(12.dp)

    val isMaterialYou = colorValue.contains("system_neutral", ignoreCase = true) ||
            colorValue.contains("system_accent", ignoreCase = true) ||
            colorValue.contains("material_you", ignoreCase = true)

    val parsedColor = if (!isMaterialYou) {
        when (colorValue) {
            "@android:color/transparent" -> Color.Transparent
            "@android:color/black", "#000000", "#FF000000" -> Color.Black
            "@android:color/white", "#FFFFFF", "#ffffff", "#FFFFFFFF" -> Color.White
            else -> colorValue.toColorOrNull()
        }
    } else null

    val hasTransparency = parsedColor != null && parsedColor.alpha < 0.99f

    val contentColor = parsedColor?.let {
        val opaque = it.copy(alpha = 1f)
        // For very transparent colors the checkerboard dominates (light background)
        // For opaque/semi-opaque colors use effective luminance against dark background
        val effectiveLuminance = if (it.alpha < 0.4f) {
            // Blend against white checkerboard
            opaque.luminance() * it.alpha + (1f - it.alpha)
        } else {
            opaque.luminance() * it.alpha
        }
        if (effectiveLuminance > 0.18f) Color.Black.copy(alpha = 0.85f)
        else Color.White.copy(alpha = 0.9f)
    } ?: MaterialTheme.colorScheme.onSurface

    val borderColor = parsedColor?.let {
        if (it.luminance() > 0.35f) Color.Black.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.15f)
    } ?: if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(
                if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick)
                else Modifier
            )
            .then(
                when {
                    isCustom -> if (parsedColor != null) Modifier.background(Color.Transparent, shape)
                    else Modifier.background(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                        shape
                    )
                    isMaterialYou -> Modifier.background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF6650A4).copy(alpha = 0.25f),
                                Color(0xFF4B86B4).copy(alpha = 0.25f),
                                Color(0xFF2D9596).copy(alpha = 0.25f),
                            )
                        )
                    )
                    parsedColor != null -> Modifier.background(Color.Transparent, shape)
                    else -> Modifier.background(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                        shape
                    )
                }
            )
            .border(1.dp, borderColor, shape)
    ) {
        // Checkerboard underlay for transparent/semi-transparent colors
        if (parsedColor != null && hasTransparency) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val cellSize = 12.dp.toPx()
                val cols = (size.width / cellSize).toInt() + 1
                val rows = (size.height / cellSize).toInt() + 1
                for (row in 0..rows) {
                    for (col in 0..cols) {
                        val isLight = (row + col) % 2 == 0
                        drawRect(
                            color = if (isLight) Color.White else Color(0xFFCCCCCC),
                            topLeft = Offset(col * cellSize, row * cellSize),
                            size = Size(cellSize, cellSize)
                        )
                    }
                }
            }
        }
        // Color overlay
        if (parsedColor != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(parsedColor)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPaddingSmall),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isCustom || isMaterialYou || parsedColor != null) {
                Icon(
                    imageVector = when {
                        isCustom -> Icons.Outlined.Palette
                        isMaterialYou -> Icons.Outlined.AutoAwesome
                        else -> Icons.Outlined.Palette
                    },
                    contentDescription = null,
                    tint = contentColor.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }

            Text(
                text = if (isCustom) stringResource(R.string.custom_color) else label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = contentColor,
                modifier = Modifier.weight(1f)
            )

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = contentColor,
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
    packageName: String,
    isDefaultBundle: Boolean,
    required: Boolean = false,
    onValueChange: (String) -> Unit
) {
    val showIconCreator = remember { mutableStateOf(false) }
    val showHeaderCreator = remember { mutableStateOf(false) }
    val isInvalid = required && value.isBlank()

    // Detect if this is icon-related or header-related field
    // Check header first, then icon (header takes priority)
    val isHeaderField = title.contains("header", ignoreCase = true) ||
            description.contains("header", ignoreCase = true)

    val isIconField = !isHeaderField && (
            title.contains("icon", ignoreCase = true) ||
                    description.contains("mipmap", ignoreCase = true)
            )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPaddingSmall)
    ) {
        // Folder picker button (needs permissions for icon/header creation)
        val folderPicker = rememberFolderPickerWithPermission { uri ->
            // Convert URI to path for patch options compatibility
            onValueChange(uri.toFilePath())
        }

        MorpheDialogTextField(
            value = value,
            onValueChange = onValueChange,
            label = {
                Text(if (required) "$title *" else title)
            },
            placeholder = {
                Text("/storage/emulated/0/folder")
            },
            isError = isInvalid,
            showClearButton = true,
            onFolderPickerClick = { folderPicker() }
        )

        // Create Icon button (only for the default Morphe bundle)
        if (isIconField && isDefaultBundle) {
            MorpheDialogOutlinedButton(
                text = stringResource(R.string.adaptive_icon_create),
                onClick = { showIconCreator.value = true },
                icon = Icons.Outlined.AutoAwesome,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Create Header button (only for the default Morphe bundle)
        if (isHeaderField && isDefaultBundle) {
            MorpheDialogOutlinedButton(
                text = stringResource(R.string.header_creator_create),
                onClick = { showHeaderCreator.value = true },
                icon = Icons.Outlined.Image,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Instructions
        if (description.isNotBlank()) {
            ExpandableSurface(
                title = stringResource(R.string.patch_option_instructions),
                content = {
                    ScrollableInstruction(
                        description = description,
                        maxHeight = 280.dp
                    )
                }
            )
        }
    }

    // Icon creator dialog
    if (showIconCreator.value) {
        AdaptiveIconCreatorDialog(
            packageName = packageName,
            onDismiss = { showIconCreator.value = false },
            onIconCreated = { path ->
                onValueChange(path)
                showIconCreator.value = false
            }
        )
    }

    // Header creator dialog
    if (showHeaderCreator.value) {
        HeaderCreatorDialog(
            packageName = packageName,
            onDismiss = { showHeaderCreator.value = false },
            onHeaderCreated = { path ->
                onValueChange(path)
                showHeaderCreator.value = false
            }
        )
    }
}

/**
 * Individual file path input with a file picker button.
 * Used for options whose description mentions "file path".
 */
@Composable
private fun FilePathInputOption(
    title: String,
    description: String,
    value: String,
    required: Boolean = false,
    onValueChange: (String) -> Unit
) {
    val isInvalid = required && value.isBlank()

    val filePicker = rememberAdaptiveFilePicker(
        mimeTypes = arrayOf(WILDCARD_MIMETYPE),
        onResult = { uri -> uri?.toFilePath()?.let { onValueChange(it) } }
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPaddingSmall)
    ) {
        MorpheDialogTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(if (required) "$title *" else title) },
            placeholder = { Text("/storage/emulated/0/file") },
            isError = isInvalid,
            showClearButton = true,
            onFilePickerClick = { filePicker() }
        )

        if (description.isNotBlank()) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = LocalDialogSecondaryTextColor.current
            )
        }
    }
}

/**
 * Combined path input with dropdown presets.
 * Used for options that have predefined values but also allow custom folder paths.
 */
@Composable
private fun PathWithPresetsOption(
    title: String,
    description: String,
    value: String,
    presets: Map<String, *>,
    packageName: String,
    isDefaultBundle: Boolean,
    required: Boolean = false,
    onValueChange: (String) -> Unit
) {
    val showIconCreator = remember { mutableStateOf(false) }
    val showHeaderCreator = remember { mutableStateOf(false) }

    // Detect if this is icon-related or header-related field
    // Check header first, then icon (header takes priority)
    val isHeaderField = title.contains("header", ignoreCase = true) ||
            description.contains("header", ignoreCase = true)

    val isIconField = !isHeaderField && (
            title.contains("icon", ignoreCase = true) ||
                    description.contains("mipmap", ignoreCase = true)
            )

    // Convert presets to Map<String, String> for dropdown
    val dropdownItems = presets.mapValues { it.value.toString() }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPaddingSmall)
    ) {
        // Folder picker
        val folderPicker = rememberFolderPickerWithPermission { uri ->
            onValueChange(uri.toFilePath())
        }

        // Dropdown TextField with folder picker and clear button
        MorpheDialogDropdownTextField(
            value = value,
            onValueChange = onValueChange,
            dropdownItems = dropdownItems,
            label = if (required) ({ Text("$title *") }) else null,
            placeholder = {
                Text("/storage/emulated/0/folder")
            },
            showClearButton = true,
            onFolderPickerClick = { folderPicker() }
        )

        // Create Icon button (only for the default Morphe bundle)
        if (isIconField && isDefaultBundle) {
            MorpheDialogOutlinedButton(
                text = stringResource(R.string.adaptive_icon_create),
                onClick = { showIconCreator.value = true },
                icon = Icons.Outlined.AutoAwesome,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Create Header button (only for the default Morphe bundle)
        if (isHeaderField && isDefaultBundle) {
            MorpheDialogOutlinedButton(
                text = stringResource(R.string.header_creator_create),
                onClick = { showHeaderCreator.value = true },
                icon = Icons.Outlined.Image,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Instructions (collapsed by default)
        if (description.isNotBlank()) {
            ExpandableSurface(
                title = stringResource(R.string.patch_option_instructions),
                content = {
                    ScrollableInstruction(
                        description = description,
                        maxHeight = 200.dp
                    )
                },
                icon = Icons.Outlined.Info,
                initialExpanded = false
            )
        }
    }

    // Icon creator dialog
    if (showIconCreator.value) {
        AdaptiveIconCreatorDialog(
            packageName = packageName,
            onDismiss = { showIconCreator.value = false },
            onIconCreated = { path ->
                onValueChange(path)
                showIconCreator.value = false
            }
        )
    }

    // Header creator dialog
    if (showHeaderCreator.value) {
        HeaderCreatorDialog(
            packageName = packageName,
            onDismiss = { showHeaderCreator.value = false },
            onHeaderCreated = { path ->
                onValueChange(path)
                showHeaderCreator.value = false
            }
        )
    }
}

@Composable
private fun TextInputOption(
    title: String,
    description: String = "",
    value: String,
    required: Boolean = false,
    keyboardType: KeyboardType,
    onValueChange: (String) -> Unit
) {
    val isInvalid = required && value.isBlank()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPaddingSmall)
    ) {
        MorpheDialogTextField(
            value = value,
            onValueChange = onValueChange,
            label = {
                Text(if (required) "$title *" else title)
            },
            placeholder = {
                Text(
                    stringResource(
                        when (keyboardType) {
                            KeyboardType.Number -> R.string.patch_option_enter_number
                            KeyboardType.Decimal -> R.string.patch_option_enter_decimal
                            else -> R.string.patch_option_enter_value
                        }
                    )
                )
            },
            isError = isInvalid,
            showClearButton = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
        )

        // Patch option description
        if (description.isNotBlank()) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = LocalDialogSecondaryTextColor.current
            )
        }
    }
}

@Composable
private fun BooleanOptionItem(
    title: String,
    description: String,
    value: Boolean,
    onValueChange: (Boolean) -> Unit
) {
    val enabledState = stringResource(R.string.enabled)
    val disabledState = stringResource(R.string.disabled)

    SettingsItem(
        onClick = { onValueChange(!value) },
        title = title,
        subtitle = description.ifBlank { null },
        showBorder = true,
        trailingContent = {
            MorpheSwitch(
                checked = value,
                onCheckedChange = onValueChange,
                modifier = Modifier.semantics {
                    stateDescription = if (value) enabledState else disabledState
                }
            )
        }
    )
}

/**
 * Subtle tinted container used for grouped patch-option content inside dialogs.
 * Uses the dialog text color at 5% alpha so it adapts to light/dark dialog surfaces.
 */
@Composable
private fun DialogTintedSurface(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = shape,
        color = LocalDialogTextColor.current.copy(alpha = 0.05f),
        content = content
    )
}

/**
 * Inline option row that shows current item count and opens [ListStringEditorDialog].
 */
@Composable
private fun ListStringInputOption(
    title: String,
    description: String,
    value: List<String>,
    onValueChange: (List<String>) -> Unit
) {
    val showEditor = remember { mutableStateOf(false) }
    val textColor = LocalDialogTextColor.current
    val secondaryColor = LocalDialogSecondaryTextColor.current

    DialogTintedSurface(onClick = { showEditor.value = true }) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MorpheDefaults.ContentPadding, vertical = MorpheDefaults.ItemSpacing),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = textColor
                )
                if (description.isNotBlank()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.width(MorpheDefaults.ItemSpacing))

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (value.isNotEmpty()) {
                    InfoBadge(
                        text = "${value.size}",
                        style = InfoBadgeStyle.Primary,
                        isCompact = true
                    )
                }
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = null,
                    tint = secondaryColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }

    if (showEditor.value) {
        ListStringEditorDialog(
            title = title,
            description = description,
            initialItems = value,
            onDismiss = { showEditor.value = false },
            onConfirm = { newList ->
                onValueChange(newList)
                showEditor.value = false
            }
        )
    }
}

/**
 * Dialog for managing a list of string values.
 */
@SuppressLint("MutableCollectionMutableState")
@Composable
private fun ListStringEditorDialog(
    title: String,
    description: String,
    initialItems: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    var items by remember { mutableStateOf(initialItems.toMutableStateList()) }
    var inputText by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf(false) }

    fun addItem() {
        val trimmed = inputText.trim()
        if (trimmed.isBlank()) {
            inputError = true
            return
        }
        if (trimmed in items) {
            inputError = true
            return
        }
        items.add(trimmed)
        inputText = ""
        inputError = false
    }

    MorpheDialog(
        onDismissRequest = onDismiss,
        title = title,
        dismissOnClickOutside = false,
        footer = {
            MorpheDialogButtonRow(
                primaryText = stringResource(R.string.save),
                onPrimaryClick = { onConfirm(items.toList()) },
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ItemSpacing)
        ) {
            // Description
            if (description.isNotBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalDialogSecondaryTextColor.current
                )
            }

            // Input row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPaddingSmall),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MorpheDialogTextField(
                    value = inputText,
                    onValueChange = {
                        inputText = it
                        inputError = false
                    },
                    placeholder = { Text(stringResource(R.string.patch_option_enter_value)) },
                    isError = inputError,
                    showClearButton = inputText.isNotBlank(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onDone = { addItem() }
                    ),
                    modifier = Modifier.weight(1f)
                )
                FilledTonalIconButton(onClick = { addItem() }) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = stringResource(R.string.add)
                    )
                }
            }

            if (inputError) {
                Text(
                    text = stringResource(
                        if (inputText.trim() in items)
                            R.string.patch_option_list_duplicate
                        else
                            R.string.patch_option_list_empty
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // Items list
            if (items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = MorpheDefaults.ContentPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.patch_option_list_empty_state),
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalDialogSecondaryTextColor.current
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items.forEachIndexed { index, item ->
                        ListStringItemRow(
                            value = item,
                            onRemove = { items.removeAt(index) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Single item row inside [ListStringEditorDialog].
 */
@Composable
private fun ListStringItemRow(
    value: String,
    onRemove: () -> Unit
) {
    val textColor = LocalDialogTextColor.current

    DialogTintedSurface {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                modifier = Modifier.weight(1f),
                overflow = TextOverflow.Ellipsis,
                maxLines = 2
            )
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.remove),
                    tint = textColor.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun DropdownOptionItem(
    title: String,
    description: String,
    value: String,
    presets: Map<String, Any?>,
    onValueChange: (Any?) -> Unit
) {
    // Convert presets to String map for dropdown: display name -> value as string
    val dropdownItems = presets.mapValues { it.value?.toString() ?: "" }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPaddingSmall)
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

        MorpheDialogDropdownTextField(
            value = value,
            onValueChange = { newValue ->
                // Try to find the actual value from presets by matching the string representation
                val actualValue = presets.entries.find { it.value?.toString() == newValue }?.value
                    ?: newValue
                onValueChange(actualValue)
            },
            dropdownItems = dropdownItems
        )
    }
}

/**
 * Button-only folder picker for a typed folder option
 * (`app.morphe.patcher.patch.FolderOption`). Options declared as plain
 * `stringOption` render via [PathInputOption] instead.
 */
@Composable
private fun FolderPickerOption(
    title: String,
    description: String,
    value: String,
    packageName: String,
    isDefaultBundle: Boolean,
    required: Boolean = false,
    onValueChange: (String) -> Unit
) {
    val showIconCreator = remember { mutableStateOf(false) }
    val showHeaderCreator = remember { mutableStateOf(false) }
    val isInvalid = required && value.isBlank()

    // Detect if this is icon-related or header-related field.
    // Check header first, then icon (header takes priority).
    val isHeaderField = title.contains("header", ignoreCase = true) ||
            description.contains("header", ignoreCase = true)

    val isIconField = !isHeaderField && (
            title.contains("icon", ignoreCase = true) ||
                    description.contains("mipmap", ignoreCase = true)
            )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPaddingSmall)
    ) {
        // Folder picker (needs permissions for icon/header creation)
        val folderPicker = rememberFolderPickerWithPermission { uri ->
            onValueChange(uri.toFilePath())
        }

        PickerFieldHeader(title = title, required = required, isInvalid = isInvalid)

        PickerButtonRow(
            label = stringResource(R.string.select_folder),
            selectedPath = value,
            icon = Icons.Outlined.Folder,
            onPick = { folderPicker() },
            onClear = { onValueChange("") },
        )

        // Create Icon button (only for the default Morphe bundle)
        if (isIconField && isDefaultBundle) {
            MorpheDialogOutlinedButton(
                text = stringResource(R.string.adaptive_icon_create),
                onClick = { showIconCreator.value = true },
                icon = Icons.Outlined.AutoAwesome,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Create Header button (only for the default Morphe bundle)
        if (isHeaderField && isDefaultBundle) {
            MorpheDialogOutlinedButton(
                text = stringResource(R.string.header_creator_create),
                onClick = { showHeaderCreator.value = true },
                icon = Icons.Outlined.Image,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Instructions
        if (description.isNotBlank()) {
            ExpandableSurface(
                title = stringResource(R.string.patch_option_instructions),
                content = {
                    ScrollableInstruction(description = description, maxHeight = 280.dp)
                }
            )
        }
    }

    // Icon creator dialog
    if (showIconCreator.value) {
        AdaptiveIconCreatorDialog(
            packageName = packageName,
            onDismiss = { showIconCreator.value = false },
            onIconCreated = { path ->
                onValueChange(path)
                showIconCreator.value = false
            }
        )
    }

    // Header creator dialog
    if (showHeaderCreator.value) {
        HeaderCreatorDialog(
            packageName = packageName,
            onDismiss = { showHeaderCreator.value = false },
            onHeaderCreated = { path ->
                onValueChange(path)
                showHeaderCreator.value = false
            }
        )
    }
}

/**
 * Button-only file picker for a typed file option
 * (`app.morphe.patcher.patch.FilePathOption`). Optionally filters by
 * [allowedExtensions] via MIME type.
 */
@Composable
private fun FilePickerOption(
    title: String,
    description: String,
    value: String,
    required: Boolean = false,
    allowedExtensions: ImmutableList<String>? = null,
    onValueChange: (String) -> Unit
) {
    val isInvalid = required && value.isBlank()

    val mimeTypes = remember(allowedExtensions) {
        extensionsToMimeTypes(allowedExtensions).ifEmpty { arrayOf(WILDCARD_MIMETYPE) }
    }

    val filePicker = rememberAdaptiveFilePicker(
        mimeTypes = mimeTypes,
        onResult = { uri -> uri?.toFilePath()?.let { onValueChange(it) } }
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPaddingSmall)
    ) {
        PickerFieldHeader(title = title, required = required, isInvalid = isInvalid)

        PickerButtonRow(
            label = stringResource(R.string.select_file),
            selectedPath = value,
            icon = Icons.AutoMirrored.Outlined.InsertDriveFile,
            onPick = { filePicker() },
            onClear = { onValueChange("") },
        )

        if (description.isNotBlank()) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = LocalDialogSecondaryTextColor.current
            )
        }
    }
}

/**
 * Image-file picker option. Restricts the picker to image MIME types and shows an
 * optional "recommended size" hint under the field.
 */
@Composable
private fun ImageInputOption(
    title: String,
    description: String,
    value: String,
    required: Boolean = false,
    allowedExtensions: ImmutableList<String>? = null,
    recommendedSize: ImageSize? = null,
    onValueChange: (String) -> Unit
) {
    val isInvalid = required && value.isBlank()

    // Fall back to image/* when no explicit extensions are declared.
    val mimeTypes = remember(allowedExtensions) {
        extensionsToMimeTypes(allowedExtensions).ifEmpty { arrayOf(IMAGE_MIMETYPE) }
    }

    val filePicker = rememberAdaptiveFilePicker(
        mimeTypes = mimeTypes,
        onResult = { uri -> uri?.toFilePath()?.let { onValueChange(it) } }
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPaddingSmall)
    ) {
        PickerFieldHeader(title = title, required = required, isInvalid = isInvalid)

        PickerButtonRow(
            label = stringResource(R.string.adaptive_icon_select_image),
            selectedPath = value,
            icon = Icons.Outlined.Image,
            onPick = { filePicker() },
            onClear = { onValueChange("") },
        )

        val subtitle = buildString {
            if (description.isNotBlank()) append(description)
            if (recommendedSize != null) {
                if (isNotEmpty()) append(" · ")
                append("Recommended: ${recommendedSize.width}×${recommendedSize.height}")
            }
        }
        if (subtitle.isNotEmpty()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = LocalDialogSecondaryTextColor.current
            )
        }
    }
}

/**
 * Header row shown above a picker button (folder/file/image options).
 * Renders the option title, an optional "*" marker for required options,
 * and switches to the theme's error color when the option is required but empty.
 */
@Composable
private fun PickerFieldHeader(title: String, required: Boolean, isInvalid: Boolean) {
    Text(
        text = if (required) "$title *" else title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = if (isInvalid) MaterialTheme.colorScheme.error else LocalDialogTextColor.current,
    )
}

/**
 * Picker row: the main "select…" outlined button plus an inline trailing
 * [Icons.Outlined.Clear] icon button. The clear button is only rendered when
 * [selectedPath] is not blank.
 */
@Composable
private fun PickerButtonRow(
    label: String,
    selectedPath: String,
    icon: ImageVector,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MorpheDialogOutlinedButton(
            text = label,
            textSuffix = selectedPath.takeIf { it.isNotBlank() },
            icon = icon,
            onClick = onPick,
            modifier = Modifier.weight(1f),
        )

        if (selectedPath.isNotBlank()) {
            IconButton(
                onClick = onClear,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Clear,
                    contentDescription = stringResource(R.string.clear),
                    tint = LocalDialogTextColor.current.copy(alpha = 0.7f),
                )
            }
        }
    }
}

/**
 * Maps a list of file extensions (e.g. `["png", "jpg"]`) into MIME types suitable
 * for [rememberAdaptiveFilePicker]. Returns an empty array when [extensions] is
 * null or empty; callers should then substitute a default wildcard MIME type.
 */
private fun extensionsToMimeTypes(extensions: ImmutableList<String>?): Array<String> {
    if (extensions.isNullOrEmpty()) return emptyArray()
    val mimeMap = android.webkit.MimeTypeMap.getSingleton()
    return extensions
        .mapNotNull { mimeMap.getMimeTypeFromExtension(it.trimStart('.').lowercase()) }
        .distinct()
        .toTypedArray()
}
