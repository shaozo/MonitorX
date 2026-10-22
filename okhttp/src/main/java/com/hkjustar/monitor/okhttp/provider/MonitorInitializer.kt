package com.hkjustar.monitor.okhttp.provider

import android.content.Context
import androidx.startup.Initializer
import com.hkjustar.monitor.okhttp.MonitorHelper

class MonitorInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        MonitorHelper.init(context)
    }
    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}