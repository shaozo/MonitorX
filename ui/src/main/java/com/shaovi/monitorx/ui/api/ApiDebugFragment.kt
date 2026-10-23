package com.shaovi.monitorx.ui.api

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.shaovi.monitorx.MonitorHelper
import com.shaovi.monitorx.api.ApiDebugBridge
import com.shaovi.monitorx.api.SavedApiRequest
import com.shaovi.monitorx.api.SavedApiRequestStore
import com.shaovi.monitorx.databinding.FragmentApiDebugBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException

class ApiDebugFragment : Fragment() {
    private var _binding: FragmentApiDebugBinding? = null
    private val binding get() = requireNotNull(_binding)
    private lateinit var store: SavedApiRequestStore
    private var currentCall: Call? = null
    private var editingId: String? = null
    private var editingCreatedAt: Long = 0L

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentApiDebugBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        super.onViewCreated(view, state)
        store = SavedApiRequestStore(requireContext())
        binding.spMethod.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_dropdown_item, METHODS
        )
        binding.spMethod.setSelection(METHODS.indexOf("POST"))
        binding.btnSend.setOnClickListener { sendRequest() }
        binding.btnCancel.setOnClickListener { currentCall?.cancel() }
        binding.btnSave.setOnClickListener { askAndSave() }
        binding.btnSaved.setOnClickListener { showSavedRequests() }
        binding.btnCapture.setOnClickListener { showCapturedRequests() }
    }

    private fun sendRequest() {
        if (currentCall != null) return
        val executor = ApiDebugBridge.executor
        if (executor == null) {
            showError("业务请求执行器尚未初始化")
            return
        }
        val request = runCatching { buildRequest(executor.baseUrl) }.getOrElse {
            showError(it.message ?: "请求参数无效")
            return
        }
        val timeout = binding.etTimeout.text.toString().toLongOrNull()?.coerceIn(1_000L, 300_000L)
            ?: 60_000L
        setSending(true)
        val startedAt = System.nanoTime()
        currentCall = executor.execute(request, timeout, object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                val message = if (call.isCanceled()) "请求已取消" else "请求失败：${e.message}"
                postResult(message, e.stackTraceToString())
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val elapsed = (System.nanoTime() - startedAt) / 1_000_000
                    val body = runCatching { it.body?.string().orEmpty() }
                        .getOrElse { error -> "读取响应失败：${error.message}" }
                    val status = "HTTP ${it.code} ${it.message} · ${elapsed}ms"
                    postResult(status, "${it.headers}\n\n${prettyBody(body)}")
                }
            }
        })
    }

    private fun buildRequest(baseUrl: String): Request {
        val rawUrl = binding.etUrl.text.toString().trim()
        require(rawUrl.isNotEmpty()) { "请输入 URL" }
        val base = baseUrl.toHttpUrlOrNull() ?: error("当前 Base URL 无效：$baseUrl")
        val resolved = rawUrl.toHttpUrlOrNull() ?: base.resolve(rawUrl)
            ?: error("URL 无效：$rawUrl")
        val urlBuilder = resolved.newBuilder()
        parseLines(binding.etQuery.text.toString(), '=') { name, value ->
            urlBuilder.addQueryParameter(name, value)
        }
        val headersBuilder = Headers.Builder()
        parseHeaders(binding.etHeaders.text.toString()).forEach { (name, value) ->
            headersBuilder.add(name, value)
        }
        val method = binding.spMethod.selectedItem.toString()
        val bodyText = binding.etBody.text.toString()
        val body = if (method == "GET") null else bodyText.toRequestBody(
            binding.etContentType.text.toString().ifBlank { "text/plain; charset=utf-8" }.toMediaType()
        )
        return Request.Builder().url(urlBuilder.build()).headers(headersBuilder.build())
            .method(method, body).build()
    }

    private fun postResult(status: String, content: String) {
        val owner = viewLifecycleOwnerLiveData.value ?: return
        owner.lifecycleScope.launch {
            val currentBinding = _binding ?: return@launch
            currentCall = null
            currentBinding.tvStatus.text = status
            currentBinding.tvResponse.text = content
            setSending(false)
        }
    }

    private fun setSending(sending: Boolean) {
        val currentBinding = _binding ?: return
        currentBinding.btnSend.isEnabled = !sending
        currentBinding.btnCancel.isEnabled = sending
    }

    private fun showError(message: String) {
        binding.tvStatus.text = message
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun askAndSave() {
        val input = EditText(requireContext()).apply {
            hint = "接口名称"
            setText(editingId?.let { id -> store.getAll().firstOrNull { it.id == id }?.name }.orEmpty())
        }
        AlertDialog.Builder(requireContext()).setTitle(if (editingId == null) "保存接口" else "覆盖保存")
            .setView(input).setNegativeButton("取消", null).setPositiveButton("保存") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) return@setPositiveButton
                val request = snapshot(name)
                store.save(request)
                editingId = request.id
                editingCreatedAt = request.createdAt
                Toast.makeText(requireContext(), "已保存", Toast.LENGTH_SHORT).show()
            }.show()
    }

    private fun snapshot(name: String) = SavedApiRequest(
        id = editingId ?: java.util.UUID.randomUUID().toString(),
        name = name,
        method = binding.spMethod.selectedItem.toString(),
        url = binding.etUrl.text.toString(), query = binding.etQuery.text.toString(),
        headers = binding.etHeaders.text.toString(), body = binding.etBody.text.toString(),
        contentType = binding.etContentType.text.toString(),
        timeoutMillis = binding.etTimeout.text.toString().toLongOrNull() ?: 60_000L,
        createdAt = editingCreatedAt.takeIf { it > 0 } ?: System.currentTimeMillis()
    )

    private fun showSavedRequests() {
        val items = store.getAll()
        if (items.isEmpty()) return showError("暂无已保存接口")
        val labels = items.map { "${it.method}  ${it.name}\n${it.url}" }.toTypedArray()
        AlertDialog.Builder(requireContext()).setTitle("已保存接口").setItems(labels) { _, index ->
            showSavedActions(items[index])
        }.show()
    }

    private fun showSavedActions(item: SavedApiRequest) {
        AlertDialog.Builder(requireContext()).setTitle(item.name)
            .setItems(arrayOf("载入", "载入并发送", "删除")) { _, action ->
                when (action) {
                    0 -> load(item)
                    1 -> { load(item); sendRequest() }
                    2 -> { store.delete(item.id); if (editingId == item.id) editingId = null }
                }
            }.show()
    }

    private fun showCapturedRequests() {
        viewLifecycleOwner.lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) { MonitorHelper.getMonitorDataList(limit = 50) }
            if (_binding == null) return@launch
            if (items.isEmpty()) return@launch showError("暂无抓包记录")
            val labels = items.map { "${it.method}  ${it.path}" }.toTypedArray()
            AlertDialog.Builder(requireContext()).setTitle("从最近抓包载入").setItems(labels) { _, index ->
                val item = items[index]
                load(SavedApiRequest(method = item.method ?: "GET", url = item.url.orEmpty(),
                    headers = normalizeCapturedHeaders(item.requestHeaders), body = item.requestBody.orEmpty(),
                    contentType = item.requestContentType ?: "application/json; charset=utf-8"))
            }.show()
        }
    }

    private fun load(item: SavedApiRequest) {
        binding.spMethod.setSelection(METHODS.indexOf(item.method).coerceAtLeast(0))
        binding.etUrl.setText(item.url); binding.etQuery.setText(item.query)
        binding.etHeaders.setText(item.headers); binding.etBody.setText(item.body)
        binding.etContentType.setText(item.contentType); binding.etTimeout.setText(item.timeoutMillis.toString())
        editingId = item.id.takeUnless { item.name.isBlank() }
        editingCreatedAt = item.createdAt
    }

    private fun normalizeCapturedHeaders(value: String?): String {
        if (value.isNullOrBlank()) return ""
        return runCatching {
            JsonParser.parseString(value).asJsonObject.entrySet().joinToString("\n") { "${it.key}: ${it.value.asString}" }
        }.getOrDefault(value)
    }

    private fun prettyBody(body: String): String = runCatching {
        Gson().newBuilder().setPrettyPrinting().create().toJson(JsonParser.parseString(body))
    }.getOrDefault(body)

    private fun parseHeaders(text: String): List<Pair<String, String>> {
        if (text.trim().startsWith("{")) {
            return runCatching { JsonParser.parseString(text).asJsonObject.entrySet().map { it.key to it.value.asString } }
                .getOrElse { error("Headers JSON 格式无效") }
        }
        val result = mutableListOf<Pair<String, String>>()
        parseLines(text, ':') { name, value -> result += name to value }
        return result
    }

    private fun parseLines(text: String, separator: Char, action: (String, String) -> Unit) {
        text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
            val index = line.indexOf(separator)
            if (index <= 0) return@forEach  // skip lines without valid separator instead of failing
            action(line.substring(0, index).trim(), line.substring(index + 1).trim())
        }
    }

    override fun onDestroyView() {
        currentCall?.cancel()
        currentCall = null
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        val METHODS = listOf("GET", "POST", "PUT", "PATCH", "DELETE")
    }
}
