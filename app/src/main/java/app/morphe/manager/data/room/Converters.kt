/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 *
 * Original hard forked code:
 * https://github.com/Jman-Github/Universal-ReVanced-Manager/blob/597b3173a004f5a9aae54326046dd7fd4c5b7777/app/src/main/java/app/revanced/manager/data/room/Converters.kt
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

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
