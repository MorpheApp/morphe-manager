package app.revanced.manager.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
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
import app.revanced.manager.ui.component.AppIcon
import app.revanced.manager.ui.component.AppLabel
import app.revanced.manager.ui.component.AppTopBar
import app.revanced.manager.ui.component.LazyColumnWithScrollbar
import app.revanced.manager.ui.component.LoadingIndicator
import app.revanced.manager.ui.component.NonSuggestedVersionDialog
import app.revanced.manager.ui.component.SafeguardHintCard
import app.revanced.manager.ui.component.SearchView
import app.revanced.manager.ui.model.SelectedApp
import app.revanced.manager.ui.viewmodel.AppSelectorViewModel
import app.revanced.manager.ui.viewmodel.BundleVersionSuggestion
import app.revanced.manager.domain.manager.PreferencesManager
import app.revanced.manager.util.APK_MIMETYPE
import app.revanced.manager.util.EventEffect
import app.revanced.manager.util.consumeHorizontalScroll
import app.revanced.manager.util.transparentListItemColors
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSelectorScreen(
    onSelect: (String) -> Unit,
    onStorageSelect: (SelectedApp.Local) -> Unit,
    onBackClick: () -> Unit,
    vm: AppSelectorViewModel = koinViewModel()
) {
    val prefs = koinInject<PreferencesManager>()
    val allowIncompatiblePatches by prefs.disablePatchVersionCompatCheck.getAsState()
    val suggestedVersionSafeguard by prefs.suggestedVersionSafeguard.getAsState()
    val bundleRecommendationsEnabled = allowIncompatiblePatches && !suggestedVersionSafeguard

    EventEffect(flow = vm.storageSelectionFlow) {
        onStorageSelect(it)
    }

    val pickApkLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let(vm::handleStorageResult)
        }

    val suggestedVersions by vm.suggestedAppVersions.collectAsStateWithLifecycle(emptyMap())
    val bundleSuggestionsByApp by vm.bundleSuggestionsByApp.collectAsStateWithLifecycle(emptyMap())

    var filterText by rememberSaveable { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf(false) }

    val appList by vm.appList.collectAsStateWithLifecycle(initialValue = emptyList())
    val filteredAppList = remember(appList, filterText) {
        appList.filter { app ->
            (vm.loadLabel(app.packageInfo)).contains(
                filterText,
                true
            ) or app.packageName.contains(filterText, true)
        }
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
            if (appList.isNotEmpty() && filterText.isNotEmpty()) {
                LazyColumnWithScrollbar(modifier = Modifier.fillMaxSize()) {
                    items(
                        items = filteredAppList,
                        key = { it.packageName }
                    ) { app ->
                        ListItem(
                            modifier = Modifier.clickable {
                                onSelect(app.packageName)
                            },
                            leadingContent = {
                                AppIcon(
                                    app.packageInfo,
                                    null,
                                    Modifier.size(36.dp)
                                )
                            },
                            headlineContent = { AppLabel(app.packageInfo) },
                            supportingContent = { Text(app.packageName) },
                            trailingContent = app.patches?.let {
                                {
                                    Text(
                                        pluralStringResource(
                                            R.plurals.patch_count,
                                            it,
                                            it
                                        )
                                    )
                                }
                            },
                            colors = transparentListItemColors
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
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                ListItem(
                    modifier = Modifier.clickable {
                        pickApkLauncher.launch(APK_MIMETYPE)
                    },
                    leadingContent = {
                        Box(Modifier.size(36.dp), Alignment.Center) {
                            Icon(
                                Icons.Default.Storage,
                                null,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    },
                    headlineContent = { Text(stringResource(R.string.select_from_storage)) },
                    supportingContent = {
                        Text(stringResource(R.string.select_from_storage_description))
                    }
                )
                HorizontalDivider()
            }

            if (appList.isNotEmpty()) {
                items(
                    items = appList,
                    key = { it.packageName }
                ) { app ->
                    ListItem(
                        modifier = Modifier.clickable {
                            onSelect(app.packageName)
                        },
                        leadingContent = { AppIcon(app.packageInfo, null, Modifier.size(36.dp)) },
                        headlineContent = {
                            AppLabel(
                                app.packageInfo,
                                defaultText = app.packageName
                            )
                        },
                        supportingContent = {
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
                                                .padding(horizontal = 16.dp, vertical = 4.dp),
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
                        },
                        trailingContent = app.patches?.let {
                            {
                                Text(
                                    pluralStringResource(
                                        R.plurals.patch_count,
                                        it,
                                        it
                                    )
                                )
                            }
                        }
                    )

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
private fun OtherSupportedVersionsInfoDialog(
    bundleName: String,
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                recommendedVersion?.let {
                    Text(
                        stringResource(
                            R.string.bundle_version_dialog_recommended,
                            stringResource(R.string.version_label, it)
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                when {
                    otherVersions.isNotEmpty() -> {
                        val context = LocalContext.current
                        val versionsText = otherVersions.joinToString(", ") { version ->
                            context.getString(R.string.version_label, version)
                        }
                        Text(
                            versionsText,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    supportsAllVersions -> {
                        Text(
                            stringResource(R.string.other_supported_versions_all),
                            style = MaterialTheme.typography.bodyMedium
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
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onShowOtherVersions: () -> Unit
) {
    ElevatedCard(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
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
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                contentColor = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = versionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
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
