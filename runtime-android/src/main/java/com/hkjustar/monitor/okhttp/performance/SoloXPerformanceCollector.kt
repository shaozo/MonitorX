package com.hkjustar.monitor.okhttp.performance

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.view.Choreographer
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

data class MetricSnapshot(
    val timestamp: Long,
    val fps: Double,
    val janks: Int,
    val jitterMs: Double,
    val cpuUsageApp: Double,
    val memoryPssMb: Double,
    val memoryJavaHeapMb: Double,
    val rxSpeedKb: Double,
    val txSpeedKb: Double,
    val batteryPct: Int,
    val batteryTemp: Double,
    val batteryVolt: Int,
    val batteryStatus: String,
    val isCharging: Boolean
)

@SuppressLint("StaticFieldLeak")
object SoloXPerformanceCollector {

    @Volatile
    var isMonitoring = false
        private set

    private var context: Context? = null
    private var scheduler: ScheduledExecutorService? = null

    // FPS / Choreographer state
    private var frameCount = 0
    private var lastFrameTimeNanos = 0L
    private var fpsStartTimeNanos = 0L
    private var jankCount = 0
    private val frameIntervalsMs = mutableListOf<Double>()
    @Volatile private var currentFps = 0.0
    @Volatile private var currentJanks = 0
    @Volatile private var currentJitterMs = 0.0

    // Traffic state
    private var lastRxBytes = 0L
    private var lastTxBytes = 0L
    private var lastTrafficTimeMs = 0L

    // CPU calc state
    private var lastAppCpuTime = 0L
    private var lastSystemTimeNanos = 0L

    // History buffer (retains last 60 samples)
    private val historyBuffer = ConcurrentLinkedQueue<MetricSnapshot>()

    // Session Recording State
    @Volatile
    var isRecordingSession = false
        private set

