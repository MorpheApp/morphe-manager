/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.morphe.manager.R
import app.morphe.manager.domain.bundles.PatchBundleSource
import app.morphe.manager.domain.bundles.RemotePatchBundle
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.domain.repository.PatchBundleRepository.LocalFileCheck
import app.morphe.manager.domain.repository.SourceMuteRepository
import app.morphe.manager.patcher.patch.PatchBundleInfo
import app.morphe.manager.ui.model.NewSourceOptions
import app.morphe.manager.ui.model.SourceScope
import app.morphe.manager.ui.screen.shared.UserMessages
import app.morphe.manager.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.Locale
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** How long an added source is waited on before its app list is no longer opened for it. */
private val NEW_SOURCE_LOAD_TIMEOUT = 10.minutes

/**
 * The patch sources as the home screen manages them: adding them from links and files, importing
 * a file opened elsewhere, changing and removing them, and the dialogs all of that asks through.
 * Owned by [HomeViewModel], as the sources page and the home screen share these dialogs.
 */
class HomeSources(
    private val coroutineScope: CoroutineScope,
    /** The app list, which hints at the swipe gestures once a source is added. */
    private val apps: HomeApps,
    app: Application,
    private val messages: UserMessages,
    private val patchBundleRepository: PatchBundleRepository,
    private val sourceMuteRepository: SourceMuteRepository
) {
    private val contentResolver: ContentResolver = app.contentResolver

    // Dialog visibility states
    var bundleToRename by mutableStateOf<PatchBundleSource?>(null)
    var showRenameBundleDialog by mutableStateOf(false)

    /** Sources added with their apps to be chosen, whose app lists open one after another. */
    private var sourceAppsQueue by mutableStateOf<List<Int>>(emptyList())

    /** Source whose app list is open, the first of [sourceAppsQueue]. */
    val sourceAppsDialogUid: Int? get() = sourceAppsQueue.firstOrNull()

    /** Closes the open app list, which brings up the next queued one. */
    fun dismissSourceApps() {
        sourceAppsQueue = sourceAppsQueue.drop(1)
    }

    // Deep link: pending source to add via confirmation dialog
    var deepLinkPendingSource by mutableStateOf<MainViewModel.DeepLinkSource?>(null)
        private set

    /** Asks whether to add the source a deep link named, instead of adding it silently. */
    fun offerDeepLinkSource(source: MainViewModel.DeepLinkSource) {
        deepLinkPendingSource = source
    }

    /**
     * A .mpp file opened from a file manager, with its manifest and what importing it would do,
     * both null while they are read.
     */
    data class PendingMpp(val uri: Uri, val fileName: String?, val manifest: MppManifest?, val check: LocalFileCheck?)

    // .mpp file opened from file manager: pending confirmation dialog
    var pendingMpp by mutableStateOf<PendingMpp?>(null)
        private set

    /** Asks whether to import the .mpp file at [uri], reading what importing it would do meanwhile. */
    fun openMpp(uri: Uri) {
        val pending = PendingMpp(uri, uri.displayName(contentResolver), manifest = null, check = null)
        pendingMpp = pending
        coroutineScope.launch {
            val (manifest, check) = inspectLocalFile(uri)
            // A file opened in the meantime, or a dismissed prompt, is not described by this one
            if (pendingMpp == pending) pendingMpp = pending.copy(manifest = manifest, check = check)
        }
    }

    /** Imports the opened file the way the add source panel would, updating the source it is a version of. */
    fun confirmMppImport(options: NewSourceOptions) {
        val pending = pendingMpp ?: return
        pendingMpp = null
        importOf(pending.uri, pending.check)?.let { importLocalSources(listOf(it), options) }
    }

    fun dismissMppImport() {
        pendingMpp = null
    }

    /**
     * A file picked in the add source panel, with the bundle name its manifest declares and what
     * importing it would do, both null while they are read.
     */
    data class PickedBundle(val uri: Uri, val name: String, val bundleName: String?, val check: LocalFileCheck?) {
        /** Whether importing it adds a source or updates one, see [importOf]. */
        val adds: Boolean get() = check is LocalFileCheck.New || check is LocalFileCheck.Update
    }

    /** Files picked in the add source panel, kept here so they outlive the picker's round trip. */
    private var pickedBundles by mutableStateOf<List<PickedBundle>>(emptyList())

    /**
     * [pickedBundles] as they would import together: a file landing on the same source as an
     * earlier one adds nothing of its own. Two versions of one new bundle would each add a source,
     * so they are told apart by the bundle name, and the first picked is the one kept.
     */
    val pickedBundleImports: List<PickedBundle>
        get() {
            val targets = mutableSetOf<String>()
            return pickedBundles.map { picked ->
                val target = when (val check = picked.check) {
                    is LocalFileCheck.New -> picked.bundleName?.lowercase(Locale.US) ?: "uid:${check.uid}"
                    is LocalFileCheck.Update -> "uid:${check.uid}"
                    else -> null
                }
                if (target != null && !targets.add(target)) picked.copy(check = LocalFileCheck.Duplicate) else picked
            }
        }

    /** Adds files to the add source panel and reads what importing each would do. */
    fun pickBundles(uris: List<Uri>) {
        val fresh = uris.distinct().filter { uri -> pickedBundles.none { it.uri == uri } }.map { uri ->
            val name = uri.displayName(contentResolver) ?: uri.toString()
            val isBundle = name.endsWith(".$MPP_EXTENSION", ignoreCase = true)
            PickedBundle(uri, name, bundleName = null, check = if (isBundle) null else LocalFileCheck.NotBundle)
        }
        pickedBundles = pickedBundles + fresh
        fresh.filter { it.check == null }.forEach { picked ->
            coroutineScope.launch {
                val (manifest, check) = inspectLocalFile(picked.uri)
                pickedBundles = pickedBundles.map {
                    if (it.uri == picked.uri) it.copy(bundleName = manifest?.name, check = check) else it
                }
            }
        }
    }

    /** The manifest of [uri] and what importing it would do, a file that cannot be read being no bundle. */
    private suspend fun inspectLocalFile(uri: Uri): Pair<MppManifest?, LocalFileCheck> {
        val manifest = withContext(Dispatchers.IO) { uri.readMppManifest(contentResolver) }
        val check = runCatching {
            patchBundleRepository.checkLocal(manifest?.name) { openLocalFile(uri) }
        }.getOrElse { LocalFileCheck.NotBundle }
        return manifest to check
    }

    /** Opens [uri] for reading, closed by whoever reads it. */
    @SuppressLint("Recycle")
    private fun openLocalFile(uri: Uri): InputStream =
        contentResolver.openInputStream(uri) ?: throw FileNotFoundException("Unable to open $uri")

    /**
     * The import [check] calls for, as [importLocalSources] takes it: a new bundle as a source of its
     * own, another version of a local source into it. Null for a file that adds nothing.
     */
    private fun importOf(uri: Uri, check: LocalFileCheck?): Pair<Uri, Int?>? = when (check) {
        is LocalFileCheck.New -> uri to null
        is LocalFileCheck.Update -> uri to check.uid
        else -> null
    }

    fun unpickBundle(uri: Uri) {
        pickedBundles = pickedBundles.filterNot { it.uri == uri }
    }

    fun clearPickedBundles() {
        pickedBundles = emptyList()
    }

    /**
     * Adds a remote source for each of [urls] and imports the picked files, a new bundle as a source
     * of its own and another version of a local source into it, as its update action would. Files
     * already added are left out. Each new source starts with [options], see [onSourcesAdded].
     */
    fun addSources(urls: List<String>, options: NewSourceOptions) = coroutineScope.launch {
        val imports = pickedBundleImports.mapNotNull { importOf(it.uri, it.check) }
        pickedBundles = emptyList()
        val added = withContext(NonCancellable) {
            val remote = if (urls.isEmpty()) emptyList() else {
                patchBundleRepository.createRemotes(urls, usePrerelease = options.usePrerelease)
            }
            remote + importLocalFiles(imports)
        }
        onSourcesAdded(added, options)
    }

    /** Local source waiting for a replacement file, so the picker result knows what it updates. */
    var localBundleUpdateUid by mutableStateOf<Int?>(null)

    /** Downloads the patch updates a check left to the user, as the user asked for them. */
    fun downloadPendingUpdates() {
        coroutineScope.launch { patchBundleRepository.updateCheckAndAwait(allowUnsafeNetwork = true) }
    }

    /**
     * Imports each file through [importLocalFiles]. Each new source starts with [options], see
     * [onSourcesAdded].
     */
    private fun importLocalSources(imports: List<Pair<Uri, Int?>>, options: NewSourceOptions) = coroutineScope.launch {
        val added = withContext(NonCancellable) { importLocalFiles(imports) }
        onSourcesAdded(added, options)
    }

    /**
     * Imports each file, as a new local source or into the one its uid names, one after another
     * under one import message.
     *
     * @return The uids of the new sources, without the ones updated in place.
     */
    private suspend fun importLocalFiles(imports: List<Pair<Uri, Int?>>): List<Int> {
        if (imports.isEmpty()) return emptyList()
        val importing = messages.showUntilDismissed(R.string.importing_ellipsis)
        val imported = try {
            imports.map { (uri, replacingUid) -> replacingUid to importLocalFile(uri, replacingUid) }
        } finally {
            importing.dismiss()
        }
        // A file that failed has already said why
        if (imported.any { (_, uid) -> uid != null }) messages.show(R.string.imported_successfully)
        // Two files carrying the same bundle land on the same source
        return imported.mapNotNull { (replacingUid, uid) -> uid.takeIf { replacingUid == null } }.distinct()
    }

    /**
     * Adds a remote source for each of [urls], downloaded together as one update. Each added source
     * starts with [options], see [onSourcesAdded], and brings as much as [scope] asks for.
     */
    fun createRemoteSources(
        urls: List<String>,
        options: NewSourceOptions,
        scope: SourceScope
    ) = coroutineScope.launch {
        val added = withContext(NonCancellable) {
            patchBundleRepository.createRemotes(urls, keptApps = scope.keptApps, usePrerelease = options.usePrerelease)
        }
        onSourcesAdded(added, options, scope)
    }

    /**
     * With [NewSourceOptions.chooseApps] queues the app list of each of [uids] that has apps to list
     * as its patches load, then hints at the swipe gestures of the source cards.
     *
     * A source only appears among the loaded ones once its patches are in, which is also what the
     * list is built from. The sources load together, so they are taken as each one arrives rather
     * than in turn, and one that never loads holds back none of the others.
     *
     * A source added for less than the whole of it has left out its other apps as it loaded, see
     * [PatchBundleRepository.createRemotes]. They were never offered, so none of them is new to the
     * user, and a source that turns out not to have what [scope] asks for is added as any other.
     */
    private suspend fun onSourcesAdded(uids: List<Int>, options: NewSourceOptions, scope: SourceScope = SourceScope.Whole) {
        if (uids.isEmpty()) return

        val pending = uids.toMutableList()
        val loaded = mutableMapOf<Int, PatchBundleInfo>()
        withTimeoutOrNull(NEW_SOURCE_LOAD_TIMEOUT) {
            patchBundleRepository.allBundlesInfoFlow.first { info ->
                pending.removeAll { uid ->
                    val bundle = info[uid] ?: return@removeAll false
                    loaded[uid] = bundle
                    // Universal patches put no app on the home screen, so there would be nothing to list
                    if (options.chooseApps && bundle.listedApps().isNotEmpty()) sourceAppsQueue += uid
                    true
                }
                pending.isEmpty()
            }
        }
        if (scope != SourceScope.Whole) withContext(NonCancellable) {
            loaded.forEach { (uid, bundle) ->
                if (scope.isMetBy(bundle)) sourceMuteRepository.markNewAppsSeen(uid)
                else sourceMuteRepository.resetForBundle(uid)
            }
        }
        delay(1.5.seconds)
        apps.triggerSwipeGestureHint()
    }

    /**
     * Points an existing local source at a newly picked file. Adding the updated file instead
     * would create a second source and strand the patch selection on the old one.
     */
    fun updateLocalSource(uid: Int, patchBundle: Uri) = importLocalSources(listOf(patchBundle to uid), NewSourceOptions())

    fun removeSources(bundles: List<PatchBundleSource>) = coroutineScope.launch {
        patchBundleRepository.remove(*bundles.toTypedArray())
    }

    /** Flips each of [bundles], on or off. */
    fun toggleSources(bundles: List<PatchBundleSource>) = coroutineScope.launch {
        patchBundleRepository.disable(*bundles.toTypedArray())
    }

    /** Downloads [bundles] again, a failed one from scratch, telling the user how it went. */
    fun updateSources(bundles: List<RemotePatchBundle>) = coroutineScope.launch {
        patchBundleRepository.updateOrRepair(bundles, showMessage = true)
    }

    /**
     * Updates the sources at [endpoints] again once the local network is allowed. A connection
     * still waiting goes through by itself, so only the sources whose update already gave up are.
     */
    fun retryLocalNetworkSources(endpoints: Collection<String>) = coroutineScope.launch {
        val updating = patchBundleRepository.activeUpdateUidsFlow.value
        val failed = patchBundleRepository.sources.value
            .filterIsInstance<RemotePatchBundle>()
            .filter { it.enabled && it.endpoint in endpoints && it.uid !in updating }
        patchBundleRepository.updateOrRepair(failed, showMessage = true)
    }

    fun reorderSources(orderedUids: List<Int>) = coroutineScope.launch {
        patchBundleRepository.reorderBundles(orderedUids)
    }

    fun setUsePrerelease(bundles: List<PatchBundleSource>, usePrerelease: Boolean) = coroutineScope.launch {
        patchBundleRepository.setUsePrerelease(bundles.mapTo(mutableSetOf()) { it.uid }, usePrerelease)
    }

    fun setUseExperimentalVersions(bundles: List<PatchBundleSource>, useExperimental: Boolean) = coroutineScope.launch {
        patchBundleRepository.setUseExperimentalVersions(bundles.mapTo(mutableSetOf()) { it.uid }, useExperimental)
    }

    /** Trusts the key [bundle] is signed with now, after the user accepted the change. */
    fun trustNewSigningKey(bundle: RemotePatchBundle) = coroutineScope.launch {
        patchBundleRepository.trustNewSigningKey(bundle)
    }

    fun changeSourceLink(bundle: RemotePatchBundle, url: String) = coroutineScope.launch {
        patchBundleRepository.changeRemoteLink(bundle, url)
    }

    /** @return The uid of the source [patchBundle] landed under, or null when it could not be read. */
    private suspend fun importLocalFile(patchBundle: Uri, replacingUid: Int?): Int? {
        val permissionFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        var persistedPermission = false
        // A cloud provider may fetch the whole file to answer this
        val size = withContext(Dispatchers.IO) {
            runCatching {
                contentResolver.openFileDescriptor(patchBundle, "r")
                    ?.use { it.statSize.takeIf { sz -> sz > 0 } }
                    ?: contentResolver.query(
                        patchBundle,
                        arrayOf(OpenableColumns.SIZE),
                        null,
                        null,
                        null
                    )
                        ?.use { cursor ->
                            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                            if (index != -1 && cursor.moveToFirst()) cursor.getLong(index) else null
                        }
            }.getOrNull()
        }?.takeIf { it > 0L }
        try {
            contentResolver.takePersistableUriPermission(patchBundle, permissionFlags)
            persistedPermission = true
        } catch (_: SecurityException) {
            // Provider may not support persistable permissions; fall back to transient grant
        }

        val openStream: suspend () -> InputStream = { openLocalFile(patchBundle) }
        return try {
            if (replacingUid != null) {
                patchBundleRepository.replaceLocal(replacingUid, size, openStream)
            } else {
                patchBundleRepository.createLocal(size, openStream)
            }
        } finally {
            if (persistedPermission) {
                try {
                    contentResolver.releasePersistableUriPermission(
                        patchBundle,
                        permissionFlags
                    )
                } catch (_: SecurityException) {
                    // Ignore if provider revoked or already released
                }
            }
        }
    }

    /**
     * User confirmed adding the source from the deep link confirmation dialog.
     *
     * A link for one app from a source that is already here only asks for that app, so it is
     * brought back to the source rather than the source being refused as a duplicate.
     */
    fun confirmDeepLinkSource(options: NewSourceOptions) {
        val source = deepLinkPendingSource ?: return
        deepLinkPendingSource = null
        val scope = source.scope
        val existingUid = patchBundleRepository.existingSourceUid(source.url)
        if (scope is SourceScope.App && existingUid != null) {
            coroutineScope.launch { sourceMuteRepository.unmuteApps(existingUid, listOf(scope.packageName)) }
        } else {
            createRemoteSources(listOf(source.url), options, scope)
        }
    }

    /** User dismissed the deep link confirmation dialog. */
    fun dismissDeepLinkSource() {
        deepLinkPendingSource = null
    }

    /**
     * Names [bundleToRename] [displayName], or gives it back its own name when that is blank. The
     * dialog stays up on a name another source already has, for the user to pick another.
     */
    fun renameBundle(displayName: String) {
        val bundle = bundleToRename ?: return
        val newName = displayName.trim().ifEmpty { null }
        coroutineScope.launch {
            when (patchBundleRepository.setDisplayName(bundle.uid, newName)) {
                PatchBundleRepository.DisplayNameUpdateResult.SUCCESS -> {
                    dismissRenameBundle()
                    messages.show(R.string.sources_dialog_renamed, newName ?: bundle.name)
                }
                PatchBundleRepository.DisplayNameUpdateResult.NO_CHANGE -> dismissRenameBundle()
                PatchBundleRepository.DisplayNameUpdateResult.DUPLICATE ->
                    messages.show(R.string.sources_dialog_duplicate_name_error)
                PatchBundleRepository.DisplayNameUpdateResult.NOT_FOUND ->
                    messages.show(R.string.sources_dialog_missing_error)
            }
        }
    }

    fun dismissRenameBundle() {
        showRenameBundleDialog = false
        bundleToRename = null
    }

    /** Closes the dialogs about sources, for an incoming one not to be stacked behind them. */
    fun dismissDialogs() {
        dismissRenameBundle()
        sourceAppsQueue = emptyList()
    }
}
