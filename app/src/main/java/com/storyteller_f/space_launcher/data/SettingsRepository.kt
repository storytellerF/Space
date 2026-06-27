package com.storyteller_f.space_launcher.data

import android.content.Context
import android.content.SharedPreferences

class SettingsRepository(context: Context) {

    companion object {
        private const val PREFS_NAME = "launcher_settings"
        // 竖屏 key（保持向前兼容，原有数据继续读取）
        private const val KEY_DRAWER_COLUMNS = "drawer_columns"
        private const val KEY_WIDGET_COLUMNS = "widget_columns"
        // 横屏专用 key
        private const val KEY_DRAWER_COLUMNS_LAND = "drawer_columns_land"
        private const val KEY_WIDGET_COLUMNS_LAND = "widget_columns_land"
        private const val KEY_SHOW_CELL_BACKGROUND = "show_cell_background"
        private const val KEY_SHOW_GRID_LINES = "show_grid_lines"

        const val DEFAULT_DRAWER_COLUMNS = 4
        const val DEFAULT_WIDGET_COLUMNS = 4
        const val DEFAULT_DRAWER_COLUMNS_LAND = 6
        const val DEFAULT_WIDGET_COLUMNS_LAND = 6
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val widgetPrefs: SharedPreferences = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)

    // ── Drawer Columns ──────────────────────────────────────────────────────

    fun getDrawerColumns(isLandscape: Boolean = false): Int {
        return if (isLandscape) {
            prefs.getInt(KEY_DRAWER_COLUMNS_LAND, DEFAULT_DRAWER_COLUMNS_LAND)
        } else {
            prefs.getInt(KEY_DRAWER_COLUMNS, DEFAULT_DRAWER_COLUMNS)
        }
    }

    fun setDrawerColumns(columns: Int, isLandscape: Boolean = false) {
        val key = if (isLandscape) KEY_DRAWER_COLUMNS_LAND else KEY_DRAWER_COLUMNS
        prefs.edit().putInt(key, columns).apply()
    }

    // ── Widget Columns ───────────────────────────────────────────────────────

    fun getWidgetColumns(isLandscape: Boolean = false): Int {
        return if (isLandscape) {
            prefs.getInt(KEY_WIDGET_COLUMNS_LAND, DEFAULT_WIDGET_COLUMNS_LAND)
        } else {
            prefs.getInt(KEY_WIDGET_COLUMNS, DEFAULT_WIDGET_COLUMNS)
        }
    }

    fun setWidgetColumns(columns: Int, isLandscape: Boolean = false) {
        val key = if (isLandscape) KEY_WIDGET_COLUMNS_LAND else KEY_WIDGET_COLUMNS
        prefs.edit().putInt(key, columns).apply()
    }

    // ── Debug Switches ───────────────────────────────────────────────────────

    fun isShowCellBackground(): Boolean {
        return prefs.getBoolean(KEY_SHOW_CELL_BACKGROUND, false)
    }

    fun setShowCellBackground(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_CELL_BACKGROUND, enabled).apply()
    }

    fun isShowGridLines(): Boolean {
        return prefs.getBoolean(KEY_SHOW_GRID_LINES, false)
    }

    fun setShowGridLines(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_GRID_LINES, enabled).apply()
    }

    // ── Widget Layout Data ───────────────────────────────────────────────────

    /**
     * 获取当前列数的唯一标识符（含方向区分）
     */
    fun getCurrentLayoutKey(isLandscape: Boolean = false): String {
        val columns = getWidgetColumns(isLandscape)
        val suffix = if (isLandscape) "_land" else "_port"
        return "layout_${columns}${suffix}"
    }

    /**
     * 保存指定列数下的 widget 数据
     */
    fun saveWidgetDataForLayout(widgetInfosJson: String, isLandscape: Boolean = false) {
        val layoutKey = getCurrentLayoutKey(isLandscape)
        widgetPrefs.edit()
            .putString(layoutKey, widgetInfosJson)
            .apply()
    }

    /**
     * 获取指定列数下的 widget 数据
     */
    fun getWidgetDataForLayout(isLandscape: Boolean = false): String {
        val layoutKey = getCurrentLayoutKey(isLandscape)
        return widgetPrefs.getString(layoutKey, "") ?: ""
    }

    /**
     * 根据指定列数和方向获取 widget 数据
     */
    fun getWidgetDataForColumns(columns: Int, isLandscape: Boolean = false): String {
        val suffix = if (isLandscape) "_land" else "_port"
        val layoutKey = "layout_${columns}${suffix}"
        return widgetPrefs.getString(layoutKey, "") ?: ""
    }

    /**
     * 清除当前列数下的 widget 数据
     */
    fun clearWidgetDataForLayout(isLandscape: Boolean = false) {
        val layoutKey = getCurrentLayoutKey(isLandscape)
        widgetPrefs.edit()
            .remove(layoutKey)
            .apply()
    }

    // ── Reset ────────────────────────────────────────────────────────────────

    /**
     * 恢复所有设置为默认值
     */
    fun resetToDefaults() {
        prefs.edit()
            .putInt(KEY_DRAWER_COLUMNS, DEFAULT_DRAWER_COLUMNS)
            .putInt(KEY_WIDGET_COLUMNS, DEFAULT_WIDGET_COLUMNS)
            .putInt(KEY_DRAWER_COLUMNS_LAND, DEFAULT_DRAWER_COLUMNS_LAND)
            .putInt(KEY_WIDGET_COLUMNS_LAND, DEFAULT_WIDGET_COLUMNS_LAND)
            .apply()
    }
}