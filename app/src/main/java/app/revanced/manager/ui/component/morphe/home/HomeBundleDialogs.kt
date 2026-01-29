package app.revanced.manager.ui.component.morphe.home

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.revanced.manager.domain.bundles.PatchBundleSource
import app.revanced.manager.domain.bundles.RemotePatchBundle
import app.revanced.manager.domain.repository.PatchBundleRepository
import app.revanced.manager.network.dto.ReVancedAsset
import app.revanced.manager.patcher.patch.PatchInfo
import app.revanced.manager.ui.component.morphe.shared.*
import app.revanced.manager.ui.component.settings.Changelog
import app.revanced.manager.util.relativeTime
import app.revanced.manager.util.simpleMessage
import kotlinx.coroutines.flow.mapNotNull
import org.koin.compose.koinInject

/**
 * Dialog for adding patch bundles
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MorpheAddBundleDialog(
    onDismiss: () -> Unit,
    onLocalSubmit: () -> Unit,
    onRemoteSubmit: (url: String) -> Unit,
    onLocalPick: () -> Unit,
    selectedLocalPath: String?
) {
    var remoteUrl by rememberSaveable { mutableStateOf("") }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) } // 0 = Remote, 1 = Local

    val isRemoteValid = remoteUrl.isNotBlank() &&
            (remoteUrl.startsWith("http://") || remoteUrl.startsWith("https://"))
    val isLocalValid = selectedLocalPath != null

    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.morphe_add_patch_source),
        footer = {
            MorpheDialogButtonRow(
                primaryText = stringResource(R.string.add),
                onPrimaryClick = {
                    when (selectedTab) {
                        0 -> if (isRemoteValid) onRemoteSubmit(remoteUrl)
                        1 -> if (isLocalValid) onLocalSubmit()
                    }
                },
                primaryEnabled = if (selectedTab == 0) isRemoteValid else isLocalValid,
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Tabs
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(
                        stringResource(R.string.morphe_add_patch_source_remote),
                        stringResource(R.string.morphe_add_patch_source_local)
                    ).forEachIndexed { index, title ->
                        val isSelected = selectedTab == index

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clickable { selectedTab = index }
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected)
                                    MaterialTheme.colorScheme.primary
                                else
                                    Color.Transparent,
                                modifier = Modifier.fillMaxSize()
                            ) {}
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected)
                                        FontWeight.Bold
                                    else
                                        FontWeight.Normal,
                                    color = if (isSelected)
                                        MaterialTheme.colorScheme.onPrimary
                                    else
                                        LocalDialogTextColor.current
                                )
                            }
                        }
                    }
                }
            }

            // Tabs content
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    fadeIn(animationSpec = tween(200)).togetherWith(fadeOut(animationSpec = tween(200)))
                }
            ) { tab ->
                when (tab) {
                    0 -> RemoteTabContent(
                        remoteUrl = remoteUrl,
                        onUrlChange = { remoteUrl = it }
                    )
                    1 -> LocalTabContent(
                        selectedPath = selectedLocalPath,
                        onPickFile = onLocalPick
                    )
                }
            }
        }
    }
}

@Composable
private fun RemoteTabContent(
    remoteUrl: String,
    onUrlChange: (String) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // URL input
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MorpheDialogTextField(
                value = remoteUrl,
                onValueChange = onUrlChange,
                label = {
                    Text(stringResource(R.string.morphe_add_patch_source_remote))
                },
                placeholder = {
                    Text(text = "https://example.com/patches.json")
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )
        }

        // Description
        InfoBadge(
            icon = Icons.Outlined.Info,
            text = stringResource(R.string.morphe_add_patch_source_remote_description),
            style = InfoBadgeStyle.Success
        )
    }
}

@Composable
private fun LocalTabContent(
    selectedPath: String?,
    onPickFile: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // File picker button
        MorpheDialogButton(
            text = if (selectedPath == null) {
                stringResource(R.string.morphe_add_patch_source_local_file)
            } else {
                stringResource(R.string.morphe_add_patch_source_local_change_file)
            },
            onClick = onPickFile,
            icon = Icons.Outlined.FolderOpen,
            modifier = Modifier.fillMaxWidth()
        )

        // Selected file path
        if (selectedPath != null) {
            InfoBadge(
                icon = null,
                text = selectedPath,
                style = InfoBadgeStyle.Default
            )
        }

        // Description
        InfoBadge(
            icon = Icons.Outlined.Info,
            text = stringResource(R.string.morphe_add_patch_source_local_file_description),
            style = InfoBadgeStyle.Success
        )
    }
}

@Composable
fun MorpheBundleDeleteConfirmDialog(
    bundle: PatchBundleSource,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.delete),
        footer = {
            MorpheDialogButtonRow(
                primaryText = stringResource(R.string.delete),
                onPrimaryClick = onConfirm,
                isPrimaryDestructive = true,
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        }
    ) {
        val secondaryColor = LocalDialogSecondaryTextColor.current

        Text(
            text = stringResource(
                R.string.morphe_sources_delete_confirm_message,
                bundle.displayTitle
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = secondaryColor
        )
    }
}

/**
 * Dialog for renaming a bundle
 */
