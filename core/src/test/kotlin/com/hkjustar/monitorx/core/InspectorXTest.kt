package com.hkjustar.monitorx.core

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class InspectorXTest {
    @After fun tearDown() = InspectorX.clearRules()

    @Test fun `facade installs and evaluates json rule pack`() {
        InspectorX.installRulePack(
            """
            {"id":"sample","rules":[{
              "id":"missing-token","name":"Missing token","type":"POINT",
              "message":"token is required","matcher":{"name":"checkout"},
              "conditions":[{"attribute":"token","operator":"BLANK"}]
            }]}
            """.trimIndent()
        )

        val violations = InspectorX.emit("checkout", mapOf("token" to ""))
        assertEquals("missing-token", violations.single().ruleId)
    }
}
