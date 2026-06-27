package com.storyteller_f.space_launcher.ui

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import com.storyteller_f.space_launcher.R
import com.storyteller_f.space_launcher.data.SettingsRepository

class SettingsBottomSheetFragment : BottomSheetDialogFragment() {

    private lateinit var settingsRepo: SettingsRepository
    private lateinit var sliderDrawerColumns: Slider
    private lateinit var sliderWidgetColumns: Slider
    private lateinit var tvDrawerColumnsValue: TextView
    private lateinit var tvWidgetColumnsValue: TextView
    private lateinit var tvWidgetWarning: TextView
    private lateinit var switchShowCellBackground: SwitchMaterial
    private lateinit var switchShowGridLines: SwitchMaterial

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        settingsRepo = SettingsRepository(requireContext())

        // 判断当前是否横屏
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val orientationLabel = if (isLandscape) "横屏" else "竖屏"

        // 初始化视图
        sliderDrawerColumns = view.findViewById(R.id.slider_drawer_columns)
        sliderWidgetColumns = view.findViewById(R.id.slider_widget_columns)
        tvDrawerColumnsValue = view.findViewById(R.id.tv_drawer_columns_value)
        tvWidgetColumnsValue = view.findViewById(R.id.tv_widget_columns_value)
        tvWidgetWarning = view.findViewById(R.id.tv_widget_warning)
        switchShowCellBackground = view.findViewById(R.id.switch_show_cell_background)
        switchShowGridLines = view.findViewById(R.id.switch_show_grid_lines)

        // 若布局中存在专用 label，则更新提示文字（可选 ID，兼容旧布局）
        view.findViewById<TextView>(R.id.tv_drawer_columns_label)?.text =
            "抽屉列数（当前：$orientationLabel）"
        view.findViewById<TextView>(R.id.tv_widget_columns_label)?.text =
            "桌面列数（当前：$orientationLabel）"

        // 关闭按钮
        view.findViewById<View>(R.id.btn_close_settings).setOnClickListener {
            dismiss()
        }

        // 设置初始值（按当前方向）
        val currentDrawerColumns = settingsRepo.getDrawerColumns(isLandscape)
        val currentWidgetColumns = settingsRepo.getWidgetColumns(isLandscape)
        val showCellBackground = settingsRepo.isShowCellBackground()
        val showGridLines = settingsRepo.isShowGridLines()

        sliderDrawerColumns.value = currentDrawerColumns.toFloat()
        sliderWidgetColumns.value = currentWidgetColumns.toFloat()
        switchShowCellBackground.isChecked = showCellBackground
        switchShowGridLines.isChecked = showGridLines

        updateDrawerColumnsText(currentDrawerColumns, orientationLabel)
        updateWidgetColumnsText(currentWidgetColumns, orientationLabel)

        // 设置监听器
        sliderDrawerColumns.addOnChangeListener { _, value, _ ->
            val columns = value.toInt()
            updateDrawerColumnsText(columns, orientationLabel)
            settingsRepo.setDrawerColumns(columns, isLandscape)
            Toast.makeText(
                requireContext(),
                "抽屉列数（$orientationLabel）已更改为 $columns",
                Toast.LENGTH_SHORT
            ).show()
        }

        sliderWidgetColumns.addOnChangeListener { _, value, _ ->
            val columns = value.toInt()
            updateWidgetColumnsText(columns, orientationLabel)

            // 如果列数发生变化，显示警告
            if (columns != currentWidgetColumns) {
                tvWidgetWarning.visibility = View.VISIBLE
                showWidgetColumnChangeWarning(columns, isLandscape, orientationLabel)
            } else {
                tvWidgetWarning.visibility = View.GONE
            }

            settingsRepo.setWidgetColumns(columns, isLandscape)
        }

        // 重置按钮
        view.findViewById<View>(R.id.btn_reset_defaults).setOnClickListener {
            showResetConfirmationDialog(isLandscape, orientationLabel)
        }

        // Debug 模式开关
        switchShowCellBackground.setOnCheckedChangeListener { _, isChecked ->
            settingsRepo.setShowCellBackground(isChecked)
            sendRefreshBroadcast()
        }

        switchShowGridLines.setOnCheckedChangeListener { _, isChecked ->
            settingsRepo.setShowGridLines(isChecked)
            sendRefreshBroadcast()
        }
    }

    private fun updateDrawerColumnsText(columns: Int, orientationLabel: String) {
        tvDrawerColumnsValue.text = "当前列数（$orientationLabel）: $columns"
    }

    private fun updateWidgetColumnsText(columns: Int, orientationLabel: String) {
        tvWidgetColumnsValue.text = "当前列数（$orientationLabel）: $columns"
    }

    private fun showWidgetColumnChangeWarning(
        newColumns: Int,
        isLandscape: Boolean,
        orientationLabel: String
    ) {
        AlertDialog.Builder(requireContext())
            .setTitle("列数更改提醒")
            .setMessage(
                "您已将桌面列数（$orientationLabel）更改为 $newColumns 列。\n\n" +
                        "更改列数将会清除当前方向布局的小部件，您需要重新添加它们。\n\n是否继续？"
            )
            .setPositiveButton("确认更改") { _, _ ->
                settingsRepo.setWidgetColumns(newColumns, isLandscape)
                sendRefreshBroadcast()
                Toast.makeText(requireContext(), "列数已更改，请重新添加小部件", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("取消") { _, _ ->
                sliderWidgetColumns.value = settingsRepo.getWidgetColumns(isLandscape).toFloat()
                updateWidgetColumnsText(settingsRepo.getWidgetColumns(isLandscape), orientationLabel)
                tvWidgetWarning.visibility = View.GONE
            }
            .setCancelable(false)
            .show()
    }

    private fun showResetConfirmationDialog(isLandscape: Boolean, orientationLabel: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("恢复默认设置")
            .setMessage(
                "确定要恢复所有设置为默认值吗？\n\n" +
                        "抽屉列数 竖屏=${SettingsRepository.DEFAULT_DRAWER_COLUMNS}，" +
                        "横屏=${SettingsRepository.DEFAULT_DRAWER_COLUMNS_LAND}；\n" +
                        "桌面列数 竖屏=${SettingsRepository.DEFAULT_WIDGET_COLUMNS}，" +
                        "横屏=${SettingsRepository.DEFAULT_WIDGET_COLUMNS_LAND}。"
            )
            .setPositiveButton("恢复默认") { _, _ ->
                settingsRepo.resetToDefaults()

                val defaultDrawer = settingsRepo.getDrawerColumns(isLandscape)
                val defaultWidget = settingsRepo.getWidgetColumns(isLandscape)
                sliderDrawerColumns.value = defaultDrawer.toFloat()
                sliderWidgetColumns.value = defaultWidget.toFloat()
                updateDrawerColumnsText(defaultDrawer, orientationLabel)
                updateWidgetColumnsText(defaultWidget, orientationLabel)
                tvWidgetWarning.visibility = View.GONE

                sendRefreshBroadcast()
                Toast.makeText(requireContext(), "已恢复默认设置", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /** 发送刷新广播通知其他组件 */
    private fun sendRefreshBroadcast() {
        val intent = android.content.Intent("com.storyteller_f.space_launcher.SETTINGS_CHANGED")
        intent.setPackage(requireContext().packageName)
        requireContext().sendBroadcast(intent)
    }
}
