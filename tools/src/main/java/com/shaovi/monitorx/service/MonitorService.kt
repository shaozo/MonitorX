package com.shaovi.monitorx.service

import android.os.SystemClock
import com.android.local.service.annotation.Get
import com.android.local.service.annotation.Page
import com.android.local.service.annotation.Service
import com.shaovi.monitorx.MonitorHelper
import com.shaovi.monitorx.api.ApiDebugBridge
import com.shaovi.monitorx.data.MonitorData
import com.shaovi.monitorx.enum.SPValueType
import com.shaovi.monitorx.im.ImMessageCollector
import com.shaovi.monitorx.im.ImMessageRecord
import com.google.gson.Gson
import com.shaovi.monitorx.mock.MockHelper
import com.shaovi.monitorx.utils.ServiceDataProvider
import com.shaovi.monitorx.utils.lastUpdateDataId
import com.shaovi.monitorx.weaknetwork.WeakNetworkHelper
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@Service(port = 9527)
abstract class MonitorService {

    companion object {
        private val activeApiDebugCalls = ConcurrentHashMap<String, Call>()
    }

    @Page("index")
    fun showMonitorPage() = "monitor_index.html"

    @Page("sp_index")
    fun showSpPage() = "sp_index.html"

    @Page("im_index")
    fun showImPage() = "im_index.html"

    @Page("api_debug_index")
    fun showApiDebugPage() = "api_debug_index.html"

    @Page("performance_index")
    fun showPerformancePage() = "performance_index.html"

    @Page("business_monitor_index")
    fun showBusinessMonitorPage() = "business_monitor_index.html"

    @Page("mqtt_index")
    fun showMqttPage() = "mqtt_index.html"

    // ── Performance & Page APIs ──
    @Get("getCurrentPage")
    fun getCurrentPageInfo(): Map<String, Any> {
        return com.shaovi.monitorx.performance.AppPageTracker.getCurrentPageInfo()
    }

    @Get("setPerformanceMonitor")
    fun setPerformanceMonitor(isOpen: Boolean): Boolean {
        if (isOpen) {
            com.shaovi.monitorx.performance.SoloXPerformanceCollector.startMonitoring()
        } else {
            com.shaovi.monitorx.performance.SoloXPerformanceCollector.stopMonitoring()
        }
        return com.shaovi.monitorx.performance.SoloXPerformanceCollector.isMonitoring
    }

    @Get("getPerformanceMetrics")
    fun getPerformanceMetrics(): Map<String, Any> {
        val collector = com.shaovi.monitorx.performance.SoloXPerformanceCollector
        return mapOf(
            "isMonitoring" to collector.isMonitoring,
            "isRecordingSession" to collector.isRecordingSession,
            "latest" to collector.getLatestSnapshotMap(),
            "history" to collector.getHistorySnapshotsList()
        )
    }

    @Get("startPerformanceSession")
    fun startPerformanceSession(name: String = ""): Boolean {
        return com.shaovi.monitorx.performance.SoloXPerformanceCollector.startSession(name)
    }

    @Get("stopPerformanceSession")
    fun stopPerformanceSession(): Map<String, Any>? {
        return com.shaovi.monitorx.performance.SoloXPerformanceCollector.stopSession()
    }

    // ── Looper Block & FlameGraph APIs ──
    @Get("setBlockSampler")
    fun setBlockSampler(isOpen: Boolean, thresholdMs: Long = 200L): Boolean {
        if (isOpen) {
            com.shaovi.monitorx.performance.BlockFlameSampler.start(blockThreshold = thresholdMs)
        } else {
            com.shaovi.monitorx.performance.BlockFlameSampler.stop()
        }
        return com.shaovi.monitorx.performance.BlockFlameSampler.isEnabled
    }

    @Get("getBlockRecords")
    fun getBlockRecords(): Map<String, Any> {
        val sampler = com.shaovi.monitorx.performance.BlockFlameSampler
        return mapOf(
            "isEnabled" to sampler.isEnabled,
            "records" to sampler.getRecordList()
        )
    }

