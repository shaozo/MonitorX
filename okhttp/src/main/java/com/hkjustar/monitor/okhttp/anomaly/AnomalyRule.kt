package com.hkjustar.monitor.okhttp.anomaly

import java.io.Serializable

enum class AnomalySeverity {
    CRITICAL,
    WARNING,
    INFO
}

enum class ParamLocation {
    ANY,
    QUERY,
    BODY,
    HEADER
}

/**
 * 业务与时序异常检测规则定义
 * 通用设计，不与任何具体业务类绑定
 */
data class AnomalyRule(
    val id: String,
    val name: String,
    val urlPattern: Regex,
    val paramKey: String = "",
    val location: ParamLocation = ParamLocation.ANY,
    val severity: AnomalySeverity = AnomalySeverity.CRITICAL,
    val alertMessage: String,
    val solution: String = "",
    val isIllegal: (value: String?) -> Boolean = { false },
    val validateParams: ((params: Map<String, String>) -> Boolean)? = null
) : Serializable
