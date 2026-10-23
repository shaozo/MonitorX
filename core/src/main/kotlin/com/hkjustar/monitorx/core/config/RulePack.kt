package com.hkjustar.monitorx.core.config

import com.hkjustar.monitorx.core.DependencyRule
import com.hkjustar.monitorx.core.EventMatcher
import com.hkjustar.monitorx.core.EventType
import com.hkjustar.monitorx.core.FlowEvent
import com.hkjustar.monitorx.core.FlowRule
import com.hkjustar.monitorx.core.ForbiddenBeforeRule
import com.hkjustar.monitorx.core.FunnelRule
import com.hkjustar.monitorx.core.PointRule
import com.hkjustar.monitorx.core.RateRule
import com.hkjustar.monitorx.core.Severity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class RulePack(
    val schemaVersion: Int = 1,
    val id: String,
    val name: String = id,
    val rules: List<RuleDefinition>
)

@Serializable
data class RuleDefinition(
    val id: String,
    val name: String,
    val type: RuleType,
    val enabled: Boolean = true,
    val severity: SeverityConfig = SeverityConfig.CRITICAL,
    val message: String,
    val solution: String = "",
    val matcher: MatcherDefinition? = null,
    val prerequisite: MatcherDefinition? = null,
    val trigger: MatcherDefinition? = null,
    val allowedAfter: MatcherDefinition? = null,
    val forbidden: MatcherDefinition? = null,
    val start: MatcherDefinition? = null,
    val completion: MatcherDefinition? = null,
    val conditions: List<ConditionDefinition> = emptyList(),
    val contextConditions: List<ConditionDefinition> = emptyList(),
    val conditionMode: ConditionMode = ConditionMode.ALL,
    val windowMs: Long? = null,
    val maxCount: Int? = null,
    val correlationAttribute: String? = null
)

@Serializable enum class RuleType { POINT, DEPENDENCY, FORBIDDEN_BEFORE, RATE, FUNNEL }
@Serializable enum class SeverityConfig { CRITICAL, WARNING, INFO }
@Serializable enum class ConditionMode { ALL, ANY }
@Serializable enum class ConditionOperator {
    BLANK, NOT_BLANK, EQUALS, NOT_EQUALS, STARTS_WITH, NOT_STARTS_WITH, MATCHES, IN
}

@Serializable
data class MatcherDefinition(
    val eventType: String? = null,
    val name: String? = null,
    val nameRegex: String? = null,
    val pathRegex: String? = null,
    val attributes: Map<String, String> = emptyMap()
)

@Serializable
data class ConditionDefinition(
    val attribute: String,
    val operator: ConditionOperator,
    val value: String? = null,
    val values: List<String> = emptyList()
)

object RulePackLoader {
    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
    }

    fun decode(content: String): RulePack {
        val pack = json.decodeFromString<RulePack>(content)
        require(pack.schemaVersion == 1) { "Unsupported MonitorX rule schema: ${pack.schemaVersion}" }
        require(pack.id.isNotBlank()) { "Rule pack id must not be blank" }
        require(pack.name.isNotBlank()) { "Rule pack name must not be blank" }
        require(pack.rules.isNotEmpty()) { "Rule pack ${pack.id} must contain at least one rule" }
        val duplicateIds = pack.rules.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
        require(duplicateIds.isEmpty()) { "Duplicate rule ids: ${duplicateIds.joinToString()}" }
        // Build every rule during validation, including disabled rules. This prevents a malformed
        // rule from being shipped unnoticed and only failing after it is enabled remotely.
        pack.rules.forEach { it.toRule() }
        return pack
    }

    fun decodeRules(content: String): List<FlowRule> = decode(content).toRules()
}

fun RulePack.toRules(): List<FlowRule> = rules.filter { it.enabled }.map { it.toRule() }