    @Get("getBlockFlameGraphData")
    fun getBlockFlameGraphData(id: String): Map<String, Any>? {
        return com.shaovi.monitorx.performance.BlockFlameSampler.getFlameGraphData(id)
    }

    // ── Business & Timing Anomaly APIs ──
    @Get("getAnomalyRecords")
    fun getAnomalyRecords(): Map<String, Any> {
        val all = com.shaovi.monitorx.anomaly.AnomalyManager.getAllRecords()
        return mapOf(
            "total" to all.size,
            "unnotifiedCount" to all.count { !it.notifiedWeb },
            "records" to all
        )
    }

    @Get("getAnomalyStatus")
    fun getAnomalyStatus(): Map<String, Any> {
        val manager = com.shaovi.monitorx.anomaly.AnomalyManager
        return mapOf(
            "status" to manager.getStatus(),
            "rulePacks" to manager.getLoadedRulePacks()
        )
    }

    @Get("getAnomalyRules")
    fun getAnomalyRules(): Map<String, Any> {
        val packs = com.shaovi.monitorx.anomaly.AnomalyManager.getLoadedRulePacks()
        return mapOf(
            "totalPacks" to packs.size,
            "totalRules" to packs.sumOf { it.totalRules },
            "enabledRules" to packs.sumOf { it.enabledRules },
            "rulePacks" to packs
        )
    }

    @Get("setAnomalyEnabled")
    fun setAnomalyEnabled(enabled: Boolean): Boolean {
        com.shaovi.monitorx.anomaly.AnomalyManager.setEnabled(enabled)
        com.shaovi.monitorx.core.FlowWatcher.enabled = enabled
        return com.shaovi.monitorx.anomaly.AnomalyManager.getStatus().enabled
    }

    @Get("getNewAnomalies")
    fun getNewAnomalies(): Map<String, Any> {
        val newRecords = com.shaovi.monitorx.anomaly.AnomalyManager.getNewAnomalies()
        return mapOf(
            "count" to newRecords.size,
            "records" to newRecords
        )
    }

    @Get("markAnomaliesNotified")
    fun markAnomaliesNotified(ids: String = ""): Boolean {
        if (ids.isNotBlank()) {
            val idList = ids.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            com.shaovi.monitorx.anomaly.AnomalyManager.markNotified(idList)
        }
        return true
    }

    @Get("clearAnomalies")
    fun clearAnomalies(): Boolean {
        com.shaovi.monitorx.anomaly.AnomalyManager.clearRecords()
        return true
    }

    @Get("query")
    fun queryMonitorData(keyword: String = "", content: String = "", limit: Int, offset: Int): MutableList<MonitorData> {
        return ServiceDataProvider.getMonitorDataList(keyword, content, limit, offset)
    }

    @Get("clean")
    fun cleanMonitorData() {
        MonitorHelper.deleteAll()
    }

