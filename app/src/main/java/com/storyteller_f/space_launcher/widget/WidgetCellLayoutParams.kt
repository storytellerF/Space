package com.storyteller_f.space_launcher.widget

import android.view.ViewGroup

/**
 * Layout params that position a child within [WidgetCellLayout] using cell coordinates.
 *
 * Inspired by Launcher3's CellLayoutLayoutParams.
 */
class WidgetCellLayoutParams(
    var cellX: Int,
    var cellY: Int,
    var cellHSpan: Int,
    var cellVSpan: Int
) : ViewGroup.MarginLayoutParams(MATCH_PARENT, MATCH_PARENT) {

    /** Pixel position computed during layout */
    var pixelX: Int = 0
    var pixelY: Int = 0

    constructor(source: ViewGroup.LayoutParams) : this(0, 0, 1, 1) {
        width = source.width
        height = source.height
    }

    /**
     * Compute pixel-level x, y, width, height from cell coordinates.
     *
     * @param cellWidth  pixel width of one cell
     * @param cellHeight pixel height of one cell
     * @param borderSpaceX horizontal gap between cells (px)
     * @param borderSpaceY vertical gap between cells (px)
     */
    fun setup(cellWidth: Int, cellHeight: Int, borderSpaceX: Int, borderSpaceY: Int) {
        val hBorderSpacing = (cellHSpan - 1) * borderSpaceX
        val vBorderSpacing = (cellVSpan - 1) * borderSpaceY

        width = cellHSpan * cellWidth + hBorderSpacing
        height = cellVSpan * cellHeight + vBorderSpacing

        pixelX = cellX * (cellWidth + borderSpaceX)
        pixelY = cellY * (cellHeight + borderSpaceY)
    }

    override fun toString(): String = "CellLP($cellX, $cellY, span=$cellHSpan×$cellVSpan)"
}
