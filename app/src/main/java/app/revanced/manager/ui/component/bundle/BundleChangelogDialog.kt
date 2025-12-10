package app.revanced.manager.ui.component.bundle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.revanced.manager.domain.bundles.RemotePatchBundle
import app.revanced.manager.network.dto.ReVancedAsset
import app.revanced.manager.ui.component.ColumnWithScrollbar
import app.revanced.manager.ui.component.FullscreenDialog
import app.revanced.manager.ui.component.settings.Changelog
import app.revanced.manager.util.relativeTime
import app.revanced.manager.util.simpleMessage
import app.morphe.manager.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BundleChangelogDialog(
    src: RemotePatchBundle,
    onDismissRequest: () -> Unit,
    showTopBar: Boolean = true
) {
    var refreshKey by remember { mutableStateOf(0) }
    var state: BundleChangelogState by remember { mutableStateOf(BundleChangelogState.Loading) }

    LaunchedEffect(src.uid, refreshKey) {
        state = BundleChangelogState.Loading
        state = try {
            val asset = src.fetchLatestReleaseInfo()
            BundleChangelogState.Success(asset)
        } catch (t: Throwable) {
            BundleChangelogState.Error(t)
        }
    }

    FullscreenDialog(
        onDismissRequest = onDismissRequest,
    ) {
        Scaffold(
            topBar = {
                if (showTopBar) {
                    BundleTopBar(
                        title = stringResource(R.string.bundle_changelog),
                        onBackClick = onDismissRequest,
                        backIcon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    )
                }
            }
        ) { paddingValues ->
            when (val current = state) {
                BundleChangelogState.Loading -> BundleChangelogLoading(
                    paddingValues = if (showTopBar) paddingValues else PaddingValues(0.dp),
                    showTopBar = showTopBar
                )
                is BundleChangelogState.Error -> BundleChangelogError(
                    paddingValues = if (showTopBar) paddingValues else PaddingValues(0.dp),
                    error = current.throwable,
                    onRetry = { refreshKey++ },
                    showTopBar = showTopBar
                )
                is BundleChangelogState.Success -> BundleChangelogContent(
                    paddingValues = if (showTopBar) paddingValues else PaddingValues(0.dp),
                    asset = current.asset,
                    showTopBar = showTopBar
                )
            }
        }
    }
}

@Composable
private fun BundleChangelogLoading(
    paddingValues: PaddingValues,
    showTopBar: Boolean = true
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .then(
                if (!showTopBar) Modifier.statusBarsPadding()
                else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.changelog_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun BundleChangelogError(
    paddingValues: PaddingValues,
    error: Throwable,
    onRetry: () -> Unit,
    showTopBar: Boolean = true
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .then(
                if (!showTopBar) Modifier.statusBarsPadding()
                else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(
                    R.string.bundle_changelog_error,
                    error.simpleMessage().orEmpty()
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Button(onClick = onRetry) {
                Text(stringResource(R.string.bundle_changelog_retry))
            }
        }
    }
}

@Composable
private fun BundleChangelogContent(
    paddingValues: PaddingValues,
    asset: ReVancedAsset,
    showTopBar: Boolean = true
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

    ColumnWithScrollbar(
        modifier = Modifier
            .fillMaxWidth()
            .padding(paddingValues)
            .then(
                if (!showTopBar) Modifier.statusBarsPadding()
                else Modifier
            )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Changelog(
                markdown = markdown.ifBlank {
                    stringResource(R.string.bundle_changelog_empty)
                },
                version = asset.version,
                publishDate = publishDate
            )
        }
    }
}

private sealed interface BundleChangelogState {
    data object Loading : BundleChangelogState
    data class Success(val asset: ReVancedAsset) : BundleChangelogState
    data class Error(val throwable: Throwable) : BundleChangelogState
}

private val doubleBracketLinkRegex =
    Regex("""\[\[([^\]]+)]\(([^)]+)\)]""")

private fun String.sanitizePatchChangelogMarkdown(): String =
    doubleBracketLinkRegex.replace(this) { match ->
        val label = match.groupValues[1]
        val link = match.groupValues[2]
        "[\\[$label\\]]($link)"
    }
