package app.revanced.manager.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.revanced.manager.ui.component.LazyColumnWithScrollbar
import app.revanced.manager.ui.component.bundle.BundleItem
import app.revanced.manager.ui.viewmodel.BundleListViewModel
import app.revanced.manager.util.EventEffect
import app.morphe.manager.R
import app.revanced.manager.domain.bundles.PatchBundleSource
import app.revanced.manager.domain.bundles.PatchBundleSource.Extensions.asRemoteOrNull
import app.revanced.manager.domain.bundles.PatchBundleSource.Extensions.isDefault
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.Flow
import org.koin.androidx.compose.koinViewModel
import androidx.compose.foundation.interaction.MutableInteractionSource
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BundleListScreen(
    viewModel: BundleListViewModel = koinViewModel(),
    eventsFlow: Flow<BundleListViewModel.Event>,
    setSelectedSourceCount: (Int) -> Unit,
    showOrderDialog: Boolean = false,
    onDismissOrderDialog: () -> Unit = {},
    onScrollStateChange: (Boolean) -> Unit = {}
) {
    val patchCounts by viewModel.patchCounts.collectAsStateWithLifecycle(emptyMap())
    val sources by viewModel.sources.collectAsStateWithLifecycle(emptyList())
    val manualUpdateInfo by viewModel.manualUpdateInfo.collectAsStateWithLifecycle(emptyMap())
    val listState = rememberLazyListState()

    EventEffect(eventsFlow) {
        viewModel.handleEvent(it)
    }
    LaunchedEffect(viewModel.selectedSources.size) {
        setSelectedSourceCount(viewModel.selectedSources.size)
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { onScrollStateChange(it) }
    }

    PullToRefreshBox(
        onRefresh = viewModel::refresh,
        isRefreshing = viewModel.isRefreshing
    ) {
        LazyColumnWithScrollbar(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            items(
                sources,
                key = { it.uid }
            ) { source ->
                BundleItem(
                    src = source,
                    patchCount = patchCounts[source.uid] ?: 0,
                    manualUpdateInfo = manualUpdateInfo[source.uid],
                    onDelete = {
                        viewModel.delete(source)
                    },
                    onUpdate = {
                        viewModel.update(source)
                    },
                    selectable = viewModel.selectedSources.size > 0,
                    onSelect = {
                        viewModel.selectedSources.add(source.uid)
                    },
                    isBundleSelected = source.uid in viewModel.selectedSources,
                    toggleSelection = { bundleIsNotSelected ->
                        if (bundleIsNotSelected) {
                            viewModel.selectedSources.add(source.uid)
                        } else {
                            viewModel.selectedSources.remove(source.uid)
                        }
                    }
                )
            }
        }
    }

    if (showOrderDialog) {
        BundleOrderDialog(
            bundles = sources,
            onDismissRequest = onDismissOrderDialog,
            onConfirm = { ordered ->
                viewModel.reorder(ordered.map(PatchBundleSource::uid))
                onDismissOrderDialog()
            }
        )
    }
}

@Composable
private fun BundleOrderDialog(
    bundles: List<PatchBundleSource>,
    onDismissRequest: () -> Unit,
    onConfirm: (List<PatchBundleSource>) -> Unit
) {
    val workingOrder = remember(bundles) { bundles.toMutableStateList() }
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        workingOrder.add(to.index, workingOrder.removeAt(from.index))
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = { onConfirm(workingOrder.toList()) }, enabled = workingOrder.isNotEmpty()) {
                Text(text = stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(R.string.cancel))
            }
        },
        title = { Text(text = stringResource(R.string.bundle_reorder_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.bundle_reorder_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyColumnWithScrollbar(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    state = lazyListState
                ) {
                    itemsIndexed(workingOrder, key = { _, bundle -> bundle.uid }) { index, bundle ->
                        val interactionSource = remember { MutableInteractionSource() }
                        ReorderableItem(reorderableState, key = bundle.uid) { _ ->
                            BundleOrderRow(
                                index = index,
                                bundle = bundle,
                                interactionSource = interactionSource
                            )
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun ReorderableCollectionItemScope.BundleOrderRow(
    index: Int,
    bundle: PatchBundleSource,
    interactionSource: MutableInteractionSource
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = (index + 1).toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = bundle.displayTitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            val typeLabel = stringResource(
                when {
                    bundle.isDefault -> R.string.bundle_type_preinstalled
                    bundle.asRemoteOrNull != null -> R.string.bundle_type_remote
                    else -> R.string.bundle_type_local
                }
            )
            Text(
                text = typeLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(
            onClick = {},
            interactionSource = interactionSource,
            modifier = Modifier.longPressDraggableHandle()
        ) {
            Icon(
                imageVector = Icons.Filled.DragHandle,
                contentDescription = stringResource(R.string.drag_handle)
            )
        }
    }
}
