package com.shaovi.monitorx.gradle.okhttp

open class ConfigExtension {
    public var logTag: String = "cs"
    public var includePackages: Array<String> = arrayOf()
    public var includeMethods: Array<String> = arrayOf()
    public var excludePackages: Array<String> = arrayOf("com.alibaba.*", "com.aliyun.*")
    public var excludeMethods: Array<String> = arrayOf()
    public var limitTime: Long = 500
    public var injectionPoint: String = "build"
    public var insertMode: String = ""
    public var anchorInterceptorClassName: String = ""
    /** Avoid using an unrecognized third-party SDK client as the API debugger client. */
    public var captureOnlyBusinessClient: Boolean = true
    /** Regex patterns matched against the complete variant name, case-insensitively. */
    public var enabledVariants: Array<String> = arrayOf(".*demo.*")
    /** Exclusions win over enabledVariants. */
    public var disabledVariants: Array<String> = emptyArray()
}
