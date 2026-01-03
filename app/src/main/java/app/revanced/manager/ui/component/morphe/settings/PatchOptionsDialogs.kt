package app.revanced.manager.ui.component.morphe.settings

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.revanced.manager.domain.manager.AppType
import app.revanced.manager.domain.manager.PatchOptionsPreferencesManager
import app.revanced.manager.domain.manager.PatchOptionsPreferencesManager.Companion.CUSTOM_HEADER_INSTRUCTION
import app.revanced.manager.domain.manager.PatchOptionsPreferencesManager.Companion.CUSTOM_ICON_INSTRUCTION
import app.revanced.manager.domain.manager.PatchOptionsPreferencesManager.Companion.DARK_THEME_COLOR_DESC
import app.revanced.manager.domain.manager.PatchOptionsPreferencesManager.Companion.DARK_THEME_COLOR_TITLE
import app.revanced.manager.domain.manager.PatchOptionsPreferencesManager.Companion.LIGHT_THEME_COLOR_DESC
import app.revanced.manager.domain.manager.PatchOptionsPreferencesManager.Companion.LIGHT_THEME_COLOR_TITLE
import app.revanced.manager.domain.manager.getLocalizedOrCustomText
import app.revanced.manager.ui.component.morphe.shared.*
import app.revanced.manager.ui.component.morphe.utils.rememberFolderPickerWithPermission
import app.revanced.manager.ui.viewmodel.PatchOptionKeys
import app.revanced.manager.ui.viewmodel.PatchOptionsViewModel
import kotlinx.coroutines.launch

/**
 * Theme color selection dialog with dynamic options from bundle
 */
