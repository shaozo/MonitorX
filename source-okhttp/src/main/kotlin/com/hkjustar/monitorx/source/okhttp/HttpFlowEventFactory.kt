package com.hkjustar.monitorx.source.okhttp

import com.hkjustar.monitorx.core.EventType
import com.hkjustar.monitorx.core.FlowEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URLDecoder

data class HttpEventSnapshot(
    val url: String,
    val path: String = "",
    val method: String = "",
    val requestHeaders: String = "",
    val requestBody: String = "",
    val responseBody: String = "",
    val pageName: String = "",
    val sourceId: String? = null
)

object HttpFlowEventFactory {
    private val json = Json { ignoreUnknownKeys = true }

    fun response(snapshot: HttpEventSnapshot): FlowEvent {
        return create(snapshot, EventType.HTTP_RESPONSE, includeResponse = true)
    }

    fun request(snapshot: HttpEventSnapshot): FlowEvent {
        return create(snapshot, EventType.HTTP_REQUEST, includeResponse = false)
    }

    private fun create(snapshot: HttpEventSnapshot, eventType: EventType, includeResponse: Boolean): FlowEvent {
        val attributes = linkedMapOf<String, String?>()
        attributes["http.method"] = snapshot.method
        attributes["http.url"] = snapshot.url
        attributes["http.path"] = snapshot.path.ifBlank {
            snapshot.url.toHttpUrlOrNull()?.encodedPath.orEmpty()
        }
        attributes["http.request.headers"] = snapshot.requestHeaders
        attributes["http.request.body"] = snapshot.requestBody
        if (includeResponse) attributes["http.response.body"] = snapshot.responseBody
        attributes["page.activity"] = snapshot.pageName
        parseParameters(snapshot).forEach { (key, value) -> attributes["http.param.$key"] = value }
        if (includeResponse) {
            parseResponse(snapshot.responseBody).forEach { (key, value) ->
                attributes["http.response.$key"] = value
            }
        }
        return FlowEvent(
            type = eventType,
            name = attributes["http.path"].orEmpty().ifBlank { snapshot.url },
            attributes = attributes,
            sourceId = snapshot.sourceId
        )
    }

    private fun parseParameters(snapshot: HttpEventSnapshot): Map<String, String> {
        val result = linkedMapOf<String, String>()
        snapshot.url.toHttpUrlOrNull()?.let { url ->
            url.queryParameterNames.forEach { key -> url.queryParameter(key)?.let { result[key] = it } }
        }
        parseStructured(snapshot.requestHeaders, result)
        parseStructured(snapshot.requestBody, result)
        return result
    }

    private fun parseStructured(raw: String, result: MutableMap<String, String>) {
        val value = raw.trim()
        if (value.startsWith('{') && value.endsWith('}')) {
            runCatching { json.decodeFromString<JsonObject>(value) }.getOrNull()?.forEach { (key, element) ->
                result[key] = (element as? JsonPrimitive)?.contentOrNull ?: element.toString()
            }
        } else if (value.contains('=')) {
            value.split('&').forEach { pair ->
                val parts = pair.split('=', limit = 2)
                if (parts.size == 2) {
                    result[decode(parts[0])] = decode(parts[1])
                }
            }
        }
    }

    private fun decode(value: String): String =
        runCatching { URLDecoder.decode(value, Charsets.UTF_8.name()) }.getOrDefault(value)

    private fun parseResponse(raw: String): Map<String, String?> {
        val root = runCatching { json.parseToJsonElement(raw.trim()) }.getOrNull() ?: return emptyMap()
        return linkedMapOf<String, String?>().also { flatten(root, "", it) }
    }

    private fun flatten(element: JsonElement, path: String, output: MutableMap<String, String?>) {
        when (element) {
            is JsonObject -> element.forEach { (key, value) ->
                flatten(value, if (path.isBlank()) key else "$path.$key", output)
            }
            is JsonArray -> element.forEachIndexed { index, value ->
                flatten(value, if (path.isBlank()) index.toString() else "$path.$index", output)
            }
            is JsonPrimitive -> if (path.isNotBlank()) output[path] = element.contentOrNull
        }
    }
}
