package com.shaovi.monitorx.api

import java.util.UUID

data class SavedApiRequest(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var method: String = "POST",
    var url: String = "",
    var query: String = "",
    var headers: String = "",
    var body: String = "",
    var contentType: String = "application/json; charset=utf-8",
    var timeoutMillis: Long = 60_000L,
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis()
)
