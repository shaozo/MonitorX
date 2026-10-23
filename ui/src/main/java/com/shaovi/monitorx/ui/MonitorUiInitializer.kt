package com.shaovi.monitorx.ui

import android.content.Context
import androidx.startup.Initializer
import com.shaovi.monitorx.anomaly.AnomalyManager
import com.shaovi.monitorx.anomaly.InAppNotifier
import com.shaovi.monitorx.provider.MonitorInitializer

class MonitorUiInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        AnomalyManager.addListener(InAppNotifier)
    }

    override fun dependencies(): List<Class<out Initializer<*>>> =
        listOf(MonitorInitializer::class.java)
}
