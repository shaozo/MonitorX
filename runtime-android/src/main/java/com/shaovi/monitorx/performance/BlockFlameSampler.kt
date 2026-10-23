package com.shaovi.monitorx.performance

import android.os.Looper
import android.util.Printer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

data class FlameNode(
    val name: String,
    var value: Int = 0,
    val childrenMap: MutableMap<String, FlameNode> = mutableMapOf()
) {
    fun toMap(): Map<String, Any> {
        val result = mutableMapOf<String, Any>(
            "name" to name,
            "value" to value
        )
        if (childrenMap.isNotEmpty()) {
            result["children"] = childrenMap.values.map { it.toMap() }
        }
        return result
    }
}

data class BlockRecord(
    val id: String,
    val timestamp: Long,
    val durationMs: Long,
    val mainThreadName: String,
    val targetHandlerMsg: String,
    val sampleCount: Int,
    val flameTree: Map<String, Any>
)

object BlockFlameSampler {

    @Volatile
    var isEnabled = false
        private set

    private var blockThresholdMs = 200L // Define block if msg duration > 200ms
    private var sampleIntervalMs = 10L  // Sample stack every 10ms

    private var scheduler: ScheduledExecutorService? = null
    private var processor: ScheduledExecutorService? = null
    private data class DispatchState(
        val generation: Long = 0L,
        val startedAtNanos: Long = 0L,
        val message: String = "",
        val isDispatching: Boolean = false
    )

    private data class CompletedDispatch(
        val generation: Long,
        val durationNanos: Long,
        val message: String
    )

    @Volatile private var dispatchState = DispatchState()
    private val completedDispatch = AtomicReference<CompletedDispatch?>()
    private var previousPrinter: Printer? = null

    private val records = ConcurrentHashMap<String, BlockRecord>()
    private val idCounter = AtomicInteger(1)

    private val looperPrinter = object : Printer {
        override fun println(x: String) {
            previousPrinter?.println(x)
            if (!isEnabled) return
            if (x.startsWith(">>>>> Dispatching to")) {
                val previous = dispatchState
                dispatchState = DispatchState(
                    generation = previous.generation + 1,
                    startedAtNanos = System.nanoTime(),
                    message = x.substringAfter(">>>>> Dispatching to "),
                    isDispatching = true
                )
            } else if (x.startsWith("<<<<< Finished to")) {
                val state = dispatchState
                if (state.isDispatching) {
                    val durationNanos = System.nanoTime() - state.startedAtNanos
                    dispatchState = state.copy(isDispatching = false)
                    if (durationNanos >= TimeUnit.MILLISECONDS.toNanos(blockThresholdMs)) {
                        completedDispatch.set(CompletedDispatch(state.generation, durationNanos, state.message))
                    }
                }
            }
        }
    }

    @Synchronized
    fun start(blockThreshold: Long = 200L, sampleInterval: Long = 10L) {
        if (isEnabled) return
        this.blockThresholdMs = blockThreshold.coerceAtLeast(50L)
        this.sampleIntervalMs = sampleInterval.coerceAtLeast(2L)

        scheduler = Executors.newSingleThreadScheduledExecutor()
        processor = Executors.newSingleThreadScheduledExecutor()
        startSamplingLoop()
        previousPrinter = readCurrentLooperPrinter()
        this.isEnabled = true
        Looper.getMainLooper().setMessageLogging(looperPrinter)
    }

    @Synchronized
    fun stop() {
        if (!isEnabled) return
        this.isEnabled = false
        Looper.getMainLooper().setMessageLogging(previousPrinter)
        previousPrinter = null
        dispatchState = dispatchState.copy(isDispatching = false)
        completedDispatch.set(null)
        scheduler?.shutdownNow()
        // Preserve blocks that already finished dispatching and are waiting for aggregation.
        processor?.shutdown()
        scheduler = null
        processor = null
    }

    fun clearRecords() {
        records.clear()
    }

