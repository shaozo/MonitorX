package com.shaovi.monitorx.utils


var lastUpdateDataId = 0L

/**
 * content-type类型 https://www.runoob.com/http/http-content-type.html
 */
var defaultContentTypes = "application/json,application/xml,text/html,text/plain,text/xml"

/**
 * 默认排除的基础设施与三方 SDK 域名（公有云存储、分析统计、广告等），支持业务层配置覆盖
 */
var defaultBlackHosts: List<String> = listOf(
    "aliyuncs.com",
    "qcloud.com",
    "myqcloud.com",
    "amazonaws.com",
    "google.com",
    "googleapis.com",
    "facebook.com",
    "crashlytics.com",
    "zego.im"
)
