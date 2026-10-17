/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.net.toUri
import app.morphe.manager.R

private const val TAG = "Morphe CompletionSound"

/** How long a tone may play before it is stopped, for a long track picked as one. */
private const val MAX_PLAY_MS = 60_000L

/**
 * Played as a notification, so the tone follows the notification volume rather than the ringer's
 * or the media's. Set before the player is prepared, which is the only point a player takes them.
 */
private val TONE_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
    .build()

/**
 * The tone played when patching finishes, shared by the single-app run and the batch queue.
 *
 * Only one tone is ever tracked, because only one run can finish at a time and the patcher
 * screen has to be able to stop whichever is playing when the user navigates away.
 */
object CompletionSound {

    private var active: MediaPlayer? = null

    /**
     * Plays the success or failure tone, falling back to the bundled one when the user's own
     * pick cannot be played.
     */
    fun play(
        context: Context,
        succeeded: Boolean,
        successSoundUri: String,
        errorSoundUri: String
    ) {
        val appContext = context.applicationContext
        if (!isAudible(appContext)) return

        val custom = if (succeeded) successSoundUri else errorSoundUri
        val bundledRes = if (succeeded) R.raw.success else R.raw.error
        val bundledUri = "android.resource://${appContext.packageName}/$bundledRes".toUri()
        val customUri = custom.takeIf { it.isNotBlank() }?.toUri()

        stop()
        start(appContext, listOfNotNull(customUri, bundledUri))
    }

    /**
     * Stops the tone currently playing, if any. Safe to call from any thread and any state,
     * and a no-op when nothing is playing.
     */
    fun stop() {
        val player = synchronized(this) { active.also { active = null } } ?: return
        runCatching { player.release() }
    }

    /**
     * Whether a notification would make a sound now. Silent and vibrate mode, a muted
     * notification volume and Do not disturb all keep the tone quiet, as they do notifications.
     */
    private fun isAudible(context: Context): Boolean {
        val audioManager = context.getSystemService(AudioManager::class.java) ?: return false
        if (audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL) return false
        if (audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION) == 0) return false
        val notificationManager = context.getSystemService(NotificationManager::class.java) ?: return true
        return notificationManager.currentInterruptionFilter <= NotificationManager.INTERRUPTION_FILTER_ALL
    }

    /** Plays the first of [uris] that can be, prepared off the caller's thread. */
    private fun start(context: Context, uris: List<Uri>) {
        val uri = uris.firstOrNull() ?: return
        val player = MediaPlayer()
        try {
            player.setAudioAttributes(TONE_ATTRIBUTES)
            player.setDataSource(context, uri)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load completion sound $uri", e)
            player.release()
            start(context, uris.drop(1))
            return
        }

        var started = false
        player.setOnPreparedListener {
            // Stopped while it was being prepared
            if (synchronized(this) { active !== it }) return@setOnPreparedListener
            started = true
            it.start()
            Handler(Looper.getMainLooper()).postDelayed({ release(it) }, MAX_PLAY_MS)
        }
        player.setOnCompletionListener { release(it) }
        player.setOnErrorListener { mp, what, extra ->
            Log.w(TAG, "Failed to play completion sound $uri ($what, $extra)")
            // A pick that cannot be played gives way to the bundled tone, one that broke off does not
            if (release(mp) && !started) start(context, uris.drop(1))
            true
        }

        synchronized(this) { active = player }
        player.prepareAsync()
    }

    /** Releases [player], and returns whether it was the tone being tracked. */
    private fun release(player: MediaPlayer): Boolean {
        val wasActive = synchronized(this) { (active === player).also { if (it) active = null } }
        if (wasActive) runCatching { player.release() }
        return wasActive
    }
}
