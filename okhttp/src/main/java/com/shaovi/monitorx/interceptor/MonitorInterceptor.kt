package com.shaovi.monitorx.interceptor

import android.net.Uri
import android.util.Log
import com.shaovi.monitorx.MonitorHelper
import com.shaovi.monitorx.data.MonitorData
import com.shaovi.monitorx.utils.*
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import okio.GzipSource
import java.io.EOFException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.min

class MonitorInterceptor : Interceptor {
    private var maxContentLength = 5L * 1024 * 1024

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val callId = System.identityHashCode(chain.call())
        if (!MonitorHelper.isOpenMonitor) {
            return chain.proceed(request)
        }
        val monitorData = MonitorData()
        fillRequestSnapshot(monitorData, request, readRequestBody = false)

        if (!monitorData.host.isWhiteHosts()) {
            return chain.proceed(request)
        }

        if (monitorData.host.isBlackHosts()) {
            return chain.proceed(request)
        }

        if (MonitorHelper.isFilterIPAddressHost && monitorData.host.isIpAddress()) {
            return chain.proceed(request)
        }

        monitorData.requestTime = Date().formatData(TIME_LONG)
        // Automatically learn baseUrl from host business requests for zero-config API debugging
        if (!hasLearnedBaseUrl.get()) {
            apiDebugBridgeRecordMethod?.let { (instance, method) ->
                runCatching {
                    method.invoke(instance, request.url.toString())
                    if (request.url.scheme.startsWith("http")) {
                        hasLearnedBaseUrl.set(true)
                    }
                }
            }
        }
        // Emit the request before network I/O. Business-flow rules can therefore distinguish
        // "the app never notified the server" from "the server call was slow or failed".
        fillRequestSnapshot(monitorData, request, readRequestBody = true)
        runCatching {
            com.shaovi.monitorx.anomaly.CoreAnomalyBridge.inspectRequest(monitorData)
        }

