package com.ai.assistance.operit.core.team

/** 团队成员 */
data class TeamMember(
    val id: String,
    val name: String,
    val emoji: String = "🤖",
    val systemPrompt: String = "",
    val modelId: String? = null,
    val isFacilitator: Boolean = false
)

/** 团队消息 */
data class TeamMessage(
    val id: String,
    val memberId: String?,
    val memberName: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isUser: Boolean = false
)

/** 团队会话 */
data class TeamSession(
    val id: String,
    val name: String,
    val members: List<TeamMember> = emptyList(),
    val messages: List<TeamMessage> = emptyList()
)

/** @ 提及解析结果 */
data class MentionParseResult(
    val mentionAll: Boolean,
    val mentionedMembers: List<TeamMember>,
    val cleanText: String
)

object TeamMentionParser {

    private val MENTION_REGEX = Regex("@([^\\s@，,。.！!？?]+)")

    /**
     * 解析用户输入中的 @ 提及。
     *
     * - @全体 / @all / @所有人  -> 广播全员
     * - @成员名                  -> 定向到该成员
     */
    fun parse(text: String, members: List<TeamMember>): MentionParseResult {
        val allKeywords = setOf("全体", "所有人", "all", "everyone")
        var mentionAll = false
        val mentioned = mutableListOf<TeamMember>()

        MENTION_REGEX.findAll(text).forEach { match ->
            val name = match.groupValues[1].trim()
            if (name.lowercase() in allKeywords) {
                mentionAll = true
            } else {
                val hit = members.firstOrNull { it.name.equals(name, ignoreCase = true) }
                if (hit != null && mentioned.none { it.id == hit.id }) {
                    mentioned.add(hit)
                }
            }
        }

        val clean = MENTION_REGEX.replace(text, "").trim()
        return MentionParseResult(mentionAll, mentioned, clean)
    }

    /** 计算本条消息应该由哪些成员响应 */
    fun resolveResponders(
        result: MentionParseResult,
        members: List<TeamMember>
    ): List<TeamMember> = when {
        result.mentionAll -> members
        result.mentionedMembers.isNotEmpty() -> result.mentionedMembers
        else -> {
            // 没有任何 @ 时，由主控（或第一个成员）响应
            val facilitator = members.firstOrNull { it.isFacilitator } ?: members.firstOrNull()
            listOfNotNull(facilitator)
        }
    }
}
