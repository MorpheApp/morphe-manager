/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.patcher.patch

/**
 * UI-facing lock state derived from a patch's availability resolver for the current install target.
 *
 * Not stored anywhere; computed on demand by [PatchInfo.lockState] and consumed by the expert-mode
 * dialog to disable checkboxes and surface the reason the patch cannot be toggled.
 */
enum class PatchLockState {
    /** Patch can be toggled freely by the user. */
    NONE,

    /** Patch is required for the current install target and cannot be unchecked. */
    LOCKED_ON,

    /** Patch is not available for the current install target and cannot be checked. */
    LOCKED_OFF,
}
