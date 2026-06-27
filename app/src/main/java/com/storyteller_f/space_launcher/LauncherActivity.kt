package com.storyteller_f.space_launcher

import android.content.res.Configuration
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.PopupWindow
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import com.storyteller_f.space_launcher.system.SystemPanel
import com.storyteller_f.space_launcher.system.SystemPanelController
import com.storyteller_f.space_launcher.ui.AppDrawerController
import com.storyteller_f.space_launcher.ui.SettingsBottomSheetFragment
import com.storyteller_f.space_launcher.ui.WidgetController
import kotlin.math.abs

class LauncherActivity : FragmentActivity() {

    private lateinit var drawerContainer: FrameLayout
    private lateinit var widgetContainer: FrameLayout
    private lateinit var mainContainer: FrameLayout
    private lateinit var appDrawerController: AppDrawerController
    private lateinit var widgetController: WidgetController
    private lateinit var systemPanelController: SystemPanelController
    private var panelState: PanelState = PanelState.Closed
        set(value) {
            if (DEBUG_GESTURE_LOGS && field != value) {
                Log.i(TAG, "panel state: ${field.toLogString()} -> ${value.toLogString()}")
            }
            field = value
        }
    private var startY = 0f
    private var startX = 0f
    private var isVerticalDragging = false
    private var isHorizontalDragging = false
    private var isSystemPanelSwipe = false
    private val minFlingVelocity by lazy { ViewConfiguration.get(this).scaledMinimumFlingVelocity }
    private val touchSlop by lazy { ViewConfiguration.get(this).scaledTouchSlop }
    private val systemPanelSwipeThreshold by lazy { 48f * resources.displayMetrics.density }
    private var velocityTracker: android.view.VelocityTracker? = null
    private var dragStartFromOpen = false
    private var widgetDragStartFromOpen = false
    // 记录抽屉打开时向下拖拽开始时 RecyclerView 是否还能向上滚动
    private var drawerRvCanScrollUp = false
    private var drawerTouchStartedInsideRecyclerView = false
    private var statusBarLightBeforeDrawer: Boolean? = null
    private var drawerCoversStatusBar = false


    var isWidgetDragging = false

    // Long press detection
    private var longPressRunnable: Runnable? = null
    private val LONG_PRESS_TIMEOUT = 500L
    private var popupWindow: PopupWindow? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Don't use enableEdgeToEdge() as it overrides the transparent window background
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        setContentView(R.layout.activity_launcher)
        // Ensure DecorView background doesn't block wallpaper
        window.decorView.setBackgroundColor(android.graphics.Color.TRANSPARENT)

        drawerContainer = findViewById(R.id.drawer_container)
        widgetContainer = findViewById(R.id.widget_container)
        mainContainer = findViewById(R.id.main)
        ViewCompat.requestApplyInsets(mainContainer)
        appDrawerController = AppDrawerController(this, drawerContainer)
        widgetController = WidgetController(this, widgetContainer)
        systemPanelController = SystemPanelController(this)
        widgetController.onCreate(savedInstanceState)
        appDrawerController.attach()
        widgetController.attach()

