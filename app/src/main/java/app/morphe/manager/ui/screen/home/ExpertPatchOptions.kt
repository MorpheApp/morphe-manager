/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import app.morphe.manager.R
import app.morphe.manager.patcher.patch.*
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.screen.shared.colorpicker.ColorPresetGrid
import app.morphe.manager.ui.screen.shared.colorpicker.ColorPresetSwatch
import app.morphe.manager.util.*
import kotlinx.collections.immutable.ImmutableList
import kotlin.math.roundToInt

/**
 * Represents the resolved UI kind of patch option.
 * Used to drive an exhaustive when-expression in [PatchOptionEditor].
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
    /** Slider for a typed integer option that declares bounds. */
    data class IntSlider(val bounds: SliderBounds)        : OptionKind
    /** Slider for a typed decimal option that declares bounds. */
    data class FloatSlider(val bounds: SliderBounds)      : OptionKind
    /** Range slider for a typed integer range option. */
    data class IntRangeSlider(val bounds: SliderBounds)   : OptionKind
    /** Range slider for a typed decimal range option. */
    data class FloatRangeSlider(val bounds: SliderBounds) : OptionKind
}

/**
 * Resolves the [OptionKind] for a given [option] and its current [value].
 * All type-detection heuristics live here, keeping the UI when-expression clean and exhaustive.
 */
