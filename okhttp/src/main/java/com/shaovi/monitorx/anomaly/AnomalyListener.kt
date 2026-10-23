package com.shaovi.monitorx.anomaly

fun interface AnomalyListener {
    fun onAnomaly(record: AnomalyRecord)
}
