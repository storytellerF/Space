package com.storyteller_f.space_launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.storyteller_f.space_launcher.ui.WidgetController
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetAddFlowUiAutomatorTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)

    @Before
    fun setUp() {
        assertTestWidgetsInstalled()
        context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun addAllTestWidgetsThroughLauncherFlow() {
        val activity = launchActivity()
        try {
            instrumentation.runOnMainSync {
                activity.openWidgetPanelForTest()
            }
            instrumentation.waitForIdleSync()
            waitForWidgetPanel()

            val widgets = listOf(
                TestWidget(label = "Space Test Simple Widget", query = "Simple"),
                TestWidget(label = "Space Test Small Widget", query = "Small"),
                TestWidget(label = "Space Test Large Widget", query = "Large"),
                TestWidget(label = "Space Test Resize Aware Widget", query = "Resize"),
                TestWidget(
                    label = "Space Test Configurable Widget",
                    query = "Configurable",
                    requiresConfiguration = true
                )
            )

            widgets.forEachIndexed { index, widget ->
                addWidget(widget)
                waitForWidgetCount(activity, expectedCount = index + 1)
            }
        } finally {
            instrumentation.runOnMainSync {
                activity.finish()
            }
        }
    }

    private fun launchActivity(): LauncherActivity {
        val intent = Intent(context, LauncherActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val activity = instrumentation.startActivitySync(intent) as LauncherActivity
        assertTrue(
            "Launcher package did not become visible",
            device.wait(Until.hasObject(By.pkg(context.packageName).depth(0)), TIMEOUT_MS)
        )
        return activity
    }

    private fun addWidget(widget: TestWidget) {
        clickWidgetMenu()
        clickText("添加 Widget")
        filterWidget(widget.query)
        clickText(widget.label)
        acceptBindDialogIfPresent()
        if (widget.requiresConfiguration) {
            clickConfigDone()
        }
        waitForWidgetPanel()
    }

    private fun clickWidgetMenu() {
        val menu = device.wait(Until.findObject(By.desc("Widget 菜单")), TIMEOUT_MS)
        assertNotNull("Widget menu FAB was not visible", menu)
        menu.click()
    }

    private fun filterWidget(label: String) {
        val search = device.wait(
            Until.findObject(By.res(context.packageName, "et_widget_search")),
            TIMEOUT_MS
        )
        assertNotNull("Widget picker search field was not visible", search)
        search.text = label
        device.waitForIdle()
    }

    private fun acceptBindDialogIfPresent() {
        val allowButton = device.wait(Until.findObject(By.res("android:id/button1")), TIMEOUT_MS)
        allowButton?.click()
        device.waitForIdle()
    }

    private fun clickConfigDone() {
        val done = device.wait(
            Until.findObject(By.res(TEST_WIDGETS_PACKAGE, "config_done")),
            CONFIG_TIMEOUT_MS
        )
        assertNotNull("Could not find configurable widget Done button", done)
        done.click()
        device.waitForIdle()
    }

    private fun clickText(text: String) {
        val objectByText = device.wait(Until.findObject(By.text(text)), TIMEOUT_MS)
        assertNotNull("Could not find text: $text", objectByText)
        objectByText.click()
        device.waitForIdle()
    }

    private fun waitForWidgetPanel() {
        assertTrue(
            "Widget panel did not become visible",
            device.wait(Until.hasObject(By.desc("Widget 菜单")), TIMEOUT_MS)
        )
    }

    private fun waitForWidgetCount(activity: LauncherActivity, expectedCount: Int) {
        repeat(30) {
            instrumentation.waitForIdleSync()
            val state = dumpState(activity, "wait-widget-count-$expectedCount-$it")
            if (
                state.contains("childCount=$expectedCount") &&
                state.contains("savedCount=$expectedCount") &&
                !state.contains("size=0x0")
            ) {
                return
            }
            Thread.sleep(100)
        }

        val finalState = dumpState(activity, "wait-widget-count-$expectedCount-final")
        assertTrue(finalState, finalState.contains("childCount=$expectedCount"))
        assertTrue(finalState, finalState.contains("savedCount=$expectedCount"))
        assertTrue(finalState, !finalState.contains("size=0x0"))
    }

    private fun dumpState(activity: LauncherActivity, reason: String): String {
        val state = arrayOf("")
        instrumentation.runOnMainSync {
            state[0] = activity.dumpWidgetDebugStateForTest(reason)
        }
        android.util.Log.i(WidgetController.DEBUG_TAG, state[0])
        return state[0]
    }

    private fun assertTestWidgetsInstalled() {
        try {
            context.packageManager.getPackageInfo(TEST_WIDGETS_PACKAGE, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            throw AssertionError(
                "Test widget provider package is not installed. Run :test-widgets:installDebug first.",
                e
            )
        }
    }

    private data class TestWidget(
        val label: String,
        val query: String,
        val requiresConfiguration: Boolean = false
    )

    private companion object {
        const val TEST_WIDGETS_PACKAGE = "com.storyteller_f.space_launcher.testwidgets"
        const val TIMEOUT_MS = 5_000L
        const val CONFIG_TIMEOUT_MS = 12_000L
    }
}