@Composable
fun MorpheRenameBundleDialog(
    initialValue: String,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var textValue by remember { mutableStateOf(initialValue) }
    val keyboardController = LocalSoftwareKeyboardController.current

    MorpheDialog(
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.patches_display_name),
        dismissOnClickOutside = false,
        footer = {
            MorpheDialogButtonRow(
                primaryText = stringResource(android.R.string.ok),
                onPrimaryClick = {
                    keyboardController?.hide()
                    onConfirm(textValue)
                },
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = {
                    keyboardController?.hide()
                    onDismissRequest()
                }
            )
        }
    ) {
        val secondaryColor = LocalDialogSecondaryTextColor.current

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.patch_bundle_rename),
                style = MaterialTheme.typography.bodyMedium,
                color = secondaryColor
            )

            MorpheDialogTextField(
                value = textValue,
                onValueChange = { textValue = it },
                placeholder = {
                    Text(
                        text = stringResource(R.string.morphe_patch_option_enter_value),
                        color = secondaryColor.copy(alpha = 0.5f)
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = null,
                        tint = secondaryColor
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        keyboardController?.hide()
                        onConfirm(textValue)
                    }
                )
            )
        }
    }
}

/**
 * Dialog displaying patches from a bundle
 */
@Composable
fun HomeBundlePatchesDialog(
    onDismissRequest: () -> Unit,
    src: PatchBundleSource
) {
    val patchBundleRepository: PatchBundleRepository = koinInject()
    val patches by remember(src.uid) {
        patchBundleRepository.bundleInfoFlow.mapNotNull { it[src.uid]?.patches }
    }.collectAsStateWithLifecycle(emptyList())

    val isLoading = patches.isEmpty()

    MorpheDialog(
        onDismissRequest = onDismissRequest,
        title = null,
        footer = {
            MorpheDialogButtonRow(
                primaryText = stringResource(android.R.string.ok),
                onPrimaryClick = onDismissRequest
            )
        },
        scrollable = false
    ) {
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header as first item
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                modifier = Modifier.size(56.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Outlined.Extension,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = src.displayTitle,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = LocalDialogTextColor.current,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Widgets,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "${patches.size} ${stringResource(R.string.patches).lowercase()}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }

                // Patches list
                items(patches) { patch ->
                    var expandVersions by rememberSaveable(src.uid, patch.name, "versions") {
                        mutableStateOf(false)
                    }
                    var expandOptions by rememberSaveable(src.uid, patch.name, "options") {
                        mutableStateOf(false)
                    }

                    PatchItemCard(
                        patch = patch,
                        expandVersions = expandVersions,
                        onExpandVersions = { expandVersions = !expandVersions },
                        expandOptions = expandOptions,
                        onExpandOptions = { expandOptions = !expandOptions }
                    )
                }
            }
        }
    }
}

/**
 * Patch item card
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PatchItemCard(
    patch: PatchInfo,
    expandVersions: Boolean,
    onExpandVersions: () -> Unit,
    expandOptions: Boolean,
    onExpandOptions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val textColor = LocalDialogTextColor.current
    val secondaryColor = LocalDialogSecondaryTextColor.current

    val rotationAngle by animateFloatAsState(
        targetValue = if (expandOptions) 180f else 0f,
        animationSpec = tween(300),
        label = "expand_rotation"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .then(
                if (!patch.options.isNullOrEmpty()) {
                    Modifier.clickable(onClick = onExpandOptions)
                } else Modifier
            ),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = patch.name,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                if (!patch.options.isNullOrEmpty()) {
                    Icon(
                        imageVector = Icons.Outlined.ExpandMore,
                        contentDescription = if (expandOptions)
                            stringResource(R.string.collapse)
                        else
                            stringResource(R.string.expand),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(24.dp)
                            .rotate(rotationAngle)
                    )
                }
            }

            // Description
            patch.description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor
                )
            }

            // Compatibility info
            if (patch.compatiblePackages.isNullOrEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    InfoBadge(
                        text = stringResource(R.string.patches_view_any_package),
                        icon = Icons.Outlined.Apps,
                        style = InfoBadgeStyle.Default,
                        isCompact = true
                    )
                    InfoBadge(
                        text = stringResource(R.string.patches_view_any_version),
                        icon = Icons.Outlined.Code,
                        style = InfoBadgeStyle.Default,
                        isCompact = true
                    )
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    patch.compatiblePackages.forEach { compatiblePackage ->
                        val packageName = compatiblePackage.packageName
                        val versions = compatiblePackage.versions.orEmpty().reversed()

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            InfoBadge(
                                text = packageName,
                                icon = Icons.Outlined.Apps,
                                style = InfoBadgeStyle.Primary,
                                isCompact = true,
                                modifier = Modifier.align(Alignment.CenterVertically)
                            )

                            if (versions.isNotEmpty()) {
                                if (expandVersions) {
                                    versions.forEach { version ->
                                        InfoBadge(
                                            text = version,
                                            icon = Icons.Outlined.Code,
                                            style = InfoBadgeStyle.Default,
                                            isCompact = true,
                                            modifier = Modifier.align(Alignment.CenterVertically)
                                        )
                                    }
                                } else {
                                    InfoBadge(
                                        text = versions.first(),
                                        icon = Icons.Outlined.Code,
                                        style = InfoBadgeStyle.Default,
                                        isCompact = true,
                                        modifier = Modifier.align(Alignment.CenterVertically)
                                    )
                                }

                                if (versions.size > 1) {
                                    InfoBadge(
                                        text = if (expandVersions)
                                            stringResource(R.string.less)
                                        else
                                            "+${versions.size - 1}",
                                        style = InfoBadgeStyle.Default,
                                        isCompact = true,
                                        modifier = Modifier
                                            .align(Alignment.CenterVertically)
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable(onClick = onExpandVersions)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Options
            if (!patch.options.isNullOrEmpty()) {
                AnimatedVisibility(
                    visible = expandOptions,
                    enter = expandVertically(tween(300)) + fadeIn(tween(300)),
                    exit = shrinkVertically(tween(300)) + fadeOut(tween(300))
                ) {
                    Column(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        patch.options.forEach { option ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = option.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = option.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = secondaryColor
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
 * Dialog displaying changelog for a bundle
 */
