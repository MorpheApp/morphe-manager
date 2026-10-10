/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri

/** Opens the system app info screen for Morphe. */
fun Context.openAppDetailsSettings() {
    startSettings(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
    )
}

/**
 * Opens the system notification settings for Morphe, where every channel can be tuned on its own.
 * Falls back to app info on OEM builds that drop the dedicated screen.
 */
fun Context.openAppNotificationSettings() {
    val opened = startSettings(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    )
    if (!opened) openAppDetailsSettings()
}

/**
 * Asks the system to exempt Morphe from battery optimization.
 * Patching runs long in a background worker, which Doze would otherwise cut short.
 */
@SuppressLint("BatteryLife")
fun Context.requestIgnoreBatteryOptimizations() {
    startSettings(
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.fromParts("package", packageName, null))
    )
}

/**
 * Opens the system "Open by default" screen of [targetPackageName], where its web links are
 * selected. Falls back to the app info screen on builds without it.
 */
fun Context.openAppOpenByDefaultSettings(targetPackageName: String): Boolean {
    val uri = Uri.fromParts("package", targetPackageName, null)
    return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            startSettings(Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, uri))) ||
            startSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri))
}

/**
 * Opens the Google Play page of [targetPackageName], where its auto-updates are turned off.
 * Falls back to the web page when no store app takes the link.
 */
fun Context.openPlayStorePage(targetPackageName: String) {
    val opened = startSettings(
        Intent(Intent.ACTION_VIEW, "market://details?id=$targetPackageName".toUri())
            .setPackage(PLAY_STORE_INSTALLER_PACKAGE)
    )
    if (!opened) {
        startSettings(
            Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$targetPackageName".toUri())
        )
    }
}

private fun Context.startSettings(intent: Intent): Boolean = try {
    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: ActivityNotFoundException) {
    false
}