        // 在 Call 结束并从全局索引移除前保留本次指标对象的局部引用。
        val callMetrics = com.shaovi.monitorx.listener.MonitorEventListener.getMetrics(chain.call())
        val startTime = System.nanoTime()
        val response: Response
        try {
            response = chain.proceed(request)
        } catch (e: Exception) {
            monitorData.errorMsg = e.toString()
            fillRequestSnapshot(monitorData, request, readRequestBody = true)
            applyMetrics(monitorData, callMetrics)
            applySummaryDurations(
                monitorData,
                callMetrics,
                chainReturnNanos = null,
                processingEndNanos = System.nanoTime(),
                fallbackStartNanos = startTime
            )
            insert(monitorData)
            throw e
        }
        val chainReturnNanos = System.nanoTime()
        try {
            fillRequestSnapshot(monitorData, response.request, readRequestBody = true)

            val originalContentType = response.headers["x-original-content-type"]
            val contentType = response.headers["Content-Type"]
            val isEncryptedApi = response.headers["x-encryption-version"] != null
            val isWhite = originalContentType.isWhiteContentType() || contentType.isWhiteContentType() || isEncryptedApi
            if (!isWhite) {
                return response
            }

            monitorData.responseTime = Date().formatData(TIME_LONG)

            monitorData.protocol = response.protocol.toString()
            monitorData.responseCode = response.code
            monitorData.responseMessage = response.message
            monitorData.responseHeaders = response.headers.toJsonString()

            val responseBody = response.body

            val displayContentType = originalContentType ?: responseBody?.contentType()?.toString() ?: contentType
            if (!displayContentType.isNullOrBlank()) {
                monitorData.responseContentType = displayContentType
            }

            val bodyHasUnknownEncoding = bodyHasUnknownEncoding(response.headers)

            if (responseBody != null && response.promisesBody() && !bodyHasUnknownEncoding) {
                val source = responseBody.source()
                source.request(Long.MAX_VALUE) // Buffer the entire body.
                var buffer = source.buffer

                if (bodyGzipped(response.headers)) {
                    GzipSource(buffer.clone()).use { gzippedResponseBody ->
                        buffer = Buffer()
                        buffer.writeAll(gzippedResponseBody)
                    }
                }

                val charset: Charset = responseBody.contentType()?.charset(StandardCharsets.UTF_8)
                    ?: StandardCharsets.UTF_8

                if (responseBody.contentLength() != 0L && buffer.isProbablyUtf8()) {
                    val body = readFromBuffer(buffer.clone(), charset)
                    // 保存原始响应；JSON 格式化延迟到详情页真正展示时执行。
                    monitorData.responseBody = body
                }
                monitorData.responseContentLength = buffer.size
            }
            val processingEndNanos = System.nanoTime()
            applyMetrics(monitorData, callMetrics)
            applySummaryDurations(
                monitorData,
                callMetrics,
                chainReturnNanos,
                processingEndNanos,
                startTime
            )
            insert(monitorData)
            return response
        } catch (e: Exception) {
            Log.d("MonitorHelper", e.message ?: "")
            return response
        }
    }

    private fun insert(monitorData: MonitorData) {
        MonitorHelper.insert(monitorData)
        runCatching {
            com.shaovi.monitorx.anomaly.CoreAnomalyBridge.inspect(monitorData)
            com.shaovi.monitorx.anomaly.AnomalyManager.inspect(monitorData)
        }
    }

    private fun applyMetrics(
        monitorData: MonitorData,
        metrics: com.shaovi.monitorx.listener.MonitorEventListener.TimingMetrics?
    ) {
        metrics?.calculate()?.let {
            monitorData.queueDuration = it.queueMs
            monitorData.dnsDuration = it.dnsMs
            monitorData.connectDuration = it.connectMs
            monitorData.requestDuration = it.requestMs
            monitorData.serverDuration = it.serverMs
            monitorData.downloadDuration = it.downloadMs
        }
    }

    private fun applySummaryDurations(
        monitorData: MonitorData,
        metrics: com.shaovi.monitorx.listener.MonitorEventListener.TimingMetrics?,
        chainReturnNanos: Long?,
        processingEndNanos: Long,
        fallbackStartNanos: Long
    ) {
        val callStartNanos = metrics?.callStartNanos?.takeIf { it > 0L } ?: fallbackStartNanos
        val networkEndNanos = metrics?.responseBodyEndNanos
            ?.takeIf { it >= callStartNanos }
            ?: metrics?.callEndNanos?.takeIf { it >= callStartNanos }
            ?: chainReturnNanos
            ?: processingEndNanos
        monitorData.networkDuration = durationMillis(callStartNanos, networkEndNanos)
        monitorData.clientProcessingDuration = chainReturnNanos
            ?.takeIf { it > networkEndNanos }
            ?.let { durationMillis(networkEndNanos, it) }
            ?: 0L
        val businessEndNanos = maxOf(chainReturnNanos ?: networkEndNanos, networkEndNanos)
        monitorData.monitorDuration = durationMillis(businessEndNanos, processingEndNanos)
        // 端到端耗时只包含网络和必要客户端处理，不计入监控工具自身开销。
        monitorData.duration = durationMillis(callStartNanos, businessEndNanos)
    }

    private fun durationMillis(startNanos: Long, endNanos: Long): Long =
        TimeUnit.NANOSECONDS.toMillis((endNanos - startNanos).coerceAtLeast(0L))

    private fun bodyHasUnknownEncoding(headers: Headers): Boolean {
        val contentEncoding = headers["Content-Encoding"] ?: return false
        return !contentEncoding.equals("identity", ignoreCase = true) &&
                !contentEncoding.equals("gzip", ignoreCase = true)
    }

    private fun readFromBuffer(buffer: Buffer, charset: Charset?): String {
        val bufferSize = buffer.size
        val maxBytes = min(bufferSize, maxContentLength)
        var body: String = try {
            buffer.readString(maxBytes, charset!!)
        } catch (e: EOFException) {
            "\\n\\n--- Unexpected end of content ---"
        }
        if (bufferSize > maxContentLength) {
            body += "\\n\\n--- Content truncated ---"
        }
        return body
    }

    private fun bodyGzipped(headers: Headers): Boolean {
        return "gzip".equals(headers["Content-Encoding"], ignoreCase = true)
    }

    private fun fillRequestSnapshot(
        monitorData: MonitorData,
        request: Request,
        readRequestBody: Boolean
    ) {
        monitorData.method = request.method
        val url = request.url.toString()
        monitorData.url = url
        if (url.isNotBlank()) {
            val uri = Uri.parse(url)
            monitorData.host = uri.host
            monitorData.path = uri.path + if (uri.query != null) "?" + uri.query else ""
            monitorData.scheme = uri.scheme
        }
        monitorData.requestHeaders = request.headers.toJsonString()
        request.body?.contentType()?.let { monitorData.requestContentType = it.toString() }
        if (readRequestBody) {
            monitorData.requestBody = readRequestBody(request)
        }
    }

    private fun readRequestBody(request: Request): String? {
        val requestBody = request.body ?: return null
        if (bodyHasUnknownEncoding(request.headers) || requestBody.isDuplex() || requestBody.isOneShot()) {
            return null
        }
        return when (requestBody) {
            is MultipartBody -> buildMultipartBodyContent(requestBody)
            else -> {
                val buffer = Buffer()
                requestBody.writeTo(buffer)
                val charset: Charset =
                    requestBody.contentType()?.charset(StandardCharsets.UTF_8)
                        ?: StandardCharsets.UTF_8
                if (buffer.isProbablyUtf8()) buffer.readString(charset) else null
            }
        }
    }

    private fun buildMultipartBodyContent(requestBody: MultipartBody): String {
        val builder = StringBuilder()
        requestBody.parts.forEach { part ->
            val isStream =
                part.body.contentType()?.toString()?.contains("otcet-stream") == true
            val key = part.headers?.value(0)
            if (isStream) {
                builder.append(key).append("; value=文件流\n")
            } else {
                builder.append(key)
                    .append("; value=")
                    .append(part.body.readString())
                    .append('\n')
            }
        }
        return builder.toString()
    }

    companion object {
        private val hasLearnedBaseUrl = java.util.concurrent.atomic.AtomicBoolean(false)
        private val apiDebugBridgeRecordMethod by lazy {
            runCatching {
                val bridgeClass = Class.forName("com.shaovi.monitorx.api.ApiDebugBridge")
                val recordMethod = bridgeClass.getMethod("recordTrafficUrl", String::class.java)
                val instance = bridgeClass.getField("INSTANCE").get(null)
                Pair(instance, recordMethod)
            }.getOrNull()
        }
    }
}
