package org.inventivetalent.notificationlogger.database.converters

import androidx.room.TypeConverter

import org.json.JSONException
import org.json.JSONObject

class JsonConverter {


    @TypeConverter
    fun jsonFromString(string: String?): JSONObject? {
        if (string == null) return null
        return try {
            JSONObject(string)
        } catch (e: JSONException) {
            null
        }

    }

    @TypeConverter
    fun jsonToString(json: JSONObject?): String {
        return json?.toString() ?: "{}"
    }

}
