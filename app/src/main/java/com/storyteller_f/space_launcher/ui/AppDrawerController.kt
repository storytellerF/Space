package com.storyteller_f.space_launcher.ui

import android.content.Context
import android.content.res.Configuration
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.marginBottom
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.storyteller_f.space_launcher.R
import com.storyteller_f.space_launcher.data.AppRepository
import com.storyteller_f.space_launcher.data.AppItem
import com.storyteller_f.space_launcher.data.SettingsRepository
import com.storyteller_f.space_launcher.LauncherActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope

class AppDrawerController(
    private val activity: LauncherActivity,
    private val container: ViewGroup
) {

    private lateinit var settingsRepo: SettingsRepository
    private lateinit var appAdapter: AppAdapter
    private lateinit var allApps: List<AppItem>
    private var currentQuery: String = ""
    private var rootView: View? = null
    private var recyclerView: RecyclerView? = null

    fun attach() {
        val view = activity.layoutInflater.inflate(R.layout.fragment_app_drawer, container, false)
        rootView = view
        container.addView(view)

        settingsRepo = SettingsRepository(activity)
        val recyclerView = view.findViewById<RecyclerView>(R.id.app_recycler_view)
        this.recyclerView = recyclerView
        val searchInputLayout = view.findViewById<TextInputLayout>(R.id.search_input_layout)
        val searchEditText = view.findViewById<TextInputEditText>(R.id.search_edit_text)
        val baseSearchBottomMargin = searchInputLayout.marginBottom

        // 设置动态列数（区分横竖屏）
        val isLandscape = activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val columns = settingsRepo.getDrawerColumns(isLandscape)
        val layoutManager = recyclerView.layoutManager as GridLayoutManager
        layoutManager.spanCount = columns
        recyclerView.setHasFixedSize(true)
        recyclerView.itemAnimator = null
        recyclerView.setItemViewCacheSize(columns * 4)

        activity.lifecycleScope.launch(Dispatchers.IO) {
            allApps = AppRepository(activity).getInstalledApps()
            withContext(Dispatchers.Main) {
                if (rootView !== view) return@withContext
                appAdapter = AppAdapter(allApps) { app ->
                    val launcherApps = activity.getSystemService(Context.LAUNCHER_APPS_SERVICE) as android.content.pm.LauncherApps
                    launcherApps.startMainActivity(app.componentName, app.user, null, null)
                    // Close drawer logic will be handled by Activity or simple translation reset
                    activity.closeDrawer()
                }
                recyclerView.adapter = appAdapter
                appAdapter.filter(currentQuery)
            }
        }

        // 设置搜索监听
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentQuery = s?.toString().orEmpty()
                if (::appAdapter.isInitialized) {
                    appAdapter.filter(currentQuery)
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        // 处理 WindowInsets，确保输入框在软键盘上方
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val bottomInsets = insets.getInsets(WindowInsetsCompat.Type.ime() or WindowInsetsCompat.Type.systemBars())

            view.updatePadding(top = statusBarInsets.top)

            Log.i(TAG, "onViewCreated: ${bottomInsets.bottom}")
            // 设置搜索框底部 padding，使其在软键盘上方
            searchInputLayout.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = baseSearchBottomMargin + bottomInsets.bottom
            }
            insets
        }
        ViewCompat.setWindowInsetsAnimationCallback(view, object : WindowInsetsAnimationCompat.Callback(
            DISPATCH_MODE_CONTINUE_ON_SUBTREE
        ) {
            var bottomBeforeAnimation = 0
            var bottomAfterAnimation = 0
            override fun onProgress(
                insets: WindowInsetsCompat,
                runningAnimations: List<WindowInsetsAnimationCompat?>
            ): WindowInsetsCompat {
                val fl = (bottomAfterAnimation - bottomBeforeAnimation) * (runningAnimations.fraction() - 1) * -1
                searchInputLayout.translationY = fl
                return insets
            }

            override fun onEnd(animation: WindowInsetsAnimationCompat) {
                super.onEnd(animation)
                searchInputLayout.translationY = 0f
            }

            override fun onStart(
                animation: WindowInsetsAnimationCompat,
                bounds: WindowInsetsAnimationCompat.BoundsCompat
            ): WindowInsetsAnimationCompat.BoundsCompat {
                bottomAfterAnimation = searchInputLayout.marginBottom
                Log.i(TAG, "onStart: $bottomAfterAnimation $bottomBeforeAnimation")
                return super.onStart(animation, bounds)
            }

            override fun onPrepare(animation: WindowInsetsAnimationCompat) {
                super.onPrepare(animation)
                bottomBeforeAnimation = searchInputLayout.marginBottom
                Log.i(TAG, "onPrepare: $bottomAfterAnimation $bottomBeforeAnimation")
            }
        })
        ViewCompat.requestApplyInsets(view)
    }

    fun detach() {
        rootView?.let(container::removeView)
        rootView = null
        recyclerView = null
    }

    fun getRecyclerView(): RecyclerView? = recyclerView

    companion object {
        private const val TAG = "AppDrawerController"
    }
}

fun List<WindowInsetsAnimationCompat?>.fraction() = getOrNull(0)?.interpolatedFraction ?: 0f
