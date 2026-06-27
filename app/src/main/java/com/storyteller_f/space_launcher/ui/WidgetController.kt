package com.storyteller_f.space_launcher.ui

import android.app.Activity
import android.appwidget.AppWidgetProviderInfo
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.util.SizeF
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.storyteller_f.space_launcher.LauncherActivity
import com.storyteller_f.space_launcher.R
import com.storyteller_f.space_launcher.data.SettingsRepository
import com.storyteller_f.space_launcher.widget.WidgetCellLayout
import com.storyteller_f.space_launcher.widget.WidgetDragHelper
import com.storyteller_f.space_launcher.widget.WidgetInfo
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

class WidgetController(
    private val activity: LauncherActivity,
    private val container: ViewGroup
) {

    private lateinit var appWidgetManager: AppWidgetManager
    private lateinit var appWidgetHost: AppWidgetHost
    private lateinit var cellLayout: WidgetCellLayout
    private lateinit var settingsRepo: SettingsRepository
    private var dragHelper: WidgetDragHelper? = null
    private var currentWidgetColumns: Int = SettingsRepository.DEFAULT_WIDGET_COLUMNS
    private var currentIsLandscape: Boolean = false
    private var menuPopupWindow: PopupWindow? = null
    private var pendingAddWidgetId: Int = -1
    private var rootView: View? = null
    private var receiverRegistered = false
    private var widgetsRestored = false
    private var restoreWidgetsScheduled = false
    private var restoreWidgetsJob: Job? = null
    private var cellLayoutPass = 0
    private val cellLayoutEvents = MutableSharedFlow<Int>(replay = 1, extraBufferCapacity = 1)

    data class ActivityResultEvent(val requestCode: Int, val resultCode: Int, val data: Intent?)
    private val activityResultFlow = MutableSharedFlow<ActivityResultEvent>(extraBufferCapacity = 1)
    
    private val settingsChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.storyteller_f.space_launcher.SETTINGS_CHANGED") {
                // 延迟一小段时间后再检查，确保设置已保存
                rootView?.postDelayed({
                    checkSettingsChanges()
                }, 100)
            }
        }
    }

    companion object {
        private const val TAG = "WidgetController"
        const val DEBUG_TAG = "WidgetLifecycleProbe"
        const val APP_WIDGET_HOST_ID = 1024
        const val REQUEST_CREATE_APPWIDGET = 2
        const val REQUEST_BIND_APPWIDGET = 3
        private const val EXTRA_PENDING_WIDGET_ID = "pending_widget_id"
    }

    fun onCreate(savedInstanceState: Bundle?) {
        savedInstanceState?.let {
            pendingAddWidgetId = it.getInt(EXTRA_PENDING_WIDGET_ID, -1)
        }
    }

    fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(EXTRA_PENDING_WIDGET_ID, pendingAddWidgetId)
    }

    fun attach() {
        val view = activity.layoutInflater.inflate(R.layout.fragment_widget, container, false)
        rootView = view
        container.addView(view)

        // Avoid status bar
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.setPadding(v.paddingLeft, insets.top, v.paddingRight, v.paddingBottom)
            WindowInsetsCompat.CONSUMED
        }

        cellLayout = view.findViewById(R.id.widget_cell_layout)
        cellLayout.onLayoutCompleted = {
            cellLayoutPass += 1
            cellLayoutEvents.tryEmit(cellLayoutPass)
        }
        cellLayoutEvents.tryEmit(cellLayoutPass)
        settingsRepo = SettingsRepository(activity)

        // 记录当前方向和列数
        currentIsLandscape = activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        currentWidgetColumns = settingsRepo.getWidgetColumns(currentIsLandscape)

        // Apply user-customizable grid size from settings
        val countX = currentWidgetColumns
        
        // 读取已有 widgets 信息，决定合理的初始行数
        val jsonStr = settingsRepo.getWidgetDataForLayout(currentIsLandscape)
        val initialInfos = WidgetInfo.listFromJson(jsonStr)
        val maxRequiredY = initialInfos.maxOfOrNull { it.cellY + it.spanY } ?: 0
        val countY = max(
            WidgetCellLayout.DEFAULT_COUNT_Y,
            maxRequiredY + WidgetCellLayout.TRAILING_EMPTY_ROWS
        )
            
        cellLayout.setGridSize(countX, countY)
        // Load debug settings
        cellLayout.showGridLines = settingsRepo.isShowGridLines()
        cellLayout.showOccupiedCellBackground = settingsRepo.isShowCellBackground()

        // 设置菜单按钮点击事件
        val menuButton = view.findViewById<FloatingActionButton>(R.id.btn_widget_menu)
        menuButton.setOnClickListener {
            showPopupMenu(menuButton)
        }

        // Set up drag helper
        dragHelper = WidgetDragHelper(
            cellLayout,
            onWidgetChanged = { changedView ->
                updateWidgetSizeOptions(changedView)
                saveAllWidgetInfos()
            },
            onWidgetDeleted = { deletedView ->
                deleteWidgetView(deletedView)
            }
        )
        dragHelper?.attach()

        appWidgetManager = AppWidgetManager.getInstance(activity)
        appWidgetHost = AppWidgetHost(activity, APP_WIDGET_HOST_ID)

        logWidgetDebugState("attach-before-restore")
        ensureWidgetsRestored()
    }

    fun onStart() {
        if (!::appWidgetHost.isInitialized) return
        appWidgetHost.startListening()
        logWidgetDebugState("onStart-after-startListening")
        ensureWidgetsRestored()
        // 注册设置变化广播接收器 (Android 14+ 需要指定导出标志)
        if (!receiverRegistered) {
            val filter = IntentFilter("com.storyteller_f.space_launcher.SETTINGS_CHANGED")
            activity.registerReceiver(settingsChangeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            receiverRegistered = true
        }
    }
    
    fun onResume() {
        // 检查设置是否发生变化
        checkSettingsChanges()
    }
    
    /**
     * 检查设置变化并刷新布局
     */
    private fun checkSettingsChanges() {
        if (!::cellLayout.isInitialized) return
        val newIsLandscape = activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val newColumns = settingsRepo.getWidgetColumns(newIsLandscape)

        // Update debug settings
        cellLayout.showGridLines = settingsRepo.isShowGridLines()
        cellLayout.showOccupiedCellBackground = settingsRepo.isShowCellBackground()
        cellLayout.invalidate()

        if (newColumns != currentWidgetColumns || newIsLandscape != currentIsLandscape) {
            // 方向或列数发生了变化，保存旧方向数据后刷新
            currentIsLandscape = newIsLandscape
            refreshGridLayout(newColumns)
        }
    }
    
    /**
     * 刷新网格布局
     */
    private fun refreshGridLayout(newColumns: Int) {
        currentWidgetColumns = newColumns
        widgetsRestored = false

        // 从prefs中获取新方向+列数对应的widget信息
        val jsonStr = settingsRepo.getWidgetDataForColumns(newColumns, currentIsLandscape)
        val currentWidgetInfos = WidgetInfo.listFromJson(jsonStr)

        // 清除现有widget显示（但保留数据）
        clearWidgetViewsOnly()

        // 更新cell layout
        val maxRequiredY = currentWidgetInfos.maxOfOrNull { it.cellY + it.spanY } ?: 0
        val newCountY = max(
            WidgetCellLayout.DEFAULT_COUNT_Y,
            maxRequiredY + WidgetCellLayout.TRAILING_EMPTY_ROWS
        )
        cellLayout.setGridSize(newColumns, newCountY)

        runAfterNextCellLayout {
            // 重新添加widget到新的布局中
            restoreWidgetsWithNewLayout(currentWidgetInfos, newColumns)
            cellLayout.requestLayout()
            awaitWidgetChildrenLaidOut("refreshGridLayout")
            widgetsRestored = true
        }

        Log.d(TAG, "Widget grid refreshed: $newColumns (landscape=$currentIsLandscape), widgets restored: ${currentWidgetInfos.size}")
    }

    /**
     * 只清除widget视图，保留widget数据
     */
    private fun clearWidgetViewsOnly() {
        // 只移除视图，不删除widget ID
        cellLayout.removeAllViews()
        cellLayout.occupancy.clear()
    }
    
    /**
     * 根据新的布局重新添加widget
     */
    private fun restoreWidgetsWithNewLayout(widgetInfos: List<WidgetInfo>, newColumns: Int) {
        val countY = cellLayout.countY
        Log.d(DEBUG_TAG, "restoreWidgetsWithNewLayout count=${widgetInfos.size} columns=$newColumns rows=$countY")
        for (info in widgetInfos) {
            val appWidgetInfo = appWidgetManager.getAppWidgetInfo(info.appWidgetId)
            if (appWidgetInfo != null) {
                // 重新添加widget视图
                addWidgetViewForLayoutChange(info.appWidgetId, info, newColumns, countY)
            } else {
                Log.w(DEBUG_TAG, "restoreWidgetsWithNewLayout skip id=${info.appWidgetId}: getAppWidgetInfo returned null")
            }
        }
    }
    
    /**
     * 为布局变更添加widget视图
     */
    private fun addWidgetViewForLayoutChange(appWidgetId: Int, info: WidgetInfo, newColumns: Int, newRows: Int) {
        val appWidgetInfo = appWidgetManager.getAppWidgetInfo(appWidgetId) ?: return
        val hostView = appWidgetHost.createView(activity, appWidgetId, appWidgetInfo)
        hostView.setAppWidget(appWidgetId, appWidgetInfo)

        val density = activity.resources.displayMetrics.density
        val cellWidthPx = cellLayout.getCellWidth()
        val cellHeightPx = cellLayout.getCellHeight()

        // 计算新的span值
        val spanX = if (cellWidthPx > 0) {
            calculateSpan(appWidgetInfo.minWidth, cellWidthPx, density)
                .coerceAtMost(newColumns)
        } else {
            1
        }
        val spanY = if (cellHeightPx > 0) {
            calculateSpan(appWidgetInfo.minHeight, cellHeightPx, density)
                .coerceAtMost(newRows)
        } else {
            1
        }

        // 尝试在新的布局中找到合适的位置
        var cellX = info.cellX
        var cellY = info.cellY
        
        // 确保位置在新网格范围内
        cellX = cellX.coerceAtMost(newColumns - spanX)
        cellY = cellY.coerceAtMost(newRows - spanY)
        
        // 检查位置是否可用，不可用则寻找新位置
        if (!cellLayout.occupancy.isRegionVacant(cellX, cellY, spanX, spanY)) {
            val vacantCell = cellLayout.occupancy.findVacantCell(spanX, spanY)
            if (vacantCell != null) {
                cellX = vacantCell.x
                cellY = vacantCell.y
            } else {
                // 如果找不到空间，扩展网格
                cellLayout.expandGridIfNeeded(cellY + spanY)
                cellX = 0
                cellY = cellLayout.countY - spanY
            }
        }

        val placed = cellLayout.addWidget(hostView, cellX, cellY, spanX, spanY)
        if (placed) {
            // 设置拖拽支持
            dragHelper?.setupChildDrag(hostView)
            
            updateWidgetSizeOptions(hostView, cellX, cellY, spanX, spanY)
        }
    }

    fun onStop() {
        // 注销广播接收器
        if (receiverRegistered) {
            try {
                activity.unregisterReceiver(settingsChangeReceiver)
            } catch (e: IllegalArgumentException) {
                // 接收器可能未注册，忽略异常
            }
            receiverRegistered = false
        }
        try {
            if (::appWidgetHost.isInitialized) {
                appWidgetHost.stopListening()
            }
        } catch (e: Exception) {
            // Ignore potential errors during stop
        }
    }

    private fun selectWidget() {
        activity.lifecycleScope.launch {
            val appWidgetId = appWidgetHost.allocateAppWidgetId()
            val info = pickWidgetSuspend(appWidgetId)
            if (info == null) {
                appWidgetHost.deleteAppWidgetId(appWidgetId)
                return@launch
            }

            // 1. Bind widget if needed
            val bound = if (!appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, info.provider)) {
                pendingAddWidgetId = appWidgetId
                val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                }
                activity.startActivityForResult(intent, REQUEST_BIND_APPWIDGET)
                val result = activityResultFlow.first { it.requestCode == REQUEST_BIND_APPWIDGET }
                result.resultCode == Activity.RESULT_OK
            } else true

            if (!bound) {
                appWidgetHost.deleteAppWidgetId(appWidgetId)
                pendingAddWidgetId = -1
                return@launch
            }

            // 2. Configure widget if needed
            val configured = if (info.configure != null) {
                try {
                    pendingAddWidgetId = appWidgetId
                    appWidgetHost.startAppWidgetConfigureActivityForResult(
                        activity,
                        appWidgetId,
                        0,
                        REQUEST_CREATE_APPWIDGET,
                        null
                    )
                    val result = activityResultFlow.first { it.requestCode == REQUEST_CREATE_APPWIDGET }
                    result.resultCode == Activity.RESULT_OK
                } catch (e: Exception) {
                    appWidgetHost.deleteAppWidgetId(appWidgetId)
                    pendingAddWidgetId = -1
                    Toast.makeText(activity, "Failed to open widget configuration", Toast.LENGTH_SHORT).show()
                    Log.e(TAG, "Failed to start configure activity", e)
                    false
                }
            } else true

            if (!configured) {
                appWidgetHost.deleteAppWidgetId(appWidgetId)
                pendingAddWidgetId = -1
                return@launch
            }

            // 3. Add widget view
            addWidgetView(appWidgetId, null)
            pendingAddWidgetId = -1
        }
    }

    private suspend fun pickWidgetSuspend(appWidgetId: Int): AppWidgetProviderInfo? =
        suspendCancellableCoroutine { continuation ->
            val picker = WidgetPickerBottomSheetFragment.newInstance(appWidgetId) { info, _ ->
                if (continuation.isActive) continuation.resume(info)
            }
            picker.onDismissListener = {
                if (continuation.isActive) continuation.resume(null)
            }
            picker.show(activity.supportFragmentManager, "widget_picker")
        }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        activityResultFlow.tryEmit(ActivityResultEvent(requestCode, resultCode, data))
    }

    fun isWidgetEditMode(): Boolean {
        return ::cellLayout.isInitialized && cellLayout.isWidgetEditMode
    }

    fun exitWidgetEditMode() {
        dragHelper?.exitEditMode()
    }

    fun cancelWidgetLongPress() {
        dragHelper?.cancelLongPress()
    }

    fun cancelWidgetEditModeLongPress() {
        dragHelper?.cancelEditModeLongPress()
    }

    fun cancelPendingWidgetMoveModeForExternalDrag() {
        dragHelper?.cancelPendingMoveModeForExternalDrag()
    }


    /**
     * Compute span (number of cells) from widget's min size in dp.
     */
    private fun calculateSpan(minSizeDp: Int, cellSizePx: Int, density: Float): Int {
        val minSizePx = (minSizeDp * density).toInt()
        return max(1, ceil(minSizePx.toFloat() / cellSizePx).toInt())
    }

    /**
     * Add widget view to the cell layout.
     *
     * @param info if non-null, use saved cell position; otherwise auto-place
     */
    private fun addWidgetView(appWidgetId: Int, info: WidgetInfo?) {
        if (!isCellLayoutMeasured()) {
            runWhenCellLayoutMeasured {
                addWidgetView(appWidgetId, info)
            }
            return
        }

        val appWidgetInfo = appWidgetManager.getAppWidgetInfo(appWidgetId) ?: return
        val hostView = appWidgetHost.createView(activity, appWidgetId, appWidgetInfo)
        hostView.setAppWidget(appWidgetId, appWidgetInfo)

        val density = activity.resources.displayMetrics.density

        // Calculate span from min size
        val cellWidthPx = cellLayout.getCellWidth()
        val cellHeightPx = cellLayout.getCellHeight()

        val spanX: Int
        val spanY: Int
        val cellX: Int
        val cellY: Int

        if (info != null) {
            // Restore saved position
            spanX = info.spanX.coerceIn(1, cellLayout.countX)
            spanY = info.spanY.coerceAtLeast(1)
            cellX = info.cellX.coerceIn(0, cellLayout.countX - spanX)
            cellY = info.cellY.coerceAtLeast(0)
            cellLayout.expandGridIfNeeded(cellY + spanY)
        } else {
            // Calculate span from widget info
            spanX = if (cellWidthPx > 0) {
                calculateSpan(appWidgetInfo.minWidth, cellWidthPx, density)
                    .coerceAtMost(cellLayout.countX)
            } else {
                max(1, appWidgetInfo.minWidth / 80).coerceAtMost(cellLayout.countX)
            }
            spanY = if (cellHeightPx > 0) {
                calculateSpan(appWidgetInfo.minHeight, cellHeightPx, density)
                    .coerceAtMost(cellLayout.countY)
            } else {
                max(1, appWidgetInfo.minHeight / 80).coerceAtMost(cellLayout.countY)
            }

            // Auto-place — expand grid if no space
            var vacantCell = cellLayout.occupancy.findVacantCell(spanX, spanY)
            if (vacantCell == null) {
                // Expand grid by adding rows
                cellLayout.expandGridIfNeeded(cellLayout.countY + spanY)
                vacantCell = cellLayout.occupancy.findVacantCell(spanX, spanY)
            }
            if (vacantCell == null) {
                Toast.makeText(activity, "No space for widget", Toast.LENGTH_SHORT).show()
                appWidgetHost.deleteAppWidgetId(appWidgetId)
                return
            }
            cellX = vacantCell.x
            cellY = vacantCell.y
        }

        val placed = cellLayout.addWidget(hostView, cellX, cellY, spanX, spanY)
        if (!placed) {
            Toast.makeText(activity, "Failed to place widget", Toast.LENGTH_SHORT).show()
            appWidgetHost.deleteAppWidgetId(appWidgetId)
            return
        }

        // Set up drag for this widget
        dragHelper?.setupChildDrag(hostView)

        updateWidgetSizeOptions(hostView, cellX, cellY, spanX, spanY)

        // 只在添加widget时保存
        saveAllWidgetInfos()
        Log.d(TAG, "Added widget $appWidgetId at ($cellX, $cellY) span ${spanX}x${spanY}")
    }

    /**
     * 保存所有widget信息（按当前布局）
     */
    private fun saveAllWidgetInfos() {
        val infos = cellLayout.getAllWidgetInfos { view ->
            (view as? AppWidgetHostView)?.appWidgetId ?: -1
        }
        settingsRepo.saveWidgetDataForLayout(WidgetInfo.listToJson(infos), currentIsLandscape)
    }
        
    /**
     * 恢复指定布局的widget
     */
    private fun restoreWidgetsForLayout() {
        val jsonStr = settingsRepo.getWidgetDataForLayout(currentIsLandscape)
        val infos = WidgetInfo.listFromJson(jsonStr)
        Log.d(DEBUG_TAG, "restoreWidgetsForLayout start saved=${infos.size} jsonLength=${jsonStr.length}")

        for (info in infos) {
            val appWidgetInfo = appWidgetManager.getAppWidgetInfo(info.appWidgetId)
            if (appWidgetInfo != null) {
                addWidgetView(info.appWidgetId, info)
            } else {
                Log.w(DEBUG_TAG, "restoreWidgetsForLayout skip id=${info.appWidgetId}: getAppWidgetInfo returned null")
            }
        }
        logWidgetDebugState("restoreWidgetsForLayout-end")
    }

    private fun clearAllWidgets() {
        if (cellLayout.childCount == 0) {
            Toast.makeText(activity, "No widgets to clear", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(activity)
            .setTitle("Clear All Widgets")
            .setMessage("Are you sure you want to remove all widgets?")
            .setPositiveButton("Clear") { _, _ ->
                clearAllWidgetsInternal()
                Toast.makeText(activity, "All widgets cleared", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "All widgets cleared")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * 打印所有widget信息到Log
     */
    private fun printAllWidgetInfo() {
        val builder = StringBuilder()
        builder.appendLine("========== Widget Info ==========")
        builder.appendLine("Total widgets: ${cellLayout.childCount}")
        builder.appendLine("Grid size: ${cellLayout.countX} x ${cellLayout.countY}")
        builder.appendLine()

        for (i in 0 until cellLayout.childCount) {
            val child = cellLayout.getChildAt(i)
            val widgetId = (child as? AppWidgetHostView)?.appWidgetId ?: -1
            val appWidgetInfo = if (widgetId != -1) appWidgetManager.getAppWidgetInfo(widgetId) else null

            val layoutParams = child.layoutParams as? com.storyteller_f.space_launcher.widget.WidgetCellLayoutParams
            
            builder.appendLine("--- Widget #$i ---")
            builder.appendLine("AppWidgetId: $widgetId")
            builder.appendLine("Widget Name: ${appWidgetInfo?.label ?: "Unknown"}")
            builder.appendLine("Min Size: ${appWidgetInfo?.minWidth}dp x ${appWidgetInfo?.minHeight}dp")
            builder.appendLine("Target Size: ${appWidgetInfo?.targetCellWidth} x ${appWidgetInfo?.targetCellHeight}")
            builder.appendLine("Cell Position: (${layoutParams?.cellX ?: -1}, ${layoutParams?.cellY ?: -1})")
            builder.appendLine("Span: ${layoutParams?.cellHSpan ?: -1} x ${layoutParams?.cellVSpan ?: -1}")
            builder.appendLine()
        }

        // 打印occupancy信息
        builder.appendLine("--- Occupancy Info ---")
        val occupancy = cellLayout.occupancy
        val occupiedCells = mutableListOf<String>()
        for (y in 0 until cellLayout.countY) {
            for (x in 0 until cellLayout.countX) {
                if (!occupancy.isRegionVacant(x, y, 1, 1)) {
                    occupiedCells.add("($x, $y)")
                }
            }
        }
        builder.appendLine("Occupied cells: ${occupiedCells.size}")
        builder.appendLine("Occupied positions: ${occupiedCells.joinToString(", ")}")
        builder.appendLine("===================================")

        Log.d(TAG, builder.toString())
        Toast.makeText(activity, "Widget info printed to log", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * 内部清除所有widget的方法（不显示提示）
     */
    private fun clearAllWidgetsInternal() {
        // 删除所有widget IDs
        for (i in 0 until cellLayout.childCount) {
            val child = cellLayout.getChildAt(i)
            val widgetId = (child as? AppWidgetHostView)?.appWidgetId ?: -1
            if (widgetId != -1) {
                appWidgetHost.deleteAppWidgetId(widgetId)
            }
        }
        // 移除所有视图并清除占用信息
        cellLayout.removeAllViews()
        cellLayout.occupancy.clear()
        cellLayout.ensureTrailingEmptyRows()
        // 清除当前方向+布局的持久化数据
        settingsRepo.clearWidgetDataForLayout(currentIsLandscape)
        widgetsRestored = true
    }

    fun detach() {
        restoreWidgetsJob?.cancel()
        restoreWidgetsJob = null
        dragHelper?.exitEditMode()
        dragHelper = null
        menuPopupWindow?.dismiss()
        menuPopupWindow = null
        if (::cellLayout.isInitialized) {
            cellLayout.onLayoutCompleted = null
        }
        rootView?.let(container::removeView)
        rootView = null
        widgetsRestored = false
        restoreWidgetsScheduled = false
    }

    /**
     * 显示菜单PopupWindow
     */
    private fun showPopupMenu(anchorView: View) {
        // 如果popup已经显示,则关闭
        if (menuPopupWindow?.isShowing == true) {
            menuPopupWindow?.dismiss()
            menuPopupWindow = null
            return
        }

        // 创建popup view
        val popupView = activity.layoutInflater
            .inflate(R.layout.popup_widget_menu, null)

        // 设置popup window
        menuPopupWindow = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            isFocusable = true
            elevation = 8f
            // 设置背景以支持点击外部关闭
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

            // 设置显示位置(在按钮上方)
            val location = IntArray(2)
            anchorView.getLocationOnScreen(location)
            showAtLocation(anchorView, Gravity.NO_GRAVITY, location[0] + anchorView.width - width, location[1] - height - 16)

            // 点击外部时关闭
            setOnDismissListener {
                menuPopupWindow = null
            }
        }

        // 设置按钮点击事件
        popupView.findViewById<View>(R.id.btn_add_widget_popup).setOnClickListener {
            selectWidget()
            menuPopupWindow?.dismiss()
            menuPopupWindow = null
        }

        popupView.findViewById<View>(R.id.btn_clear_widgets_popup).setOnClickListener {
            clearAllWidgets()
            menuPopupWindow?.dismiss()
            menuPopupWindow = null
        }

        popupView.findViewById<View>(R.id.btn_print_widgets_popup).setOnClickListener {
            printAllWidgetInfo()
            menuPopupWindow?.dismiss()
            menuPopupWindow = null
        }
    }

    private fun updateWidgetSizeOptions(view: View) {
        val hostView = view as? AppWidgetHostView ?: return
        val lp = view.layoutParams as? com.storyteller_f.space_launcher.widget.WidgetCellLayoutParams ?: return
        updateWidgetSizeOptions(hostView, lp.cellX, lp.cellY, lp.cellHSpan, lp.cellVSpan)
    }

    private fun updateWidgetSizeOptions(hostView: AppWidgetHostView, cellX: Int, cellY: Int, spanX: Int, spanY: Int) {
        if (!isCellLayoutMeasured()) {
            runWhenCellLayoutMeasured {
                updateWidgetSizeOptions(hostView, cellX, cellY, spanX, spanY)
            }
            return
        }

        val rect = cellLayout.cellToRect(cellX, cellY, spanX, spanY)
        if (rect.width() <= 0 || rect.height() <= 0) return

        val density = activity.resources.displayMetrics.density
        val widthDp = (rect.width() / density).roundToInt().coerceAtLeast(1)
        val heightDp = (rect.height() / density).roundToInt().coerceAtLeast(1)
        val options = Bundle()
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
        options.putParcelableArrayList(
            AppWidgetManager.OPTION_APPWIDGET_SIZES,
            arrayListOf(SizeF(widthDp.toFloat(), heightDp.toFloat()))
        )
        appWidgetManager.updateAppWidgetOptions(hostView.appWidgetId, options)
    }

    private fun runWhenCellLayoutMeasured(action: suspend () -> Unit): Job? {
        if (!::cellLayout.isInitialized) return null
        return activity.lifecycleScope.launch {
            awaitCellLayoutMeasured()
            if (rootView != null) {
                action()
            }
        }
    }

    private fun runAfterNextCellLayout(action: suspend () -> Unit): Job? {
        if (!::cellLayout.isInitialized) return null
        return activity.lifecycleScope.launch {
            val startPass = cellLayoutPass
            cellLayout.requestLayout()
            awaitCellLayoutPassAfter(startPass)
            if (rootView != null) {
                action()
            }
        }
    }

    fun ensureWidgetsRestored() {
        if (!::cellLayout.isInitialized || !::appWidgetManager.isInitialized || widgetsRestored || restoreWidgetsScheduled) {
            Log.d(
                DEBUG_TAG,
                "ensureWidgetsRestored skipped initialized=${::cellLayout.isInitialized}/${::appWidgetManager.isInitialized} " +
                        "widgetsRestored=$widgetsRestored restoreWidgetsScheduled=$restoreWidgetsScheduled"
            )
            return
        }

        restoreWidgetsScheduled = true
        Log.d(DEBUG_TAG, "ensureWidgetsRestored scheduled")
        restoreWidgetsJob = activity.lifecycleScope.launch {
            awaitCellLayoutMeasured()
            awaitFrame()
            logWidgetDebugState("ensureWidgetsRestored-after-measured")
            if (!widgetsRestored) {
                restoreWidgetsForLayout()
                cellLayout.requestLayout()
                awaitWidgetChildrenLaidOut("ensureWidgetsRestored")
                widgetsRestored = true
                logWidgetDebugState("ensureWidgetsRestored-after-child-layout")
            }
        }.also { job ->
            job.invokeOnCompletion {
                restoreWidgetsScheduled = false
                if (restoreWidgetsJob == job) {
                    restoreWidgetsJob = null
                }
            }
        }
    }

    private suspend fun awaitCellLayoutMeasured() {
        if (isCellLayoutMeasured()) return
        cellLayout.requestLayout()
        Log.d(DEBUG_TAG, "awaitCellLayoutMeasured waiting")
        cellLayoutEvents
            .onStart { emit(cellLayoutPass) }
            .filter { rootView != null && isCellLayoutMeasured() }
            .first()
        Log.d(DEBUG_TAG, "awaitCellLayoutMeasured ready")
    }

    private suspend fun awaitCellLayoutPassAfter(startPass: Int) {
        cellLayoutEvents
            .onStart { emit(cellLayoutPass) }
            .filter { pass -> pass > startPass && rootView != null && isCellLayoutMeasured() }
            .first()
    }

    private suspend fun awaitWidgetChildrenLaidOut(reason: String) {
        if (!::cellLayout.isInitialized || cellLayout.childCount == 0 || areWidgetChildrenLaidOut()) {
            return
        }

        val startPass = cellLayoutPass
        Log.d(
            DEBUG_TAG,
            "awaitWidgetChildrenLaidOut waiting reason=$reason pass=$startPass childCount=${cellLayout.childCount}"
        )
        cellLayout.requestLayout()
        cellLayoutEvents
            .filter { pass ->
                pass > startPass && rootView != null && areWidgetChildrenLaidOut()
            }
            .first()
        Log.d(DEBUG_TAG, "awaitWidgetChildrenLaidOut ready reason=$reason pass=$cellLayoutPass")
    }

    private fun areWidgetChildrenLaidOut(): Boolean {
        if (!::cellLayout.isInitialized) return false
        for (index in 0 until cellLayout.childCount) {
            val child = cellLayout.getChildAt(index)
            if (child.visibility != View.GONE && (child.width <= 0 || child.height <= 0)) {
                return false
            }
        }
        return true
    }

    private fun isCellLayoutMeasured(): Boolean {
        return ::cellLayout.isInitialized &&
                cellLayout.getCellWidth() > 0 &&
                cellLayout.getCellHeight() > 0
    }

    fun dumpWidgetDebugState(reason: String): String {
        val initialized = ::cellLayout.isInitialized && ::settingsRepo.isInitialized
        val savedJson = if (initialized) settingsRepo.getWidgetDataForLayout(currentIsLandscape) else ""
        val savedInfos = if (initialized) WidgetInfo.listFromJson(savedJson) else emptyList()
        val missingInfoIds = if (::appWidgetManager.isInitialized) {
            savedInfos
                .filter { appWidgetManager.getAppWidgetInfo(it.appWidgetId) == null }
                .map { it.appWidgetId }
        } else {
            savedInfos.map { it.appWidgetId }
        }
        val childSummaries = if (::cellLayout.isInitialized) {
            (0 until cellLayout.childCount).joinToString(prefix = "[", postfix = "]") { index ->
                val child = cellLayout.getChildAt(index)
                val widgetId = (child as? AppWidgetHostView)?.appWidgetId ?: -1
                val lp = child.layoutParams as? com.storyteller_f.space_launcher.widget.WidgetCellLayoutParams
                "#$index id=$widgetId cell=${lp?.cellX},${lp?.cellY} span=${lp?.cellHSpan}x${lp?.cellVSpan} " +
                        "size=${child.width}x${child.height}"
            }
        } else {
            "[]"
        }

        return buildString {
            append("reason=").append(reason)
            append(" rootAttached=").append(rootView?.parent != null)
            append(" hostInitialized=").append(::appWidgetHost.isInitialized)
            append(" managerInitialized=").append(::appWidgetManager.isInitialized)
            append(" receiverRegistered=").append(receiverRegistered)
            append(" widgetsRestored=").append(widgetsRestored)
            append(" restoreScheduled=").append(restoreWidgetsScheduled)
            append(" restoreJobActive=").append(restoreWidgetsJob?.isActive == true)
            append(" layoutPass=").append(cellLayoutPass)
            append(" landscape=").append(currentIsLandscape)
            append(" columns=").append(currentWidgetColumns)
            append(" measured=").append(isCellLayoutMeasured())
            if (::cellLayout.isInitialized) {
                append(" childCount=").append(cellLayout.childCount)
                append(" grid=").append(cellLayout.countX).append("x").append(cellLayout.countY)
                append(" cell=").append(cellLayout.getCellWidth()).append("x").append(cellLayout.getCellHeight())
                append(" view=").append(cellLayout.width).append("x").append(cellLayout.height)
            }
            append(" savedCount=").append(savedInfos.size)
            append(" savedJsonLength=").append(savedJson.length)
            append(" missingInfoIds=").append(missingInfoIds)
            append(" children=").append(childSummaries)
        }
    }

    fun logWidgetDebugState(reason: String) {
        Log.d(DEBUG_TAG, dumpWidgetDebugState(reason))
    }

    private fun deleteWidgetView(view: View) {
        val widgetId = (view as? AppWidgetHostView)?.appWidgetId ?: -1
        if (widgetId != -1) {
            appWidgetHost.deleteAppWidgetId(widgetId)
        }
        cellLayout.removeWidget(view)
        saveAllWidgetInfos()
    }
}
