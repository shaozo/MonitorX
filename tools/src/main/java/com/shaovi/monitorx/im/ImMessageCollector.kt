package com.shaovi.monitorx.im

import com.google.gson.Gson
import com.shaovi.monitorx.MonitorHelper
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.util.ArrayDeque
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * IM 消息抓包收集器。
 *
 * 每条记录以单行 JSON 的形式追加到 [FILE_NAME]，读写操作通过 [lock] 串行化。
 * 这里同时从记录数、文件字节数、单条内容和待写队列四个维度设置上限，
 * 避免调试监控数据在业务进程中引发 OOM。
 */
object ImMessageCollector {
    private const val FILE_NAME = "monitor_im_records.jsonl"
    // 裁剪时先写入临时文件，避免边读边覆盖源文件。
    private const val TEMP_FILE_NAME = "$FILE_NAME.tmp"

    // 记录条数上限，也是查询一次允许保留在内存中的最大记录数。
    const val MAX_RECORD_COUNT = 3000

    // 条数无法约束大消息，因此再对日志文件设置 8 MiB 的硬上限。
    private const val MAX_FILE_SIZE_BYTES = 8L * 1024 * 1024

    // 入队前限制单条消息长度，防止超大 content 同时占用队列和 JSON 序列化内存。
    private const val MAX_CONTENT_LENGTH = 256 * 1024

    // 写入速度跟不上消息速度时，最多缓存 64 条待写任务。
    private const val MAX_PENDING_RECORDS = 64
    private const val CONTENT_TRUNCATED_SUFFIX = "\n--- Content truncated ---"

    // 正常情况每新增 200 条执行一次条数裁剪，减少每次写入都扫描文件的 IO 开销。
    private const val TRIM_TRIGGER_COUNT = 200

    private val gson = Gson()

