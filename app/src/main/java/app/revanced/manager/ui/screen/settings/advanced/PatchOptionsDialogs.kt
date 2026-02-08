package app.revanced.manager.ui.screen.settings.advanced

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.revanced.manager.domain.manager.PatchOptionsPreferencesManager
import app.revanced.manager.domain.manager.PatchOptionsPreferencesManager.Companion.CUSTOM_HEADER_INSTRUCTION
import app.revanced.manager.domain.manager.PatchOptionsPreferencesManager.Companion.CUSTOM_ICON_INSTRUCTION
import app.revanced.manager.domain.manager.PatchOptionsPreferencesManager.Companion.DARK_THEME_COLOR_DESC
import app.revanced.manager.domain.manager.PatchOptionsPreferencesManager.Companion.DARK_THEME_COLOR_TITLE
import app.revanced.manager.domain.manager.PatchOptionsPreferencesManager.Companion.LIGHT_THEME_COLOR_DESC
import app.revanced.manager.domain.manager.PatchOptionsPreferencesManager.Companion.LIGHT_THEME_COLOR_TITLE
import app.revanced.manager.domain.manager.getLocalizedOrCustomText
import app.revanced.manager.ui.screen.home.ColorPresetItem
import app.revanced.manager.ui.screen.home.ExpandableSurface
import app.revanced.manager.ui.screen.home.ScrollableInstruction
import app.revanced.manager.ui.screen.shared.*
import app.revanced.manager.ui.viewmodel.PatchOptionKeys
import app.revanced.manager.ui.viewmodel.PatchOptionsViewModel
import app.revanced.manager.util.AppPackages
import app.revanced.manager.util.rememberFolderPickerWithPermission
import app.revanced.manager.util.toFilePath
import kotlinx.coroutines.launch

/**
 * Theme color selection dialog with dynamic options from bundle
 */
