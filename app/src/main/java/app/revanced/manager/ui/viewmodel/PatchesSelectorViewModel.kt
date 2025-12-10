package app.revanced.manager.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.SavedStateHandleSaveableApi
import androidx.lifecycle.viewmodel.compose.saveable
import app.morphe.manager.R
import app.revanced.manager.data.room.profile.PatchProfilePayload
import app.revanced.manager.data.room.options.Option as StoredOption
import app.revanced.manager.domain.manager.PreferencesManager
import app.revanced.manager.domain.bundles.PatchBundleSource.Extensions.asRemoteOrNull
import app.revanced.manager.domain.bundles.PatchBundleSource.Extensions.isDefault
import app.revanced.manager.domain.bundles.RemotePatchBundle
import app.revanced.manager.domain.repository.PatchBundleRepository
import app.revanced.manager.domain.repository.DuplicatePatchProfileNameException
import app.revanced.manager.domain.repository.PatchProfileRepository
import app.revanced.manager.domain.repository.remapLocalBundles
import app.revanced.manager.patcher.patch.Option
import app.revanced.manager.patcher.patch.PatchBundleInfo
import app.revanced.manager.patcher.patch.PatchBundleInfo.Extensions.toPatchSelection
import app.revanced.manager.patcher.patch.PatchInfo
import app.revanced.manager.ui.model.navigation.SelectedApplicationInfo
import app.revanced.manager.util.Options
import app.revanced.manager.util.PatchSelection
import app.revanced.manager.util.tag
import app.revanced.manager.util.saver.Nullable
import app.revanced.manager.util.saver.nullableSaver
import app.revanced.manager.util.saver.persistentMapSaver
import app.revanced.manager.util.saver.persistentSetSaver
import app.revanced.manager.util.saver.snapshotStateMapSaver
import app.revanced.manager.util.toast
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import kotlin.collections.ArrayDeque

