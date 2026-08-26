package com.ai.assistance.operit.core.tools

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ToolProgressEvent(
    val toolName: String,
    val progress: Float,
    val message: String = "",
    val priority: Int = 0,
    val level: Int = 0
)

object ToolProgressBus {
    const val SUMMARY_PROGRESS_TOOL_NAME: String = "__SUMMARY__"

    private val _progress = MutableStateFlow<ToolProgressEvent?>(null)
    val progress: StateFlow<ToolProgressEvent?> = _progress.asStateFlow()

    private fun priorityForTool(toolName: String): Int {
        return when (toolName) {
            SUMMARY_PROGRESS_TOOL_NAME -> 1000
            "grep_context" -> 100
            "grep_code" -> 10
            "find_files" -> 5
            else -> 0
        }
    }

    fun update(toolName: String, progress: Float, message: String = "") {
        update(toolName = toolName, progress = progress, message = message, priority = priorityForTool(toolName))
    }

    fun update(toolName: String, progress: Float, message: String = "", priority: Int, level: Int = 0) {
        val next = ToolProgressEvent(
            toolName = toolName,
            progress = progress,
            message = message,
            priority = priority,
            level = level
        )

        // CAS 循环：读-判-写必须原子，否则并发 update 会基于过期 current 覆盖彼此的写入
        while (true) {
            val current = _progress.value
            val shouldReplace =
                current == null ||
                    current.toolName == next.toolName ||
                    current.progress >= 1f ||
                    next.priority > current.priority ||
                    (next.priority == current.priority && next.level >= current.level)

            if (!shouldReplace) return
            if (_progress.compareAndSet(current, next)) return
        }
    }

    fun clear() {
        _progress.value = null
    }
}