@Composable
fun ThemeColorDialog(
    patchOptionsPrefs: PatchOptionsPreferencesManager,
    patchOptionsViewModel: PatchOptionsViewModel,
    packageName: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Get current values from preferences
    val darkColor by when (packageName) {
        AppPackages.YOUTUBE -> patchOptionsPrefs.darkThemeBackgroundColorYouTube.getAsState()
        AppPackages.YOUTUBE_MUSIC -> patchOptionsPrefs.darkThemeBackgroundColorYouTubeMusic.getAsState()
        else -> patchOptionsPrefs.darkThemeBackgroundColorYouTube.getAsState()
    }

    val lightColor by patchOptionsPrefs.lightThemeBackgroundColorYouTube.getAsState()

    // Local state for custom color input
    var showDarkColorPicker by remember { mutableStateOf(false) }
    var showLightColorPicker by remember { mutableStateOf(false) }

    // Get theme options from bundle
    val themeOptions = patchOptionsViewModel.getThemeOptions(packageName)

    // Get dark theme option with its presets
    val darkThemeOption = patchOptionsViewModel.getOption(themeOptions, PatchOptionKeys.DARK_THEME_COLOR)
    val darkPresets = darkThemeOption?.let { patchOptionsViewModel.getOptionPresetsMap(it) } ?: emptyMap()

    // Get light theme option (YouTube only)
    val lightThemeOption = patchOptionsViewModel.getOption(themeOptions, PatchOptionKeys.LIGHT_THEME_COLOR)
    val lightPresets = lightThemeOption?.let { patchOptionsViewModel.getOptionPresetsMap(it) } ?: emptyMap()

    // Get default values from presets
    val defaultDarkColor = darkPresets.entries.firstOrNull()?.value?.toString() ?: "@android:color/black"
    val defaultLightColor = lightPresets.entries.firstOrNull()?.value?.toString() ?: "@android:color/white"

    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_advanced_patch_options_theme_colors),
        titleTrailingContent = {
            IconButton(
                onClick = {
                    scope.launch {
                        when (packageName) {
                            AppPackages.YOUTUBE -> {
                                patchOptionsPrefs.darkThemeBackgroundColorYouTube.update(defaultDarkColor)
                                patchOptionsPrefs.lightThemeBackgroundColorYouTube.update(defaultLightColor)
                            }
                            AppPackages.YOUTUBE_MUSIC -> {
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
                    R.string.settings_advanced_patch_options_dark_theme_color
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
                        R.string.settings_advanced_patch_options_theme_color_description
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
                                when (packageName) {
                                    AppPackages.YOUTUBE -> patchOptionsPrefs.darkThemeBackgroundColorYouTube.update(colorValue)
                                    AppPackages.YOUTUBE_MUSIC -> patchOptionsPrefs.darkThemeBackgroundColorYouTubeMusic.update(colorValue)
                                }
                            }
                        }
                    )
                }

                // Custom color option
                ColorPresetItem(
                    label = stringResource(R.string.custom_color),
                    colorValue = darkColor,
                    isSelected = darkPresets.values.none { it?.toString() == darkColor },
                    isCustom = true,
                    onClick = { showDarkColorPicker = true }
                )
            }

            // Light Theme Section (YouTube only, if available)
            if (packageName == AppPackages.YOUTUBE && lightThemeOption != null) {
                Spacer(modifier = Modifier.height(8.dp))

                val localizedTitle = getLocalizedOrCustomText(
                    context,
                    lightThemeOption.title,
                    LIGHT_THEME_COLOR_TITLE,
                    R.string.settings_advanced_patch_options_light_theme_color
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
                        R.string.settings_advanced_patch_options_theme_color_description
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
                    label = stringResource(R.string.custom_color),
                    colorValue = lightColor,
                    isSelected = lightPresets.values.none { it?.toString() == lightColor },
                    isCustom = true,
                    onClick = { showLightColorPicker = true }
                )
            }

            // Show message if no options available
            if (darkThemeOption == null && lightThemeOption == null) {
                Text(
                    text = stringResource(R.string.settings_advanced_patch_options_no_available),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalDialogSecondaryTextColor.current.copy(alpha = 0.7f),
                    fontStyle = FontStyle.Italic
                )
            }
        }
    }

    // Dark Color Picker Dialog
    if (showDarkColorPicker) {
        ColorPickerDialog(
            title = stringResource(R.string.settings_advanced_patch_options_dark_theme_color),
            currentColor = darkColor,
            onColorSelected = { color ->
                scope.launch {
                    when (packageName) {
                        AppPackages.YOUTUBE -> patchOptionsPrefs.darkThemeBackgroundColorYouTube.update(color)
                        AppPackages.YOUTUBE_MUSIC -> patchOptionsPrefs.darkThemeBackgroundColorYouTubeMusic.update(color)
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
            title = stringResource(R.string.settings_advanced_patch_options_light_theme_color),
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
 * Custom branding dialog with folder picker and adaptive icon creator
 */
@Composable
fun CustomBrandingDialog(
    patchOptionsPrefs: PatchOptionsPreferencesManager,
    patchOptionsViewModel: PatchOptionsViewModel,
    packageName: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Get current values from preferences
    var appName by remember {
        mutableStateOf(
            when (packageName) {
                AppPackages.YOUTUBE -> patchOptionsPrefs.customAppNameYouTube.getBlocking()
                AppPackages.YOUTUBE_MUSIC -> patchOptionsPrefs.customAppNameYouTubeMusic.getBlocking()
                else -> ""
            }
        )
    }

    var iconPath by remember {
        mutableStateOf(
            when (packageName) {
                AppPackages.YOUTUBE -> patchOptionsPrefs.customIconPathYouTube.getBlocking()
                AppPackages.YOUTUBE_MUSIC -> patchOptionsPrefs.customIconPathYouTubeMusic.getBlocking()
                else -> ""
            }
        )
    }

    // State for icon creator dialog
    var showIconCreator by remember { mutableStateOf(false) }

    // Get branding options from bundle
    val brandingOptions = patchOptionsViewModel.getBrandingOptions(packageName)
    val appNameOption = patchOptionsViewModel.getOption(brandingOptions, PatchOptionKeys.CUSTOM_NAME)
    val iconOption = patchOptionsViewModel.getOption(brandingOptions, PatchOptionKeys.CUSTOM_ICON)

    // Folder picker with permission handling (needs permissions for icon creation)
    val openFolderPicker = rememberFolderPickerWithPermission(
        onFolderPicked = { uri ->
            // Convert URI to path for patch options compatibility
            iconPath = uri.toFilePath()
        }
    )

    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_advanced_patch_options_custom_branding),
        footer = {
            MorpheDialogButtonRow(
                primaryText = stringResource(R.string.save),
                onPrimaryClick = {
                    scope.launch {
                        patchOptionsPrefs.edit {
                            when (packageName) {
                                AppPackages.YOUTUBE -> {
                                    patchOptionsPrefs.customAppNameYouTube.value = appName
                                    patchOptionsPrefs.customIconPathYouTube.value = iconPath
                                }
                                AppPackages.YOUTUBE_MUSIC -> {
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
                        Text(stringResource(R.string.settings_advanced_patch_options_custom_branding_app_name))
                    },
                    placeholder = {
                        Text(stringResource(R.string.settings_advanced_patch_options_custom_branding_app_name_hint))
                    },
                    showClearButton = true,
                )
            }

            // Icon Path field with Folder Picker
            if (iconOption != null) {
                MorpheDialogTextField(
                    value = iconPath,
                    onValueChange = { iconPath = it },
                    label = {
                        Text(stringResource(R.string.settings_advanced_patch_options_custom_branding_custom_icon))
                    },
                    placeholder = {
                        Text("/storage/emulated/0/icons")
                    },
                    showClearButton = true,
                    onFolderPickerClick = { openFolderPicker() }
                )

                Spacer(modifier = Modifier.height(0.dp))

                // Create Icon button
                MorpheDialogOutlinedButton(
                    text = stringResource(R.string.adaptive_icon_create),
                    onClick = { showIconCreator = true },
                    icon = Icons.Outlined.AutoAwesome,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(0.dp))

                // Expandable Instructions Section
                iconOption.description.let { description ->
                    val localizedDescription = getLocalizedOrCustomText(
                        context,
                        description,
                        CUSTOM_ICON_INSTRUCTION,
                        R.string.settings_advanced_patch_options_custom_branding_custom_icon_instruction
                    )

                    ExpandableSurface(
                        title = stringResource(R.string.patch_option_instructions),
                        content = { ScrollableInstruction(description = localizedDescription) }
                    )
                }
            }

            // Show message if no options available
            if (appNameOption == null && iconOption == null) {
                Text(
                    text = stringResource(R.string.settings_advanced_patch_options_no_available),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalDialogSecondaryTextColor.current.copy(alpha = 0.7f),
                    fontStyle = FontStyle.Italic
                )
            }
        }
    }

    // Icon creator dialog
    if (showIconCreator) {
        AdaptiveIconCreatorDialog(
            onDismiss = { showIconCreator = false },
            onIconCreated = { path ->
                iconPath = path
                showIconCreator = false
            }
        )
    }
}

/**
 * Custom header dialog with folder picker and dynamic instructions from bundle
 */
@Composable
fun CustomHeaderDialog(
    patchOptionsPrefs: PatchOptionsPreferencesManager,
    patchOptionsViewModel: PatchOptionsViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var headerPath by remember { mutableStateOf(patchOptionsPrefs.customHeaderPath.getBlocking()) }

    // State for header creator dialog
    var showHeaderCreator by remember { mutableStateOf(false) }

    // Get header options from bundle
    val headerOptions = patchOptionsViewModel.getHeaderOptions()
    val customOption = patchOptionsViewModel.getOption(headerOptions, PatchOptionKeys.CUSTOM_HEADER)

    // Folder picker with permission handling (needs permissions for header creation)
    val openFolderPicker = rememberFolderPickerWithPermission(
        onFolderPicked = { uri ->
            // Convert URI to path for patch options compatibility
            headerPath = uri.toFilePath()
        }
    )

    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_advanced_patch_options_custom_header),
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
                        Text(stringResource(R.string.settings_advanced_patch_options_custom_header))
                    },
                    placeholder = {
                        Text("/storage/emulated/0/header")
                    },
                    showClearButton = true,
                    onFolderPickerClick = { openFolderPicker() }
                )

                Spacer(modifier = Modifier.height(0.dp))

                // Create Header button
                MorpheDialogOutlinedButton(
                    text = stringResource(R.string.header_creator_create),
                    onClick = { showHeaderCreator = true },
                    icon = Icons.Outlined.Image,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(0.dp))

                // Expandable Instructions Section
                customOption.description.let { description ->
                    val localizedDescription = getLocalizedOrCustomText(
                        context,
                        description,
                        CUSTOM_HEADER_INSTRUCTION,
                        R.string.settings_advanced_patch_options_custom_header_instruction
                    )

                    ExpandableSurface(
                        title = stringResource(R.string.patch_option_instructions),
                        content = { ScrollableInstruction(description = localizedDescription) }
                    )
                }
            } else {
                // No option available
                Text(
                    text = stringResource(R.string.settings_advanced_patch_options_no_available),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalDialogSecondaryTextColor.current.copy(alpha = 0.7f),
                    fontStyle = FontStyle.Italic
                )
            }
        }
    }

    // Header creator dialog
    if (showHeaderCreator) {
        HeaderCreatorDialog(
            onDismiss = { showHeaderCreator = false },
            onHeaderCreated = { path ->
                headerPath = path
                showHeaderCreator = false
            }
        )
    }
}