private fun resolveOptionKind(option: Option<*>, value: Any?): OptionKind {
    // Typed options dispatch to their dedicated picker Kind. Untyped string options
    // fall through to the heuristics below and render with the classic text field.
    option.explicitKind?.let { kind ->
        val explicit = when (kind) {
            ExplicitOptionKind.Folder      -> OptionKind.FolderPicker
            ExplicitOptionKind.FilePath    -> OptionKind.FilePicker
            ExplicitOptionKind.Files       -> OptionKind.StringList
            ExplicitOptionKind.Image       -> OptionKind.Image
            ExplicitOptionKind.Color       -> OptionKind.Color
            // A slider cannot be drawn without bounds, so such an option falls back
            // to the numeric field below instead of rendering as an empty track
            ExplicitOptionKind.IntSlider   -> option.sliderBounds?.let(OptionKind::IntSlider)
            ExplicitOptionKind.FloatSlider -> option.sliderBounds?.let(OptionKind::FloatSlider)
            ExplicitOptionKind.IntRange    -> option.sliderBounds?.let(OptionKind::IntRangeSlider)
            ExplicitOptionKind.FloatRange  -> option.sliderBounds?.let(OptionKind::FloatRangeSlider)
        }
        if (explicit != null) return explicit
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
 * A stored range option value read back as a range. Range options hold a two element list,
 * so anything else means the value predates the option or was written by another tool.
 */
private fun Any?.asFloatRange(): ClosedFloatingPointRange<Float>? {
    val pair = (this as? List<*>)?.mapNotNull { (it as? Number)?.toFloat() } ?: return null
    if (pair.size != 2) return null
    return minOf(pair[0], pair[1])..maxOf(pair[0], pair[1])
}

/**
 * Whether an option holds nothing: no value, a blank text or an empty list. A cleared field stores
 * a blank, which the patcher reads as unset too, so it counts the same as a missing value.
 */
private fun Any?.isUnsetOptionValue(): Boolean = when (this) {
    null -> true
    is String -> isBlank()
    is Collection<*> -> isEmpty()
    else -> false
}

/**
 * Whether two option values mean the same. A number can come back from storage as another type
 * than the option declares, so numbers are compared by value, and an unset value matches any other.
 */
private fun optionValueEquals(a: Any?, b: Any?): Boolean = when {
    a.isUnsetOptionValue() && b.isUnsetOptionValue() -> true
    a is Number && b is Number -> a.toDouble() == b.toDouble()
    a is List<*> && b is List<*> -> a.size == b.size && a.indices.all { optionValueEquals(a[it], b[it]) }
    else -> a == b
}

/**
 * Options dialog for configuring patch options. Headed by the app the patch is for, as the patch
 * list it opens from is, with each option on a card of its own that says where it stands.
 *
 * @param packageName App being patched, whose icon heads the dialog.
 * @param appName Name of that app, shown with the option count under the patch name.
 * @param appIcon Icon of that app where no source has one.
 * @param accentColor Color of that app, which tints the header band.
 */
@Composable
internal fun PatchOptionsDialog(
    patch: PatchInfo,
    packageName: String,
    appName: String,
    appIcon: Drawable?,
    accentColor: Color?,
    isDefaultBundle: Boolean,
    values: Map<String, Any?>?,
    onValueChange: (String, Any?) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val options = patch.options.orEmpty()
    // A key the stored map lacks is still on the patch's own value
    fun valueOf(option: Option<*>): Any? =
        if (values == null || option.key !in values) option.default else values[option.key]
    val anyChanged = options.any { !optionValueEquals(valueOf(it), it.default) }

    val showColorPicker = remember { mutableStateOf<Pair<String, String>?>(null) }
    val scrollState = rememberScrollState()

    AppDialog(
        onDismissRequest = onDismiss,
        accentColor = accentColor,
        footer = {
            AppDialogOutlinedButton(
                text = stringResource(R.string.close),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        },
        padding = DialogPadding.Compact,
        scrollable = false,
        contentArrangement = Arrangement.Top,
        fillContentHeight = true,
        hideFooterWhileTyping = true
    ) {
        ListDialogHeader(
            icon = { modifier ->
                AppIcon(packageName = packageName, icon = appIcon, contentDescription = null, modifier = modifier)
            },
            title = patch.displayName,
            subtitle = listOf(
                appName,
                pluralStringResource(R.plurals.option_count, options.size, options.size.toString())
            ).joinToString(" · ")
        ) {
            TitleAction(
                icon = Icons.Outlined.Restore,
                contentDescription = stringResource(R.string.reset),
                onClick = onReset,
                style = TitleActionStyle.Accent,
                enabled = anyChanged
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScrollFade(scrollState)
                .verticalScroll(scrollState)
                .padding(vertical = Defaults.ItemSpacing),
            // Spaced as the patch list the dialog opens from
            verticalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingSmall)
        ) {
            // What the patch does leads into its options
            if (!patch.description.isNullOrBlank()) {
                Text(
                    text = rememberTranslated(patch.description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = dialogSecondaryTextColor()
                )
            }

            options.forEach { option ->
                val value = valueOf(option)
                PatchOptionEditor(
                    option = option,
                    value = value,
                    heading = OptionHeading(
                        title = option.title,
                        // Only shown translated, the option kind is still told from the original
                        description = rememberTranslated(option.description),
                        required = option.required,
                        missing = option.required && value.isUnsetOptionValue(),
                        changed = !optionValueEquals(value, option.default),
                        // Dropping the stored value puts the option back on the patch's own
                        onReset = { onValueChange(option.key, null) }
                    ),
                    packageName = packageName,
                    isDefaultBundle = isDefaultBundle,
                    onValueChange = { onValueChange(option.key, it) },
                    onCustomColorClick = { showColorPicker.value = option.key to it }
                )
            }
        }
    }

    // Color picker dialog
    showColorPicker.value?.let { (key, currentColor) ->
        ColorPickerDialog(
            title = options.find { it.key == key }?.title ?: key,
            currentColor = currentColor,
            onColorSelected = { newColor ->
                onValueChange(key, newColor)
                showColorPicker.value = null
            },
            onDismiss = { showColorPicker.value = null }
        )
    }
}

/**
 * The card of one option, with the control its [OptionKind] calls for.
 *
 * @param onCustomColorClick Opens the color picker on the color the option holds.
 */
@Composable
private fun PatchOptionEditor(
    option: Option<*>,
    value: Any?,
    heading: OptionHeading,
    packageName: String,
    isDefaultBundle: Boolean,
    onValueChange: (Any?) -> Unit,
    onCustomColorClick: (String) -> Unit
) {
    // The patcher rejects a Long where an Int is declared, so the text is read as the
    // option's own type. A cleared field drops the value back to the patch default
    fun onNumberInput(text: String) {
        if (text.isBlank()) return onValueChange(null)
        coerceOptionValue(option.type, text)?.let(onValueChange)
    }

    when (val kind = resolveOptionKind(option, value)) {
        OptionKind.StringList -> ListStringInputOption(
            heading = heading,
            value = when (value) {
                is List<*> -> value.filterIsInstance<String>()
                is String  -> value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                else       -> emptyList()
            },
            onValueChange = { newList ->
                // Check the KType classifier to determine how the patcher expects the value.
                // List<String> options need a real List<String>, while plain String options expect a comma-separated String.
                if (option.type.classifier == List::class) {
                    onValueChange(newList.ifEmpty { null })
                } else {
                    onValueChange(newList.joinToString(", ").ifBlank { null })
                }
            }
        )

        OptionKind.Color -> {
            val color = value as? String ?: "#000000"
            ColorOptionWithPresets(
                heading = heading,
                value = color,
                presets = option.presets,
                onPresetSelect = onValueChange,
                onCustomColorClick = { onCustomColorClick(color) }
            )
        }

        OptionKind.PathWithPresets -> PathWithPresetsOption(
            heading = heading,
            value = value?.toString() ?: "",
            presets = option.presets as Map<String, Any?>,
            packageName = packageName,
            isDefaultBundle = isDefaultBundle,
            onValueChange = onValueChange
        )

        OptionKind.StringDropdown -> DropdownOption(
            heading = heading,
            value = value?.toString() ?: "",
            presets = option.presets as Map<String, Any?>,
            onValueChange = onValueChange
        )

        OptionKind.Path -> FolderOptionCard(
            heading = heading,
            value = value?.toString() ?: "",
            typed = false,
            asset = optionAssetOf(heading, isDefaultBundle),
            packageName = packageName,
            onValueChange = onValueChange
        )

        OptionKind.FilePath -> FilePathInputOption(
            heading = heading,
            value = value?.toString() ?: "",
            onValueChange = onValueChange
        )

        OptionKind.FolderPicker -> FolderOptionCard(
            heading = heading,
            value = value?.toString() ?: "",
            typed = true,
            asset = optionAssetOf(heading, isDefaultBundle),
            packageName = packageName,
            onValueChange = onValueChange
        )

        OptionKind.FilePicker -> FilePickerOption(
            heading = heading,
            value = value?.toString() ?: "",
            allowedExtensions = option.allowedExtensions,
            onValueChange = onValueChange
        )

        OptionKind.Image -> ImageInputOption(
            heading = heading,
            value = value?.toString() ?: "",
            allowedExtensions = option.allowedExtensions,
            recommendedSize = option.recommendedSize,
            onValueChange = onValueChange
        )

        OptionKind.StringText -> TextInputOption(
            heading = heading,
            value = value?.toString() ?: "",
            keyboardType = KeyboardType.Text,
            // Pass "" explicitly so the field stays visually cleared after
            // the user taps ✕. updateOption stores "" as a valid value (key
            // is kept in the map), which prevents the repository from re-injecting
            // the bundled default on the next load.
            // "" is stripped back to null (→ patcher default) in
            // Options.sanitizeForPatcher() before being sent to the patcher.
            onValueChange = onValueChange
        )

        OptionKind.BooleanToggle -> BooleanOptionItem(
            heading = heading,
            value = value as? Boolean == true,
            onValueChange = onValueChange
        )

        OptionKind.IntLong -> TextInputOption(
            heading = heading,
            value = (value as? Number)?.toLong()?.toString() ?: "",
            keyboardType = KeyboardType.Number,
            onValueChange = ::onNumberInput
        )

        OptionKind.FloatDouble -> TextInputOption(
            heading = heading,
            value = (value as? Number)?.toFloat()?.toString() ?: "",
            keyboardType = KeyboardType.Decimal,
            onValueChange = ::onNumberInput
        )

        OptionKind.ArrayDropdown -> DropdownOption(
            heading = heading,
            value = value?.toString() ?: "",
            presets = option.presets ?: emptyMap(),
            onValueChange = onValueChange
        )

        is OptionKind.IntSlider -> OptionCard(heading) {
            SliderOptionInput(
                value = (value as? Number)?.toFloat() ?: kind.bounds.min,
                min = kind.bounds.min,
                max = kind.bounds.max,
                step = kind.bounds.step,
                isInteger = true,
                onValueChange = { onValueChange(it.roundToInt()) }
            )
        }

        is OptionKind.FloatSlider -> OptionCard(heading) {
            SliderOptionInput(
                value = (value as? Number)?.toFloat() ?: kind.bounds.min,
                min = kind.bounds.min,
                max = kind.bounds.max,
                step = kind.bounds.step,
                isInteger = false,
                onValueChange = onValueChange
            )
        }

        is OptionKind.IntRangeSlider -> OptionCard(heading) {
            RangeSliderOptionInput(
                value = value.asFloatRange() ?: (kind.bounds.min..kind.bounds.max),
                min = kind.bounds.min,
                max = kind.bounds.max,
                step = kind.bounds.step,
                isInteger = true,
                onValueChange = { range ->
                    onValueChange(listOf(range.start.roundToInt(), range.endInclusive.roundToInt()))
                }
            )
        }

        is OptionKind.FloatRangeSlider -> OptionCard(heading) {
            RangeSliderOptionInput(
                value = value.asFloatRange() ?: (kind.bounds.min..kind.bounds.max),
                min = kind.bounds.min,
                max = kind.bounds.max,
                step = kind.bounds.step,
                isInteger = false,
                onValueChange = { range ->
                    onValueChange(listOf(range.start, range.endInclusive))
                }
            )
        }
    }
}

/**
 * What the creator of [heading] makes, told from how the option names itself, or null where it
 * asks for neither picture. Only the default Morphe bundle reads what the creators make.
 */
private fun optionAssetOf(heading: OptionHeading, isDefaultBundle: Boolean): OptionAsset? {
    if (!isDefaultBundle) return null
    // Check header first, then icon (header takes priority)
    return when {
        heading.title.contains("header", ignoreCase = true) ||
                heading.description.contains("header", ignoreCase = true) -> OptionAsset.Header

        heading.title.contains("icon", ignoreCase = true) ||
                heading.description.contains("mipmap", ignoreCase = true) -> OptionAsset.Icon

        else -> null
    }
}

/**
 * Color option as the swatches every color choice in the app is made from, its presets first and
 * the picker for any other color last. A preset no single color can show, such as a Material You
 * role or a color of the app's own theme, is told apart by its name alone, so those go ahead of
 * the swatches as named choices.
 */
@Composable
private fun ColorOptionWithPresets(
    heading: OptionHeading,
    value: String,
    presets: Map<String, *>?,
    onPresetSelect: (String) -> Unit,
    onCustomColorClick: () -> Unit
) {
    val entries = remember(presets) {
        presets.orEmpty().mapNotNull { (label, presetValue) -> presetValue?.toString()?.let { label to it } }
    }
    val swatches = remember(entries) {
        entries.mapNotNull { (label, preset) ->
            optionSwatchColor(preset)?.let { ColorPresetSwatch(key = preset, color = it, label = label) }
        }
    }
    val named = remember(entries, swatches) { entries.filter { (_, preset) -> swatches.none { it.key == preset } } }
    val selectedSwatch = swatches.find { it.key == value }
    val isCustom = entries.none { (_, preset) -> preset == value }

    OptionCard(heading) {
        // The choices the app gives everywhere, one card to a named preset, named in the type of
        // the option cards they sit in
        named.forEach { (label, preset) ->
            RadioSelectionCard(
                selected = preset == value,
                onSelect = { onPresetSelect(preset) }
            ) {
                CardHeadingText(name = label, description = null, modifier = Modifier.weight(1f))
            }
        }

        // A named choice shows its name, while a swatch carries none of its own, so the one in
        // effect is named under the grid. The name keeps its gap to the grid inside the fold, so
        // the line leaves no gap behind once it has folded away
        val swatchName = if (selectedSwatch != null || isCustom) selectedSwatch?.label ?: value else null
        var lastSwatchName by remember { mutableStateOf(swatchName.orEmpty()) }
        LaunchedEffect(swatchName) { swatchName?.let { lastSwatchName = it } }

        Column {
            ColorPresetGrid(
                presets = swatches,
                selectedKey = selectedSwatch?.key,
                onSelect = { onPresetSelect(it.key as String) },
                customColor = if (isCustom) optionSwatchColor(value) else null,
                onCustomClick = onCustomColorClick
            )

            AnimatedVisibility(
                visible = swatchName != null,
                enter = Animations.expandFadeEnter,
                exit = Animations.shrinkFadeExit
            ) {
                AnimatedContent(
                    targetState = swatchName ?: lastSwatchName,
                    transitionSpec = Animations.fadeCrossfade(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Defaults.ItemSpacing),
                    label = "colorOptionSwatchName"
                ) { name ->
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = dialogTextColor(),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * Swatch color of a color option value: a hex color or one of the plain Android colors, or null
 * for one no single color can show, such as a Material You role or a color of the app's own theme.
 */
private fun optionSwatchColor(value: String): Color? = when (value) {
    "@android:color/transparent" -> Color.Transparent
    "@android:color/black" -> Color.Black
    "@android:color/white" -> Color.White
    else -> value.toColorOrNull()
}

/**
 * Individual file path input with a file picker button.
 * Used for options whose description mentions "file path".
 */
@Composable
private fun FilePathInputOption(
    heading: OptionHeading,
    value: String,
    onValueChange: (String) -> Unit
) {
    val filePicker = rememberAdaptiveFilePicker(
        mimeTypes = arrayOf(WILDCARD_MIMETYPE),
        onResult = { uri -> uri?.toFilePath()?.let { onValueChange(it) } }
    )

    OptionCard(heading) {
        AppDialogTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text("/storage/emulated/0/file", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            isError = heading.missing,
            showClearButton = true,
            onFilePickerClick = { filePicker() }
        )
    }
}

/**
 * Combined path input with dropdown presets.
 * Used for options that have predefined values but also allow custom folder paths.
 */
@Composable
private fun PathWithPresetsOption(
    heading: OptionHeading,
    value: String,
    presets: Map<String, *>,
    packageName: String,
    isDefaultBundle: Boolean,
    onValueChange: (String) -> Unit
) {
    // Folder picker
    val folderPicker = rememberFolderPickerWithPermission { uri ->
        onValueChange(uri.toFilePath())
    }

    OptionCard(heading, showDescription = false) {
        // Dropdown TextField with folder picker and clear button
        AppDialogDropdownTextField(
            value = value,
            onValueChange = onValueChange,
            // Convert presets to Map<String, String> for dropdown
            dropdownItems = presets.mapValues { it.value.toString() },
            placeholder = {
                Text("/storage/emulated/0/folder", maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            showClearButton = true,
            onFolderPickerClick = { folderPicker() }
        )

        optionAssetOf(heading, isDefaultBundle)?.let { asset ->
            AssetCreatorAction(asset, packageName, onCreated = onValueChange)
        }
        OptionInstructions(heading.description)
    }
}

/** Dropdown for an option that declares a set of values. */
@Composable
private fun DropdownOption(
    heading: OptionHeading,
    value: String,
    presets: Map<String, Any?>,
    onValueChange: (Any?) -> Unit
) {
    OptionCard(heading) {
        DropdownOptionField(value = value, presets = presets, onValueChange = onValueChange)
    }
}

@Composable
private fun TextInputOption(
    heading: OptionHeading,
    value: String,
    keyboardType: KeyboardType,
    onValueChange: (String) -> Unit
) {
    OptionCard(heading) {
        AppDialogTextField(
            value = value,
            onValueChange = onValueChange,
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
            isError = heading.missing,
            showClearButton = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
        )
    }
}

/** Switch at the end of the title row, with the whole card toggling it as a patch card does. */
@Composable
private fun BooleanOptionItem(
    heading: OptionHeading,
    value: Boolean,
    onValueChange: (Boolean) -> Unit
) {
    OptionCard(
        heading = heading,
        showStatus = false,
        onClick = { onValueChange(!value) },
        trailing = { ToggleSwitch(checked = value, onCheckedChange = onValueChange) }
    )
}

/**
 * List option edited in place, as every other option is: its values as chips that remove
 * themselves when tapped, and a field that adds what is typed. Several values can go in at once,
 * separated by commas, the way list options describe themselves.
 */
@Composable
private fun ListStringInputOption(
    heading: OptionHeading,
    value: List<String>,
    onValueChange: (List<String>) -> Unit
) {
    var input by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<Int?>(null) }
    // A list of numbers is most likely added to with more numbers, so the keyboard offers digits
    val numeric = value.isNotEmpty() && value.all { it.toDoubleOrNull() != null }

    fun addInput() {
        val entries = input.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        error = when {
            entries.isEmpty() -> R.string.patch_option_list_empty
            entries.distinct().size != entries.size || entries.any { it in value } -> R.string.patch_option_list_duplicate
            else -> {
                onValueChange(value + entries)
                input = ""
                null
            }
        }
    }

    OptionCard(heading) {
        if (value.isNotEmpty()) {
            // A chip already keeps a touch target's height around it, which spaces the lines
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingSmall)) {
                value.forEachIndexed { index, item ->
                    AppInputChip(
                        label = item,
                        onRemove = { onValueChange(value.filterIndexed { i, _ -> i != index }) }
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingSmall),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppDialogTextField(
                value = input,
                onValueChange = {
                    input = it
                    error = null
                },
                placeholder = { Text(stringResource(R.string.patch_option_enter_value)) },
                isError = error != null,
                showClearButton = input.isNotBlank(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { addInput() }),
                modifier = Modifier.weight(1f)
            )
            TitleAction(
                icon = Icons.Outlined.Add,
                contentDescription = stringResource(R.string.add),
                onClick = ::addInput,
                style = TitleActionStyle.Accent
            )
        }

        error?.let {
            Text(
                text = stringResource(it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

/**
 * Button-only file picker for a typed file option
 * (`app.morphe.patcher.patch.FilePathOption`). Optionally filters by
 * [allowedExtensions] via MIME type.
 */
@Composable
private fun FilePickerOption(
    heading: OptionHeading,
    value: String,
    allowedExtensions: ImmutableList<String>? = null,
    onValueChange: (String) -> Unit
) {
    val mimeTypes = remember(allowedExtensions) {
        extensionsToMimeTypes(allowedExtensions).ifEmpty { arrayOf(WILDCARD_MIMETYPE) }
    }

    val filePicker = rememberAdaptiveFilePicker(
        mimeTypes = mimeTypes,
        onResult = { uri -> uri?.toFilePath()?.let { onValueChange(it) } }
    )

    OptionCard(heading) {
        PickerButtonRow(
            label = stringResource(R.string.select_file),
            selectedPath = value,
            icon = Icons.AutoMirrored.Outlined.InsertDriveFile,
            onPick = { filePicker() },
            onClear = { onValueChange("") },
        )
    }
}

/**
 * Image-file picker option. Restricts the picker to image MIME types and shows an
 * optional "recommended size" hint under the field.
 */
@Composable
private fun ImageInputOption(
    heading: OptionHeading,
    value: String,
    allowedExtensions: ImmutableList<String>? = null,
    recommendedSize: ImageSize? = null,
    onValueChange: (String) -> Unit
) {
    // Fall back to image/* when no explicit extensions are declared.
    val mimeTypes = remember(allowedExtensions) {
        extensionsToMimeTypes(allowedExtensions).ifEmpty { arrayOf(IMAGE_MIMETYPE) }
    }

    val filePicker = rememberAdaptiveFilePicker(
        mimeTypes = mimeTypes,
        onResult = { uri -> uri?.toFilePath()?.let { onValueChange(it) } }
    )

    OptionCard(heading) {
        PickerButtonRow(
            label = stringResource(R.string.adaptive_icon_select_image),
            selectedPath = value,
            icon = Icons.Outlined.Image,
            onPick = { filePicker() },
            onClear = { onValueChange("") },
        )

        if (recommendedSize != null) {
            Text(
                text = stringResource(
                    R.string.patch_option_recommended_size,
                    recommendedSize.width.toString(),
                    recommendedSize.height.toString()
                ),
                style = MaterialTheme.typography.bodySmall,
                color = dialogSecondaryTextColor()
            )
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
