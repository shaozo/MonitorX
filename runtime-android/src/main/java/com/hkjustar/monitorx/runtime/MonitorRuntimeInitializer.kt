package com.hkjustar.monitorx.runtime

import android.app.Application
import android.content.Context
import androidx.startup.Initializer
import com.hkjustar.monitor.okhttp.performance.AppPageTracker
import com.hkjustar.monitor.okhttp.performance.SoloXPerformanceCollector
import com.hkjustar.monitor.okhttp.provider.MonitorInitializer
import com.hkjustar.monitor.okhttp.runtime.MonitorEnvironment

class MonitorRuntimeInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        val application = context.applicationContext as? Application ?: return
        AppPageTracker.register(application)
        SoloXPerformanceCollector.init(application)
        MonitorEnvironment.install(
            currentPageInfo = AppPageTracker::getCurrentPageInfo,
            topActivity = AppPageTracker::getTopActivity
        )
    }

    override fun dependencies(): List<Class<out Initializer<*>>> =
        listOf(MonitorInitializer::class.java)
}
