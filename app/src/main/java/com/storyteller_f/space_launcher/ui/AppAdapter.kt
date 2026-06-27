package com.storyteller_f.space_launcher.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.storyteller_f.space_launcher.R
import com.storyteller_f.space_launcher.data.AppItem

class AppAdapter(private var apps: List<AppItem>, private val onItemClick: (AppItem) -> Unit) :
    RecyclerView.Adapter<AppAdapter.AppViewHolder>() {

    private var filteredApps: List<AppItem> = apps

    init {
        setHasStableIds(true)
    }

    class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.app_icon)
        val label: TextView = view.findViewById(R.id.app_label)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val app = filteredApps[position]
        holder.label.text = app.label
        holder.icon.setImageDrawable(app.icon)
        holder.itemView.setOnClickListener { onItemClick(app) }
    }

    override fun getItemCount() = filteredApps.size

    override fun getItemId(position: Int): Long {
        val app = filteredApps[position]
        return "${app.user.hashCode()}:${app.componentName.flattenToShortString()}".hashCode().toLong()
    }

    fun filter(query: String) {
        filteredApps = if (query.isBlank()) {
            apps
        } else {
            apps.filter { app ->
                app.label.contains(query, ignoreCase = true)
            }
        }
        notifyDataSetChanged()
    }

    fun updateApps(newApps: List<AppItem>) {
        apps = newApps
        filteredApps = newApps
        notifyDataSetChanged()
    }
}
