package com.hkjustar.monitor.okhttp.anomaly

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.hkjustar.monitor.okhttp.MonitorHelper
import com.hkjustar.monitor.okhttp.runtime.MonitorEnvironment
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 客户端异常通知器
 * 1. 发送常驻系统通知栏 Notification（可下拉回溯、堆叠查看、点击直接跳入抓包诊断）
 * 2. 在前台 Activity 屏幕顶部弹出醒目的告警条（点击也可直接跳入抓包详情）
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

        // 1. 发送系统通知（常驻通知栏，即使退出当前页面也可随时下拉回溯）
        sendSystemNotification(record)

        // 2. 在前台页面弹出悬浮高亮横幅（第一现场强视觉提示）
        val appContext = MonitorHelper.context?.applicationContext ?: return
        androidx.core.content.ContextCompat.getMainExecutor(appContext).execute mainThread@{
            val activity = MonitorEnvironment.topActivity() ?: return@mainThread
            if (activity.isFinishing || activity.isDestroyed) return@mainThread
            showTopBanner(activity, record)
        }
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
            "com.hkjustar.monitor.okhttp.ui.MonitorMainActivity"
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

    /**
     * 前台页面悬浮警告条
     */
    private fun showTopBanner(activity: Activity, record: AnomalyRecord) {
        val decorView = activity.window?.decorView as? ViewGroup ?: return

        val bannerContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#D32F2F")) // 警示深红
            elevation = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24f, activity.resources.displayMetrics)
            val padH = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16f, activity.resources.displayMetrics).toInt()
            val padV = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12f, activity.resources.displayMetrics).toInt()
            val statusBarHeight = getStatusBarHeight(activity)
            setPadding(padH, padV + statusBarHeight, padH, padV)
        }

        val titleTv = TextView(activity).apply {
            text = "🚨 [${record.severity}] ${record.ruleName}"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
        }

        val msgTv = TextView(activity).apply {
            text = "${record.alertMessage}\n(接口: ${record.path} | 字段: ${record.paramKey} = \"${record.actualValue ?: "null"}\")"
            setTextColor(Color.parseColor("#FFF3F3"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, 4, 0, 0)
        }

        val tipTv = TextView(activity).apply {
            text = "👉 点击打开抓包详情 | 已存入系统通知栏与PC端"
            setTextColor(Color.parseColor("#FFCDD2"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setPadding(0, 4, 0, 0)
        }

        bannerContainer.addView(titleTv)
        bannerContainer.addView(msgTv)
        bannerContainer.addView(tipTv)

        val layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP
        }

        decorView.addView(bannerContainer, layoutParams)

        var isRemoved = false
        val removeBanner = {
            if (!isRemoved) {
                isRemoved = true
                runCatching { decorView.removeView(bannerContainer) }
            }
        }

        // 点击横幅：直接跳转进 MonitorMainActivity，方便一键查看！
        bannerContainer.setOnClickListener {
            removeBanner()
            runCatching {
                val intent = Intent().setClassName(
                    activity,
                    "com.hkjustar.monitor.okhttp.ui.MonitorMainActivity"
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("EXTRA_ANOMALY_PATH", record.path)
                    putExtra("EXTRA_ANOMALY_PARAM", record.paramKey)
                }
                activity.startActivity(intent)
            }
        }

        // The coroutine is cancelled automatically when the Activity is destroyed. Its finally
        // block also removes the injected View, so no delayed callback retains the Activity.
        (activity as? LifecycleOwner)?.lifecycleScope?.launch {
            try {
                delay(5000L)
            } finally {
                removeBanner()
            }
        }
    }

    private fun getStatusBarHeight(activity: Activity): Int {
        val resourceId = activity.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) activity.resources.getDimensionPixelSize(resourceId) else 0
    }
}
