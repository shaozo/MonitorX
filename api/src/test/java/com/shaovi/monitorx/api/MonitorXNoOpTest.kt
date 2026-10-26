package com.shaovi.monitorx.api

import org.junit.Assert.assertFalse
import org.junit.Test

class MonitorXNoOpTest {
    @Test
    fun apiWithoutRuntimeUsesNoOpBackend() {
        assertFalse(MonitorX.isRuntimeAvailable())
        MonitorX.setEnabled(true)
        assertFalse(MonitorX.isEnabled())
        // Verified: Calling diagnostic flow APIs without runtime is safe and no-op
        MonitorX.setContext("is_new_user", "true")
        MonitorX.setContext(mapOf("env" to "prod"))
        MonitorX.removeContext("is_new_user")
        MonitorX.clearContext()
        MonitorX.setBusinessMonitoringEnabled(true)
        MonitorX.emit("TEST_EVENT", mapOf("k" to "v"))
        MonitorX.resetSession()
    }
}
