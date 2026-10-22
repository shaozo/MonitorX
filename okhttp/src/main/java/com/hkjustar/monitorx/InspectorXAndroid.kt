package com.hkjustar.monitorx

import android.content.Context
import com.hkjustar.monitor.okhttp.MonitorHelper
import com.hkjustar.monitor.okhttp.anomaly.AnomalyManager
import com.hkjustar.monitorx.core.InspectorX
import com.hkjustar.monitorx.core.config.RulePack
import com.hkjustar.monitorx.core.config.RulePackLoader

/** Android entry point for automatic initialization and portable asset rule packs. */
object InspectorXAndroid {
    fun install(context: Context) = MonitorHelper.init(context.applicationContext)

    fun loadRulePack(context: Context, assetPath: String) =
        AnomalyManager.loadRulePack(context.applicationContext, assetPath)

    fun loadRulePacks(context: Context, assetDirectory: String = "monitorx"): List<String> =
        AnomalyManager.loadRulePacks(context.applicationContext, assetDirectory)

    fun validateRulePack(content: String): RulePack = RulePackLoader.decode(content)

    fun resetSession(sessionId: String? = null) = InspectorX.resetSession(sessionId)
}
