package app.revanced.manager.data.room.bundles

import androidx.room.*
import io.ktor.http.*

sealed class Source {
    object Local : Source() {
        const val SENTINEL = "local"

        override fun toString() = SENTINEL
    }

    object API : Source() {
        const val SENTINEL = "api"

        override fun toString() = SENTINEL
    }

    data class Remote(val url: Url) : Source() {
        override fun toString() = url.toString()
    }

    data class GitHubPullRequest(val url: Url) : Source() {
        override fun toString() = url.toString()
    }

    companion object {
        fun from(value: String) = when (value) {
            Local.SENTINEL -> Local
            API.SENTINEL -> API
            else ->
                if (value.matches(Regex("^https://github\\.com/([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+)/pull/(\\d+)/?\$")))
                    GitHubPullRequest(Url(value))
                else
                    Remote(Url(value))
        }
    }
}

@Entity(tableName = "patch_bundles")
data class PatchBundleEntity(
    @PrimaryKey val uid: Int,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "display_name") val displayName: String? = null,
    @ColumnInfo(name = "version") val versionHash: String? = null,
    @ColumnInfo(name = "source") val source: Source,
    @ColumnInfo(name = "auto_update") val autoUpdate: Boolean,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long? = null,
    @ColumnInfo(name = "updated_at") val updatedAt: Long? = null
)

data class PatchBundleProperties(
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "display_name") val displayName: String? = null,
    @ColumnInfo(name = "version") val versionHash: String? = null,
    @ColumnInfo(name = "source") val source: Source,
    @ColumnInfo(name = "auto_update") val autoUpdate: Boolean,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long? = null,
    @ColumnInfo(name = "updated_at") val updatedAt: Long? = null
)
