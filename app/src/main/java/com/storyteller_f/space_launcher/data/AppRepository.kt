package com.storyteller_f.space_launcher.data

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.UserHandle
import android.os.UserManager
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.roundToInt

import android.content.ComponentName

data class AppItem(
    val label: String,
    val packageName: String,
    val componentName: ComponentName,
    val icon: Drawable,
    val user: UserHandle
)

class AppRepository(private val context: Context) {

    fun getInstalledApps(): List<AppItem> {
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
        val iconSize = (ICON_SIZE_DP * context.resources.displayMetrics.density).roundToInt()
        
        val appList = mutableListOf<AppItem>()
        
        val profiles = userManager.userProfiles
        for (user in profiles) {
            val apps = launcherApps.getActivityList(null, user)
            for (info in apps) {
                // Filter out self if needed, but usually LauncherApps doesn't include the launcher activity itself unless it declares a launcher category 
                // (which we do, so we might want to filter 'com.storyteller_f.space_launcher' if we don't want to see ourselves)
                if (info.applicationInfo.packageName == context.packageName) continue

                appList.add(
                    AppItem(
                        label = info.label.toString(),
                        packageName = info.applicationInfo.packageName,
                        componentName = info.componentName,
                        icon = info.getIcon(0).toFixedSizeBitmapDrawable(iconSize),
                        user = user
                    )
                )
            }
        }
        
        return appList.sortedBy { it.label.lowercase() }
    }

    private fun Drawable.toFixedSizeBitmapDrawable(size: Int): BitmapDrawable {
        val bitmap = toBitmap(size, size, Bitmap.Config.ARGB_8888)
        return BitmapDrawable(context.resources, bitmap).apply {
            setBounds(0, 0, size, size)
        }
    }

    companion object {
        private const val ICON_SIZE_DP = 48
    }
}
