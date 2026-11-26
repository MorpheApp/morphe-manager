/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi

/** How the processes of this app ended, newest first, as the system recorded them, or only process [pid]'s. */
@RequiresApi(Build.VERSION_CODES.R)
fun Context.processExits(limit: Int = 0, pid: Int = 0): List<ApplicationExitInfo> =
    getSystemService(ActivityManager::class.java).getHistoricalProcessExitReasons(packageName, pid, limit)

/** The cause of the end, what the system added to it and the memory the process held, for a log. */
@RequiresApi(Build.VERSION_CODES.R)
fun ApplicationExitInfo.describe(): String {
    val cause = when (reason) {
        ApplicationExitInfo.REASON_LOW_MEMORY -> "killed for low memory"
        ApplicationExitInfo.REASON_SIGNALED -> "killed by signal $status"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "native crash"
        ApplicationExitInfo.REASON_CRASH -> "crash"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "killed for excessive resource usage"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "stopped by the user"
        ApplicationExitInfo.REASON_EXIT_SELF -> "exited with $status"
        else -> "ended for reason $reason"
    }
    // Both are reported in kilobytes, and left at 0 for a process killed before they were read
    val memory = "pss=${bytesToMebibytes(pss * 1024)}MB rss=${bytesToMebibytes(rss * 1024)}MB".takeIf { rss > 0 }
    return listOfNotNull(cause, description?.takeIf { it.isNotBlank() }, memory).joinToString(", ")
}
