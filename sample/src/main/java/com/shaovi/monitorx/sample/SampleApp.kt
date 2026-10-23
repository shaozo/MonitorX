package com.shaovi.monitorx.sample

import android.app.Application
import android.content.Context
import com.shaovi.monitorx.api.MonitorX
import com.shaovi.monitorx.core.InspectorX

class SampleApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // 1. 初始化 MonitorX (在 Debug 环境自动启动抓包与悬浮服务，在 Release 环境自动 no-op)
        MonitorX.initialize(this)
        MonitorX.setEnabled(true)

        // 2. 预置 SharedPreferences 数据，便于在 MonitorX 中进行 SP 检查与实时编辑
        setupSamplePreferences()

        // 3. 安装业务异常规则包
        setupInspectorRules()
    }

    private fun setupSamplePreferences() {
        val userSp = getSharedPreferences("user_profile", Context.MODE_PRIVATE)
        userSp.edit()
            .putString("username", "developer_test")
            .putInt("user_level", 5)
            .putBoolean("is_vip", true)
            .putFloat("account_balance", 888.88f)
            .putString("token", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.sample_token")
            .apply()

        val appConfigSp = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        appConfigSp.edit()
            .putBoolean("dark_mode_enabled", false)
            .putString("env", "staging")
            .putInt("cache_size_mb", 256)
            .apply()
    }

    private fun setupInspectorRules() {
        runCatching {
            val json = assets.open("monitorx/checkout-rules.json").bufferedReader().use { it.readText() }
            InspectorX.installRulePack(json)
        }
    }
}
