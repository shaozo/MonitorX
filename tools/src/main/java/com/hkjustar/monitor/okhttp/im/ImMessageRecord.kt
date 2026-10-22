package com.hkjustar.monitor.okhttp.im

import java.io.Serializable

data class ImMessageRecord(
    var id: Long = 0,
    var timestamp: Long = 0,
    var type: Int = 0,
    var content: String = ""
) : Serializable
