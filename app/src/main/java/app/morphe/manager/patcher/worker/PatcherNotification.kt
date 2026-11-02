/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.patcher.worker

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import app.morphe.manager.MainActivity
import app.morphe.manager.R
import app.morphe.manager.util.UpdateNotificationManager

/**
 * The ongoing notification of a patch run and the install that follows it. Every service that
 * holds them in the foreground shows it under the same ID, so there is one notification whichever
 * of them holds it.
 */
object PatcherNotification {
    const val ID = 1

    val foregroundServiceType
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0

    /** Opens the app where the run is shown. */
    @SuppressLint("WrongConstant")
    fun contentIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    /**
     * The notification for the run as [title] and [text] describe it.
     *
     * @param patchProgress Completed to total patches, or null while the step has no count.
     */
    fun build(
        context: Context,
        title: CharSequence,
        text: CharSequence?,
        patchProgress: Pair<Int, Int>? = null
    ): Notification = Notification.Builder(context, UpdateNotificationManager.CHANNEL_PATCHER)
        .setContentTitle(title)
        .setContentText(text)
        .apply {
            if (patchProgress != null) {
                val (completed, total) = patchProgress
                setSubText("$completed / $total")
                setProgress(total, completed, false)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                applyLiveUpdate(patchProgress)
            }
        }
        .setSmallIcon(Icon.createWithResource(context, R.drawable.ic_notification))
        .setContentIntent(contentIntent(context))
        .setCategory(Notification.CATEGORY_SERVICE)
        .setOngoing(true)
        .build()

    /** Request promotion to a Live Update, shown as a status bar chip while the manager is in the background. */
    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    private fun Notification.Builder.applyLiveUpdate(patchProgress: Pair<Int, Int>?) {
        val style = Notification.ProgressStyle()
        if (patchProgress != null) {
            val (completed, total) = patchProgress
            // The bar length is the sum of its segments, a single one spans every patch
            style.progressSegments = listOf(Notification.ProgressStyle.Segment(total.coerceAtLeast(1)))
            style.progress = completed
            setShortCriticalText("$completed/$total")
        } else {
            style.isProgressIndeterminate = true
        }
        setStyle(style)
        // The builder setter only exists from 36.1, the extra is what the framework reads on 36.0
        addExtras(Bundle().apply { putBoolean(NotificationCompat.EXTRA_REQUEST_PROMOTED_ONGOING, true) })
    }
}
