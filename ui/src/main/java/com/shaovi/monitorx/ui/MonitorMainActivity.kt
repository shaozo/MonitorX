package com.shaovi.monitorx.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.navigation.NavigationBarView
import com.shaovi.monitorx.MonitorHelper
import com.shaovi.monitorx.R
import com.shaovi.monitorx.databinding.ActivityMonitorMainBinding
import com.shaovi.monitorx.im.ImMessageCollector
import com.shaovi.monitorx.anomaly.AnomalyManager
import com.shaovi.monitorx.ui.im.ImMessageFragment
import com.shaovi.monitorx.ui.api.ApiDebugFragment
import com.shaovi.monitorx.ui.anomaly.BusinessIssuesFragment
import com.shaovi.monitorx.ui.request.MonitorMainFragment
import com.shaovi.monitorx.ui.sp.SPFileListFragment
import com.shaovi.monitorx.utils.getPhoneWifiIpAddress
import kotlin.concurrent.thread

class MonitorMainActivity : AppCompatActivity(), NavigationBarView.OnItemSelectedListener {

    private var handle: Handler = Handler(Looper.getMainLooper())

    private lateinit var binding: ActivityMonitorMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMonitorMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initView()
        initPage()
    }

    override fun onDestroy() {
        super.onDestroy()
        handle.removeCallbacksAndMessages(null)
    }

    private fun initView() {
        binding.tvTitle.text = getString(R.string.monitor_app_name)
        binding.tvClean.setOnClickListener {
            thread {
                MonitorHelper.deleteAll()
                ImMessageCollector.clear()
                AnomalyManager.clearRecords()
            }
        }
        getPhoneWifiIpAddress()?.let {
            binding.tvWifiAddress.visibility = View.VISIBLE
            val monitorUrl = "局域网内可访问：$it:${MonitorHelper.port}/index"
            binding.tvWifiAddress.text = monitorUrl
            Log.d("MonitorHelper", monitorUrl)
        }

        binding.ivSetting.setOnClickListener {
            startActivity(Intent(this, MonitorConfigActivity::class.java))
        }

        binding.bottomNavigationView.setOnItemSelectedListener(this)
    }

    private fun initPage() {
        val fragments = listOf(
            MonitorMainFragment(),
            ImMessageFragment(),
            SPFileListFragment(),
            ApiDebugFragment(),
            BusinessIssuesFragment()
        )
        binding.viewPager.orientation = ViewPager2.ORIENTATION_HORIZONTAL
        binding.viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = fragments.size

            override fun createFragment(position: Int): Fragment {
                return fragments[position]
            }
        }
        binding.viewPager.offscreenPageLimit = fragments.size
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                when (position) {
                    0 -> binding.bottomNavigationView.selectedItemId = R.id.navigation_monitor
                    1 -> binding.bottomNavigationView.selectedItemId = R.id.navigation_im
                    2 -> binding.bottomNavigationView.selectedItemId = R.id.navigation_sharedPrefs
                    3 -> binding.bottomNavigationView.selectedItemId = R.id.navigation_api_debug
                    4 -> binding.bottomNavigationView.selectedItemId = R.id.navigation_business_issues
                }
            }
        })
        if (intent.hasExtra("EXTRA_ANOMALY_PATH")) {
            binding.viewPager.setCurrentItem(4, false)
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.navigation_monitor -> {
                binding.viewPager.currentItem = 0
                return true
            }
            R.id.navigation_im -> {
                binding.viewPager.currentItem = 1
                return true
            }
            R.id.navigation_sharedPrefs -> {
                binding.viewPager.currentItem = 2
                return true
            }
            R.id.navigation_api_debug -> {
                binding.viewPager.currentItem = 3
                return true
            }
            R.id.navigation_business_issues -> {
                binding.viewPager.currentItem = 4
                return true
            }
        }
        return false
    }

}
