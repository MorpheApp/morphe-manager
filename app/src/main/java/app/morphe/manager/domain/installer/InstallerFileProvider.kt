package app.morphe.manager.domain.installer

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/**
 * Lightweight content provider used to expose APK files to external installers.
 *
 * It mirrors the behavior we relied on from [androidx.core.content.FileProvider] while avoiding
 * the XML parsing crash that occurred on some devices when launching third-party installers.
 */
class InstallerFileProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val columns = projection?.takeIf { it.isNotEmpty() }
            ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val file = buildFile(contextOrThrow(), uri)
        val cursor = MatrixCursor(columns)
        val row = Array<Any?>(columns.size) { null }
        columns.forEachIndexed { index, column ->
            row[index] = when (column) {
                OpenableColumns.DISPLAY_NAME -> file.name
                OpenableColumns.SIZE -> file.takeIf { it.exists() }?.length() ?: 0L
                else -> null
            }
        }
        cursor.addRow(row)
        return cursor
    }

    override fun getType(uri: Uri): String = APK_MIME

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        throw UnsupportedOperationException("Read-only provider")
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        val file = buildFile(contextOrThrow(), uri)
        return if (file.exists() && file.delete()) 1 else 0
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        throw UnsupportedOperationException("Read-only provider")
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (!mode.contains('r')) {
            throw IllegalArgumentException("Only read access is supported.")
        }
        val file = buildFile(contextOrThrow(), uri)
        if (!file.exists()) {
            throw FileNotFoundException("File ${file.name} does not exist.")
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    private fun contextOrThrow(): Context = context
        ?: throw IllegalStateException("Context unavailable for InstallerFileProvider")

    companion object {
        private const val APK_MIME = "application/vnd.android.package-archive"
        const val SHARE_DIR = "installer_share"

        fun authority(context: Context): String = "${context.packageName}.installerfileprovider"

        fun buildUri(context: Context, fileName: String): Uri =
            Uri.Builder()
                .scheme("content")
                .authority(authority(context))
                .appendPath(fileName)
                .build()

        /**
         * Copies [file] into the share directory under its original name (overwriting if size
         * differs) and returns a content URI for it. Used by [AckpineInstaller] for internal
         * and Shizuku installs, and by [InstallerManager] for external installer intents.
         */
        fun getUriForFile(context: Context, file: File): Uri {
            val shareDir = File(context.cacheDir, SHARE_DIR).also { it.mkdirs() }
            val dest = File(shareDir, file.name)
            // Always copy if size or mtime differs. We stamp dest with the source's mtime
            // after each copy, so dest.lastModified() == file.lastModified() on the next
            // call only if the source hasn't changed — skipping the copy is then safe.
            // Using < instead of != would silently serve a stale cached APK when the patcher
            // rebuilds the same-named output file without changing its size, causing
            // INSTALL_FAILED_UPDATE_INCOMPATIBLE on update installs
            if (!dest.exists() || dest.length() != file.length() || dest.lastModified() != file.lastModified()) {
                file.copyTo(dest, overwrite = true)
                dest.setLastModified(file.lastModified())
            }
            return buildUri(context, dest.name)
        }

        private fun buildFile(context: Context, uri: Uri): File {
            if (uri.authority != authority(context)) {
                throw IllegalArgumentException("Unknown authority: ${uri.authority}")
            }
            val segments = uri.pathSegments
            if (segments.size != 1) {
                throw IllegalArgumentException("Unexpected path: ${uri.path}")
            }
            val fileName = segments.first()
            require(".." !in fileName) { "Path traversal is not allowed." }

            val dir = File(context.cacheDir, SHARE_DIR)
            val target = File(dir, fileName)
            val canonicalDir = dir.canonicalFile
            val canonicalTarget = target.canonicalFile
            if (!canonicalTarget.path.startsWith(canonicalDir.path)) {
                throw SecurityException("Resolved path escapes share directory.")
            }
            return canonicalTarget
        }
    }
}