        drawerContainer.post {
            drawerContainer.translationY = drawerContainer.height.toFloat()
            if (panelState.panelOrNull() == Panel.DRAWER) {
                panelState = PanelState.Closed
            }
        }
        widgetContainer.post {
            widgetContainer.translationX = -widgetContainer.width.toFloat()
            if (panelState.panelOrNull() == Panel.WIDGET) {
                panelState = PanelState.Closed
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (popupWindow?.isShowing == true) {
                    dismissPopupWindow()
                } else if (widgetController.isWidgetEditMode()) {
                    widgetController.exitWidgetEditMode()
                } else if (panelState.isOpened(Panel.WIDGET)) {
                    closeWidgetPanel()
                } else if (panelState.isOpened(Panel.DRAWER)) {
                    closeDrawer()
                }
            }
        })
    }

    override fun onStart() {
        super.onStart()
        widgetController.onStart()
    }
    
    override fun onResume() {
        super.onResume()
        // Reset drag state if returning from another activity
        isWidgetDragging = false
        widgetController.onResume()
    }

    override fun onStop() {
        widgetController.onStop()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        widgetController.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        widgetController.detach()
        appDrawerController.detach()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        println("onActivityResult: $requestCode, $resultCode, $data")
        widgetController.onActivityResult(requestCode, resultCode, data)
    }
    
    fun closeDrawer() {
        animatePanel(
            Panel.DRAWER,
            drawerContainer.height.toFloat(),
            PanelState.FlingToClose(Panel.DRAWER),
            PanelState.Closed
        )
        Log.i(TAG, "closeDrawer: height: ${drawerContainer.height}")
    }

    private fun animatePanel(panel: Panel, targetTranslation: Float, mid: PanelState, end: PanelState) {
        if (panel == Panel.WIDGET && targetTranslation == 0f) {
            widgetController.ensureWidgetsRestored()
        }
        panelState = mid
        val animator = when (panel) {
            Panel.DRAWER -> drawerContainer.animate().translationY(targetTranslation)
            Panel.WIDGET -> widgetContainer.animate().translationX(targetTranslation)
        }
        animator
            .setDuration(300)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .setUpdateListener {
                if (panel == Panel.DRAWER) {
                    updateAppPanelStatusBarAppearanceForPosition()
                }
            }
            .withEndAction {
                panelState = end
                if (panel == Panel.DRAWER) {
                    updateAppPanelStatusBarAppearanceForPosition()
                }
            }
            .start()
    }

    private fun applyAppPanelStatusBarAppearance() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (statusBarLightBeforeDrawer == null) {
            statusBarLightBeforeDrawer = controller.isAppearanceLightStatusBars
        }
        controller.isAppearanceLightStatusBars = shouldUseLightStatusBarForAppPanel()
    }

    private fun restoreStatusBarAppearanceAfterDrawer() {
        val previous = statusBarLightBeforeDrawer ?: return
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = previous
        statusBarLightBeforeDrawer = null
    }

    private fun updateAppPanelStatusBarAppearanceForPosition() {
        val coversStatusBar = drawerContainer.translationY <= getStatusBarTopInset().toFloat()
        if (coversStatusBar == drawerCoversStatusBar) return

        drawerCoversStatusBar = coversStatusBar
        if (coversStatusBar) {
            applyAppPanelStatusBarAppearance()
        } else {
            restoreStatusBarAppearanceAfterDrawer()
        }
    }

    private fun getStatusBarTopInset(): Int {
        val insets = ViewCompat.getRootWindowInsets(mainContainer) ?: return 0
        return insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
    }

    private fun shouldUseLightStatusBarForAppPanel(): Boolean {
        val nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightMode != Configuration.UI_MODE_NIGHT_YES
    }

    fun closeWidgetPanel() {
        animatePanel(
            Panel.WIDGET,
            -widgetContainer.width.toFloat(),
            PanelState.FlingToClose(Panel.WIDGET),
            PanelState.Closed
        )
        Log.i(TAG, "closeWidget: width: ${widgetContainer.width}")
    }

    fun openWidgetPanelForTest() {
        widgetContainer.animate().cancel()
        widgetController.ensureWidgetsRestored()
        widgetContainer.translationX = 0f
        panelState = PanelState.Opened(Panel.WIDGET)
    }

    fun dumpWidgetDebugStateForTest(reason: String): String {
        return widgetController.dumpWidgetDebugState(reason)
    }

    private fun settleTransientPanelState() {
        val panel = panelState.panelOrNull() ?: return
        if (panelState is PanelState.Opened) return

        when (panel) {
            Panel.DRAWER -> {
                val height = drawerContainer.height.toFloat()
                if (height <= 0f) return

                val currentY = drawerContainer.translationY.coerceIn(0f, height)
                val shouldOpen = currentY < height / 2f
                drawerContainer.translationY = if (shouldOpen) 0f else height
                updateAppPanelStatusBarAppearanceForPosition()
                panelState = if (shouldOpen) {
                    PanelState.Opened(Panel.DRAWER)
                } else {
                    PanelState.Closed
                }
            }
            Panel.WIDGET -> {
                val width = widgetContainer.width.toFloat()
                if (width <= 0f) return

                val currentX = widgetContainer.translationX.coerceIn(-width, 0f)
                val shouldOpen = currentX > -width / 2f
                widgetContainer.translationX = if (shouldOpen) 0f else -width
                panelState = if (shouldOpen) {
                    PanelState.Opened(Panel.WIDGET)
                } else {
                    PanelState.Closed
                }
            }
        }
    }

    private fun showPopupMenu(x: Float, y: Float) {
        dismissPopupWindow()

        val popupView = layoutInflater.inflate(R.layout.popup_menu_layout, null)
        popupWindow = PopupWindow(popupView, FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, true).apply {
            isFocusable = true
            isOutsideTouchable = true
            elevation = 8f

            popupView.findViewById<View>(R.id.btn_wallpaper_settings).setOnClickListener {
                dismissPopupWindow()
                openWallpaperSettings()
            }

            popupView.findViewById<View>(R.id.btn_system_settings).setOnClickListener {
                dismissPopupWindow()
                openSystemSettings()
            }

            popupView.findViewById<View>(R.id.btn_space_settings).setOnClickListener {
                dismissPopupWindow()
                openSpaceSettings()
            }
        }

        popupWindow?.showAtLocation(mainContainer, Gravity.NO_GRAVITY, x.toInt(), y.toInt())
        Log.i(TAG, "PopupMenu shown at: x=$x, y=$y")
    }

    private fun dismissPopupWindow() {
        popupWindow?.dismiss()
        popupWindow = null
    }

    private fun openWallpaperSettings() {
        try {
            val intent = Intent(Intent.ACTION_SET_WALLPAPER)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开壁纸设置", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Failed to open wallpaper settings", e)
        }
    }

    private fun openSystemSettings() {
        try {
            val intent = Intent(Settings.ACTION_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开系统设置", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Failed to open system settings", e)
        }
    }

    private fun openSpaceSettings() {
        try {
            val settingsFragment = SettingsBottomSheetFragment()
            settingsFragment.show(supportFragmentManager, "settings_bottom_sheet")
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开Space设置", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Failed to open space settings", e)
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (DEBUG_GESTURE_LOGS) {
            Log.d(TAG, "dispatchTouchEvent() called with: ev = $ev")
        }

        if (isWidgetDragging) {
            return super.dispatchTouchEvent(ev)
        }

        if (velocityTracker == null) {
            velocityTracker = android.view.VelocityTracker.obtain()
        }
        velocityTracker?.addMovement(ev)

        val screenHeight = drawerContainer.height.toFloat()
        val screenWidth = widgetContainer.width.toFloat()

        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = ev.rawX
                startY = ev.rawY
                isVerticalDragging = false
                isHorizontalDragging = false
                isSystemPanelSwipe = false
                drawerContainer.animate().cancel()
                widgetContainer.animate().cancel()
                settleTransientPanelState()

                // 记录手指按下时 RecyclerView 是否还能向上滚动（有内容在顶部以上）
                if (panelState.isOpened(Panel.DRAWER)) {
                    val drawerRv = getDrawerRecyclerView()
                    drawerRvCanScrollUp = drawerRv?.canScrollVertically(-1) ?: false
                    drawerTouchStartedInsideRecyclerView = drawerRv?.isPointInside(ev.rawX, ev.rawY) == true
                } else {
                    drawerTouchStartedInsideRecyclerView = false
                }

                // Only detect long press when drawer and widget are closed
                if (panelState.isIdleClosed()) {
                    longPressRunnable = Runnable {
                        showPopupMenu(ev.rawX, ev.rawY)
                    }
                    mainContainer.postDelayed(longPressRunnable, LONG_PRESS_TIMEOUT)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = ev.rawY - startY
                val dx = ev.rawX - startX

                if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                    widgetController.cancelPendingWidgetMoveModeForExternalDrag()
                }

                // Cancel long press if moved too much
                if (abs(dx) > 10 || abs(dy) > 10) {
                    longPressRunnable?.let {
                        mainContainer.removeCallbacks(it)
                        longPressRunnable = null
                    }
                }

                // If not yet dragging, check if we should start
                if (!isHorizontalDragging && !isVerticalDragging && !isSystemPanelSwipe) {
                    // Check if vertical movement dominates horizontal
                    if (abs(dy) > abs(dx)) {
                        // Swipe down on the desktop: left half opens notifications, right half opens quick settings.
                        if (panelState.isIdleClosed() && dy > systemPanelSwipeThreshold) {
                            isSystemPanelSwipe = true
                            val panel = if (startX < screenWidth / 2f) {
                                SystemPanel.NOTIFICATIONS
                            } else {
                                SystemPanel.QUICK_SETTINGS
                            }
                            systemPanelController.open(panel)
                            velocityTracker?.clear()
                            return true
                        }
                        // Swipe Up to open drawer — only when Widget panel is also closed
                        if (panelState.isIdleClosed() && dy < -touchSlop) {
                            isVerticalDragging = true
                            dragStartFromOpen = false
                            panelState = PanelState.Dragging(Panel.DRAWER)
                        }
                        // Swipe Down to close drawer — only when RecyclerView is already at the top
                        if (panelState.isOpened(Panel.DRAWER) && dy > touchSlop) {
                            val drawerRv = getDrawerRecyclerView()
                            val rvAtTop = drawerRv?.canScrollVertically(-1) == false
                            val rvSettling = drawerRv?.scrollState == RecyclerView.SCROLL_STATE_SETTLING
                            // 如果按下时 RV 就在顶部，或者现在已经滚到顶部，才接管关闭手势
                            val canTakeOverDrawerGesture = if (drawerTouchStartedInsideRecyclerView) {
                                (!drawerRvCanScrollUp || rvAtTop) && !rvSettling
                            } else {
                                true
                            }
                            if (canTakeOverDrawerGesture) {
                                isVerticalDragging = true
                                dragStartFromOpen = true
                                panelState = PanelState.Dragging(Panel.DRAWER)
                            }
                        }
                    } else if (abs(dx) > abs(dy)) {
                        if (panelState.isIdleClosed() && dx > touchSlop) {
                            isHorizontalDragging = true
                            widgetDragStartFromOpen = false
                            widgetController.ensureWidgetsRestored()
                            panelState = PanelState.Dragging(Panel.WIDGET)
                        }
                        if (panelState.isOpened(Panel.WIDGET) && dx < -touchSlop) {
                            isHorizontalDragging = true
                            widgetDragStartFromOpen = true
                            panelState = PanelState.Dragging(Panel.WIDGET)
                        }
                    }
                }

                if (isVerticalDragging) {
                    var targetTransY = if (dragStartFromOpen) dy else screenHeight + dy
                    // Clamp
                    if (targetTransY < 0) targetTransY = 0f
                    if (targetTransY > screenHeight) targetTransY = screenHeight

                    drawerContainer.translationY = targetTransY
                    updateAppPanelStatusBarAppearanceForPosition()
                    return true // Consume event
                } else if (isHorizontalDragging) {
                    var targetTransX = if (widgetDragStartFromOpen) dx else -screenWidth + dx
                    if (targetTransX < -screenWidth) targetTransX = -screenWidth
                    if (targetTransX > 0) targetTransX = 0f
                    widgetContainer.translationX = targetTransX
                    return true
                } else if (isSystemPanelSwipe) {
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (DEBUG_GESTURE_LOGS) {
                    Log.d(TAG, "dispatchTouchEvent() ACTION_UP or ACTION_CANCEL ${isVerticalDragging} ${isHorizontalDragging}")
                }

                // Cancel long press if lifted before timeout
                longPressRunnable?.let {
                    mainContainer.removeCallbacks(it)
                    longPressRunnable = null
                }

                if (isSystemPanelSwipe) {
                    isSystemPanelSwipe = false
                    velocityTracker?.clear()
                    return true
                } else if (isVerticalDragging) {
                    isVerticalDragging = false

                    velocityTracker?.computeCurrentVelocity(1000)
                    val velocityY = velocityTracker?.yVelocity ?: 0f

                    val currentY = drawerContainer.translationY
                    val height = drawerContainer.height.toFloat()
                    val distanceEnoughOpen = currentY < height * 0.4f
                    val distanceEnoughClose = currentY > height * 0.6f
                    val velocityEnoughOpen = -velocityY > minFlingVelocity
                    val velocityEnoughClose = velocityY > minFlingVelocity

                    val openEnough = distanceEnoughOpen || velocityEnoughOpen
                    val closeEnough = distanceEnoughClose || velocityEnoughClose

                    if (openEnough && !closeEnough) {
                        animatePanel(Panel.DRAWER, 0f, PanelState.FlingToOpen(Panel.DRAWER), PanelState.Opened(Panel.DRAWER))
                    } else if (closeEnough && !openEnough) {
                        animatePanel(Panel.DRAWER, height, PanelState.FlingToClose(Panel.DRAWER), PanelState.Closed)
                    } else {
                        // When both conditions are true, prioritize velocity direction
                        if (-velocityY > minFlingVelocity) {
                            // Fling upward (open direction)
                            animatePanel(Panel.DRAWER, 0f, PanelState.FlingToOpen(Panel.DRAWER), PanelState.Opened(Panel.DRAWER))
                        } else if (velocityY > minFlingVelocity) {
                            // Fling downward (close direction)
                            animatePanel(Panel.DRAWER, height, PanelState.FlingToClose(Panel.DRAWER), PanelState.Closed)
                        } else if (dragStartFromOpen) {
                            animatePanel(Panel.DRAWER, 0f, PanelState.ResetToOpen(Panel.DRAWER), PanelState.Opened(Panel.DRAWER))
                        } else {
                            animatePanel(Panel.DRAWER, height, PanelState.ResetToClose(Panel.DRAWER), PanelState.Closed)
                        }
                    }

                    velocityTracker?.clear()
                    return true
                } else if (isHorizontalDragging) {
                    isHorizontalDragging = false
                    velocityTracker?.computeCurrentVelocity(1000)
                    val velocityX = velocityTracker?.xVelocity ?: 0f
                    val currentX = widgetContainer.translationX
                    val width = widgetContainer.width.toFloat()
                    val fraction = (currentX + width) / width // 0 (closed at -w) -> 1 (open at 0)
                    val distanceEnoughOpen = fraction > 0.6f
                    val distanceEnoughClose = fraction < 0.4f
                    val velocityEnoughOpen = velocityX > minFlingVelocity
                    val velocityEnoughClose = -velocityX > minFlingVelocity

                    val openEnough = distanceEnoughOpen || velocityEnoughOpen
                    val closeEnough = distanceEnoughClose || velocityEnoughClose

                    if (DEBUG_GESTURE_LOGS) {
                        Log.i(TAG, "dispatchTouchEvent: openEnough: $openEnough, closeEnough: $closeEnough")
                    }

                    if (openEnough && !closeEnough) {
                        animatePanel(Panel.WIDGET, 0f, PanelState.FlingToOpen(Panel.WIDGET), PanelState.Opened(Panel.WIDGET))
                    } else if (closeEnough && !openEnough) {
                        animatePanel(Panel.WIDGET, -width, PanelState.FlingToClose(Panel.WIDGET), PanelState.Closed)
                    } else {
                        // When both conditions are true, prioritize velocity direction
                        if (velocityX > minFlingVelocity) {
                            // Fling to right (open direction)
                            animatePanel(Panel.WIDGET, 0f, PanelState.FlingToOpen(Panel.WIDGET), PanelState.Opened(Panel.WIDGET))
                        } else if (-velocityX > minFlingVelocity) {
                            // Fling to left (close direction)
                            animatePanel(Panel.WIDGET, -width, PanelState.FlingToClose(Panel.WIDGET), PanelState.Closed)
                        } else if (widgetDragStartFromOpen) {
                            animatePanel(Panel.WIDGET, 0f, PanelState.ResetToOpen(Panel.WIDGET), PanelState.Opened(Panel.WIDGET))
                        } else {
                            animatePanel(Panel.WIDGET, -width, PanelState.ResetToClose(Panel.WIDGET), PanelState.Closed)
                        }
                    }

                    velocityTracker?.clear()
                    return true
                }
                
                // If we didn't drag, clear tracker
                if (ev.action == MotionEvent.ACTION_UP || ev.action == MotionEvent.ACTION_CANCEL) {
                    velocityTracker?.clear()
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    /**
     * 获取 AppDrawerController 中的 RecyclerView，用于判断其滚动状态。
     */
    private fun getDrawerRecyclerView(): RecyclerView? {
        return appDrawerController.getRecyclerView()
    }

    companion object {
        private const val TAG = "LauncherActivity"
        private const val DEBUG_GESTURE_LOGS = false
    }

    private enum class Panel {
        DRAWER,
        WIDGET
    }

    private sealed class PanelState {
        data object Closed : PanelState()
        data class Opened(val panel: Panel) : PanelState()
        data class Dragging(val panel: Panel) : PanelState()
        data class FlingToOpen(val panel: Panel) : PanelState()
        data class FlingToClose(val panel: Panel) : PanelState()
        data class ResetToOpen(val panel: Panel) : PanelState()
        data class ResetToClose(val panel: Panel) : PanelState()

        fun isIdleClosed(): Boolean = this is Closed

        fun isOpened(panel: Panel): Boolean = this is Opened && this.panel == panel

        fun panelOrNull(): Panel? {
            return when (this) {
                Closed -> null
                is Opened -> panel
                is Dragging -> panel
                is FlingToOpen -> panel
                is FlingToClose -> panel
                is ResetToOpen -> panel
                is ResetToClose -> panel
            }
        }

        fun toLogString(): String {
            val panel = panelOrNull()?.name?.lowercase() ?: "none"
            val state = when (this) {
                Closed -> "closed"
                is Opened -> "opened"
                is Dragging -> "dragging"
                is FlingToOpen -> "fling_to_open"
                is FlingToClose -> "fling_to_close"
                is ResetToOpen -> "reset_to_open"
                is ResetToClose -> "reset_to_close"
            }
            return "$panel/$state"
        }
    }
}

private fun View.isPointInside(rawX: Float, rawY: Float): Boolean {
    val location = IntArray(2)
    getLocationOnScreen(location)
    val left = location[0]
    val top = location[1]
    val right = left + width
    val bottom = top + height
    return rawX >= left && rawX <= right && rawY >= top && rawY <= bottom
}
