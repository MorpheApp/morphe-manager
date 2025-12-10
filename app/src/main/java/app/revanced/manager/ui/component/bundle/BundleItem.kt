package app.revanced.manager.ui.component.bundle

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.outlined.Warning
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import app.morphe.manager.R
import app.revanced.manager.data.platform.NetworkInfo
import app.revanced.manager.domain.bundles.PatchBundleSource
import app.revanced.manager.domain.bundles.PatchBundleSource.Extensions.asRemoteOrNull
import app.revanced.manager.domain.bundles.PatchBundleSource.Extensions.isDefault
import app.revanced.manager.domain.repository.PatchBundleRepository
import app.revanced.manager.domain.repository.PatchBundleRepository.DisplayNameUpdateResult
import app.revanced.manager.ui.component.ConfirmDialog
import app.revanced.manager.ui.component.TextInputDialog
import app.revanced.manager.ui.component.bundle.extractGithubReleaseUrlFromDownload
import app.revanced.manager.ui.component.bundle.initialGithubReleaseUrl
import app.revanced.manager.ui.component.haptics.HapticCheckbox
import app.revanced.manager.util.consumeHorizontalScroll
import app.revanced.manager.util.relativeTime
import app.revanced.manager.util.simpleMessage
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
) {
    var viewBundleDialogPage by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirmationDialog by rememberSaveable { mutableStateOf(false) }
    var autoOpenReleaseRequest by rememberSaveable { mutableStateOf<Int?>(null) }
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val networkInfo = koinInject<NetworkInfo>()
    val bundleRepo = koinInject<PatchBundleRepository>()
    val coroutineScope = rememberCoroutineScope()
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

    val displayVersion = src.version
    val remoteSource = src.asRemoteOrNull
    val installedSignature = remoteSource?.installedVersionSignature
    val manualUpdateBadge = manualUpdateInfo?.takeIf { info ->
        val latest = info.latestVersion
        val baseline = installedSignature ?: displayVersion
        !latest.isNullOrBlank() && baseline != null && latest != baseline
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
                        IconButton(onClick = { showRenameDialog = true }) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = stringResource(R.string.patch_bundle_rename)
                            )
                        }
                    }
                    val hasCustomName =
                        src.displayName?.takeUnless { it.isBlank() } != null && src.displayTitle != src.name
                    if (hasCustomName) {
                        val internalNameScrollState = rememberScrollState()
                        Text(
                            text = src.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        color = MaterialTheme.colorScheme.outline
                    )
                    manualUpdateBadge?.let { info ->
                        val label = info.latestVersion?.takeUnless { it.isBlank() }?.let { version ->
                            stringResource(R.string.bundle_update_manual_available_with_version, version)
                        } ?: stringResource(R.string.bundle_update_manual_available)

                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ActionIconButton(
                    onClick = {
                        coroutineScope.launch {
                            openBundleReleasePage(
                                src = src,
                                networkInfo = networkInfo,
                                context = context,
                                uriHandler = uriHandler
                            )
                        }
                    }
                ) {
                    Icon(
                        FontAwesomeIcons.Brands.Github,
                        contentDescription = stringResource(R.string.bundle_release_page),
                        modifier = Modifier.size(18.dp)
                    )
                }
                val showUpdate = manualUpdateBadge != null || src.asRemoteOrNull != null
                if (showUpdate) {
                    ActionIconButton(onClick = onUpdate) {
                        Icon(
                            Icons.Outlined.Update,
                            contentDescription = stringResource(R.string.refresh),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                ActionIconButton(onClick = { showDeleteConfirmationDialog = true }) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.delete),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionIconButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp)
    ) {
        content()
    }
}

private suspend fun openBundleReleasePage(
    src: PatchBundleSource,
    networkInfo: NetworkInfo,
    context: android.content.Context,
    uriHandler: androidx.compose.ui.platform.UriHandler
) {
    val manifestSource = src.patchBundle?.manifestAttributes?.source
    val cached = initialGithubReleaseUrl(src, manifestSource)
    if (!cached.isNullOrBlank()) {
        uriHandler.openUri(cached)
        return
    }

    val remote = src.asRemoteOrNull
    if (remote == null) {
        context.toast(context.getString(R.string.bundle_release_page_unavailable))
        return
    }

    if (!networkInfo.isConnected()) {
        context.toast(context.getString(R.string.bundle_release_page_unavailable))
        return
    }

    runCatching {
        val asset = remote.fetchLatestReleaseInfo()
        val url = extractGithubReleaseUrlFromDownload(asset.downloadUrl)
            ?: asset.pageUrl?.takeUnless { it.isBlank() }
            ?: extractGithubReleaseUrlFromDownload(remote.endpoint)
        if (url.isNullOrBlank()) {
            context.toast(context.getString(R.string.bundle_release_page_unavailable))
        } else {
            uriHandler.openUri(url)
        }
    }.onFailure { error ->
        context.toast(
            context.getString(
                R.string.bundle_release_page_error,
                error.simpleMessage().orEmpty()
            )
        )
    }
}
