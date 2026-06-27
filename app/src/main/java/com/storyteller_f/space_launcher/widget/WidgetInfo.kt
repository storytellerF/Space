package com.storyteller_f.space_launcher.widget

import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistable info about a widget's position in the cell grid.
 */
data class WidgetInfo(
    val appWidgetId: Int,
    val cellX: Int,
    val cellY: Int,
    val spanX: Int,
    val spanY: Int
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", appWidgetId)
        put("cx", cellX)
        put("cy", cellY)
        put("sx", spanX)
        put("sy", spanY)
    }

    companion object {
        fun fromJson(json: JSONObject): WidgetInfo = WidgetInfo(
            appWidgetId = json.getInt("id"),
            cellX = json.getInt("cx"),
            cellY = json.getInt("cy"),
            spanX = json.getInt("sx"),
            spanY = json.getInt("sy")
        )

        fun listToJson(list: List<WidgetInfo>): String {
            val arr = JSONArray()
            list.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }

        fun listFromJson(jsonStr: String): List<WidgetInfo> {
            if (jsonStr.isBlank()) return emptyList()
            return try {
                val arr = JSONArray(jsonStr)
                (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}
