package com.shaovi.monitorx.runtime

import android.app.Activity

/** Optional Android runtime hooks. The capture artifact has safe no-op defaults. */
object MonitorEnvironment {
    @Volatile
    private var pageInfoProvider: () -> Map<String, Any> = { emptyMap() }

    @Volatile
    private var topActivityProvider: () -> Activity? = { null }

    fun install(
        currentPageInfo: () -> Map<String, Any>,
        topActivity: () -> Activity?
    ) {
        pageInfoProvider = currentPageInfo
        topActivityProvider = topActivity
    }

    fun currentPageInfo(): Map<String, Any> = pageInfoProvider()

    fun topActivity(): Activity? = topActivityProvider()
}
