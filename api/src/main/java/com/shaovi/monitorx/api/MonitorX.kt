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
}

/** Runtime contract implemented by the optional MonitorX Android artifact. */
interface MonitorXBackend {
    fun initialize(context: Context)
    fun setEnabled(enabled: Boolean)
    fun isEnabled(): Boolean
    fun open(context: Context): Boolean
}

private object NoOpMonitorXBackend : MonitorXBackend {
    override fun initialize(context: Context) = Unit
    override fun setEnabled(enabled: Boolean) = Unit
    override fun isEnabled(): Boolean = false
    override fun open(context: Context): Boolean = false
}
