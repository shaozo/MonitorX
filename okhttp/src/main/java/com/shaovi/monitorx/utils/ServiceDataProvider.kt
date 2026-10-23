package com.shaovi.monitorx.utils

import com.shaovi.monitorx.MonitorHelper
import com.shaovi.monitorx.data.MonitorData

object ServiceDataProvider {

    fun getMonitorDataList(keyword: String = "", content: String = "", limit: Int = 50, offset: Int = 0): MutableList<MonitorData> {
        return MonitorHelper.getMonitorDataList(keyword, content, limit, offset)
    }

    fun getMonitorDataByLastId(lastUpdateDataId: Long): MutableList<MonitorData> {
        return MonitorHelper.getMonitorDataByLastId(lastUpdateDataId)
    }

    fun getMonitorDataByLastId(keyword: String = "", content: String = "", lastUpdateDataId: Long): MutableList<MonitorData> {
        return MonitorHelper.getMonitorDataByLastId(keyword, content, lastUpdateDataId)
    }
}
