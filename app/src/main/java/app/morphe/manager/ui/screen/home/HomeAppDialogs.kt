/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.domain.repository.SourceMuteRepository
import app.morphe.manager.domain.repository.appsToKeepFrom
import app.morphe.manager.patcher.patch.PatchInfo
import app.morphe.manager.patcher.patch.appColorFor
import app.morphe.manager.ui.model.HomeAppItem
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.util.toast
import app.morphe.manager.util.withToast
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.util.Locale

/**
 * Dialog that shows available patches for a specific app, one block per source they come from.
 * Shown when the user swipes right on a home app card.
 */
@Composable
fun AppPatchesDialog(
    item: HomeAppItem,
    patchesByBundle: Map<Int, List<PatchInfo>>,
    bundleNames: Map<Int, String>,
    onDismiss: () -> Unit,
    isLoading: Boolean = false
) {
    val sourcesByUid = rememberSourcesByUid()
    val sourceAccents = patchesByBundle.keys.associateWith { uid ->
        key(uid) { sourcesByUid[uid]?.let { rememberBundleAccent(it) } }
    }

    // Sources with at least one patch written for this app come first, then those with universal
    // patches alone, each by name. Within a source its specific patches lead its universal ones
    val sections = remember(patchesByBundle, bundleNames, item.packageName, sourcesByUid, sourceAccents) {
        val isMultiBundle = patchesByBundle.size > 1
        patchesByBundle.entries
            .sortedWith(
                compareBy(
                    { (_, patches) -> patches.all { it.isUniversal } },
                    { (uid, _) -> bundleNames[uid] ?: uid.toString() }
                )
            )
            .map { (uid, patches) ->
                val (universal, specific) = patches.partition { it.isUniversal }
                PatchListSection(
                    key = uid.toString(),
                    title = bundleNames[uid] ?: uid.toString(),
                    patches = specific.sortedBy { it.name } + universal.sortedBy { it.name },
                    packageName = item.packageName,
                    // Several sources side by side each wear the color of their icon. One whose icon
                    // has none gets a color derived from the uid, so it keeps it between openings
                    accentColor = if (isMultiBundle) {
                        sourceAccents[uid] ?: run {
                            val hue = ((uid.hashCode() * 2654435761L) and 0xFFFFFFFFL).toFloat() % 360f
                            Color.hsl(hue = hue, saturation = 0.55f, lightness = 0.60f)
                        }
                    } else null,
                    icon = { modifier ->
                        val source = sourcesByUid[uid]
                        if (source != null) {
                            BundleIcon(bundle = source, modifier = modifier, enabled = source.enabled)
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Layers,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = modifier
                            )
                        }
                    }
                )
            }
    }
    val patchCount = sections.sumOf { it.patches.size }
    // The app's own color, as whichever source names one declares it
    val brandColor = remember(patchesByBundle, item.packageName) {
        patchesByBundle.values.asSequence().flatten().appColorFor(item.packageName)
    }

    PatchListDialog(
        icon = { modifier ->
            AppIcon(
                packageInfo = item.packageInfo,
                packageName = if (item.packageInfo == null) item.packageName else null,
                contentDescription = null,
                placeholderGradientColors = item.gradientColors,
                modifier = modifier
            )
        },
        title = item.displayName,
        subtitle = pluralStringResource(R.plurals.patch_count, patchCount, patchCount.toString()),
        sections = sections,
        isLoading = isLoading,
        saveStateKey = "app_patches_${item.id}",
        onDismiss = onDismiss,
        accentColor = brandColor
    )
}

/**
 * Confirmation dialog asking user whether to hide the app.
 */
