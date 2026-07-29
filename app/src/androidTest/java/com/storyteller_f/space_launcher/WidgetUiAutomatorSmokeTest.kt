package com.storyteller_f.space_launcher

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetUiAutomatorSmokeTest {

    @Test
    fun uiAutomatorCanInspectWidgetPanel() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val intent = Intent(context, LauncherActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val activity = instrumentation.startActivitySync(intent) as LauncherActivity

        try {
            assertTrue(
                "Launcher package did not become visible",
                device.wait(Until.hasObject(By.pkg(context.packageName).depth(0)), TIMEOUT_MS)
            )

            instrumentation.runOnMainSync {
                activity.openWidgetPanelForTest()
            }
            instrumentation.waitForIdleSync()

            val widgetMenu = device.wait(
                Until.findObject(By.desc("Widget 菜单")),
                TIMEOUT_MS
            )
            assertNotNull("Widget menu FAB was not visible to UiAutomator", widgetMenu)
        } finally {
            instrumentation.runOnMainSync {
                activity.finish()
            }
        }
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
