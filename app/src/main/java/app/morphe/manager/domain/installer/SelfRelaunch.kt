/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.installer

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat

// Two minutes of polls, enough for a slow install while a failed one leaves nothing running long
private const val RELAUNCH_POLLS = 240
private const val RELAUNCH_POLL_INTERVAL = "0.5"

// The new version shows up before the install is through, and its last stop of the package
// would close a window opened right away
private const val RELAUNCH_SETTLE_SECONDS = 2

/**
 * A command for a privileged shell to run before Morphe installs an update of itself, which opens
 * the new version once it replaced this one. The install ends this process along with any shell
 * it owns, so the command detaches into a session of its own and returns at once.
 *
 * Null when Morphe has no launcher entry to open.
 */
internal fun selfRelaunchCommand(context: Context): String? {
    val pm = context.packageManager
    val packageName = context.packageName
    val component = pm.getLaunchIntentForPackage(packageName)?.component ?: return null
    val versionCode = PackageInfoCompat.getLongVersionCode(pm.getPackageInfo(packageName, 0))

    // The first version code dumpsys prints is the installed one, and it is empty mid-install
    val script = $$"""
        i=0
        while [ $i -lt $$RELAUNCH_POLLS ]; do
            installed="$(dumpsys package $${packageName.shellQuote()} | grep -m 1 -o 'versionCode=[0-9]*')"
            if [ -n "$installed" ] && [ "$installed" != 'versionCode=$$versionCode' ]; then
                sleep $$RELAUNCH_SETTLE_SECONDS
                exec am start --user $$appUserId -n $${component.flattenToShortString().shellQuote()}
            fi
            sleep $$RELAUNCH_POLL_INTERVAL
            i=$((i + 1))
        done
    """.trimIndent()

    return "setsid sh -c ${script.shellQuote()} </dev/null >/dev/null 2>&1 &"
}

/** Quotes this for a POSIX shell as a single word, whatever it holds. */
internal fun String.shellQuote() = "'${replace("'", "'\"'\"'")}'"
