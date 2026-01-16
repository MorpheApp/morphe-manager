package app.revanced.manager.ui.component.morphe.home

import android.content.pm.PackageInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.revanced.manager.data.room.apps.installed.InstalledApp
import app.revanced.manager.ui.component.AppIcon
import app.revanced.manager.ui.component.AppLabel
import app.revanced.manager.ui.component.LoadingIndicator
import app.revanced.manager.ui.component.morphe.shared.AnimatedBackground
import app.revanced.manager.ui.component.morphe.shared.MorpheCard
import app.revanced.manager.ui.component.morphe.shared.SectionTitle
import app.revanced.manager.ui.viewmodel.InstalledAppsViewModel
import app.revanced.manager.ui.viewmodel.MorpheThemeSettingsViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun MorpheInstalledAppsScreen(
    onAppClick: (InstalledApp) -> Unit,
    viewModel: InstalledAppsViewModel = koinViewModel(),
    themeViewModel: MorpheThemeSettingsViewModel = koinViewModel()
) {
    val installedApps by viewModel.apps.collectAsStateWithLifecycle(initialValue = null)
    val backgroundType by themeViewModel.prefs.backgroundType.getAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Animated background
        AnimatedBackground(type = backgroundType)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Screen title
            SectionTitle(
                text = stringResource(R.string.installed),
                icon = Icons.Outlined.Apps
            )

            when {
                // Loading state
                installedApps == null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingIndicator()
                    }
                }

                // Empty state
                installedApps!!.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.no_patched_apps_found),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Installed apps list
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                            items = installedApps!!,
                            key = { it.currentPackageName }
                        ) { app ->
                            MorpheInstalledAppCard(
                                installedApp = app,
                                packageInfo = viewModel.packageInfoMap[app.currentPackageName],
                                isMissingInstall = app.currentPackageName in viewModel.missingPackages,
                                bundleSummaries = viewModel.bundleSummaries[app.currentPackageName].orEmpty(),
                                onClick = { onAppClick(app) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Installed app card
 */
@Composable
private fun MorpheInstalledAppCard(
    installedApp: InstalledApp,
    packageInfo: PackageInfo?,
    isMissingInstall: Boolean,
    bundleSummaries: List<InstalledAppsViewModel.AppBundleSummary>,
    onClick: () -> Unit
) {
    MorpheCard(
        elevation = 2.dp,
        cornerRadius = 20.dp,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(
                packageInfo = packageInfo,
                contentDescription = null,
                modifier = Modifier.size(56.dp)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AppLabel(
                    packageInfo = packageInfo,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    defaultText = installedApp.currentPackageName
                )

                Text(
                    text = installedApp.currentPackageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val versionText = installedApp.version
                    .takeIf { it.isNotBlank() }
                    ?.let { if (it.startsWith("v")) it else "v$it" }

                val installTypeText =
                    stringResource(installedApp.installType.stringResource)

                val detailLine = listOfNotNull(versionText, installTypeText)
                    .joinToString(" • ")

                if (detailLine.isNotBlank()) {
                    Text(
                        text = detailLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                bundleSummaries.forEach { summary ->
                    val version = summary.version?.let {
                        if (it.startsWith("v")) it else "v$it"
                    }

                    Text(
                        text = listOfNotNull(summary.title, version)
                            .joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                    )
                }

                if (isMissingInstall) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ) {
                        Text(
                            text = stringResource(R.string.patches_missing),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(
                                horizontal = 10.dp,
                                vertical = 4.dp
                            )
                        )
                    }
                }
            }
        }
    }
}
