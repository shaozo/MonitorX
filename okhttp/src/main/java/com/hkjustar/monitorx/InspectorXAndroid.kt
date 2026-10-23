package com.hkjustar.monitorx

import android.content.Context
import com.hkjustar.monitor.okhttp.MonitorHelper
import com.hkjustar.monitor.okhttp.anomaly.AnomalyManager
import com.hkjustar.monitorx.core.InspectorX
import com.hkjustar.monitorx.core.config.RulePack
import com.hkjustar.monitorx.core.config.RulePackLoader
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Android entry point for automatic initialization and portable asset rule packs. */
object InspectorXAndroid {
    private val watchdogScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val paymentWatchdogs = ConcurrentHashMap<String, Job>()
    fun install(context: Context) = MonitorHelper.init(context.applicationContext)

    fun loadRulePack(context: Context, assetPath: String) =
        AnomalyManager.loadRulePack(context.applicationContext, assetPath)

    fun loadRulePacks(context: Context, assetDirectory: String = "monitorx"): List<String> =
        AnomalyManager.loadRulePacks(context.applicationContext, assetDirectory)

    fun validateRulePack(content: String): RulePack = RulePackLoader.decode(content)

    fun resetSession(sessionId: String? = null) = InspectorX.resetSession(sessionId)

    /**
     * Starts a process-scoped payment watchdog after Google Play reports PURCHASED.
     * The matching HTTP_REQUEST rule completes it as soon as noticeResult is actually invoked.
     */
    fun watchPaymentServerNotification(
        purchaseToken: String,
        productId: String = "",
        timeoutMs: Long = 15_000L
    ) {
        if (purchaseToken.isBlank()) return
        InspectorX.emit(
            "PAYMENT_SUCCEEDED",
            mapOf(
                "http.param.purchase_token" to purchaseToken,
                "payment.product_id" to productId
            )
        )
        paymentWatchdogs.remove(purchaseToken)?.cancel()
        paymentWatchdogs[purchaseToken] = watchdogScope.launch {
            delay(timeoutMs.coerceAtLeast(1_000L))
            InspectorX.emit("PAYMENT_SERVER_NOTIFY_WATCHDOG")
            paymentWatchdogs.remove(purchaseToken)
        }
    }
}