    @Get("apiDebugExecute")
    fun executeApiDebugRequest(
        httpMethod: String,
        url: String,
        query: String = "",
        headers: String = "",
        body: String = "",
        contentType: String = "application/json; charset=utf-8",
        timeoutMillis: Long = 60_000L,
        requestId: String = ""
    ): ApiDebugWebResponse {
        val executor = ApiDebugBridge.getEffectiveExecutor()
            ?: return ApiDebugWebResponse(error = "业务请求执行器尚未初始化，请发起一次网络请求或配置 ApiDebugBridge.executor")
        val timeout = timeoutMillis.coerceIn(1_000L, 300_000L)
        val request = runCatching {
            val rawUrl = url.trim()
            require(rawUrl.isNotEmpty()) { "请输入 URL" }
            val baseUrl = executor.baseUrl.toHttpUrlOrNull() ?: error("当前 Base URL 无效")
            val resolvedUrl = rawUrl.toHttpUrlOrNull() ?: baseUrl.resolve(rawUrl)
                ?: error("URL 无效：$rawUrl")
            val urlBuilder = resolvedUrl.newBuilder()
            parseKeyValueLines(query, '=') { name, value -> urlBuilder.addQueryParameter(name, value) }
            val headersBuilder = Headers.Builder()
            parseKeyValueLines(headers, ':') { name, value -> headersBuilder.add(name, value) }
            val method = httpMethod.uppercase().takeIf { it in API_DEBUG_METHODS } ?: "POST"
            val requestBody = if (method == "GET") null else body.toRequestBody(
                contentType.ifBlank { "text/plain; charset=utf-8" }.toMediaType()
            )
            Request.Builder().url(urlBuilder.build()).headers(headersBuilder.build())
                .method(method, requestBody).build()
        }.getOrElse { return ApiDebugWebResponse(error = it.message ?: "请求参数无效") }

        val result = AtomicReference<ApiDebugWebResponse>()
        val latch = CountDownLatch(1)
        val startedAt = System.nanoTime()
        val call = executor.execute(request, timeout, object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                result.set(ApiDebugWebResponse(
                    error = if (call.isCanceled()) "请求已取消" else e.message.orEmpty(),
                    sentBody = ApiDebugBridge.lastSentRequestBody,
                    sentUrl = ApiDebugBridge.lastSentRequestUrl,
                    sentHeaders = ApiDebugBridge.lastSentRequestHeaders
                ))
                latch.countDown()
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    result.set(ApiDebugWebResponse(
                        code = it.code,
                        message = it.message,
                        durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt),
                        headers = it.headers.toString(),
                        body = runCatching { it.body?.string().orEmpty() }
                            .getOrElse { error -> "读取响应失败：${error.message}" },
                        sentBody = ApiDebugBridge.lastSentRequestBody,
                        sentUrl = ApiDebugBridge.lastSentRequestUrl,
                        sentHeaders = ApiDebugBridge.lastSentRequestHeaders
                    ))
                }
                latch.countDown()
            }
        })
        if (requestId.isNotBlank()) activeApiDebugCalls[requestId] = call
        try {
            if (!latch.await(timeout + 1_000L, TimeUnit.MILLISECONDS)) {
                call.cancel()
                return ApiDebugWebResponse(
                    error = "请求超时",
                    sentBody = ApiDebugBridge.lastSentRequestBody,
                    sentUrl = ApiDebugBridge.lastSentRequestUrl,
                    sentHeaders = ApiDebugBridge.lastSentRequestHeaders
                )
            }
            return result.get() ?: ApiDebugWebResponse(error = "请求未返回结果")
        } finally {
            if (requestId.isNotBlank()) activeApiDebugCalls.remove(requestId, call)
        }
    }

    @Get("apiDebugCancel")
    fun cancelApiDebugRequest(requestId: String): Boolean {
        return activeApiDebugCalls.remove(requestId)?.let {
            it.cancel()
            true
        } ?: false
    }

    private fun parseKeyValueLines(text: String, separator: Char, action: (String, String) -> Unit) {
        val trimmed = text.trim()
        // Support JSON-formatted headers (e.g. from capture-load)
        if (separator == ':' && trimmed.startsWith("{")) {
            runCatching {
                val json = org.json.JSONObject(trimmed)
                json.keys().forEach { key -> action(key, json.optString(key).orEmpty()) }
            }
            return
        }
        text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
            val index = line.indexOf(separator)
            if (index <= 0) return@forEach  // skip lines without valid separator instead of failing
            action(line.substring(0, index).trim(), line.substring(index + 1).trim())
        }
    }

    @Get("imQuery")
    fun queryImData(limit: Int = 100, offset: Int = 0, type: String = ""): List<ImMessageRecord> {
        return ImMessageCollector.query(limit, offset, type.toIntOrNull())
    }

    @Get("imClean")
    fun cleanImData() {
        ImMessageCollector.clear()
    }

    @Get("imAutoFetch")
    fun autoFetchImData(lastFetchId: Long, type: String = ""): List<ImMessageRecord> {
        val targetType = type.toIntOrNull()
        repeat(8) {
            val data = ImMessageCollector.queryAfterId(lastFetchId, type = targetType)
            if (data.isNotEmpty() || lastFetchId == 0L) {
                return data
            }
            SystemClock.sleep(250)
        }
        return emptyList()
    }

    @Get("autoFetch")
    fun autoFetchData(keyword: String = "", content: String = "", lastFetchId: Long): MutableList<MonitorData> {
        repeat(8) {
            if (lastFetchId != lastUpdateDataId || lastFetchId == 0L) {
                return ServiceDataProvider.getMonitorDataByLastId(keyword, content, lastFetchId)
            }
            SystemClock.sleep(250)
        }
        return mutableListOf()
    }

    @Get("sharedPrefs")
    fun getSharedPrefsFilesData() = MonitorHelper.getSharedPrefsFilesData()

    @Get("getSharedPrefsByFileName")
    fun getSharedPrefsByFileName(fileName: String) = MonitorHelper.getSpFile(fileName)

    @Get("updateSpValue")
    fun updateSpValue(fileName: String, key: String, value: String, valueType: String) {
        val realValue = when (valueType) {
            SPValueType.Int.value -> value.toIntOrNull()
            SPValueType.Double.value -> value.toDoubleOrNull()
            SPValueType.Long.value -> value.toLongOrNull()
            SPValueType.Float.value -> value.toFloatOrNull()
            SPValueType.Boolean.value -> value.toBoolean()
            else -> value
        }
        MonitorHelper.updateSpValue(fileName, key, realValue)
    }

    @Get("setWeakNetConfig")
    fun configWeak(weakType: String) {
        WeakNetworkHelper.configWeak(weakType)
    }

    @Get("setMockConfig")
    fun setMockConfig(mockBaseUrl: String, mockPath: String, mockResponse: String) {
        MockHelper.configMock(mockBaseUrl, mockPath, mockResponse)
    }

    @Get("openMockService")
    fun openMockService(isOpen: Boolean) {
        MockHelper.isOpen = isOpen
    }

    @Get("getMockRules")
    fun getMockRules(): String {
        val result = mapOf(
            "isOpen" to MockHelper.isOpen,
            "rules" to MockHelper.rules
        )
        return Gson().toJson(result)
    }

    @Get("saveMockRule")
    fun saveMockRule(
        id: String,
        name: String,
        path: String,
        method: String,
        statusCode: Int,
        responseBody: String,
        mockBaseUrl: String,
        delayMs: Long,
        isEnabled: Boolean
    ) {
        val rule = com.shaovi.monitorx.mock.MockRule(
            id = if (id.isBlank()) java.util.UUID.randomUUID().toString() else id,
            name = name,
            path = path,
            method = if (method.isBlank()) "ALL" else method,
            statusCode = if (statusCode <= 0) 200 else statusCode,
            responseBody = responseBody,
            mockBaseUrl = mockBaseUrl,
            delayMs = delayMs,
            isEnabled = isEnabled
        )
        MockHelper.addOrUpdateRule(rule)
    }

    @Get("deleteMockRule")
    fun deleteMockRule(id: String) {
        MockHelper.deleteRule(id)
    }

    @Get("toggleMockRule")
    fun toggleMockRule(id: String, isEnabled: Boolean) {
        MockHelper.toggleRule(id, isEnabled)
    }
}

data class ApiDebugWebResponse(
    val code: Int = 0,
    val message: String = "",
    val durationMillis: Long = 0L,
    val headers: String = "",
    val body: String = "",
    val error: String = "",
    /** The request body actually sent (after interceptor processing: common params + signing). */
    val sentBody: String = "",
    /** The request URL actually sent (after domain switching). */
    val sentUrl: String = "",
    /** The request headers actually sent. */
    val sentHeaders: String = ""
)

private val API_DEBUG_METHODS = setOf("GET", "POST", "PUT", "PATCH", "DELETE")
