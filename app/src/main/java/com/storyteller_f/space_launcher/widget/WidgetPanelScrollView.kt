package com.storyteller_f.space_launcher.widget

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.ScrollView
import kotlin.math.abs

/**
 * Lets scrollable content inside hosted widgets consume vertical gestures before
 * the widget panel itself starts scrolling.
 */
class WidgetPanelScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ScrollView(context, attrs, defStyleAttr) {

    private val hitRect = Rect()
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var activeNestedScrollTarget: View? = null
    private var lastRawY = 0f

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        val superIntercepted = super.onInterceptTouchEvent(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activeNestedScrollTarget = findScrollableChildUnder(this, ev.rawX.toInt(), ev.rawY.toInt())
                lastRawY = ev.rawY
            }
            MotionEvent.ACTION_MOVE -> {
                val target = activeNestedScrollTarget
                val dy = ev.rawY - lastRawY
                if (target != null && abs(dy) > touchSlop) {
                    if (canTargetScrollWithFinger(target, dy)) {
                        lastRawY = ev.rawY
                        return false
                    }
                    activeNestedScrollTarget = null
                    return superIntercepted
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activeNestedScrollTarget = null
            }
        }

        if (activeNestedScrollTarget != null) {
            return false
        }

        return superIntercepted
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
            activeNestedScrollTarget = null
        }
        return super.onTouchEvent(ev)
    }

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        // When a child (e.g. drag helper) takes over touch handling, clear the nested
        // scroll target so the scroll view fully relinquishes interception.
        if (disallowIntercept) {
            activeNestedScrollTarget = null
        }
        super.requestDisallowInterceptTouchEvent(disallowIntercept)
    }

    private fun canTargetScrollWithFinger(target: View, fingerDy: Float): Boolean {
        return if (fingerDy > 0f) {
            target.canScrollVertically(-1)
        } else {
            target.canScrollVertically(1)
        }
    }

    private fun findScrollableChildUnder(view: View, rawX: Int, rawY: Int): View? {
        if (!isPointInside(view, rawX, rawY)) return null

        if (view is ViewGroup) {
            for (i in view.childCount - 1 downTo 0) {
                findScrollableChildUnder(view.getChildAt(i), rawX, rawY)?.let {
                    return it
                }
            }
        }

        if (view !== this && (view.canScrollVertically(-1) || view.canScrollVertically(1))) {
            return view
        }

        return null
    }

    private fun isPointInside(view: View, rawX: Int, rawY: Int): Boolean {
        if (view.visibility != View.VISIBLE) return false
        return view.getGlobalVisibleRect(hitRect) && hitRect.contains(rawX, rawY)
    }
}
