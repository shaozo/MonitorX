package com.shaovi.monitorx.anomaly

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.shaovi.monitorx.MonitorHelper
import com.shaovi.monitorx.runtime.MonitorEnvironment
import java.util.concurrent.ConcurrentHashMap

/**
 * 客户端异常通知器：仅发送系统通知，不向宿主 Activity 注入 View，避免干扰业务测试。
 */
object InAppNotifier : AnomalyListener {

    private const val CHANNEL_ID = "monitor_anomaly_channel_v1"
    private const val CHANNEL_NAME = "业务与风控暗坑异常预警"
    private const val DEBOUNCE_MS = 2500L

    private val lastAlertTimes = ConcurrentHashMap<String, Long>()

    override fun onAnomaly(record: AnomalyRecord) {
        val now = System.currentTimeMillis()
        val lastTime = lastAlertTimes[record.ruleId] ?: 0L
        if (now - lastTime < DEBOUNCE_MS) {
            return
        }
        lastAlertTimes[record.ruleId] = now

        // 仅使用系统通知；不显示应用内横幅，也不额外触发宿主页面的触觉反馈。
        sendSystemNotification(record)
    }

    /**
     * 发送系统通知栏通知（Notification）
     * 驻留系统下拉菜单，点击直接打开 MonitorMainActivity 查看详情
     */
    @android.annotation.SuppressLint("MissingPermission")
    private fun sendSystemNotification(record: AnomalyRecord) {
        val context = MonitorHelper.context ?: MonitorEnvironment.topActivity()?.applicationContext ?: return

        ensureNotificationChannel(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val intent = Intent().setClassName(
            context,
            "com.shaovi.monitorx.ui.MonitorMainActivity"
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_ANOMALY_PATH", record.path)
            putExtra("EXTRA_ANOMALY_PARAM", record.paramKey)
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        val pendingIntent = PendingIntent.getActivity(
            context,
            record.id.hashCode(),
            intent,
            flags
        )

        val bigText = buildString {
            append("【危害描述】").append(record.alertMessage).append("\n")
            append("【接口路径】").append(record.path).append("\n")
            append("【异常字段】").append(record.paramKey).append(" = \"").append(record.actualValue ?: "null").append("\"\n")
            if (record.solution.isNotBlank()) {
                append("【排查建议】").append(record.solution).append("\n")
            }
            append("【发生时间】").append(record.timeString)
            if (record.activityName.isNotBlank()) {
                append(" (").append(record.activityName).append(")")
            }
            append("\n👉 点击立即打开抓包与排查详情")
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("🚨 [${record.severity}] ${record.ruleName}")
            .setContentText("${record.path} | ${record.paramKey}=${record.actualValue ?: "null"}")
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(record.id.hashCode(), notification)
        }
    }

    private fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val existing = manager.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "用于实时推送客户端定位、易盾设备指纹等业务与时序异常"
                    enableVibration(true)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

}
