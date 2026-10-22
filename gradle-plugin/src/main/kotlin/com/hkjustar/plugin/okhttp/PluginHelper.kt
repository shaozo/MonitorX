package com.hkjustar.plugin.okhttp

object PluginHelper {
    var extension: ConfigExtension? = null

    fun targetBuilderMethodName(): String {
        return when (extension?.injectionPoint?.trim()?.lowercase()) {
            "init", "<init>", "constructor" -> "<init>"
            else -> "build"
        }
    }

    fun insertMode(): String {
        val normalizedMode = extension?.insertMode?.trim()?.lowercase().orEmpty()
        return when (targetBuilderMethodName()) {
            "<init>" -> "first"
            else -> when (normalizedMode) {
                "" -> "before"
                "before", "after", "append", "last", "prepend", "first" -> normalizedMode
                else -> "before"
            }
        }
    }

    fun anchorInterceptorClassName(): String {
        return when (insertMode()) {
            "before", "after" -> extension?.anchorInterceptorClassName
                ?.takeIf { it.isNotBlank() }
                ?: ""
            else -> ""
        }
    }
}
