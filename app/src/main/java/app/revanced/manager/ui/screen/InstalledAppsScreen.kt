package app.revanced.manager.ui.screen

import android.content.pm.PackageInfo
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.revanced.manager.data.room.apps.installed.InstallType
import app.revanced.manager.data.room.apps.installed.InstalledApp
import app.revanced.manager.ui.component.AppIcon
import app.revanced.manager.ui.component.AppLabel
import app.revanced.manager.ui.component.LazyColumnWithScrollbar
import app.revanced.manager.ui.component.LoadingIndicator
import app.revanced.manager.ui.component.haptics.HapticCheckbox
import app.revanced.manager.ui.viewmodel.InstalledAppsViewModel
import app.revanced.manager.ui.viewmodel.InstalledAppsViewModel.AppBundleSummary
import app.morphe.manager.R
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InstalledAppsScreen(
    onAppClick: (InstalledApp) -> Unit,
    viewModel: InstalledAppsViewModel = koinViewModel()
) {
    val installedApps by viewModel.apps.collectAsStateWithLifecycle(initialValue = null)
    val selectionActive = viewModel.selectedApps.isNotEmpty()

    when {
        installedApps == null -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator()
            }
        }

        installedApps!!.isEmpty() -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.no_patched_apps_found),
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }

        else -> {
            LazyColumnWithScrollbar(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top,
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(
                    installedApps!!,
                    key = { it.currentPackageName }
                ) { installedApp ->
                        val packageName = installedApp.currentPackageName
                        val packageInfo = viewModel.packageInfoMap[packageName]
                        val isSaved = installedApp.installType == InstallType.SAVED
                        val isMissingInstall = packageName in viewModel.missingPackages
                        val isSelectable = isSaved || isMissingInstall
                        val isSelected = packageName in viewModel.selectedApps
                        val bundleSummaries = viewModel.bundleSummaries[packageName].orEmpty()

                        InstalledAppCard(
                            installedApp = installedApp,
                            packageInfo = packageInfo,
                            isSelected = isSelected,
                            selectionActive = selectionActive,
                            isSelectable = isSelectable,
                            isMissingInstall = isMissingInstall,
                            bundleSummaries = bundleSummaries,
                            onClick = {
                                when {
                                selectionActive && isSelectable -> viewModel.toggleSelection(installedApp)
                                selectionActive -> {}
                                else -> onAppClick(installedApp)
                            }
                        },
                        onLongClick = {
                            if (isSelectable) {
                                viewModel.toggleSelection(installedApp)
                            } else {
                                onAppClick(installedApp)
                            }
                        },
                        onSelectionChange = { checked ->
                            viewModel.setSelection(installedApp, checked)
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InstalledAppCard(
    installedApp: InstalledApp,
    packageInfo: PackageInfo?,
    isSelected: Boolean,
    selectionActive: Boolean,
    isSelectable: Boolean,
    isMissingInstall: Boolean,
    bundleSummaries: List<InstalledAppsViewModel.AppBundleSummary>,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSelectionChange: (Boolean) -> Unit,
) {
    val cardShape = RoundedCornerShape(16.dp)
    val elevation = if (isSelected) 6.dp else 2.dp
                    val formattedVersion = installedApp.version
                        .takeIf { it.isNotBlank() }
                        ?.let(::formatVersion)
                    val detailLine = listOfNotNull(
                        formattedVersion,
                        stringResource(installedApp.installType.stringResource)
                    ).joinToString(" • ")

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(cardShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = cardShape,
        tonalElevation = elevation,
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(elevation)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionActive) {
                HapticCheckbox(
                    checked = isSelected,
                    onCheckedChange = if (isSelectable) onSelectionChange else null,
                    enabled = isSelectable
                )
            }
            AppIcon(
                packageInfo = packageInfo,
                contentDescription = null,
                modifier = Modifier.size(48.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AppLabel(
                    packageInfo = packageInfo,
                    style = MaterialTheme.typography.titleMedium,
                    defaultText = installedApp.currentPackageName
                )
                Text(
                    text = installedApp.currentPackageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (detailLine.isNotBlank()) {
                    Text(
                        text = detailLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (bundleSummaries.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        bundleSummaries.forEach { summary ->
                            val versionText = summary.version?.let(::formatVersion)
                            val bundleLine = listOfNotNull(summary.title, versionText).joinToString(" • ")
                            Text(
                                text = bundleLine,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (isMissingInstall) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusChip(
                            text = stringResource(R.string.patches_missing),
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}

private fun formatVersion(raw: String): String =
    if (raw.startsWith("v", ignoreCase = true)) raw else "v$raw"

@Composable
private fun StatusChip(
    text: String,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = containerColor,
        contentColor = contentColor
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}
