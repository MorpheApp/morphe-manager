/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.net.toUri
import app.morphe.manager.R

private const val TAG = "Morphe CompletionSound"

/** How long a tone is tracked before it is stopped, in case nothing else stops it. */
private const val ACTIVE_RINGTONE_RETAIN_MS = 60_000L

/**
 * The tone played when patching finishes, shared by the single-app run and the batch queue.
 *
 * Only one tone is ever tracked, because only one run can finish at a time and the patcher
 * screen has to be able to stop whichever is playing when the user navigates away.
 */
object CompletionSound {

    @Volatile
    private var active: Ringtone? = null

    /**
     * Plays the success or failure tone, falling back to the bundled one when the user's own
     * pick cannot be loaded.
     */
    fun play(
        context: Context,
        succeeded: Boolean,
        successSoundUri: String,
        errorSoundUri: String
    ) {
        val appContext = context.applicationContext

        // Respect ringer mode and notification-stream volume.
        // So users can silence the tone with the volume rocker before it plays
        val audioManager = appContext.getSystemService(AudioManager::class.java) ?: return
        if (audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        if (audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION) == 0) return

        val custom = if (succeeded) successSoundUri else errorSoundUri
        val bundledRes = if (succeeded) R.raw.success else R.raw.error
        val bundledUri = "android.resource://${appContext.packageName}/$bundledRes".toUri()
        val customUri = custom.takeIf { it.isNotBlank() }?.toUri()

        val ringtone = customUri?.let { tryGetRingtone(appContext, it) }
            ?: tryGetRingtone(appContext, bundledUri)
            ?: return
        ringtone.audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        // Exposed so the patcher screen can stop it when the user navigates home before it
        // finishes on its own
        active = ringtone
        try {
            ringtone.play()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to play completion sound", e)
        }
        // Safety-net cleanup in case the user never navigates home
        Handler(Looper.getMainLooper()).postDelayed({
            if (active === ringtone) stop()
        }, ACTIVE_RINGTONE_RETAIN_MS)
    }

    /**
     * Stops the tone currently playing, if any. Safe to call from any thread and any state,
     * and a no-op when nothing is playing.
     */
    fun stop() {
        val ringtone = active ?: return
        active = null
        runCatching { ringtone.stop() }
    }

    private fun tryGetRingtone(context: Context, uri: Uri): Ringtone? = runCatching {
        RingtoneManager.getRingtone(context, uri)
    }.onFailure {
        Log.w(TAG, "Failed to load ringtone $uri", it)
    }.getOrNull()
}
