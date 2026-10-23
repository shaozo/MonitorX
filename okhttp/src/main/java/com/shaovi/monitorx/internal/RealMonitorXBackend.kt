package com.shaovi.monitorx.internal

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.shaovi.monitorx.MonitorHelper
import com.shaovi.monitorx.anomaly.AnomalyManager
import com.shaovi.monitorx.api.MonitorXBackend
import com.shaovi.monitorx.core.FlowWatcher

/** Loaded reflectively by monitor-api only when the real Android runtime is packaged. */
object RealMonitorXBackend : MonitorXBackend {
    override fun initialize(context: Context) {
        MonitorHelper.init(context.applicationContext)
    }

    override fun setEnabled(enabled: Boolean) {
        MonitorHelper.isOpenMonitor = enabled
        AnomalyManager.isEnabled = enabled
        FlowWatcher.enabled = enabled
    }

    override fun isEnabled(): Boolean = MonitorHelper.isOpenMonitor

    override fun open(context: Context): Boolean = runCatching {
        val intent = Intent().setClassName(
            context.packageName,
            "com.shaovi.monitorx.ui.MonitorMainActivity"
        )
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}