@Composable
fun ThemeColorDialog(
    patchOptionsPrefs: PatchOptionsPreferencesManager,
    viewModel: PatchOptionsViewModel,
    appType: AppType,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Get current values from preferences
    val darkColor by when (appType) {
        AppType.YOUTUBE -> patchOptionsPrefs.darkThemeBackgroundColorYouTube.getAsState()
        AppType.YOUTUBE_MUSIC -> patchOptionsPrefs.darkThemeBackgroundColorYouTubeMusic.getAsState()
    }

    val lightColor by patchOptionsPrefs.lightThemeBackgroundColorYouTube.getAsState()

    // Local state for custom color input
    var showDarkColorPicker by remember { mutableStateOf(false) }
    var showLightColorPicker by remember { mutableStateOf(false) }
    var customDarkColor by remember { mutableStateOf(darkColor) }
    var customLightColor by remember { mutableStateOf(lightColor) }

    // Get theme options from bundle
    val themeOptions = viewModel.getThemeOptions(appType.packageName)

    // Get dark theme option with its presets
    val darkThemeOption = viewModel.getOption(themeOptions, PatchOptionKeys.DARK_THEME_COLOR)
    val darkPresets = darkThemeOption?.let { viewModel.getOptionPresetsMap(it) } ?: emptyMap()

    // Get light theme option (YouTube only)
    val lightThemeOption = viewModel.getOption(themeOptions, PatchOptionKeys.LIGHT_THEME_COLOR)
    val lightPresets = lightThemeOption?.let { viewModel.getOptionPresetsMap(it) } ?: emptyMap()

    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.morphe_patch_options_theme_colors),
        footer = {
            MorpheDialogOutlinedButton(
                text = stringResource(R.string.close),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 500.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Dark Theme Section
            if (darkThemeOption != null) {
                val localizedTitle = getLocalizedOrCustomText(
                    context,
                    darkThemeOption.title,
                    DARK_THEME_COLOR_TITLE,
                    R.string.morphe_patch_options_dark_theme_color
                )
                Text(
                    text = localizedTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = LocalDialogTextColor.current
                )

                darkThemeOption.description.takeIf { it.isNotEmpty() }?.let { desc ->
                    val localizedDesc = getLocalizedOrCustomText(
                        context,
                        desc,
                        DARK_THEME_COLOR_DESC,
                        R.string.morphe_patch_options_dark_theme_color_description
                    )
                    Text(
                        text = localizedDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalDialogSecondaryTextColor.current
                    )
                }

                // Presets
                darkPresets.forEach { (label, value) ->
                    val colorValue = value?.toString() ?: return@forEach
                    ThemePresetItem(
                        label = label,
                        colorValue = colorValue,
                        isSelected = darkColor == colorValue,
                        onClick = {
                            scope.launch {
                                when (appType) {
                                    AppType.YOUTUBE -> patchOptionsPrefs.darkThemeBackgroundColorYouTube.update(colorValue)
                                    AppType.YOUTUBE_MUSIC -> patchOptionsPrefs.darkThemeBackgroundColorYouTubeMusic.update(colorValue)
                                }
                            }
                        }
                    )
                }

                // Custom color option
                CustomColorItem(
                    label = stringResource(R.string.morphe_custom_color),
                    currentColor = darkColor,
                    isCustomSelected = darkPresets.values.none { it?.toString() == darkColor },
                    onClick = { showDarkColorPicker = true }
                )
            }

            // Light Theme Section (YouTube only, if available)
            if (appType == AppType.YOUTUBE && lightThemeOption != null) {
                Spacer(modifier = Modifier.height(8.dp))

                val localizedTitle = getLocalizedOrCustomText(
                    context,
                    lightThemeOption.title,
                    LIGHT_THEME_COLOR_TITLE,
                    R.string.morphe_patch_options_light_theme_color
                )
                Text(
                    text = localizedTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = LocalDialogTextColor.current
                )

                lightThemeOption.description.takeIf { it.isNotEmpty() }?.let { desc ->
                    val localizedDesc = getLocalizedOrCustomText(
                        context,
                        desc,
                        LIGHT_THEME_COLOR_DESC,
                        R.string.morphe_patch_options_light_theme_color_description
                    )
                    Text(
                        text = localizedDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalDialogSecondaryTextColor.current
                    )
                }

                // Presets
                lightPresets.forEach { (label, value) ->
                    val colorValue = value?.toString() ?: return@forEach
                    ThemePresetItem(
                        label = label,
                        colorValue = colorValue,
                        isSelected = lightColor == colorValue,
                        onClick = {
                            scope.launch {
                                patchOptionsPrefs.lightThemeBackgroundColorYouTube.update(colorValue)
                            }
                        }
                    )
                }

                // Custom color option
                CustomColorItem(
                    label = stringResource(R.string.morphe_custom_color),
                    currentColor = lightColor,
                    isCustomSelected = lightPresets.values.none { it?.toString() == lightColor },
                    onClick = { showLightColorPicker = true }
                )
            }

            // Show message if no options available
            if (darkThemeOption == null && lightThemeOption == null) {
                Text(
                    text = stringResource(R.string.morphe_patch_options_no_available),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalDialogSecondaryTextColor.current.copy(alpha = 0.7f),
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
        }
    }

    // Dark Color Picker Dialog
    if (showDarkColorPicker) {
        ColorPickerDialog(
            title = stringResource(R.string.morphe_patch_options_dark_theme_color),
            currentColor = darkColor,
            onColorSelected = { color ->
                scope.launch {
                    when (appType) {
                        AppType.YOUTUBE -> patchOptionsPrefs.darkThemeBackgroundColorYouTube.update(color)
                        AppType.YOUTUBE_MUSIC -> patchOptionsPrefs.darkThemeBackgroundColorYouTubeMusic.update(color)
                    }
                }
                showDarkColorPicker = false
            },
            onDismiss = { showDarkColorPicker = false }
        )
    }

    // Light Color Picker Dialog
    if (showLightColorPicker) {
        ColorPickerDialog(
            title = stringResource(R.string.morphe_patch_options_light_theme_color),
            currentColor = lightColor,
            onColorSelected = { color ->
                scope.launch {
                    patchOptionsPrefs.lightThemeBackgroundColorYouTube.update(color)
                }
                showLightColorPicker = false
            },
            onDismiss = { showLightColorPicker = false }
        )
    }
}

@Composable
private fun ThemePresetItem(
    label: String,
    colorValue: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    MorpheClickableCard(
        onClick = onClick,
        cornerRadius = 8.dp,
        alpha = if (isSelected) 0.1f else 0.05f
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Color preview dot
            ColorPreviewDot(colorValue = colorValue)

            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = LocalDialogTextColor.current,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.weight(1f)
            )

            if (isSelected) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun CustomColorItem(
    label: String,
    currentColor: String,
    isCustomSelected: Boolean,
    onClick: () -> Unit
) {
    MorpheClickableCard(
        onClick = onClick,
        cornerRadius = 8.dp,
        alpha = if (isCustomSelected) 0.1f else 0.05f
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Color picker icon or preview
            if (isCustomSelected) {
                ColorPreviewDot(colorValue = currentColor)
            } else {
                Icon(
                    imageVector = Icons.Outlined.Palette,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalDialogTextColor.current,
                    fontWeight = if (isCustomSelected) FontWeight.Bold else FontWeight.Normal
                )
                if (isCustomSelected) {
                    Text(
                        text = currentColor,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = LocalDialogTextColor.current.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Custom branding dialog with folder picker and dynamic instructions from bundle
 */
@Composable
fun CustomBrandingDialog(
    patchOptionsPrefs: PatchOptionsPreferencesManager,
    viewModel: PatchOptionsViewModel,
    appType: AppType,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Get current values from preferences
    var appName by remember {
        mutableStateOf(
            when (appType) {
                AppType.YOUTUBE -> patchOptionsPrefs.customAppNameYouTube.getBlocking()
                AppType.YOUTUBE_MUSIC -> patchOptionsPrefs.customAppNameYouTubeMusic.getBlocking()
            }
        )
    }

    var iconPath by remember {
        mutableStateOf(
            when (appType) {
                AppType.YOUTUBE -> patchOptionsPrefs.customIconPathYouTube.getBlocking()
                AppType.YOUTUBE_MUSIC -> patchOptionsPrefs.customIconPathYouTubeMusic.getBlocking()
            }
        )
    }

    // Get branding options from bundle
    val brandingOptions = viewModel.getBrandingOptions(appType.packageName)
    val appNameOption = viewModel.getOption(brandingOptions, PatchOptionKeys.CUSTOM_NAME)
    val iconOption = viewModel.getOption(brandingOptions, PatchOptionKeys.CUSTOM_ICON)

    // State for expandable instructions
    var showInstructions by remember { mutableStateOf(false) }
    val rotationAngle by animateFloatAsState(
        targetValue = if (showInstructions) 180f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "instruction_rotation"
    )

    // Folder picker with permission handling
    val openFolderPicker = rememberFolderPickerWithPermission(
        onFolderPicked = { path ->
            iconPath = path
        }
    )

    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.morphe_patch_options_custom_branding),
        footer = {
            MorpheDialogButtonRow(
                primaryText = stringResource(R.string.save),
                onPrimaryClick = {
                    scope.launch {
                        patchOptionsPrefs.edit {
                            when (appType) {
                                AppType.YOUTUBE -> {
                                    patchOptionsPrefs.customAppNameYouTube.value = appName
                                    patchOptionsPrefs.customIconPathYouTube.value = iconPath
                                }
                                AppType.YOUTUBE_MUSIC -> {
                                    patchOptionsPrefs.customAppNameYouTubeMusic.value = appName
                                    patchOptionsPrefs.customIconPathYouTubeMusic.value = iconPath
                                }
                            }
                        }
                        onDismiss()
                    }
                },
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // App Name field
            if (appNameOption != null) {
                OutlinedTextField(
                    value = appName,
                    onValueChange = { appName = it },
                    label = {
                        Text(
                            stringResource(R.string.morphe_patch_options_custom_branding_app_name),
                            color = LocalDialogSecondaryTextColor.current
                        )
                    },
                    placeholder = {
                        Text(
                            stringResource(R.string.morphe_patch_options_custom_branding_app_name_hint),
                            color = LocalDialogSecondaryTextColor.current.copy(alpha = 0.6f)
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = LocalDialogTextColor.current,
                        unfocusedTextColor = LocalDialogTextColor.current,
                        focusedBorderColor = LocalDialogTextColor.current.copy(alpha = 0.5f),
                        unfocusedBorderColor = LocalDialogTextColor.current.copy(alpha = 0.2f),
                        cursorColor = LocalDialogTextColor.current
                    )
                )
            }

            // Icon Path field with Folder Picker
            if (iconOption != null) {
                OutlinedTextField(
                    value = iconPath,
                    onValueChange = { iconPath = it },
                    label = {
                        Text(
                            stringResource(R.string.morphe_patch_options_custom_branding_custom_icon),
                            color = LocalDialogSecondaryTextColor.current
                        )
                    },
                    placeholder = {
                        Text(
                            "/storage/emulated/0/icons", // No need localization
                            color = LocalDialogSecondaryTextColor.current.copy(alpha = 0.6f)
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        IconButton(
                            onClick = openFolderPicker,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FolderOpen,
                                contentDescription = "Pick folder",
                                tint = LocalDialogTextColor.current.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = LocalDialogTextColor.current,
                        unfocusedTextColor = LocalDialogTextColor.current,
                        focusedBorderColor = LocalDialogTextColor.current.copy(alpha = 0.5f),
                        unfocusedBorderColor = LocalDialogTextColor.current.copy(alpha = 0.2f),
                        cursorColor = LocalDialogTextColor.current
                    )
                )

                // Expandable Instructions Section
                iconOption.description.let { description ->
                    val localizedDescription = getLocalizedOrCustomText(
                        context,
                        description,
                        CUSTOM_ICON_INSTRUCTION,
                        R.string.morphe_patch_options_custom_branding_custom_icon_instruction
                    )

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showInstructions = !showInstructions },
                        shape = RoundedCornerShape(12.dp),
                        color = LocalDialogTextColor.current.copy(alpha = 0.05f)
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
                                        imageVector = Icons.Outlined.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.morphe_patch_options_icon_instructions_title),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = LocalDialogTextColor.current
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Outlined.ExpandMore,
                                    contentDescription = if (showInstructions) "Collapse" else "Expand",
                                    modifier = Modifier
                                        .size(20.dp)
                                        .rotate(rotationAngle),
                                    tint = LocalDialogTextColor.current.copy(alpha = 0.7f)
                                )
                            }

                            // Expandable Content with description from bundle
                            AnimatedVisibility(
                                visible = showInstructions,
                                enter = expandVertically(animationSpec = tween(300)) + fadeIn(),
                                exit = shrinkVertically(animationSpec = tween(300)) + fadeOut()
                            ) {
                                ScrollableInstruction(description = localizedDescription)
                            }
                        }
                    }
                }
            }

            // Show message if no options available
            if (appNameOption == null && iconOption == null) {
                Text(
                    text = stringResource(R.string.morphe_patch_options_no_available),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalDialogSecondaryTextColor.current.copy(alpha = 0.7f),
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
        }
    }
}

/**
 * Custom header dialog with folder picker and dynamic instructions from bundle
 */
@Composable
fun CustomHeaderDialog(
    patchOptionsPrefs: PatchOptionsPreferencesManager,
    viewModel: PatchOptionsViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var headerPath by remember { mutableStateOf(patchOptionsPrefs.customHeaderPath.getBlocking()) }

    // Get header options from bundle
    val headerOptions = viewModel.getHeaderOptions()
    val customOption = viewModel.getOption(headerOptions, PatchOptionKeys.CUSTOM_HEADER)

    // State for expandable instructions
    var showInstructions by remember { mutableStateOf(false) }
    val rotationAngle by animateFloatAsState(
        targetValue = if (showInstructions) 180f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "instruction_rotation"
    )

    // Folder picker with permission handling
    val openFolderPicker = rememberFolderPickerWithPermission(
        onFolderPicked = { path ->
            headerPath = path
        }
    )

    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.morphe_patch_options_custom_header),
        footer = {
            MorpheDialogButtonRow(
                primaryText = stringResource(R.string.save),
                onPrimaryClick = {
                    scope.launch {
                        patchOptionsPrefs.customHeaderPath.update(headerPath)
                        onDismiss()
                    }
                },
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (customOption != null) {
                OutlinedTextField(
                    value = headerPath,
                    onValueChange = { headerPath = it },
                    label = {
                        Text(
                            stringResource(R.string.morphe_patch_options_custom_header),
                            color = LocalDialogSecondaryTextColor.current
                        )
                    },
                    placeholder = {
                        Text(
                            "/storage/emulated/0/header", // No need localization
                            color = LocalDialogSecondaryTextColor.current.copy(alpha = 0.6f)
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        IconButton(
                            onClick = openFolderPicker,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FolderOpen,
                                contentDescription = "Pick folder",
                                tint = LocalDialogTextColor.current.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = LocalDialogTextColor.current,
                        unfocusedTextColor = LocalDialogTextColor.current,
                        focusedBorderColor = LocalDialogTextColor.current.copy(alpha = 0.5f),
                        unfocusedBorderColor = LocalDialogTextColor.current.copy(alpha = 0.2f),
                        cursorColor = LocalDialogTextColor.current
                    )
                )

                // Expandable Instructions Section
                customOption.description.let { description ->
                    val localizedDescription = getLocalizedOrCustomText(
                        context,
                        description,
                        CUSTOM_HEADER_INSTRUCTION,
                        R.string.morphe_patch_options_custom_header_instruction
                    )

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showInstructions = !showInstructions },
                        shape = RoundedCornerShape(12.dp),
                        color = LocalDialogTextColor.current.copy(alpha = 0.05f)
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
                                        imageVector = Icons.Outlined.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.morphe_patch_options_header_instructions_title),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = LocalDialogTextColor.current
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Outlined.ExpandMore,
                                    contentDescription = if (showInstructions) "Collapse" else "Expand",
                                    modifier = Modifier
                                        .size(20.dp)
                                        .rotate(rotationAngle),
                                    tint = LocalDialogTextColor.current.copy(alpha = 0.7f)
                                )
                            }

                            // Expandable Content with description from bundle
                            AnimatedVisibility(
                                visible = showInstructions,
                                enter = expandVertically(animationSpec = tween(300)) + fadeIn(),
                                exit = shrinkVertically(animationSpec = tween(300)) + fadeOut()
                            ) {
                                ScrollableInstruction(description = localizedDescription)
                            }
                        }
                    }
                }
            } else {
                // No option available
                Text(
                    text = stringResource(R.string.morphe_patch_options_no_available),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalDialogSecondaryTextColor.current.copy(alpha = 0.7f),
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
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
            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                modifier = Modifier.padding(bottom = 4.dp)
            )

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
