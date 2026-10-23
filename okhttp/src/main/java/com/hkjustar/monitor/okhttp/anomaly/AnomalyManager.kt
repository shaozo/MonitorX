package com.hkjustar.monitor.okhttp.anomaly

import android.content.Context
import android.net.Uri
import com.hkjustar.monitor.okhttp.data.MonitorData
import com.hkjustar.monitor.okhttp.runtime.MonitorEnvironment
import org.json.JSONObject
import java.net.URLDecoder
import java.util.concurrent.CopyOnWriteArrayList
import com.hkjustar.monitorx.core.FlowWatcher
import com.hkjustar.monitorx.core.config.RulePackLoader
import com.hkjustar.monitorx.core.config.toRules
import java.util.concurrent.ConcurrentHashMap

/**
 * 业务与时序暗坑异常监控管理器
 * 完全与具体业务解耦，提供通用规则注册、参数萃取、异常断言与事件分发
 */
object AnomalyManager {

    private const val MAX_RECORDS = 500

    private val rules = CopyOnWriteArrayList<AnomalyRule>()
    private val records = CopyOnWriteArrayList<AnomalyRecord>()
    private val listeners = CopyOnWriteArrayList<AnomalyListener>()
    private val loadedRulePacks = ConcurrentHashMap<String, LoadedRulePack>()

    @Volatile
    var isEnabled: Boolean = true

    fun addRule(rule: AnomalyRule) {
        rules.removeAll { it.id == rule.id }
        rules.add(rule)
    }

    fun addRules(ruleList: List<AnomalyRule>) {
        ruleList.forEach { addRule(it) }
    }

    fun clearRules() {
        rules.clear()
    }

    fun getAllRecords(): List<AnomalyRecord> {
        return records.sortedByDescending { it.timestamp }
    }

    data class LoadedRulePack(
        val id: String,
        val name: String,
        val assetPath: String,
        val totalRules: Int,
        val enabledRules: Int
    )

    data class Status(
        val enabled: Boolean,
        val loadedPacks: Int,
        val activeRules: Int,
        val totalRecords: Int,
        val criticalRecords: Int,
        val warningRecords: Int,
        val infoRecords: Int
    )

    fun getLoadedRulePacks(): List<LoadedRulePack> = loadedRulePacks.values.sortedBy { it.assetPath }

    fun getStatus(): Status {
        val snapshot = records.toList()
        return Status(
            enabled = isEnabled && FlowWatcher.enabled,
            loadedPacks = loadedRulePacks.size,
            activeRules = loadedRulePacks.values.sumOf { it.enabledRules } + rules.size,
            totalRecords = snapshot.size,
            criticalRecords = snapshot.count { it.severity == AnomalySeverity.CRITICAL.name },
            warningRecords = snapshot.count { it.severity == AnomalySeverity.WARNING.name },
            infoRecords = snapshot.count { it.severity == AnomalySeverity.INFO.name }
        )
    }

    /**
     * 获取未被 Web 页面提示过的新异常
     */
    fun getNewAnomalies(): List<AnomalyRecord> {
        return records.filter { !it.notifiedWeb }
    }

    /**
     * 标记指定的异常记录已经被 Web 端通知过
     */
    fun markNotified(ids: List<String>) {
        val idSet = ids.toSet()
        records.forEach { record ->
            if (record.id in idSet) {
                record.notifiedWeb = true
            }
        }
    }

    fun clearRecords() {
        records.clear()
    }

    fun addListener(listener: AnomalyListener) {
        if (!listeners.contains(listener)) listeners += listener
    }

    fun removeListener(listener: AnomalyListener) {
        listeners -= listener
    }

    /** Enables violations produced by monitor-core to reuse the existing Monitor UI. */
    fun installCoreBridge() {
        CoreAnomalyBridge.install()
    }

    /** Loads a portable, declarative MonitorX rule pack from the host application's assets. */
    fun loadRulePack(context: Context, assetPath: String) {
        require(assetPath.isNotBlank()) { "assetPath must not be blank" }
        val content = context.assets.open(assetPath).bufferedReader().use { it.readText() }
        val pack = RulePackLoader.decode(content)
        val rules = pack.toRules()
        installCoreBridge()
        FlowWatcher.registerAll(rules)
        loadedRulePacks[pack.id] = LoadedRulePack(
            id = pack.id,
            name = pack.name,
            assetPath = assetPath,
            totalRules = pack.rules.size,
            enabledRules = pack.rules.count { it.enabled }
        )
    }

    /**
     * Discovers rule packs contributed by the app and Android library dependencies.
     * Every direct JSON child under assets/monitorx is loaded in stable filename order.
     */
    fun loadRulePacks(context: Context, assetDirectory: String = "monitorx"): List<String> {
        require(assetDirectory.isNotBlank()) { "assetDirectory must not be blank" }
        val files = context.assets.list(assetDirectory)
            .orEmpty()
            .filter { it.endsWith(".json", ignoreCase = true) }
            .sorted()
        files.forEach { file -> loadRulePack(context, "$assetDirectory/$file") }
        return files
    }