    /**
     * 单线程写入保证 JSONL 记录顺序稳定。
     *
     * 队列满时丢弃最旧的待写监控记录：抓包数据允许降级，
     * 但不能使用无界队列持续占用业务进程内存。
     */
    private val executor = ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(MAX_PENDING_RECORDS),
        ThreadPoolExecutor.DiscardOldestPolicy()
    )

    // collect 在工作线程写入，query/clear 可能从其他线程调用，所有文件状态都由此锁保护。
    private val lock = Any()
    private var currentId = 0L

    // 距离上一次裁剪新增的记录数。
    private var unTrimmedCount = 0

    /**
     * 异步收集一条 IM 消息。
     *
     * 截断必须发生在 [executor] 入队之前，否则 Runnable 仍会强引用原始大字符串，
     * 在消息高峰时可能因队列积压形成另一个内存峰值。
     */
    @JvmStatic
    fun collect(type: Int, content: String) {
        val safeContent = truncateContent(content)
        executor.execute {
            synchronized(lock) {
                val context = MonitorHelper.context ?: return@synchronized
                val file = File(context.filesDir, FILE_NAME)
                file.parentFile?.mkdirs()

                // 先处理旧版遗留的超大文件，再解析 ID，避免 forEachLine 读取过多数据。
                if (file.length() > MAX_FILE_SIZE_BYTES) {
                    trimIfNeeded(file)
                }
                ensureCurrentId(file)
                val record = ImMessageRecord(
                    id = ++currentId,
                    timestamp = System.currentTimeMillis(),
                    type = type,
                    content = safeContent
                )
                file.appendText(gson.toJson(record) + "\n")
                unTrimmedCount++

                // 文件字节数超限时立即裁剪，不等待达到批量触发条件。
                if (unTrimmedCount >= TRIM_TRIGGER_COUNT || file.length() > MAX_FILE_SIZE_BYTES) {
                    trimIfNeeded(file)
                }
            }
        }
    }

    /** 清除正式日志、可能遗留的临时文件，并重置内存计数器。 */
    fun clear() {
        synchronized(lock) {
            val context = MonitorHelper.context ?: return
            File(context.filesDir, FILE_NAME).delete()
            File(context.filesDir, TEMP_FILE_NAME).delete()
            currentId = 0L
            unTrimmedCount = 0
        }
    }

    /**
     * 按时间倒序查询记录，可选择按 [type] 过滤。
     *
     * 文件仍按正序逐行扫描，但只用固定大小的 [ArrayDeque] 保留最新的
     * `offset + limit` 条匹配记录。这样不会像 `readLines()` 一样把整个文件加载到内存。
     */
    fun query(limit: Int = 100, offset: Int = 0, type: Int? = null): List<ImMessageRecord> {
        synchronized(lock) {
            val context = MonitorHelper.context ?: return emptyList()
            val file = File(context.filesDir, FILE_NAME)
            if (!file.exists()) return emptyList()
            if (file.length() > MAX_FILE_SIZE_BYTES) {
                trimIfNeeded(file)
            }

            val safeLimit = limit.coerceIn(0, MAX_RECORD_COUNT)
            val safeOffset = offset.coerceAtLeast(0)
            if (safeLimit == 0 || safeOffset >= MAX_RECORD_COUNT) return emptyList()

            val requiredCount = (safeOffset.toLong() + safeLimit)
                .coerceAtMost(MAX_RECORD_COUNT.toLong())
                .toInt()
            val records = ArrayDeque<ImMessageRecord>(requiredCount)
            file.forEachLine { line ->
                // 单条损坏不应阻断整个查询，直接跳过无法解析的 JSONL 行。
                val record = runCatching {
                    gson.fromJson(line, ImMessageRecord::class.java)
                }.getOrNull() ?: return@forEachLine
                if (type != null && record.type != type) return@forEachLine

                // 队列满后移除最旧元素，因此扫描结束时只保留最新的 requiredCount 条。
                if (records.size == requiredCount) {
                    records.removeFirst()
                }
                records.addLast(record)
            }
            return records.toList()
                .asReversed()
                .drop(safeOffset)
                .take(safeLimit)
        }
    }

    fun queryAfterId(lastId: Long, limit: Int = 100, type: Int? = null): List<ImMessageRecord> {
        return query(limit = MAX_RECORD_COUNT, offset = 0, type = type)
            .filter { it.id > lastId }
            .take(limit.coerceAtLeast(0))
    }

    fun queryById(id: Long): ImMessageRecord? {
        return query(limit = MAX_RECORD_COUNT).firstOrNull { it.id == id }
    }

    /**
     * 同时按记录数和文件字节数裁剪日志。
     *
     * 旧实现通过 `readLines().takeLast().joinToString()` 裁剪，会同时持有整文件、
     * 最后 3000 行和新的大字符串，`StringBuilder` 扩容时容易 OOM。
     *
     * 当前实现只保留最后 [MAX_RECORD_COUNT] 个行起始偏移，计算出最终的起始位置后，
     * 使用固定大小的字节缓冲区把文件尾部复制到临时文件，内存占用不再随文件增长。
     */
    private fun trimIfNeeded(file: File) {
        if (!file.exists() || file.length() == 0L) {
            unTrimmedCount = 0
            return
        }

        val fileLength = file.length()
        val lineStarts = findLastLineStarts(file, fileLength)

        // 字节上限可能落在某条 UTF-8 JSON 中间，必须前进到下一个完整行起点。
        val earliestByteToKeep = (fileLength - MAX_FILE_SIZE_BYTES).coerceAtLeast(0L)
        while (lineStarts.isNotEmpty()) {
            val firstLineStart = lineStarts.peekFirst() ?: break
            if (firstLineStart >= earliestByteToKeep) break
            lineStarts.removeFirst()
        }
        val startOffset = lineStarts.peekFirst() ?: fileLength
        if (startOffset == 0L) {
            unTrimmedCount = 0
            return
        }

        val tempFile = File(file.parentFile, TEMP_FILE_NAME)
        tempFile.delete()
        try {
            // RandomAccessFile 可直接 seek 到需要保留的第一行，无需再解析或拼接 JSON。
            RandomAccessFile(file, "r").use { input ->
                input.seek(startOffset)
                tempFile.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val readCount = input.read(buffer)
                        if (readCount < 0) break
                        output.write(buffer, 0, readCount)
                    }
                }
            }

            // 只有临时文件完整写入后才覆盖源文件，避免读写同一文件造成数据错位。
            tempFile.copyTo(file, overwrite = true)
            unTrimmedCount = 0
        } finally {
            // 无论复制是否成功都清理临时文件，避免额外占用应用内部存储。
            tempFile.delete()
        }
    }

    /**
     * 进程重启后从文件恢复最大 ID。
     *
     * 使用 [File.forEachLine] 逐行处理，只保留当前最大值，不创建整文件记录列表。
     */
    private fun ensureCurrentId(file: File) {
        if (currentId > 0L || !file.exists()) return
        var maxId = 0L
        file.forEachLine { line ->
            val id = runCatching {
                gson.fromJson(line, ImMessageRecord::class.java)?.id
            }.getOrNull() ?: return@forEachLine
            if (id > maxId) maxId = id
        }
        currentId = maxId
    }

    /**
     * 返回文件最后 [MAX_RECORD_COUNT] 行的起始字节偏移。
     *
     * JSON 字符串内的换行符会被 Gson 转义，因此文件中的 `\n` 字节可以作为可靠的记录边界。
     * 队列中仅保存 Long 偏移，即使扫描旧版遗留的大文件也不会创建大字符串。
     */
    private fun findLastLineStarts(file: File, fileLength: Long): ArrayDeque<Long> {
        val lineStarts = ArrayDeque<Long>(MAX_RECORD_COUNT)
        lineStarts.addLast(0L)
        var absoluteOffset = 0L
        FileInputStream(file).buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val readCount = input.read(buffer)
                if (readCount < 0) break
                for (index in 0 until readCount) {
                    if (buffer[index] != '\n'.code.toByte()) continue
                    val nextLineStart = absoluteOffset + index + 1
                    if (nextLineStart >= fileLength) continue
                    if (lineStarts.size == MAX_RECORD_COUNT) {
                        lineStarts.removeFirst()
                    }
                    lineStarts.addLast(nextLineStart)
                }
                absoluteOffset += readCount
            }
        }
        return lineStarts
    }

    /**
     * 截断超大消息，并尽量保留完整的 Unicode 字符。
     *
     * Kotlin/Java 字符串以 UTF-16 表示；如果截断点刚好落在代理对中间，
     * 向前回退一个 Char，避免将 emoji 等字符切成非法序列。
     */
    private fun truncateContent(content: String): String {
        if (content.length <= MAX_CONTENT_LENGTH) return content
        var endIndex = MAX_CONTENT_LENGTH
        if (Character.isHighSurrogate(content[endIndex - 1]) &&
            Character.isLowSurrogate(content[endIndex])
        ) {
            endIndex--
        }
        return content.substring(0, endIndex) + CONTENT_TRUNCATED_SUFFIX
    }
}
