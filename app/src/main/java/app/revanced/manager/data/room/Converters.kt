package app.revanced.manager.data.room

import androidx.room.TypeConverter
import app.revanced.manager.data.room.bundles.Source
import app.revanced.manager.data.room.options.Option.SerializedValue
import app.revanced.manager.data.room.profile.PatchProfilePayload
import java.io.File
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    companion object {
        private val profileJson = Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }
    }

    @TypeConverter
    fun sourceFromString(value: String) = Source.from(value)

    @TypeConverter
    fun sourceToString(value: Source) = value.toString()

    @TypeConverter
    fun fileFromString(value: String) = File(value)

    @TypeConverter
    fun fileToString(file: File): String = file.path

    @TypeConverter
    fun serializedOptionFromString(value: String) = SerializedValue.fromJsonString(value)

    @TypeConverter
    fun serializedOptionToString(value: SerializedValue) = value.toJsonString()

    @TypeConverter
    fun patchProfilePayloadFromString(value: String) =
        profileJson.decodeFromString<PatchProfilePayload>(value)

    @TypeConverter
    fun patchProfilePayloadToString(payload: PatchProfilePayload) =
        profileJson.encodeToString(payload)
}
