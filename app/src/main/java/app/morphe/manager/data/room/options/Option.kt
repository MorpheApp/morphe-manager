/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 *
 * Original hard forked code:
 * https://github.com/Jman-Github/Universal-ReVanced-Manager/blob/597b3173a004f5a9aae54326046dd7fd4c5b7777/app/src/main/java/app/revanced/manager/data/room/options/Option.kt
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.manager.data.room.options

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import app.morphe.manager.patcher.patch.Option
import app.morphe.manager.patcher.patch.coerceOptionValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlin.reflect.KClass

@Entity(
    tableName = "options",
    primaryKeys = ["group", "patch_name", "key"],
    foreignKeys = [ForeignKey(
        OptionGroup::class,
        parentColumns = ["uid"],
        childColumns = ["group"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class Option(
    @ColumnInfo(name = "group") val group: Int,
    @ColumnInfo(name = "patch_name") val patchName: String,
    @ColumnInfo(name = "key") val key: String,
    @ColumnInfo(name = "value") val value: SerializedValue // Encoded as JSON
) {
    @Serializable
    data class SerializedValue(val raw: JsonElement) {
        fun toJsonString() = json.encodeToString(raw)

        /** A stored value outlives its bundle, so it is read back as the type declared today. */
        fun deserializeFor(option: Option<*>): Any? {
            if (raw is JsonNull) return null

            return coerceOptionValue(option.type, raw.decode())
                ?: throw SerializationException("Cannot deserialize $raw as ${option.type}")
        }

        companion object {
            private val json = Json {
                // Patcher does not forbid the use of these values, so we should support them
                allowSpecialFloatingPointValues = true
            }

            /** The plain value behind a JSON element, or null for one we never store. */
            private fun JsonElement.decode(): Any? = when (this) {
                is JsonArray -> map { element -> element.decode() ?: return null }
                is JsonPrimitive ->
                    if (isString) content
                    else booleanOrNull ?: longOrNull ?: doubleOrNull

                else -> null
            }

            fun fromJsonString(value: String) = SerializedValue(json.decodeFromString(value))
            fun fromValue(value: Any?) = SerializedValue(when (value) {
                null -> JsonNull
                is Number -> JsonPrimitive(value)
                is Boolean -> JsonPrimitive(value)
                is String -> JsonPrimitive(value)
                is List<*> -> buildJsonArray {
                    var elementClass: KClass<out Any>? = null

                    value.forEach {
                        when (it) {
                            null -> throw SerializationException("List elements must not be null")
                            is Number -> add(it)
                            is Boolean -> add(it)
                            is String -> add(it)
                            else -> throw SerializationException("Unknown element type: ${it::class.simpleName}")
                        }

                        if (elementClass == null) elementClass = it::class
                        else if (elementClass != it::class) throw SerializationException("List elements must have the same type")
                    }
                }

                else -> throw SerializationException("Unknown type: ${value::class.simpleName}")
            })
        }
    }

    class SerializationException(message: String, cause: Throwable? = null) :
        Exception(message, cause)
}
