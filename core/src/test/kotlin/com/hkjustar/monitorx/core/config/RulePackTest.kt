package com.hkjustar.monitorx.core.config

import com.hkjustar.monitorx.core.EventType
import com.hkjustar.monitorx.core.FlowEvent
import com.hkjustar.monitorx.core.FlowWatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RulePackTest {
    @After fun cleanUp() {
        FlowWatcher.clearRules()
        FlowWatcher.clearContext()
    }

    @Test
    fun decodesPortablePointRule() {
        val rules = RulePackLoader.decodeRules(
            """{
              "schemaVersion": 1,
              "id": "sample",
              "rules": [{
                "id": "bad_token",
                "name": "Bad token",
                "type": "POINT",
                "message": "Token missing",
                "matcher": {"eventType": "HTTP_RESPONSE", "pathRegex": ".*/login"},
                "conditions": [{"attribute": "http.param.token", "operator": "BLANK"}]
              }]
            }"""
        )
        FlowWatcher.registerAll(rules)

        val violations = FlowWatcher.emit(
            FlowEvent(
                EventType.HTTP_RESPONSE,
                "/login",
                attributes = mapOf("http.path" to "/login", "http.param.token" to "")
            )
        )

        assertEquals(1, violations.size)
        assertEquals("bad_token", violations.single().ruleId)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDuplicateRuleIds() {
        RulePackLoader.decode(
            """{"id":"bad","rules":[
              {"id":"same","name":"A","type":"POINT","message":"x"},
              {"id":"same","name":"B","type":"POINT","message":"x"}
            ]}"""
        )
    }

    @Test
    fun disabledRulesAreNotBuilt() {
        val rules = RulePackLoader.decodeRules(
            """{"id":"sample","rules":[{
              "id":"off","name":"Off","type":"POINT","enabled":false,"message":"x"
            }]}"""
        )
        assertTrue(rules.isEmpty())
    }

    @Test
    fun contextConditionsGatePointRules() {
        FlowWatcher.registerAll(RulePackLoader.decodeRules(
            """{"id":"context","rules":[{
              "id":"new-user-only","name":"New user only","type":"POINT","message":"missing",
              "matcher":{"name":"SIGN_IN"},
              "contextConditions":[{"attribute":"user.isNew","operator":"EQUALS","value":"true"}],
              "conditions":[{"attribute":"http.response.info.yd_device_id","operator":"BLANK"}]
            }]}"""
        ))

        assertTrue(FlowWatcher.emit("SIGN_IN").isEmpty())
        FlowWatcher.setContext("user.isNew", "true")
        assertEquals(1, FlowWatcher.emit("SIGN_IN").size)
        FlowWatcher.resetSession("next-user")
        assertTrue(FlowWatcher.contextSnapshot().isEmpty())
    }
}
