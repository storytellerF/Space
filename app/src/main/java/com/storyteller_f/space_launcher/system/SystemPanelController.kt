package com.storyteller_f.space_launcher.system

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import com.storyteller_f.space_launcher.accessibility.PanelAccessibilityService

class SystemPanelController(private val context: Context) {

    fun open(panel: SystemPanel): Boolean {
        val opened = when (panel) {
            SystemPanel.NOTIFICATIONS -> PanelAccessibilityService.openNotifications()
            SystemPanel.QUICK_SETTINGS -> PanelAccessibilityService.openQuickSettings()
        }

        if (opened) return true

        promptEnableAccessibilityService()
        return false
    }

    private fun promptEnableAccessibilityService() {
        val message = if (PanelAccessibilityService.isConnected()) {
            "无法打开系统面板"
        } else {
            "请开启 Space 无障碍服务，用于下滑打开系统面板"
        }
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()

        if (!PanelAccessibilityService.isConnected()) {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
    }
}

enum class SystemPanel {
    NOTIFICATIONS,
    QUICK_SETTINGS
}
