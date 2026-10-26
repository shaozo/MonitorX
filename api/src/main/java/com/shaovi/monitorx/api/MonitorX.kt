package com.shaovi.monitorx.api

import android.content.Context

/**
 * Stable entry point that is safe to call from source shared by debug and release builds.
 *
 * When a real MonitorX runtime is not packaged, every operation delegates to an in-process no-op
 * backend. The no-op backend does not start threads, create databases, register interceptors, or
 * launch UI.
 */
object MonitorX {
    private const val BACKEND_CLASS = "com.shaovi.monitorx.internal.RealMonitorXBackend"

    private val backend: MonitorXBackend by lazy(LazyThreadSafetyMode.PUBLICATION) {
        runCatching {
            val backendClass = Class.forName(BACKEND_CLASS)
            backendClass.getField("INSTANCE").get(null) as MonitorXBackend
        }.getOrElse { NoOpMonitorXBackend }
    }

    /** True when a real MonitorX runtime is present in this build variant. */
    @JvmStatic
    fun isRuntimeAvailable(): Boolean = backend !== NoOpMonitorXBackend

    /** Initializes the real runtime when present; otherwise does nothing. */
    @JvmStatic
    fun initialize(context: Context) = backend.initialize(context.applicationContext)

    /** Enables or disables monitoring when the real runtime is present. */
    @JvmStatic
    fun setEnabled(enabled: Boolean) = backend.setEnabled(enabled)

    /** False for a no-op build, otherwise the current runtime state. */
    @JvmStatic
    fun isEnabled(): Boolean = backend.isEnabled()

    /** Opens the MonitorX UI when available. Returns false for a no-op build. */
    @JvmStatic
    fun open(context: Context): Boolean = backend.open(context)

    /** Sets a key-value pair in the active diagnostics session context. No-op when runtime is missing. */
    @JvmStatic
    fun setContext(key: String, value: String?) {
        diagnosticsBackend?.setContext(key, value)
    }

    /** Sets multiple context key-value pairs in batch. No-op when runtime is missing. */
    @JvmStatic
    fun setContext(values: Map<String, String?>) {
        diagnosticsBackend?.setContext(values)
    }

    /** Removes a specific key from the session context. No-op when runtime is missing. */
    @JvmStatic
    fun removeContext(key: String) {
        diagnosticsBackend?.removeContext(key)
    }

    /** Clears all session context variables. No-op when runtime is missing. */
    @JvmStatic
    fun clearContext() {
        diagnosticsBackend?.clearContext()
    }

    /** Explicitly enables or disables business rule monitoring in the optional runtime. */
    @JvmStatic
    fun setBusinessMonitoringEnabled(enabled: Boolean) {
        (backend as? MonitorXBusinessMonitoringBackend)?.setBusinessMonitoringEnabled(enabled)
    }

    /** Replaces automatically discovered rule packs with explicitly selected asset files. */
    @JvmStatic
    fun loadBusinessRulePacks(context: Context, assetPaths: List<String>): List<String> =
        (backend as? MonitorXRulePackBackend)?.loadBusinessRulePacks(context, assetPaths).orEmpty()

    /** Replaces automatically discovered rule packs with JSON files from an explicit asset directory. */
    @JvmStatic
    fun loadBusinessRuleDirectory(context: Context, assetDirectory: String): List<String> =
        (backend as? MonitorXRulePackBackend)?.loadBusinessRuleDirectory(context, assetDirectory).orEmpty()

    /** Emits a custom flow event into the diagnostic engine. No-op when runtime is missing. */
    @JvmStatic
    fun emit(name: String, attributes: Map<String, String?> = emptyMap()) {
        diagnosticsBackend?.emit(name, attributes)
    }

    /** Resets the active diagnostic session and clears transient context. No-op when runtime is missing. */
    @JvmStatic
    fun resetSession(sessionId: String? = null) {
        diagnosticsBackend?.resetSession(sessionId)
    }

    private val diagnosticsBackend: MonitorXDiagnosticsBackend?
        get() = backend as? MonitorXDiagnosticsBackend
}

/** Runtime contract implemented by the optional MonitorX Android artifact. */
interface MonitorXBackend {
    fun initialize(context: Context)
    fun setEnabled(enabled: Boolean)
    fun isEnabled(): Boolean
    fun open(context: Context): Boolean
}

/** Optional diagnostics capability implemented by newer runtimes without breaking old backends. */
interface MonitorXDiagnosticsBackend {
    fun setContext(key: String, value: String?)
    fun setContext(values: Map<String, String?>)
    fun removeContext(key: String)
    fun clearContext()
    fun emit(name: String, attributes: Map<String, String?> = emptyMap())
    fun resetSession(sessionId: String? = null)
}

/** Optional business-monitoring configuration supported by newer runtimes. */
interface MonitorXBusinessMonitoringBackend {
    fun setBusinessMonitoringEnabled(enabled: Boolean)
}

/** Optional manual rule selection supported by newer Android runtimes. */
interface MonitorXRulePackBackend {
    fun loadBusinessRulePacks(context: Context, assetPaths: List<String>): List<String>
    fun loadBusinessRuleDirectory(context: Context, assetDirectory: String): List<String>
}

private object NoOpMonitorXBackend : MonitorXBackend {
    override fun initialize(context: Context) = Unit
    override fun setEnabled(enabled: Boolean) = Unit
    override fun isEnabled(): Boolean = false
    override fun open(context: Context): Boolean = false
}
