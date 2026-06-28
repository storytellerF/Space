package com.storyteller_f.space_launcher.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsRepositoryTest {

    private lateinit var context: Context
    private lateinit var repo: SettingsRepository

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        repo = SettingsRepository(context)
        repo.resetToDefaults()
        context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE).edit().clear().apply()
    }

    @After
    fun tearDown() {
        repo.resetToDefaults()
        context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE).edit().clear().apply()
    }

    // ── Drawer columns ───────────────────────────────────────────────────────

    @Test
    fun getDrawerColumns_portrait_returnsDefaultFour() {
        assertEquals(SettingsRepository.DEFAULT_DRAWER_COLUMNS, repo.getDrawerColumns(false))
    }

    @Test
    fun getDrawerColumns_landscape_returnsDefaultSix() {
        assertEquals(SettingsRepository.DEFAULT_DRAWER_COLUMNS_LAND, repo.getDrawerColumns(true))
    }

    @Test
    fun setDrawerColumns_portrait_persists() {
        repo.setDrawerColumns(5, false)
        assertEquals(5, repo.getDrawerColumns(false))
    }

    @Test
    fun setDrawerColumns_portrait_doesNotAffectLandscape() {
        repo.setDrawerColumns(5, false)
        assertEquals(SettingsRepository.DEFAULT_DRAWER_COLUMNS_LAND, repo.getDrawerColumns(true))
    }

    @Test
    fun setDrawerColumns_landscape_persists() {
        repo.setDrawerColumns(3, true)
        assertEquals(3, repo.getDrawerColumns(true))
    }

    @Test
    fun setDrawerColumns_landscape_doesNotAffectPortrait() {
        repo.setDrawerColumns(3, true)
        assertEquals(SettingsRepository.DEFAULT_DRAWER_COLUMNS, repo.getDrawerColumns(false))
    }

    // ── Widget columns ───────────────────────────────────────────────────────

    @Test
    fun setWidgetColumns_portrait_persists() {
        repo.setWidgetColumns(6, false)
        assertEquals(6, repo.getWidgetColumns(false))
    }

    @Test
    fun setWidgetColumns_portrait_doesNotAffectLandscape() {
        repo.setWidgetColumns(6, false)
        assertEquals(SettingsRepository.DEFAULT_WIDGET_COLUMNS_LAND, repo.getWidgetColumns(true))
    }

    @Test
    fun setWidgetColumns_landscape_persists() {
        repo.setWidgetColumns(5, true)
        assertEquals(5, repo.getWidgetColumns(true))
    }

    @Test
    fun setWidgetColumns_landscape_doesNotAffectPortrait() {
        repo.setWidgetColumns(5, true)
        assertEquals(SettingsRepository.DEFAULT_WIDGET_COLUMNS, repo.getWidgetColumns(false))
    }

    // ── getCurrentLayoutKey ──────────────────────────────────────────────────

    @Test
    fun getCurrentLayoutKey_portrait_endsWithPort() {
        assertTrue(repo.getCurrentLayoutKey(false).endsWith("_port"))
    }

    @Test
    fun getCurrentLayoutKey_landscape_endsWithLand() {
        assertTrue(repo.getCurrentLayoutKey(true).endsWith("_land"))
    }

    @Test
    fun getCurrentLayoutKey_reflectsCurrentWidgetColumns() {
        repo.setWidgetColumns(5, false)
        assertEquals("layout_5_port", repo.getCurrentLayoutKey(false))
    }

    // ── Widget data persistence ──────────────────────────────────────────────

    @Test
    fun saveAndGetWidgetData_portrait_roundtrip() {
        val json = """[{"id":1,"cx":0,"cy":0,"sx":2,"sy":2}]"""
        repo.saveWidgetDataForLayout(json, false)
        assertEquals(json, repo.getWidgetDataForLayout(false))
    }

    @Test
    fun saveAndGetWidgetData_landscape_roundtrip() {
        val json = """[{"id":2,"cx":1,"cy":0,"sx":1,"sy":1}]"""
        repo.saveWidgetDataForLayout(json, true)
        assertEquals(json, repo.getWidgetDataForLayout(true))
    }

    @Test
    fun saveWidgetData_portraitAndLandscape_storedSeparately() {
        val portrait = """[{"id":1,"cx":0,"cy":0,"sx":2,"sy":2}]"""
        val landscape = """[{"id":2,"cx":0,"cy":0,"sx":3,"sy":1}]"""
        repo.saveWidgetDataForLayout(portrait, false)
        repo.saveWidgetDataForLayout(landscape, true)
        assertEquals(portrait, repo.getWidgetDataForLayout(false))
        assertEquals(landscape, repo.getWidgetDataForLayout(true))
    }

    @Test
    fun clearWidgetData_removesData() {
        repo.saveWidgetDataForLayout("""[{"id":1,"cx":0,"cy":0,"sx":1,"sy":1}]""", false)
        repo.clearWidgetDataForLayout(false)
        assertEquals("", repo.getWidgetDataForLayout(false))
    }

    @Test
    fun getWidgetDataForColumns_readsSpecificColumnSlot() {
        repo.setWidgetColumns(5, false)
        val json = """[{"id":1,"cx":0,"cy":0,"sx":2,"sy":2}]"""
        repo.saveWidgetDataForLayout(json, false) // saved under layout_5_port
        assertEquals(json, repo.getWidgetDataForColumns(5, false))
        assertEquals("", repo.getWidgetDataForColumns(4, false))
    }

    // ── resetToDefaults ──────────────────────────────────────────────────────

    @Test
    fun resetToDefaults_restoresAllFourColumnSettings() {
        repo.setDrawerColumns(6, false)
        repo.setDrawerColumns(3, true)
        repo.setWidgetColumns(6, false)
        repo.setWidgetColumns(3, true)
        repo.resetToDefaults()
        assertEquals(SettingsRepository.DEFAULT_DRAWER_COLUMNS, repo.getDrawerColumns(false))
        assertEquals(SettingsRepository.DEFAULT_DRAWER_COLUMNS_LAND, repo.getDrawerColumns(true))
        assertEquals(SettingsRepository.DEFAULT_WIDGET_COLUMNS, repo.getWidgetColumns(false))
        assertEquals(SettingsRepository.DEFAULT_WIDGET_COLUMNS_LAND, repo.getWidgetColumns(true))
    }

    // ── Debug switches ───────────────────────────────────────────────────────

    @Test
    fun debugSwitches_defaultFalse() {
        assertFalse(repo.isShowCellBackground())
        assertFalse(repo.isShowGridLines())
    }

    @Test
    fun setShowCellBackground_togglesPersists() {
        repo.setShowCellBackground(true)
        assertTrue(repo.isShowCellBackground())
        repo.setShowCellBackground(false)
        assertFalse(repo.isShowCellBackground())
    }

    @Test
    fun setShowGridLines_togglesPersists() {
        repo.setShowGridLines(true)
        assertTrue(repo.isShowGridLines())
        repo.setShowGridLines(false)
        assertFalse(repo.isShowGridLines())
    }
}
