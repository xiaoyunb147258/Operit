# AI 团队软件架构

Operit 已改造为「AI 团队软件」：由一名**指挥者(Leader)** 统筹，指挥者既可以自己动手干活，也可以将子任务委派给多名**工作者(Worker)**。整体协作围绕「**项目**」展开，历史消息按「项目 → 指挥者 → 工作者」的树状结构存储与展示。

## 团队模型

| 概念 | 落地载体 | 说明 |
|------|---------|------|
| 项目(Project) | `ChatEntity` / 对话 | 一次团队协作会话/工作区 |
| 指挥者(Leader) | `CharacterCard` + `roleType=LEADER` | 统筹规划，可调用 `delegate_task` 委派任务，也可直接执行 |
| 工作者(Worker) | `CharacterCard` + `roleType=WORKER` | 接受指挥者委派，各自绑定独立模型与工具白名单 |
| 团队(Team) | `CharacterGroupCard` + `leaderCardId` | 一名指挥者 + 多名工作者 |
| 历史消息树 | `MessageEntity`/`ChatMessage` 按 `roleName` 分组 | 项目 → 指挥者历史消息 / 工作者历史消息 |

## 数据层

- `CharacterCard` 新增字段：
  - `roleType`：`LEADER`（指挥者）/ `WORKER`（工作者）/ `GENERAL`（普通角色）
  - `teamId`：所属团队（项目）ID
- `CharacterGroupCard` 新增字段：
  - `leaderCardId`：指挥者角色卡 ID
  - `GroupMemberConfig.roleType`：成员角色类型
  - 计算属性 `workerMembers`（工作者列表）、`leaderMember`（指挥者成员）
- `CharacterCardManager`：
  - `setCharacterRole(cardId, roleType, teamId)`：注册角色为指挥者或工作者
- 角色卡/团队卡通过 Gson 与 DataStore 持久化，字段向后兼容（默认 `GENERAL`/`WORKER`）。

## 指挥工具

在 `ToolRegistration.kt` 中注册了团队指挥工具（`core/tools/defaultTool/standard/StandardTeamCommandTool.kt`）：

- **`delegate_task`**：指挥者将任务委派给指定工作者。调用 `worker_name` + `task_description`，通过 `TeamCommandManager` 记录委派请求，供编排层读取并触发对应工作者的独立 Agent 轮次。
- **`list_team_members`**：列出当前团队（或全局）的指挥者与工作者。
- **`TeamCommandManager`**：持有待处理的委派请求（`pendingDelegation`）。

指挥者支持「自己干活」：不调用委派工具时，按普通轮次直接执行（含工具调用）。

## 历史消息树状渲染

`ui/features/chat/components/TeamHistoryTree.kt`：

- `buildTeamHistoryTree(messages, leaderName)`：将聊天历史按角色（指挥者/工作者）分组为树状结构。
- `TeamHistoryTreeView(branches)`：树状 UI，根节点为「项目」，子节点为「指挥者 / 工作者」及其消息数。

## 当前实现状态

数据层与指挥工具已落地，编排与 UI 接线仍在进行中：

- 已完成：`CharacterCard` / `CharacterGroupCard` 的团队字段与持久化、`CharacterCardManager.setCharacterRole`、`delegate_task` 与 `list_team_members` 工具注册
- 尚未接线：`TeamCommandManager.consumeDelegation()` 暂无调用方，委派请求被记录后还没有编排层消费它、触发工作者的独立 Agent 轮次
- 尚未接线：`TeamHistoryTreeView` 与 `buildTeamHistoryTree` 暂未被任何界面引用，聊天界面仍按普通流式布局渲染历史

## 移除的内容

- **虚拟形象**：`avator/*`、`core/avatar`、DragonBones/MMD/FBX/glTF 渲染与相关 UI（悬浮全屏、助手配置、主题头像选择等静态头像除外）。
- **本地大模型**：`llm/*`（MNN、llama.cpp）、`MNNProvider`、`LlamaProvider`、`data/mnn`、`MnnModelDownload*`。云端 LLM 提供商保留。
- **Web Chat**：`web-chat/` 前端、`integrations/http` 后端服务器（External HTTP Chat）。

## 多模态生成模型

多模态生成（图像/视频/音频等）能力以**工具**形式注册，供团队角色通过工具白名单统一调用（保留现有绘图/媒体工具协议）。
