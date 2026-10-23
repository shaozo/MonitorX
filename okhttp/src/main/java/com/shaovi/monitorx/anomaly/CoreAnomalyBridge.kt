package com.shaovi.monitorx.anomaly

import com.shaovi.monitorx.data.MonitorData
import com.shaovi.monitorx.runtime.MonitorEnvironment
import com.shaovi.monitorx.core.FlowEvent
import com.shaovi.monitorx.core.FlowWatcher
import com.shaovi.monitorx.core.Violation
import com.shaovi.monitorx.core.ViolationReporter
import com.shaovi.monitorx.source.okhttp.HttpEventSnapshot
import com.shaovi.monitorx.source.okhttp.HttpFlowEventFactory

/** Connects the reusable MonitorX rule engine to the existing Monitor UI and PC console. */
internal object CoreAnomalyBridge : ViolationReporter {
    @Volatile
    private var installed = false

    fun install() {
        if (installed) return
        synchronized(this) {
            if (installed) return
            FlowWatcher.addReporter(this)
            installed = true
        }
    }

    fun inspect(data: MonitorData) {
        install()
        FlowWatcher.emit(data.toFlowEvent())
    }

    fun inspectRequest(data: MonitorData) {
        install()
        FlowWatcher.emit(data.toHttpSnapshot().let(HttpFlowEventFactory::request))
    }

    override fun report(violation: Violation) {
        AnomalyManager.record(violation.toAnomalyRecord())
    }

    private fun MonitorData.toFlowEvent(): FlowEvent {
        return HttpFlowEventFactory.response(toHttpSnapshot())
    }

    private fun MonitorData.toHttpSnapshot(): HttpEventSnapshot {
        return HttpEventSnapshot(
                url = url.orEmpty(),
                path = path.orEmpty(),
                method = method.orEmpty(),
                requestHeaders = requestHeaders.orEmpty(),
                requestBody = requestBody.orEmpty(),
                responseBody = responseBody.orEmpty(),
                pageName = MonitorEnvironment.currentPageInfo()["activitySimple"]?.toString().orEmpty(),
                sourceId = id.toString()
            )
    }

    private fun Violation.toAnomalyRecord(): AnomalyRecord {
        val path = event.attributes["http.path"].orEmpty().ifBlank { event.name }
        val paramKey = metadata["paramKey"].orEmpty().ifBlank { "事件" }
        return AnomalyRecord(
            id = id,
            timestamp = event.timestampMs,
            ruleId = ruleId,
            ruleName = ruleName,
            severity = severity.name,
            url = event.attributes["http.url"].orEmpty(),
            path = path,
            method = event.attributes["http.method"].orEmpty(),
            paramKey = paramKey,
            actualValue = metadata["actualValue"],
            alertMessage = message,
            solution = solution,
            monitorDataId = event.sourceId?.toLongOrNull() ?: 0L,
            activityName = event.attributes["page.activity"].orEmpty()
        )
    }

}
