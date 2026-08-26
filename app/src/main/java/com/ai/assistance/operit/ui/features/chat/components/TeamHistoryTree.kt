package com.ai.assistance.operit.ui.features.chat.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.model.ChatMessage

/*
 * 团队历史消息树组件（AI 团队软件方向）。
 * 当前尚未接入任何聊天界面，聊天页仍按普通流式布局渲染；
 * 接入时建议由 ChatViewModel 提供 leaderName（当前团队指挥者名）。
 */

/**
 * 团队历史消息树节点
 * 结构：项目(Project) -> 指挥者(Leader) / 工作者(Worker)
 */
data class TeamHistoryBranch(
    val roleName: String, // 指挥者或工作者名
    val isLeader: Boolean, // 是否为指挥者
    val messages: List<ChatMessage> // 该角色发送的消息
)

/**
 * 将一段聊天历史按角色（指挥者/工作者）分组为树状结构。
 *
 * 分组键：AI 消息使用 [ChatMessage.roleName]；无角色名时归入“AI”。
 * 用户消息单独保留为 user 分支。
 */
fun buildTeamHistoryTree(
    messages: List<ChatMessage>,
    leaderName: String? = null
): List<TeamHistoryBranch> {
    val userMessages = messages.filter { it.sender == "user" }
    val aiMessages = messages.filter { it.sender == "ai" }
    val groupedByRole = aiMessages.groupBy { it.roleName.ifBlank { "AI" } }
    val orderedRoles = groupedByRole.keys.sortedBy { name -> if (name == leaderName) 0 else 1 }

    return buildList {
        if (userMessages.isNotEmpty()) {
            add(TeamHistoryBranch(roleName = "用户", isLeader = false, messages = userMessages))
        }
        orderedRoles.forEach { role ->
            add(
                TeamHistoryBranch(
                    roleName = role,
                    isLeader = role == leaderName,
                    messages = groupedByRole.getValue(role)
                )
            )
        }
    }
}

/**
 * 树状展示团队历史消息：项目 -> 指挥者 / 工作者（可折叠由调用方控制）。
 */
@Composable
fun TeamHistoryTreeView(
    branches: List<TeamHistoryBranch>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // 项目根节点
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "📁 项目",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }
        branches.forEach { branch ->
            Row(
                modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (branch.isLeader) "🎖 " else "👤 ",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = branch.roleName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (branch.isLeader) FontWeight.SemiBold else FontWeight.Normal
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "（${branch.messages.size} 条消息）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
