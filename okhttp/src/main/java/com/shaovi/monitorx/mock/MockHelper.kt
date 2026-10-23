package com.shaovi.monitorx.mock

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import java.util.concurrent.CopyOnWriteArrayList

object MockHelper {

    private const val PREF_NAME = "monitorx_mock_prefs"
    private const val KEY_IS_OPEN = "key_mock_is_open"
    private const val KEY_RULES = "key_mock_rules"
    private val gson = Gson()
    private var appContext: Context? = null

    var isOpen: Boolean = false
        set(value) {
            field = value
            saveToPrefs()
        }

    val rules = CopyOnWriteArrayList<MockRule>()

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        loadFromPrefs()
    }

    private fun loadFromPrefs() {
        val ctx = appContext ?: return
        runCatching {
            val sp = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            isOpen = sp.getBoolean(KEY_IS_OPEN, false)
            val json = sp.getString(KEY_RULES, null)
            if (!json.isNullOrBlank()) {
                val listType = object : TypeToken<List<MockRule>>() {}.type
                val savedRules: List<MockRule> = gson.fromJson(json, listType)
                rules.clear()
                rules.addAll(savedRules)
            }
        }
    }

    private fun saveToPrefs() {
        val ctx = appContext ?: return
        runCatching {
            val sp = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            sp.edit()
                .putBoolean(KEY_IS_OPEN, isOpen)
                .putString(KEY_RULES, gson.toJson(rules))
                .apply()
        }
    }

    fun addOrUpdateRule(rule: MockRule) {
        val index = rules.indexOfFirst { it.id == rule.id }
        if (index >= 0) {
            rules[index] = rule
        } else {
            rules.add(0, rule)
        }
        saveToPrefs()
    }

    fun deleteRule(id: String) {
        rules.removeAll { it.id == id }
        saveToPrefs()
    }

    fun toggleRule(id: String, enabled: Boolean) {
        rules.find { it.id == id }?.let {
            it.isEnabled = enabled
            saveToPrefs()
        }
    }

    fun findMatchingRule(path: String?, method: String? = null): MockRule? {
        if (!isOpen || path.isNullOrBlank()) return null
        return rules.firstOrNull { rule ->
            rule.isEnabled &&
                    (rule.method.equals("ALL", ignoreCase = true) || method == null || rule.method.equals(method, ignoreCase = true)) &&
                    (path.contains(rule.path) || rule.path.contains(path))
        }
    }

    // --- Legacy backwards compatibility properties ---
    var mockBaseUrl: String = ""
        get() = rules.firstOrNull { it.mockBaseUrl.isNotBlank() }?.mockBaseUrl ?: field
        set(value) {
            field = value
            if (value.isNotBlank() && rules.none { it.mockBaseUrl == value }) {
                addOrUpdateRule(MockRule(name = "默认网络 Mock", path = mockPaths, mockBaseUrl = value))
            }
        }

    var mockPaths: String = ""
        get() = rules.joinToString(",") { it.path }
        set(value) {
            field = value
        }

    var mockResponse: String = ""
        get() = rules.firstOrNull { it.responseBody.isNotBlank() }?.responseBody ?: field
        set(value) {
            field = value
            if (value.isNotBlank() && rules.none { it.responseBody == value }) {
                addOrUpdateRule(MockRule(name = "默认响应 Mock", path = mockPaths, responseBody = value))
            }
        }

    var mockSuccessResponseCode: Int = 200

    fun isMockPath(path: String?): Boolean {
        return findMatchingRule(path) != null
    }

    fun isMockByCustomResponse(path: String?, method: String? = null): Boolean {
        val rule = findMatchingRule(path, method) ?: return false
        return rule.responseBody.isNotBlank()
    }

    fun isMockByNetworkResponse(path: String?, method: String? = null): Boolean {
        val rule = findMatchingRule(path, method) ?: return false
        return rule.mockBaseUrl.isNotBlank() && rule.responseBody.isBlank()
    }

    fun configMock(mockBaseUrl: String, mockPath: String, mockResponse: String) {
        val rule = MockRule(
            name = if (mockResponse.isNotBlank()) "自定义响应: $mockPath" else "重定向: $mockPath",
            path = mockPath,
            mockBaseUrl = mockBaseUrl,
            responseBody = mockResponse,
            isEnabled = true
        )
        addOrUpdateRule(rule)
    }

    fun buildMockServer(chain: Interceptor.Chain, request: Request): Response {
        val path = request.url.encodedPath
        val rule = findMatchingRule(path, request.method)
        val targetBaseUrl = rule?.mockBaseUrl?.takeIf { it.isNotBlank() } ?: mockBaseUrl
        val newBaseUrl = targetBaseUrl.toHttpUrlOrNull() ?: return chain.proceed(request)
        val newHttpUrl = request.url
            .newBuilder()
            .scheme(newBaseUrl.scheme)
            .host(newBaseUrl.host)
            .port(newBaseUrl.port)
            .build()
        return chain.proceed(request.newBuilder().url(newHttpUrl).build())
    }

    fun mockResponseBody(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath
        val rule = findMatchingRule(path, request.method)

        if (rule != null && rule.delayMs > 0) {
            try {
                Thread.sleep(rule.delayMs)
            } catch (_: InterruptedException) {}
        }

        val respBodyString = rule?.responseBody ?: mockResponse
        val statusCode = rule?.statusCode ?: mockSuccessResponseCode

        val response = runCatching { chain.proceed(request) }.getOrNull()
        val contentType = response?.body?.contentType() ?: "application/json; charset=utf-8".toMediaTypeOrNull()
        val responseBody = respBodyString.toResponseBody(contentType)

        val builder = (response?.newBuilder() ?: Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1))
            .message("Mocked by MonitorX")
            .code(statusCode)
            .body(responseBody)
            .addHeader("X-MonitorX-Mocked", "true")

        return builder.build()
    }
}