@Composable
internal fun HideAppDialog(
    item: HomeAppItem,
    onDismiss: () -> Unit,
    onHide: () -> Unit
) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.home_app_hide_title),
        footer = {
            AppDialogButtonRow(
                primaryText = stringResource(R.string.hide),
                primaryIcon = Icons.Outlined.VisibilityOff,
                onPrimaryClick = onHide,
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        },
        padding = DialogPadding.Compact
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Original app card preview
            AppCardLayout(
                gradientColors = item.gradientColors,
                onClick = {},
                modifier = Modifier.fillMaxWidth()
            ) {
                AppCardContent(
                    packageName = item.id,
                    packageInfo = item.packageInfo,
                    displayName = item.displayName,
                    subtitle = stringResource(R.string.home_app_will_be_hidden),
                    gradientColors = item.gradientColors,
                )
            }

            // Explanation text
            Text(
                text = stringResource(R.string.home_app_hide_message),
                style = MaterialTheme.typography.bodyLarge,
                color = LocalDialogSecondaryTextColor.current,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Dialog listing all hidden apps.
 *
 * Swipe gestures (disabled in multi-select mode):
 * - Swipe LEFT  → Patches dialog
 * - Swipe RIGHT → Unhide
 *
 * Long-press enters multi-select; bulk unhide via footer button.
 */
@Composable
internal fun HiddenAppsDialog(
    hiddenAppItems: List<HomeAppItem>,
    onUnhide: (String) -> Unit,
    onUnhideMultiple: (Set<String>) -> Unit = {},
    onShowPatches: (HomeAppItem) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val itemSpacing = rememberWindowSize().itemSpacing
    val isMultiSelectMode = remember { mutableStateOf(false) }
    val selectedPackages = rememberSelectionState<String>()

    // Sync selection with current item list; exit mode if no items remain
    LaunchedEffect(hiddenAppItems) {
        val currentPackages = hiddenAppItems.mapTo(mutableSetOf()) { it.id }
        selectedPackages.retain { it in currentPackages }
        if (selectedPackages.isEmpty) isMultiSelectMode.value = false
    }

    val view = LocalView.current
    val density = LocalDensity.current
    val actionThresholdPx = with(density) { 90.dp.toPx() }

    val patchesLabel = stringResource(R.string.patches)
    val unhideLabel = stringResource(R.string.unhide)
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    val tertiaryContainer = MaterialTheme.colorScheme.tertiaryContainer
    val onTertiaryContainer = MaterialTheme.colorScheme.onTertiaryContainer

    val startConfig = remember(unhideLabel, tertiaryContainer, onTertiaryContainer) {
        SwipeActionConfig(
            icon = Icons.Outlined.Visibility,
            label = unhideLabel,
            containerColor = tertiaryContainer,
            contentColor = onTertiaryContainer
        )
    }
    val endConfig = remember(patchesLabel, primaryContainer, onPrimaryContainer) {
        SwipeActionConfig(
            icon = Icons.Outlined.Extension,
            label = patchesLabel,
            containerColor = primaryContainer,
            contentColor = onPrimaryContainer
        )
    }

    AppDialog(
        onDismissRequest = onDismiss,
        dismissOnClickOutside = !isMultiSelectMode.value,
        title = stringResource(R.string.home_app_hidden_apps_title),
        footer = {
            AppDialogOutlinedButton(
                text = stringResource(R.string.close),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        },
        bottomBar = if (isMultiSelectMode.value) {
            {
                MultiSelectShell(
                    visible = true,
                    onBack = {
                        isMultiSelectMode.value = false
                        selectedPackages.clear()
                    }
                ) {
                    SelectionActionBar(
                        selectedCount = selectedPackages.size,
                        totalCount = hiddenAppItems.size,
                        onSelectAll = {
                            selectedPackages.setAll(hiddenAppItems.map { it.id })
                        },
                        onDeselectAll = { selectedPackages.clear() },
                        actions = listOf(
                            SelectionAction(
                                icon = Icons.Outlined.Visibility,
                                label = stringResource(R.string.unhide),
                                onClick = context.withToast(stringResource(R.string.unhide_done)) {
                                    onUnhideMultiple(selectedPackages.keys.toSet())
                                    isMultiSelectMode.value = false
                                    selectedPackages.clear()
                                },
                                tone = ActionTone.Tertiary
                            )
                        ),
                        onCancel = {
                            isMultiSelectMode.value = false
                            selectedPackages.clear()
                        }
                    )
                }
            }
        } else null,
        padding = DialogPadding.Compact,
        scrollable = false
    ) {
        if (hiddenAppItems.isEmpty()) {
            HomeEmptyState(
                icon = Icons.Outlined.Visibility,
                title = stringResource(R.string.home_app_no_hidden)
            )
        } else {
            val listState = rememberLazyListState()
            Box(modifier = Modifier.fillMaxWidth()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(itemSpacing)
                ) {
                    items(
                        items = hiddenAppItems,
                        key = { it.id }
                    ) { item ->
                        val isSelected = selectedPackages.contains(item.id)
                        val offsetX = remember(item.id) { Animatable(0f) }

                        // Snap card back when entering multi-select
                        LaunchedEffect(isMultiSelectMode.value) {
                            if (isMultiSelectMode.value) offsetX.animateTo(0f, tween(200))
                        }

                        SelectableCard(
                            modifier = Modifier.animatedListItem(this),
                            isSelected = isSelected,
                            isSelectionMode = isMultiSelectMode.value
                        ) {
                            SwipeableCardContainer(
                                offsetX = offsetX,
                                actionThresholdPx = actionThresholdPx,
                                onSwipeToStart = { onUnhide(item.id) },
                                onSwipeToEnd = { onShowPatches(item) },
                                startHaptic = HapticFeedbackConstants.LONG_PRESS,
                                endHaptic = HapticFeedbackConstants.VIRTUAL_KEY,
                                enabled = !isMultiSelectMode.value,
                                background = { startProgress, endProgress ->
                                    SwipeBackground(
                                        startProgress = startProgress,
                                        endProgress = endProgress,
                                        startConfig = startConfig,
                                        endConfig = endConfig,
                                        modifier = Modifier
                                            .matchParentSize()
                                            .clip(RoundedCornerShape(24.dp))
                                    )
                                }
                            ) {
                                AppCardLayout(
                                    gradientColors = item.gradientColors,
                                    onClick = {
                                        if (isMultiSelectMode.value) {
                                            selectedPackages.toggle(item.id)
                                        } else {
                                            onUnhide(item.id)
                                        }
                                    },
                                    onLongClick = {
                                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                        isMultiSelectMode.value = true
                                        selectedPackages.toggle(item.id)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    AppCardContent(
                                        packageName = item.id,
                                        packageInfo = item.packageInfo,
                                        displayName = item.displayName,
                                        subtitle = if (isMultiSelectMode.value) null
                                        else stringResource(R.string.home_app_hidden_apps_hint),
                                        gradientColors = item.gradientColors,
                                    )
                                }
                            }
                        }
                    }
                }

                ListScrollbar(
                    listState = listState,
                    modifier = Modifier.offset(x = LocalDialogHorizontalInset.current)
                )

                ScrollToTopButton(
                    listState = listState,
                    modifier = Modifier.offset(x = LocalDialogHorizontalInset.current)
                )
            }
        }
    }
}

/**
 * Which sources the selected apps are patched from.
 *
 * Asked of the apps rather than of the sources, which is the way round the question comes up: the
 * user is looking at apps, and several of them usually want the same answer. A source stays on
 * everywhere else - this only decides whether these apps are offered it.
 */
@Composable
fun AppPatchSourcesDialog(
    packages: Set<String>,
    onDismiss: () -> Unit
) {
    val patchBundleRepository: PatchBundleRepository = koinInject()
    val sourceMuteRepository: SourceMuteRepository = koinInject()
    val scope = rememberCoroutineScope()

    val context = LocalContext.current
    val lastSourceMessage = stringResource(R.string.home_app_patch_sources_last)

    val bundleInfo by patchBundleRepository.bundleInfoFlow.collectAsStateWithLifecycle(emptyMap())
    val sourcesByUid = rememberSourcesByUid()
    // Keyed by app, the way every rule below asks the question
    val keptFrom by sourceMuteRepository.mutedSources.collectAsStateWithLifecycle(emptyMap())

    // Which sources have anything to offer each app. A universal patch names no app, so the source
    // carrying it reaches every one of them
    val coveredBy: Map<String, Set<Int>> = remember(bundleInfo, packages) {
        packages.associateWith { packageName ->
            bundleInfo.entries.mapNotNullTo(mutableSetOf()) { (uid, info) ->
                uid.takeIf {
                    info.patches.any { patch ->
                        patch.isUniversal ||
                                patch.compatiblePackages?.any { it.packageName == packageName } == true
                    }
                }
            }
        }
    }

    // Read the other way round for the list, and named the way the source list names them
    val rows = remember(coveredBy, keptFrom, sourcesByUid, packages) {
        coveredBy.values.flatten().distinct()
            .map { uid ->
                val reaches = packages.filter { uid in coveredBy[it].orEmpty() }
                val held = reaches.count { uid in keptFrom[it].orEmpty() }
                Triple(uid, sourcesByUid[uid]?.displayTitle ?: uid.toString(), held to reaches.size)
            }
            .sortedBy { (_, title, _) -> title.lowercase(Locale.ROOT) }
    }

    AppDialog(
        onDismissRequest = onDismiss,
        title = pluralStringResource(
            R.plurals.home_app_patch_sources_title,
            packages.size,
            packages.size.toString()
        ),
        footer = {
            AppDialogOutlinedButton(
                text = stringResource(R.string.close),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        },
        padding = DialogPadding.Compact,
        scrollable = false
    ) {
        Text(
            text = stringResource(R.string.home_app_patch_sources_description),
            style = MaterialTheme.typography.bodyMedium,
            color = LocalDialogSecondaryTextColor.current,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Defaults.ContentPaddingSmall)
        )

        val listState = rememberLazyListState()
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingSmall)
        ) {
            items(items = rows, key = { (uid, _, _) -> uid }) { (uid, title, counts) ->
                val (held, reaches) = counts
                val state = when (held) {
                    0 -> ToggleableState.On
                    reaches -> ToggleableState.Off
                    else -> ToggleableState.Indeterminate
                }

                RadioSelectionCard(
                    selected = state == ToggleableState.On,
                    onSelect = {
                        scope.launch {
                            // Anything but "offered to all of them" is answered by offering it to
                            // all of them, so one tap always has a result the row can show
                            if (state == ToggleableState.On) {
                                val reached = appsToKeepFrom(uid, packages, coveredBy, keptFrom)
                                // Refusing to leave an app with nothing to patch from would
                                // otherwise read as a checkbox that does nothing
                                if (reached.isEmpty()) {
                                    context.toast(lastSourceMessage)
                                }
                                reached.forEach { sourceMuteRepository.mute(it, uid) }
                            } else {
                                packages.forEach { sourceMuteRepository.unmute(it, uid) }
                            }
                        }
                    },
                    role = Role.Checkbox,
                    leadingContent = { SelectionCheckIndicator(state) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .animatedListItem(this)
                ) {
                    IconTextRow(
                        modifier = Modifier.weight(1f),
                        // Drawn the way the source list draws it, so a source is recognized at a glance
                        leadingContent = sourcesByUid[uid]?.let { source ->
                            { BundleIcon(bundle = source, modifier = Modifier.size(40.dp)) }
                        },
                        title = title,
                        // Two things the box alone cannot say: that the selected apps disagree, and
                        // that a source only has patches for some of them, which is what decides how
                        // far a tap on it reaches
                        description = when {
                            state == ToggleableState.Indeterminate -> stringResource(
                                R.string.home_app_patch_sources_mixed,
                                (reaches - held).toString(),
                                reaches.toString()
                            )

                            reaches < packages.size -> stringResource(
                                R.string.home_app_patch_sources_covers,
                                reaches.toString(),
                                packages.size.toString()
                            )

                            else -> null
                        }
                    )
                }
            }
        }
    }
}