    private var sessionName = ""
    private var sessionStartTime = 0L
    private var sessionStartRxBytes = TrafficStats.UNSUPPORTED.toLong()
    private val sessionSnapshots = mutableListOf<MetricSnapshot>()

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isMonitoring) return

            if (fpsStartTimeNanos == 0L) {
                fpsStartTimeNanos = frameTimeNanos
                lastFrameTimeNanos = frameTimeNanos
            } else {
                frameCount++
                val intervalMs = (frameTimeNanos - lastFrameTimeNanos) / 1_000_000.0
                frameIntervalsMs.add(intervalMs)
                if (intervalMs > 16.67 + 8.0) { // Jank threshold
                    jankCount++
                }
                lastFrameTimeNanos = frameTimeNanos

                val elapsedNs = frameTimeNanos - fpsStartTimeNanos
                if (elapsedNs >= 1_000_000_000L) { // 1 second elapsed
                    currentFps = (frameCount * 1_000_000_000.0) / elapsedNs
                    currentFps = currentFps.coerceAtMost(120.0)
                    currentJanks = jankCount

                    // Calculate Jitter (std dev of frame intervals)
                    if (frameIntervalsMs.size > 1) {
                        val mean = frameIntervalsMs.average()
                        val variance = frameIntervalsMs.sumOf { (it - mean) * (it - mean) } / frameIntervalsMs.size
                        currentJitterMs = Math.sqrt(variance)
                    } else {
                        currentJitterMs = 0.0
                    }

                    // Reset for next sec
                    frameCount = 0
                    jankCount = 0
                    fpsStartTimeNanos = frameTimeNanos
                    frameIntervalsMs.clear()
                }
            }

            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun init(ctx: Context) {
        this.context = ctx.applicationContext
    }

    @Synchronized
    fun startMonitoring() {
        if (isMonitoring) return
        isMonitoring = true
        historyBuffer.clear()

        // Init traffic & CPU base
        val uid = Process.myUid()
        lastRxBytes = TrafficStats.getUidRxBytes(uid)
        lastTxBytes = TrafficStats.getUidTxBytes(uid)
        lastTrafficTimeMs = System.currentTimeMillis()
        lastAppCpuTime = Process.getElapsedCpuTime()
        lastSystemTimeNanos = System.nanoTime()

        // Post Choreographer on main thread
        Handler(Looper.getMainLooper()).post {
            frameCount = 0
            jankCount = 0
            lastFrameTimeNanos = 0L
            fpsStartTimeNanos = 0L
            frameIntervalsMs.clear()
            currentFps = 0.0
            currentJanks = 0
            currentJitterMs = 0.0
            Choreographer.getInstance().postFrameCallback(frameCallback)
        }

        // Immediately sample once to avoid initial empty history
        runCatching { sampleAndRecord() }

        // Start Background Sampler with initial delay 0
        scheduler = Executors.newSingleThreadScheduledExecutor()
        scheduler?.scheduleWithFixedDelay({
            try {
                sampleAndRecord()
            } catch (e: Exception) {
                // Ignore sampling errors
            }
        }, 0, 1, TimeUnit.SECONDS)
    }

    @Synchronized
    fun stopMonitoring() {
        if (!isMonitoring) return
        isMonitoring = false

        Handler(Looper.getMainLooper()).post {
            Choreographer.getInstance().removeFrameCallback(frameCallback)
        }

        scheduler?.shutdownNow()
        scheduler = null
        historyBuffer.clear()
        if (isRecordingSession) stopSession()
    }

    // ── Session Recording ──
    @Synchronized
    fun startSession(name: String): Boolean {
        if (!isMonitoring) startMonitoring()
        isRecordingSession = true
        sessionName = name.ifBlank { "性能测试用例_${System.currentTimeMillis()}" }
        sessionStartTime = System.currentTimeMillis()
        sessionStartRxBytes = TrafficStats.getUidRxBytes(Process.myUid())
        synchronized(sessionSnapshots) { sessionSnapshots.clear() }
        return true
    }

    @Synchronized
    fun stopSession(): Map<String, Any>? {
        if (!isRecordingSession) return null
        isRecordingSession = false
        val endTime = System.currentTimeMillis()

        val copyList: List<MetricSnapshot>
        synchronized(sessionSnapshots) { copyList = ArrayList(sessionSnapshots) }

        if (copyList.isEmpty()) return null

        val durationSec = ((endTime - sessionStartTime) / 1000.0).coerceAtLeast(1.0)
        val avgFps = copyList.map { it.fps }.average()
        val totalJanks = copyList.sumOf { it.janks }
        val avgJitter = copyList.map { it.jitterMs }.average()
        val avgCpu = copyList.map { it.cpuUsageApp }.average()
        val maxMemory = copyList.maxOfOrNull { it.memoryPssMb } ?: 0.0
        val avgMemory = copyList.map { it.memoryPssMb }.average()
        val sessionEndRxBytes = TrafficStats.getUidRxBytes(Process.myUid())
        val totalRxMb = if (sessionStartRxBytes != TrafficStats.UNSUPPORTED.toLong() &&
            sessionEndRxBytes >= sessionStartRxBytes
        ) {
            (sessionEndRxBytes - sessionStartRxBytes) / (1024.0 * 1024.0)
        } else 0.0

        return mapOf(
            "id" to "session_${sessionStartTime}",
            "name" to sessionName,
            "startTime" to sessionStartTime,
            "endTime" to endTime,
            "durationSec" to Math.round(durationSec * 10) / 10.0,
            "sampleCount" to copyList.size,
            "summary" to mapOf(
                "avgFps" to Math.round(avgFps * 10) / 10.0,
                "totalJanks" to totalJanks,
                "avgJitterMs" to Math.round(avgJitter * 10) / 10.0,
                "avgCpuPct" to Math.round(avgCpu * 10) / 10.0,
                "maxMemoryPssMb" to Math.round(maxMemory * 10) / 10.0,
                "avgMemoryPssMb" to Math.round(avgMemory * 10) / 10.0,
                "rxBytesTotal" to totalRxMb
            ),
            "samples" to copyList.map { snapshotToMap(it) }
        )
    }

    fun getLatestSnapshotMap(): Map<String, Any> {
        return historyBuffer.lastOrNull()?.let(::snapshotToMap).orEmpty()
    }

    fun getHistorySnapshotsList(): List<Map<String, Any>> {
        return historyBuffer.map { snapshotToMap(it) }
    }

    private fun sampleAndRecord() {
        val snapshot = sampleCurrentMetrics()
        historyBuffer.add(snapshot)
        while (historyBuffer.size > 60) {
            historyBuffer.poll()
        }
        if (isRecordingSession) {
            synchronized(sessionSnapshots) {
                sessionSnapshots.add(snapshot)
            }
        }
    }

    private fun sampleCurrentMetrics(): MetricSnapshot {
        val now = System.currentTimeMillis()

        // CPU calculation
        val appCpuTime = Process.getElapsedCpuTime()
        val sysTimeNs = System.nanoTime()
        val timeDiffNs = sysTimeNs - lastSystemTimeNanos
        val cpuDiffMs = appCpuTime - lastAppCpuTime
        val numCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

        var cpuUsage = 0.0
        if (timeDiffNs > 0 && cpuDiffMs >= 0) {
            val cpuTimeNs = TimeUnit.MILLISECONDS.toNanos(cpuDiffMs)
            cpuUsage = (cpuTimeNs.toDouble() / timeDiffNs.toDouble()) * 100.0 / numCores
            cpuUsage = cpuUsage.coerceIn(0.0, 100.0)
        }
        lastAppCpuTime = appCpuTime
        lastSystemTimeNanos = sysTimeNs

        // Memory calculation
        val runtime = Runtime.getRuntime()
        val javaHeapMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024.0 * 1024.0)

        var pssMb = javaHeapMb
        runCatching {
            val memInfo = Debug.MemoryInfo()
            Debug.getMemoryInfo(memInfo)
            pssMb = memInfo.totalPss / 1024.0
        }

        // Traffic calculation
        val uid = Process.myUid()
        val currRx = TrafficStats.getUidRxBytes(uid)
        val currTx = TrafficStats.getUidTxBytes(uid)
        val timeDiffSec = (now - lastTrafficTimeMs) / 1000.0

        var rxSpeed = 0.0
        var txSpeed = 0.0
        if (timeDiffSec > 0 &&
            lastRxBytes > 0 && currRx >= lastRxBytes &&
            lastTxBytes > 0 && currTx >= lastTxBytes
        ) {
            rxSpeed = (currRx - lastRxBytes) / 1024.0 / timeDiffSec
            txSpeed = (currTx - lastTxBytes) / 1024.0 / timeDiffSec
        }
        lastRxBytes = currRx
        lastTxBytes = currTx
        lastTrafficTimeMs = now

        // Battery calculation
        var batteryPct = 100
        var batteryTemp = 25.0
        var batteryVolt = 3800
        var batteryStatusStr = "未知"
        var isCharging = false

        context?.let { ctx ->
            runCatching {
                val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                val batteryStatus = ctx.registerReceiver(null, intentFilter)
                if (batteryStatus != null) {
                    val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    if (level >= 0 && scale > 0) {
                        batteryPct = (level * 100 / scale.toFloat()).toInt()
                    }
                    val temp = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
                    batteryTemp = temp / 10.0
                    batteryVolt = batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)

                    val status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                            status == BatteryManager.BATTERY_STATUS_FULL
                    batteryStatusStr = if (isCharging) "充电中" else "放电中"
                }
            }
        }

        return MetricSnapshot(
            timestamp = now,
            fps = Math.round(currentFps * 10) / 10.0,
            janks = currentJanks,
            jitterMs = Math.round(currentJitterMs * 10) / 10.0,
            cpuUsageApp = Math.round(cpuUsage * 10) / 10.0,
            memoryPssMb = Math.round(pssMb * 10) / 10.0,
            memoryJavaHeapMb = Math.round(javaHeapMb * 10) / 10.0,
            rxSpeedKb = Math.round(rxSpeed * 10) / 10.0,
            txSpeedKb = Math.round(txSpeed * 10) / 10.0,
            batteryPct = batteryPct,
            batteryTemp = batteryTemp,
            batteryVolt = batteryVolt,
            batteryStatus = batteryStatusStr,
            isCharging = isCharging
        )
    }

    private fun snapshotToMap(s: MetricSnapshot): Map<String, Any> {
        return mapOf(
            "timestamp" to s.timestamp,
            "fps" to s.fps,
            "janks" to s.janks,
            "jitterMs" to s.jitterMs,
            "cpuUsageApp" to s.cpuUsageApp,
            "memoryPssMb" to s.memoryPssMb,
            "memoryJavaHeapMb" to s.memoryJavaHeapMb,
            "rxSpeedKb" to s.rxSpeedKb,
            "txSpeedKb" to s.txSpeedKb,
            "batteryPct" to s.batteryPct,
            "batteryTemp" to s.batteryTemp,
            "batteryVolt" to s.batteryVolt,
            "batteryStatus" to s.batteryStatus,
            "isCharging" to s.isCharging
        )
    }
}
