package app.revanced.manager.ui.component.morphe.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.revanced.manager.domain.bundles.RemotePatchBundle
import app.revanced.manager.network.dto.ReVancedAsset
import app.revanced.manager.ui.component.settings.Changelog
import app.revanced.manager.util.relativeTime
import app.revanced.manager.util.simpleMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MorpheBundleChangelogSheet(
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

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        scrimColor = Color.Transparent
    ) {
        when (val current = state) {
            BundleChangelogState.Loading -> BundleChangelogSheetLoading()
            is BundleChangelogState.Error -> BundleChangelogSheetError(
                error = current.throwable,
                onRetry = {}
            )
            is BundleChangelogState.Success -> BundleChangelogSheetContent(
                asset = current.asset
            )
        }
    }
}

@Composable
private fun BundleChangelogSheetLoading() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator()
            Text(
                text = stringResource(R.string.changelog_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun BundleChangelogSheetError(
    error: Throwable,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(vertical = 48.dp),
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
private fun BundleChangelogSheetContent(
    asset: ReVancedAsset
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val publishDate = remember(asset.createdAt) {
        asset.createdAt.relativeTime(context)
    }
    val markdown = remember(asset.description) {
        asset.description
            .replace("\r\n", "\n")
            .sanitizePatchChangelogMarkdown()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .verticalScroll(scrollState)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Styled Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Column {
                    Text(
                        text = stringResource(R.string.bundle_changelog),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = publishDate,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // Changelog content
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

private val doubleBracketLinkRegex = Regex("""\[\[([^]]+)]\(([^)]+)\)]""")

private fun String.sanitizePatchChangelogMarkdown(): String =
    doubleBracketLinkRegex.replace(this) { match ->
        val label = match.groupValues[1]
        val link = match.groupValues[2]
        "[\\[$label\\]]($link)"
    }
