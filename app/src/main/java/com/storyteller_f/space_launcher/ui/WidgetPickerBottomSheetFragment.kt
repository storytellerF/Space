package com.storyteller_f.space_launcher.ui

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.textfield.TextInputEditText
import com.storyteller_f.space_launcher.R

/**
 * 自定义 BottomSheet，用于选择要添加的 Widget。
 *
 * 用法：
 * ```
 * val picker = WidgetPickerBottomSheetFragment.newInstance(appWidgetId) { info, id ->
 *     // 用户选择了 widget，info 为 AppWidgetProviderInfo，id 为已分配的 appWidgetId
 * }
 * picker.show(supportFragmentManager, "widget_picker")
 * ```
 */
class WidgetPickerBottomSheetFragment : BottomSheetDialogFragment() {

    /** 回调：(appWidgetProviderInfo, appWidgetId) */
    var onWidgetSelected: ((AppWidgetProviderInfo, Int) -> Unit)? = null

    var onDismissListener: (() -> Unit)? = null

    /** 预先分配好的 appWidgetId，由调用方传入 */
    private var allocatedAppWidgetId: Int = -1

    private lateinit var adapter: WidgetPickerAdapter
    private var allWidgets: List<AppWidgetProviderInfo> = emptyList()

    companion object {
        private const val ARG_APP_WIDGET_ID = "arg_app_widget_id"

        fun newInstance(
            allocatedAppWidgetId: Int,
            onWidgetSelected: (AppWidgetProviderInfo, Int) -> Unit
        ): WidgetPickerBottomSheetFragment {
            return WidgetPickerBottomSheetFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_APP_WIDGET_ID, allocatedAppWidgetId)
                }
                this.onWidgetSelected = onWidgetSelected
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        onDismissListener?.invoke()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_widget_picker, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        allocatedAppWidgetId = arguments?.getInt(ARG_APP_WIDGET_ID, -1) ?: -1

        // 关闭按钮
        view.findViewById<View>(R.id.btn_close_widget_picker).setOnClickListener {
            dismiss()
        }

        // 获取所有可用 Widget
        val appWidgetManager = AppWidgetManager.getInstance(requireContext())
        allWidgets = appWidgetManager.installedProviders
            .sortedWith(compareBy(
                { getAppLabel(it) },
                { getWidgetLabel(it) }
            ))

        // 设置 RecyclerView
        val recyclerView = view.findViewById<RecyclerView>(R.id.rv_widget_list)
        adapter = WidgetPickerAdapter(allWidgets) { info ->
            onWidgetSelected?.invoke(info, allocatedAppWidgetId)
            dismiss()
        }
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        // 搜索框
        val searchInput = view.findViewById<TextInputEditText>(R.id.et_widget_search)
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterWidgets(s?.toString() ?: "")
            }
        })
    }

    private fun filterWidgets(query: String) {
        val filtered = if (query.isBlank()) {
            allWidgets
        } else {
            val lowerQuery = query.lowercase()
            allWidgets.filter { info ->
                getWidgetLabel(info).lowercase().contains(lowerQuery) ||
                        getAppLabel(info).lowercase().contains(lowerQuery)
            }
        }
        adapter.updateList(filtered)
    }

    private fun getWidgetLabel(info: AppWidgetProviderInfo): String {
        return try {
            info.loadLabel(requireContext().packageManager)
        } catch (e: Exception) {
            info.provider.className
        }
    }

    private fun getAppLabel(info: AppWidgetProviderInfo): String {
        return try {
            val appInfo = requireContext().packageManager.getApplicationInfo(
                info.provider.packageName,
                PackageManager.GET_META_DATA
            )
            requireContext().packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            info.provider.packageName
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Inner Adapter
    // ──────────────────────────────────────────────────────────────────────────

    inner class WidgetPickerAdapter(
        private var items: List<AppWidgetProviderInfo>,
        private val onClick: (AppWidgetProviderInfo) -> Unit
    ) : RecyclerView.Adapter<WidgetPickerAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.widget_icon)
            val label: TextView = view.findViewById(R.id.widget_label)
            val appName: TextView = view.findViewById(R.id.widget_app_name)
            val size: TextView = view.findViewById(R.id.widget_size)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_widget_picker, parent, false)
            return ViewHolder(v)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val info = items[position]
            val pm = requireContext().packageManager

            // 图标
            try {
                holder.icon.setImageDrawable(info.loadIcon(requireContext(), resources.displayMetrics.densityDpi))
            } catch (e: Exception) {
                holder.icon.setImageResource(android.R.drawable.sym_def_app_icon)
            }

            // Widget 名称
            holder.label.text = getWidgetLabel(info)

            // App 名称
            holder.appName.text = getAppLabel(info)

            // 最小尺寸
            holder.size.text = "${info.minWidth} × ${info.minHeight} dp"

            // 点击事件
            holder.itemView.setOnClickListener { onClick(info) }
        }

        fun updateList(newList: List<AppWidgetProviderInfo>) {
            items = newList
            notifyDataSetChanged()
        }
    }
}
