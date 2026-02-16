package app.morphe.manager.domain.bundles

import androidx.compose.runtime.Stable
import app.morphe.manager.patcher.patch.PatchBundle
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

        /**
         * Get GitHub avatar URL if this bundle is from a GitHub repository
         */
        val PatchBundleSource.githubAvatarUrl: String? get() {
            val remote = this as? RemotePatchBundle ?: return null
            return extractGitHubOwner(remote.endpoint)?.let { owner ->
                "https://github.com/$owner.png"
            }
        }

        /**
         * Extract GitHub owner/organization name from endpoint URL
         */
        private fun extractGitHubOwner(endpoint: String): String? {
            return try {
                val uri = java.net.URI(endpoint)
                val host = uri.host?.lowercase(java.util.Locale.US) ?: return null
                val segments = uri.path?.trim('/')?.split('/')?.filter { it.isNotBlank() } ?: return null

                when {
                    // raw.githubusercontent.com/owner/repo/...
                    host == "raw.githubusercontent.com" && segments.isNotEmpty() -> segments[0]

                    // github.com/owner/repo/...
                    host == "github.com" && segments.isNotEmpty() -> segments[0]

                    else -> null
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}
