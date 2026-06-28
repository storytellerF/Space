package com.storyteller_f.space_launcher.widget

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for GridOccupancy.findVacantCell that need a real Android runtime
 * because the method returns android.graphics.Point, which is not available
 * in JVM unit tests.
 */
@RunWith(AndroidJUnit4::class)
class GridOccupancyInstrumentedTest {

    @Test
    fun findVacantCell_emptyGrid_returnsOrigin() {
        val grid = GridOccupancy(4, 4)
        val result = grid.findVacantCell(1, 1)
        assertNotNull(result)
        assertEquals(0, result!!.x)
        assertEquals(0, result.y)
    }

    @Test
    fun findVacantCell_scanOrder_isTopLeftFirst() {
        val grid = GridOccupancy(4, 4)
        grid.markCells(0, 0, 2, 2, true)
        val result = grid.findVacantCell(1, 1)
        assertNotNull(result)
        assertEquals(2, result!!.x)
        assertEquals(0, result.y)
    }

    @Test
    fun findVacantCell_partiallyOccupied_findsNextVacant() {
        val grid = GridOccupancy(4, 4)
        grid.markCells(0, 0, 3, 1, true)
        val result = grid.findVacantCell(1, 1)
        assertNotNull(result)
        assertEquals(3, result!!.x)
        assertEquals(0, result.y)
    }

    @Test
    fun findVacantCell_exactFitInLastSlot() {
        val grid = GridOccupancy(4, 4)
        grid.markCells(0, 0, 4, 3, true)
        grid.markCells(0, 3, 2, 1, true)
        val result = grid.findVacantCell(2, 1)
        assertNotNull(result)
        assertEquals(2, result!!.x)
        assertEquals(3, result.y)
    }
}
