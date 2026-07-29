package com.storyteller_f.space_launcher.testwidgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews

abstract class BaseTestWidgetProvider(
    private val title: String,
    private val detail: String,
    private val backgroundColorRes: Int
) : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { appWidgetId ->
            update(context, appWidgetManager, appWidgetId, detail)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        val width = newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val height = newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        update(context, appWidgetManager, appWidgetId, "options ${width}x${height} dp")
    }

    fun update(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        detailText: String
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_test_card).apply {
            setInt(R.id.widget_root, "setBackgroundColor", context.getColor(backgroundColorRes))
            setTextViewText(R.id.widget_title, title)
            setTextViewText(R.id.widget_detail, detailText)
        }
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}

class SimpleWidgetProvider : BaseTestWidgetProvider(
    title = "Simple",
    detail = "No configuration",
    backgroundColorRes = R.color.widget_simple_bg
)

class SmallWidgetProvider : BaseTestWidgetProvider(
    title = "Small",
    detail = "Small min size",
    backgroundColorRes = R.color.widget_small_bg
)

class LargeWidgetProvider : BaseTestWidgetProvider(
    title = "Large",
    detail = "Large min size",
    backgroundColorRes = R.color.widget_large_bg
)

class ConfigurableWidgetProvider : BaseTestWidgetProvider(
    title = "Configured",
    detail = "Configuration completed",
    backgroundColorRes = R.color.widget_configurable_bg
)

class ResizeAwareWidgetProvider : BaseTestWidgetProvider(
    title = "Resize Aware",
    detail = "Waiting for options",
    backgroundColorRes = R.color.widget_resize_bg
)

class ConfigActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        setContentView(R.layout.activity_config)

        findViewById<android.view.View>(R.id.config_done).setOnClickListener {
            val appWidgetId = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                ConfigurableWidgetProvider().update(
                    this,
                    AppWidgetManager.getInstance(this),
                    appWidgetId,
                    "Configuration completed"
                )
            }
            setResult(
                RESULT_OK,
                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            )
            finish()
        }
    }
}
