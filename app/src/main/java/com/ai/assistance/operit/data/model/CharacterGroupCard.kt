package com.ai.assistance.operit.data.model

/**
 * 群组角色卡成员配置
 */
data class GroupMemberConfig(
    val characterCardId: String,
    val orderIndex: Int = 0,
    val roleType: String = CharacterRoleType.WORKER // 团队成员角色类型（默认工作者）
)

/**
 * 群组角色卡（团队）
 * 一个团队 = 一名指挥者(Leader) + 多名工作者(Worker)
 */
data class CharacterGroupCard(
    val id: String,
    val name: String,
    val description: String = "",
    val leaderCardId: String? = null, // 指挥者角色卡ID
    val members: List<GroupMemberConfig> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    /** 工作者列表（排除指挥者） */
    val workerMembers: List<GroupMemberConfig>
        get() = members.filter { it.characterCardId != leaderCardId }

    /** 指挥者成员 */
    val leaderMember: GroupMemberConfig?
        get() = leaderCardId?.let { leaderId -> members.firstOrNull { it.characterCardId == leaderId } }
}
