package com.example.appfreezer

import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

data class AppInfo(
    val name: String,
    val packageName: String,
    val icon: android.graphics.drawable.Drawable?,
    var isEnabled: Boolean = true
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AppInfo
        return packageName == other.packageName
    }

    override fun hashCode(): Int {
        return packageName.hashCode()
    }
}

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvAppCount: TextView
    private lateinit var appAdapter: AppAdapter
    private lateinit var btnShowAll: android.widget.Button
    private lateinit var btnShowFavorites: android.widget.Button
    private lateinit var sharedPrefs: SharedPreferences

    // 状态栏提示管理
    private val statusHandler = Handler(Looper.getMainLooper())
    private var statusRunnable: Runnable? = null

    private val allAppList = mutableListOf<AppInfo>()
    private val filteredAppList = mutableListOf<AppInfo>()
    private val favoriteApps = mutableSetOf<String>()
    private var hasRootAccess = false

    enum class FilterState { ALL, FAVORITES }
    private var currentFilter = FilterState.ALL

    /**
     * 在状态栏显示提示信息，2秒后恢复正常
     */
    private fun showStatus(message: String) {
        // 取消之前的定时任务
        statusRunnable?.let { statusHandler.removeCallbacks(it) }

        // 立即显示消息
        tvAppCount.text = "💬 $message"
        tvAppCount.setTextColor(0xFF2196F3.toInt()) // 蓝色

        // 2秒后恢复正常显示
        statusRunnable = Runnable {
            updateUI()
            tvAppCount.setTextColor(0xFF000000.toInt()) // 恢复黑色
        }
        statusHandler.postDelayed(statusRunnable!!, 2000)
    }

    /**
     * 显示错误信息，3秒后恢复
     */
    private fun showError(message: String) {
        statusRunnable?.let { statusHandler.removeCallbacks(it) }

        tvAppCount.text = "❌ $message"
        tvAppCount.setTextColor(0xFFFF5722.toInt()) // 红色

        statusRunnable = Runnable {
            updateUI()
            tvAppCount.setTextColor(0xFF000000.toInt())
        }
        statusHandler.postDelayed(statusRunnable!!, 3000)
    }

    /**
     * 显示警告信息，2秒后恢复
     */
    private fun showWarning(message: String) {
        statusRunnable?.let { statusHandler.removeCallbacks(it) }

        tvAppCount.text = "⚠️ $message"
        tvAppCount.setTextColor(0xFFFF9800.toInt()) // 橙色

        statusRunnable = Runnable {
            updateUI()
            tvAppCount.setTextColor(0xFF000000.toInt())
        }
        statusHandler.postDelayed(statusRunnable!!, 2000)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initPreferences()
        initViews()
        checkRootAccess()
        loadUserApps()
    }

    private fun initPreferences() {
        sharedPrefs = getSharedPreferences("app_freezer_prefs", MODE_PRIVATE)
        loadFavorites()
    }

    private fun loadFavorites() {
        val favoritesString = sharedPrefs.getString("favorite_apps", "") ?: ""
        favoriteApps.clear()
        if (favoritesString.isNotEmpty()) {
            favoriteApps.addAll(favoritesString.split(","))
        }
    }

    private fun saveFavorites() {
        sharedPrefs.edit()
            .putString("favorite_apps", favoriteApps.joinToString(","))
            .apply()
    }

    private fun initViews() {
        recyclerView = findViewById(R.id.recyclerView)
        tvAppCount = findViewById(R.id.tvAppCount)
        btnShowAll = findViewById(R.id.btnShowAll)
        btnShowFavorites = findViewById(R.id.btnShowFavorites)

        appAdapter = AppAdapter(
            appList = filteredAppList,
            favoriteApps = favoriteApps,
            onSwitchChanged = { appInfo, isEnabled ->
                handleAppToggle(appInfo, isEnabled)
            },
            onAppClick = { appInfo ->
                launchApp(appInfo)
            },
            onFavoriteToggle = { appInfo ->
                toggleFavorite(appInfo)
            },
            onShowStatus = { message ->
                showWarning(message) // 冻结应用点击提示
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = appAdapter

        // 筛选按钮点击事件
        btnShowAll.setOnClickListener { setFilter(FilterState.ALL) }
        btnShowFavorites.setOnClickListener { setFilter(FilterState.FAVORITES) }

        // 初始状态设置为显示全部
        setFilter(FilterState.ALL)
    }

    private fun checkRootAccess() {
        Thread {
            hasRootAccess = RootUtils.checkRootAccess()
            runOnUiThread {
                if (!hasRootAccess) {
                    showError("Root权限被拒绝！请在Root管理器中授权此应用")

                    val builder = androidx.appcompat.app.AlertDialog.Builder(this)
                    builder.setTitle("需要Root权限")
                    builder.setMessage("应用需要Root权限才能冻结其他应用。\n\n请确保：\n1. 手机已获取Root权限\n2. 在Root管理器中授权此应用\n3. 点击'重试'再次尝试")
                    builder.setPositiveButton("重试") { _, _ -> checkRootAccess() }
                    builder.setNegativeButton("取消", null)
                    builder.show()
                } else {
                    showStatus("Root权限获取成功！")
                }
            }
        }.start()
    }

    private fun setFilter(filter: FilterState) {
        currentFilter = filter

        // 重置按钮样式
        val defaultColor = 0xFFE0E0E0.toInt()
        val defaultTextColor = 0xFF000000.toInt()

        btnShowAll.setBackgroundColor(defaultColor)
        btnShowAll.setTextColor(defaultTextColor)
        btnShowFavorites.setBackgroundColor(defaultColor)
        btnShowFavorites.setTextColor(defaultTextColor)

        // 设置选中按钮样式
        when (filter) {
            FilterState.ALL -> {
                btnShowAll.setBackgroundColor(0xFF2196F3.toInt())
                btnShowAll.setTextColor(0xFFFFFFFF.toInt())
            }
            FilterState.FAVORITES -> {
                btnShowFavorites.setBackgroundColor(0xFFFF9800.toInt())
                btnShowFavorites.setTextColor(0xFFFFFFFF.toInt())
            }
        }

        applyCurrentFilter()
    }

    private fun applyCurrentFilter() {
        filteredAppList.clear()

        when (currentFilter) {
            FilterState.ALL -> filteredAppList.addAll(allAppList)
            FilterState.FAVORITES -> {
                filteredAppList.addAll(allAppList.filter { favoriteApps.contains(it.packageName) })
            }
        }

        updateUI()
        appAdapter.notifyDataSetChanged()
    }

    private fun updateUI() {
        val totalApps = allAppList.size
        val enabledCount = allAppList.count { it.isEnabled }
        val disabledCount = totalApps - enabledCount
        val currentCount = filteredAppList.size
        val favoriteCount = favoriteApps.size

        val filterText = when (currentFilter) {
            FilterState.ALL -> "全部"
            FilterState.FAVORITES -> "收藏"
        }

        tvAppCount.text = "显示 $currentCount 个$filterText 应用 (总计: ${enabledCount}正常 / ${disabledCount}冻结 / ${favoriteCount}收藏)"
    }

    private fun handleAppToggle(appInfo: AppInfo, isEnabled: Boolean) {
        if (!hasRootAccess) {
            showError("没有Root权限，无法执行操作")
            val position = filteredAppList.indexOf(appInfo)
            if (position != -1) {
                appAdapter.notifyItemChanged(position)
            }
            return
        }

        Thread {
            val result = if (isEnabled) {
                RootUtils.unfreezeApp(appInfo.packageName)
            } else {
                RootUtils.freezeApp(appInfo.packageName)
            }

            runOnUiThread {
                if (result.first) {
                    val mainPosition = allAppList.indexOf(appInfo)
                    if (mainPosition != -1) {
                        allAppList[mainPosition].isEnabled = isEnabled
                    }

                    applyCurrentFilter()

                    // 使用状态栏提示
                    showStatus("${appInfo.name} ${if (isEnabled) "已解除冻结" else "已冻结"}")
                } else {
                    showError("操作失败: ${result.second}")
                    val position = filteredAppList.indexOf(appInfo)
                    if (position != -1) {
                        appAdapter.notifyItemChanged(position)
                    }
                }
            }
        }.start()
    }

    private fun launchApp(appInfo: AppInfo) {
        try {
            val intent = packageManager.getLaunchIntentForPackage(appInfo.packageName)
            if (intent != null) {
                startActivity(intent)
                showStatus("正在启动 ${appInfo.name}")
            } else {
                showWarning("${appInfo.name} 无法启动")
            }
        } catch (e: Exception) {
            showError("启动失败: ${e.message}")
        }
    }

    private fun toggleFavorite(appInfo: AppInfo) {
        if (favoriteApps.contains(appInfo.packageName)) {
            favoriteApps.remove(appInfo.packageName)
            showStatus("${appInfo.name} 已从收藏中移除")
        } else {
            favoriteApps.add(appInfo.packageName)
            showStatus("${appInfo.name} 已添加到收藏")
        }

        saveFavorites()
        applyCurrentFilter()
    }

    private fun showAppDetails(appInfo: AppInfo) {
        if (!hasRootAccess) {
            showError("需要Root权限才能查看详细状态")
            return
        }

        Thread {
            val permissionStatus = RootUtils.getAppPermissionStatus(appInfo.packageName)

            runOnUiThread {
                val builder = androidx.appcompat.app.AlertDialog.Builder(this)
                builder.setTitle("${appInfo.name} 详细信息")

                val statusText = StringBuilder()
                statusText.append("包名: ${appInfo.packageName}\n")
                statusText.append("收藏状态: ${if (favoriteApps.contains(appInfo.packageName)) "已收藏 ⭐" else "未收藏"}\n\n")
                statusText.append("系统状态:\n")

                permissionStatus.forEach { (key, value) ->
                    statusText.append("$key: $value\n")
                }

                builder.setMessage(statusText.toString())
                builder.setPositiveButton("确定", null)

                if (!appInfo.isEnabled) {
                    builder.setNeutralButton("强制停止") { _, _ ->
                        Thread {
                            RootUtils.forceStopApp(appInfo.packageName)
                            runOnUiThread {
                                showStatus("${appInfo.name} 已强制停止")
                            }
                        }.start()
                    }
                }

                builder.show()
            }
        }.start()
    }

    private fun loadUserApps() {
        Thread {
            val packageManager = packageManager
            allAppList.clear()

            if (hasRootAccess) {
                loadAppsWithRoot()
            } else {
                loadAppsWithoutRoot()
            }

            allAppList.sortBy { it.name.lowercase() }

            runOnUiThread {
                applyCurrentFilter()
            }
        }.start()
    }

    private fun loadAppsWithRoot() {
        try {
            val allPackages = RootUtils.getAllUserPackages()

            for (packageName in allPackages) {
                try {
                    val appInfo = try {
                        packageManager.getApplicationInfo(packageName, PackageManager.MATCH_DISABLED_COMPONENTS)
                    } catch (e: Exception) {
                        try {
                            packageManager.getApplicationInfo(packageName, 0)
                        } catch (e2: Exception) {
                            continue
                        }
                    }

                    if ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0) continue

                    val appName = try {
                        packageManager.getApplicationLabel(appInfo).toString()
                    } catch (e: Exception) {
                        packageName
                    }

                    val icon = try {
                        packageManager.getApplicationIcon(appInfo)
                    } catch (e: Exception) {
                        null
                    }

                    val isEnabled = !RootUtils.isAppFrozen(packageName)

                    allAppList.add(AppInfo(appName, packageName, icon, isEnabled))

                } catch (e: Exception) {
                    continue
                }
            }
        } catch (e: Exception) {
            loadAppsWithoutRoot()
        }
    }

    private fun loadAppsWithoutRoot() {
        try {
            val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)

            for (appInfo in installedApps) {
                if ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0) {
                    val appName = packageManager.getApplicationLabel(appInfo).toString()
                    val packageName = appInfo.packageName
                    val icon = try {
                        packageManager.getApplicationIcon(appInfo)
                    } catch (e: Exception) {
                        null
                    }

                    val isEnabled = appInfo.enabled

                    allAppList.add(AppInfo(appName, packageName, icon, isEnabled))
                }
            }
        } catch (e: Exception) {
            runOnUiThread {
                showError("加载应用列表失败: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 清理定时任务
        statusRunnable?.let { statusHandler.removeCallbacks(it) }
    }
}