    internal fun record(record: AnomalyRecord) {
        if (records.size >= MAX_RECORDS) {
            records.removeAt(0)
        }
        records.add(record)
        listeners.forEach { listener -> runCatching { listener.onAnomaly(record) } }
    }

    /**
     * 对网络监控数据进行业务与时序暗坑检查
     */
    fun inspect(monitorData: MonitorData) {
        if (!isEnabled || rules.isEmpty()) return

        val rawUrl = monitorData.url.orEmpty()
        val path = monitorData.path.orEmpty()
        if (rawUrl.isBlank()) return

        // 提取各种来源的参数
        val queryParams by lazy { parseQueryParams(rawUrl) }
        val bodyParams by lazy { parseBodyParams(monitorData.requestBody.orEmpty()) }
        val headerParams by lazy { parseHeaderParams(monitorData.requestHeaders.orEmpty()) }
        val allParams by lazy {
            val map = HashMap<String, String>(queryParams.size + bodyParams.size + headerParams.size)
            map.putAll(headerParams)
            map.putAll(queryParams)
            map.putAll(bodyParams)
            map
        }

        val currentPageInfo = MonitorEnvironment.currentPageInfo()
        val activityName = currentPageInfo["activitySimple"]?.toString() ?: ""

        for (rule in rules) {
            // 匹配 URL 或 Path
            if (!rule.urlPattern.containsMatchIn(rawUrl) && !rule.urlPattern.containsMatchIn(path)) {
                continue
            }

            val isIllegal: Boolean
            val displayValue: String?
            val displayKey: String

            if (rule.validateParams != null) {
                isIllegal = runCatching { rule.validateParams.invoke(allParams) }.getOrDefault(false)
                displayKey = rule.paramKey.ifBlank { "联合参数" }
                val yd = allParams["yd_device_id"]
                val encryptId = allParams["yd_device_encrypt_id"]
                displayValue = if (yd != null || encryptId != null) {
                    "yd_device_id=${yd ?: "null"}, yd_device_encrypt_id=${encryptId ?: "null"}"
                } else {
                    allParams.entries.take(5).joinToString(", ") { "${it.key}=${it.value}" }
                }
            } else {
                val actualValue = when (rule.location) {
                    ParamLocation.QUERY -> queryParams[rule.paramKey]
                    ParamLocation.BODY -> bodyParams[rule.paramKey]
                    ParamLocation.HEADER -> headerParams[rule.paramKey]
                    ParamLocation.ANY -> {
                        bodyParams[rule.paramKey]
                            ?: queryParams[rule.paramKey]
                            ?: headerParams[rule.paramKey]
                    }
                }
                isIllegal = runCatching { rule.isIllegal(actualValue) }.getOrDefault(false)
                displayKey = rule.paramKey
                displayValue = actualValue
            }

            if (isIllegal) {
                val record = AnomalyRecord(
                    ruleId = rule.id,
                    ruleName = rule.name,
                    severity = rule.severity.name,
                    url = rawUrl,
                    path = path.ifBlank { rawUrl },
                    method = monitorData.method.orEmpty(),
                    paramKey = displayKey,
                    actualValue = displayValue,
                    alertMessage = rule.alertMessage,
                    solution = rule.solution,
                    monitorDataId = monitorData.id,
                    notifiedWeb = false,
                    activityName = activityName
                )

                record(record)
            }
        }
    }



    private fun parseQueryParams(url: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return result
        runCatching {
            uri.queryParameterNames?.forEach { name ->
                uri.getQueryParameter(name)?.let { value ->
                    result[name] = value
                }
            }
        }
        return result
    }

    private fun parseBodyParams(body: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return result

        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            runCatching {
                val json = JSONObject(trimmed)
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = json.opt(key)?.toString() ?: ""
                    result[key] = value
                }
            }
        } else if (trimmed.contains("=") || trimmed.contains("&")) {
            // Form 表单
            runCatching {
                trimmed.split("&").forEach { pair ->
                    val parts = pair.split("=", limit = 2)
                    if (parts.size == 2) {
                        val k = runCatching { URLDecoder.decode(parts[0], "UTF-8") }.getOrDefault(parts[0])
                        val v = runCatching { URLDecoder.decode(parts[1], "UTF-8") }.getOrDefault(parts[1])
                        result[k] = v
                    }
                }
            }
        }
        return result
    }

    private fun parseHeaderParams(headers: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val trimmed = headers.trim()
        if (trimmed.isEmpty()) return result
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            runCatching {
                val json = JSONObject(trimmed)
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    result[key] = json.optString(key)
                }
            }
        }
        return result
    }
}