private fun RuleDefinition.toRule(): FlowRule {
    require(id.isNotBlank()) { "Rule id must not be blank" }
    require(name.isNotBlank()) { "$id name must not be blank" }
    require(message.isNotBlank()) { "$id message must not be blank" }
    (conditions + contextConditions).forEach { condition -> condition.validate(id) }
    val level = Severity.valueOf(severity.name)
    return when (type) {
        RuleType.POINT -> {
            val target = requireNotNull(matcher) { "$id requires matcher" }.toMatcher(id)
            require(conditions.isNotEmpty()) { "$id requires at least one condition" }
            PointRule(
                id = id,
                name = name,
                severity = level,
                matcher = target,
                message = message,
                solution = solution,
                metadata = { event -> conditionMetadata(event) },
                isViolation = { event -> contextConditionsMatch() && conditionsMatch(event) }
            )
        }
        RuleType.DEPENDENCY -> DependencyRule(
            id, name, level,
            requireNotNull(prerequisite) { "$id requires prerequisite" }.toMatcher(id),
            requireNotNull(trigger) { "$id requires trigger" }.toMatcher(id),
            message, solution
        )
        RuleType.FORBIDDEN_BEFORE -> ForbiddenBeforeRule(
            id, name, level,
            requireNotNull(allowedAfter) { "$id requires allowedAfter" }.toMatcher(id),
            requireNotNull(forbidden) { "$id requires forbidden" }.toMatcher(id),
            message, solution
        )
        RuleType.RATE -> RateRule(
            id = id,
            name = name,
            severity = level,
            matcher = requireNotNull(matcher) { "$id requires matcher" }.toMatcher(id),
            windowMs = requireNotNull(windowMs) { "$id requires windowMs" },
            maxCount = requireNotNull(maxCount) { "$id requires maxCount" },
            keySelector = { event -> correlationAttribute?.let(event.attributes::get).orEmpty().ifBlank { event.name } },
            message = { count -> "$message (count=$count)" },
            solution = solution
        )
        RuleType.FUNNEL -> FunnelRule(
            id = id,
            name = name,
            severity = level,
            start = requireNotNull(start) { "$id requires start" }.toMatcher(id),
            completion = requireNotNull(completion) { "$id requires completion" }.toMatcher(id),
            timeoutMs = requireNotNull(windowMs) { "$id requires windowMs" },
            correlationKey = { event -> correlationAttribute?.let(event.attributes::get).orEmpty().ifBlank { "default" } },
            message = message,
            solution = solution
        )
    }
}

private fun ConditionDefinition.validate(ruleId: String) {
    require(attribute.isNotBlank()) { "$ruleId condition attribute must not be blank" }
    when (operator) {
        ConditionOperator.EQUALS,
        ConditionOperator.NOT_EQUALS,
        ConditionOperator.STARTS_WITH,
        ConditionOperator.NOT_STARTS_WITH,
        ConditionOperator.MATCHES -> requireNotNull(value) { "$ruleId $operator condition requires value" }
        ConditionOperator.IN -> require(values.isNotEmpty()) { "$ruleId IN condition requires values" }
        ConditionOperator.BLANK,
        ConditionOperator.NOT_BLANK -> Unit
    }
    if (operator == ConditionOperator.MATCHES) {
        runCatching { Regex(value.orEmpty()) }
            .getOrElse { error("$ruleId has invalid condition regex: $value") }
    }
}

private fun MatcherDefinition.toMatcher(ruleId: String): EventMatcher {
    val type = eventType?.let {
        runCatching { EventType.valueOf(it) }.getOrElse { error("$ruleId has invalid eventType: $it") }
    }
    val compiledName = nameRegex?.let { pattern ->
        runCatching { Regex(pattern) }.getOrElse { error("$ruleId has invalid nameRegex: $pattern") }
    }
    val compiledPath = pathRegex?.let { pattern ->
        runCatching { Regex(pattern) }.getOrElse { error("$ruleId has invalid pathRegex: $pattern") }
    }
    return { event ->
        (type == null || event.type == type) &&
            (name == null || event.name == name) &&
            (compiledName == null || compiledName.containsMatchIn(event.name)) &&
            (compiledPath == null || compiledPath.containsMatchIn(event.attributes["http.path"].orEmpty())) &&
            attributes.all { (key, value) -> event.attributes[key] == value }
    }
}

private fun RuleDefinition.conditionsMatch(event: FlowEvent): Boolean {
    val results = conditions.map { it.matches(event.attributes[it.attribute]) }
    return when (conditionMode) {
        ConditionMode.ALL -> results.all { it }
        ConditionMode.ANY -> results.any { it }
    }
}

private fun RuleDefinition.contextConditionsMatch(): Boolean =
    contextConditions.all { condition ->
        condition.matches(com.hkjustar.monitorx.core.FlowWatcher.contextSnapshot()[condition.attribute])
    }

private fun RuleDefinition.conditionMetadata(event: FlowEvent): Map<String, String?> {
    val keys = conditions.map { it.attribute }.distinct()
    return mapOf(
        "paramKey" to keys.joinToString { it.removePrefix("http.param.") },
        "actualValue" to keys.joinToString { "$it=${event.attributes[it]}" }
    )
}

private fun ConditionDefinition.matches(actual: String?): Boolean = when (operator) {
    ConditionOperator.BLANK -> actual.isNullOrBlank()
    ConditionOperator.NOT_BLANK -> !actual.isNullOrBlank()
    ConditionOperator.EQUALS -> actual == value
    ConditionOperator.NOT_EQUALS -> actual != value
    ConditionOperator.STARTS_WITH -> actual?.startsWith(value.orEmpty()) == true
    ConditionOperator.NOT_STARTS_WITH -> actual?.startsWith(value.orEmpty()) != true
    ConditionOperator.MATCHES -> actual?.let { Regex(requireNotNull(value) { "MATCHES requires value" }).matches(it) } == true
    ConditionOperator.IN -> actual in values
}
