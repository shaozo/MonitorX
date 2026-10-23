package com.shaovi.monitorx.core

object Matchers {
    fun event(name: String, type: EventType? = null): EventMatcher = {
        it.name == name && (type == null || it.type == type)
    }

    fun http(path: Regex, type: EventType = EventType.HTTP_REQUEST): EventMatcher = {
        it.type == type && path.containsMatchIn(it.attributes["http.path"].orEmpty())
    }
}
