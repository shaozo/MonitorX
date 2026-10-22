package com.hkjustar.monitor.okhttp.api

import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request

interface ApiDebugExecutor {
    val baseUrl: String
    fun execute(request: Request, timeoutMillis: Long, callback: Callback): Call
}

object ApiDebugBridge {
    @Volatile
    var executor: ApiDebugExecutor? = null

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
}
