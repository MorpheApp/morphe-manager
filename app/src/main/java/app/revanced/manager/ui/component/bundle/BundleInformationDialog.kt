package app.revanced.manager.ui.component.bundle

import android.content.ClipData
import android.content.ClipboardManager
import android.webkit.URLUtil.isValidUrl
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowRight
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Commit
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.R.string.auto_update
import app.morphe.manager.R.string.auto_update_description
import app.morphe.manager.R.string.field_not_set
import app.morphe.manager.R.string.patches
import app.morphe.manager.R.string.patches_url
import app.morphe.manager.R.string.view_patches
import app.revanced.manager.data.platform.NetworkInfo
import app.revanced.manager.domain.bundles.LocalPatchBundle
import app.revanced.manager.domain.bundles.PatchBundleSource
import app.revanced.manager.domain.bundles.PatchBundleSource.Extensions.asRemoteOrNull
import app.revanced.manager.domain.bundles.PatchBundleSource.Extensions.isDefault
import app.revanced.manager.domain.manager.PreferencesManager
import app.revanced.manager.domain.repository.PatchBundleRepository
import app.revanced.manager.domain.repository.PatchBundleRepository.DisplayNameUpdateResult
import app.revanced.manager.ui.component.ColumnWithScrollbar
import app.revanced.manager.ui.component.ExceptionViewerDialog
import app.revanced.manager.ui.component.FullscreenDialog
import app.revanced.manager.ui.component.TextInputDialog
import app.revanced.manager.ui.component.haptics.HapticSwitch
import app.revanced.manager.util.simpleMessage
import app.revanced.manager.util.toast
import kotlinx.coroutines.launch
import androidx.core.content.getSystemService
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Brands
import compose.icons.fontawesomeicons.brands.Github
import org.koin.compose.koinInject
import java.net.URI

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BundleInformationDialog(
    src: PatchBundleSource,
    patchCount: Int,
    onDismissRequest: () -> Unit,
    onDeleteRequest: () -> Unit,
    onUpdate: () -> Unit,
    autoOpenReleaseRequest: Int? = null,
) {
    val bundleRepo = koinInject<PatchBundleRepository>()
    val networkInfo = koinInject<NetworkInfo>()
    val prefs = koinInject<PreferencesManager>()
    val hasNetwork = remember { networkInfo.isConnected() }
    val composableScope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = remember(context) { context.getSystemService<ClipboardManager>() }
    val uriHandler = LocalUriHandler.current
    var viewCurrentBundlePatches by remember { mutableStateOf(false) }
    var viewBundleChangelog by remember { mutableStateOf(false) }
    val isLocal = src is LocalPatchBundle
    val bundleManifestAttributes = src.patchBundle?.manifestAttributes
    val manifestSource = bundleManifestAttributes?.source
    val (autoUpdate, endpoint) = src.asRemoteOrNull?.let { it.autoUpdate to it.endpoint }
        ?: (null to null)
    var showDisplayNameDialog by remember { mutableStateOf(false) }
    var releasePageUrl by remember(src.uid, manifestSource) {
        mutableStateOf(
            initialGithubReleaseUrl(
                src = src,
                manifestSource = manifestSource
            )
        )
    }
    var releasePageLoading by remember { mutableStateOf(false) }

    fun onAutoUpdateChange(new: Boolean) = composableScope.launch {
        with(bundleRepo) {
            src.asRemoteOrNull?.setAutoUpdate(new)
        }
    }

    fun openReleasePage() = composableScope.launch {
        releasePageUrl?.let {
            uriHandler.openUri(it)
            return@launch
        }

        val remote = src.asRemoteOrNull
        if (remote == null) {
            context.toast(context.getString(R.string.bundle_release_page_unavailable))
            return@launch
        }

        if (!hasNetwork) {
            context.toast(context.getString(R.string.bundle_release_page_unavailable))
            return@launch
        }

        releasePageLoading = true
        try {
            val asset = remote.fetchLatestReleaseInfo()
            val url = extractGithubReleaseUrlFromDownload(asset.downloadUrl)
                ?: asset.pageUrl?.takeUnless { it.isBlank() }
                ?: extractGithubReleaseUrlFromDownload(remote.endpoint)
            if (url.isNullOrBlank()) {
                context.toast(context.getString(R.string.bundle_release_page_unavailable))
            } else {
                releasePageUrl = url
                uriHandler.openUri(url)
            }
        } catch (t: Throwable) {
            context.toast(
                context.getString(
                    R.string.bundle_release_page_error,
                    t.simpleMessage().orEmpty()
                )
            )
        } finally {
            releasePageLoading = false
        }
    }

    LaunchedEffect(autoOpenReleaseRequest) {
        autoOpenReleaseRequest?.let {
            openReleasePage()
            onDismissRequest()
        }
    }

    if (viewCurrentBundlePatches) {
        BundlePatchesDialog(
            src = src,
            onDismissRequest = {
                viewCurrentBundlePatches = false
            }
        )
    }
    if (viewBundleChangelog) {
        val remote = src.asRemoteOrNull
        if (remote != null) {
            BundleChangelogDialog(
                src = remote,
                onDismissRequest = {
                    viewBundleChangelog = false
                }
            )
        } else {
            viewBundleChangelog = false
        }
    }

    FullscreenDialog(
        onDismissRequest = onDismissRequest,
    ) {
        Scaffold(
            topBar = {
                BundleTopBar(
                    title = src.displayTitle,
                    onBackClick = onDismissRequest,
                    backIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    },
                    actions = {
                        val githubButtonEnabled =
                            (releasePageUrl != null || (!isLocal && hasNetwork)) && !releasePageLoading
                        IconButton(
                            onClick = { if (!releasePageLoading) openReleasePage() },
                            enabled = githubButtonEnabled
                        ) {
                            Icon(
                                imageVector = FontAwesomeIcons.Brands.Github,
                                contentDescription = stringResource(R.string.bundle_release_page),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        IconButton(onClick = onDeleteRequest) {
                            Icon(
                                Icons.Outlined.DeleteOutline,
                                stringResource(R.string.delete)
                            )
                        }
                        if (!isLocal && hasNetwork) {
                            IconButton(onClick = onUpdate) {
                                Icon(
                                    Icons.Outlined.Update,
                                    stringResource(R.string.refresh)
                                )
                            }
                        }
                    }
                )
            },
        ) { paddingValues ->
            ColumnWithScrollbar(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(paddingValues),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Tag(Icons.Outlined.Sell, src.name)
                    bundleManifestAttributes?.description?.let {
                        Tag(Icons.Outlined.Description, it)
                    }
                    bundleManifestAttributes?.source?.let {
                        Tag(Icons.Outlined.Commit, it)
                    }
                    bundleManifestAttributes?.author?.let {
                        Tag(Icons.Outlined.Person, it)
                    }
                    bundleManifestAttributes?.contact?.let {
                        Tag(Icons.AutoMirrored.Outlined.Send, it)
                    }
                    bundleManifestAttributes?.website?.let {
                        Tag(Icons.Outlined.Language, it, isUrl = true)
                    }
                    bundleManifestAttributes?.license?.let {
                        Tag(Icons.Outlined.Gavel, it)
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                if (showDisplayNameDialog) {
                    TextInputDialog(
                        initial = src.displayName.orEmpty(),
                        title = stringResource(R.string.patches_display_name),
                        onDismissRequest = { showDisplayNameDialog = false },
                        onConfirm = { value ->
                            composableScope.launch {
                                val result = bundleRepo.setDisplayName(src.uid, value.trim().ifEmpty { null })
                                when (result) {
                                    DisplayNameUpdateResult.SUCCESS, DisplayNameUpdateResult.NO_CHANGE -> {
                                        showDisplayNameDialog = false
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

                BundleListItem(
                    modifier = Modifier.clickable { showDisplayNameDialog = true },
                    headlineText = stringResource(R.string.patches_display_name),
                    supportingText = src.displayName?.takeUnless { it.isBlank() }
                        ?: stringResource(field_not_set)
                )

                if (isLocal) {
                    val identifierValue = src.uid.toString()
                    BundleListItem(
                        headlineText = stringResource(R.string.bundle_uid),
                        supportingText = identifierValue,
                        trailingContent = {
                            IconButton(
                                onClick = {
                                    clipboard?.setPrimaryClip(
                                        ClipData.newPlainText(
                                            context.getString(R.string.bundle_uid),
                                            identifierValue
                                        )
                                    )
                                    context.toast(context.getString(R.string.toast_copied_to_clipboard))
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ContentCopy,
                                    contentDescription = stringResource(R.string.copy_to_clipboard)
                                )
                            }
                        }
                    )
                }

                if (autoUpdate != null) {
                    BundleListItem(
                        headlineText = stringResource(auto_update),
                        supportingText = stringResource(auto_update_description),
                        trailingContent = {
                            HapticSwitch(
                                checked = autoUpdate,
                                onCheckedChange = ::onAutoUpdateChange
                            )
                        },
                        modifier = Modifier.clickable {
                            onAutoUpdateChange(!autoUpdate)
                        }
                    )
                }

                if (src.isDefault) {
                    val useBundlePrerelease by prefs.usePatchesPrereleases.getAsState()

                    BundleListItem(
                        headlineText = stringResource(R.string.patches_prereleases),
                        supportingText = stringResource(R.string.patches_prereleases_description, src.name),
                        trailingContent = {
                            HapticSwitch(
                                checked = useBundlePrerelease,
                                onCheckedChange = {
                                    composableScope.launch {
                                        prefs.usePatchesPrereleases.update(
                                            it
                                        )
                                        onUpdate()
                                    }
                                }
                            )
                        },
                        modifier = Modifier.clickable {
                            composableScope.launch {
                                prefs.usePatchesPrereleases.update(!useBundlePrerelease)
                                onUpdate()
                            }
                        }
                    )
                }

                endpoint?.takeUnless { src.isDefault }?.let { url ->
                    var showUrlInputDialog by rememberSaveable {
                        mutableStateOf(false)
                    }
                    if (showUrlInputDialog) {
                        TextInputDialog(
                            initial = url,
                            title = stringResource(patches_url),
                            onDismissRequest = { showUrlInputDialog = false },
                            onConfirm = {
                                showUrlInputDialog = false
                                TODO("Not implemented.")
                            },
                            validator = {
                                if (it.isEmpty()) return@TextInputDialog false

                                isValidUrl(it)
                            }
                        )
                    }

                    BundleListItem(
                        modifier = Modifier.clickable(
                            enabled = false,
                            onClick = {
                                showUrlInputDialog = true
                            }
                        ),
                        headlineText = stringResource(patches_url),
                        supportingText = url.ifEmpty {
                            stringResource(field_not_set)
                        },
                        trailingContent = {
                            IconButton(
                                onClick = {
                                    clipboard?.setPrimaryClip(
                                        ClipData.newPlainText(
                                            context.getString(patches_url),
                                            url
                                        )
                                    )
                                    context.toast(context.getString(R.string.toast_copied_to_clipboard))
                                },
                                enabled = url.isNotEmpty()
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ContentCopy,
                                    contentDescription = stringResource(R.string.copy_to_clipboard)
                                )
                            }
                        }
                    )
                }

                val patchesClickable = patchCount > 0
                BundleListItem(
                    headlineText = stringResource(patches),
                    supportingText = stringResource(view_patches),
                    modifier = Modifier.clickable(
                        enabled = patchesClickable,
                        onClick = {
                            viewCurrentBundlePatches = true
                        }
                    )
                ) {
                    if (patchesClickable) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowRight,
                            stringResource(patches)
                        )
                    }
                }

                src.asRemoteOrNull?.let {
                    BundleListItem(
                        headlineText = stringResource(R.string.bundle_changelog),
                        supportingText = stringResource(R.string.bundle_view_changelog),
                        modifier = Modifier.clickable {
                            viewBundleChangelog = true
                        }
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowRight,
                            stringResource(R.string.bundle_changelog)
                        )
                    }
                }

                src.error?.let {
                    var showDialog by rememberSaveable {
                        mutableStateOf(false)
                    }
                    if (showDialog) ExceptionViewerDialog(
                        onDismiss = { showDialog = false },
                        text = remember(it) { it.stackTraceToString() }
                    )

                    BundleListItem(
                        headlineText = stringResource(R.string.patches_error),
                        supportingText = stringResource(R.string.patches_error_description),
                        trailingContent = {
                            Icon(
                                Icons.AutoMirrored.Outlined.ArrowRight,
                                null
                            )
                        },
                        modifier = Modifier.clickable { showDialog = true }
                    )
                }
                if (src.state is PatchBundleSource.State.Missing && !isLocal) {
                    BundleListItem(
                        headlineText = stringResource(R.string.patches_error),
                        supportingText = stringResource(R.string.patches_not_downloaded),
                        modifier = Modifier.clickable(onClick = onUpdate)
                    )
                }
            }
        }
    }
}

@Composable
private fun Tag(
    icon: ImageVector,
    text: String,
    isUrl: Boolean = false
) {
    val uriHandler = LocalUriHandler.current

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = if (isUrl) {
            Modifier
                .clickable {
                    try {
                        uriHandler.openUri(text)
                    } catch (_: Exception) {
                    }
                }
        } else
            Modifier,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isUrl) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        )
    }
}

internal const val DEFAULT_PATCH_RELEASES_URL = // FIXME
    "https://github.com/HundEdFeteTree/HappyFunTest/releases"
internal val GITHUB_SOURCE_REGEX =
    Regex("^(?:git@|ssh://git@|https?://|git://)?github\\.com[:/](.+)$", RegexOption.IGNORE_CASE)

internal fun initialGithubReleaseUrl(
    src: PatchBundleSource,
    manifestSource: String?
): String? {
    if (src.isDefault) return DEFAULT_PATCH_RELEASES_URL
    if (src !is LocalPatchBundle) return null
    return manifestSource
        ?.extractGithubRepoPath()
        ?.let(::buildGithubReleaseUrl)
}

internal fun extractGithubReleaseUrlFromDownload(downloadUrl: String?): String? {
    if (downloadUrl.isNullOrBlank()) return null
    val uri = runCatching { URI(downloadUrl) }.getOrNull() ?: return null
    val path = uri.path ?: return null
    val marker = "/releases"
    val index = path.indexOf(marker, ignoreCase = true)
    if (index == -1) return null
    val authority = uri.authority ?: return null
    val scheme = uri.scheme ?: "https"
    val releasePath = path.substring(0, index + marker.length)
    return buildString {
        append(scheme)
        append("://")
        append(authority)
        append(releasePath)
    }
}

internal fun String.extractGithubRepoPath(): String? {
    val cleaned = trim().removeSuffix(".git")
    val match = GITHUB_SOURCE_REGEX.find(cleaned) ?: return null
    val path = match.groupValues[1].trimStart('/', ':')
    return path.takeIf { it.isNotEmpty() }
}

internal fun buildGithubReleaseUrl(repoPath: String): String =
    "https://github.com/${repoPath.trim('/').trim()}/releases"
