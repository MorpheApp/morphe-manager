package app.revanced.manager.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.revanced.manager.patcher.patch.PatchInfo
import app.revanced.manager.ui.component.bundle.BundleTopBar
import app.revanced.manager.ui.component.bundle.PatchItem
import app.morphe.manager.R

data class AppliedPatchBundleUi(
    val uid: Int,
    val title: String,
    val version: String?,
    val patchInfos: List<PatchInfo>,
    val fallbackNames: List<String>,
    val bundleAvailable: Boolean
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AppliedPatchesDialog(
    bundles: List<AppliedPatchBundleUi>,
    onDismissRequest: () -> Unit
) {
    FullscreenDialog(onDismissRequest = onDismissRequest) {
        Scaffold(
            topBar = {
                BundleTopBar(
                    title = stringResource(R.string.applied_patches),
                    onBackClick = onDismissRequest,
                    backIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                )
            }
        ) { paddingValues ->
            if (bundles.isEmpty()) {
                Column(
                    modifier = Modifier
                        .padding(paddingValues)
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.applied_patches_empty),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                return@Scaffold
            }

            LazyColumnWithScrollbar(
                modifier = Modifier.padding(paddingValues),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(bundles, key = AppliedPatchBundleUi::uid) { bundle ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        val bundleTitle = buildString {
                            append(bundle.title)
                            bundle.version?.takeIf { it.isNotBlank() }?.let {
                                append(" (")
                                append(it)
                                append(")")
                            }
                        }

                        Text(
                            text = bundleTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        bundle.patchInfos.forEachIndexed { index, patch ->
                            val versionKey = "versions-$index"
                            val optionsKey = "options-$index"
                            var expandVersions by rememberSaveable(bundle.uid, patch.name, versionKey) { mutableStateOf(false) }
                            var expandOptions by rememberSaveable(bundle.uid, patch.name, optionsKey) { mutableStateOf(false) }

                            PatchItem(
                                patch = patch,
                                expandVersions = expandVersions,
                                onExpandVersions = { expandVersions = !expandVersions },
                                expandOptions = expandOptions,
                                onExpandOptions = { expandOptions = !expandOptions },
                                showCompatibilityMeta = false
                            )

                            if (index != bundle.patchInfos.lastIndex) {
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }

                        if (bundle.fallbackNames.isNotEmpty()) {
                            if (bundle.patchInfos.isNotEmpty()) {
                                Text(
                                    text = stringResource(R.string.applied_patches_bundle_missing),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            } else if (!bundle.bundleAvailable) {
                                Text(
                                    text = stringResource(R.string.applied_patches_bundle_missing),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }

                            bundle.fallbackNames.forEach { patchName ->
                                Text(
                                    text = "\u2022 $patchName",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(start = 8.dp, top = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
