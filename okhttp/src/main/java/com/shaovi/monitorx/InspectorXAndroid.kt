package com.shaovi.monitorx

import android.content.Context
import com.shaovi.monitorx.MonitorHelper
import com.shaovi.monitorx.anomaly.AnomalyManager
import com.shaovi.monitorx.core.InspectorX
import com.shaovi.monitorx.core.config.RulePack
import com.shaovi.monitorx.core.config.RulePackLoader
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
     * Compatibility wrapper for the original payment watchdog API.
     * New integrations should model the flow explicitly with MonitorX.emit and a rule pack.
     */
    @Deprecated(
        message = "Use MonitorX.emit with a payment flow rule pack",
        replaceWith = ReplaceWith("MonitorX.emit(\"PAYMENT_SUCCEEDED\", mapOf(\"http.param.purchase_token\" to purchaseToken, \"payment.product_id\" to productId))")
    )
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