@Composable
fun HomeBundleChangelogDialog(
    src: RemotePatchBundle,
    onDismissRequest: () -> Unit
) {
    var state: BundleChangelogState by remember { mutableStateOf(BundleChangelogState.Loading) }

    LaunchedEffect(src.uid) {
        state = BundleChangelogState.Loading
        state = try {
            val asset = src.fetchLatestReleaseInfo()
            BundleChangelogState.Success(asset)
        } catch (t: Throwable) {
            BundleChangelogState.Error(t)
        }
    }

    MorpheDialog(
        onDismissRequest = onDismissRequest,
        title = null,
        footer = {
            MorpheDialogButtonRow(
                primaryText = stringResource(android.R.string.ok),
                onPrimaryClick = onDismissRequest
            )
        }
    ) {
        when (val current = state) {
            BundleChangelogState.Loading -> BundleChangelogLoading()
            is BundleChangelogState.Error -> BundleChangelogError(
                error = current.throwable,
                onDismissRequest = onDismissRequest,
                onRetry = {
                    // Reload on retry
                }
            )
            is BundleChangelogState.Success -> BundleChangelogContent(
                asset = current.asset
            )
        }
    }
}

@Composable
private fun BundleChangelogLoading() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 64.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = stringResource(R.string.changelog_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalDialogSecondaryTextColor.current
            )
        }
    }
}

@Composable
private fun BundleChangelogError(
    error: Throwable,
    onDismissRequest: () -> Unit,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Error icon with circular background
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                modifier = Modifier.size(80.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            // Error details
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(
                        R.string.bundle_changelog_error,
                        error.simpleMessage().orEmpty()
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = LocalDialogTextColor.current
                )
                MorpheDialogButton(
                    text = stringResource(R.string.bundle_changelog_retry),
                    onClick = onRetry
                )
            }

            // Dismiss button
            MorpheDialogButton(
                text = stringResource(android.R.string.ok),
                onClick = onDismissRequest,
                modifier = Modifier.widthIn(min = 140.dp)
            )
        }
    }
}

@Composable
private fun BundleChangelogContent(
    asset: ReVancedAsset
) {
    val context = LocalContext.current

    val publishDate = remember(asset.createdAt) {
        asset.createdAt.relativeTime(context)
    }
    val markdown = remember(asset.description) {
        asset.description
            .replace("\r\n", "\n")
            .sanitizePatchChangelogMarkdown()
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Header with version info
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon with gradient-like appearance
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Version info
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = asset.version,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = LocalDialogTextColor.current
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = publishDate,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Changelog markdown content
        Changelog(
            markdown = markdown.ifBlank {
                stringResource(R.string.bundle_changelog_empty)
            },
//            version = asset.version,
//            publishDate = publishDate
        )
    }
}

private sealed interface BundleChangelogState {
    data object Loading : BundleChangelogState
    data class Success(val asset: ReVancedAsset) : BundleChangelogState
    data class Error(val throwable: Throwable) : BundleChangelogState
}

private val doubleBracketLinkRegex = Regex("""\[\[([^]]+)]\(([^)]+)\)]""")

private fun String.sanitizePatchChangelogMarkdown(): String =
    doubleBracketLinkRegex.replace(this) { match ->
        val label = match.groupValues[1]
        val link = match.groupValues[2]
        "[\\[$label\\]]($link)"
    }
