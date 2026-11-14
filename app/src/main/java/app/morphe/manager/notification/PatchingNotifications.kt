/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.notification

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
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

/**
 * The notifications of patching and of the install that follows it.
 *
 * The ongoing one exists because a foreground service cannot go without one, and stays quiet on
 * [NotificationChannels.PATCHER]. Every service that holds the run in the foreground shows it under
 * [ONGOING_ID], so there is one notification whichever of them holds it.
 * [ForegroundWork][app.morphe.manager.service.ForegroundWork] keeps it up to date, while the
 * services of WorkManager and of the patcher process take their foreground under it as it stands
 * when they start. What the user should notice, a result or an install waiting on them, goes to
 * [NotificationChannels.PATCHING_RESULTS].
 */
object PatchingNotifications {
    const val ONGOING_ID = 1
    private const val PATCH_RESULT_ID = 2
    private const val BATCH_RESULT_ID = 2005
    private const val INSTALL_CONFIRMATION_ID = 2006

    val foregroundServiceType
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0

    /** Opens the app where the run is shown. */
    private fun patcherIntent(context: Context) = NotificationIntents.activity(
        context,
        NotificationIntents.REQUEST_CODE_PATCHER,
        Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
    )

    /** The ongoing notification showing [content]. */
    fun ongoing(
        context: Context,
        content: OngoingContent
    ): Notification = Notification.Builder(context, NotificationChannels.PATCHER)
        .setContentTitle(content.title)
        .setContentText(content.text)
        .apply {
            content.patchProgress?.let { (completed, total) ->
                setSubText("$completed / $total")
                setProgress(total, completed, false)
            }
            if (content.live && Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                applyLiveUpdate(content.patchProgress)
            }
            // Otherwise the system holds back a foreground started on screen for ten seconds, and
            // a run shows from its start or only near its end depending on how recent the last was
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            }
        }
        .setSmallIcon(Icon.createWithResource(context, R.drawable.ic_notification))
        .setContentIntent(patcherIntent(context))
        .setCategory(Notification.CATEGORY_SERVICE)
        .setOngoing(true)
        // Every step and patch posts it again, a channel the user made audible must not sound for each
        .setOnlyAlertOnce(true)
        .build()

    /** Whether the ongoing notification is on screen, which the user may have dismissed. */
    fun isOngoingShown(context: Context): Boolean =
        context.notificationManager.activeNotifications.any { it.id == ONGOING_ID }

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

    /**
     * Post the result of a single run that finished while the user was not watching it.
     *
     * @param silent Whether the completion tone of the app plays instead of the channel sound.
     */
    fun showPatchResult(context: Context, succeeded: Boolean, silent: Boolean) = showResult(
        context,
        PATCH_RESULT_ID,
        succeeded,
        context.getText(R.string.patcher_notification_text),
        patcherIntent(context),
        silent
    )

    /**
     * Post the result of a queue that finished while the user was not watching it. Tapping it
     * opens the queue on that run.
     *
     * @param silent Whether the completion tone of the app plays instead of the channel sound.
     */
    fun showBatchResult(context: Context, patched: Int, failed: Int, skipped: Int, silent: Boolean) =
        showResult(
            context,
            BATCH_RESULT_ID,
            succeeded = patched > 0,
            context.getString(
                R.string.batch_patch_summary,
                patched.toString(),
                failed.toString(),
                skipped.toString()
            ),
            NotificationIntents.activity(context, NotificationIntents.REQUEST_CODE_BATCH_RESULT) {
                action = MainActivity.ACTION_SHOW_BATCH_RESULT
            },
            silent
        )

    private fun showResult(
        context: Context,
        notificationId: Int,
        succeeded: Boolean,
        text: CharSequence,
        contentIntent: PendingIntent,
        silent: Boolean
    ) {
        val notification = NotificationCompat.Builder(context, NotificationChannels.PATCHING_RESULTS)
            .setSmallIcon(
                if (succeeded) R.drawable.ic_notification_done else R.drawable.ic_notification_failed
            )
            .setColor(
                context.getColor(
                    if (succeeded) R.color.notification_success else R.color.notification_failure
                )
            )
            .setContentTitle(
                context.getString(
                    if (succeeded) R.string.patcher_complete_title else R.string.patcher_failed_title
                )
            )
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setSilent(silent)
            .build()

        context.notificationManager.notify(notificationId, notification)
    }

    /**
     * Clear patching results once the user is back in the manager, where the result is already
     * on screen. Tapping one is not the only way back, recents and the launcher are too.
     */
    fun cancelResults(context: Context) {
        context.notificationManager.cancel(PATCH_RESULT_ID)
        context.notificationManager.cancel(BATCH_RESULT_ID)
    }

    /**
     * Posts [confirmIntent], the system dialog an install is waiting on, so a tap opens it. The
     * install stands still until the user answers, so it goes where they will notice it.
     */
    @SuppressLint("WrongConstant")
    fun showInstallConfirmation(context: Context, confirmIntent: Intent) {
        val pendingIntent = PendingIntent.getActivity(
            context,
            INSTALL_CONFIRMATION_ID,
            confirmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(context, NotificationChannels.PATCHING_RESULTS)
            .setContentTitle(context.getString(R.string.installer_confirm_notification_title))
            .setContentText(context.getString(R.string.installer_confirm_notification_text))
            .setSmallIcon(Icon.createWithResource(context, R.drawable.ic_notification))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        context.notificationManager.notify(INSTALL_CONFIRMATION_ID, notification)
    }

    /** Takes down a confirmation the session no longer waits on, or that the user went back to. */
    fun cancelInstallConfirmation(context: Context) {
        context.notificationManager.cancel(INSTALL_CONFIRMATION_ID)
    }

    private val Context.notificationManager: NotificationManager
        get() = getSystemService(NotificationManager::class.java)
}
