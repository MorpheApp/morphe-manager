package app.revanced.manager.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.ExperimentalFoundationApi
import android.content.pm.PackageInfo
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.revanced.manager.data.platform.Filesystem
import app.revanced.manager.ui.component.AppIcon
import app.revanced.manager.ui.component.AppLabel
import app.revanced.manager.ui.component.AppTopBar
import app.revanced.manager.ui.component.CheckedFilterChip
import app.revanced.manager.ui.component.LazyColumnWithScrollbar
import app.revanced.manager.ui.component.LoadingIndicator
import app.revanced.manager.ui.component.NonSuggestedVersionDialog
import app.revanced.manager.ui.component.patches.PathSelectorDialog
import app.revanced.manager.ui.component.SafeguardHintCard
import app.revanced.manager.ui.component.SearchView
import app.revanced.manager.ui.model.SelectedApp
import app.revanced.manager.ui.viewmodel.AppSelectorViewModel
import app.revanced.manager.ui.viewmodel.BundleVersionSuggestion
import app.revanced.manager.domain.manager.PreferencesManager
import app.revanced.manager.util.EventEffect
import app.revanced.manager.util.isAllowedApkFile
import app.revanced.manager.util.consumeHorizontalScroll
import app.revanced.manager.util.openUrl
import java.io.File
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSelectorScreen(
    onSelect: (String) -> Unit,
    onStorageSelect: (SelectedApp.Local) -> Unit,
    onBackClick: () -> Unit,
    autoOpenStorage: Boolean = false,
    returnToDashboardOnStorage: Boolean = false,
    vm: AppSelectorViewModel = koinViewModel()
) {
    val prefs = koinInject<PreferencesManager>()
    val fs = koinInject<Filesystem>()
    val storageRoots = remember { fs.storageRoots() }
    val allowIncompatiblePatches by prefs.disablePatchVersionCompatCheck.getAsState()
    val suggestedVersionSafeguard by prefs.suggestedVersionSafeguard.getAsState()
    val bundleRecommendationsEnabled = allowIncompatiblePatches && !suggestedVersionSafeguard

    EventEffect(flow = vm.storageSelectionFlow) {
        onStorageSelect(it)
        if (returnToDashboardOnStorage) {
            onBackClick()
        }
    }

    var showStorageDialog by rememberSaveable { mutableStateOf(false) }
    val (permissionContract, permissionName) = remember { fs.permissionContract() }
    val permissionLauncher =
        rememberLauncherForActivityResult(permissionContract) { granted ->
            if (granted) {
                showStorageDialog = true
            } else if (returnToDashboardOnStorage) {
                onBackClick()
            }
        }
    val openStoragePicker = {
        if (fs.hasStoragePermission()) {
            showStorageDialog = true
        } else {
            permissionLauncher.launch(permissionName)
        }
    }
    val quickStorageOnly = autoOpenStorage && returnToDashboardOnStorage
    LaunchedEffect(autoOpenStorage) {
        if (autoOpenStorage) {
            openStoragePicker()
        }
    }

    if (quickStorageOnly) {
        // Skip rendering the selector UI; just trigger the picker and wait for result/back navigation.
        androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize())
        return
    }

    if (showStorageDialog) {
        PathSelectorDialog(
            roots = storageRoots,
            onSelect = { path ->
                showStorageDialog = false
                path?.let { vm.handleStorageFile(File(it.toString())) }
                if (path == null && returnToDashboardOnStorage) {
                    onBackClick()
                }
            },
            fileFilter = ::isAllowedApkFile,
            allowDirectorySelection = false
        )
    }

    val suggestedVersions by vm.suggestedAppVersions.collectAsStateWithLifecycle(emptyMap())
    val bundleSuggestionsByApp by vm.bundleSuggestionsByApp.collectAsStateWithLifecycle(emptyMap())

    var filterText by rememberSaveable { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf(false) }
    var filterInstalledOnly by rememberSaveable { mutableStateOf(false) }
    var filterPatchesAvailable by rememberSaveable { mutableStateOf(false) }
    var showFilterMenu by rememberSaveable { mutableStateOf(false) }

    val appList by vm.appList.collectAsStateWithLifecycle(initialValue = emptyList())
    val filteredAppList = remember(appList, filterText, filterInstalledOnly, filterPatchesAvailable) {
        appList
            .asSequence()
            .filter { app ->
                if (filterInstalledOnly && app.packageInfo == null) return@filter false
                if (filterPatchesAvailable && (app.patches ?: 0) <= 0) return@filter false
                true
            }
            .filter { app ->
                if (filterText.isBlank()) return@filter true
                (vm.loadLabel(app.packageInfo)).contains(filterText, true) ||
                        app.packageName.contains(filterText, true)
            }
            .toList()
    }

    vm.nonSuggestedVersionDialogSubject?.let {
        NonSuggestedVersionDialog(
            suggestedVersion = suggestedVersions[it.packageName].orEmpty(),
            onDismiss = vm::dismissNonSuggestedVersionDialog
        )
    }

    if (search)
        SearchView(
            query = filterText,
            onQueryChange = { filterText = it },
            onActiveChange = { search = it },
            placeholder = { Text(stringResource(R.string.search_apps)) }
        ) {
            if (filteredAppList.isNotEmpty() && filterText.isNotEmpty()) {
                LazyColumnWithScrollbar(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = filteredAppList,
                        key = { it.packageName }
                    ) { app ->
                        AppSelectorCard(
                            packageInfo = app.packageInfo,
                            packageName = app.packageName,
                            patchCount = app.patches,
                            onClick = { onSelect(app.packageName) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = stringResource(R.string.search),
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = stringResource(R.string.type_anything),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.select_app),
                scrollBehavior = scrollBehavior,
                onBackClick = onBackClick,
                actions = {
                    Box {
                        val filterTint =
                            if (filterInstalledOnly || filterPatchesAvailable) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        IconButton(onClick = { showFilterMenu = true }) {
                            Icon(
                                imageVector = Icons.Outlined.FilterList,
                                contentDescription = stringResource(R.string.app_filter_title),
                                tint = filterTint
                            )
                        }
                        DropdownMenu(
                            expanded = showFilterMenu,
                            onDismissRequest = { showFilterMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.app_filter_installed_only)) },
                                trailingIcon = {
                                    if (filterInstalledOnly) Icon(Icons.Filled.Check, null)
                                },
                                onClick = {
                                    filterInstalledOnly = !filterInstalledOnly
                                    showFilterMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.app_filter_patches_available)) },
                                trailingIcon = {
                                    if (filterPatchesAvailable) Icon(Icons.Filled.Check, null)
                                },
                                onClick = {
                                    filterPatchesAvailable = !filterPatchesAvailable
                                    showFilterMenu = false
                                }
                            )
                        }
                    }
                    IconButton(onClick = { search = true }) {
                        Icon(Icons.Outlined.Search, stringResource(R.string.search))
                    }
                }
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { paddingValues ->
        LazyColumnWithScrollbar(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "app-selector-actions") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SelectFromStorageCard(onClick = openStoragePicker)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CheckedFilterChip(
                            selected = filterInstalledOnly,
                            onClick = { filterInstalledOnly = !filterInstalledOnly },
                            label = { Text(stringResource(R.string.app_filter_installed_only)) }
                        )
                        CheckedFilterChip(
                            selected = filterPatchesAvailable,
                            onClick = { filterPatchesAvailable = !filterPatchesAvailable },
                            label = { Text(stringResource(R.string.app_filter_patches_available)) }
                        )
                    }
                }
            }

            if (appList.isNotEmpty()) {
                items(
                    items = filteredAppList,
                    key = { it.packageName }
                ) { app ->
                    AppSelectorCard(
                        packageInfo = app.packageInfo,
                        packageName = app.packageName,
                        patchCount = app.patches,
                        onClick = { onSelect(app.packageName) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val bundleSuggestions = bundleSuggestionsByApp[app.packageName].orEmpty()
                        var expanded by rememberSaveable(app.packageName) { mutableStateOf(false) }
                        var dialogBundleUid by remember { mutableStateOf<Int?>(null) }

                        LaunchedEffect(bundleRecommendationsEnabled) {
                            if (!bundleRecommendationsEnabled) {
                                expanded = false
                                dialogBundleUid = null
                            }
                        }

                        if (bundleSuggestions.isNotEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                val toggleLabel = stringResource(
                                    if (expanded) R.string.hide_suggested_versions
                                    else R.string.show_suggested_versions
                                )
                                TextButton(
                                    onClick = { expanded = !expanded },
                                    modifier = Modifier.align(Alignment.Start),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ChevronRight,
                                        contentDescription = null
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = toggleLabel,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (bundleRecommendationsEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (expanded) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        if (!bundleRecommendationsEnabled) {
                                            SafeguardHintCard(
                                                title = stringResource(R.string.bundle_version_dialog_locked_title),
                                                description = stringResource(R.string.bundle_version_dialog_locked_hint),
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }

                                        bundleSuggestions.forEach { suggestion ->
                                            BundleSuggestionCard(
                                                suggestion = suggestion,
                                                packageName = app.packageName,
                                                enabled = bundleRecommendationsEnabled,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .widthIn(max = 560.dp)
                                                    .alpha(if (bundleRecommendationsEnabled) 1f else 0.6f),
                                                onShowOtherVersions = {
                                                    if (bundleRecommendationsEnabled) {
                                                        dialogBundleUid = suggestion.bundleUid
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            if (dialogBundleUid != null && bundleRecommendationsEnabled) {
                                bundleSuggestions
                                    .firstOrNull { it.bundleUid == dialogBundleUid }
                                    ?.let { suggestion ->
                                        OtherSupportedVersionsInfoDialog(
                                            bundleName = suggestion.bundleName,
                                            packageName = app.packageName,
                                            recommendedVersion = suggestion.recommendedVersion,
                                            otherVersions = suggestion.otherSupportedVersions,
                                            supportsAllVersions = suggestion.supportsAllVersions,
                                            onDismissRequest = { dialogBundleUid = null }
                                        )
                                    }
                            } else if (dialogBundleUid != null) {
                                dialogBundleUid = null
                            }
                        }
                    }
                }
                if (filteredAppList.isEmpty()) {
                    item(key = "app-selector-empty") {
                        Column(
                            modifier = Modifier
                                .fillParentMaxSize()
                                .padding(32.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = stringResource(R.string.app_filter_no_results),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                item {
                    Box(
                        modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center
                    ) {
                        LoadingIndicator()
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectFromStorageCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                contentColor = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(R.string.select_from_storage),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(R.string.select_from_storage_description),
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

@Composable
private fun AppSelectorCard(
    packageInfo: PackageInfo?,
    packageName: String,
    patchCount: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: (@Composable () -> Unit)? = null
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIcon(
                    packageInfo = packageInfo,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AppLabel(
                        packageInfo = packageInfo,
                        defaultText = packageName,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                patchCount?.takeIf { it > 0 }?.let { count ->
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text(
                            text = pluralStringResource(R.plurals.patch_count, count, count),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            content?.invoke()
        }
    }
}

@Composable
private fun VersionSearchRow(
    label: String,
    packageName: String,
    version: String?,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        VersionSearchChip(
            label = label,
            packageName = packageName,
            version = version,
            highlighted = highlighted
        )
    }
}

@Composable
private fun VersionSearchChip(
    label: String,
    packageName: String,
    version: String?,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    val context = LocalContext.current
    val background = if (highlighted) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
    }
    val contentColor = if (highlighted) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        onClick = { context.openUrl(buildSearchUrl(packageName, version)) },
        modifier = modifier.widthIn(max = 220.dp),
        shape = RoundedCornerShape(999.dp),
        color = background,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = stringResource(R.string.search),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

private fun buildSearchUrl(packageName: String, version: String?): String {
    val encodedPackage = Uri.encode(packageName)
    val encodedVersion = version?.takeIf { it.isNotBlank() }?.let(Uri::encode)
    return if (encodedVersion == null) {
        "https://www.google.com/search?q=$encodedPackage"
    } else {
        "https://www.google.com/search?q=$encodedPackage+$encodedVersion"
    }
}

@Composable
private fun OtherSupportedVersionsInfoDialog(
    bundleName: String,
    packageName: String,
    recommendedVersion: String?,
    otherVersions: List<String>,
    supportsAllVersions: Boolean,
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.close))
            }
        },
        title = { Text(stringResource(R.string.other_supported_versions_title, bundleName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                recommendedVersion?.let { version ->
                    VersionSearchRow(
                        label = stringResource(
                            R.string.bundle_version_suggested_label,
                            stringResource(R.string.version_label, version)
                        ),
                        packageName = packageName,
                        version = version,
                        modifier = Modifier.align(Alignment.Start),
                        highlighted = true
                    )
                }
                when {
                    otherVersions.isNotEmpty() -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            otherVersions.chunked(2).forEach { row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    row.forEach { version ->
                                        VersionSearchRow(
                                            label = stringResource(R.string.version_label, version),
                                            packageName = packageName,
                                            version = version,
                                            modifier = Modifier
                                                .weight(1f)
                                                .wrapContentWidth(Alignment.Start)
                                        )
                                    }
                                    if (row.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                    supportsAllVersions -> {
                        VersionSearchRow(
                            label = stringResource(R.string.other_supported_versions_all),
                            packageName = packageName,
                            version = null,
                            modifier = Modifier.align(Alignment.Start),
                            highlighted = true
                        )
                    }
                    else -> {
                        Text(
                            stringResource(R.string.other_supported_versions_empty),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BundleSuggestionCard(
    suggestion: BundleVersionSuggestion,
    packageName: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onShowOtherVersions: () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val nameScrollState = rememberScrollState()
            Text(
                suggestion.bundleName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .consumeHorizontalScroll(nameScrollState)
                    .horizontalScroll(nameScrollState)
            )
            val versionLabel = suggestion.recommendedVersion
                ?.let { stringResource(R.string.version_label, it) }
                ?: stringResource(R.string.bundle_version_all_versions)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (suggestion.recommendedVersion != null) {
                    VersionSearchChip(
                        label = versionLabel,
                        packageName = packageName,
                        version = suggestion.recommendedVersion,
                        modifier = Modifier,
                        highlighted = true
                    )
                } else if (suggestion.supportsAllVersions) {
                    VersionSearchChip(
                        label = versionLabel,
                        packageName = packageName,
                        version = null,
                        modifier = Modifier,
                        highlighted = true
                    )
                }
            }
            Text(
                text = if (suggestion.supportsAllVersions) {
                    stringResource(R.string.other_supported_versions_all)
                } else {
                    stringResource(R.string.bundle_version_dialog_recommended, versionLabel)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(
                onClick = onShowOtherVersions,
                enabled = enabled,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.textButtonColors(
                    containerColor = if (enabled) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    },
                    contentColor = if (enabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.clip(RoundedCornerShape(50))
            ) {
                Text(
                    text = stringResource(R.string.show_other_versions),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
