package com.shaovi.monitorx.anomaly

import java.io.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * 捕获到的异常违规记录实体
 */
data class AnomalyRecord(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val timeString: String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp)),
    val ruleId: String,
    val ruleName: String,
    val severity: String = AnomalySeverity.CRITICAL.name,
    val url: String,
    val path: String,
    val method: String,
    val paramKey: String,
    val actualValue: String?,
    val alertMessage: String,
    val solution: String = "",
    val monitorDataId: Long = 0L,
    var notifiedWeb: Boolean = false,
    val activityName: String = ""
) : Serializable
