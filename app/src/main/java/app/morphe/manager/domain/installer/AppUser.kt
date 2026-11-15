/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.installer

import android.os.Process

// UserHandle.PER_USER_RANGE, which Android keeps hidden
private const val PER_USER_RANGE = 100_000

/**
 * The user Morphe runs in. An install with elevated rights is kept to it, as the system installer
 * keeps every install, rather than reaching a work profile or private space as well.
 */
internal val appUserId: Int get() = Process.myUid() / PER_USER_RANGE