@OptIn(SavedStateHandleSaveableApi::class)
class PatchesSelectorViewModel(input: SelectedApplicationInfo.PatchesSelector.ViewModelParams) :
    ViewModel(), KoinComponent {
    private val app: Application = get()
    private val savedStateHandle: SavedStateHandle = get()
    val prefs: PreferencesManager = get()
    private val patchBundleRepository: PatchBundleRepository = get()
    val missingPatchNames: List<String>? = input.missingPatchNames
    private val patchProfileRepository: PatchProfileRepository = get()

    private val packageName = input.app.packageName
    val appVersion = input.app.version
    private var currentBundles: List<PatchBundleInfo.Scoped> = emptyList()

    var selectionWarningEnabled by mutableStateOf(true)
        private set
    var allowUniversalPatches by mutableStateOf(true)
        private set

    var allowIncompatiblePatches by mutableStateOf(prefs.disablePatchVersionCompatCheck.getBlocking())
        private set
    private val suggestedVersionSafeguardEnabled = prefs.suggestedVersionSafeguard.getBlocking()
    private val allowUniversalPatchesFlow = prefs.disableUniversalPatchCheck.flow
    val bundlesFlow =
        patchBundleRepository.scopedBundleInfoFlow(packageName, input.app.version)
            .combine(allowUniversalPatchesFlow) { bundles, allowUniversal ->
                if (allowUniversal) bundles else bundles.map(PatchBundleInfo.Scoped::withoutUniversalPatches)
            }
    val bundleDisplayNames =
        patchBundleRepository.sources.map { sources ->
            sources.associate { source ->
                val title = source.displayTitle
                source.uid to title
            }
        }
    val bundleEndpoints =
        patchBundleRepository.sources.map { sources ->
            sources.associate { source ->
                source.uid to (source as? RemotePatchBundle)?.endpoint
            }
        }
    val bundleIdentifiers =
        patchBundleRepository.sources.map { sources ->
            sources.associate { source ->
                val identifier = source.patchBundle?.manifestAttributes?.name?.takeUnless { it.isNullOrBlank() }
                    ?: source.name
                source.uid to identifier
            }
        }
    val bundleTypes =
        patchBundleRepository.sources.map { sources ->
            sources.associate { source ->
                val type = when {
                    source.isDefault -> BundleSourceType.Preinstalled
                    source.asRemoteOrNull != null -> BundleSourceType.Remote
                    else -> BundleSourceType.Local
                }
                source.uid to type
            }
        }

    private val defaultPatchSelection = bundlesFlow
        .map { bundles ->
            bundles.toPatchSelection(allowIncompatiblePatches) { _, patch -> patch.include }
                .toPersistentPatchSelection()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = persistentMapOf()
        )

    private var currentDefaultSelection: PersistentPatchSelection = persistentMapOf()

    val defaultSelectionCount = defaultPatchSelection.map { selection ->
        selection.values.sumOf { it.size }
    }

    private val filterState = mutableStateOf(resolveInitialFilter(savedStateHandle))
    var filter: Int
        get() = filterState.value
        private set(value) {
            filterState.value = value
            savedStateHandle["filter"] = value
        }

    init {
        viewModelScope.launch {
            prefs.disablePatchVersionCompatCheck.flow
                .distinctUntilChanged()
                .collect { allow ->
                    allowIncompatiblePatches = allow
                    filter = if (allow) {
                        filter or SHOW_INCOMPATIBLE
                    } else {
                        filter and SHOW_INCOMPATIBLE.inv()
                    }
                }
        }

        allowUniversalPatches = prefs.disableUniversalPatchCheck.getBlocking()

        viewModelScope.launch {
            allowUniversalPatchesFlow
                .distinctUntilChanged()
                .collect { allowUniversal ->
                    allowUniversalPatches = allowUniversal
                    if (allowUniversal) {
                        filter = filter or SHOW_UNIVERSAL
                    } else {
                        filter = filter and SHOW_UNIVERSAL.inv()
                        pruneSelectionsAndOptions()
                    }
                }
        }

        viewModelScope.launch {
            bundlesFlow.collect { bundles ->
                currentBundles = bundles
                if (!allowUniversalPatches) pruneSelectionsAndOptions()
            }
        }

        viewModelScope.launch {
            if (prefs.disableSelectionWarning.get()) {
                selectionWarningEnabled = false
                return@launch
            }

            fun PatchBundleInfo.Scoped.hasDefaultPatches() =
                patchSequence(allowIncompatiblePatches).any { it.include }

            // Don't show the warning if there are no default patches.
            selectionWarningEnabled = bundlesFlow.first().any(PatchBundleInfo.Scoped::hasDefaultPatches)
        }

        viewModelScope.launch {
            currentDefaultSelection = defaultPatchSelection.first()
        }

        viewModelScope.launch {
            defaultPatchSelection.collect { currentDefaultSelection = it }
        }
    }

    private var hasModifiedSelection = false
    var customPatchSelection: PersistentPatchSelection? by savedStateHandle.saveable(
        key = "selection",
        stateSaver = selectionSaver,
    ) {
        mutableStateOf(input.currentSelection?.toPersistentPatchSelection())
    }

    private val patchOptions: PersistentOptions by savedStateHandle.saveable(
        saver = optionsSaver,
    ) {
        // Convert Options to PersistentOptions
        input.options.mapValuesTo(mutableStateMapOf()) { (_, allPatches) ->
            allPatches.mapValues { (_, options) -> options.toPersistentMap() }.toPersistentMap()
        }
    }

    /**
     * Show the patch options dialog for this patch.
     */
    var optionsDialog by mutableStateOf<Pair<Int, PatchInfo>?>(null)

    val compatibleVersions = mutableStateListOf<String>()

    // This is for the required options screen.
    private val requiredOptsPatchesDeferred = viewModelScope.async(start = CoroutineStart.LAZY) {
        bundlesFlow.first().map { bundle ->
            bundle to bundle.patchSequence(allowIncompatiblePatches).filter { patch ->
                val opts by lazy {
                    getOptions(bundle.uid, patch).orEmpty()
                }
                isSelected(
                    bundle.uid,
                    patch
                ) && patch.options?.any { it.required && it.default == null && it.key !in opts } ?: false
            }.toList()
        }.filter { (_, patches) -> patches.isNotEmpty() }
    }
    val requiredOptsPatches = flow { emit(requiredOptsPatchesDeferred.await()) }

    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    private data class SelectionSnapshot(
        val selection: PersistentPatchSelection?,
        val options: Options
    )

    private data class HistoryEntry(
        val snapshot: SelectionSnapshot,
        val description: String
    )

    private val history = ArrayDeque<HistoryEntry>()
    private val future = ArrayDeque<HistoryEntry>()

    fun selectionIsValid(bundles: List<PatchBundleInfo.Scoped>) = bundles.any { bundle ->
        bundle.patchSequence(allowIncompatiblePatches).any { patch ->
            isSelected(bundle.uid, patch)
        }
    }

    fun isSelected(bundle: Int, patch: PatchInfo): Boolean {
        customPatchSelection?.let { selection ->
            return selection[bundle]?.contains(patch.name) == true
        }
        return currentDefaultSelection[bundle]?.contains(patch.name) ?: patch.include
    }

    fun togglePatch(bundle: Int, patch: PatchInfo) = viewModelScope.launch {
        hasModifiedSelection = true

        val baseSelection = customPatchSelection ?: run {
            if (currentDefaultSelection.isNotEmpty()) currentDefaultSelection
            else defaultPatchSelection.value ?: defaultPatchSelection.first()
        }

        val newPatches = baseSelection[bundle]?.let { patches ->
            if (patch.name in patches)
                patches.remove(patch.name)
            else
                patches.add(patch.name)
        } ?: persistentSetOf(patch.name)

        customPatchSelection = baseSelection.put(bundle, newPatches)
    }

    fun reset() {
        recordSnapshot(actionLabel(R.string.patch_selection_action_all_defaults))
        patchOptions.clear()
        customPatchSelection = null
        hasModifiedSelection = false
        app.toast(app.getString(R.string.patch_selection_reset_toast))
    }

    fun deselectAll() {
        recordSnapshot(actionLabel(R.string.patch_selection_action_deselect_all))
        hasModifiedSelection = true
        customPatchSelection = persistentMapOf()
        patchOptions.clear()
        app.toast(app.getString(R.string.patch_selection_deselected_all_toast))
    }

    fun selectAll() {
        if (currentBundles.isEmpty()) return

        val selections = currentBundles
            .associate { bundle ->
                bundle.uid to bundle.patchSequence(allowIncompatiblePatches)
                    .map(PatchInfo::name)
                    .toPersistentSet()
            }
            .filterValues { it.isNotEmpty() }

        if (selections.isEmpty()) {
            app.toast(app.getString(R.string.patch_selection_select_all_empty_toast))
            return
        }

        recordSnapshot(actionLabel(R.string.patch_selection_action_select_all))
        hasModifiedSelection = true
        customPatchSelection = selections.toPersistentMap()
        app.toast(app.getString(R.string.patch_selection_selected_all_toast))
    }

    fun selectBundle(bundleUid: Int, bundleName: String) = viewModelScope.launch {
        val bundle = currentBundles.firstOrNull { it.uid == bundleUid }
        if (bundle == null) {
            app.toast(app.getString(R.string.patch_selection_select_bundle_empty_toast, bundleName))
            return@launch
        }

        val patches = bundle.patchSequence(allowIncompatiblePatches)
            .map(PatchInfo::name)
            .toPersistentSet()

        if (patches.isEmpty()) {
            app.toast(app.getString(R.string.patch_selection_select_bundle_empty_toast, bundleName))
            return@launch
        }

        val baseSelection = customPatchSelection ?: run {
            if (currentDefaultSelection.isNotEmpty()) currentDefaultSelection
            else defaultPatchSelection.value ?: defaultPatchSelection.first()
        }

        recordSnapshot(actionLabel(R.string.patch_selection_action_select_bundle, bundleName))
        hasModifiedSelection = true
        customPatchSelection = baseSelection.put(bundleUid, patches)
        app.toast(
            app.getString(
                R.string.patch_selection_selected_bundle_toast,
                bundleName
            )
        )
    }

    fun deselectBundle(bundleUid: Int, bundleName: String) = viewModelScope.launch {
        val baseSelection = customPatchSelection ?: run {
            if (currentDefaultSelection.isNotEmpty()) currentDefaultSelection
            else defaultPatchSelection.value ?: defaultPatchSelection.first()
        }

        val selectedPatches = baseSelection[bundleUid] ?: persistentSetOf()
        if (selectedPatches.isEmpty()) {
            app.toast(
                app.getString(
                    R.string.patch_selection_no_selected_bundle_toast,
                    bundleName
                )
            )
            return@launch
        }

        recordSnapshot(actionLabel(R.string.patch_selection_action_deselect_bundle, bundleName))
        hasModifiedSelection = true
        customPatchSelection = baseSelection.put(bundleUid, persistentSetOf())
        patchOptions.remove(bundleUid)
        app.toast(
            app.getString(
                R.string.patch_selection_deselected_bundle_toast,
                bundleName
            )
        )
    }

    fun resetBundleToDefaults(bundleUid: Int, bundleName: String) = viewModelScope.launch {
        val defaultSelection = currentDefaultSelection[bundleUid] ?: persistentSetOf()
        val baseSelection = customPatchSelection ?: run {
            if (currentDefaultSelection.isNotEmpty()) currentDefaultSelection
            else defaultPatchSelection.value ?: defaultPatchSelection.first()
        }

        recordSnapshot(actionLabel(R.string.patch_selection_action_bundle_defaults, bundleName))
        hasModifiedSelection = true
        customPatchSelection = if (defaultSelection.isEmpty()) {
            baseSelection.remove(bundleUid)
        } else {
            baseSelection.put(bundleUid, defaultSelection)
        }
        patchOptions.remove(bundleUid)
        app.toast(
            app.getString(
                R.string.patch_selection_reset_bundle_toast,
                bundleName
            )
        )
    }

    fun getCustomSelection(): PatchSelection? {
        // Convert persistent collections to standard hash collections because persistent collections are not parcelable.

        return customPatchSelection?.mapValues { (_, v) -> v.toSet() }
    }

    fun getOptions(): Options {
        // Convert the collection for the same reasons as in getCustomSelection()

        return patchOptions.mapValues { (_, allPatches) -> allPatches.mapValues { (_, options) -> options.toMap() } }
    }

    fun getOptions(bundle: Int, patch: PatchInfo) = patchOptions[bundle]?.get(patch.name)

    fun setOption(bundle: Int, patch: PatchInfo, key: String, value: Any?) {
        // All patches
        val patchesToOpts = patchOptions.getOrElse(bundle, ::persistentMapOf)
        // The key-value options of an individual patch
        val patchToOpts = patchesToOpts
            .getOrElse(patch.name, ::persistentMapOf)
            .put(key, value)

        patchOptions[bundle] = patchesToOpts.put(patch.name, patchToOpts)
    }

    fun resetOptions(bundle: Int, patch: PatchInfo) {
        app.toast(app.getString(R.string.patch_options_reset_toast))
        patchOptions[bundle] = patchOptions[bundle]?.remove(patch.name) ?: return
    }

    private fun pruneSelectionsAndOptions() {
        if (currentBundles.isEmpty()) return

        val availablePatches = currentBundles.associate { bundle ->
            bundle.uid to bundle.patches.map(PatchInfo::name).toSet()
        }

        customPatchSelection?.let { current ->
            val pruned = current.pruneTo(availablePatches)
            if (pruned !== current) {
                customPatchSelection = pruned
                hasModifiedSelection = true
            }
        }

        patchOptions.keys.toList().forEach { bundleUid ->
            val bundleOptions = patchOptions[bundleUid] ?: return@forEach
            val allowed = availablePatches[bundleUid] ?: emptySet()
            val filtered = bundleOptions
                .filterKeys { it in allowed }
                .toPersistentMap()

            when {
                filtered.isEmpty() -> patchOptions.remove(bundleUid)
                filtered.size != bundleOptions.size -> patchOptions[bundleUid] = filtered
            }
        }
    }

    val profiles = combine(
        patchProfileRepository.profilesForPackageFlow(packageName),
        patchBundleRepository.bundleInfoFlow,
        patchBundleRepository.sources
    ) { profiles, bundleInfoSnapshot, sources ->
        if (profiles.isEmpty()) return@combine emptyList()

        val signatureMap = bundleInfoSnapshot.mapValues { (_, info) ->
            info.patches.map { it.name.trim().lowercase() }.toSet()
        }

        profiles.map { profile ->
            val remappedPayload = profile.payload.remapLocalBundles(sources, signatureMap)
            if (remappedPayload !== profile.payload) {
                viewModelScope.launch(Dispatchers.Default) {
                    patchProfileRepository.updateProfile(
                        uid = profile.uid,
                        packageName = profile.packageName,
                        appVersion = profile.appVersion,
                        name = profile.name,
                        payload = remappedPayload
                    )
                }
                profile.copy(payload = remappedPayload)
            } else profile
        }
    }

    suspend fun savePatchProfile(
        name: String,
        selectedBundles: Set<Int>,
        existingProfileId: Int?
    ): Boolean {
        if (selectedBundles.isEmpty()) return false
        val selection = (customPatchSelection ?: currentDefaultSelection).toPatchSelection()
        val options = getOptions()
        val displayNames = bundleDisplayNames.first()
        val endpoints = bundleEndpoints.first()
        val identifiers = bundleIdentifiers.first()

        val bundles = selectedBundles.map { bundleUid ->
            val patches = selection[bundleUid]?.toList().orEmpty()
            val serializedOptions = serializeOptions(bundleUid, patches.toSet(), options)
            PatchProfilePayload.Bundle(
                bundleUid = bundleUid,
                patches = patches,
                options = serializedOptions,
                displayName = displayNames[bundleUid],
                sourceEndpoint = endpoints[bundleUid],
                sourceName = identifiers[bundleUid]
            )
        }

        val payload = PatchProfilePayload(bundles)
        return try {
            if (existingProfileId != null) {
                val updated = patchProfileRepository.updateProfile(
                    uid = existingProfileId,
                    packageName = packageName,
                    appVersion = appVersion,
                    name = name,
                    payload = payload
                )
                if (updated != null) {
                    app.toast(app.getString(R.string.patch_profile_updated_toast, name))
                } else {
                    patchProfileRepository.createProfile(
                        packageName = packageName,
                        appVersion = appVersion,
                        name = name,
                        payload = payload
                    )
                    app.toast(app.getString(R.string.patch_profile_saved_toast, name))
                }
            } else {
                patchProfileRepository.createProfile(
                    packageName = packageName,
                    appVersion = appVersion,
                    name = name,
                    payload = payload
                )
                app.toast(app.getString(R.string.patch_profile_saved_toast, name))
            }
            true
        } catch (duplicate: DuplicatePatchProfileNameException) {
            app.toast(app.getString(R.string.patch_profile_duplicate_toast, duplicate.profileName))
            false
        } catch (t: Exception) {
            Log.e(tag, "Failed to save patch profile", t)
            app.toast(app.getString(R.string.patch_profile_save_failed_toast))
            false
        }
    }

    private fun serializeOptions(
        bundleUid: Int,
        selectedPatches: Set<String>,
        options: Options
    ): Map<String, Map<String, StoredOption.SerializedValue>> {
        val bundleOptions = options[bundleUid] ?: return emptyMap()
        val serializedOptions = mutableMapOf<String, MutableMap<String, StoredOption.SerializedValue>>()

        bundleOptions.forEach { (patchName, optionValues) ->
            if (selectedPatches.isNotEmpty() && patchName !in selectedPatches) return@forEach
            val serializedForPatch = mutableMapOf<String, StoredOption.SerializedValue>()

            optionValues.forEach { (key, value) ->
                try {
                    serializedForPatch[key] = StoredOption.SerializedValue.fromValue(value)
                } catch (e: StoredOption.SerializationException) {
                    Log.w(
                        tag,
                        "Failed to serialize option $patchName:$key for bundle $bundleUid",
                        e
                    )
                }
            }

            if (serializedForPatch.isNotEmpty()) {
                serializedOptions[patchName] = serializedForPatch
            }
        }

        return serializedOptions.mapValues { entry -> entry.value.toMap() }
    }

    fun dismissDialogs() {
        optionsDialog = null
        compatibleVersions.clear()
    }

    fun openIncompatibleDialog(incompatiblePatch: PatchInfo) {
        compatibleVersions.addAll(incompatiblePatch.compatiblePackages?.find { it.packageName == packageName }?.versions.orEmpty())
    }

    fun toggleFlag(flag: Int) {
        filter = filter xor flag
    }

    private fun resolveInitialFilter(handle: SavedStateHandle): Int {
        val stored = handle.get<Any?>("filter")
        val resolved = when (stored) {
            is Int -> stored
            is MutableState<*> -> defaultFilterFlags()
            else -> defaultFilterFlags()
        }
        handle["filter"] = resolved
        return resolved
    }

    fun undoAction() {
        val entry = history.removeLastOrNull() ?: return
        future.addLast(HistoryEntry(currentSnapshot(), entry.description))
        applySnapshot(entry.snapshot)
        updateHistoryState()
        app.toast(app.getString(R.string.patch_selection_history_undo, entry.description))
    }

    fun redoAction() {
        val entry = future.removeLastOrNull() ?: return
        history.addLast(HistoryEntry(currentSnapshot(), entry.description))
        applySnapshot(entry.snapshot)
        updateHistoryState()
        app.toast(app.getString(R.string.patch_selection_history_redo, entry.description))
    }

    private fun recordSnapshot(actionDescription: String) {
        history.addLast(HistoryEntry(currentSnapshot(), actionDescription))
        future.clear()
        updateHistoryState()
    }

    private fun currentSnapshot() = SelectionSnapshot(
        customPatchSelection,
        patchOptions.toOptions()
    )

    private fun applySnapshot(snapshot: SelectionSnapshot) {
        customPatchSelection = snapshot.selection
        patchOptions.clear()
        snapshot.options.forEach { (bundleUid, patchMap) ->
            val persistent = patchMap.mapValues { (_, options) ->
                options.toPersistentMap()
            }.toPersistentMap()
            patchOptions[bundleUid] = persistent
        }
        hasModifiedSelection = true
    }

    private fun updateHistoryState() {
        canUndo = history.isNotEmpty()
        canRedo = future.isNotEmpty()
    }

    private fun PersistentOptions.toOptions(): Options =
        mapValues { (_, bundleOptions) ->
            bundleOptions.mapValues { (_, options) -> options.toMap() }.toMap()
        }.toMap()

    private fun actionLabel(@StringRes labelRes: Int, vararg args: Any?): String =
        app.getString(labelRes, *args)

    private fun defaultFilterFlags(): Int =
        if (allowIncompatiblePatches || !suggestedVersionSafeguardEnabled)
            SHOW_UNIVERSAL or SHOW_INCOMPATIBLE
        else
            SHOW_UNIVERSAL

    companion object {
        const val SHOW_INCOMPATIBLE = 1 // 2^0
        const val SHOW_UNIVERSAL = 2 // 2^1

        private val optionsSaver: Saver<PersistentOptions, Options> = snapshotStateMapSaver(
            // Patch name -> Options
            valueSaver = persistentMapSaver(
                // Option key -> Option value
                valueSaver = persistentMapSaver()
            )
        )

        private val selectionSaver: Saver<PersistentPatchSelection?, Nullable<PatchSelection>> =
            nullableSaver(persistentMapSaver(valueSaver = persistentSetSaver()))
    }
}

