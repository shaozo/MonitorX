package com.hkjustar.monitor.okhttp.anomaly

fun interface AnomalyListener {
    fun onAnomaly(record: AnomalyRecord)
}
