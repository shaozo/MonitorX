package com.hkjustar.monitor.okhttp.ui

import android.content.Context
import androidx.startup.Initializer
import com.hkjustar.monitor.okhttp.anomaly.AnomalyManager
import com.hkjustar.monitor.okhttp.anomaly.InAppNotifier
import com.hkjustar.monitor.okhttp.provider.MonitorInitializer

class MonitorUiInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        AnomalyManager.addListener(InAppNotifier)
    }

    override fun dependencies(): List<Class<out Initializer<*>>> =
        listOf(MonitorInitializer::class.java)
}
