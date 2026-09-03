/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import java.util.concurrent.ConcurrentHashMap

/**
 * Keyed values that age out after [ttlMillis].
 *
 * Reads, writes and invalidation come from whichever thread the caller happens to be on, so the
 * store carries its own thread safety rather than leaving it to a lock the callers must remember.
 */
internal class TimedCache<K : Any, V : Any>(
    private val ttlMillis: Long,
    private val now: () -> Long = System::currentTimeMillis
) {
    private val entries = ConcurrentHashMap<K, Entry<V>>()

    /** The value held for [key], or null once it has aged out. */
    operator fun get(key: K): V? {
        val entry = entries[key] ?: return null
        if (now() - entry.storedAt <= ttlMillis) return entry.value
        // Conditional so a value written while this one was found stale survives
        entries.remove(key, entry)
        return null
    }

    operator fun set(key: K, value: V) {
        entries[key] = Entry(value, now())
    }

    fun remove(key: K) {
        entries.remove(key)
    }

    /** Drops every entry whose key matches, for invalidating a group of them at once. */
    fun removeKeys(predicate: (K) -> Boolean) {
        entries.keys.removeAll(predicate)
    }

    private class Entry<V : Any>(val value: V, val storedAt: Long)
}
