/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.repository

import app.morphe.manager.data.room.AppDatabase
import app.morphe.manager.data.room.selection.SourceAppList
import app.morphe.manager.data.room.selection.SourceMuteDao
import app.morphe.manager.patcher.patch.PatchBundleInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private const val SQLITE_BIND_LIMIT = 900

/**
 * Which sources each app is kept from.
 *
 * A source switched off in the source list is off for everything; this narrows one app instead.
 * Someone who patches YouTube from one source and Reddit from another stops being asked which to
 * use. Patches added to the source they turned down never join a selection behind their back.
 *
 * A source kept from an app also stops bringing it to the home screen, so a source with hundreds
 * of apps can be cut down to the few wanted from it. An app no source brings leaves the list, see
 * [appsBrought]. Such a source can also leave out the apps it starts supporting later, so the cut
 * holds as it grows: each one is kept from it on arrival, as if the user had left it out.
 *
 * Apps are keyed by the package the user patches, not by the install a run produces: a copy is
 * built from the same app and is offered the same sources.
 */
class SourceMuteRepository(db: AppDatabase) {
    private val dao = db.sourceMuteDao()

    /** Muted source UIDs per package. Packages that rule out nothing are absent. */
    val mutedSources: Flow<Map<String, Set<Int>>> = dao.getAll()
        .map { mutes ->
            mutes.groupBy({ it.packageName }, { it.patchBundle })
                .mapValues { (_, uids) -> uids.toSet() }
        }
        .distinctUntilChanged()

    /** The sources [packageName] rules out, as a flow of its own for the per-app call sites. */
    fun mutedFor(packageName: String): Flow<Set<Int>> =
        mutedSources.map { it[packageName].orEmpty() }.distinctUntilChanged()

    suspend fun getMutedFor(packageName: String): Set<Int> = dao.getForPackage(packageName).toSet()

    /** Rules every source out for [packageName] except [keepUid], out of [candidates]. */
    suspend fun keepOnly(packageName: String, keepUid: Int, candidates: Set<Int>) =
        dao.replaceForPackage(packageName, candidates - keepUid)

    suspend fun mute(packageName: String, bundleUid: Int) =
        dao.replaceForPackage(packageName, getMutedFor(packageName) + bundleUid)

    /** Offers every source to [packageName] again. */
    suspend fun unmuteAll(packageName: String) = dao.replaceForPackage(packageName, emptySet())

    suspend fun unmute(packageName: String, bundleUid: Int) =
        dao.clearForPackageAndBundle(packageName, bundleUid)

    /**
     * Keeps every one of [packageNames] from [bundleUid], on top of what is already ruled out. A
     * source's app list answers this way.
     */
    suspend fun muteApps(bundleUid: Int, packageNames: Collection<String>) =
        dao.addForBundle(bundleUid, packageNames)

    /** Offers [bundleUid] to every one of [packageNames] again. */
    suspend fun unmuteApps(bundleUid: Int, packageNames: Collection<String>) {
        // Older SQLite builds cap a single statement at 999 bound values
        packageNames.chunked(SQLITE_BIND_LIMIT).forEach { dao.clearForBundle(bundleUid, it) }
    }

    /**
     * Apps left out on arrival and not looked at yet, per source that leaves new apps out. A
     * source that brings them is absent, so the keys also answer which sources leave them out.
     */
    val unseenNewApps: Flow<Map<Int, Set<String>>> = dao.getAppLists()
        .map { lists -> lists.associate { it.patchBundle to it.unseenApps } }
        .distinctUntilChanged()

    /**
     * Whether [bundleUid] brings the apps it starts supporting. Turned off, everything it names
     * now counts as known, so only an app it names later is left out.
     */
    suspend fun setBringsNewApps(bundleUid: Int, bringsNewApps: Boolean, listedApps: Set<String>) {
        if (bringsNewApps) dao.clearAppList(bundleUid)
        else dao.upsertAppList(SourceAppList(bundleUid, knownApps = listedApps))
    }

    /**
     * Leaves out what each of [bundles] names for the first time, see [SourceMuteDao.leaveOutNewApps].
     * Only the sources that leave new apps out have their apps listed, which most never do.
     */
    suspend fun leaveOutNewApps(bundles: Map<Int, PatchBundleInfo>) {
        val tracked = dao.getAppListBundleUids().toSet()
        if (tracked.isEmpty()) return
        dao.leaveOutNewApps(
            bundles.filterKeys { it in tracked }.mapValues { (_, info) -> info.listedApps() }
        )
    }

