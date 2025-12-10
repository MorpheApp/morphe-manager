package app.revanced.manager.domain.bundles

import app.revanced.manager.data.redux.ActionContext
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
    directory: File
) : PatchBundleSource(name, uid, displayName, createdAt, updatedAt, error, directory) {
    suspend fun ActionContext.replace(patches: InputStream) {
        withContext(Dispatchers.IO) {
            patchBundleOutputStream().use { outputStream ->
                patches.copyTo(outputStream)
            }
        }
    }

    override fun copy(
        error: Throwable?,
        name: String,
        displayName: String?,
        createdAt: Long?,
        updatedAt: Long?
    ) = LocalPatchBundle(
        name,
        uid,
        displayName,
        createdAt,
        updatedAt,
        error,
        directory
    )
}
