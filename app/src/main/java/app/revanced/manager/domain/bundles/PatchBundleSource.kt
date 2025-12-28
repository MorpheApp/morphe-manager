package app.revanced.manager.domain.bundles

import androidx.compose.runtime.Stable
import app.revanced.manager.data.redux.ActionContext
import app.revanced.manager.patcher.patch.PatchBundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FilterOutputStream
import java.io.IOException
import java.io.OutputStream

/**
 * A [PatchBundle] source.
 */
@Stable
sealed class PatchBundleSource(
    val name: String,
    val uid: Int,
    val displayName: String?,
    val createdAt: Long?,
    val updatedAt: Long?,
    error: Throwable?,
    protected val directory: File,
    val enabled: Boolean
) {
    protected val patchesFile = directory.resolve("patches.jar")
    internal val patchesJarFile: File get() = patchesFile

    val state = runCatching {
        when {
            error != null -> State.Failed(error)
            !hasInstalled() -> State.Missing
            else -> State.Available(PatchBundle(patchesFile.absolutePath))
        }
    }.getOrElse { throwable ->
        State.Failed(throwable)
    }

    val patchBundle get() = (state as? State.Available)?.bundle
    val version get() = patchBundle?.manifestAttributes?.version
    val isNameOutOfDate get() = patchBundle?.manifestAttributes?.name?.let { it != name } == true
    val error get() = (state as? State.Failed)?.throwable
    val displayTitle get() = displayName?.takeUnless { it.isBlank() } ?: name

    suspend fun ActionContext.deleteLocalFile() = withContext(Dispatchers.IO) {
        patchesFile.delete()
    }

    abstract fun copy(
        error: Throwable? = this.error,
        name: String = this.name,
        displayName: String? = this.displayName,
        createdAt: Long? = this.createdAt,
        updatedAt: Long? = this.updatedAt,
        enabled: Boolean = this.enabled
    ): PatchBundleSource

    protected fun hasInstalled() = patchesFile.exists()

    protected fun patchBundleOutputStream(): OutputStream = with(patchesFile) {
        // Android 14+ requires dex containers to be readonly.
        setWritable(true, true)
        val base = outputStream()
        object : FilterOutputStream(base) {
            override fun close() {
                try {
                    super.close()
                } finally {
                    setReadOnly()
                }
            }
        }
    }

    protected fun requireNonEmptyPatchesFile(context: String) {
        val length = runCatching { patchesFile.length() }.getOrDefault(0L)
        if (length < MIN_PATCH_BUNDLE_BYTES) {
            runCatching { patchesFile.delete() }
            throw IOException("$context produced an empty or truncated patch bundle (size=$length)")
        }
    }

    sealed interface State {
        data object Missing : State
        data class Failed(val throwable: Throwable) : State
        data class Available(val bundle: PatchBundle) : State
    }

    companion object Extensions {
        private const val MIN_PATCH_BUNDLE_BYTES = 8L
        val PatchBundleSource.isDefault inline get() = uid == 0
        val PatchBundleSource.asRemoteOrNull inline get() = this as? RemotePatchBundle
        val PatchBundleSource.actualName inline get() = name
    }
}
