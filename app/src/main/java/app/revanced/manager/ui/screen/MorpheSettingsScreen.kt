package app.revanced.manager.ui.screen

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.BuildConfig
import app.morphe.manager.R
import app.revanced.manager.network.downloader.DownloaderPluginState
import app.revanced.manager.ui.component.ExceptionViewerDialog
import app.revanced.manager.ui.component.PasswordField
import app.revanced.manager.ui.theme.Theme
import app.revanced.manager.ui.viewmodel.AboutViewModel
import app.revanced.manager.ui.viewmodel.DashboardViewModel
import app.revanced.manager.ui.viewmodel.DownloadsViewModel
import app.revanced.manager.ui.viewmodel.GeneralSettingsViewModel
import app.revanced.manager.ui.viewmodel.ImportExportViewModel
import app.revanced.manager.util.toColorOrNull
import app.revanced.manager.util.toast
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel

/**
 * Morphe Settings Screen
 */
@SuppressLint("BatteryLife")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MorpheSettingsScreen(
    onBackClick: () -> Unit,
    generalViewModel: GeneralSettingsViewModel = koinViewModel(),
    downloadsViewModel: DownloadsViewModel = koinViewModel(),
    importExportViewModel: ImportExportViewModel = koinViewModel(),
    dashboardViewModel: DashboardViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    // Appearance
    val theme by generalViewModel.prefs.theme.getAsState()
    val pureBlackTheme by generalViewModel.prefs.pureBlackTheme.getAsState()
    val dynamicColor by generalViewModel.prefs.dynamicColor.getAsState()
    val customAccentColorHex by generalViewModel.prefs.customAccentColor.getAsState()
    val customThemeColorHex by generalViewModel.prefs.customThemeColor.getAsState()

    // Plugins
    val pluginStates by downloadsViewModel.downloaderPluginStates.collectAsStateWithLifecycle()

    // Dialog states
    var showAboutDialog by rememberSaveable { mutableStateOf(false) }
    var showPluginDialog by rememberSaveable { mutableStateOf<String?>(null) }
    var showKeystoreCredentialsDialog by rememberSaveable { mutableStateOf(false) }

    // Keystore import launcher
    val importKeystoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            importExportViewModel.startKeystoreImport(it)
        }
    }

    // Keystore export launcher
    val exportKeystoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        uri?.let { importExportViewModel.exportKeystore(it) }
    }

    // Show keystore credentials dialog when needed
    LaunchedEffect(importExportViewModel.showCredentialsDialog) {
        showKeystoreCredentialsDialog = importExportViewModel.showCredentialsDialog
    }

    // Show about dialog
    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
    }

    // Show plugin management dialog
    showPluginDialog?.let { packageName ->
        val state = pluginStates[packageName]
        var showExceptionViewer by remember { mutableStateOf(false) }

        if (showExceptionViewer && state is DownloaderPluginState.Failed) {
            ExceptionViewerDialog(
                text = state.throwable.stackTraceToString(),
                onDismiss = { showExceptionViewer = false }
            )
        } else {
            PluginActionDialog(
                packageName = packageName,
                state = state,
                onDismiss = { showPluginDialog = null },
                onTrust = { downloadsViewModel.trustPlugin(packageName) },
                onRevoke = { downloadsViewModel.revokePluginTrust(packageName) },
                onUninstall = { downloadsViewModel.uninstallPlugin(packageName) },
                onViewError = { showExceptionViewer = true }
            )
        }
    }

    // Show keystore credentials dialog
    if (showKeystoreCredentialsDialog) {
        KeystoreCredentialsDialog(
            onDismiss = {
                importExportViewModel.cancelKeystoreImport()
                showKeystoreCredentialsDialog = false
            },
            onSubmit = { alias, pass ->
                coroutineScope.launch {
                    val result = importExportViewModel.tryKeystoreImport(alias, pass)
                    if (result) {
                        showKeystoreCredentialsDialog = false
                    } else {
                        context.toast(context.getString(R.string.import_keystore_wrong_credentials))
                    }
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(vertical = 8.dp)
        ) {
            // Appearance Section
            SectionHeader(
                icon = Icons.Outlined.Palette,
                title = stringResource(R.string.appearance)
            )

            SettingsCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Interface switcher
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                coroutineScope.launch {
                                    generalViewModel.prefs.useMorpheHomeScreen.update(false)
                                }
                                // Navigate back and let the app switch to original interface
                                onBackClick()
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.SwapHoriz,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.morphe_settings_return_to_advanced),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = stringResource(R.string.morphe_settings_return_to_advanced_description),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.Outlined.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Text(
                        text = stringResource(R.string.theme),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ThemeOption(
                            theme = Theme.SYSTEM,
                            icon = Icons.Outlined.PhoneAndroid,
                            label = stringResource(R.string.system),
                            selected = theme == Theme.SYSTEM,
                            onClick = { generalViewModel.setTheme(Theme.SYSTEM) },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeOption(
                            theme = Theme.LIGHT,
                            icon = Icons.Outlined.LightMode,
                            label = stringResource(R.string.light),
                            selected = theme == Theme.LIGHT,
                            onClick = { generalViewModel.setTheme(Theme.LIGHT) },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeOption(
                            theme = Theme.DARK,
                            icon = Icons.Outlined.DarkMode,
                            label = stringResource(R.string.dark),
                            selected = theme == Theme.DARK,
                            onClick = { generalViewModel.setTheme(Theme.DARK) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    AnimatedVisibility(visible = theme != Theme.LIGHT) {
                        Column(modifier = Modifier.padding(top = 16.dp)) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        coroutineScope.launch {
                                            generalViewModel.prefs.pureBlackTheme
                                                .update(!pureBlackTheme)
                                        }
                                    },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Contrast,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.pure_black_theme),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = stringResource(R.string.pure_black_theme_description),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = pureBlackTheme,
                                        onCheckedChange = {
                                            coroutineScope.launch {
                                                generalViewModel.prefs.pureBlackTheme.update(it)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Dynamic Color (Android 12+)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Spacer(modifier = Modifier.height(16.dp))

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    coroutineScope.launch {
                                        generalViewModel.prefs.dynamicColor.update(!dynamicColor)
                                    }
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Palette,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.dynamic_color),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = stringResource(R.string.dynamic_color_description),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = dynamicColor,
                                    onCheckedChange = {
                                        coroutineScope.launch {
                                            generalViewModel.prefs.dynamicColor.update(it)
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // Accent Color Presets
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = stringResource(R.string.accent_color_presets),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    val accentPresets = remember {
                        listOf(
                            Color(0xFF6750A4),
                            Color(0xFF386641),
                            Color(0xFF0061A4),
                            Color(0xFF8E24AA),
                            Color(0xFFEF6C00),
                            Color(0xFF00897B),
                            Color(0xFFD81B60),
                            Color(0xFF5C6BC0),
                            Color(0xFF43A047),
                            Color(0xFFFF7043),
                            Color(0xFF1DE9B6),
                            Color(0xFFFFC400),
                            Color(0xFF00B8D4),
                            Color(0xFFBA68C8)
                        )
                    }

                    val selectedAccentArgb = customAccentColorHex.toColorOrNull()?.toArgb()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Reset button (no color selected)
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .border(
                                    width = if (selectedAccentArgb == null) 2.dp else 1.dp,
                                    color = if (selectedAccentArgb == null)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.outline,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                .clickable {
                                    generalViewModel.setCustomAccentColor(null)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Reset",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        accentPresets.forEach { preset ->
                            val isSelected = selectedAccentArgb != null && preset.toArgb() == selectedAccentArgb
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.outline,
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .background(preset, RoundedCornerShape(12.dp))
                                    .clickable {
                                        generalViewModel.setCustomAccentColor(preset)
                                    }
                            )
                        }
                    }

                    // Theme Color Presets
                    Spacer(modifier = Modifier.height(16.dp))

                    val isSystemInDarkTheme = isSystemInDarkTheme()
                    val isDarkTheme = when (theme) {
                        Theme.LIGHT -> false
                        Theme.DARK -> true
                        Theme.SYSTEM -> isSystemInDarkTheme
                    }

                    LaunchedEffect(isDarkTheme) {
                        if (isDarkTheme && customThemeColorHex.toColorOrNull() != null) {
                            generalViewModel.setCustomThemeColor(null)
                        }
                    }

                    AnimatedVisibility(visible = !isDarkTheme) {
                        Column {
                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = stringResource(R.string.theme_color),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            val themePresets = remember {
                                listOf(
                                    Color(0xFF1C1B1F),
                                    Color(0xFF2D2A32),
                                    Color(0xFF1A1A2E),
                                    Color(0xFF0F0F1E),
                                    Color(0xFF16213E),
                                    Color(0xFF1F1B24),
                                    Color(0xFF0A1929),
                                    Color(0xFF1B1B2F),
                                    Color(0xFF162447),
                                    Color(0xFF1F1D2B),
                                    Color(0xFF2C2C54),
                                    Color(0xFF1E1E2E)
                                )
                            }

                            val selectedThemeArgb = customThemeColorHex.toColorOrNull()?.toArgb()

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Кнопка скидання
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .border(
                                            width = if (selectedThemeArgb == null) 2.dp else 1.dp,
                                            color = if (selectedThemeArgb == null)
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.outline,
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                        .clickable {
                                            generalViewModel.setCustomThemeColor(null)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = "Reset",
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                themePresets.forEach { preset ->
                                    val isSelected = selectedThemeArgb != null && preset.toArgb() == selectedThemeArgb
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .border(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = if (isSelected)
                                                    MaterialTheme.colorScheme.primary
                                                else
                                                    MaterialTheme.colorScheme.outline,
                                                shape = RoundedCornerShape(14.dp)
                                            )
                                            .background(preset, RoundedCornerShape(12.dp))
                                            .clickable {
                                                generalViewModel.setCustomThemeColor(preset)
                                            }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Updates Section
            SectionHeader(
                icon = Icons.Outlined.Update,
                title = stringResource(R.string.updates)
            )

            SettingsCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    val usePrereleases by generalViewModel.prefs.usePatchesPrereleases.getAsState()

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                coroutineScope.launch {
                                    val newValue = !usePrereleases

                                    // Update the preference
                                    generalViewModel.togglePatchesPrerelease(newValue)

                                    // Show toast about preference change
                                    context.toast(
                                        if (newValue)
                                            context.getString(R.string.morphe_update_patches_prerelease_enabled)
                                        else
                                            context.getString(R.string.morphe_update_patches_prerelease_disabled)
                                    )

                                    // Wait a bit for the preference to propagate
                                    delay(300)

                                    // Silently update the official bundle in background
                                    withContext(Dispatchers.IO) {
                                        dashboardViewModel.patchBundleRepository.updateOfficialBundle(
                                            showProgress = false, // Don't show progress
                                            showToast = false     // Don't show toast
                                        )
                                    }
                                }
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Science,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.morphe_update_use_patches_prereleases),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = stringResource(R.string.morphe_update_use_patches_prereleases_description),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = usePrereleases,
                                onCheckedChange = { newValue ->
                                    coroutineScope.launch {
                                        // Update the preference
                                        generalViewModel.togglePatchesPrerelease(newValue)

                                        // Show toast about preference change
                                        context.toast(
                                            if (newValue)
                                                context.getString(R.string.morphe_update_patches_prerelease_enabled)
                                            else
                                                context.getString(R.string.morphe_update_patches_prerelease_disabled)
                                        )

                                        // Wait a bit for the preference to propagate
                                        delay(300)

                                        // Silently update the official bundle in background
                                        withContext(Dispatchers.IO) {
                                            dashboardViewModel.patchBundleRepository.updateOfficialBundle(
                                                showProgress = false, // Don't show progress
                                                showToast = false     // Don't show toast
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }

            if (false) {
                Spacer(modifier = Modifier.height(24.dp))

                // Plugins Section
                SectionHeader(
                    icon = Icons.Filled.Download,
                    title = stringResource(R.string.downloader_plugins)
                )

                SettingsCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (pluginStates.isEmpty()) {
                            Text(
                                text = stringResource(R.string.downloader_no_plugins_installed),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            pluginStates.forEach { (packageName, state) ->
                                PluginItem(
                                    packageName = packageName,
                                    state = state,
                                    onClick = { showPluginDialog = packageName },
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Import & Export Section
            SectionHeader(
                icon = Icons.Outlined.Build,
                title = stringResource(R.string.import_export)
            )

            SettingsCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Keystore Import
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { importKeystoreLauncher.launch("*/*") },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.Key,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.import_keystore),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = stringResource(R.string.import_keystore_description),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                Icons.Outlined.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Keystore Export
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                if (!importExportViewModel.canExport()) {
                                    context.toast(context.getString(R.string.export_keystore_unavailable))
                                } else {
                                    exportKeystoreLauncher.launch("Morphe.keystore")
                                }
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.Upload,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.export_keystore),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = stringResource(R.string.export_keystore_description),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                Icons.Outlined.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (dashboardViewModel.rootInstaller?.requestRootAccessIfNotAskedYet(context) == true) {
                // Debugging Section
                SectionHeader(
                    icon = Icons.Outlined.DeveloperMode,
                    title = stringResource(R.string.debugging)
                )

                SettingsCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val useRootMode by generalViewModel.prefs.useRootMode.getAsState()

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    coroutineScope.launch {
                                        val newValue = !useRootMode
                                        generalViewModel.toggleRootMode(newValue)
                                        context.toast(
                                            if (newValue)
                                                context.getString(R.string.morphe_root_mode_enabled)
                                            else
                                                context.getString(R.string.morphe_root_mode_disabled)
                                        )
                                    }
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Security,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.morphe_root_mode),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = stringResource(R.string.morphe_root_mode_description),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = useRootMode,
                                    onCheckedChange = { newValue ->
                                        coroutineScope.launch {
                                            generalViewModel.toggleRootMode(newValue)
                                            context.toast(
                                                if (newValue)
                                                    context.getString(R.string.morphe_root_mode_enabled)
                                                else
                                                    context.getString(R.string.morphe_root_mode_disabled)
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // About Section
            SectionHeader(
                icon = Icons.Outlined.Info,
                title = stringResource(R.string.about)
            )

            SettingsCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    // About item
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showAboutDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val icon = rememberDrawablePainter(
                                drawable = remember {
                                    AppCompatResources.getDrawable(context, R.mipmap.ic_launcher)
                                }
                            )
                            Image(
                                painter = icon,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.app_name),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Version ${BuildConfig.VERSION_NAME}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                Icons.Outlined.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Share Website
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                // Share website functionality
                                try {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, "https://morphe.software")
                                    }
                                    context.startActivity(
                                        Intent.createChooser(
                                            shareIntent,
                                            context.getString(R.string.morphe_share_website)
                                        )
                                    )
                                } catch (e: Exception) {
                                    context.toast("Failed to share website: ${e.message}")
                                }
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.Language,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.morphe_share_website),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = stringResource(R.string.morphe_share_website_description),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                Icons.Outlined.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Section header with icon and title. Used to visually separate different settings categories
 */
@Composable
private fun SectionHeader(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Card wrapper for settings sections. Provides consistent styling across all settings groups
 */
@Composable
private fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        content()
    }
}

/**
 * Individual theme selection button. Displays icon and label with visual feedback for selected state
 */
@Composable
private fun ThemeOption(
    theme: Theme,
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(80.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline
            }
        ),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}

/**
 * Individual plugin list item. Shows plugin name, status icon, and allows clicking for management
 */
@Composable
private fun PluginItem(
    packageName: String,
    state: DownloaderPluginState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val packageInfo = remember(packageName) {
        try {
            context.packageManager.getPackageInfo(packageName, 0)
        } catch (e: Exception) {
            null
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (state) {
                    is DownloaderPluginState.Loaded -> Icons.Outlined.CheckCircle
                    is DownloaderPluginState.Failed -> Icons.Outlined.Error
                    is DownloaderPluginState.Untrusted -> Icons.Outlined.Warning
                },
                contentDescription = null,
                tint = when (state) {
                    is DownloaderPluginState.Loaded -> MaterialTheme.colorScheme.primary
                    is DownloaderPluginState.Failed -> MaterialTheme.colorScheme.error
                    is DownloaderPluginState.Untrusted -> MaterialTheme.colorScheme.tertiary
                },
                modifier = Modifier.size(24.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = packageInfo?.applicationInfo?.loadLabel(context.packageManager)?.toString()
                        ?: packageName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = stringResource(
                        when (state) {
                            is DownloaderPluginState.Loaded -> R.string.downloader_plugin_state_trusted
                            is DownloaderPluginState.Failed -> R.string.downloader_plugin_state_failed
                            is DownloaderPluginState.Untrusted -> R.string.downloader_plugin_state_untrusted
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Plugin management dialog for managing plugins with signature display
 */
@Composable
private fun PluginActionDialog(
    packageName: String,
    state: DownloaderPluginState?,
    onDismiss: () -> Unit,
    onTrust: () -> Unit,
    onRevoke: () -> Unit,
    onUninstall: () -> Unit,
    onViewError: () -> Unit
) {
    val context = LocalContext.current
    val pm = remember { context.packageManager }

    val signature = remember(packageName) {
        runCatching {
            val androidSignature = pm.getPackageInfo(
                packageName,
                android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
            ).signingInfo?.apkContentsSigners?.firstOrNull()

            if (androidSignature != null) {
                val hash = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(androidSignature.toByteArray())
                hash.joinToString(":") { "%02X".format(it) }
            } else {
                "Unknown"
            }
        }.getOrNull() ?: "Unknown"
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Icon
                Surface(
                    shape = CircleShape,
                    color = when (state) {
                        is DownloaderPluginState.Loaded -> MaterialTheme.colorScheme.primaryContainer
                        is DownloaderPluginState.Failed -> MaterialTheme.colorScheme.errorContainer
                        is DownloaderPluginState.Untrusted -> MaterialTheme.colorScheme.tertiaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = when (state) {
                                is DownloaderPluginState.Loaded -> Icons.Outlined.CheckCircle
                                is DownloaderPluginState.Failed -> Icons.Outlined.Error
                                is DownloaderPluginState.Untrusted -> Icons.Outlined.Warning
                                else -> Icons.Outlined.Info
                            },
                            contentDescription = null,
                            tint = when (state) {
                                is DownloaderPluginState.Loaded -> MaterialTheme.colorScheme.onPrimaryContainer
                                is DownloaderPluginState.Failed -> MaterialTheme.colorScheme.onErrorContainer
                                is DownloaderPluginState.Untrusted -> MaterialTheme.colorScheme.onTertiaryContainer
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // Title
                Text(
                    text = when (state) {
                        is DownloaderPluginState.Loaded -> stringResource(R.string.downloader_plugin_revoke_trust_dialog_title)
                        is DownloaderPluginState.Failed -> stringResource(R.string.downloader_plugin_state_failed)
                        is DownloaderPluginState.Untrusted -> stringResource(R.string.downloader_plugin_trust_dialog_title)
                        else -> packageName
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                // Content
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when (state) {
                        is DownloaderPluginState.Failed -> {
                            Text(
                                text = stringResource(R.string.downloader_plugin_failed_dialog_body, packageName),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        else -> {
                            Text(
                                text = "Package:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = packageName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "Signature (SHA-256):",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = signature,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Buttons - two rows
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // First row: Action and Uninstall
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                    ) {
                        // Action button (Trust/Revoke/View Error)
                        when (state) {
                            is DownloaderPluginState.Loaded -> {
                                FilledTonalButton(
                                    onClick = {
                                        onRevoke()
                                        onDismiss()
                                    }
                                ) {
                                    Text(stringResource(R.string.continue_))
                                }
                            }
                            is DownloaderPluginState.Untrusted -> {
                                FilledTonalButton(
                                    onClick = {
                                        onTrust()
                                        onDismiss()
                                    }
                                ) {
                                    Text(stringResource(R.string.continue_))
                                }
                            }
                            is DownloaderPluginState.Failed -> {
                                FilledTonalButton(onClick = onViewError) {
                                    Text(stringResource(R.string.downloader_plugin_view_error))
                                }
                            }
                            else -> {}
                        }

                        // Uninstall button
                        OutlinedButton(
                            onClick = {
                                onUninstall()
                                onDismiss()
                            }
                        ) {
                            Text(stringResource(R.string.uninstall))
                        }
                    }

                    // Second row: Dismiss button
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(0.5f)
                    ) {
                        Text(stringResource(R.string.dismiss))
                    }
                }
            }
        }
    }
}

/**
 * About dialog. Shows app icon, version, description, and links
 */
@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // App Icon with gradient background
                Box(
                    modifier = Modifier.size(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primaryContainer,
                                            MaterialTheme.colorScheme.secondaryContainer
                                        )
                                    )
                                )
                        )
                    }
                    val icon = rememberDrawablePainter(
                        drawable = remember {
                            AppCompatResources.getDrawable(context, R.mipmap.ic_launcher)
                        }
                    )
                    Image(
                        painter = icon,
                        contentDescription = null,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                }

                // App Name & Version
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Version ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Description
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.revanced_manager_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = 26.sp,
                        )
                    }
                }

                // Social Links
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AboutViewModel.socials.forEach { link ->
                        SocialIconButton(
                            icon = AboutViewModel.getSocialIcon(link.name),
                            contentDescription = link.name,
                            onClick = { uriHandler.openUri(link.url) }
                        )
                    }
                }

                // Close button
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    FilledTonalButton(onClick = onDismiss) {
                        Text(stringResource(R.string.close))
                    }
                }
            }
        }
    }
}

/**
 * Social link button. Styled button for opening social media links
 */
@Composable
private fun SocialIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Surface(
        onClick = onClick,
        modifier = Modifier.size(56.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = if (isPressed) 12.dp else 4.dp,
        shadowElevation = if (isPressed) 8.dp else 2.dp,
        interactionSource = interactionSource
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

/**
 * Keystore Credentials Dialog. Allows entering alias and password for keystore import
 */
@Composable
private fun KeystoreCredentialsDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit
) {
    var alias by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Icon
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.Key,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // Title
                Text(
                    text = stringResource(R.string.import_keystore_dialog_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                // Description
                Text(
                    text = stringResource(R.string.import_keystore_dialog_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                // Alias Input
                OutlinedTextField(
                    value = alias,
                    onValueChange = { alias = it },
                    label = { Text(stringResource(R.string.import_keystore_dialog_alias_field)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Password Input
                PasswordField(
                    value = pass,
                    onValueChange = { pass = it },
                    label = { Text(stringResource(R.string.import_keystore_dialog_password_field)) },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Import button
                    FilledTonalButton(
                        onClick = { onSubmit(alias, pass) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            stringResource(R.string.import_keystore_dialog_button),
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Cancel button
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            stringResource(R.string.cancel),
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
