/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.patcher.patch

/**
 * A patch source as diagnostics refer to it: what it is called and which version was used.
 *
 * Kept as a pair rather than two parallel lists, because those drift apart as soon as one of
 * them is deduplicated, and an app patched from several sources then reports the wrong
 * version against the wrong name.
 */
data class PatchSourceRef(
    val name: String,
    val version: String?
)
