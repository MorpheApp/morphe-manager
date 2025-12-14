package app.revanced.manager.ui.component.morphe.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.revanced.manager.domain.bundles.PatchBundleSource
import app.revanced.manager.domain.repository.PatchBundleRepository
import app.revanced.manager.patcher.patch.PatchInfo
import app.revanced.manager.ui.component.ArrowButton
import kotlinx.coroutines.flow.mapNotNull
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MorpheBundlePatchesSheet(
    onDismissRequest: () -> Unit,
    src: PatchBundleSource
) {
    val patchBundleRepository: PatchBundleRepository = koinInject()
    val patches by remember(src.uid) {
        patchBundleRepository.bundleInfoFlow.mapNotNull { it[src.uid]?.patches }
    }.collectAsStateWithLifecycle(emptyList())

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollState = rememberScrollState()
    val isLoading = patches.isEmpty()

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentWindowInsets = { WindowInsets.systemBars },
        scrimColor = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(start = 24.dp, bottom = 24.dp, end = 24.dp)
        ) {
            // Header Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Styled Header with Count
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
                                imageVector = Icons.Outlined.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Column for title and count
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.patches),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Patch counter
                        if (!isLoading) {
                            Text(
                                text = "${patches.size} ${stringResource(R.string.patches).lowercase()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Text(
                                text = "...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Content
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    patches.forEach { patch ->
                        var expandVersions by rememberSaveable(src.uid, patch.name, "versions") {
                            mutableStateOf(false)
                        }
                        var expandOptions by rememberSaveable(src.uid, patch.name, "options") {
                            mutableStateOf(false)
                        }

                        PatchItemCompact(
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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PatchItemCompact(
    patch: PatchInfo,
    expandVersions: Boolean,
    onExpandVersions: () -> Unit,
    expandOptions: Boolean,
    onExpandOptions: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (patch.options.isNullOrEmpty()) Modifier
                else Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onExpandOptions)
            ),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                if (!patch.options.isNullOrEmpty()) {
                    ArrowButton(expanded = expandOptions, onClick = null)
                }
            }

            // Description
            patch.description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Compatibility info
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (patch.compatiblePackages.isNullOrEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PatchInfoChipCompact(
                            text = "$PACKAGE_ICON ${stringResource(R.string.patches_view_any_package)}"
                        )
                        PatchInfoChipCompact(
                            text = "$VERSION_ICON ${stringResource(R.string.patches_view_any_version)}"
                        )
                    }
                } else {
                    patch.compatiblePackages.forEach { compatiblePackage ->
                        val packageName = compatiblePackage.packageName
                        val versions = compatiblePackage.versions.orEmpty().reversed()

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            PatchInfoChipCompact(
                                modifier = Modifier.align(Alignment.CenterVertically),
                                text = "$PACKAGE_ICON $packageName"
                            )

                            if (versions.isNotEmpty()) {
                                if (expandVersions) {
                                    versions.forEach { version ->
                                        PatchInfoChipCompact(
                                            modifier = Modifier.align(Alignment.CenterVertically),
                                            text = "$VERSION_ICON $version"
                                        )
                                    }
                                } else {
                                    PatchInfoChipCompact(
                                        modifier = Modifier.align(Alignment.CenterVertically),
                                        text = "$VERSION_ICON ${versions.first()}"
                                    )
                                }
                                if (versions.size > 1) {
                                    PatchInfoChipCompact(
                                        onClick = onExpandVersions,
                                        text = if (expandVersions)
                                            stringResource(R.string.less)
                                        else
                                            "+${versions.size - 1}"
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Options
            if (!patch.options.isNullOrEmpty()) {
                AnimatedVisibility(visible = expandOptions) {
                    Column(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        patch.options.forEach { option ->
                            OutlinedCard(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.outlinedCardColors(
                                    containerColor = Color.Transparent
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = option.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = option.description,
                                        style = MaterialTheme.typography.bodySmall
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

@Composable
private fun PatchInfoChipCompact(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    text: String
) {
    val shape = RoundedCornerShape(8.dp)
    val cardModifier = if (onClick != null) {
        Modifier
            .clip(shape)
            .clickable(onClick = onClick)
    } else {
        Modifier
    }

    OutlinedCard(
        modifier = modifier.then(cardModifier),
        colors = CardDefaults.outlinedCardColors(
            containerColor = Color.Transparent
        ),
        shape = shape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.20f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

const val PACKAGE_ICON = "\uD83D\uDCE6"
const val VERSION_ICON = "\uD83C\uDFAF"
