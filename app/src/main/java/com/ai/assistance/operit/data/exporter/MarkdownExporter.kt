package com.ai.assistance.operit.data.exporter

import android.content.Context
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.model.ChatMessage
import java.time.format.DateTimeFormatter

/**
 * Markdown 格式导出器
 */
object MarkdownExporter {
    
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    
    /**
     * 转义 chat-info / msg 注释里的元数据值
     * 值内的分隔符必须转义，否则导入侧按 [,;] 切分属性时会把值截断。
     * 实体不带分号（&#44 而非 &#44;）：分号同为切分符，带分号的实体会被一起切散。
     * & 必须最先转义，否则先引入的实体里的 & 会被后续替换二次转义
     */
    private fun escapeMetaValue(value: String) =
        value.replace("&", "&amp").replace(",", "&#44")
            .replace(";", "&#59").replace("=", "&#61")

    /**
     * 导出单个对话为 Markdown
     */
    fun exportSingle(context: Context, chatHistory: ChatHistory): String {
        val sb = StringBuilder()

        // 结构化元数据注释 (简化格式)
        // 格式: key=value, key=value
        val metaParts = mutableListOf<String>()
        metaParts.add("id=${escapeMetaValue(chatHistory.id)}")
        metaParts.add("title=${escapeMetaValue(chatHistory.title)}")
        metaParts.add("created=${escapeMetaValue(chatHistory.createdAt.format(dateFormatter))}")
        metaParts.add("updated=${escapeMetaValue(chatHistory.updatedAt.format(dateFormatter))}")
        if (chatHistory.group != null) {
            metaParts.add("group=${escapeMetaValue(chatHistory.group)}")
        }
        sb.appendLine("<!-- chat-info: ${metaParts.joinToString(", ")} -->")

        // YAML Front Matter (保留用于兼容性和可读性)
        sb.appendLine("---")
        sb.appendLine("title: ${chatHistory.title}")
        sb.appendLine("created: ${chatHistory.createdAt.format(dateFormatter)}")
        sb.appendLine("updated: ${chatHistory.updatedAt.format(dateFormatter)}")
        if (chatHistory.group != null) {
            sb.appendLine("group: ${chatHistory.group}")
        }
        sb.appendLine("messages: ${chatHistory.messages.size}")
        sb.appendLine("---")
        sb.appendLine()

        // 标题
        sb.appendLine("# ${chatHistory.title}")
        sb.appendLine()

        // 元信息
        sb.appendLine(context.getString(R.string.markdown_export_created_time, chatHistory.createdAt.format(dateFormatter)))
        sb.appendLine(context.getString(R.string.markdown_export_updated_time, chatHistory.updatedAt.format(dateFormatter)))
        if (chatHistory.group != null) {
            sb.appendLine(context.getString(R.string.markdown_export_group, chatHistory.group))
        }
        sb.appendLine()
        sb.appendLine("---")
        sb.appendLine()

        // 消息内容
        for (message in chatHistory.messages) {
            appendMessage(sb, message)
        }

        return sb.toString()
    }
    
    /**
     * 导出多个对话为 Markdown
     */
    fun exportMultiple(context: Context, chatHistories: List<ChatHistory>): String {
        val sb = StringBuilder()

        sb.appendLine(context.getString(R.string.markdown_export_title))
        sb.appendLine()
        sb.appendLine(context.getString(R.string.markdown_export_export_time, java.time.LocalDateTime.now().format(dateFormatter)))
        sb.appendLine(context.getString(R.string.markdown_export_conversation_count, chatHistories.size))
        sb.appendLine(context.getString(R.string.markdown_export_total_messages, chatHistories.sumOf { it.messages.size }))
        sb.appendLine()
        sb.appendLine("---")
        sb.appendLine()

        for ((index, chatHistory) in chatHistories.withIndex()) {
            if (index > 0) {
                sb.appendLine()
                sb.appendLine("---")
                sb.appendLine()
            }

            sb.append(exportSingle(context, chatHistory))
        }

        return sb.toString()
    }
    
    /**
     * 添加单条消息
     */
    private fun appendMessage(sb: StringBuilder, message: ChatMessage) {
        // 消息元数据注释 (简化格式)
        val msgParts = mutableListOf<String>()
        
        // 角色直接作为第一个参数，或者使用 role=xxx
        // 为了简洁，我们使用 role=xxx，但导入时支持简写
        val role = if (message.sender == "user") "user" else "ai"
        msgParts.add(role) // 简写: <!-- msg: user -->
        
        if (message.modelName.isNotEmpty() && message.modelName != "markdown") {
            msgParts.add("model=${escapeMetaValue(message.modelName)}")
        }
        
        msgParts.add("timestamp=${message.timestamp}")
        
        sb.appendLine("<!-- msg: ${msgParts.joinToString(", ")} -->")
        
        // 角色标题 (保留用于可读性)
        val roleIcon = if (message.sender == "user") "👤" else "🤖"
        val roleText = if (message.sender == "user") "User" else "Assistant"
        sb.appendLine("## $roleIcon $roleText")
        sb.appendLine()
        
        // 消息元数据（可选，视觉展示）
        if (message.modelName.isNotEmpty() && message.modelName != "markdown" && message.modelName != "unknown") {
            sb.appendLine("*Model: ${message.modelName}*")
            sb.appendLine()
        }
        
        // 消息内容
        sb.appendLine(message.content)
        sb.appendLine()
    }
}
