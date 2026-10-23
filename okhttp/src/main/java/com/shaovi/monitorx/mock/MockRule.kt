package com.shaovi.monitorx.mock

import java.io.Serializable
import java.util.UUID

/**
 * Mock 规则定义：支持按 path、method 匹配并返回自定义响应或重定向到目标服务器
 */
data class MockRule(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var path: String = "",
    var method: String = "ALL", // GET, POST, PUT, DELETE, ALL
    var statusCode: Int = 200,
    var responseBody: String = "",
    var mockBaseUrl: String = "",
    var delayMs: Long = 0,
    var isEnabled: Boolean = true
) : Serializable
