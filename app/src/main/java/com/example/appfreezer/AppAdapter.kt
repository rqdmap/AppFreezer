package com.example.appfreezer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.RecyclerView

class AppAdapter(
    private val appList: List<AppInfo>,
    private val favoriteApps: Set<String>,
    private val onSwitchChanged: (AppInfo, Boolean) -> Unit,
    private val onAppClick: (AppInfo) -> Unit,
    private val onFavoriteToggle: (AppInfo) -> Unit,
    private val onShowStatus: (String) -> Unit // 新增：状态栏提示回调
) : RecyclerView.Adapter<AppAdapter.AppViewHolder>() {

    class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivAppIcon: ImageView = itemView.findViewById(R.id.ivAppIcon)
        val tvAppName: TextView = itemView.findViewById(R.id.tvAppName)
        val tvPackageName: TextView = itemView.findViewById(R.id.tvPackageName)
        val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        val switchEnable: SwitchCompat = itemView.findViewById(R.id.switchEnable)
        val ivFavorite: ImageView = itemView.findViewById(R.id.ivFavorite)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val appInfo = appList[position]
        val isFavorite = favoriteApps.contains(appInfo.packageName)

        holder.tvAppName.text = appInfo.name
        holder.tvPackageName.text = appInfo.packageName

        // 设置收藏图标
        holder.ivFavorite.setImageResource(
            if (isFavorite) android.R.drawable.btn_star_big_on
            else android.R.drawable.btn_star_big_off
        )

        // 设置状态文本、颜色和可点击性
        if (appInfo.isEnabled) {
            holder.tvStatus.text = "正常运行"
            holder.tvStatus.setTextColor(0xFF4CAF50.toInt()) // 绿色
            holder.ivAppIcon.alpha = 1.0f

            // 非冻结应用可以点击启动
            holder.itemView.isClickable = true
            holder.itemView.background = android.graphics.drawable.ColorDrawable(0x08000000) // 轻微背景色表示可点击
        } else {
            holder.tvStatus.text = "已冻结"
            holder.tvStatus.setTextColor(0xFFFF9800.toInt()) // 橙色
            holder.ivAppIcon.alpha = 0.4f // 明显变灰

            // 冻结应用不可点击启动
            holder.itemView.isClickable = false
            holder.itemView.background = android.graphics.drawable.ColorDrawable(0x00000000) // 透明背景
        }

        // 设置开关状态
        holder.switchEnable.setOnCheckedChangeListener(null)
        holder.switchEnable.isChecked = appInfo.isEnabled

        // 设置开关监听器
        holder.switchEnable.setOnCheckedChangeListener { _, isChecked ->
            if (holder.adapterPosition != RecyclerView.NO_POSITION) {
                onSwitchChanged(appInfo, isChecked)
            }
        }

        // 设置应用图标
        if (appInfo.icon != null) {
            holder.ivAppIcon.setImageDrawable(appInfo.icon)
        } else {
            holder.ivAppIcon.setImageResource(android.R.drawable.ic_menu_info_details)
        }

        // 单击事件 - 启动应用（仅非冻结应用）
        holder.itemView.setOnClickListener {
            if (appInfo.isEnabled) {
                onAppClick(appInfo)
            } else {
                // 冻结应用点击提示 - 使用状态栏提示而不是Toast
                onShowStatus("请先解冻 ${appInfo.name} 才能启动")
            }
        }

        // 长按事件 - 收藏/取消收藏
        holder.itemView.setOnLongClickListener {
            onFavoriteToggle(appInfo)
            true
        }

        // 收藏图标点击事件
        holder.ivFavorite.setOnClickListener {
            onFavoriteToggle(appInfo)
        }
    }

    override fun getItemCount(): Int = appList.size
}