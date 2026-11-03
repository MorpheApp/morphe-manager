/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.notification

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.morphe.manager.MainActivity

/**
 * What a tap on a notification of the app opens. The request codes live together because each
 * has to be unique per target: a shared one would rewrite the intent of every notification using it.
 */
internal object NotificationIntents {
    const val REQUEST_CODE_PATCHER = 0
    const val REQUEST_CODE_UPDATE_CHECK = 1
    const val REQUEST_CODE_BATCH_RESULT = 2
    const val REQUEST_CODE_BUNDLE_CHANGELOG = 3
    const val REQUEST_CODE_MANAGER_CHANGELOG = 4
    const val REQUEST_CODE_MOUNT_REPLACED = 5

    /** Wraps an intent onto [MainActivity] as a [PendingIntent] under [requestCode]. */
    @SuppressLint("WrongConstant")
    fun activity(
        context: Context,
        requestCode: Int,
        flags: Int = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP,
        configure: Intent.() -> Unit = {}
    ): PendingIntent = PendingIntent.getActivity(
        context,
        requestCode,
        Intent(context, MainActivity::class.java).setFlags(flags).apply(configure),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
}
