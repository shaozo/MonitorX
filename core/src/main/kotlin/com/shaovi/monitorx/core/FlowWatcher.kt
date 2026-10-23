package com.shaovi.monitorx.core

import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ConcurrentHashMap

object FlowWatcher {
    private data class RegisteredRule(val rule: FlowRule, val state: RuleState = RuleState())

    private val rules = CopyOnWriteArrayList<RegisteredRule>()
    private val reporters = CopyOnWriteArrayList<ViolationReporter>()
    private val sessionContext = ConcurrentHashMap<String, String>()

    @Volatile
    var enabled: Boolean = true

    @Volatile
    var currentSessionId: String = UUID.randomUUID().toString()
        private set

    fun register(rule: FlowRule) {
        rules.removeAll { it.rule.id == rule.id }
        rules += RegisteredRule(rule)
    }

    fun registerAll(newRules: Iterable<FlowRule>) = newRules.forEach(::register)

    fun addReporter(reporter: ViolationReporter) {
        if (!reporters.contains(reporter)) reporters += reporter
    }

    fun removeReporter(reporter: ViolationReporter) {
        reporters -= reporter
    }

    fun emit(event: FlowEvent): List<Violation> {
        if (!enabled) return emptyList()
        val violations = mutableListOf<Violation>()
        rules.forEach { registered ->
            val result = synchronized(registered.state) {
                runCatching { registered.rule.evaluate(event, registered.state) }.getOrElse { emptyList() }
            }
            violations += result
        }
        violations.forEach { violation -> reporters.forEach { it.report(violation) } }
        return violations
    }

    fun emit(name: String, attributes: Map<String, String?> = emptyMap()): List<Violation> =
        emit(FlowEvent(type = EventType.CUSTOM, name = name, attributes = attributes))

    fun setContext(key: String, value: String?) {
        require(key.isNotBlank()) { "Context key must not be blank" }
        if (value == null) sessionContext.remove(key) else sessionContext[key] = value
    }

    fun setContext(values: Map<String, String?>) = values.forEach(::setContext)

    fun removeContext(key: String) { sessionContext.remove(key) }

    fun contextSnapshot(): Map<String, String> = sessionContext.toMap()

    fun clearContext() = sessionContext.clear()

    fun resetSession(sessionId: String = UUID.randomUUID().toString()) {
        currentSessionId = sessionId
        clearContext()
        rules.forEach { registered ->
            synchronized(registered.state) {
                registered.rule.onReset(registered.state)
                registered.state.clear()
            }
        }
    }

    fun clearRules() = rules.clear()
}
