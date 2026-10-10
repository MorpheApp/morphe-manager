/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 *
 * Original hard forked code:
 * https://github.com/Jman-Github/Universal-ReVanced-Manager/blob/597b3173a004f5a9aae54326046dd7fd4c5b7777/app/src/main/java/app/revanced/manager/data/room/bundles/PatchBundleEntity.kt
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.manager.data.room.bundles

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
        private val gitHubPullRequestPattern =
            Regex("^https://github\\.com/([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+)/pull/(\\d+)/?$")

        fun from(value: String) = when (value) {
            Local.SENTINEL -> Local
            API.SENTINEL -> API
            else -> if (gitHubPullRequestPattern.matches(value)) {
                GitHubPullRequest(Url(value))
            } else Remote(Url(value))
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
    @ColumnInfo(name = "enabled") val enabled: Boolean = true,
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
    @ColumnInfo(name = "enabled") val enabled: Boolean = true,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long? = null,
    @ColumnInfo(name = "updated_at") val updatedAt: Long? = null
)