// Versions of other types, but utilizing persistent/observable collection types.
private typealias PersistentOptions = SnapshotStateMap<Int, PersistentMap<String, PersistentMap<String, Any?>>>
private typealias PersistentPatchSelection = PersistentMap<Int, PersistentSet<String>>

private fun PatchSelection.toPersistentPatchSelection(): PersistentPatchSelection =
    mapValues { (_, v) -> v.toPersistentSet() }.toPersistentMap()

private fun PersistentPatchSelection.toPatchSelection(): PatchSelection =
    mapValues { (_, v) -> v.toSet() }

private fun PatchBundleInfo.Scoped.withoutUniversalPatches(): PatchBundleInfo.Scoped {
    if (universal.isEmpty()) return this

    val filteredPatches = patches.filter { it.compatiblePackages != null }
    return copy(
        patches = filteredPatches,
        universal = emptyList()
    )
}

private fun PersistentPatchSelection.pruneTo(
    available: Map<Int, Set<String>>
): PersistentPatchSelection {
    var changed = false
    val filtered = buildMap<Int, PersistentSet<String>> {
        this@pruneTo.forEach { (bundleUid, patches) ->
            val allowed = available[bundleUid] ?: run {
                if (patches.isNotEmpty()) changed = true
                return@forEach
            }
            val kept = patches.filter { it in allowed }.toPersistentSet()
            if (kept.size != patches.size) changed = true
            if (kept.isNotEmpty()) put(bundleUid, kept)
        }
    }

    if (!changed) return this
    if (filtered.isEmpty()) return persistentMapOf()
    return filtered.toPersistentMap()
}
