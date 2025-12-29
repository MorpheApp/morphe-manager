package app.revanced.manager.ui.component.bundle

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.revanced.manager.data.platform.NetworkInfo
import androidx.compose.ui.draw.clip
import app.revanced.manager.domain.bundles.PatchBundleSource
import app.revanced.manager.domain.bundles.PatchBundleSource.Extensions.asRemoteOrNull
import app.revanced.manager.domain.bundles.PatchBundleSource.Extensions.isDefault
import app.revanced.manager.domain.repository.PatchBundleRepository
import app.revanced.manager.domain.repository.PatchBundleRepository.DisplayNameUpdateResult
import app.revanced.manager.ui.component.ConfirmDialog
import app.revanced.manager.ui.component.TextInputDialog
import app.revanced.manager.ui.component.haptics.HapticCheckbox
import app.revanced.manager.util.consumeHorizontalScroll
import app.revanced.manager.util.relativeTime
import app.revanced.manager.util.toast
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Brands
import compose.icons.fontawesomeicons.brands.Github
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun BundleItem(
    src: PatchBundleSource,
    patchCount: Int,
    manualUpdateInfo: PatchBundleRepository.ManualBundleUpdateInfo?,
    selectable: Boolean,
    isBundleSelected: Boolean,
    toggleSelection: (Boolean) -> Unit,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    onUpdate: () -> Unit,
    onDisable: () -> Unit,
) {
    var viewBundleDialogPage by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirmationDialog by rememberSaveable { mutableStateOf(false) }
    var showDisableConfirmationDialog by rememberSaveable { mutableStateOf(false) }
    var showEnableConfirmationDialog by rememberSaveable { mutableStateOf(false) }
    var autoOpenReleaseRequest by rememberSaveable { mutableStateOf<Int?>(null) }
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val networkInfo = koinInject<NetworkInfo>()
    val bundleRepo = koinInject<PatchBundleRepository>()
    val coroutineScope = rememberCoroutineScope()
//    val catalogUrl = remember(src) {
//        if (src.isDefault) PatchListCatalog.revancedCatalogUrl() else PatchListCatalog.resolveCatalogUrl(src)
//    }
    var showLinkSheet by rememberSaveable { mutableStateOf(false) }
    var showRenameDialog by rememberSaveable { mutableStateOf(false) }

    if (viewBundleDialogPage) {
        BundleInformationDialog(
            src = src,
            patchCount = patchCount,
            onDismissRequest = {
                viewBundleDialogPage = false
                autoOpenReleaseRequest = null
            },
            onDeleteRequest = { showDeleteConfirmationDialog = true },
            onDisableRequest = {
                if (src.enabled) {
                    showDisableConfirmationDialog = true
                } else {
                    showEnableConfirmationDialog = true
                }
            },
            onUpdate = onUpdate,
            autoOpenReleaseRequest = autoOpenReleaseRequest,
        )
    }

    val bundleTitle = src.displayTitle

    if (showRenameDialog) {
        TextInputDialog(
            initial = src.displayName.orEmpty(),
            title = stringResource(R.string.patches_display_name),
            onDismissRequest = { showRenameDialog = false },
            onConfirm = { value ->
                coroutineScope.launch {
                    val result = bundleRepo.setDisplayName(src.uid, value.trim().ifEmpty { null })
                    when (result) {
                        DisplayNameUpdateResult.SUCCESS, DisplayNameUpdateResult.NO_CHANGE -> {
                            showRenameDialog = false
                        }
                        DisplayNameUpdateResult.DUPLICATE -> {
                            context.toast(context.getString(R.string.patch_bundle_duplicate_name_error))
                        }
                        DisplayNameUpdateResult.NOT_FOUND -> {
                            context.toast(context.getString(R.string.patch_bundle_missing_error))
                        }
                    }
                }
            },
            validator = { true }
        )
    }

    if (showDeleteConfirmationDialog) {
        ConfirmDialog(
            onDismiss = { showDeleteConfirmationDialog = false },
            onConfirm = {
                showDeleteConfirmationDialog = false
                onDelete()
                viewBundleDialogPage = false
            },
            title = stringResource(R.string.delete),
            description = stringResource(
                R.string.patches_delete_single_dialog_description,
                bundleTitle
            ),
            icon = Icons.Outlined.Delete
        )
    }

    if (showDisableConfirmationDialog) {
        ConfirmDialog(
            onDismiss = { showDisableConfirmationDialog = false },
            onConfirm = {
                showDisableConfirmationDialog = false
                onDisable()
                viewBundleDialogPage = false
            },
            title = stringResource(R.string.disable),
            description = stringResource(
                R.string.patches_disable_single_dialog_description,
                bundleTitle
            ),
            icon = Icons.Outlined.Block
        )
    }

    if (showEnableConfirmationDialog) {
        ConfirmDialog(
            onDismiss = { showEnableConfirmationDialog = false },
            onConfirm = {
                showEnableConfirmationDialog = false
                onDisable()
                viewBundleDialogPage = false
            },
            title = stringResource(R.string.enable),
            description = stringResource(
                R.string.patches_enable_single_dialog_description,
                bundleTitle
            ),
            icon = Icons.Outlined.CheckCircle
        )
    }

    val displayVersion = src.version
    val remoteSource = src.asRemoteOrNull
    val installedSignature = remoteSource?.installedVersionSignature
    val manualUpdateBadge = manualUpdateInfo?.takeIf { info ->
        val latest = info.latestVersion
        val baseline = installedSignature ?: displayVersion
        !latest.isNullOrBlank() && baseline != null && latest != baseline
    }

    val disabledAlpha = 0.38f
    val primaryTextColor = if (src.enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = disabledAlpha)
    }
    val secondaryTextColor = if (src.enabled) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = disabledAlpha)
    }
    val outlineTextColor = if (src.enabled) {
        MaterialTheme.colorScheme.outline
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = disabledAlpha)
    }
    val accentTextColor = if (src.enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = disabledAlpha)
    }

    val cardShape = RoundedCornerShape(16.dp)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(cardShape)
            .combinedClickable(
                onClick = { viewBundleDialogPage = true },
                onLongClick = onSelect,
            ),
        shape = cardShape,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (selectable) {
                    HapticCheckbox(
                        checked = isBundleSelected,
                        onCheckedChange = toggleSelection,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val statusIcon = remember(src.state) {
                        when (src.state) {
                            is PatchBundleSource.State.Failed -> Icons.Outlined.ErrorOutline to R.string.patches_error
                            is PatchBundleSource.State.Missing -> Icons.Outlined.Warning to R.string.patches_missing
                            is PatchBundleSource.State.Available -> null
                        }
                    }
                    val titleScrollState = rememberScrollState()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = src.displayTitle,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                color = primaryTextColor,
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .consumeHorizontalScroll(titleScrollState)
                                    .horizontalScroll(titleScrollState)
                            )
                            statusIcon?.let { (icon, description) ->
                                Icon(
                                    icon,
                                    contentDescription = stringResource(description),
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                    val hasCustomName =
                        src.displayName?.takeUnless { it.isBlank() } != null && src.displayTitle != src.name
                    if (hasCustomName) {
                        val internalNameScrollState = rememberScrollState()
                        Text(
                            text = src.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = secondaryTextColor,
                            modifier = Modifier
                                .consumeHorizontalScroll(internalNameScrollState)
                                .horizontalScroll(internalNameScrollState)
                        )
                    }
                    val patchCountText =
                        if (src.state is PatchBundleSource.State.Available) {
                            pluralStringResource(R.plurals.patch_count, patchCount, patchCount)
                        } else null
                    val versionText = src.version?.let {
                        if (it.startsWith("v", ignoreCase = true)) it else "v$it"
                    }
                    val detailLine = listOfNotNull(patchCountText, versionText).joinToString(" • ")
                    if (detailLine.isNotEmpty()) {
                        Text(
                            text = detailLine,
                            style = MaterialTheme.typography.bodySmall,
                            color = secondaryTextColor
                        )
                    }
                    val timestampLine = listOfNotNull(
                        src.createdAt?.takeIf { it > 0 }?.relativeTime(context)?.let {
                            stringResource(R.string.bundle_created_at, it)
                        },
                        src.updatedAt?.takeIf { it > 0 }?.relativeTime(context)?.let {
                            stringResource(R.string.bundle_updated_at, it)
                        }
                    ).joinToString(" • ")
                    if (timestampLine.isNotEmpty()) {
                        Text(
                            text = timestampLine,
                            style = MaterialTheme.typography.bodySmall,
                            color = secondaryTextColor
                        )
                    }
                    val typeLabel = stringResource(
                        when {
                            src.isDefault -> R.string.bundle_type_preinstalled
                            src.asRemoteOrNull != null -> R.string.bundle_type_remote
                            else -> R.string.bundle_type_local
                        }
                    )
                    Text(
                        text = typeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = outlineTextColor
                    )
                    manualUpdateBadge?.let { info ->
                        val label = info.latestVersion?.takeUnless { it.isBlank() }?.let { version ->
                            stringResource(R.string.bundle_update_manual_available_with_version, version)
                        } ?: stringResource(R.string.bundle_update_manual_available)

                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = accentTextColor
                        )
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.width(ActionButtonSize)
            ) {
                ActionButtonPair(
                    leadingOnClick = { showRenameDialog = true },
                    leadingIcon = Icons.Outlined.Edit,
                    leadingDescription = stringResource(R.string.patch_bundle_rename),
                    trailingOnClick = { showLinkSheet = true },
                    trailingIcon = FontAwesomeIcons.Brands.Github,
                    trailingDescription = stringResource(R.string.bundle_release_page),
                )
                val showUpdate = manualUpdateBadge != null || src.asRemoteOrNull != null
                if (showUpdate) {
                    ActionIconButton(onClick = onUpdate) {
                        Icon(
                            Icons.Outlined.Update,
                            contentDescription = stringResource(R.string.refresh),
                            modifier = Modifier.size(ActionIconSize)
                        )
                    }
                }
                // Morphe: For now, don't allow removing or disable the bundle of patches
//                val toggleIcon = if (src.enabled) Icons.Outlined.Block else Icons.Outlined.CheckCircle
//                val toggleLabel = if (src.enabled) R.string.disable else R.string.enable
//                ActionButtonPair(
//                    leadingOnClick = {
//                        if (src.enabled) {
//                            showDisableConfirmationDialog = true
//                        } else {
//                            showEnableConfirmationDialog = true
//                        }
//                    },
//                    leadingIcon = toggleIcon,
//                    leadingDescription = stringResource(toggleLabel),
//                    trailingOnClick = { showDeleteConfirmationDialog = true },
//                    trailingIcon = Icons.Outlined.Delete,
//                    trailingDescription = stringResource(R.string.delete),
//                )

                // Show delete button only for non-default bundles
                if (!src.isDefault) {
                    ActionIconButton(onClick = { showDeleteConfirmationDialog = true }) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = stringResource(R.string.delete),
                            modifier = Modifier.size(ActionIconSize)
                        )
                    }
                }
            }
        }
    }

    if (showLinkSheet) {
        // Morphe
//        BundleLinksSheet(
//            bundleTitle = bundleTitle,
//            catalogUrl = catalogUrl,
//            onReleaseClick = {
//                coroutineScope.launch {
//                    openBundleReleasePage(src, networkInfo, context, uriHandler)
//                }
//            },
//            onCatalogClick = {
//                coroutineScope.launch {
//                    openBundleCatalogPage(catalogUrl, context, uriHandler)
//                }
//            },
//            onDismissRequest = { showLinkSheet = false }
//        )
    }

