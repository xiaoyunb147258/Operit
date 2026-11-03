package com.ai.assistance.operit.ui.features.team

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.core.team.TeamAiService
import com.ai.assistance.operit.core.team.TeamMember
import com.ai.assistance.operit.core.team.TeamMessage
import com.ai.assistance.operit.core.team.TeamMentionParser
import kotlinx.coroutines.launch
import java.util.UUID

private val MEMBER_COLORS = listOf(
    Color(0xFF6C63FF), Color(0xFF00BFA5), Color(0xFFFF6B6B),
    Color(0xFFFFA726), Color(0xFF29B6F6), Color(0xFFAB47BC)
)

/** 智能体团队 · 群聊协作 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentTeamScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var members by remember { mutableStateOf<List<TeamMember>>(emptyList()) }
    var messages by remember { mutableStateOf<List<TeamMessage>>(emptyList()) }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }

    var showAddMember by remember { mutableStateOf(false) }
    var newMemberName by remember { mutableStateOf("") }
    var newMemberPrompt by remember { mutableStateOf("") }

    var showAutoDesign by remember { mutableStateOf(false) }
    var goalText by remember { mutableStateOf("") }

    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(modifier = modifier.fillMaxSize()) {
        // ===== 成员栏 =====
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Groups, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Row(
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (members.isEmpty()) {
                    Text(
                        "还没有成员",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                members.forEachIndexed { index, m ->
                    MemberChip(m, MEMBER_COLORS[index % MEMBER_COLORS.size]) {
                        input = if (input.isBlank()) "@${m.name} " else "$input @${m.name} "
                    }
                }
            }
            IconButton(onClick = { showAutoDesign = true }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.AutoAwesome, "AI 自动组队", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = { showAddMember = true }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Add, "添加成员", modifier = Modifier.size(18.dp))
            }
        }

        // ===== 状态条 =====
        if (statusText.isNotEmpty()) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(
                statusText,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }

        // ===== 消息区 =====
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (messages.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("智能体团队", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "先建团队，再开始协作",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { showAutoDesign = true }) {
                        Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("告诉 AI 我的目的，自动组队")
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = { showAddMember = true }) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("手动创建成员")
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages) { msg -> MessageBubble(msg, members) }
                }
            }
        }

        // ===== 快捷操作 =====
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AssistChip(onClick = { input = "@全体 " }, label = { Text("@全体", fontSize = 11.sp) })
            AssistChip(onClick = { messages = emptyList() }, label = { Text("清空", fontSize = 11.sp) })
        }

        // ===== 输入区 =====
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                if (input.isEmpty()) {
                    Text(
                        if (members.isEmpty()) "请先创建团队成员" else "发消息，@成员 定向，@全体 广播",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    enabled = !busy && members.isNotEmpty(),
                    textStyle = TextStyle(
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                enabled = !busy && input.isNotBlank() && members.isNotEmpty(),
                onClick = {
                    val text = input.trim()
                    if (text.isEmpty()) return@FilledIconButton

                    val parsed = TeamMentionParser.parse(text, members)
                    val responders = TeamMentionParser.resolveResponders(parsed, members)

                    val userMsg = TeamMessage(
                        id = UUID.randomUUID().toString(),
                        memberId = null,
                        memberName = "我",
                        content = text,
                        isUser = true
                    )
                    val historySnapshot = messages
                    messages = messages + userMsg
                    input = ""
                    busy = true

                    scope.launch {
                        val newReplies = mutableListOf<TeamMessage>()
                        responders.forEach { member ->
                            statusText = "${member.name} 正在思考…"
                            val reply = TeamAiService.memberReply(
                                context = context,
                                member = member,
                                teamMembers = members,
                                history = historySnapshot,
                                userInput = text
                            )
                            newReplies.add(
                                TeamMessage(
                                    id = UUID.randomUUID().toString(),
                                    memberId = member.id,
                                    memberName = member.name,
                                    content = reply
                                )
                            )
                            // 逐条追加，实时可见
                            messages = messages + newReplies.last()
                        }
                        statusText = ""
                        busy = false
                    }
                }
            ) {
                Icon(Icons.Default.Send, "发送")
            }
        }
    }

    // ===== 手动添加成员 =====
    if (showAddMember) {
        AlertDialog(
            onDismissRequest = { showAddMember = false },
            title = { Text("添加成员") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newMemberName,
                        onValueChange = { newMemberName = it },
                        label = { Text("名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newMemberPrompt,
                        onValueChange = { newMemberPrompt = it },
                        label = { Text("角色设定（可选）") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newMemberName.isNotBlank()) {
                        members = members + TeamMember(
                            id = UUID.randomUUID().toString(),
                            name = newMemberName.trim(),
                            emoji = "🤖",
                            systemPrompt = newMemberPrompt.trim(),
                            isFacilitator = members.isEmpty()
                        )
                        newMemberName = ""
                        newMemberPrompt = ""
                        showAddMember = false
                    }
                }) { Text("添加") }
            },
            dismissButton = { TextButton(onClick = { showAddMember = false }) { Text("取消") } }
        )
    }

    // ===== AI 自动组队 =====
    if (showAutoDesign) {
        AlertDialog(
            onDismissRequest = { if (!busy) showAutoDesign = false },
            title = { Text("AI 自动组队") },
            text = {
                Column {
                    Text(
                        "说说你的目的，AI 会设计合适的团队成员",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = goalText,
                        onValueChange = { goalText = it },
                        placeholder = { Text("例如：帮我做一个 Android 自动化测试项目", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                    if (statusText.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(6.dp))
                        Text(statusText, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy && goalText.isNotBlank(),
                    onClick = {
                        val goal = goalText.trim()
                        busy = true
                        statusText = "AI 正在设计团队…"
                        scope.launch {
                            val designed = TeamAiService.autoDesignMembers(context, goal)
                            if (designed.isNotEmpty()) {
                                members = designed
                                messages = messages + TeamMessage(
                                    id = UUID.randomUUID().toString(),
                                    memberId = null,
                                    memberName = "系统",
                                    content = "已根据目的创建团队：" + designed.joinToString("、") { it.emoji + it.name }
                                )
                            } else {
                                messages = messages + TeamMessage(
                                    id = UUID.randomUUID().toString(),
                                    memberId = null,
                                    memberName = "系统",
                                    content = "自动组队失败，请检查模型配置或手动创建成员"
                                )
                            }
                            statusText = ""
                            busy = false
                            showAutoDesign = false
                        }
                    }
                ) { Text("生成团队") }
            },
            dismissButton = {
                TextButton(onClick = { if (!busy) showAutoDesign = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun MemberChip(member: TeamMember, color: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(member.emoji, fontSize = 12.sp)
        Spacer(Modifier.width(4.dp))
        Text(member.name, fontSize = 11.sp, color = color, fontWeight = FontWeight.Medium)
        if (member.isFacilitator) {
            Spacer(Modifier.width(3.dp))
            Text("主", fontSize = 8.sp, color = color.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun MessageBubble(msg: TeamMessage, members: List<TeamMember>) {
    val memberIndex = members.indexOfFirst { it.id == msg.memberId }
    val isSystem = msg.memberName == "系统"
    val color = when {
        isSystem -> MaterialTheme.colorScheme.outline
        msg.isUser -> MaterialTheme.colorScheme.primary
        else -> MEMBER_COLORS[memberIndex.coerceAtLeast(0) % MEMBER_COLORS.size]
    }

    if (isSystem) {
        Text(
            msg.content,
            fontSize = 11.sp,
            color = Color(0xFF6BCB77),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        )
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!msg.isUser) {
            Box(
                modifier = Modifier.size(28.dp).background(color.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(members.getOrNull(memberIndex.coerceAtLeast(0))?.emoji ?: "🤖", fontSize = 13.sp)
            }
            Spacer(Modifier.width(8.dp))
        }
        Column(horizontalAlignment = if (msg.isUser) Alignment.End else Alignment.Start) {
            Text(msg.memberName, fontSize = 10.sp, color = color, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .background(color.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(msg.content, fontSize = 13.sp)
            }
        }
    }
}
