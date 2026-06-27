package com.storyteller_f.space_launcher.widget

import android.graphics.Point

/**
 * Tracks which cells in the grid are occupied.
 * Simplified from Launcher3's GridOccupancy.
 */
class GridOccupancy(val countX: Int, val countY: Int) {

    val cells = Array(countX) { BooleanArray(countY) }

    /**
     * Find the first vacant region that can fit [spanX] x [spanY].
     * Scans top-to-bottom, left-to-right.
     */
    fun findVacantCell(spanX: Int, spanY: Int): Point? {
        for (y in 0..(countY - spanY)) {
            for (x in 0..(countX - spanX)) {
                if (isRegionVacant(x, y, spanX, spanY)) {
                    return Point(x, y)
                }
            }
        }
        return null
    }

    fun isRegionVacant(x: Int, y: Int, spanX: Int, spanY: Int): Boolean {
        if (x < 0 || y < 0 || x + spanX > countX || y + spanY > countY) return false
        for (i in x until x + spanX) {
            for (j in y until y + spanY) {
                if (cells[i][j]) return false
            }
        }
        return true
    }

    fun markCells(cellX: Int, cellY: Int, spanX: Int, spanY: Int, value: Boolean) {
        if (cellX < 0 || cellY < 0) return
        for (x in cellX until minOf(cellX + spanX, countX)) {
            for (y in cellY until minOf(cellY + spanY, countY)) {
                cells[x][y] = value
            }
        }
    }

    fun clear() {
        for (x in 0 until countX) {
            for (y in 0 until countY) {
                cells[x][y] = false
            }
        }
    }

    fun getMaxOccupiedY(): Int {
        var maxY = -1
        for (x in 0 until countX) {
            for (y in 0 until countY) {
                if (cells[x][y]) {
                    maxY = maxOf(maxY, y)
                }
            }
        }
        return maxY
    }

    override fun toString(): String {
        val sb = StringBuilder("Grid ($countX x $countY):\n")
        for (y in 0 until countY) {
            for (x in 0 until countX) {
                sb.append(if (cells[x][y]) "1 " else "0 ")
            }
            sb.append('\n')
        }
        return sb.toString()
    }
}
