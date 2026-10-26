package br.com.bragasaude.data.local

import androidx.room.TypeConverter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Date

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }

    @TypeConverter
    fun fromStringList(value: String?): List<String>? {
        return value?.let { Json.decodeFromString(it) }
    }

    @TypeConverter
    fun toStringList(list: List<String>?): String? {
        return list?.let { Json.encodeToString(it) }
    }

    @TypeConverter
    fun fromStringMap(value: String?): Map<String, String>? {
        return value?.let { Json.decodeFromString(it) }
    }

    @TypeConverter
    fun toStringMap(map: Map<String, String>?): String? {
        return map?.let { Json.encodeToString(it) }
    }
}
