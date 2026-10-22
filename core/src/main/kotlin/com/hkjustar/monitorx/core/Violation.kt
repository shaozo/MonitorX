package com.hkjustar.monitorx.core

import java.util.UUID

enum class Severity {
    CRITICAL,
    WARNING,
    INFO
}

data class Violation(
    val ruleId: String,
    val ruleName: String,
    val severity: Severity,
    val message: String,
    val solution: String = "",
    val event: FlowEvent,
    val metadata: Map<String, String?> = emptyMap(),
    val id: String = UUID.randomUUID().toString()
)

fun interface ViolationReporter {
    fun report(violation: Violation)
}
