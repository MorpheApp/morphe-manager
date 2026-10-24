/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import android.view.HapticFeedbackConstants
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
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
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.*
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.util.Locale

/**
 * Dialog that shows available patches for a specific app, one block per source they come from.
 * Shown when the user swipes right on a home app card.
 */
@Composable
fun DialogHostScope.AppPatchesDialog(
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
                                imageVector = MaterialSymbols.Rounded.Layers,
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
internal fun DialogHostScope.HideAppDialog(
    item: HomeAppItem,
    onDismiss: () -> Unit,
    onHide: () -> Unit
) {
    AppDialog(
        onDismissRequest = onDismiss,
        accentColor = rememberAppColor(item.packageName),
        title = stringResource(R.string.home_app_hide_title),
        description = stringResource(R.string.home_app_hide_message),
        footer = {
            AppDialogButtonRow(
                primaryText = stringResource(R.string.hide),
                primaryIcon = MaterialSymbols.Rounded.Visibility_off,
                onPrimaryClick = onHide,
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        },
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
    }
}

/** Asks before [apps] go back to the home screen together, naming each of them. */
@Composable
private fun DialogHostScope.UnhideAppsDialog(
    apps: List<HomeAppItem>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    ConfirmDialog(
        title = pluralStringResource(R.plurals.home_apps_unhide_title, apps.size, apps.size.toString()),
        message = stringResource(R.string.home_apps_unhide_message),
        primaryText = stringResource(R.string.unhide),
        isPrimaryDestructive = false,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        items = apps.map { ConfirmItem(MaterialSymbols.Rounded.Visibility, it.displayName) }
    )
}

/**
 * Dialog listing all hidden apps.
 *
 * Swipe gestures (disabled in multi-select mode):
 * - Swipe toward the start → unhide
 * - Swipe toward the end   → patches dialog
 *
 * Long-press enters multi-select; bulk unhide via the selection bar.
 */
@Composable
internal fun DialogHostScope.HiddenAppsDialog(
    hiddenAppItems: List<HomeAppItem>,
    onUnhide: (String) -> Unit,
    onUnhideMultiple: (Set<String>) -> Unit = {},
    onShowPatches: (HomeAppItem) -> Unit,
    onDismiss: () -> Unit
) {
    val messages = koinInject<UserMessages>()
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
    val unhideConfig = rememberUnhideSwipeAction()

    // The apps a bulk unhide waits to be confirmed for
    val pendingUnhide = remember { mutableStateOf<List<HomeAppItem>?>(null) }

    DialogHost(target = pendingUnhide.value) { apps ->
        UnhideAppsDialog(
            apps = apps,
            onConfirm = messages.confirming(appsShownMessage(apps)) {
                pendingUnhide.value = null
                onUnhideMultiple(apps.mapTo(mutableSetOf()) { it.id })
                isMultiSelectMode.value = false
                selectedPackages.clear()
            },
            onDismiss = { pendingUnhide.value = null }
        )
    }

    val accent = MaterialTheme.colorScheme.primary
    ListDialog(
        onDismissRequest = onDismiss,
        dismissOnClickOutside = !isMultiSelectMode.value,
        header = {
            ListDialogHeader(
                icon = { modifier ->
                    ListDialogHeaderIcon(icon = MaterialSymbols.Rounded.Visibility_off, color = accent, modifier = modifier)
                },
                title = stringResource(R.string.home_app_hidden_apps_title),
                subtitle = pluralStringResource(
                    R.plurals.home_category_app_count,
                    hiddenAppItems.size,
                    hiddenAppItems.size.toString()
                ),
                accentColor = accent,
                actions = {
                    // Gives way to the selection bar, which unhides whatever part of the list is picked
                    AnimatedVisibility(
                        visible = hiddenAppItems.isNotEmpty() && !isMultiSelectMode.value,
                        enter = Animations.headerActionEnter,
                        exit = Animations.headerActionExit
                    ) {
                        TitleAction(
                            icon = MaterialSymbols.Rounded.Visibility,
                            contentDescription = stringResource(R.string.show_all),
                            onClick = { pendingUnhide.value = hiddenAppItems },
                            style = TitleActionStyle.Neutral
                        )
                    }
                }
            )
        },
        footer = {
            AppDialogOutlinedButton(
                text = stringResource(R.string.close),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        },
        bottomBar = if (isMultiSelectMode.value) {
            {
                DockedPanel(
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
                                icon = MaterialSymbols.Rounded.Visibility,
                                label = stringResource(R.string.unhide),
                                onClick = {
                                    pendingUnhide.value = hiddenAppItems.filter { selectedPackages.contains(it.id) }
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
        } else null
    ) {
        DialogLazyList(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(itemSpacing)
        ) {
            if (hiddenAppItems.isEmpty()) {
                item(key = "empty_state") {
                    EmptyState(
                        message = stringResource(R.string.home_app_no_hidden),
                        icon = MaterialSymbols.Rounded.Visibility,
                        modifier = Modifier.animateItem()
                    )
                }
            }

            items(
                items = hiddenAppItems,
                key = { it.id }
            ) { item ->
                val isSelected = selectedPackages.contains(item.id)
                val unhide = unhideReporting(item, onUnhide)

                SelectableCard(
                    modifier = Modifier.animatedListItem(this),
                    isSelected = isSelected,
                    isSelectionMode = isMultiSelectMode.value
                ) {
                    SwipeableCardContainer(
                        start = SwipeAction(unhideConfig, unhide),
                        end = SwipeAction(rememberPatchesSwipeAction(item.packageName)) { onShowPatches(item) },
                        enabled = !isMultiSelectMode.value
                    ) {
                        AppCardLayout(
                            gradientColors = item.gradientColors,
                            onClick = {
                                if (isMultiSelectMode.value) {
                                    selectedPackages.toggle(item.id)
                                } else {
                                    unhide()
                                }
                            },
                            selected = isSelected.takeIf { isMultiSelectMode.value },
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
fun DialogHostScope.AppPatchSourcesDialog(
    packages: Set<String>,
    onDismiss: () -> Unit
) {
    val patchBundleRepository: PatchBundleRepository = koinInject()
    val sourceMuteRepository: SourceMuteRepository = koinInject()
    val scope = rememberCoroutineScope()

    val messages = koinInject<UserMessages>()
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
        description = stringResource(R.string.home_app_patch_sources_description),
        footer = {
            AppDialogOutlinedButton(
                text = stringResource(R.string.close),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        },
        scrollable = false
    ) {
        DialogLazyList(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(DialogListSpacing)
        ) {
            items(items = rows, key = { (uid, _, _) -> uid }) { (uid, title, counts) ->
                val (held, reaches) = counts
                // Worn the way the source list wears it, so a source reads as the same one there
                val accentColor = sourcesByUid[uid]?.let { rememberBundleAccent(it) }
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
                                    messages.show(lastSourceMessage)
                                }
                                reached.forEach { sourceMuteRepository.mute(it, uid) }
                            } else {
                                packages.forEach { sourceMuteRepository.unmute(it, uid) }
                            }
                        }
                    },
                    role = Role.Checkbox,
                    leadingContent = { SelectionCheckIndicator(state, accentColor = accentColor) },
                    accentColor = accentColor,
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

/** What hiding [apps] reports: the app by name when it is the only one, otherwise how many went. */
@Composable
internal fun appsHiddenMessage(apps: List<HomeAppItem>): String =
    appsVisibilityMessage(apps, R.string.home_app_hidden_named, R.plurals.home_apps_hidden_count)

/** Shows [item] again through [onUnhide], reporting it as [appsShownMessage] does. */
@Composable
internal fun unhideReporting(item: HomeAppItem, onUnhide: (String) -> Unit): () -> Unit =
    koinInject<UserMessages>().confirming(appsShownMessage(listOf(item))) { onUnhide(item.id) }

/** What showing [apps] again reports, as [appsHiddenMessage] does for hiding them. */
@Composable
internal fun appsShownMessage(apps: List<HomeAppItem>): String =
    appsVisibilityMessage(apps, R.string.home_app_shown_named, R.plurals.home_apps_shown_count)

@Composable
private fun appsVisibilityMessage(apps: List<HomeAppItem>, @StringRes named: Int, @PluralsRes counted: Int): String =
    apps.singleOrNull()?.let { stringResource(named, it.displayName) }
        ?: pluralStringResource(counted, apps.size, apps.size.toString())
