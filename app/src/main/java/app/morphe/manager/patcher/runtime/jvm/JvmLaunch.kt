/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.patcher.runtime.jvm

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * How [app.morphe.manager.patcher.runtime.PatcherService] starts the JVM, settled by the app so
 * the service only has to carry it out.
 *
 * @param command What starts the JVM as a process of its own, followed by its options.
 * @param mainClass The class whose main runs, such as `a.b.MainKt`.
 * @param heapLimitMb The heap to give the JVM.
 */
@Parcelize
data class JvmLaunch(
    val command: List<String>,
    val options: List<String>,
    val mainClass: String,
    val heapLimitMb: Int
) : Parcelable
