package com.shaovi.monitorx.api

import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request

interface ApiDebugExecutor {
    val baseUrl: String
    fun execute(request: Request, timeoutMillis: Long, callback: Callback): Call
}

object ApiDebugBridge {
    @Volatile
    var executor: ApiDebugExecutor? = null

    /** Automatically captured host business client, injected by plugin without manual code. */
    @Volatile
    var autoClient: okhttp3.OkHttpClient? = null

    /** Automatically inferred baseUrl learned from real network traffic or builder. */
    @Volatile
    var autoBaseUrl: String = ""

    /** Captured final request body after all interceptors have processed it. */
    @Volatile
    var lastSentRequestBody: String = ""

    /** Captured final request URL after all interceptors have processed it. */
    @Volatile
    var lastSentRequestUrl: String = ""

    /** Captured final request headers after all interceptors have processed it. */
    @Volatile
    var lastSentRequestHeaders: String = ""

    fun clearCapturedRequest() {
        lastSentRequestBody = ""
        lastSentRequestUrl = ""
        lastSentRequestHeaders = ""
    }

    /** Learns baseUrl automatically from real outgoing requests so debug console works out-of-the-box. */
    fun recordTrafficUrl(url: String) {
        if (autoBaseUrl.isBlank()) {
            runCatching {
                val httpUrl = url.toHttpUrlOrNull() ?: return
                val host = httpUrl.host.lowercase()
                val isInfrastructureOrSdk = listOf(
                    "aliyuncs.com", "qcloud.com", "myqcloud.com", "amazonaws.com",
                    "google.com", "googleapis.com", "facebook.com", "crashlytics.com", "zego.im"
                ).any { rule -> host == rule || host.endsWith(".$rule") }
                if (isInfrastructureOrSdk) {
                    return
                }
                val portSuffix = if (httpUrl.port != 80 && httpUrl.port != 443) ":${httpUrl.port}" else ""
                autoBaseUrl = "${httpUrl.scheme}://${httpUrl.host}$portSuffix/"
            }
        }
    }

    /** Registers a captured OkHttpClient automatically as the default fallback debug executor. */
    fun registerAutoClient(client: okhttp3.OkHttpClient, baseUrl: String = "") {
        autoClient = client
        if (baseUrl.isNotBlank()) autoBaseUrl = baseUrl
    }

    /** Returns user-defined custom executor if set; otherwise falls back to automatically injected executor. */
    fun getEffectiveExecutor(): ApiDebugExecutor? {
        val custom = executor
        if (custom != null) return custom
        val client = autoClient ?: return null
        return object : ApiDebugExecutor {
            override val baseUrl: String
                get() = autoBaseUrl.ifBlank { "http://localhost/" }

            override fun execute(request: Request, timeoutMillis: Long, callback: Callback): Call {
                clearCapturedRequest()
                val requestClient = client.newBuilder()
                    .connectTimeout(timeoutMillis, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .writeTimeout(timeoutMillis, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .readTimeout(timeoutMillis, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .callTimeout(timeoutMillis, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .build()
                return requestClient.newCall(request).also { it.enqueue(callback) }
            }
        }
    }
}
