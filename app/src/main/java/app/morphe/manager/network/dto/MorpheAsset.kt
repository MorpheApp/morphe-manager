/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 *
 * Original hard forked code:
 * https://github.com/Jman-Github/Universal-ReVanced-Manager/blob/597b3173a004f5a9aae54326046dd7fd4c5b7777/app/src/main/java/app/revanced/manager/network/dto/ReVancedAsset.kt
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.manager.network.dto

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MorpheAsset (
    @SerialName("download_url")
    val downloadUrl: String,
    @SerialName("created_at")
    val createdAt: LocalDateTime,
    @SerialName("signature_download_url")
    val signatureDownloadUrl: String? = null,
    @SerialName("page_url")
    val pageUrl: String? = null,
    val description: String,
    val version: String,
)
