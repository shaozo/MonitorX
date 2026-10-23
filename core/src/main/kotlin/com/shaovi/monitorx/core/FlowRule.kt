package com.shaovi.monitorx.core

interface FlowRule {
    val id: String
    val name: String
    val severity: Severity

    fun evaluate(event: FlowEvent, state: RuleState): List<Violation>

    fun onReset(state: RuleState) = Unit
}

class RuleState internal constructor() {
    private val values = HashMap<String, Any>()

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(key: String): T? = values[key] as? T

    fun put(key: String, value: Any) {
        values[key] = value
    }

    fun remove(key: String): Any? = values.remove(key)

    fun clear() = values.clear()
}

typealias EventMatcher = (FlowEvent) -> Boolean