//    if (showLinkSheet) {
//        BundleLinksSheet(
//            bundleTitle = bundleTitle,
//            catalogUrl = catalogUrl,
//            onReleaseClick = {
//                coroutineScope.launch {
//                    openBundleReleasePage(src, networkInfo, context, uriHandler)
//                }
//            },
//            onCatalogClick = {
//                coroutineScope.launch {
//                    openBundleCatalogPage(catalogUrl, context, uriHandler)
//                }
//            },
//            onDismissRequest = { showLinkSheet = false }
//        )
//    }
}

@Composable
private fun ActionIconButton(
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    IconButton(
        enabled = enabled,
        onClick = onClick,
        modifier = modifier.size(ActionButtonSize)
    ) {
        content()
    }
}

private val ActionButtonSize = 40.dp
private val ActionIconSize = 18.dp
private val ActionButtonSpacing = 4.dp
private val ActionButtonOffset = ActionButtonSize + ActionButtonSpacing

@Composable
private fun ActionButtonPair(
    enabled: Boolean = true,
    leadingOnClick: () -> Unit,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector,
    leadingDescription: String,
    trailingOnClick: () -> Unit,
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector,
    trailingDescription: String,
) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        ActionIconButton(
            enabled = enabled,
            onClick = trailingOnClick,
            modifier = Modifier.align(Alignment.Center)
        ) {
            Icon(
                trailingIcon,
                contentDescription = trailingDescription,
                modifier = Modifier.size(ActionIconSize)
            )
        }
        ActionIconButton(
            onClick = leadingOnClick,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = -ActionButtonOffset)
        ) {
            Icon(
                leadingIcon,
                contentDescription = leadingDescription,
                modifier = Modifier.size(ActionIconSize)
            )
        }
    }
}
