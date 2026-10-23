package com.shaovi.monitorx.core.config

import com.shaovi.monitorx.core.EventType
import com.shaovi.monitorx.core.FlowEvent
import com.shaovi.monitorx.core.FlowWatcher
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
    fun disabledValidRulesAreNotInstalled() {
        val rules = RulePackLoader.decodeRules(
            """{"id":"sample","rules":[{
              "id":"off","name":"Off","type":"POINT","enabled":false,"message":"x",
              "matcher":{"name":"event"},
              "conditions":[{"attribute":"value","operator":"BLANK"}]
            }]}"""
        )
        assertTrue(rules.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMalformedDisabledRules() {
        RulePackLoader.decode(
            """{"id":"sample","rules":[{
              "id":"off","name":"Off","type":"POINT","enabled":false,"message":"x"
            }]}"""
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsConditionWithoutRequiredValue() {
        RulePackLoader.decode(
            """{"id":"sample","rules":[{
              "id":"invalid","name":"Invalid","type":"POINT","message":"x",
              "matcher":{"name":"event"},
              "conditions":[{"attribute":"value","operator":"MATCHES"}]
            }]}"""
        )
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

    @Test
    fun paymentFunnelCompletesByPurchaseTokenOrReportsTimeout() {
        FlowWatcher.registerAll(RulePackLoader.decodeRules(
            """{"id":"payment","rules":[{
              "id":"missing-notice","name":"Missing notice","type":"FUNNEL","message":"missing",
              "start":{"eventType":"CUSTOM","name":"PAYMENT_SUCCEEDED"},
              "completion":{"eventType":"HTTP_REQUEST","pathRegex":".*/noticeResult"},
              "windowMs":15000,"correlationAttribute":"http.param.purchase_token"
            }]}"""
        ))

        FlowWatcher.emit(FlowEvent(
            type = EventType.CUSTOM,
            name = "PAYMENT_SUCCEEDED",
            elapsedRealtimeMs = 1_000,
            attributes = mapOf("http.param.purchase_token" to "paid-ok")
        ))
        FlowWatcher.emit(FlowEvent(
            type = EventType.HTTP_REQUEST,
            name = "/noticeResult",
            elapsedRealtimeMs = 2_000,
            attributes = mapOf(
                "http.path" to "/noticeResult",
                "http.param.purchase_token" to "paid-ok"
            )
        ))
        assertTrue(FlowWatcher.emit(FlowEvent(
            type = EventType.CUSTOM,
            name = "PAYMENT_SERVER_NOTIFY_WATCHDOG",
            elapsedRealtimeMs = 20_000
        )).isEmpty())

        FlowWatcher.emit(FlowEvent(
            type = EventType.CUSTOM,
            name = "PAYMENT_SUCCEEDED",
            elapsedRealtimeMs = 30_000,
            attributes = mapOf("http.param.purchase_token" to "missing")
        ))
        val violations = FlowWatcher.emit(FlowEvent(
            type = EventType.CUSTOM,
            name = "PAYMENT_SERVER_NOTIFY_WATCHDOG",
            elapsedRealtimeMs = 46_000
        ))
        assertEquals("missing-notice", violations.single().ruleId)
    }

}
