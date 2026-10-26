package com.shaovi.monitorx.data

class PropertiesData(
    var port: String,
    var dbName: String,
    var whiteContentTypes: String?,//ContentType白名单
    var whiteHosts: String?,//host白名单
    var blackHosts: String?,//host黑名单
    var isFilterIPAddressHost: Boolean = false,//是否过滤纯IP地址的host
    var businessMonitoringEnabled: Boolean? = null,
    var businessRuleAssets: List<String> = emptyList(),
    var businessRuleDirectory: String? = null
)
