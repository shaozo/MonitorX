package com.shaovi.monitorx.internal

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.shaovi.monitorx.MonitorHelper
import com.shaovi.monitorx.anomaly.AnomalyManager
import com.shaovi.monitorx.api.MonitorXBackend
import com.shaovi.monitorx.api.MonitorXBusinessMonitoringBackend
import com.shaovi.monitorx.api.MonitorXDiagnosticsBackend
import com.shaovi.monitorx.api.MonitorXRulePackBackend
import com.shaovi.monitorx.core.FlowWatcher

/** Loaded reflectively by monitor-api only when the real Android runtime is packaged. */
object RealMonitorXBackend : MonitorXBackend, MonitorXDiagnosticsBackend,
    MonitorXBusinessMonitoringBackend, MonitorXRulePackBackend {
    override fun initialize(context: Context) {
        MonitorHelper.init(context.applicationContext)
    }

    override fun setEnabled(enabled: Boolean) {
        MonitorHelper.isOpenMonitor = enabled
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

    override fun setContext(key: String, value: String?) = FlowWatcher.setContext(key, value)

    override fun setContext(values: Map<String, String?>) = FlowWatcher.setContext(values)

    override fun removeContext(key: String) = FlowWatcher.removeContext(key)

    override fun clearContext() = FlowWatcher.clearContext()

    override fun setBusinessMonitoringEnabled(enabled: Boolean) {
        AnomalyManager.setEnabled(enabled)
        FlowWatcher.enabled = enabled
    }

    override fun loadBusinessRulePacks(context: Context, assetPaths: List<String>): List<String> =
        AnomalyManager.loadManualRulePacks(context.applicationContext, assetPaths)

    override fun loadBusinessRuleDirectory(context: Context, assetDirectory: String): List<String> =
        AnomalyManager.loadManualRuleDirectory(context.applicationContext, assetDirectory)

    override fun emit(name: String, attributes: Map<String, String?>) {
        FlowWatcher.emit(name, attributes)
    }

    override fun resetSession(sessionId: String?) {
        if (sessionId == null) FlowWatcher.resetSession() else FlowWatcher.resetSession(sessionId)
    }
}
