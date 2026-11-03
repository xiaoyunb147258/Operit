package com.ai.assistance.operit.core.logging

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 内存日志环形缓冲。
 *
 * 设计要点：
 * 1. 只存内存，不落盘（用户要求：日志不写本地）。
 * 2. 固定容量环形缓冲，超出自动丢弃最旧一条，内存占用恒定。
 * 3. 默认只采集 WARN/ERROR（低开销），可选切到全部级别。
 * 4. 通过 StateFlow 暴露增量，Compose 端直接 collect 即可实时刷新。
 */
object InMemoryLogBuffer {

    /** 单条日志 */
    data class LogEntry(
        val timestamp: Long,
        val priority: Int,
        val tag: String,
        val message: String,
        val throwable: String? = null
    ) {
        fun levelName(): String = when (priority) {
            Log.VERBOSE -> "V"
            Log.DEBUG -> "D"
            Log.INFO -> "I"
            Log.WARN -> "W"
            Log.ERROR -> "E"
            Log.ASSERT -> "A"
            else -> "?"
        }

        fun formatTime(): String =
            TIME_FORMAT.format(Date(timestamp))
    }

    private val TIME_FORMAT = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    /** 默认容量：2000 条，约 1-2MB 内存 */
    private const val DEFAULT_CAPACITY = 2000

    /** 单条消息最大长度，防止某条巨型日志撑爆内存 */
    private const val MAX_MESSAGE_CHARS = 4000

    private val capacity = DEFAULT_CAPACITY
    private val buffer = ArrayDeque<LogEntry>(capacity)
    private val lock = Any()

    /** 采集级别阈值：默认 WARN，切到全部时变成 VERBOSE */
    @Volatile
    var minPriority: Int = Log.WARN
        private set

    /** 是否采集全部日志 */
    @Volatile
    var captureAll: Boolean = false
        private set

    /** 是否启用采集（关闭时零开销） */
    @Volatile
    var enabled: Boolean = true

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    private val _version = MutableStateFlow(0L)
    val version: StateFlow<Long> = _version.asStateFlow()

    /** 切换采集模式 */
    fun setCaptureAll(all: Boolean) {
        captureAll = all
        minPriority = if (all) Log.VERBOSE else Log.WARN
        synchronized(lock) {
            if (!all) {
                // 切回“仅错误”时，把非错误条目清掉，减少内存
                buffer.removeAll { it.priority < Log.WARN }
            }
            _entries.value = buffer.toList()
            _version.value = _version.value + 1
        }
    }

    /** 增删日志 */
    fun add(priority: Int, tag: String?, message: String?, throwable: Throwable? = null) {
        if (!enabled) return
        if (priority < minPriority) return

        val msg = message ?: ""
        val safeMessage = if (msg.length > MAX_MESSAGE_CHARS) {
            msg.substring(0, MAX_MESSAGE_CHARS) + "…[truncated]"
        } else msg

        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            priority = priority,
            tag = tag ?: "-",
            message = safeMessage,
            throwable = throwable?.let { shortStack(it) }
        )

        synchronized(lock) {
            if (buffer.size >= capacity) {
                buffer.removeFirst()
            }
            buffer.addLast(entry)
            // 只在需要时刷新快照：批量写入时由防抖控制
        }
        scheduleFlush()
    }

    private var lastFlushAt = 0L

    /** 防抖：最多每 250ms 同步一次 StateFlow，避免高频日志拖 CPU */
    private fun scheduleFlush() {
        val now = System.currentTimeMillis()
        if (now - lastFlushAt < 250) return
        lastFlushAt = now
        flushNow()
    }

    fun flushNow() {
        synchronized(lock) {
            _entries.value = buffer.toList()
            _version.value = _version.value + 1
        }
    }

    fun clear() {
        synchronized(lock) {
            buffer.clear()
            _entries.value = emptyList()
            _version.value = _version.value + 1
        }
    }

    /** 导出为纯文本（仅用于复制/分享，不写磁盘） */
    fun exportText(): String {
        val snapshot = synchronized(lock) { buffer.toList() }
        return buildString {
            snapshot.forEach { e ->
                append(e.formatTime())
                append(' ')
                append(e.levelName())
                append('/')
                append(e.tag)
                append(": ")
                append(e.message)
                if (e.throwable != null) {
                    append('\n')
                    append(e.throwable)
                }
                append('\n')
            }
        }
    }

    private fun shortStack(t: Throwable): String {
        val sw = java.io.StringWriter()
        t.printStackTrace(java.io.PrintWriter(sw))
        val s = sw.toString()
        return if (s.length > 4000) s.substring(0, 4000) + "…" else s
    }
}
