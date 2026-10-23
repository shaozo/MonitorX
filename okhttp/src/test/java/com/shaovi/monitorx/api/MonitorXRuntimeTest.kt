package com.shaovi.monitorx.api

import org.junit.Assert.assertTrue
import org.junit.Test

class MonitorXRuntimeTest {
    @Test
    fun apiFindsRealBackendWhenRuntimeIsPackaged() {
        assertTrue(MonitorX.isRuntimeAvailable())
    }
}
