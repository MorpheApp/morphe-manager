package app.revanced.manager.ui.component.morphe.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.revanced.manager.domain.manager.PatchOptionsPreferencesManager
import app.revanced.manager.ui.component.morphe.shared.*
import kotlinx.coroutines.launch

/**
 * Theme color selection dialog
 */
@Composable
fun ThemeColorDialog(
    patchOptionsPrefs: PatchOptionsPreferencesManager,
    isYouTube: Boolean,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val darkColor by patchOptionsPrefs.darkThemeBackgroundColor.getAsState()
    val lightColor by patchOptionsPrefs.lightThemeBackgroundColor.getAsState()

    // Dark theme presets
    val darkPresets = remember {
        mapOf(
            "@android:color/black" to R.string.morphe_theme_preset_pure_black,
            "@android:color/system_neutral1_900" to R.string.morphe_theme_preset_material_you,
            "#212121" to R.string.morphe_theme_preset_classic,
            "#181825" to R.string.morphe_theme_preset_catppuccin_mocha,
            "#290025" to R.string.morphe_theme_preset_dark_pink,
            "#001029" to R.string.morphe_theme_preset_dark_blue,
            "#002905" to R.string.morphe_theme_preset_dark_green,
            "#282900" to R.string.morphe_theme_preset_dark_yellow,
            "#291800" to R.string.morphe_theme_preset_dark_orange,
            "#290000" to R.string.morphe_theme_preset_dark_red
        )
    }

    // Light theme presets (YouTube only)
    val lightPresets = remember {
        mapOf(
            "@android:color/white" to R.string.morphe_theme_preset_white,
            "@android:color/system_neutral1_50" to R.string.morphe_theme_preset_material_you,
            "#E6E9EF" to R.string.morphe_theme_preset_catppuccin_latte,
            "#FCCFF3" to R.string.morphe_theme_preset_light_pink,
            "#D1E0FF" to R.string.morphe_theme_preset_light_blue,
            "#CCFFCC" to R.string.morphe_theme_preset_light_green,
            "#FDFFCC" to R.string.morphe_theme_preset_light_yellow,
            "#FFE6CC" to R.string.morphe_theme_preset_light_orange,
            "#FFD6D6" to R.string.morphe_theme_preset_light_red
        )
    }

    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.morphe_theme_colors),
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
            Text(
                text = stringResource(R.string.morphe_theme_dark_color),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = LocalDialogTextColor.current
            )

            darkPresets.forEach { (value, labelRes) ->
                ThemePresetItem(
                    label = stringResource(labelRes),
                    isSelected = darkColor == value,
                    onClick = {
                        scope.launch {
                            patchOptionsPrefs.darkThemeBackgroundColor.update(value)
                        }
                    }
                )
            }

            // Light Theme Section (YouTube only)
            if (isYouTube) {
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.morphe_theme_light_color),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = LocalDialogTextColor.current
                )

                lightPresets.forEach { (value, labelRes) ->
                    ThemePresetItem(
                        label = stringResource(labelRes),
                        isSelected = lightColor == value,
                        onClick = {
                            scope.launch {
                                patchOptionsPrefs.lightThemeBackgroundColor.update(value)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemePresetItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) {
            LocalDialogTextColor.current.copy(alpha = 0.1f)
        } else {
            LocalDialogTextColor.current.copy(alpha = 0.05f)
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = LocalDialogTextColor.current,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
            if (isSelected) {
                Text(
                    text = "✓",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Custom branding dialog
 */
@Composable
fun CustomBrandingDialog(
    patchOptionsPrefs: PatchOptionsPreferencesManager,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()

    // Get current values from preferences
    val currentAppName by patchOptionsPrefs.customAppName.getAsState()
    val currentIconPath by patchOptionsPrefs.customIconPath.getAsState()

    // Local state for editing
    var appName by remember { mutableStateOf(currentAppName) }
    var iconPath by remember { mutableStateOf(currentIconPath) }

    // Update local state when prefs change
    LaunchedEffect(currentAppName) {
        appName = currentAppName
    }
    LaunchedEffect(currentIconPath) {
        iconPath = currentIconPath
    }

    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.morphe_custom_branding),
        footer = {
            MorpheDialogButtonRow(
                primaryText = stringResource(R.string.save),
                onPrimaryClick = {
                    scope.launch {
                        patchOptionsPrefs.edit {
                            patchOptionsPrefs.customAppName.value = appName
                            patchOptionsPrefs.customIconPath.value = iconPath
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
            // App Name
            OutlinedTextField(
                value = appName,
                onValueChange = { appName = it },
                label = {
                    Text(
                        stringResource(R.string.morphe_custom_app_name),
                        color = LocalDialogSecondaryTextColor.current
                    )
                },
                placeholder = {
                    Text(
                        stringResource(R.string.morphe_custom_app_name_hint),
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

            // Icon Path
            OutlinedTextField(
                value = iconPath,
                onValueChange = { iconPath = it },
                label = {
                    Text(
                        stringResource(R.string.morphe_custom_icon_path),
                        color = LocalDialogSecondaryTextColor.current
                    )
                },
                placeholder = {
                    Text(
                        stringResource(R.string.morphe_custom_icon_path_hint),
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

            Text(
                text = stringResource(R.string.morphe_custom_icon_path_description),
                style = MaterialTheme.typography.bodySmall,
                color = LocalDialogSecondaryTextColor.current
            )
        }
    }
}

/**
 * Custom header dialog (YouTube only)
 */
@Composable
fun CustomHeaderDialog(
    patchOptionsPrefs: PatchOptionsPreferencesManager,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()

    // Get current value from preferences
    val currentHeaderPath by patchOptionsPrefs.customHeaderPath.getAsState()

    // Local state for editing
    var headerPath by remember { mutableStateOf(currentHeaderPath) }

    // Update local state when prefs change
    LaunchedEffect(currentHeaderPath) {
        headerPath = currentHeaderPath
    }

    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.morphe_custom_header),
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
            OutlinedTextField(
                value = headerPath,
                onValueChange = { headerPath = it },
                label = {
                    Text(
                        stringResource(R.string.morphe_custom_header),
                        color = LocalDialogSecondaryTextColor.current
                    )
                },
                placeholder = {
                    Text(
                        stringResource(R.string.morphe_custom_header_hint),
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

            Text(
                text = stringResource(R.string.morphe_custom_header_description),
                style = MaterialTheme.typography.bodySmall,
                color = LocalDialogSecondaryTextColor.current
            )
        }
    }
}
