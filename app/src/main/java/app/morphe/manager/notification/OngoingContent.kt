/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.notification

/**
 * What the ongoing notification says about one piece of work.
 *
 * @param live Whether it is the Live Update of patching, the one step that reports progress.
 * The work before and after it shows a plain notification.
 * @param patchProgress Completed to total patches, or null while the step has no count.
 */
data class OngoingContent(
    val title: CharSequence,
    val text: CharSequence? = null,
    val live: Boolean = false,
    val patchProgress: Pair<Int, Int>? = null
)
