package app.revanced.manager.domain.bundles

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

class LocalPatchBundle(
    name: String,
    uid: Int,
    displayName: String?,
    createdAt: Long?,
    updatedAt: Long?,
    error: Throwable?,
    directory: File,
    enabled: Boolean
) : PatchBundleSource(name, uid, displayName, createdAt, updatedAt, error, directory, enabled) {
    suspend fun replace(
        patches: InputStream,
        totalBytes: Long? = null,
        onProgress: ((bytesRead: Long, totalBytes: Long?) -> Unit)? = null
    ) {
        withContext(Dispatchers.IO) {
            patchBundleOutputStream().use { outputStream ->
                val buffer = ByteArray(256 * 1024)
                var readTotal = 0L
                while (true) {
                    val read = patches.read(buffer)
                    if (read == -1) break
                    outputStream.write(buffer, 0, read)
                    readTotal += read
                    onProgress?.invoke(readTotal, totalBytes)
                }
            }
            requireNonEmptyPatchesFile("Importing patch bundle")
        }
    }

    suspend fun replaceFromTempFile(
        tempFile: File,
        totalBytes: Long? = null,
        onProgress: ((bytesRead: Long, totalBytes: Long?) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val target = patchesJarFile
        target.parentFile?.mkdirs()
        target.setWritable(true, true)
        if (target.exists() && !target.delete()) {
            return@withContext false
        }
        if (!tempFile.renameTo(target)) {
            return@withContext false
        }
        target.setReadOnly()
        requireNonEmptyPatchesFile("Importing patch bundle")
        onProgress?.invoke(0L, totalBytes)
        return@withContext true
    }

    override fun copy(
        error: Throwable?,
        name: String,
        displayName: String?,
        createdAt: Long?,
        updatedAt: Long?,
        enabled: Boolean
    ) = LocalPatchBundle(
        name,
        uid,
        displayName,
        createdAt,
        updatedAt,
        error,
        directory,
        enabled
    )
}
