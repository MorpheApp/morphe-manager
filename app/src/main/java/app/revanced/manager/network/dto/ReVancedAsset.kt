package app.revanced.manager.network.dto

import kotlinx.datetime.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

// Custom serializer to handle ISO 8601 datetime strings (with or without timezone)
object LocalDateTimeSerializer : KSerializer<LocalDateTime> {
    override val descriptor = PrimitiveSerialDescriptor("LocalDateTime", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDateTime) {
        // Convert LocalDateTime to ISO 8601 UTC format
        encoder.encodeString(value.toInstant(TimeZone.UTC).toString())
    }

    override fun deserialize(decoder: Decoder): LocalDateTime {
        val string = decoder.decodeString()

        return try {
            // Convert to UTC LocalDateTime
            Instant.parse(string).toLocalDateTime(TimeZone.UTC)
        } catch (e: Exception) {
            try {
                // If that fails, parse as LocalDateTime (without timezone)
                LocalDateTime.parse(string)
            } catch (e2: Exception) {
                throw IllegalArgumentException("Cannot parse datetime string: $string", e2)
            }
        }
    }
}

@Serializable
data class ReVancedAsset (
    @SerialName("download_url")
    val downloadUrl: String,
    @SerialName("created_at")
    @Serializable(with = LocalDateTimeSerializer::class)
    val createdAt: LocalDateTime,
    @SerialName("signature_download_url")
    val signatureDownloadUrl: String? = null,
    @SerialName("page_url")
    val pageUrl: String? = null,
    val description: String,
    val version: String,
)
