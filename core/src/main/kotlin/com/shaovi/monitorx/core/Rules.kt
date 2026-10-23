package com.shaovi.monitorx.core

class PointRule(
    override val id: String,
    override val name: String,
    override val severity: Severity = Severity.CRITICAL,
    private val matcher: EventMatcher,
    private val message: String,
    private val solution: String = "",
    private val metadata: (FlowEvent) -> Map<String, String?> = { emptyMap() },
    private val isViolation: (FlowEvent) -> Boolean
) : FlowRule {
    override fun evaluate(event: FlowEvent, state: RuleState): List<Violation> {
        if (!matcher(event) || !isViolation(event)) return emptyList()
        return listOf(Violation(id, name, severity, message, solution, event, metadata(event)))
    }
}

class DependencyRule(
    override val id: String,
    override val name: String,
    override val severity: Severity = Severity.CRITICAL,
    private val prerequisite: EventMatcher,
    private val trigger: EventMatcher,
    private val message: String,
    private val solution: String = ""
) : FlowRule {
    override fun evaluate(event: FlowEvent, state: RuleState): List<Violation> {
        if (prerequisite(event)) state.put("ready", true)
        if (!trigger(event) || state.get<Boolean>("ready") == true) return emptyList()
        return listOf(Violation(id, name, severity, message, solution, event))
    }
}

class ForbiddenBeforeRule(
    override val id: String,
    override val name: String,
    override val severity: Severity = Severity.CRITICAL,
    private val allowedAfter: EventMatcher,
    private val forbidden: EventMatcher,
    private val message: String,
    private val solution: String = ""
) : FlowRule {
    override fun evaluate(event: FlowEvent, state: RuleState): List<Violation> {
        if (allowedAfter(event)) state.put("allowed", true)
        if (!forbidden(event) || state.get<Boolean>("allowed") == true) return emptyList()
        return listOf(Violation(id, name, severity, message, solution, event))
    }
}

class RateRule(
    override val id: String,
    override val name: String,
    override val severity: Severity = Severity.WARNING,
    private val matcher: EventMatcher,
    private val windowMs: Long,
    private val maxCount: Int,
    private val keySelector: (FlowEvent) -> String = { it.name },
    private val message: (Int) -> String,
    private val solution: String = ""
) : FlowRule {
    init {
        require(windowMs > 0) { "windowMs must be positive" }
        require(maxCount > 0) { "maxCount must be positive" }
    }

    override fun evaluate(event: FlowEvent, state: RuleState): List<Violation> {
        if (!matcher(event)) return emptyList()
        val key = "rate:${keySelector(event)}"
        val timestamps = state.get<ArrayDeque<Long>>(key) ?: ArrayDeque<Long>().also { state.put(key, it) }
        val cutoff = event.elapsedRealtimeMs - windowMs
        while (timestamps.firstOrNull()?.let { it < cutoff } == true) timestamps.removeFirst()
        timestamps.addLast(event.elapsedRealtimeMs)
        if (timestamps.size <= maxCount) return emptyList()
        return listOf(Violation(id, name, severity, message(timestamps.size), solution, event))
    }
}

class FunnelRule(
    override val id: String,
    override val name: String,
    override val severity: Severity = Severity.WARNING,
    private val start: EventMatcher,
    private val completion: EventMatcher,
    private val timeoutMs: Long,
    private val correlationKey: (FlowEvent) -> String = { "default" },
    private val message: String,
    private val solution: String = ""
) : FlowRule {
    init {
        require(timeoutMs > 0) { "timeoutMs must be positive" }
    }

    override fun evaluate(event: FlowEvent, state: RuleState): List<Violation> {
        val violations = mutableListOf<Violation>()
        val pending = state.get<MutableMap<String, FlowEvent>>("pending")
            ?: linkedMapOf<String, FlowEvent>().also { state.put("pending", it) }

        val expired = pending.filterValues {
            event.elapsedRealtimeMs - it.elapsedRealtimeMs >= timeoutMs
        }
        expired.forEach { (key, startedEvent) ->
            pending.remove(key)
            violations += Violation(id, name, severity, message, solution, startedEvent)
        }

        if (completion(event)) pending.remove(correlationKey(event))
        if (start(event)) pending[correlationKey(event)] = event
        return violations
    }
}
