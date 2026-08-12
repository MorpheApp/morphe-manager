/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.apk

import android.os.Looper
import android.util.Log
import app.morphe.manager.data.room.AppDatabase
import app.morphe.manager.data.room.apk.ApkSignature
import app.morphe.manager.util.AppCoroutineScope
import app.morphe.manager.util.tag
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

// Enough for every saved, original and installed archive a tracked app can point at
private const val MEMORY_ENTRIES = 256

/**
 * Certificate fingerprints of APKs on disk, remembered across process restarts.
 *
 * Extracting them verifies the whole archive, so a cold start must not repeat the work. An entry
 * describes one exact archive: a file rewritten at the same path is keyed apart and read again.
 */
class ApkSignatureCache(
    db: AppDatabase,
    private val scope: AppCoroutineScope
) {
    private val dao = db.apkSignatureDao()

    private val entries = object : LinkedHashMap<String, Set<String>>(0, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Set<String>>) =
            size > MEMORY_ENTRIES
    }
    private var restored = false
    private val persistenceMutex = Mutex()

    /** Captures the exact file revision that an archive read is about to inspect. */
    internal fun stamp(file: File): ApkFileStamp? = file.apkFileStampOrNull()

    /** Fingerprints previously taken from [stamp], or null when it has to be read. */
    internal fun get(stamp: ApkFileStamp): Set<String>? {
        return synchronized(entries) {
            restoreOnce()
            entries[stamp.cacheKey]
        }
    }

    /**
     * Stores [hashes] only if [file] is still the revision that was inspected.
     *
     * Retained APKs are overwritten in place. Rechecking the stamp prevents hashes read from the
     * old archive from being assigned to the new revision, while the mutex prevents an older
     * asynchronous database write from winning after a newer one.
     */
    internal fun putIfUnchanged(file: File, stamp: ApkFileStamp, hashes: Set<String>) {
        if (file.apkFileStampOrNull() != stamp) return
        synchronized(entries) { entries[stamp.cacheKey] = hashes }

        val record = ApkSignature(
            filePath = stamp.path,
            fileSize = stamp.size,
            lastModified = stamp.lastModified,
            hashes = hashes.joinToString(ApkSignature.SEPARATOR)
        )
        scope.launch {
            persistenceMutex.withLock {
                if (file.apkFileStampOrNull() != stamp) return@withLock
                runCatching { dao.upsert(record) }
                    .onFailure { Log.e(tag, "Failed to persist signatures for ${file.absolutePath}", it) }
            }
        }
    }

    /**
     * Loads the persisted entries on first use.
     *
     * A single query on the caller's thread, because a suspending hand-off would race with the
     * very archive read this exists to spare. A main-thread caller keeps the memory layer only.
     */
    private fun restoreOnce() {
        if (restored) return
        if (Looper.myLooper() == Looper.getMainLooper()) return
        restored = true

        val persisted = runCatching { dao.getAllBlocking() }
            .onFailure { Log.e(tag, "Failed to read persisted APK signatures", it) }
            .getOrNull()
            ?: return

        val stale = mutableListOf<ApkSignature>()
        persisted.forEach { record ->
            val file = File(record.filePath)
            if (file.length() != record.fileSize || file.lastModified() != record.lastModified) {
                stale += record
                return@forEach
            }
            entries[ApkFileStamp(record.filePath, record.fileSize, record.lastModified).cacheKey] =
                record.hashes.split(ApkSignature.SEPARATOR).filterTo(mutableSetOf()) { it.isNotEmpty() }
        }

        if (stale.isEmpty()) return
        scope.launch {
            persistenceMutex.withLock {
                runCatching {
                    stale.forEach { record ->
                        dao.deleteRevision(record.filePath, record.fileSize, record.lastModified)
                    }
                }.onFailure { Log.e(tag, "Failed to drop stale APK signatures", it) }
            }
        }
    }
}