    fun getRecordList(): List<Map<String, Any>> {
        return records.values.sortedByDescending { it.timestamp }.map {
            mapOf(
                "id" to it.id,
                "timestamp" to it.timestamp,
                "durationMs" to it.durationMs,
                "mainThreadName" to it.mainThreadName,
                "targetHandlerMsg" to it.targetHandlerMsg,
                "sampleCount" to it.sampleCount
            )
        }
    }

    fun getFlameGraphData(id: String): Map<String, Any>? {
        return records[id]?.flameTree
    }

    private fun startSamplingLoop() {
        val mainThread = Looper.getMainLooper().thread
        var sampledGeneration = 0L
        val samples = mutableListOf<Array<StackTraceElement>>()

        scheduler?.scheduleWithFixedDelay({
            val completed = completedDispatch.get()
            if (completed != null && completed.generation == sampledGeneration) {
                completedDispatch.compareAndSet(completed, null)
                if (samples.isNotEmpty()) {
                    processCompletedDispatch(completed, ArrayList(samples))
                }
            }

            val state = dispatchState
            if (state.generation != sampledGeneration) {
                sampledGeneration = state.generation
                samples.clear()
            }
            if (isEnabled && state.isDispatching && samples.size < 500) {
                mainThread.stackTrace.takeIf { it.isNotEmpty() }?.let(samples::add)
            }
        }, 0L, sampleIntervalMs, TimeUnit.MILLISECONDS)
    }

    private fun processCompletedDispatch(completed: CompletedDispatch, samples: List<Array<StackTraceElement>>) {
        // Tree aggregation can be expensive and must never extend the main-thread block.
        processor?.execute {
                val recordId = "block_" + System.currentTimeMillis() + "_" + idCounter.getAndIncrement()
                val flameTree = buildFlameGraphTree(samples)
                val record = BlockRecord(
                    id = recordId,
                    timestamp = System.currentTimeMillis(),
                    durationMs = TimeUnit.NANOSECONDS.toMillis(completed.durationNanos),
                    mainThreadName = Looper.getMainLooper().thread.name,
                    targetHandlerMsg = completed.message.take(150),
                    sampleCount = samples.size,
                    flameTree = flameTree.toMap()
                )
                // Retain last 30 block records
                if (records.size >= 30) {
                    val oldestKey = records.values.minByOrNull { it.timestamp }?.id
                    if (oldestKey != null) records.remove(oldestKey)
                }
                records[recordId] = record
        }
    }

    /** Best-effort preservation for apps that already installed a Looper Printer. */
    private fun readCurrentLooperPrinter(): Printer? = runCatching {
        val field = Looper::class.java.getDeclaredField("mLogging")
        field.isAccessible = true
        field.get(Looper.getMainLooper()) as? Printer
    }.getOrNull()

    private fun buildFlameGraphTree(samples: List<Array<StackTraceElement>>): FlameNode {
        val root = FlameNode(name = "main (Looper.loop)")
        root.value = samples.size

        for (stack in samples) {
            var curr = root
            // Stack elements from bottom (outer) to top (inner)
            val reversedStack = stack.reversed()
            for (elem in reversedStack) {
                val className = elem.className
                // Collapse framework dispatch frames into a real node so descendants keep
                // their stack depth instead of being re-parented to an unrelated caller.
                if (className.startsWith("android.os.Looper") || className.startsWith("android.os.Handler")) {
                    val child = curr.childrenMap.getOrPut("[Android message dispatch]") {
                        FlameNode(name = "[Android message dispatch]")
                    }
                    child.value++
                    curr = child
                    continue
                }
                val nodeName = "${className.substringAfterLast('.')}.${elem.methodName}:${elem.lineNumber}"
                val child = curr.childrenMap.getOrPut(nodeName) { FlameNode(name = nodeName) }
                child.value++
                curr = child
            }
        }
        return root
    }
}
