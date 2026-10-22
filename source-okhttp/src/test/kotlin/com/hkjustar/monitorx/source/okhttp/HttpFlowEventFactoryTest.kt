package com.hkjustar.monitorx.source.okhttp

import org.junit.Assert.assertEquals
import org.junit.Test

class HttpFlowEventFactoryTest {
    @Test
    fun combinesQueryAndJsonBodyParameters() {
        val event = HttpFlowEventFactory.response(
            HttpEventSnapshot(
                url = "https://example.test/register?source=invite",
                method = "POST",
                requestBody = """{"latitude":"99999","enabled":true}"""
            )
        )

        assertEquals("/register", event.attributes["http.path"])
        assertEquals("invite", event.attributes["http.param.source"])
        assertEquals("99999", event.attributes["http.param.latitude"])
        assertEquals("true", event.attributes["http.param.enabled"])
    }

    @Test
    fun flattensNestedResponseJson() {
        val event = HttpFlowEventFactory.response(
            HttpEventSnapshot(
                url = "https://example.test/game/getUserSignInRecord",
                responseBody = """{"info":{"isNewUser":1,"yd_device_id":"device-1"}}"""
            )
        )

        assertEquals("1", event.attributes["http.response.info.isNewUser"])
        assertEquals("device-1", event.attributes["http.response.info.yd_device_id"])
    }
}
