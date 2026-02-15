package app.morphe.manager.data.room

import androidx.room.TypeConverter
import app.morphe.manager.data.room.apps.installed.SelectionPayload
import app.morphe.manager.data.room.bundles.Source
import app.morphe.manager.data.room.options.Option.SerializedValue
import kotlinx.serialization.json.Json
import java.io.File

class Converters {
    companion object {
        private val json = Json {
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
    fun selectionPayloadFromString(value: String) =
        json.decodeFromString<SelectionPayload>(value)

    @TypeConverter
    fun selectionPayloadToString(payload: SelectionPayload) =
        json.encodeToString(payload)
}
