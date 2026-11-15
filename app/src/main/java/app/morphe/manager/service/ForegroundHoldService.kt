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
import app.morphe.manager.notification.OngoingContent
import app.morphe.manager.notification.PatchingNotifications
import app.morphe.manager.util.tag

/**
 * The foreground [ForegroundWork] holds the app in, showing the ongoing notification of
 * [PatchingNotifications] for as long as there is work.
 *
 * It is bound rather than started, so letting go of it never races the system waiting for the
 * foreground. [Host] binds it for the first piece of work and unbinds it with the last.
 */
class ForegroundHoldService : Service() {
    private var foreground = false

    // A system that refused the foreground refuses it again, so it is asked once per binding
    private var refused = false

    override fun onBind(intent: Intent): IBinder = LocalBinder()

    override fun onDestroy() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    // Posted through the foreground alone: a system that withholds it, at times silently, then
    // shows nothing, where an ongoing notification posted directly would outlive the work
    private fun show(content: OngoingContent) {
        if (refused) return
        try {
            ServiceCompat.startForeground(
                this,
                PatchingNotifications.ONGOING_ID,
                PatchingNotifications.ongoing(this, content),
                PatchingNotifications.foregroundServiceType
            )
            foreground = true
        } catch (e: RuntimeException) {
            // The work goes on without it, as it would have without the hold
            refused = true
            Log.w(tag, "Could not hold the app in the foreground", e)
        }
    }

    /**
     * Ends the Live Update of [last] before the notification goes with the foreground. Left to
     * the foreground, the system removes the notification, while the Live Update shown apart from
     * it, such as in the Now Bar of One UI, can stay behind.
     */
    private fun endLiveUpdate(last: OngoingContent) {
        // Posting it again would bring back a notification the user dismissed
        if (!last.live || !foreground || !PatchingNotifications.isOngoingShown(this)) return
        show(last.copy(live = false))
    }

    private inner class LocalBinder : Binder() {
        val service get() = this@ForegroundHoldService
    }

    /** Holds the foreground for [ForegroundWork] through the service, bound while there is work. */
    internal class Host(private val context: Context) : ForegroundHost {
        private var connection: Connection? = null

        // What the notification shows, for a service that connects later and a Live Update to end
        private var shown: OngoingContent? = null

        @Synchronized
        override fun show(content: OngoingContent) {
            shown = content
            val bound = connection ?: Connection().also {
                connection = it
                context.bindService(Intent(context, ForegroundHoldService::class.java), it, BIND_AUTO_CREATE)
            }
            bound.service?.show(content)
        }

        @Synchronized
        override fun release() {
            val bound = connection ?: return
            connection = null
            shown?.let { bound.service?.endLiveUpdate(it) }
            shown = null
            context.unbindService(bound)
        }

        private inner class Connection : ServiceConnection {
            var service: ForegroundHoldService? = null

            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                synchronized(this@Host) {
                    // Let go of before it connected, so unbound already
                    if (connection !== this) return
                    val connected = (binder as LocalBinder).service
                    service = connected
                    shown?.let { connected.show(it) }
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                synchronized(this@Host) { service = null }
            }
        }
    }
}
