package com.hkjustar.monitorx.core

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlowWatcherTest {
    @After
    fun tearDown() {
        FlowWatcher.clearRules()
        FlowWatcher.resetSession()
    }

    @Test
    fun dependencyReportsTriggerBeforePrerequisite() {
        FlowWatcher.register(
            DependencyRule(
                id = "login_requires_sdk",
                name = "SDK not ready",
                prerequisite = Matchers.event("SDK_READY"),
                trigger = Matchers.event("LOGIN"),
                message = "SDK must be ready"
            )
        )

        assertEquals(1, FlowWatcher.emit("LOGIN").size)
        assertTrue(FlowWatcher.emit("SDK_READY").isEmpty())
        assertTrue(FlowWatcher.emit("LOGIN").isEmpty())
    }

    @Test
    fun forbiddenBeforeAllowsEventsAfterGate() {
        FlowWatcher.register(
            ForbiddenBeforeRule(
                id = "privacy_first",
                name = "Privacy ordering",
                allowedAfter = Matchers.event("PRIVACY_ACCEPTED"),
                forbidden = Matchers.event("DEVICE_UPLOAD"),
                message = "Privacy must be accepted"
            )
        )

        assertEquals(1, FlowWatcher.emit("DEVICE_UPLOAD").size)
        FlowWatcher.emit("PRIVACY_ACCEPTED")
        assertTrue(FlowWatcher.emit("DEVICE_UPLOAD").isEmpty())
    }

    @Test
    fun rateUsesMonotonicEventTime() {
        FlowWatcher.register(
            RateRule(
                id = "double_submit",
                name = "Double submit",
                matcher = Matchers.event("SUBMIT"),
                windowMs = 500,
                maxCount = 1,
                message = { "Submitted $it times" }
            )
        )

        val first = FlowEvent(EventType.CUSTOM, "SUBMIT", elapsedRealtimeMs = 100)
        val second = FlowEvent(EventType.CUSTOM, "SUBMIT", elapsedRealtimeMs = 200)
        assertTrue(FlowWatcher.emit(first).isEmpty())
        assertEquals(1, FlowWatcher.emit(second).size)
    }

    @Test
    fun resetSessionClearsRuleState() {
        FlowWatcher.register(
            DependencyRule(
                id = "session_dependency",
                name = "Session dependency",
                prerequisite = Matchers.event("READY"),
                trigger = Matchers.event("ACTION"),
                message = "Not ready"
            )
        )
        FlowWatcher.emit("READY")
        assertTrue(FlowWatcher.emit("ACTION").isEmpty())

        FlowWatcher.resetSession("new-session")

        assertEquals(1, FlowWatcher.emit("ACTION").size)
    }

    @Test
    fun funnelReportsExpiredStartOnNextEvent() {
        FlowWatcher.register(
            FunnelRule(
                id = "reward_dialog",
                name = "Reward dialog missing",
                start = Matchers.event("REWARD_SUCCESS"),
                completion = Matchers.event("REWARD_DIALOG"),
                timeoutMs = 2_000,
                message = "Dialog was not shown"
            )
        )

        FlowWatcher.emit(FlowEvent(EventType.CUSTOM, "REWARD_SUCCESS", elapsedRealtimeMs = 100))
        val violations = FlowWatcher.emit(FlowEvent(EventType.CUSTOM, "TICK", elapsedRealtimeMs = 2_101))
        assertEquals(1, violations.size)
    }
}
