/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.backup

import android.app.Application
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import app.morphe.manager.domain.backup.SettingsExporter.Companion.settingsJson
import app.morphe.manager.domain.manager.KeystoreManager
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.domain.manager.SettingsSection
import app.morphe.manager.ui.viewmodel.ManagerSettingsExportFile
import app.morphe.manager.util.FilenameUtils
import app.morphe.manager.util.JSON_MIMETYPE
import app.morphe.manager.util.getOrCreateFile
import app.morphe.manager.util.pickedFolder
import app.morphe.manager.util.sha256Fingerprint
import app.morphe.manager.util.tag
import app.morphe.manager.worker.AutoBackupWorker
import app.morphe.manager.worker.ScheduleInterval
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.encodeToStream
import java.io.IOException
import java.io.OutputStream
import kotlin.time.Duration.Companion.minutes

/**
 * Keeps copies of the manager's settings, and of the signing key where the user wants one, in a
 * folder of their choosing, such as one a sync app carries off the device.
 *
 * Settings only change while the app is open, so the next copy is planned whenever the user leaves
 * it, for once [PreferencesManager.autoBackupInterval] has passed since the last one. Nothing has
 * to wake the device in between, and a copy no different from the last one is never written.
 */
@OptIn(ExperimentalSerializationApi::class)
class AutoBackup(
    private val app: Application,
    private val prefs: PreferencesManager,
    private val exporter: SettingsExporter,
    private val keystoreManager: KeystoreManager
) {
    /**
     * Starts backing up into [folder], just picked by the user, and makes the first copy at once,
     * which shows the folder works. Holds on to the grant of a folder from the system picker and
     * lets go of the one held before. False when the first copy could not be written.
     */
    suspend fun enable(folder: Uri): Boolean {
        if (folder.scheme == ContentResolver.SCHEME_CONTENT) {
            app.contentResolver.takePersistableUriPermission(folder, FOLDER_ACCESS)
        }
        val previous = prefs.autoBackupFolder.get()
        if (previous != folder.toString()) releaseGrant(previous)
        prefs.edit {
            prefs.autoBackupFolder.value = folder.toString()
            prefs.autoBackupFailed.value = false
        }
        // Forced, as the copies made so far are in the old folder
        return backUpNow()
    }

    /**
     * Writes a copy right away, even one no different from the last, for the user asking for it.
     * Finished even if the screen that asked goes away, so no copy is left half written.
     */
    suspend fun backUpNow(): Boolean = withContext(NonCancellable) { backUp(force = true) }

    /** Stops backing up and lets go of the folder. The copies already made stay in it. */
    suspend fun disable() {
        // First, so a copy being written cannot record itself after the rest is cleared
        AutoBackupWorker.cancel(app)
        releaseGrant(prefs.autoBackupFolder.get())
        prefs.edit {
            prefs.autoBackupFolder.value = ""
            prefs.lastAutoBackupAt.value = 0L
            prefs.lastAutoBackupDigest.value = ""
            prefs.autoBackupFailed.value = false
        }
    }

    suspend fun setInterval(interval: ScheduleInterval) {
        prefs.autoBackupInterval.update(interval)
        scheduleNext(replace = true)
    }

    /**
     * Whether the signing key goes along. Turned on, it is written right away rather than with
     * the next change.
     */
    suspend fun setIncludesSigningKey(include: Boolean) {
        prefs.autoBackupSigningKey.update(include)
        if (include && isEnabled()) AutoBackupWorker.schedule(app, delayMillis = 0, replace = true)
    }

    /**
     * Plans the next copy for once the interval since the last one has passed, called whenever the
     * user leaves the app. A copy already planned stays as it is unless [replace] moves it, as its
     * time only changes along with the interval.
     */
    suspend fun scheduleNext(replace: Boolean = false) {
        if (!isEnabled()) return
        val interval = prefs.autoBackupInterval.get().minutes.minutes
        val dueAt = prefs.lastAutoBackupAt.get() + interval.inWholeMilliseconds
        AutoBackupWorker.schedule(app, (dueAt - System.currentTimeMillis()).coerceAtLeast(0), replace)
    }

    /**
     * Writes a copy into the folder unless it would match the last one and is not [forced][force],
     * along with the signing key where the user wants it. False when the folder could not be
     * written to, which the settings show.
     */
    suspend fun backUp(force: Boolean): Boolean = withContext(Dispatchers.IO) {
        val folderUri = prefs.autoBackupFolder.get().ifEmpty { return@withContext true }
        val succeeded = try {
            val folder = app.pickedFolder(folderUri.toUri())?.takeIf { it.canWrite() }
                ?: throw IOException("The backup folder cannot be written to")
            if (prefs.autoBackupSigningKey.get()) backUpSigningKey(folder)
            backUpSettings(folder, force)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(tag, "Automatic backup failed", e)
            false
        }
        prefs.autoBackupFailed.update(!succeeded)
        succeeded
    }

    private suspend fun backUpSettings(folder: DocumentFile, force: Boolean) {
        // Every section, whatever the last manual export left out, as what to take back is chosen on restore
        val export = exporter.managerSettings(SettingsSection.entries.toSet())
        val digest = export.digest()
        if (!force && digest == prefs.lastAutoBackupDigest.get()) return

        folder.writeNew(JSON_MIMETYPE, FilenameUtils.timestamped(SETTINGS_FILE_NAME)) { output ->
            settingsJson.encodeToStream(export, output)
        }
        prefs.edit {
            prefs.lastAutoBackupDigest.value = digest
            prefs.lastAutoBackupAt.value = System.currentTimeMillis()
        }
        pruneOldCopies(folder)
    }

    /**
     * Writes the key under a name taken from its fingerprint, so it is written once, and a key the
     * user imports later goes beside it rather than over the one older apps were signed with.
     */
    private suspend fun backUpSigningKey(folder: DocumentFile) {
        val key = keystoreManager.signingKeyInfo() ?: return
        val name = "$SIGNING_KEY_FILE_PREFIX${key.sha256.take(8)}.p12"
        if (folder.findFile(name) != null) return
        folder.writeNew(KEYSTORE_MIMETYPE, name) { output -> keystoreManager.export(output) }
    }

    /**
     * Leaves the newest [KEPT_COPIES] copies in the folder. Files it holds besides them are never
     * touched.
     */
    private fun pruneOldCopies(folder: DocumentFile) {
        folder.listFiles()
            .filter { file -> file.name?.let { it.startsWith(SETTINGS_FILE_PREFIX) && it.endsWith(".json") } == true }
            // The timestamp in the name sorts them by age
            .sortedByDescending { it.name }
            .drop(KEPT_COPIES)
            .forEach { it.delete() }
    }

    /** Writes a new file [name] into this folder, and removes it again should the write fail halfway. */
    private inline fun DocumentFile.writeNew(mimeType: String, name: String, write: (OutputStream) -> Unit) {
        val file = getOrCreateFile(mimeType, name) ?: throw IOException("Could not create $name")
        try {
            app.contentResolver.openOutputStream(file.uri, "wt")!!.use(write)
        } catch (e: Exception) {
            file.delete()
            throw e
        }
    }

    /** Lets go of the grant to [folder], held only for one the system picker gave. */
    private fun releaseGrant(folder: String) {
        val uri = folder.ifEmpty { return }.toUri()
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return
        // Already gone once the user revoked it, or for data restored from another device
        runCatching { app.contentResolver.releasePersistableUriPermission(uri, FOLDER_ACCESS) }
    }

    private suspend fun isEnabled() = prefs.autoBackupFolder.get().isNotEmpty()

    companion object {
        /** Copies kept in the folder, enough to go back past a change that is regretted only later. */
        const val KEPT_COPIES = 5

        private const val SETTINGS_FILE_NAME = "morphe_settings_backup.json"

        /** What [FilenameUtils.timestamped] leaves of [SETTINGS_FILE_NAME] ahead of the timestamp. */
        private const val SETTINGS_FILE_PREFIX = "morphe_settings_backup-"

        private const val SIGNING_KEY_FILE_PREFIX = "morphe_signing_key-"
        private const val KEYSTORE_MIMETYPE = "application/x-pkcs12"
        private const val FOLDER_ACCESS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    }
}

/** What tells two backups apart: the settings they hold, without the dates written into them. */
private fun ManagerSettingsExportFile.digest(): String {
    val undated = copy(
        selections = selections?.run { copy(exportDate = "", bundles = bundles.map { it.copy(exportDate = "") }) }
    )
    return settingsJson.encodeToString(undated).toByteArray().sha256Fingerprint()
}
