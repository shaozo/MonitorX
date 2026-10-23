package com.shaovi.monitorx.api

import org.junit.Assert.assertFalse
import org.junit.Test

class MonitorXNoOpTest {
    @Test
    fun apiWithoutRuntimeUsesNoOpBackend() {
        assertFalse(MonitorX.isRuntimeAvailable())
        MonitorX.setEnabled(true)
        assertFalse(MonitorX.isEnabled())
    }
}
