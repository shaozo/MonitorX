package com.shaovi.monitorx.runtime

import android.app.Application
import android.content.Context
import androidx.startup.Initializer
import com.shaovi.monitorx.performance.AppPageTracker
import com.shaovi.monitorx.performance.SoloXPerformanceCollector
import com.shaovi.monitorx.provider.MonitorInitializer
import com.shaovi.monitorx.runtime.MonitorEnvironment

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
