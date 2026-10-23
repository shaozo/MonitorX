package com.shaovi.monitorx.provider

import android.content.Context
import androidx.startup.Initializer
import com.shaovi.monitorx.MonitorHelper

class MonitorInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        MonitorHelper.init(context)
    }
    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}