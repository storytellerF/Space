package com.storyteller_f.space_launcher.widget

import org.junit.Assert.*
import org.junit.Test

class WidgetCellLayoutParamsTest {

    // ── single cell ─────────────────────────────────────────────────────────

    @Test
    fun setup_singleCell_noBorderSpace_sizeEqualsCellSize() {
        val params = WidgetCellLayoutParams(cellX = 0, cellY = 0, cellHSpan = 1, cellVSpan = 1)
        params.setup(cellWidth = 100, cellHeight = 150, borderSpaceX = 0, borderSpaceY = 0)
        assertEquals(100, params.width)
        assertEquals(150, params.height)
        assertEquals(0, params.pixelX)
        assertEquals(0, params.pixelY)
    }

    @Test
    fun setup_singleCell_withBorderSpace_pixelPositionIncludesGap() {
        val params = WidgetCellLayoutParams(cellX = 1, cellY = 2, cellHSpan = 1, cellVSpan = 1)
        params.setup(cellWidth = 100, cellHeight = 100, borderSpaceX = 10, borderSpaceY = 10)
        assertEquals(100, params.width)
        assertEquals(100, params.height)
        assertEquals(110, params.pixelX)   // 1 * (100 + 10)
        assertEquals(220, params.pixelY)   // 2 * (100 + 10)
    }

    // ── multi-cell span ─────────────────────────────────────────────────────

    @Test
    fun setup_hSpan2_widthIncludesOneBorderGap() {
        val params = WidgetCellLayoutParams(cellX = 0, cellY = 0, cellHSpan = 2, cellVSpan = 1)
        params.setup(cellWidth = 100, cellHeight = 100, borderSpaceX = 8, borderSpaceY = 8)
        // 2 * 100 + (2-1) * 8 = 208
        assertEquals(208, params.width)
        assertEquals(100, params.height)
    }

    @Test
    fun setup_span4x2_widthAndHeightIncludeCorrectGaps() {
        val params = WidgetCellLayoutParams(cellX = 0, cellY = 0, cellHSpan = 4, cellVSpan = 2)
        params.setup(cellWidth = 50, cellHeight = 80, borderSpaceX = 4, borderSpaceY = 6)
        // width  = 4 * 50 + (4-1) * 4 = 212
        // height = 2 * 80 + (2-1) * 6 = 166
        assertEquals(212, params.width)
        assertEquals(166, params.height)
    }

    // ── pixel position ───────────────────────────────────────────────────────

    @Test
    fun setup_nonZeroPosition_pixelXYAccountForBorderSpaceInOffset() {
        val params = WidgetCellLayoutParams(cellX = 2, cellY = 3, cellHSpan = 1, cellVSpan = 1)
        params.setup(cellWidth = 60, cellHeight = 60, borderSpaceX = 4, borderSpaceY = 4)
        // pixelX = 2 * (60 + 4) = 128
        // pixelY = 3 * (60 + 4) = 192
        assertEquals(128, params.pixelX)
        assertEquals(192, params.pixelY)
    }

    @Test
    fun setup_zeroBorderSpace_pixelPositionIsCellIndexTimesCellSize() {
        val params = WidgetCellLayoutParams(cellX = 3, cellY = 2, cellHSpan = 1, cellVSpan = 1)
        params.setup(cellWidth = 80, cellHeight = 80, borderSpaceX = 0, borderSpaceY = 0)
        assertEquals(240, params.pixelX)  // 3 * 80
        assertEquals(160, params.pixelY)  // 2 * 80
    }
}
