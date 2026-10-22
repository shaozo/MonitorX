package com.hkjustar.monitorx.core

import com.hkjustar.monitorx.core.config.RulePack
import com.hkjustar.monitorx.core.config.RulePackLoader
import com.hkjustar.monitorx.core.config.toRules

/** Stable public facade for embedding InspectorX without depending on implementation objects. */
object InspectorX {
    var enabled: Boolean
        get() = FlowWatcher.enabled
        set(value) { FlowWatcher.enabled = value }

    val sessionId: String
        get() = FlowWatcher.currentSessionId

    fun installRulePack(content: String): RulePack =
        RulePackLoader.decode(content).also { FlowWatcher.registerAll(it.toRules()) }

    fun register(rule: FlowRule) = FlowWatcher.register(rule)

    fun inspect(event: FlowEvent): List<Violation> = FlowWatcher.emit(event)

    fun emit(name: String, attributes: Map<String, String?> = emptyMap()): List<Violation> =
        FlowWatcher.emit(name, attributes)

    fun addReporter(reporter: ViolationReporter) = FlowWatcher.addReporter(reporter)

    fun removeReporter(reporter: ViolationReporter) = FlowWatcher.removeReporter(reporter)

    fun setContext(key: String, value: String?) = FlowWatcher.setContext(key, value)

    fun setContext(values: Map<String, String?>) = FlowWatcher.setContext(values)

    fun removeContext(key: String) = FlowWatcher.removeContext(key)

    fun contextSnapshot(): Map<String, String> = FlowWatcher.contextSnapshot()

    fun clearContext() = FlowWatcher.clearContext()

    fun resetSession(sessionId: String? = null) {
        if (sessionId == null) FlowWatcher.resetSession() else FlowWatcher.resetSession(sessionId)
    }

    fun clearRules() = FlowWatcher.clearRules()
}
