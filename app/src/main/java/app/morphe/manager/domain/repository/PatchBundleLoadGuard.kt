package app.morphe.manager.domain.repository

import android.app.Application
import android.os.Build
import android.util.Log
import app.morphe.manager.BuildConfig
import app.morphe.manager.util.tag
import java.io.File

/**
 * Keeps a patch bundle that cannot be read from taking the launch down with it.
 *
 * Reading a bundle runs its dex inside this process, so a fault in it arrives as a native signal
 * no catch block can reach: the process disappears without a stack trace and without naming the
 * bundle responsible. Both halves of this class exist for that failure mode, one to remove a known
 * cause and one to contain the rest.
 */
class PatchBundleLoadGuard(
    private val app: Application,
    private val bundlesDir: File,
) {
    private val dexCacheStamp = bundlesDir.resolve(DEX_CACHE_STAMP_FILE)
    private val ledgerFile = bundlesDir.resolve(LEDGER_FILE)

    // Guards the strike table and the ledger it is written to. Reads run outside it, so a reload
    // and a patcher run never wait on each other for the length of a dex load
    private val lock = Any()
    private val strikes = mutableMapOf<Int, Strike>()
    private var prepared = false

    /**
     * Settles the on-disk state left by earlier runs. Must run before the first bundle is read.
     */
    fun prepare() = synchronized(lock) {
        if (prepared) return@synchronized
        prepared = true

        strikes.putAll(readLedger())
        purgeDexCachesOfPreviousInstall()
        recordInterruptedLoads()
    }

    /**
     * Reads a bundle with [uid] attributed on disk, so a native death inside [load] is traced back
     * to it on the next launch. Throws [PatchBundleHeldBackException] once it has done so twice.
     */
    fun <T> read(uid: Int, patchesJar: File, load: () -> T): T {
        val stamp = stampOf(patchesJar)

        synchronized(lock) {
            val strike = strikes[uid]
            if (strike != null) {
                if (strike.stamp != stamp) {
                    // A replaced bundle ships a different dex and gets a cache of its own, so
                    // whatever the old file did says nothing about this one
                    strikes.remove(uid)
                    writeLedger()
                } else if (strike.count >= HELD_BACK_AFTER) {
                    throw PatchBundleHeldBackException(uid)
                }
            }
        }

        val marker = inFlightFileFor(uid)
        runCatching { marker.writeText(stamp) }
            .onFailure { Log.e(tag, "Could not mark bundle $uid as loading", it) }

        try {
            return load().also {
                synchronized(lock) {
                    if (strikes.remove(uid) != null) writeLedger()
                }
            }
        } finally {
            runCatching { marker.delete() }
        }
    }

    /**
     * Drops what is remembered about [uid], for a bundle that is being taken off disk.
     */
    fun forget(uid: Int) = synchronized(lock) {
        if (strikes.remove(uid) != null) writeLedger()
    }

    /**
     * Drops the dex caches built against an earlier install of the manager.
     *
     * Up to Android 10 the cache next to a bundle holds quickened bytecode, which carries vtable
     * indices of the manager's own classes. ART keeps reusing that cache after the manager is
     * replaced, so an updated manager calls through stale indices and dies on a signal. Later
     * runtimes dropped quickening and invalidate the cache on their own.
     */
    private fun purgeDexCachesOfPreviousInstall() {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) return

        val identity = installIdentity()
        if (runCatching { dexCacheStamp.readText() }.getOrNull() == identity) return

        bundlesDir.listFiles()?.forEach { entry ->
            val oat = entry.resolve(DEX_CACHE_DIR)
            if (!oat.isDirectory) return@forEach
            if (oat.deleteRecursively()) {
                Log.d(tag, "Dropped the stale dex cache of bundle ${entry.name}")
            } else {
                Log.w(tag, "Could not drop the stale dex cache of bundle ${entry.name}")
            }
        }

        runCatching { dexCacheStamp.writeText(identity) }
            .onFailure { Log.e(tag, "Could not record the dex cache identity", it) }
    }

    /**
     * Turns every read that never finished into a strike against the bundle it was reading.
     */
    private fun recordInterruptedLoads() {
        val markers = bundlesDir.listFiles { file -> file.name.startsWith(IN_FLIGHT_PREFIX) }
        markers?.forEach { marker ->
            val uid = marker.name.removePrefix(IN_FLIGHT_PREFIX).toIntOrNull()
            val stamp = runCatching { marker.readText() }.getOrNull()?.trim().orEmpty()
            runCatching { marker.delete() }
            if (uid == null) return@forEach

            val previous = strikes[uid]?.takeIf { it.stamp == stamp }
            val count = (previous?.count ?: 0) + 1
            strikes[uid] = Strike(count, stamp)

            // A process can also die mid-read because the user swiped it away or the system
            // reclaimed it, so one interrupted read is not yet a verdict on the bundle
            Log.w(
                tag,
                "Bundle $uid was being read when the previous process died ($count of $HELD_BACK_AFTER)"
            )
        }

        if (!markers.isNullOrEmpty()) writeLedger()
    }

    private fun readLedger(): Map<Int, Strike> {
        val lines = runCatching { ledgerFile.readLines() }.getOrNull() ?: return emptyMap()
        return lines.mapNotNull { line ->
            val parts = line.split('|')
            if (parts.size != 3) return@mapNotNull null
            val uid = parts[0].toIntOrNull() ?: return@mapNotNull null
            val count = parts[1].toIntOrNull() ?: return@mapNotNull null
            uid to Strike(count, parts[2])
        }.toMap()
    }

    private fun writeLedger() {
        runCatching {
            if (strikes.isEmpty()) {
                ledgerFile.delete()
                return@runCatching
            }

            ledgerFile.writeText(
                strikes.entries.joinToString("\n") { (uid, strike) ->
                    "$uid|${strike.count}|${strike.stamp}"
                }
            )
        }.onFailure { Log.e(tag, "Could not record bundle read failures", it) }
    }

    private fun inFlightFileFor(uid: Int) = bundlesDir.resolve("$IN_FLIGHT_PREFIX$uid")

    /**
     * Identifies the installed manager the way ART does, by the APK it was loaded from.
     */
    private fun installIdentity() = "${BuildConfig.VERSION_CODE}|${app.applicationInfo.sourceDir}"

    private fun stampOf(patchesJar: File) =
        runCatching { "${patchesJar.lastModified()}-${patchesJar.length()}" }.getOrDefault("unknown")

    private data class Strike(val count: Int, val stamp: String)

    private companion object {
        const val DEX_CACHE_DIR = "oat"
        const val DEX_CACHE_STAMP_FILE = "dex-cache-identity"
        const val IN_FLIGHT_PREFIX = "reading-"
        const val LEDGER_FILE = "read-failures"

        /** Interrupted reads of the same file tolerated before the bundle is held back. */
        const val HELD_BACK_AFTER = 2
    }
}

/**
 * Thrown in place of reading a bundle that has already killed the process from the same file.
 */
class PatchBundleHeldBackException(val uid: Int) : Exception(
    "Bundle $uid killed the process while loading and will not be read again until it changes"
)
