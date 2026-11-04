/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.service

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import app.morphe.manager.notification.PatchingNotifications
import app.morphe.manager.util.tag
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Keeps the app in the foreground while work that must not die with it runs in its own process:
 * a patch run up to the install it hands over to, a batch run between the workers of its apps,
 * or an install itself.
 *
 * Some systems end a process without a foreground service as soon as the app leaves the screen,
 * and refuse one started from the background. Taken on screen, a [Hold] covers what follows, and
 * lets a worker started meanwhile take its foreground as well. Each [Hold] binds the service, which
 * stays in the foreground until the last one is released.
 *
 * It is bound rather than started, so letting go of it never races the system waiting for the
 * foreground. It shows the ongoing notification of [PatchingNotifications], which a worker takes
 * over while it runs.
 */
class ForegroundHoldService : Service() {
    override fun onBind(intent: Intent): IBinder = LocalBinder()

    override fun onDestroy() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    // Posted through the foreground alone: a system that withholds it, at times silently, then
    // shows nothing, where an ongoing notification posted directly would outlive the work
    private fun show(title: CharSequence, text: CharSequence?) {
        try {
            ServiceCompat.startForeground(
                this,
                PatchingNotifications.ONGOING_ID,
                PatchingNotifications.ongoing(this, title, text, live = false),
                PatchingNotifications.foregroundServiceType
            )
        } catch (e: RuntimeException) {
            // The work goes on without it, as it would have without the hold
            Log.w(tag, "Could not hold the app in the foreground", e)
        }
    }

    private inner class LocalBinder : Binder() {
        val service get() = this@ForegroundHoldService
    }

    /** A hold on the foreground for one piece of work, shown as [title] and [text] while the latest. */
    class Hold(
        private val context: Context,
        title: CharSequence,
        text: CharSequence? = null
    ) : ServiceConnection {
        @Volatile
        private var service: ForegroundHoldService? = null

        @Volatile
        private var content = title to text

        private val released = AtomicBoolean(false)

        init {
            context.bindService(Intent(context, ForegroundHoldService::class.java), this, BIND_AUTO_CREATE)
        }

        /** Shows the work as [title] and [text] from now on. */
        fun update(title: CharSequence, text: CharSequence? = null) {
            content = title to text
            refresh()
        }

        /** Shows the work again, over the last step a worker that has finished left in the notification. */
        fun refresh() {
            val (title, text) = content
            service?.show(title, text)
        }

        /** Ends the hold. A canceled run may get here twice, and only the first call counts. */
        fun release() {
            if (!released.compareAndSet(false, true)) return
            service = null
            context.unbindService(this)
        }

        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = (binder as LocalBinder).service
            refresh()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
        }
    }
}
