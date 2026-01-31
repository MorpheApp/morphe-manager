package app.revanced.manager.ui.component.morphe.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
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
import app.revanced.manager.ui.component.morphe.home.ColorPresetItem
import app.revanced.manager.ui.component.morphe.home.ExpandableSurface
import app.revanced.manager.ui.component.morphe.home.ScrollableInstruction
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

    // Get theme options from bundle
    val themeOptions = viewModel.getThemeOptions(appType.packageName)

    // Get dark theme option with its presets
    val darkThemeOption = viewModel.getOption(themeOptions, PatchOptionKeys.DARK_THEME_COLOR)
    val darkPresets = darkThemeOption?.let { viewModel.getOptionPresetsMap(it) } ?: emptyMap()

    // Get light theme option (YouTube only)
    val lightThemeOption = viewModel.getOption(themeOptions, PatchOptionKeys.LIGHT_THEME_COLOR)
    val lightPresets = lightThemeOption?.let { viewModel.getOptionPresetsMap(it) } ?: emptyMap()

    // Get default values from presets
    val defaultDarkColor = darkPresets.entries.firstOrNull()?.value?.toString() ?: "@android:color/black"
    val defaultLightColor = lightPresets.entries.firstOrNull()?.value?.toString() ?: "@android:color/white"

    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.morphe_patch_options_theme_colors),
        titleTrailingContent = {
            IconButton(
                onClick = {
                    scope.launch {
                        when (appType) {
                            AppType.YOUTUBE -> {
                                patchOptionsPrefs.darkThemeBackgroundColorYouTube.update(defaultDarkColor)
                                patchOptionsPrefs.lightThemeBackgroundColorYouTube.update(defaultLightColor)
                            }
                            AppType.YOUTUBE_MUSIC -> {
                                patchOptionsPrefs.darkThemeBackgroundColorYouTubeMusic.update(defaultDarkColor)
                            }
                        }
                    }
                },
            ) {
                Icon(
                    imageVector = Icons.Outlined.Restore,
                    contentDescription = stringResource(R.string.reset),
                    tint = LocalDialogTextColor.current
                )
            }
        },
        footer = {
            MorpheDialogOutlinedButton(
                text = stringResource(R.string.save),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                    ColorPresetItem(
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
                ColorPresetItem(
                    label = stringResource(R.string.morphe_custom_color),
                    colorValue = darkColor,
                    isSelected = darkPresets.values.none { it?.toString() == darkColor },
                    isCustom = true,
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
                    ColorPresetItem(
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
                ColorPresetItem(
                    label = stringResource(R.string.morphe_custom_color),
                    colorValue = lightColor,
                    isSelected = lightPresets.values.none { it?.toString() == lightColor },
                    isCustom = true,
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // App Name field
            if (appNameOption != null) {
                MorpheDialogTextField(
                    value = appName,
                    onValueChange = { appName = it },
                    label = {
                        Text(stringResource(R.string.morphe_patch_options_custom_branding_app_name))
                    },
                    placeholder = {
                        Text(stringResource(R.string.morphe_patch_options_custom_branding_app_name_hint))
                    },
                    trailingIcon = {
                        // Reset button
                        if (appName.isNotEmpty()) {
                            IconButton(
                                onClick = { appName = "" },
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
                    }
                )
            }

            // Icon Path field with Folder Picker
            if (iconOption != null) {
                MorpheDialogTextField(
                    value = iconPath,
                    onValueChange = { iconPath = it },
                    label = {
                        Text(stringResource(R.string.morphe_patch_options_custom_branding_custom_icon))
                    },
                    placeholder = {
                        Text("/storage/emulated/0/icons")
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
                                if (iconPath.isNotEmpty()) {
                                    IconButton(
                                        onClick = { iconPath = "" },
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
                            IconButton(
                                onClick = openFolderPicker,
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

                Spacer(modifier = Modifier.height(0.dp))

                // Expandable Instructions Section
                iconOption.description.let { description ->
                    val localizedDescription = getLocalizedOrCustomText(
                        context,
                        description,
                        CUSTOM_ICON_INSTRUCTION,
                        R.string.morphe_patch_options_custom_branding_custom_icon_instruction
                    )

                    ExpandableSurface(
                        title = stringResource(R.string.morphe_patch_option_instructions),
                        content = { ScrollableInstruction(description = localizedDescription) }
                    )
                }
            }

            // Show message if no options available
            if (appNameOption == null && iconOption == null) {
                Text(
                    text = stringResource(R.string.morphe_patch_options_no_available),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalDialogSecondaryTextColor.current.copy(alpha = 0.7f),
                    fontStyle = FontStyle.Italic
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (customOption != null) {
                MorpheDialogTextField(
                    value = headerPath,
                    onValueChange = { headerPath = it },
                    label = {
                        Text(stringResource(R.string.morphe_patch_options_custom_header))
                    },
                    placeholder = {
                        Text("/storage/emulated/0/header")
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
                                if (headerPath.isNotEmpty()) {
                                    IconButton(
                                        onClick = { headerPath = "" },
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
                            IconButton(
                                onClick = openFolderPicker,
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

                Spacer(modifier = Modifier.height(0.dp))

                // Expandable Instructions Section
                customOption.description.let { description ->
                    val localizedDescription = getLocalizedOrCustomText(
                        context,
                        description,
                        CUSTOM_HEADER_INSTRUCTION,
                        R.string.morphe_patch_options_custom_header_instruction
                    )

                    ExpandableSurface(
                        title = stringResource(R.string.morphe_patch_option_instructions),
                        content = { ScrollableInstruction(description = localizedDescription) }
                    )
                }
            } else {
                // No option available
                Text(
                    text = stringResource(R.string.morphe_patch_options_no_available),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalDialogSecondaryTextColor.current.copy(alpha = 0.7f),
                    fontStyle = FontStyle.Italic
                )
            }
        }
    }
}
