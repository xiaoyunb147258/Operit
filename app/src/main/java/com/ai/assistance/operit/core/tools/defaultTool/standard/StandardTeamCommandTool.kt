package com.ai.assistance.operit.core.tools.defaultTool.standard

import android.content.Context
import com.ai.assistance.operit.data.model.CharacterRoleType
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.CharacterGroupCardManager
import kotlinx.coroutines.runBlocking

/** 一次委派请求（指挥者 -> 工作者） */
data class TeamDelegationRequest(
    val workerName: String,
    val taskDescription: String,
    val requestedAt: Long = System.currentTimeMillis()
)

/**
 * 团队指挥命令的共享状态。
 * 指挥者调用 delegate_task 后，此处记录委派请求，由编排层读取并触发对应工作者的独立 Agent 轮次。
 */
object TeamCommandManager {
    @Volatile
    var pendingDelegation: TeamDelegationRequest? = null

    fun recordDelegation(workerName: String, taskDescription: String) {
        pendingDelegation = TeamDelegationRequest(workerName = workerName, taskDescription = taskDescription)
    }

    fun consumeDelegation(): TeamDelegationRequest? {
        val request = pendingDelegation
        pendingDelegation = null
        return request
    }
}

/**
 * 团队指挥工具：让指挥者向工作者委派任务，或查看团队成员。
 */
object StandardTeamCommandTool {

    /** 委派任务给指定工作者 */
    fun delegateTask(context: Context, workerName: String, taskDescription: String): String {
        TeamCommandManager.recordDelegation(workerName, taskDescription)
        return "已向工作者「$workerName」委派任务：$taskDescription"
    }

    /** 列出当前团队的指挥者与工作者 */
    fun listTeamMembers(
        context: Context,
        teamId: String?
    ): String = runBlocking {
        val characterCardManager = CharacterCardManager.getInstance(context)
        val groupCardManager = CharacterGroupCardManager.getInstance(context)

        if (teamId.isNullOrBlank()) {
            // 无团队归属：列出所有已标记为指挥者/工作者的角色
            val cards = characterCardManager.getAllCharacterCards()
            val leaders = cards.filter { CharacterRoleType.normalize(it.roleType) == CharacterRoleType.LEADER }
            val workers = cards.filter { CharacterRoleType.normalize(it.roleType) == CharacterRoleType.WORKER }
            buildString {
                appendLine("团队成员（全局）：")
                appendLine("指挥者：${leaders.joinToString { it.name }.ifEmpty { "（无）" }}")
                appendLine("工作者：${workers.joinToString { it.name }.ifEmpty { "（无）" }}")
            }
        } else {
            val group = groupCardManager.getCharacterGroupCard(teamId!!)
            if (group == null) {
                "未找到团队：$teamId"
            } else {
                val leaderName = group.leaderCardId
                    ?.let { characterCardManager.getCharacterCard(it) }
                    ?.name
                    ?: "（未指定指挥者）"
                val workerNames = group.workerMembers
                    .mapNotNull { characterCardManager.getCharacterCard(it.characterCardId)?.name }
                    .ifEmpty { listOf("（无）") }

                buildString {
                    appendLine("团队「${group.name}」成员：")
                    appendLine("指挥者：$leaderName")
                    appendLine("工作者：${workerNames.joinToString("、")}")
                }
            }
        }
    }
}