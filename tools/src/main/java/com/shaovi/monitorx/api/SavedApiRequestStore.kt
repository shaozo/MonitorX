package com.shaovi.monitorx.api

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class SavedApiRequestStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "monitor_api_debug_requests",
        Context.MODE_PRIVATE
    )
    private val gson = Gson()

    fun getAll(): MutableList<SavedApiRequest> {
        val json = preferences.getString(KEY_REQUESTS, null) ?: return mutableListOf()
        return runCatching {
            val type = object : TypeToken<MutableList<SavedApiRequest>>() {}.type
            gson.fromJson<MutableList<SavedApiRequest>>(json, type) ?: mutableListOf()
        }.getOrDefault(mutableListOf()).sortedByDescending { it.updatedAt }.toMutableList()
    }

    fun save(request: SavedApiRequest) {
        val requests = getAll()
        val index = requests.indexOfFirst { it.id == request.id }
        request.updatedAt = System.currentTimeMillis()
        if (index >= 0) requests[index] = request else requests.add(request)
        persist(requests)
    }

    fun delete(id: String) {
        persist(getAll().filterNot { it.id == id })
    }

    private fun persist(requests: List<SavedApiRequest>) {
        preferences.edit().putString(KEY_REQUESTS, gson.toJson(requests)).apply()
    }

    private companion object {
        const val KEY_REQUESTS = "requests"
    }
}
