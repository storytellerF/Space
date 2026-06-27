package com.storyteller_f.space_launcher.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.getSystemService

class PanelAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) {
            instance = null
        }
        super.onDestroy()
    }

    companion object {
        private var instance: PanelAccessibilityService? = null

        fun isConnected(): Boolean = instance != null

        fun openNotifications(): Boolean {
            return instance?.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS) == true
        }

        fun openQuickSettings(): Boolean {
            return instance?.swipeFromTopRight() == true
        }
    }

    private fun swipeFromTopRight(): Boolean {
        val bounds = getSystemService<WindowManager>()?.currentWindowMetrics?.bounds ?: return false
        val startX = bounds.width() * 0.85f
        val startY = 1f
        val endY = bounds.height() * 0.75f
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(startX, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 260L))
            .build()
        return dispatchGesture(gesture, null, null)
    }
}
