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

    /** Fingerprints previously taken from [file], or null when it has to be read. */
    fun get(file: File): Set<String>? {
        val key = key(file) ?: return null
        return synchronized(entries) {
            restoreOnce()
            entries[key]
        }
    }

    fun put(file: File, hashes: Set<String>) {
        val key = key(file) ?: return
        synchronized(entries) { entries[key] = hashes }

        val record = ApkSignature(
            filePath = file.absolutePath,
            fileSize = file.length(),
            lastModified = file.lastModified(),
            hashes = hashes.joinToString(ApkSignature.SEPARATOR)
        )
        scope.launch {
            runCatching { dao.upsert(record) }
                .onFailure { Log.e(tag, "Failed to persist signatures for ${file.absolutePath}", it) }
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
        restored = true
        if (Looper.myLooper() == Looper.getMainLooper()) return

        val persisted = runCatching { dao.getAllBlocking() }
            .onFailure { Log.e(tag, "Failed to read persisted APK signatures", it) }
            .getOrNull()
            ?: return

        val stale = mutableListOf<String>()
        persisted.forEach { record ->
            val file = File(record.filePath)
            if (file.length() != record.fileSize || file.lastModified() != record.lastModified) {
                stale += record.filePath
                return@forEach
            }
            entries[cacheKey(record.filePath, record.fileSize, record.lastModified)] =
                record.hashes.split(ApkSignature.SEPARATOR).filterTo(mutableSetOf()) { it.isNotEmpty() }
        }

        if (stale.isEmpty()) return
        scope.launch {
            runCatching { dao.deleteByPaths(stale) }
                .onFailure { Log.e(tag, "Failed to drop stale APK signatures", it) }
        }
    }

    private fun key(file: File): String? {
        if (!file.isFile) return null
        return cacheKey(file.absolutePath, file.length(), file.lastModified())
    }

    private fun cacheKey(path: String, size: Long, lastModified: Long) = "$path:$size:$lastModified"
}
