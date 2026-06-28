package com.storyteller_f.space_launcher.widget

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for WidgetInfo JSON serialization. Runs as an instrumented test because
 * org.json.JSONObject/JSONArray are Android-only and not available in JVM unit tests.
 */
@RunWith(AndroidJUnit4::class)
class WidgetInfoInstrumentedTest {

    private val sample = WidgetInfo(appWidgetId = 42, cellX = 1, cellY = 2, spanX = 3, spanY = 4)

    // ── toJson ──────────────────────────────────────────────────────────────

    @Test
    fun toJson_mapsAllFieldsCorrectly() {
        val json = sample.toJson()
        assertEquals(42, json.getInt("id"))
        assertEquals(1, json.getInt("cx"))
        assertEquals(2, json.getInt("cy"))
        assertEquals(3, json.getInt("sx"))
        assertEquals(4, json.getInt("sy"))
    }

    // ── fromJson / toJson roundtrip ──────────────────────────────────────

    @Test
    fun fromJson_toJson_roundtrip() {
        assertEquals(sample, WidgetInfo.fromJson(sample.toJson()))
    }

    @Test
    fun fromJson_toJson_roundtrip_zeroPosition() {
        val info = WidgetInfo(appWidgetId = 0, cellX = 0, cellY = 0, spanX = 1, spanY = 1)
        assertEquals(info, WidgetInfo.fromJson(info.toJson()))
    }

    // ── listToJson / listFromJson ────────────────────────────────────────

    @Test
    fun listToJson_listFromJson_multipleItems_roundtrip() {
        val list = listOf(
            WidgetInfo(1, 0, 0, 2, 2),
            WidgetInfo(2, 2, 0, 1, 1),
            WidgetInfo(3, 0, 2, 4, 1)
        )
        assertEquals(list, WidgetInfo.listFromJson(WidgetInfo.listToJson(list)))
    }

    @Test
    fun listToJson_singleItem_roundtrip() {
        val list = listOf(sample)
        val result = WidgetInfo.listFromJson(WidgetInfo.listToJson(list))
        assertEquals(1, result.size)
        assertEquals(sample, result[0])
    }

    @Test
    fun listToJson_emptyList_producesEmptyJsonArray() {
        assertEquals("[]", WidgetInfo.listToJson(emptyList()))
    }

    // ── listFromJson edge cases ──────────────────────────────────────────

    @Test
    fun listFromJson_emptyString_returnsEmptyList() {
        assertEquals(emptyList<WidgetInfo>(), WidgetInfo.listFromJson(""))
    }

    @Test
    fun listFromJson_blankString_returnsEmptyList() {
        assertEquals(emptyList<WidgetInfo>(), WidgetInfo.listFromJson("   "))
    }

    @Test
    fun listFromJson_invalidJson_returnsEmptyList() {
        assertEquals(emptyList<WidgetInfo>(), WidgetInfo.listFromJson("{not valid json}"))
    }

    @Test
    fun listFromJson_emptyJsonArray_returnsEmptyList() {
        assertEquals(emptyList<WidgetInfo>(), WidgetInfo.listFromJson("[]"))
    }
}
