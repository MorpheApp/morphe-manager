package app.revanced.manager.ui.component.morphe.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.revanced.manager.data.platform.Filesystem
import app.revanced.manager.domain.manager.PatchOptionsPreferencesManager
import app.revanced.manager.domain.repository.PatchBundleRepository
import app.revanced.manager.ui.component.morphe.shared.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Theme color selection dialog
 */
@Composable
fun ThemeColorDialog(
    patchOptionsPrefs: PatchOptionsPreferencesManager,
    appType: AppType,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()

    // Get appropriate preferences based on app type
    val darkColor by when (appType) {
        AppType.YOUTUBE -> patchOptionsPrefs.darkThemeBackgroundColorYouTube.getAsState()
        AppType.YOUTUBE_MUSIC -> patchOptionsPrefs.darkThemeBackgroundColorYouTubeMusic.getAsState()
    }

    // Light theme only for YouTube
    val lightColor by patchOptionsPrefs.lightThemeBackgroundColorYouTube.getAsState()

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
                            when (appType) {
                                AppType.YOUTUBE -> patchOptionsPrefs.darkThemeBackgroundColorYouTube.update(value)
                                AppType.YOUTUBE_MUSIC -> patchOptionsPrefs.darkThemeBackgroundColorYouTubeMusic.update(value)
                            }
                        }
                    }
                )
            }

            // Light Theme Section (YouTube only)
            if (appType == AppType.YOUTUBE) {
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
                                patchOptionsPrefs.lightThemeBackgroundColorYouTube.update(value)
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
 * Custom branding dialog with folder picker and instructions
 */
@Composable
fun CustomBrandingDialog(
    patchOptionsPrefs: PatchOptionsPreferencesManager,
    appType: AppType,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val bundleRepository: PatchBundleRepository = koinInject()
    val fs: Filesystem = koinInject()

    // Get appropriate preferences based on app type
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

    // State for expandable instructions
    var showInstructions by remember { mutableStateOf(false) }
    val rotationAngle by animateFloatAsState(
        targetValue = if (showInstructions) 180f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "instruction_rotation"
    )

    // Load full description from bundle repository
    var iconDescription by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            val bundleInfo = bundleRepository.bundleInfoFlow.first()
            val defaultBundle = bundleInfo[PatchBundleRepository.DEFAULT_SOURCE_UID]

            // Find "Custom branding" patch based on app type
            val packageName = when (appType) {
                AppType.YOUTUBE -> "com.google.android.youtube"
                AppType.YOUTUBE_MUSIC -> "com.google.android.apps.youtube.music"
            }

            val customBrandingPatch = defaultBundle?.patches?.find { patch ->
                patch.name.equals("Custom branding", ignoreCase = true) &&
                        patch.compatiblePackages?.any { it.packageName == packageName } == true
            }

            // Get customIcon option description
            val iconOption = customBrandingPatch?.options?.find {
                it.key.equals("customIcon", ignoreCase = true)
            }

            iconDescription = iconOption?.description
        } catch (e: Exception) {
            iconDescription = null
        } finally {
            isLoading = false
        }
    }

    // Folder picker launcher
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            // Convert content:// URI to file path
            val path = it.path?.replace("/tree/primary:", "/storage/emulated/0/")
                ?: it.toString()
            iconPath = path
        }
    }

    // Get permission contract and name
    val (permissionContract, permissionName) = remember { fs.permissionContract() }

    // Permission launcher - launches folder picker after permission is granted
    val permissionLauncher = rememberLauncherForActivityResult(contract = permissionContract) { granted ->
        if (granted) {
            folderPickerLauncher.launch(null)
        }
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

            // Icon Path with Folder Picker Button
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
                trailingIcon = {
                    IconButton(
                        onClick = {
                            // Check if storage permission is granted
                            if (fs.hasStoragePermission()) {
                                folderPickerLauncher.launch(null)
                            } else {
                                // Request storage permission
                                permissionLauncher.launch(permissionName)
                            }
                        },
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
                                text = stringResource(R.string.morphe_icon_instructions_title),
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

                    // Expandable Content
                    AnimatedVisibility(
                        visible = showInstructions,
                        enter = expandVertically(
                            animationSpec = tween(durationMillis = 300)
                        ) + fadeIn(
                            animationSpec = tween(durationMillis = 300)
                        ),
                        exit = shrinkVertically(
                            animationSpec = tween(durationMillis = 300)
                        ) + fadeOut(
                            animationSpec = tween(durationMillis = 300)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                                .heightIn(max = 300.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            HorizontalDivider(
                                color = LocalDialogTextColor.current.copy(alpha = 0.1f),
                                modifier = Modifier.padding(bottom = 4.dp)
                            )

                            when {
                                isLoading -> {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 16.dp),
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            color = MaterialTheme.colorScheme.primary,
                                            strokeWidth = 2.dp
                                        )
                                    }
                                }
                                iconDescription != null -> {
                                    Text(
                                        text = iconDescription!!,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = LocalDialogSecondaryTextColor.current,
                                        lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.4f
                                    )
                                }
                                else -> {
                                    Text(
                                        text = stringResource(R.string.morphe_icon_instructions_unavailable),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = LocalDialogSecondaryTextColor.current.copy(alpha = 0.7f),
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
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
 * Custom header dialog with folder picker and instructions
 */
@Composable
fun CustomHeaderDialog(
    patchOptionsPrefs: PatchOptionsPreferencesManager,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val bundleRepository: PatchBundleRepository = koinInject()
    val fs: Filesystem = koinInject()
    var headerPath by remember { mutableStateOf(patchOptionsPrefs.customHeaderPath.getBlocking()) }

    // State for expandable instructions
    var showInstructions by remember { mutableStateOf(false) }
    val rotationAngle by animateFloatAsState(
        targetValue = if (showInstructions) 180f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "instruction_rotation"
    )

    // Load full description from bundle repository
    var headerDescription by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            val bundleInfo = bundleRepository.bundleInfoFlow.first()
            val defaultBundle = bundleInfo[PatchBundleRepository.DEFAULT_SOURCE_UID]

            // Find "Change header" patch for YouTube
            val changeHeaderPatch = defaultBundle?.patches?.find { patch ->
                patch.name.equals("Change header", ignoreCase = true) &&
                        patch.compatiblePackages?.any { it.packageName == "com.google.android.youtube" } == true
            }

            // Get custom header option description
            val headerOption = changeHeaderPatch?.options?.find {
                it.key.equals("custom", ignoreCase = true)
            }

            headerDescription = headerOption?.description
        } catch (e: Exception) {
            headerDescription = null
        } finally {
            isLoading = false
        }
    }

    // Folder picker launcher
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            // Convert content:// URI to file path
            val path = it.path?.replace("/tree/primary:", "/storage/emulated/0/")
                ?: it.toString()
            headerPath = path
        }
    }

    // Get permission contract and name
    val (permissionContract, permissionName) = remember { fs.permissionContract() }

    // Permission launcher - launches folder picker after permission is granted
    val permissionLauncher = rememberLauncherForActivityResult(contract = permissionContract) { granted ->
        if (granted) {
            folderPickerLauncher.launch(null)
        }
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
                trailingIcon = {
                    IconButton(
                        onClick = {
                            // Check if storage permission is granted
                            if (fs.hasStoragePermission()) {
                                folderPickerLauncher.launch(null)
                            } else {
                                // Request storage permission
                                permissionLauncher.launch(permissionName)
                            }
                        },
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
                                text = stringResource(R.string.morphe_header_instructions_title),
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

                    // Expandable Content
                    AnimatedVisibility(
                        visible = showInstructions,
                        enter = expandVertically(
                            animationSpec = tween(durationMillis = 300)
                        ) + fadeIn(
                            animationSpec = tween(durationMillis = 300)
                        ),
                        exit = shrinkVertically(
                            animationSpec = tween(durationMillis = 300)
                        ) + fadeOut(
                            animationSpec = tween(durationMillis = 300)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                                .heightIn(max = 300.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            HorizontalDivider(
                                color = LocalDialogTextColor.current.copy(alpha = 0.1f),
                                modifier = Modifier.padding(bottom = 4.dp)
                            )

                            when {
                                isLoading -> {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 16.dp),
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            color = MaterialTheme.colorScheme.primary,
                                            strokeWidth = 2.dp
                                        )
                                    }
                                }
                                headerDescription != null -> {
                                    Text(
                                        text = headerDescription!!,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = LocalDialogSecondaryTextColor.current,
                                        lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.4f
                                    )
                                }
                                else -> {
                                    Text(
                                        text = stringResource(R.string.morphe_header_instructions_unavailable),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = LocalDialogSecondaryTextColor.current.copy(alpha = 0.7f),
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
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
