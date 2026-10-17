/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import app.morphe.manager.R
import app.morphe.manager.util.APK_MIMETYPE
import app.morphe.manager.util.AppCoroutineScope
import app.morphe.manager.util.exportApkTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import java.io.File

/** Saves an APK to a place the user picks, see [rememberApkExporter]. */
@Stable
class ApkExporter internal constructor(
    private val pending: MutableState<String?>,
    private val launcher: ManagedActivityResultLauncher<String, Uri?>
) {
    /** Asks where [file] goes, offering it under [name]. */
    fun export(file: File, name: String = file.name) {
        pending.value = file.absolutePath
        launcher.launch(name)
    }
}

/**
 * Remembers an [ApkExporter], which says in a message how each save went. The copy runs on the
 * app's scope, so closing the screen that asked for it does not cut the file short.
 *
 * @param onExported Told whether the file was written in full.
 */
@Composable
fun rememberApkExporter(onExported: (Boolean) -> Unit = {}): ApkExporter {
    val context = LocalContext.current.applicationContext
    val messages = koinInject<UserMessages>()
    val appScope = koinInject<AppCoroutineScope>()
    val currentOnExported by rememberUpdatedState(onExported)
    // Kept by path through recreation, as the picker can outlive the activity that opened it
    val pending = rememberSaveable { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(CreateDocument(APK_MIMETYPE)) { uri ->
        val file = pending.value?.let(::File)
        pending.value = null
        if (file == null || uri == null) return@rememberLauncherForActivityResult

        appScope.launch {
            val exported = context.exportApkTo(file, uri)
            messages.show(if (exported) R.string.save_apk_success else R.string.saved_app_export_failed)
            withContext(Dispatchers.Main) { currentOnExported(exported) }
        }
    }

    return remember(launcher) { ApkExporter(pending, launcher) }
}