    /** The new apps of [bundleUid] have been seen in its app list and stop being flagged. */
    suspend fun markNewAppsSeen(bundleUid: Int) = dao.clearUnseenApps(bundleUid)

    suspend fun resetForBundle(bundleUid: Int) {
        dao.clearForBundle(bundleUid)
        dao.clearAppList(bundleUid)
    }

    /** Sources a backup has something to say about: an app kept from them, or new apps left out. */
    suspend fun getAllBundleUids(): List<Int> =
        (dao.getAllBundleUids() + dao.getAppListBundleUids()).distinct()

    /** The apps kept from [bundleUid], which a backup carries alongside that source's selection. */
    suspend fun exportForBundle(bundleUid: Int): List<String> = dao.getForBundle(bundleUid)

    /** What [bundleUid] has listed so far when it leaves new apps out, or null when it brings them. */
    suspend fun exportKnownApps(bundleUid: Int): List<String>? =
        dao.getAppList(bundleUid)?.knownApps?.sorted()

    /** Replaces what a backup says about [bundleUid], for a restore that starts from a clean slate. */
    suspend fun importForBundle(bundleUid: Int, packageNames: Collection<String>, knownApps: Collection<String>?) {
        resetForBundle(bundleUid)
        dao.addForBundle(bundleUid, packageNames)
        knownApps?.let { dao.upsertAppList(SourceAppList(bundleUid, knownApps = it.toSet())) }
    }

    /**
     * Adds what a backup says about [bundleUid] on top of what is here. A source that leaves new
     * apps out on either side keeps doing so, knowing the apps both sides have seen.
     */
    suspend fun mergeForBundle(bundleUid: Int, packageNames: Collection<String>, knownApps: Collection<String>?) {
        dao.addForBundle(bundleUid, packageNames)
        if (knownApps == null) return
        val current = dao.getAppList(bundleUid)
        dao.upsertAppList(
            current?.copy(knownApps = current.knownApps + knownApps)
                ?: SourceAppList(bundleUid, knownApps = knownApps.toSet())
        )
    }

    suspend fun reset() {
        dao.clear()
        dao.clearAppLists()
    }
}

/**
 * The apps a "stop offering this source" actually reaches, out of [apps], for the per-app choice
 * of which sources patch them.
 *
 * An app is left out when the source has no patches for it, or when it is already kept from it.
 * The case that matters is the third: this is the last source still offered to it. That choice is
 * about how the app is patched, and taking the last source away would take the app off the home
 * screen instead, so the exclusion is never recorded rather than recorded and then surprising.
 */
fun appsToKeepFrom(
    bundleUid: Int,
    apps: Set<String>,
    coveredBy: Map<String, Set<Int>>,
    keptFrom: Map<String, Set<Int>>
): Set<String> = apps.filterTo(mutableSetOf()) { app ->
    val covering = coveredBy[app].orEmpty()
    val alreadyKeptFrom = keptFrom[app].orEmpty()

    bundleUid in covering &&
            bundleUid !in alreadyKeptFrom &&
            (covering - alreadyKeptFrom - bundleUid).isNotEmpty()
}

/**
 * The apps this source brings to the home screen: the ones its own patches name, less the ones
 * kept from it. An app no source brings leaves the list.
 */
fun PatchBundleInfo.appsBrought(keptFrom: Map<String, Set<Int>>): Set<String> =
    listedApps().filterTo(mutableSetOf()) { uid !in keptFrom[it].orEmpty() }

/**
 * Drops the sources [muted] rules out, unless that would leave nothing behind.
 *
 * Every source of an app can end up muted: the app was taken out of every source's list, the last
 * one was deleted, or a stale row outlived the patches it pointed at. Such an app is off the home
 * screen, but an install the manager tracks still reaches patching. Ignoring the exclusions is the
 * recoverable half of that, since the user then sees more sources than they asked for rather than
 * a dead end they cannot open to fix.
 */
fun <T> List<T>.withoutMutedSources(muted: Set<Int>, uidOf: (T) -> Int): List<T> {
    if (muted.isEmpty()) return this
    return filterNot { uidOf(it) in muted }.ifEmpty { this }
}
