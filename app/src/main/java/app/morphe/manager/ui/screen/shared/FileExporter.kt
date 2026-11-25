/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.annotation.StringRes
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import app.morphe.manager.R
import app.morphe.manager.util.APK_MIMETYPE
import app.morphe.manager.util.AppCoroutineScope
import app.morphe.manager.util.exportFileTo
import app.morphe.manager.util.rememberDocumentSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import java.io.File

/** Saves a file the app keeps to a place the user picks, see [rememberFileExporter]. */
@Stable
class FileExporter internal constructor(
    private val pending: MutableState<String?>,
    private val save: (String) -> Unit
) {
    /** Asks where [file] goes, offering it under [name]. */
    fun export(file: File, name: String = file.name) {
        pending.value = file.absolutePath
        save(name)
    }
}

/**
 * Remembers a [FileExporter] for files of [mimeType], which says in a message how each save went.
 * The copy runs on the app's scope, so closing the screen that asked for it does not cut the file
 * short.
 *
 * @param onExported Told whether the file was written in full.
 */
@Composable
fun rememberFileExporter(
    mimeType: String,
    @StringRes successMessage: Int,
    @StringRes failureMessage: Int,
    onExported: (Boolean) -> Unit = {}
): FileExporter {
    val context = LocalContext.current.applicationContext
    val messages = koinInject<UserMessages>()
    val appScope = koinInject<AppCoroutineScope>()
    val currentOnExported by rememberUpdatedState(onExported)
    // Kept by path through recreation, as the picker can outlive the activity that opened it
    val pending = rememberSaveable { mutableStateOf<String?>(null) }

    val save = rememberDocumentSaver(mimeType) { uri ->
        val file = pending.value?.let(::File)
        pending.value = null
        if (file == null) return@rememberDocumentSaver

        appScope.launch {
            val exported = context.exportFileTo(file, uri)
            messages.show(if (exported) successMessage else failureMessage)
            withContext(Dispatchers.Main) { currentOnExported(exported) }
        }
    }

    return remember(save) { FileExporter(pending, save) }
}

/** A [FileExporter] for patched APKs. */
@Composable
fun rememberApkExporter(onExported: (Boolean) -> Unit = {}): FileExporter = rememberFileExporter(
    mimeType = APK_MIMETYPE,
    successMessage = R.string.save_apk_success,
    failureMessage = R.string.saved_app_export_failed,
    onExported = onExported
)
