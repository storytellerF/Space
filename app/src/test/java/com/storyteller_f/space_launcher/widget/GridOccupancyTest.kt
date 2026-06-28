package com.storyteller_f.space_launcher.widget

import org.junit.Assert.*
import org.junit.Test

class GridOccupancyTest {

    // findVacantCell coordinate tests are in GridOccupancyInstrumentedTest (androidTest),
    // because android.graphics.Point is not available in JVM unit tests.

    @Test
    fun findVacantCell_fullyOccupied_returnsNull() {
        val grid = GridOccupancy(2, 2)
        grid.markCells(0, 0, 2, 2, true)
        assertNull(grid.findVacantCell(1, 1))
    }

    @Test
    fun findVacantCell_spanWiderThanGrid_returnsNull() {
        val grid = GridOccupancy(2, 2)
        assertNull(grid.findVacantCell(3, 1))
    }

    @Test
    fun findVacantCell_spanTallerThanGrid_returnsNull() {
        val grid = GridOccupancy(2, 2)
        assertNull(grid.findVacantCell(1, 3))
    }

    // ── isRegionVacant ──────────────────────────────────────────────────────

    @Test
    fun isRegionVacant_emptyGrid_returnsTrue() {
        val grid = GridOccupancy(4, 4)
        assertTrue(grid.isRegionVacant(0, 0, 4, 4))
    }

    @Test
    fun isRegionVacant_singleOccupiedCell_returnsFalseForOverlappingRegion() {
        val grid = GridOccupancy(4, 4)
        grid.markCells(2, 2, 1, 1, true)
        assertFalse(grid.isRegionVacant(0, 0, 4, 4))
        assertTrue(grid.isRegionVacant(0, 0, 2, 2))
    }

    @Test
    fun isRegionVacant_negativeX_returnsFalse() {
        val grid = GridOccupancy(4, 4)
        assertFalse(grid.isRegionVacant(-1, 0, 1, 1))
    }

    @Test
    fun isRegionVacant_negativeY_returnsFalse() {
        val grid = GridOccupancy(4, 4)
        assertFalse(grid.isRegionVacant(0, -1, 1, 1))
    }

    @Test
    fun isRegionVacant_spanExceedsRightEdge_returnsFalse() {
        val grid = GridOccupancy(4, 4)
        assertFalse(grid.isRegionVacant(3, 0, 2, 1))
    }

    @Test
    fun isRegionVacant_spanExceedsBottomEdge_returnsFalse() {
        val grid = GridOccupancy(4, 4)
        assertFalse(grid.isRegionVacant(0, 3, 1, 2))
    }

    // ── markCells ───────────────────────────────────────────────────────────

    @Test
    fun markCells_setsCorrectCells() {
        val grid = GridOccupancy(4, 4)
        grid.markCells(1, 1, 2, 2, true)
        assertTrue(grid.cells[1][1])
        assertTrue(grid.cells[2][1])
        assertTrue(grid.cells[1][2])
        assertTrue(grid.cells[2][2])
        assertFalse(grid.cells[0][0])
        assertFalse(grid.cells[3][3])
    }

    @Test
    fun markCells_unsetsCorrectCells() {
        val grid = GridOccupancy(4, 4)
        grid.markCells(0, 0, 4, 4, true)
        grid.markCells(1, 1, 2, 2, false)
        assertFalse(grid.cells[1][1])
        assertFalse(grid.cells[2][2])
        assertTrue(grid.cells[0][0])
        assertTrue(grid.cells[3][3])
    }

    @Test
    fun markCells_negativeOrigin_isIgnored() {
        val grid = GridOccupancy(4, 4)
        grid.markCells(-1, -1, 2, 2, true)
        for (x in 0 until 4) for (y in 0 until 4) assertFalse(grid.cells[x][y])
    }

    @Test
    fun markCells_exceedsBounds_clampsToGrid() {
        val grid = GridOccupancy(4, 4)
        grid.markCells(3, 3, 3, 3, true)
        assertTrue(grid.cells[3][3])
        assertFalse(grid.cells[2][3])
        assertFalse(grid.cells[3][2])
    }

    // ── clear ───────────────────────────────────────────────────────────────

    @Test
    fun clear_resetsAllCells() {
        val grid = GridOccupancy(4, 4)
        grid.markCells(0, 0, 4, 4, true)
        grid.clear()
        for (x in 0 until 4) for (y in 0 until 4) assertFalse(grid.cells[x][y])
    }

    // ── getMaxOccupiedY ─────────────────────────────────────────────────────

    @Test
    fun getMaxOccupiedY_emptyGrid_returnsMinusOne() {
        assertEquals(-1, GridOccupancy(4, 4).getMaxOccupiedY())
    }

    @Test
    fun getMaxOccupiedY_someOccupied_returnsMaxRow() {
        val grid = GridOccupancy(4, 4)
        grid.markCells(0, 0, 1, 3, true)
        assertEquals(2, grid.getMaxOccupiedY())
    }

    @Test
    fun getMaxOccupiedY_lastRowOccupied_returnsLastIndex() {
        val grid = GridOccupancy(4, 4)
        grid.markCells(2, 3, 1, 1, true)
        assertEquals(3, grid.getMaxOccupiedY())
    }
}
