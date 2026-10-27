package com.shaovi.monitorx.utils

import com.shaovi.monitorx.MonitorHelper
import java.util.regex.Matcher
import java.util.regex.Pattern
import androidx.core.net.toUri
import kotlin.text.contains


fun String?.isWhiteContentType(): Boolean {
    val whiteContentTypes = MonitorHelper.whiteContentTypes
    val intercept =
        whiteContentTypes.isNullOrBlank() || !this.isNullOrEmpty() && whiteContentTypes.contains(
            this.split(";")[0]
        )
    println("${MonitorHelper.TAG}---->whiteContentTypes = $whiteContentTypes 当前ContentType = $this   是否在白名单:$intercept")
    return intercept
}

fun String?.isWhiteHosts(): Boolean {
    val whiteHosts = MonitorHelper.whiteHosts
    if (this.isNullOrBlank() || whiteHosts.isNullOrBlank()) return true
    val whiteList = whiteHosts.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    val intercept = whiteList.any { matchesHostRule(this, it) }
    println("${MonitorHelper.TAG}---->whiteHosts = $whiteHosts  当前host= $this   是否在白名单:$intercept")
    return intercept
}

fun String?.isBlackHosts(): Boolean {
    if (this.isNullOrBlank()) return false
    // 显式白名单优先级高于默认黑名单：如果用户明确配置了 whiteHosts 且包含当前 host，则不被 defaultBlackHosts 拦截
    val whiteHosts = MonitorHelper.whiteHosts
    val whiteList = whiteHosts.orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() }
    val isExplicitWhite = whiteList.isNotEmpty() && whiteList.any { matchesHostRule(this, it) }

    val blackHosts = MonitorHelper.blackHosts
    val blackList = blackHosts.orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() }
    val isConfigured = blackList.any { matchesHostRule(this, it) }
    val isDefault = MonitorHelper.useDefaultBlackHosts &&
        !isExplicitWhite &&
        defaultBlackHosts.any { matchesHostRule(this, it) }
    val intercept = isConfigured || isDefault
    println("${MonitorHelper.TAG}---->blackHosts = $blackHosts  当前host= $this   是否在黑名单:$intercept")
    return intercept
}

private fun matchesHostRule(host: String, configuredRule: String): Boolean {
    val rule = configuredRule.trim().lowercase().removePrefix("*.").trimStart('.').trimEnd('.')
    if (rule.isEmpty()) return false
    val normalizedHost = host.lowercase().trimEnd('.')
    return normalizedHost == rule || normalizedHost.endsWith(".$rule")
}

fun String?.isSkipInterceptByHost(): Boolean {
    return !this.isWhiteHosts() && this.isBlackHosts()
}

val IP_ADDRESS_PATTERN: Pattern =
    Pattern.compile("(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})")

fun String?.isIpAddress(): Boolean {
    if (this.isNullOrBlank()) return false
    val matcher: Matcher = IP_ADDRESS_PATTERN.matcher(this)
    if (!matcher.matches()) return false
    for (i in 1..matcher.groupCount()) {
        try {
            val group = matcher.group(i) ?: return false
            if (group.toInt() > 255) {
                return false
            }
        } catch (e: Exception) {
            return false
        }
    }
    return true
}
