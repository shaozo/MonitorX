package com.shaovi.monitorx.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.snackbar.Snackbar
import com.shaovi.monitorx.MonitorHelper
import com.shaovi.monitorx.R
import com.shaovi.monitorx.anomaly.AnomalyManager
import com.shaovi.monitorx.databinding.ActivityMonitorMainBinding
import com.shaovi.monitorx.im.ImMessageCollector
import com.shaovi.monitorx.ui.anomaly.BusinessIssuesFragment
import com.shaovi.monitorx.ui.api.ApiDebugActivity
import com.shaovi.monitorx.ui.im.ImMessageActivity
import com.shaovi.monitorx.ui.request.MonitorMainFragment
import com.shaovi.monitorx.ui.sp.SPFileListFragment
import com.shaovi.monitorx.ui.tools.ToolsFragment
import com.shaovi.monitorx.utils.getPhoneWifiIpAddress
import com.shaovi.monitorx.utils.setupEdgeToEdge
import kotlin.concurrent.thread

class MonitorMainActivity : AppCompatActivity(), NavigationBarView.OnItemSelectedListener {

    private var handle: Handler = Handler(Looper.getMainLooper())

    private lateinit var binding: ActivityMonitorMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMonitorMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupEdgeToEdge(
            rootView = binding.root,
            topView = binding.appBarLayout,
            bottomView = binding.bottomNavigationView
        ) { insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            binding.bottomNavigationView.post {
                val bottomHeight = binding.bottomNavigationView.height
                binding.viewPager.setPadding(0, 0, 0, if (bottomHeight > 0) bottomHeight else navBars.bottom + 64)
            }
        }
        initView()
        initPage()
    }

    override fun onDestroy() {
        super.onDestroy()
        handle.removeCallbacksAndMessages(null)
    }

    private fun initView() {
        binding.tvTitle.text = getString(R.string.monitor_app_name)

        // 安全清空：弹出确认对话框并使用 Snackbar 反馈
        binding.ivClean.setOnClickListener {
            showClearConfirmDialog()
        }

        // 设置齿轮
        binding.ivSetting.setOnClickListener {
            startActivity(Intent(this, MonitorConfigActivity::class.java))
        }

        getPhoneWifiIpAddress()?.let {
            binding.cardWifi.visibility = View.VISIBLE
            binding.tvWifiAddress.visibility = View.VISIBLE
            val monitorUrl = "局域网内可访问：$it:${MonitorHelper.port}/index"
            binding.tvWifiAddress.text = monitorUrl
            Log.d("MonitorHelper", monitorUrl)
        }

        binding.bottomNavigationView.setOnItemSelectedListener(this)
    }

    private fun showClearConfirmDialog() {
        val currentTab = binding.viewPager.currentItem
        when (currentTab) {
            0 -> {
                MaterialAlertDialogBuilder(this)
                    .setTitle("清空网络抓包记录？")
                    .setMessage("确定清空全部已捕获的 HTTP/HTTPS 请求与响应日志？此操作不可撤回。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("清空网络记录") { _, _ ->
                        thread {
                            MonitorHelper.deleteAll()
                            runOnUiThread {
                                Snackbar.make(binding.root, "已成功清空网络抓包记录", Snackbar.LENGTH_SHORT)
                                    .setAnchorView(binding.bottomNavigationView)
                                    .show()
                            }
                        }
                    }
                    .show()
            }
            1 -> {
                MaterialAlertDialogBuilder(this)
                    .setTitle("清空业务异常记录？")
                    .setMessage("确定清空全部已捕获的业务时序与风控异常警报日志？此操作不可撤回。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("清空异常记录") { _, _ ->
                        thread {
                            AnomalyManager.clearRecords()
                            runOnUiThread {
                                Snackbar.make(binding.root, "已成功清空业务异常记录", Snackbar.LENGTH_SHORT)
                                    .setAnchorView(binding.bottomNavigationView)
                                    .show()
                            }
                        }
                    }
                    .show()
            }
            else -> {
                MaterialAlertDialogBuilder(this)
                    .setTitle("清空全部调试数据？")
                    .setMessage("该操作将清空以下全部调试数据：\n• 全部 HTTP 抓包记录\n• 全部 IM 消息流历史\n• 全部业务风控异常警报\n\n清空后不可撤回，是否确认？")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("确认清空全部") { _, _ ->
                        thread {
                            MonitorHelper.deleteAll()
                            ImMessageCollector.clear()
                            AnomalyManager.clearRecords()
                            runOnUiThread {
                                Snackbar.make(binding.root, "已成功清空全部调试数据", Snackbar.LENGTH_SHORT)
                                    .setAnchorView(binding.bottomNavigationView)
                                    .show()
                            }
                        }
                    }
                    .show()
            }
        }
    }

    private fun initPage() {
        val toolsFragment = ToolsFragment().apply {
            onOpenApiDebug = {
                startActivity(Intent(this@MonitorMainActivity, ApiDebugActivity::class.java))
            }
            onOpenIm = {
                startActivity(Intent(this@MonitorMainActivity, ImMessageActivity::class.java))
            }
        }

        val fragments = listOf(
            MonitorMainFragment(),
            BusinessIssuesFragment(),
            SPFileListFragment(),
            toolsFragment
        )

        binding.viewPager.orientation = ViewPager2.ORIENTATION_HORIZONTAL
        binding.viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = fragments.size
            override fun createFragment(position: Int): Fragment = fragments[position]
        }
        binding.viewPager.offscreenPageLimit = fragments.size
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                when (position) {
                    0 -> binding.bottomNavigationView.selectedItemId = R.id.navigation_monitor
                    1 -> binding.bottomNavigationView.selectedItemId = R.id.navigation_business_issues
                    2 -> binding.bottomNavigationView.selectedItemId = R.id.navigation_sharedPrefs
                    3 -> binding.bottomNavigationView.selectedItemId = R.id.navigation_tools
                }
            }
        })

        // 若从悬浮告警通知条跳转，直接切到“异常”页面
        if (intent.hasExtra("EXTRA_ANOMALY_PATH")) {
            binding.viewPager.setCurrentItem(1, false)
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.navigation_monitor -> {
                binding.viewPager.currentItem = 0
                return true
            }
            R.id.navigation_business_issues -> {
                binding.viewPager.currentItem = 1
                return true
            }
            R.id.navigation_sharedPrefs -> {
                binding.viewPager.currentItem = 2
                return true
            }
            R.id.navigation_tools -> {
                binding.viewPager.currentItem = 3
                return true
            }
        }
        return false
    }
}
