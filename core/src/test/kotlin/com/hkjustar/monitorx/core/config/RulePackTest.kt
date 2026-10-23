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

    @Test
    fun decodesGemgalaBusinessRulesAssetFile() {
        val candidates = listOf(
            java.io.File("../../app/src/demo/assets/monitorx/gemgala-rules.json"),
            java.io.File("../app/src/demo/assets/monitorx/gemgala-rules.json"),
            java.io.File("app/src/demo/assets/monitorx/gemgala-rules.json")
        )
        val file = candidates.firstOrNull { it.exists() }
        org.junit.Assert.assertNotNull("gemgala-rules.json should exist", file)
        val pack = RulePackLoader.decode(file!!.readText())
        assertEquals("gemgala-business-rules", pack.id)
        assertTrue(pack.rules.size >= 17)

        val rules = pack.toRules()
        FlowWatcher.registerAll(rules)

        // 验证业务错误规则 gemgala_core_biz_error 能准确命中
        val bizErrorViolations = FlowWatcher.emit(FlowEvent(
            type = EventType.HTTP_RESPONSE,
            name = "/gift/giveGifts",
            attributes = mapOf(
                "http.path" to "/gift/giveGifts",
                "http.response.result" to "2001",
                "http.response.msg" to "Balance not enough"
            )
        ))
        assertTrue(bizErrorViolations.any { it.ruleId == "gemgala_core_biz_error" })

        // 验证送礼短时间重复提交规则 gemgala_duplicate_gift_send
        val giftEvent1 = FlowEvent(
            type = EventType.HTTP_RESPONSE,
            name = "/gift/giveGifts",
            elapsedRealtimeMs = 1000,
            attributes = mapOf("http.path" to "/gift/giveGifts", "http.response.result" to "1000")
        )
        val giftEvent2 = FlowEvent(
            type = EventType.HTTP_RESPONSE,
            name = "/gift/giveGifts",
            elapsedRealtimeMs = 2000,
            attributes = mapOf("http.path" to "/gift/giveGifts", "http.response.result" to "1000")
        )
        FlowWatcher.emit(giftEvent1)
        val giftViolations = FlowWatcher.emit(giftEvent2)
        assertTrue(giftViolations.any { it.ruleId == "gemgala_duplicate_gift_send" })
    }
}
