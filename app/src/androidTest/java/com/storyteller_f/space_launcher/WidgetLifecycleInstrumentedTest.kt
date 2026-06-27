package com.storyteller_f.space_launcher

import android.content.Intent
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.storyteller_f.space_launcher.ui.WidgetController
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetLifecycleInstrumentedTest {

    @Test
    fun logWidgetRestoreStateAfterFirstOpen() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val intent = Intent(context, LauncherActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val activity = instrumentation.startActivitySync(intent) as LauncherActivity

        try {
            instrumentation.waitForIdleSync()
            logState(instrumentation, activity, "after-launch")

            instrumentation.runOnMainSync {
                activity.openWidgetPanelForTest()
            }
            instrumentation.waitForIdleSync()
            waitForWidgetRestoreToSettle(instrumentation, activity)

            val finalState = dumpState(instrumentation, activity, "after-first-open")
            Log.i(WidgetController.DEBUG_TAG, finalState)
            assertTrue(finalState, finalState.contains("measured=true"))
            assertRestoredWidgetsAreLaidOutWhenSavedDataExists(finalState)
        } finally {
            instrumentation.runOnMainSync {
                activity.finish()
            }
        }
    }

    private fun waitForWidgetRestoreToSettle(
        instrumentation: android.app.Instrumentation,
        activity: LauncherActivity
    ) {
        repeat(20) {
            instrumentation.waitForIdleSync()
            val state = dumpState(instrumentation, activity, "settle-$it")
            Log.i(WidgetController.DEBUG_TAG, state)
            if (state.contains("restoreJobActive=false") && state.contains("measured=true")) {
                return
            }
            Thread.sleep(100)
        }
    }

    private fun logState(
        instrumentation: android.app.Instrumentation,
        activity: LauncherActivity,
        reason: String
    ) {
        Log.i(WidgetController.DEBUG_TAG, dumpState(instrumentation, activity, reason))
    }

    private fun dumpState(
        instrumentation: android.app.Instrumentation,
        activity: LauncherActivity,
        reason: String
    ): String {
        val state = arrayOf("")
        instrumentation.runOnMainSync {
            state[0] = activity.dumpWidgetDebugStateForTest(reason)
        }
        return state[0]
    }

    private fun assertRestoredWidgetsAreLaidOutWhenSavedDataExists(state: String) {
        val savedCount = extractInt(state, "savedCount")
        if (savedCount <= 0 || !state.contains("missingInfoIds=[]")) return

        val childCount = extractInt(state, "childCount")
        assertTrue(state, childCount >= savedCount)
        assertTrue(state, !state.contains("size=0x0"))
    }

    private fun extractInt(state: String, key: String): Int {
        return Regex("""$key=(\d+)""")
            .find(state)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
            ?: 0
    }
}
