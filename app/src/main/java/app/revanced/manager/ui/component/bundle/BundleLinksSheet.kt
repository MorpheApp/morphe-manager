package app.revanced.manager.ui.component.bundle

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.List
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.revanced.manager.data.platform.NetworkInfo
import app.revanced.manager.domain.bundles.PatchBundleSource
import app.revanced.manager.domain.bundles.PatchBundleSource.Extensions.asRemoteOrNull
import app.revanced.manager.util.simpleMessage
import app.revanced.manager.util.toast
import app.universal.revanced.manager.R
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Brands
import compose.icons.fontawesomeicons.brands.Github
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BundleLinksSheet(
    bundleTitle: String,
    catalogUrl: String?,
    onReleaseClick: () -> Unit,
    onCatalogClick: () -> Unit,
    onDismissRequest: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = stringResource(R.string.bundle_links_title, bundleTitle),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            LinkOptionRow(
                icon = {
                    Icon(
                        FontAwesomeIcons.Brands.Github,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                },
                text = stringResource(R.string.bundle_link_release)
            ) {
                coroutineScope.launch {
                    sheetState.hide()
                    onDismissRequest()
                    onReleaseClick()
                }
            }
            LinkOptionRow(
                icon = {
                    Icon(
                        Icons.Outlined.List,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                },
                text = stringResource(
                    if (catalogUrl != null) R.string.bundle_link_catalog else R.string.bundle_catalog_unavailable
                ),
                enabled = catalogUrl != null
            ) {
                coroutineScope.launch {
                    sheetState.hide()
                    onDismissRequest()
                    onCatalogClick()
                }
            }
        }
    }
}

@Composable
fun LinkOptionRow(
    icon: @Composable () -> Unit,
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (enabled) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.surface
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val iconTint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
        CompositionLocalProvider(LocalContentColor provides iconTint) {
            icon()
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
            modifier = Modifier.weight(1f)
        )
    }
}

suspend fun openBundleReleasePage(
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

fun openBundleCatalogPage(
    catalogUrl: String?,
    context: android.content.Context,
    uriHandler: androidx.compose.ui.platform.UriHandler
) {
    if (catalogUrl.isNullOrBlank()) {
        context.toast(context.getString(R.string.bundle_catalog_unavailable))
        return
    }
    runCatching {
        uriHandler.openUri(catalogUrl)
    }.onFailure {
        context.toast(context.getString(R.string.bundle_catalog_unavailable))
    }
}
