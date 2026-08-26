<h1 align="center">
  <img src="app/src/main/assets/logo.svg" width="48" height="48" align="absmiddle" alt=""> Operit AI · AI 团队版
</h1>

<div align="center">
  <img src="https://img.shields.io/github/last-commit/qinghai-lipipeng/Operit" alt="最近提交">
  <img src="https://img.shields.io/badge/Platform-Android_8.0%2B-brightgreen.svg" alt="平台">
  <img src="https://img.shields.io/badge/License-LGPL--3.0--only-blue.svg" alt="许可证">
  <br>
  <a href="docs/doc-src/dev-core/BUILDING.md"><img src="https://img.shields.io/badge/📦-构建指南-blue.svg" alt="构建指南"></a>
  <a href="docs/doc-src/dev-core/CONTRIBUTING.md"><img src="https://img.shields.io/badge/contributions-welcome-brightgreen.svg" alt="欢迎贡献"></a>
</div>

<div align="center">
  <img src="docs/assets/readme/operit-ai-banner-zh-cn.webp" width="100%" alt="Operit AI - Android 开源 AI Agent 平台（AI 团队版）">
</div>

## 📋 出处声明 (Attribution)

本仓库是原项目 **[Operit](https://github.com/AAswordman/Operit)**（作者：AAswordman）的复刻版本，基于 [LGPL-3.0](LICENSE) 许可证进行二次开发。原项目版权归原作者所有，详细信息请参阅本仓库的 [NOTICE](NOTICE) 与 [LICENSE](LICENSE) 文件。

## 项目简介

本仓库将 Operit 改造为「**AI 团队软件**」：由一名指挥者（Leader）统筹项目，指挥者既可以自己动手干活，也可以通过 `delegate_task` 工具将子任务委派给多名工作者（Worker）；历史消息按「项目 → 指挥者 → 工作者」的树状结构组织与展示。

团队协作的落地方式：

- **指挥者与工作者**：角色卡新增 `roleType`（LEADER / WORKER / GENERAL）与 `teamId` 字段，团队卡（`CharacterGroupCard`）新增 `leaderCardId` 指定指挥者
- **指挥工具**：内置 `delegate_task`（委派任务）与 `list_team_members`（查看团队成员）两个工具，指挥者按普通轮次直接执行任务时不依赖委派
- **历史消息树**：聊天历史按角色分组为树状结构（项目 → 指挥者 / 工作者），提供 `TeamHistoryTree` 组件用于树状渲染

架构细节见 [docs/TEAM_ARCHITECTURE.md](docs/TEAM_ARCHITECTURE.md)。

## 与上游的主要差异

本 fork 面向「AI 团队软件」方向，除新增团队协作能力外，还移除了以下模块：

- **虚拟形象**：`avator/`（DragonBones / MMD / FBX 渲染）及应用内相关 UI
- **本地大模型**：`llm/`（MNN、llama.cpp），云端 LLM 提供商全部保留
- **Web Chat**：`web-chat/` 前端与 External HTTP Chat 后端服务器

其余核心能力（云端模型接入、工具调用、ToolPkg / MCP / Skill 扩展、工作区、记忆体系、UI 自动化等）均保留。完整的目录结构说明见 [Repo_Arch_Basic.md](Repo_Arch_Basic.md)。

## 核心能力

- **面向任务的 Agent 工作台**：支持附件、工作区上下文、工具执行过程、文件变更和多轮任务管理
- **自主选择模型与服务**：连接主流云模型、自定义兼容端点和密钥池，可分别为聊天、记忆、总结、UI 控制等任务指定模型
- **深度集成 Android 与网页环境**：在授权后调用系统能力、操作应用界面，或通过内置浏览器读取和操作网页
- **移动开发与终端环境**：在手机上管理项目、编辑代码、预览网页，并使用 Ubuntu 用户空间、SSH 和 SFTP
- **长期记忆与角色体系**：管理图谱化记忆、对话历史与角色卡，角色卡可绑定独立模型与工具白名单
- **可组合的扩展与自动化**：通过统一市场、工作流和系统集成组合工具，构建可重复执行的任务流程

## 文档导航

- [AI 团队软件架构](docs/TEAM_ARCHITECTURE.md) — 团队模型、数据层、指挥工具与历史消息树的设计
- [仓库目录结构](Repo_Arch_Basic.md) — 根目录各模块的用途说明
- [构建指南](docs/doc-src/dev-core/BUILDING.md) — Linux 环境下从零编译 APK 的完整步骤
- [贡献指南](docs/doc-src/dev-core/CONTRIBUTING.md) — 分支命名与 PR 规范
- [ToolPkg 格式指南](docs/TOOLPKG_FORMAT_GUIDE.md) 与 [脚本开发指南](docs/SCRIPT_DEV_GUIDE.md) — 扩展包与脚本开发

## 构建

环境要求与详细步骤见[构建指南](docs/doc-src/dev-core/BUILDING.md)。大致流程：

```bash
git clone --recurse-submodules https://github.com/qinghai-lipipeng/Operit.git
cd Operit
./gradlew :app:assembleDebug
```

注意：`terminal` 子模块（OperitTerminalCore）在克隆后需初始化检出，否则 Gradle 无法解析 `:terminal` 模块。

## 许可证

本项目基于 [GNU LGPL-3.0](LICENSE) 许可证发布，二次开发同样遵循该许可。 fork 关系与出处声明见 [NOTICE](NOTICE)。
