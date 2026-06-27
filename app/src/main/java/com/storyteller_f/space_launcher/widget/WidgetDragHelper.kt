package com.storyteller_f.space_launcher.widget

import android.animation.ValueAnimator
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ScrollView
import android.content.ContextWrapper
import android.view.ViewConfiguration
import com.storyteller_f.space_launcher.LauncherActivity
import kotlin.math.ceil

/**
 * Handles long-press drag of widgets within [WidgetCellLayout].
 *
 * Usage: attach to the cell layout via [attach].
 */
class WidgetDragHelper(
    private val cellLayout: WidgetCellLayout,
    private val onWidgetChanged: (View) -> Unit,
    private val onWidgetDeleted: (View) -> Unit
) {

    companion object {
        private const val TAG = "WidgetDragHelper"
        private const val LONG_PRESS_TIMEOUT_MS = 1000L
        private const val EDIT_MODE_TIMEOUT_MS = 2000L
        private const val DRAG_ELEVATION = 8f
        private const val AUTO_SCROLL_EDGE_DP = 72f
        private const val AUTO_SCROLL_MAX_STEP_DP = 24f
    }

    private var isDragging = false
    private var dragView: View? = null
    private var origCellX = 0
    private var origCellY = 0
    private var touchOffsetX = 0f
    private var touchOffsetY = 0f
    private var dragBaseLeft = 0
    private var dragBaseTop = 0
    private var origTranslationX = 0f
    private var origTranslationY = 0f
    private var origElevation = 0f

    private var longPressRunnable: Runnable? = null
    private var editModeRunnable: Runnable? = null
    private var longPressReady = false
    private var pendingLongPressChild: View? = null
    private var movedSinceDown = false
    private var downX = 0f
    private var downY = 0f
    private val touchSlop = ViewConfiguration.get(cellLayout.context).scaledTouchSlop
    private val cellLayoutLocation = IntArray(2)
    private val dragViewLocation = IntArray(2)
    private var resizeView: View? = null
    private var resizeStartCellX = 0
    private var resizeStartCellY = 0
    private var resizeStartSpanX = 0
    private var resizeStartSpanY = 0

    /**
     * Attach drag handling to the cell layout.
     * Call this after the cell layout is ready.
     */
    fun attach() {
        cellLayout.dragHelper = this
    }

    private fun getLauncherActivity(): LauncherActivity? {
        var context = cellLayout.context
        while (context is ContextWrapper) {
            if (context is LauncherActivity) {
                return context
            }
            context = context.baseContext
        }
        return null
    }

    fun setupChildDrag(child: View) {
        // Handled globally by onInterceptTouchEvent.
    }

    private fun eventContentX(event: MotionEvent): Float {
        cellLayout.getLocationOnScreen(cellLayoutLocation)
        return event.rawX - cellLayoutLocation[0]
    }

    private fun eventContentY(event: MotionEvent): Float {
        cellLayout.getLocationOnScreen(cellLayoutLocation)
        return event.rawY - cellLayoutLocation[1]
    }

    private fun displayedViewLeftInContent(view: View): Int {
        view.getLocationOnScreen(dragViewLocation)
        cellLayout.getLocationOnScreen(cellLayoutLocation)
        return dragViewLocation[0] - cellLayoutLocation[0]
    }

    private fun displayedViewTopInContent(view: View): Int {
        view.getLocationOnScreen(dragViewLocation)
        cellLayout.getLocationOnScreen(cellLayoutLocation)
        return dragViewLocation[1] - cellLayoutLocation[1]
    }

    private fun autoScrollParentIfNeeded(event: MotionEvent) {
        val scrollView = cellLayout.parent as? ScrollView ?: return
        val child = scrollView.getChildAt(0) ?: return
        val scrollRange = (child.height - scrollView.height).coerceAtLeast(0)
        if (scrollRange == 0) return

        scrollView.getLocationOnScreen(cellLayoutLocation)
        val pointerY = event.rawY - cellLayoutLocation[1]
        val density = cellLayout.resources.displayMetrics.density
        val edgeSize = AUTO_SCROLL_EDGE_DP * density
        val maxStep = AUTO_SCROLL_MAX_STEP_DP * density

        val deltaY = when {
            pointerY < edgeSize -> {
                val strength = ((edgeSize - pointerY) / edgeSize).coerceIn(0f, 1f)
                -(maxStep * strength).toInt().coerceAtLeast(1)
            }
            pointerY > scrollView.height - edgeSize -> {
                val strength = ((pointerY - (scrollView.height - edgeSize)) / edgeSize)
                    .coerceIn(0f, 1f)
                (maxStep * strength).toInt().coerceAtLeast(1)
            }
            else -> 0
        }
        if (deltaY == 0) return

        val nextScrollY = (scrollView.scrollY + deltaY).coerceIn(0, scrollRange)
        if (nextScrollY != scrollView.scrollY) {
            scrollView.scrollTo(scrollView.scrollX, nextScrollY)
        }
    }

    private fun startDrag(
        view: View,
        touchX: Float,
        touchY: Float,
        keepEditModeRunnable: Boolean = false
    ) {
        if (!keepEditModeRunnable) {
            cancelPendingPress()
        }
        isDragging = true
        dragView = view
        val lp = view.layoutParams as WidgetCellLayoutParams
        origCellX = lp.cellX
        origCellY = lp.cellY
        origElevation = view.elevation

        // 记录手指在 widget 内的相对偏移，拖拽时保持此偏移不变
        val bounds = Rect()
        view.getHitRect(bounds)
        dragBaseLeft = bounds.left
        dragBaseTop = bounds.top
        touchOffsetX = touchX - bounds.left
        touchOffsetY = touchY - bounds.top
        origTranslationX = view.translationX
        origTranslationY = view.translationY

        view.elevation = DRAG_ELEVATION
        view.alpha = 0.8f
        view.bringToFront()

        // Show grid lines while dragging
        cellLayout.forceShowGrid = true
        cellLayout.invalidate()

        view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        Log.d(TAG, "Started drag: widget at ($origCellX, $origCellY), touchOffset=($touchOffsetX, $touchOffsetY)")
        
        cellLayout.parent?.requestDisallowInterceptTouchEvent(true)
        getLauncherActivity()?.isWidgetDragging = true
    }

    private fun enterEditMode() {
        val touchedChild = pendingLongPressChild
        cancelPendingPress()
        if (isDragging) {
            resetDragState(dragView)
        }
        cellLayout.isWidgetEditMode = true
        cancelChildTouch(touchedChild)
        cellLayout.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        cellLayout.parent?.requestDisallowInterceptTouchEvent(true)
        getLauncherActivity()?.isWidgetDragging = true
        Log.d(TAG, "Entered widget edit mode")
    }

    private fun cancelChildTouch(child: View?) {
        child ?: return
        val now = SystemClock.uptimeMillis()
        val cancelEvent = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
        child.dispatchTouchEvent(cancelEvent)
        cancelEvent.recycle()
        child.isPressed = false
    }

    fun exitEditMode() {
        if (!cellLayout.isWidgetEditMode) return
        cancelResize()
        cellLayout.isWidgetEditMode = false
        cellLayout.parent?.requestDisallowInterceptTouchEvent(false)
        getLauncherActivity()?.isWidgetDragging = false
        Log.d(TAG, "Exited widget edit mode")
    }

    fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN && isDragging) {
            Log.w(TAG, "New touch sequence started while still dragging, resetting state.")
            cancelDrag()
        }
        
        if (isDragging) return true
        if (cellLayout.isWidgetEditMode) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = eventContentX(event)
                    downY = eventContentY(event)
                    val closeChild = cellLayout.findCloseButtonChild(downX.toInt(), downY.toInt())
                    if (closeChild != null) {
                        onWidgetDeleted(closeChild)
                        return true
                    }
                    val handleChild = cellLayout.findResizeHandleChild(downX.toInt(), downY.toInt())
                    if (handleChild != null) {
                        startResize(handleChild)
                        return true
                    }
                    exitEditMode()
                }
                MotionEvent.ACTION_MOVE -> return resizeView != null
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    finishResize()
                    return true
                }
            }
            return resizeView != null
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = eventContentX(event)
                downY = eventContentY(event)
                movedSinceDown = false
                val child = findChildByPosition(downX.toInt(), downY.toInt())
                if (child != null) {
                    pendingLongPressChild = child
                    longPressReady = false
                    longPressRunnable = Runnable {
                        longPressReady = true
                        cancelChildTouch(child)
                        startDrag(child, downX, downY, keepEditModeRunnable = true)
                    }
                    editModeRunnable = Runnable {
                        if (pendingLongPressChild == child && !movedSinceDown) {
                            enterEditMode()
                        }
                    }
                    cellLayout.postDelayed(longPressRunnable, LONG_PRESS_TIMEOUT_MS)
                    cellLayout.postDelayed(editModeRunnable, EDIT_MODE_TIMEOUT_MS)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (hasPendingPress()) {
                    cancelEditModeIfMoved(event)
                    val dx = eventContentX(event) - downX
                    val dy = eventContentY(event) - downY
                    if (Math.hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
                        val child = pendingLongPressChild
                        val canStartDrag = longPressReady && child != null
                        cancelPendingPress()
                        if (canStartDrag) {
                            startDrag(child, downX, downY)
                            return true
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                cancelPendingPress()
            }
        }
        return isDragging
    }

    fun cancelLongPress() {
        cancelPendingPress()
    }

    fun cancelEditModeLongPress() {
        movedSinceDown = true
        editModeRunnable?.let {
            cellLayout.removeCallbacks(it)
            editModeRunnable = null
        }
    }

    fun cancelPendingMoveModeForExternalDrag() {
        if (!isDragging && !longPressReady) {
            cancelPendingPress()
        } else {
            cancelEditModeLongPress()
        }
    }

    private fun cancelPendingPress() {
        longPressRunnable?.let {
            cellLayout.removeCallbacks(it)
            longPressRunnable = null
        }
        editModeRunnable?.let {
            cellLayout.removeCallbacks(it)
            editModeRunnable = null
        }
        longPressReady = false
        pendingLongPressChild = null
        movedSinceDown = false
        if (!isDragging && !cellLayout.isWidgetEditMode) {
            getLauncherActivity()?.isWidgetDragging = false
        }
    }

    private fun cancelEditModeIfMoved(event: MotionEvent) {
        if (movedSinceDown) return
        val dx = eventContentX(event) - downX
        val dy = eventContentY(event) - downY
        if (Math.hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
            movedSinceDown = true
            editModeRunnable?.let {
                cellLayout.removeCallbacks(it)
                editModeRunnable = null
            }
        }
    }

    private fun hasPendingPress(): Boolean {
        return longPressRunnable != null || editModeRunnable != null || pendingLongPressChild != null
    }

    private fun findChildByPosition(x: Int, y: Int): View? {
        for (i in cellLayout.childCount - 1 downTo 0) {
            val child = cellLayout.getChildAt(i)
            if (child.visibility == View.VISIBLE) {
                val bounds = Rect()
                child.getHitRect(bounds)
                if (bounds.contains(x, y)) {
                    return child
                }
            }
        }
        return null
    }

    fun onTouchEvent(event: MotionEvent): Boolean {
        if (cellLayout.isWidgetEditMode) {
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    updateResize(event)
                    return resizeView != null
                }
                MotionEvent.ACTION_UP -> {
                    finishResize()
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    cancelResize()
                    return true
                }
            }
            return true
        }

        if (!isDragging) {
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    if (hasPendingPress()) {
                        cancelEditModeIfMoved(event)
                        val dx = eventContentX(event) - downX
                        val dy = eventContentY(event) - downY
                        if (Math.hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
                            val child = pendingLongPressChild
                            val canStartDrag = longPressReady && child != null
                            cancelPendingPress()
                            if (canStartDrag) {
                                startDrag(child, downX, downY)
                                return true
                            }
                        }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    cancelPendingPress()
                }
            }
            return false
        }

        val view = dragView ?: return false

        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                cancelEditModeIfMoved(event)
                autoScrollParentIfNeeded(event)
                val lp = view.layoutParams as WidgetCellLayoutParams
                val rawX = eventContentX(event)
                val rawY = eventContentY(event)

                // widget 视图自由跟随手指，保持长按时的相对偏移
                val newLeft = (rawX - touchOffsetX).toInt()
                val newTop  = (rawY - touchOffsetY).toInt()
                view.translationX = origTranslationX + newLeft - dragBaseLeft
                view.translationY = origTranslationY + newTop - dragBaseTop

                // 预览落点框：使用拖动中 widget 实际显示的左上角作为锚点。
                val rawCell = cellLayout.unclampedPointToCell(
                    displayedViewLeftInContent(view),
                    displayedViewTopInContent(view)
                )

                // 限制单次拖拽最多扩展的行数，避免无限增加网格
                val maxOccupiedY = cellLayout.occupancy.getMaxOccupiedY()
                // 允许的最大底部行数（保证可以拖到最底下有内容的下方再空出几行）
                val allowedMaxY = maxOf(
                    WidgetCellLayout.DEFAULT_COUNT_Y, 
                    maxOccupiedY + 1 + WidgetCellLayout.TRAILING_EMPTY_ROWS + lp.cellVSpan
                )
                
                val targetY = rawCell.y.coerceAtMost(allowedMaxY - lp.cellVSpan)

                if (targetY + lp.cellVSpan + WidgetCellLayout.TRAILING_EMPTY_ROWS > cellLayout.countY) {
                    cellLayout.expandGridIfNeeded(
                        targetY + lp.cellVSpan + WidgetCellLayout.TRAILING_EMPTY_ROWS
                    )
                }
                val clampedCellX = rawCell.x.coerceIn(0, cellLayout.countX - lp.cellHSpan)
                val clampedCellY = targetY.coerceIn(0, cellLayout.countY - lp.cellVSpan)
                val previewRect = cellLayout.cellToRect(
                    clampedCellX, clampedCellY, lp.cellHSpan, lp.cellVSpan
                )
                cellLayout.previewRect = previewRect
                cellLayout.invalidate()

                return true
            }

            MotionEvent.ACTION_UP -> {
                finishDrag(event)
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                cancelDrag()
                return true
            }
        }
        return false
    }

    private fun finishDrag(event: MotionEvent) {
        val view = dragView ?: return
        val lp = view.layoutParams as WidgetCellLayoutParams

        val cell = cellLayout.unclampedPointToCell(
            displayedViewLeftInContent(view),
            displayedViewTopInContent(view)
        )
        
        val maxOccupiedY = cellLayout.occupancy.getMaxOccupiedY()
        val allowedMaxY = maxOf(
            WidgetCellLayout.DEFAULT_COUNT_Y, 
            maxOccupiedY + 1 + WidgetCellLayout.TRAILING_EMPTY_ROWS + lp.cellVSpan
        )
        
        val boundedTargetY = cell.y.coerceAtMost(allowedMaxY - lp.cellVSpan)

        if (boundedTargetY + lp.cellVSpan + WidgetCellLayout.TRAILING_EMPTY_ROWS > cellLayout.countY) {
            cellLayout.expandGridIfNeeded(
                boundedTargetY + lp.cellVSpan + WidgetCellLayout.TRAILING_EMPTY_ROWS
            )
        }
        val targetX = cell.x.coerceIn(0, cellLayout.countX - lp.cellHSpan)
        val targetY = boundedTargetY.coerceIn(0, cellLayout.countY - lp.cellVSpan)

        // Clear old occupancy
        cellLayout.occupancy.markCells(
            origCellX, origCellY, lp.cellHSpan, lp.cellVSpan, false
        )

        var placed = false

        if (cellLayout.occupancy.isRegionVacant(targetX, targetY, lp.cellHSpan, lp.cellVSpan)) {
            // Direct placement
            lp.cellX = targetX
            lp.cellY = targetY
            cellLayout.occupancy.markCells(targetX, targetY, lp.cellHSpan, lp.cellVSpan, true)
            placed = true
        } else {
            // Try push-down avoidance
            if (cellLayout.pushWidgetsDown(targetX, targetY, lp.cellHSpan, lp.cellVSpan)) {
                lp.cellX = targetX
                lp.cellY = targetY
                cellLayout.occupancy.markCells(
                    targetX, targetY, lp.cellHSpan, lp.cellVSpan, true
                )
                placed = true
            }
        }

        if (!placed) {
            // Revert to original position
            lp.cellX = origCellX
            lp.cellY = origCellY
            cellLayout.occupancy.markCells(
                origCellX, origCellY, lp.cellHSpan, lp.cellVSpan, true
            )
            Log.d(TAG, "Drag failed, reverting to ($origCellX, $origCellY)")
        } else {
            Log.d(TAG, "Widget moved to (${lp.cellX}, ${lp.cellY})")
            cellLayout.ensureTrailingEmptyRows()
            onWidgetChanged(view)
        }

        if (!placed) {
            cellLayout.ensureTrailingEmptyRows()
        }
        resetDragState(view)
        cellLayout.requestLayout()
    }

    private fun startResize(view: View) {
        resizeView = view
        val lp = view.layoutParams as WidgetCellLayoutParams
        resizeStartCellX = lp.cellX
        resizeStartCellY = lp.cellY
        resizeStartSpanX = lp.cellHSpan
        resizeStartSpanY = lp.cellVSpan
        view.bringToFront()
        view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        cellLayout.parent?.requestDisallowInterceptTouchEvent(true)
        getLauncherActivity()?.isWidgetDragging = true
    }

    private fun updateResize(event: MotionEvent) {
        val view = resizeView ?: return
        autoScrollParentIfNeeded(event)

        val x = eventContentX(event).toInt()
        val y = eventContentY(event).toInt()
        val cellSizeX = cellLayout.getCellWidth() + cellLayout.getBorderSpace()
        val cellSizeY = cellLayout.getCellHeight() + cellLayout.getBorderSpace()
        if (cellSizeX <= 0 || cellSizeY <= 0) return

        val spanX = ceil((x - cellLayout.cellToRect(resizeStartCellX, resizeStartCellY, 1, 1).left).toFloat() / cellSizeX)
            .toInt()
            .coerceIn(1, cellLayout.countX - resizeStartCellX)
        val spanY = ceil((y - cellLayout.cellToRect(resizeStartCellX, resizeStartCellY, 1, 1).top).toFloat() / cellSizeY)
            .toInt()
            .coerceAtLeast(1)

        cellLayout.resizeWidget(view, spanX, spanY)
    }

    private fun finishResize() {
        val view = resizeView ?: return
        resizeView = null
        onWidgetChanged(view)
        cellLayout.parent?.requestDisallowInterceptTouchEvent(false)
        getLauncherActivity()?.isWidgetDragging = cellLayout.isWidgetEditMode
    }

    private fun cancelResize() {
        val view = resizeView ?: return
        cellLayout.resizeWidget(view, resizeStartSpanX, resizeStartSpanY)
        resizeView = null
        cellLayout.parent?.requestDisallowInterceptTouchEvent(false)
        getLauncherActivity()?.isWidgetDragging = cellLayout.isWidgetEditMode
    }

    private fun cancelDrag() {
        val view = dragView
        if (view != null) {
            val lp = view.layoutParams as WidgetCellLayoutParams
            lp.cellX = origCellX
            lp.cellY = origCellY
        }
        resetDragState(view)
        cellLayout.requestLayout()
    }

    private fun resetDragState(view: View?) {
        cancelPendingPress()
        if (view != null) {
            view.elevation = origElevation
            view.alpha = 1f
            view.translationX = origTranslationX
            view.translationY = origTranslationY
        }
        isDragging = false
        dragView = null
        cellLayout.forceShowGrid = false
        cellLayout.previewRect = null
        cellLayout.invalidate()
        cellLayout.parent?.requestDisallowInterceptTouchEvent(false)
        getLauncherActivity()?.isWidgetDragging = false
    }
}
