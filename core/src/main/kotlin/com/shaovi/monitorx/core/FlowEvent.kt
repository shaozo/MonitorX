package com.shaovi.monitorx.core

import java.util.UUID

enum class EventType {
    HTTP_REQUEST,
    HTTP_RESPONSE,
    ACTIVITY_LIFECYCLE,
    CUSTOM
}

data class FlowEvent(
    val type: EventType,
    val name: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val elapsedRealtimeMs: Long = MonitorClock.elapsedRealtimeMs(),
    val sessionId: String = FlowWatcher.currentSessionId,
    val attributes: Map<String, String?> = emptyMap(),
    val sourceId: String? = null,
    val id: String = UUID.randomUUID().toString()
)

internal object MonitorClock {
    private val startedAtNanos = System.nanoTime()

    fun elapsedRealtimeMs(): Long = (System.nanoTime() - startedAtNanos) / 1_000_000L
}
