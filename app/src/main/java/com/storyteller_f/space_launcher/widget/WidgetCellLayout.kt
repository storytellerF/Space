package com.storyteller_f.space_launcher.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Rect
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import kotlin.math.roundToInt

/**
 * A cell-based grid ViewGroup for hosting app widgets.
 * Inspired by Launcher3's CellLayout.
 *
 * Each child is positioned by [WidgetCellLayoutParams] which specifies
 * cellX, cellY, cellHSpan, cellVSpan.
 */
class WidgetCellLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "WidgetCellLayout"
        const val DEFAULT_COUNT_X = 4
        const val DEFAULT_COUNT_Y = 6
        const val DEFAULT_BORDER_SPACE_DP = 4
        const val TRAILING_EMPTY_ROWS = 2
    }

    var countX: Int = DEFAULT_COUNT_X
        private set
    var countY: Int = DEFAULT_COUNT_Y
        private set

    private var cellWidth: Int = 0
    private var cellHeight: Int = 0
    private val borderSpacePx: Int
    private val resizeHandleSizePx: Int
    private val editButtonSizePx: Int

    var occupancy: GridOccupancy = GridOccupancy(countX, countY)
        private set

    var dragHelper: WidgetDragHelper? = null
    var onLayoutCompleted: (() -> Unit)? = null

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        return dragHelper?.onInterceptTouchEvent(ev) ?: super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return dragHelper?.onTouchEvent(event) ?: super.onTouchEvent(event)
    }

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        super.requestDisallowInterceptTouchEvent(disallowIntercept)
        if (disallowIntercept) {
            dragHelper?.cancelLongPress()
        }
    }

    // For drawing grid lines (debug / visual aid)
    var showGridLines: Boolean = false
    var forceShowGrid: Boolean = false
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(40, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    // For drawing coordinates text
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(128, 255, 255, 255)
        textAlign = Paint.Align.CENTER
    }

    // For showing occupied cell backgrounds (debug mode)
    var showOccupiedCellBackground: Boolean = false
    private val occupiedCellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(60, 255, 100, 100) // Red tint for occupied cells
        style = Paint.Style.FILL
    }
    private val vacantCellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(30, 100, 255, 100) // Green tint for vacant cells
        style = Paint.Style.FILL
    }

    // For drawing drop preview
    private val previewPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(80, 100, 180, 255)
        style = Paint.Style.FILL
    }
    var previewRect: Rect? = null

    var isWidgetEditMode: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            forceShowGrid = value
            invalidate()
        }
    private val resizeHandlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(230, 255, 255, 255)
        style = Paint.Style.FILL
    }
    private val resizeHandleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 56, 132, 255)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val closeButtonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(230, 255, 255, 255)
        style = Paint.Style.FILL
    }
    private val closeButtonStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 244, 67, 54)
        style = Paint.Style.STROKE
        strokeWidth = 2f
        strokeCap = Paint.Cap.ROUND
    }

    init {
        setWillNotDraw(false)
        val density = context.resources.displayMetrics.density
        borderSpacePx = (DEFAULT_BORDER_SPACE_DP * density).toInt()
        resizeHandleSizePx = (28 * density).roundToInt()
        editButtonSizePx = (28 * density).roundToInt()
        textPaint.textSize = 12 * density
    }

    /**
     * Update grid dimensions at runtime.
     * This clears the occupancy and requires all children to be re-added.
     */
    fun setGridSize(x: Int, y: Int) {
        if (x == countX && y == countY) return
        countX = x
        countY = y
        occupancy = GridOccupancy(countX, countY)
        requestLayout()
    }

    /**
     * Expand countY so that the grid can fit a widget at row [requiredMaxY].
     * Preserves existing occupancy data.
     */
    fun expandGridIfNeeded(requiredMaxY: Int) {
        if (requiredMaxY <= countY) return
        resizeGridRows(requiredMaxY)
    }

    /**
     * Keep blank cells after the bottom-most widget for easier scrolling and placement.
     */
    fun ensureTrailingEmptyRows() {
        val occupiedBottom = occupancy.getMaxOccupiedY() + 1
        val requiredRows = maxOf(DEFAULT_COUNT_Y, occupiedBottom + TRAILING_EMPTY_ROWS)
        if (requiredRows == countY) return
        resizeGridRows(requiredRows)
    }

    private fun resizeGridRows(newCountY: Int) {
        val newOccupancy = GridOccupancy(countX, newCountY)
        for (x in 0 until countX) {
            for (y in 0 until minOf(countY, newCountY)) {
                newOccupancy.cells[x][y] = occupancy.cells[x][y]
            }
        }
        countY = newCountY
        occupancy = newOccupancy
        requestLayout()
    }

    // ─── Measure & Layout ────────────────────────────────────────

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)

        val childWidth = widthSize - paddingLeft - paddingRight

        // cellWidth from available width; cellHeight = cellWidth (square cells)
        cellWidth = (childWidth - (countX - 1) * borderSpacePx) / countX
        cellHeight = cellWidth

        // Total height = rows * cellHeight + (rows-1) * border + padding
        val contentHeight = countY * cellHeight + (countY - 1) * borderSpacePx +
                paddingTop + paddingBottom

        // Measure each child using its cell span
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE) continue
            val lp = child.layoutParams as WidgetCellLayoutParams
            lp.setup(cellWidth, cellHeight, borderSpacePx, borderSpacePx)
            child.measure(
                MeasureSpec.makeMeasureSpec(lp.width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(lp.height, MeasureSpec.EXACTLY)
            )
        }

        setMeasuredDimension(widthSize, contentHeight)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE) continue
            val lp = child.layoutParams as WidgetCellLayoutParams

            val left = paddingLeft + lp.pixelX
            val top = paddingTop + lp.pixelY
            child.layout(left, top, left + lp.width, top + lp.height)
        }
        onLayoutCompleted?.invoke()
    }

    // ─── Drawing ─────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (showOccupiedCellBackground) {
            drawCellBackgrounds(canvas)
        } else if (showGridLines || forceShowGrid) {
            drawGrid(canvas)
        }

        // Draw drop preview
        previewRect?.let {
            canvas.drawRect(it, previewPaint)
        }

        if (isWidgetEditMode) {
            drawEditControls(canvas)
        }
    }

    private fun drawEditControls(canvas: Canvas) {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility != View.VISIBLE) continue
            val bounds = Rect()
            child.getHitRect(bounds)
            val closeButton = closeButtonRect(bounds)
            canvas.drawOval(
                closeButton.left.toFloat(),
                closeButton.top.toFloat(),
                closeButton.right.toFloat(),
                closeButton.bottom.toFloat(),
                closeButtonPaint
            )
            val inset = editButtonSizePx * 0.32f
            canvas.drawLine(
                closeButton.left + inset,
                closeButton.top + inset,
                closeButton.right - inset,
                closeButton.bottom - inset,
                closeButtonStrokePaint
            )
            canvas.drawLine(
                closeButton.right - inset,
                closeButton.top + inset,
                closeButton.left + inset,
                closeButton.bottom - inset,
                closeButtonStrokePaint
            )

            val handle = resizeHandleRect(bounds)
            canvas.drawRoundRect(
                handle.left.toFloat(),
                handle.top.toFloat(),
                handle.right.toFloat(),
                handle.bottom.toFloat(),
                8f,
                8f,
                resizeHandlePaint
            )
            canvas.drawRoundRect(
                handle.left.toFloat(),
                handle.top.toFloat(),
                handle.right.toFloat(),
                handle.bottom.toFloat(),
                8f,
                8f,
                resizeHandleStrokePaint
            )
        }
    }

    private fun drawCellBackgrounds(canvas: Canvas) {
        for (x in 0 until countX) {
            for (y in 0 until countY) {
                val rect = cellToRect(x, y, 1, 1)
                val paint = if (occupancy.cells[x][y]) occupiedCellPaint else vacantCellPaint
                canvas.drawRect(rect, paint)
            }
        }
        // Draw grid lines on top
        if (showGridLines || forceShowGrid) {
            drawGrid(canvas)
        }
    }

    private fun drawGrid(canvas: Canvas) {
        val textOffset = (textPaint.descent() + textPaint.ascent()) / 2
        for (x in 0 until countX) {
            for (y in 0 until countY) {
                val rect = cellToRect(x, y, 1, 1)
                canvas.drawRect(rect, gridPaint)
                canvas.drawText("($x, $y)", rect.centerX().toFloat(), rect.centerY().toFloat() - textOffset, textPaint)
            }
        }
    }

    // ─── Cell ↔ Pixel ────────────────────────────────────────────

    fun cellToRect(cellX: Int, cellY: Int, spanX: Int, spanY: Int): Rect {
        val left = paddingLeft + cellX * (cellWidth + borderSpacePx)
        val top = paddingTop + cellY * (cellHeight + borderSpacePx)
        val width = spanX * cellWidth + (spanX - 1) * borderSpacePx
        val height = spanY * cellHeight + (spanY - 1) * borderSpacePx
        return Rect(left, top, left + width, top + height)
    }

    fun pointToCell(pixelX: Int, pixelY: Int): Point {
        val x = ((pixelX - paddingLeft) / (cellWidth + borderSpacePx))
            .coerceIn(0, countX - 1)
        val y = ((pixelY - paddingTop) / (cellHeight + borderSpacePx))
            .coerceIn(0, countY - 1)
        return Point(x, y)
    }

    /**
     * Similar to pointToCell, but does not clamp the Y coordinate to countY.
     * Useful for detecting drags beyond the bottom of the grid to expand it.
     */
    fun unclampedPointToCell(pixelX: Int, pixelY: Int): Point {
        val x = ((pixelX - paddingLeft) / (cellWidth + borderSpacePx))
            .coerceIn(0, countX - 1)
        val y = maxOf(0, (pixelY - paddingTop) / (cellHeight + borderSpacePx))
        return Point(x, y)
    }

    fun getCellWidth(): Int = cellWidth
    fun getCellHeight(): Int = cellHeight
    fun getBorderSpace(): Int = borderSpacePx

    fun findCloseButtonChild(x: Int, y: Int): View? {
        if (!isWidgetEditMode) return null
        for (i in childCount - 1 downTo 0) {
            val child = getChildAt(i)
            if (child.visibility != View.VISIBLE) continue
            val bounds = Rect()
            child.getHitRect(bounds)
            if (closeButtonRect(bounds).contains(x, y)) {
                return child
            }
        }
        return null
    }

    fun findResizeHandleChild(x: Int, y: Int): View? {
        if (!isWidgetEditMode) return null
        for (i in childCount - 1 downTo 0) {
            val child = getChildAt(i)
            if (child.visibility != View.VISIBLE) continue
            val bounds = Rect()
            child.getHitRect(bounds)
            if (resizeHandleRect(bounds).contains(x, y)) {
                return child
            }
        }
        return null
    }

    fun resizeWidget(view: View, spanX: Int, spanY: Int): Boolean {
        val lp = view.layoutParams as? WidgetCellLayoutParams ?: return false
        val targetSpanX = spanX.coerceIn(1, countX - lp.cellX)
        val targetSpanY = spanY.coerceAtLeast(1)
        expandGridIfNeeded(lp.cellY + targetSpanY)

        occupancy.markCells(lp.cellX, lp.cellY, lp.cellHSpan, lp.cellVSpan, false)
        val canResize = occupancy.isRegionVacant(lp.cellX, lp.cellY, targetSpanX, targetSpanY)
        if (!canResize) {
            occupancy.markCells(lp.cellX, lp.cellY, lp.cellHSpan, lp.cellVSpan, true)
            return false
        }

        lp.cellHSpan = targetSpanX
        lp.cellVSpan = targetSpanY
        occupancy.markCells(lp.cellX, lp.cellY, lp.cellHSpan, lp.cellVSpan, true)
        ensureTrailingEmptyRows()
        requestLayout()
        invalidate()
        return true
    }

    private fun resizeHandleRect(widgetBounds: Rect): Rect {
        return Rect(
            widgetBounds.right - resizeHandleSizePx,
            widgetBounds.bottom - resizeHandleSizePx,
            widgetBounds.right,
            widgetBounds.bottom
        )
    }

    private fun closeButtonRect(widgetBounds: Rect): Rect {
        return Rect(
            widgetBounds.right - editButtonSizePx,
            widgetBounds.top,
            widgetBounds.right,
            widgetBounds.top + editButtonSizePx
        )
    }

    // ─── Widget Management ───────────────────────────────────────

    /**
     * Add a widget at the specified cell position.
     *
     * @return true if the widget was successfully placed
     */
    fun addWidget(view: View, cellX: Int, cellY: Int, spanX: Int, spanY: Int): Boolean {
        if (!occupancy.isRegionVacant(cellX, cellY, spanX, spanY)) {
            Log.w(TAG, "Cannot add widget: region ($cellX,$cellY ${spanX}x${spanY}) occupied")
            return false
        }
        val lp = WidgetCellLayoutParams(cellX, cellY, spanX, spanY)
        addView(view, lp)
        occupancy.markCells(cellX, cellY, spanX, spanY, true)
        ensureTrailingEmptyRows()
        return true
    }

    /**
     * Find a vacant cell and add the widget there.
     *
     * @return the cell position or null if no space is available
     */
    fun addWidgetAutoPlace(view: View, spanX: Int, spanY: Int): Point? {
        val cell = occupancy.findVacantCell(spanX, spanY) ?: return null
        if (addWidget(view, cell.x, cell.y, spanX, spanY)) {
            return cell
        }
        return null
    }

    fun removeWidget(view: View) {
        val lp = view.layoutParams as? WidgetCellLayoutParams ?: return
        occupancy.markCells(lp.cellX, lp.cellY, lp.cellHSpan, lp.cellVSpan, false)
        removeView(view)
        ensureTrailingEmptyRows()
    }

    /**
     * Move a widget to new cell coords, updating occupancy.
     */
    fun moveWidget(view: View, newCellX: Int, newCellY: Int): Boolean {
        val lp = view.layoutParams as? WidgetCellLayoutParams ?: return false

        // Clear old occupancy
        occupancy.markCells(lp.cellX, lp.cellY, lp.cellHSpan, lp.cellVSpan, false)

        // Check if new position is valid
        if (!occupancy.isRegionVacant(newCellX, newCellY, lp.cellHSpan, lp.cellVSpan)) {
            // Restore old occupancy
            occupancy.markCells(lp.cellX, lp.cellY, lp.cellHSpan, lp.cellVSpan, true)
            return false
        }

        lp.cellX = newCellX
        lp.cellY = newCellY
        occupancy.markCells(newCellX, newCellY, lp.cellHSpan, lp.cellVSpan, true)
        ensureTrailingEmptyRows()
        requestLayout()
        return true
    }

    /**
     * Try to push occupying widgets down to make room at (targetX, targetY, spanX, spanY).
     *
     * @return true if the region was successfully cleared
     */
    fun pushWidgetsDown(targetX: Int, targetY: Int, spanX: Int, spanY: Int): Boolean {
        // Collect views that overlap with the target region
        val overlapping = mutableListOf<View>()
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val lp = child.layoutParams as? WidgetCellLayoutParams ?: continue
            if (rectsOverlap(
                    lp.cellX, lp.cellY, lp.cellHSpan, lp.cellVSpan,
                    targetX, targetY, spanX, spanY
                )
            ) {
                overlapping.add(child)
            }
        }

        if (overlapping.isEmpty()) return true

        // Sort by cellY ascending so we push top ones first
        overlapping.sortBy { (it.layoutParams as WidgetCellLayoutParams).cellY }

        // Try to push each one down
        for (child in overlapping) {
            val lp = child.layoutParams as WidgetCellLayoutParams
            val pushToY = targetY + spanY
            // Auto-expand grid if pushing would exceed bounds
            expandGridIfNeeded(pushToY + lp.cellVSpan)

            // Temporarily clear the child from occupancy
            occupancy.markCells(lp.cellX, lp.cellY, lp.cellHSpan, lp.cellVSpan, false)

            // Recursively check if that spot is free (may need to push further)
            if (!occupancy.isRegionVacant(lp.cellX, pushToY, lp.cellHSpan, lp.cellVSpan)) {
                // Try pushing widgets at that new location down too
                if (!pushWidgetsDown(lp.cellX, pushToY, lp.cellHSpan, lp.cellVSpan)) {
                    // Can't push, restore and fail
                    occupancy.markCells(lp.cellX, lp.cellY, lp.cellHSpan, lp.cellVSpan, true)
                    return false
                }
            }

            // Move the widget
            lp.cellY = pushToY
            occupancy.markCells(lp.cellX, pushToY, lp.cellHSpan, lp.cellVSpan, true)
        }

        return true
    }

    /**
     * Rebuild occupancy from current children (useful after deserialization).
     */
    fun rebuildOccupancy() {
        occupancy.clear()
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val lp = child.layoutParams as? WidgetCellLayoutParams ?: continue
            occupancy.markCells(lp.cellX, lp.cellY, lp.cellHSpan, lp.cellVSpan, true)
        }
    }

    /**
     * Get all widget cell info for persistence.
     */
    fun getAllWidgetInfos(idExtractor: (View) -> Int): List<WidgetInfo> {
        val list = mutableListOf<WidgetInfo>()
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val lp = child.layoutParams as? WidgetCellLayoutParams ?: continue
            list.add(
                WidgetInfo(
                    appWidgetId = idExtractor(child),
                    cellX = lp.cellX,
                    cellY = lp.cellY,
                    spanX = lp.cellHSpan,
                    spanY = lp.cellVSpan
                )
            )
        }
        return list
    }

    // ─── Layout Params ───────────────────────────────────────────

    override fun generateDefaultLayoutParams(): LayoutParams =
        WidgetCellLayoutParams(0, 0, 1, 1)

    override fun generateLayoutParams(attrs: AttributeSet?): LayoutParams =
        WidgetCellLayoutParams(0, 0, 1, 1)

    override fun checkLayoutParams(p: LayoutParams?): Boolean =
        p is WidgetCellLayoutParams

    override fun generateLayoutParams(p: LayoutParams): LayoutParams =
        WidgetCellLayoutParams(p)

    // ─── Helpers ─────────────────────────────────────────────────

    private fun rectsOverlap(
        x1: Int, y1: Int, w1: Int, h1: Int,
        x2: Int, y2: Int, w2: Int, h2: Int
    ): Boolean {
        return x1 < x2 + w2 && x1 + w1 > x2 && y1 < y2 + h2 && y1 + h1 > y2
    }
}
