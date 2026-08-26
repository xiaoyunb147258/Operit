---
date: 2026-08-26
scope: app/src/main/java、tools/、ci/
method: 静态阅读，未执行构建与测试
status: 草稿；全部 32 项已按 1_FixPlan.md 完成修复（各节含 [DONE] 实施记录）
---

# 静态代码审核报告

## 概览

审核以四个并行通道覆盖 core、data、api/services 三大 Kotlin 分区与 Python、TypeScript 工具脚本。严重级条目均已对照源码二次确认，其余条目附有代码内证链。本报告只收录确定性高、有实际影响的问题，风格问题与纯理论性问题已排除。

问题计数：严重 9、高 9、中 8、低 6，共 32 项。配套修复方案见 [1_FixPlan.md](1_FixPlan.md)。

## 严重问题

严重指必然触发，或触发后造成死锁、ANR、数据丢失与静默数据损坏的问题。

### S1 标准执行器先等待后读流，大输出命令必然误报超时

位置：[StandardShellExecutor.kt](../../../app/src/main/java/com/ai/assistance/operit/core/tools/system/shell/StandardShellExecutor.kt#L57-L78)

`executeCommand` 与 `executeWithShell` 都是先 `process.waitFor` 等待进程退出，之后才读 stdout 与 stderr。Linux 管道缓冲区约 64KB，输出写满后子进程阻塞在 write 上无法退出，`waitFor` 必然超时，进程随后被销毁，已产生的输出全部丢失，错误信息显示为命令超时，与真实原因不符。

`cat` 大文件、`ls -R`、`find` 等输出超过缓冲区的常用命令都会触发，这是标准 Shell 工具的基础执行路径。

### S2 Root 执行器顺序读流且无超时，stderr 大输出时永久挂起

位置：[RootShellExecutor.kt](../../../app/src/main/java/com/ai/assistance/operit/core/tools/system/shell/RootShellExecutor.kt#L309-L325)

`executeCommandWithExec` 先把 stdout 读到 EOF 再读 stderr，`waitFor` 排在两个读循环之后且无超时参数。命令产生大量 stderr 而 stdout 稀少时，子进程阻塞在 stderr 管道上无法退出，stdout 管道不会关闭，读线程永久阻塞在 `readLine` 上。该路径没有任何超时保护，协程在 `Dispatchers.IO` 上永久挂起且无法取消。L386-L401 的 SHELL 身份 exec 分支为同一模式。

### S3 定位工具取消回调在主线程 runBlocking，自死锁

位置：[StandardSystemOperationTools.kt](../../../app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/standard/StandardSystemOperationTools.kt#L1572-L1577)

`get_location` 的 `invokeOnCancellation` 回调里执行 `runBlocking(Dispatchers.Main) { locationManager.removeUpdates(...) }`。取消回调在发起取消的线程上同步执行，挂起点本身位于主线程，聊天取消即主线程取消，回调在主线程阻塞等待一个必须由主线程 Looper 调度的任务，构成自死锁。

用户在定位工具执行中点停止即触发主线程 ANR。L1555 附近另用 `GlobalScope.launch` 做超时，未与 continuation 生命周期联动，属同源的实现错误。

### S4 Sherpa 语音 shutdown 在主线程死锁

位置：[SherpaSpeechProvider.kt](../../../app/src/main/java/com/ai/assistance/operit/api/speech/SherpaSpeechProvider.kt#L507-L529)、[SherpaMnnSpeechProvider.kt](../../../app/src/main/java/com/ai/assistance/operit/api/speech/SherpaMnnSpeechProvider.kt#L611-L644)

`shutdown` 用 `runBlocking { cancelRecognition() }` 阻塞调用线程，而 `cancelRecognition` 内部 `recordingJob?.join()` 要等录音循环退出，录音循环的 finally 块必须切回 `Dispatchers.Main` 才能执行。`shutdown` 从主线程调用且录音正在进行时，主线程被 runBlocking 阻塞，Main dispatcher 永远无法调度，join 永不返回，永久死锁。

已确认的触发路径：

- `AIForegroundService.onDestroy` 主线程经 `stopWakeMonitoring` 与 `releaseWakeSpeechProvider` 调用 `provider.shutdown`，开启唤醒监听后系统回收服务即触发
- `SpeechToTextScreen` 的 `LaunchedEffect` 与 `onDispose` 直接调用 `speechService.shutdown`，识别中切换引擎或退出页面即触发

### S5 消息数组导入分支不可达，标准 JSON 被拆成 N 个单消息会话

位置：[GenericJsonConverter.kt](../../../app/src/main/java/com/ai/assistance/operit/data/converter/GenericJsonConverter.kt#L52-L57)

`isConversationObject` 的定义为含 messages 键、含 title 键、或同时含 role 与 content 键三者其一，见 L103-L107。`isMessageObject` 的定义为同时含 role 与 content 键，见 L112-L114，后者是前者的真子集。因此 L57 的 `else if (isMessageObject(...))` 分支是死代码。

导入最通用的格式 `[{"role":"user","content":"..."}, ...]` 时，每条消息都会经 `parseConversationObject` 变成一个独立的单消息 ChatHistory。导入 50 条消息得到 50 个单条消息的会话，而不是 1 个含 50 条消息的会话。

### S6 会话保存先删后插且无事务，中断即永久丢数据

位置：[ChatHistoryManager.kt](../../../app/src/main/java/com/ai/assistance/operit/data/repository/ChatHistoryManager.kt#L564-L621)

`saveChatHistoryInternal` 的完整写序列为删除该聊天全部消息与 variants、再重新插入，整个文件没有任何 `withTransaction` 调用，删除和插入是两个独立 Room 事务。进程在删除之后、插入完成之前被杀，该聊天所有消息与 variants 永久丢失。导入大量会话时逐会话调用此函数，时间窗口很大。并发的 `loadChatMessages` 在两事务之间查询会得到空或半截消息列表。

同源问题：

- `deleteMessageVariant` 依次执行删除 variant、重排后续索引、更新选中索引，同样无事务，中断后 `selectedVariantIndex` 指向不存在的 variant，`hydrateMessages` 的 `first { }` 抛 NoSuchElementException 被 `loadChatMessages` 捕获后返回空列表，数据库里数据完好但会话在 UI 上永久显示空白
- `deleteMessagesFrom` 先删 variants 再删 messages，中断会留下孤儿 variants

### S7 字符串统计脚本硬编码开发机路径，任何其他环境必崩

位置：[count_ui_strings.py](../../../tools/string/count_ui_strings.py#L228)

`file_path.relative_to('D:\\Code\\prog\\assistance')` 把原作者机器的 Windows 绝对路径硬编码进仓库，且该调用位于 L238 的 try 块之外。仓库不在该路径时，包括本仓库的 Linux 环境与任何其他克隆，处理第一个 Kotlin 文件即抛出未捕获的 ValueError，整个脚本崩溃。

L323 的 `rel_path.split('ui\\features\\')` 使用 Windows 反斜杠分隔符，在 Linux 上永远匹配不到，IndexError 被 L327 的裸 `except: pass` 吞掉，模块级统计静默丢失。

### S8 翻译同步脚本正则吞掉自闭合标签，误删相邻翻译条目

位置：[fill_missing_translations.py](../../../tools/string/fill_missing_translations.py#L215-L225)

`_remove_stale_strings` 先用 `string_pattern` 做替换，该模式的 `([^>]*)` 会连同自闭合标签结尾的斜杠一起捕获，随后的非贪婪 `.*?</string>` 在 DOTALL 下一直延伸到下一个成对条目的闭合标签，把自闭合条目与其后的正常条目整体吞为一个 match。L221 的 `empty_string_pattern` 专为自闭合设计，但作用于已被错误替换过的文本，几乎不会命中。

`values-pt-rBR/strings.xml` 的 L1514、L1515、L3904、L5081 确实存在自闭合条目，该文件正是此脚本的处理目标。自闭合条目的 name 属于 stale 集合时，从该行到下一个 `</string>` 的全部内容被整段误删，相邻无辜翻译行静默丢失并写入文件。被吞条目中有 stale 条目时，删除计数校验不符，L628 附近抛 RuntimeError 中止整个运行。

### S9 MCP 桥接 socket 空闲超时小于请求超时，长耗时工具必失败

位置：[index.ts](../../../tools/mcp_bridge/index.ts#L1194)

`socket.setTimeout(120000)` 小于 L94 的 `REQUEST_TIMEOUT = 180000`，注释声称是 REQUEST_TIMEOUT 的两倍，与实际不符。socket 超时是空闲超时，客户端发出 toolcall 后等待结果期间连接无数据流动。执行时间在 120 到 180 秒之间的工具调用，客户端连接先被服务端断开并从 `activeConnections` 移除，helper 返回结果时对已 end 的 socket 写入，客户端永远收不到结果。这正是 180 秒请求超时要覆盖的场景。

## 高危问题

### H1 MCP 会话创建竞态，远程连接永久泄漏

位置：[MCPToolExecutor.kt](../../../app/src/main/java/com/ai/assistance/operit/core/tools/mcp/MCPToolExecutor.kt#L519-L570)

`getOrCreateSession` 的读缓存、connect 秒级网络操作、写缓存三步完全无同步。`ConcurrentHashMap` 只保证单次操作原子。两个线程并发进入时各自创建并连接，后写者覆盖缓存，先建立连接的 session 从此失去引用，除换 descriptor 与注销外没有任何路径会关闭它，连接永久泄漏。系统提示词鼓励 AI 并行调用工具，`ToolExecutionManager` 确实会并行执行部分工具，触发概率高。

### H2 PhoneAgent 任务注册表 remove 竞态，任务脱离取消控制

位置：[PhoneAgentJobRegistry.kt](../../../app/src/main/java/com/ai/assistance/operit/core/tools/agent/PhoneAgentJobRegistry.kt#L32-L38)

`unregister` 在清空 set 后用单参 `remove(agentId)` 移除 map 条目。与 `register` 并发时，后者在 L15 拿到旧 set 引用但尚未 add，前者清空并移除条目，随后新任务被加入这个孤儿 set。该任务之后永远无法被 `cancelAgent` 与 `cancelAll` 找到，用户停止操作后 agent 任务继续在后台执行。

### H3 Gemini 流式括号深度计数不识别字符串字面量，内容丢失

位置：[GeminiProvider.kt](../../../app/src/main/java/com/ai/assistance/operit/api/chat/llmprovider/GeminiProvider.kt#L1548-L1551)

官方 Gemini 端点的流式响应走分段 JSON 累积分支，`jsonDepth` 用逐字符朴素计数，不跳过 JSON 字符串字面量内部的括号。模型输出的 text 字段包含未配对括号时，输出代码、JSON、`[笑]` 类文本极其常见：

- 深度提前归零，解析不完整 JSON 失败，该 chunk 与累积内容全部丢弃
- 深度被多算，chunk 边界永远不归零，多个对象拼接后解析失败丢弃
- 深度为负时后续 chunk 永远无法解析，流结束后的整体解析同样失败，整个响应内容丢失并报网络中断错误

### H4 不可恢复的 4xx 错误被重试 5 次共 31 秒

位置：[OpenAIProvider.kt](../../../app/src/main/java/com/ai/assistance/operit/api/chat/llmprovider/OpenAIProvider.kt#L1520-L1560)、[ClaudeProvider.kt](../../../app/src/main/java/com/ai/assistance/operit/api/chat/llmprovider/ClaudeProvider.kt#L1395-L1437)、[GeminiProvider.kt](../../../app/src/main/java/com/ai/assistance/operit/api/chat/llmprovider/GeminiProvider.kt#L1054-L1099)

三个 Provider 都定义了 `NonRetriableException`，注释声称是否重试由统一策略决定，但全仓库唯一的统一策略只有 429 时的中间提示抑制，不阻止重试。`handleRetryableError` 对一切 IOException 一视同仁地重试。API Key 无效、请求体非法、模型不存在等不可恢复错误被重试至 `MAX_RETRY_ATTEMPTS = 5`，指数退避累计 31 秒，并向服务器发送 6 次注定失败的请求。

### H5 工作流防重守卫 TOCTOU，同一工作流并发执行两份

位置：[WorkflowRepository.kt](../../../app/src/main/java/com/ai/assistance/operit/data/repository/WorkflowRepository.kt#L486-L491)

`triggerWorkflow` 在 L486 检查运行中任务列表，在 L491 注册当前 Job，两步之间没有持有 `runningWorkflowLock`。Tasker、Intent、语音触发同时到达时，两个并发调用都能通过空检查并各自注册、各自执行，防重守卫被击穿。运行期间的 `updateExecutionStatus` 对工作流 JSON 文件做读改整写，与用户编辑互相覆盖，丢更新。

### H6 MCP 本地服务状态非原子更新，并发下丢失与复活

位置：[MCPLocalServer.kt](../../../app/src/main/java/com/ai/assistance/operit/data/mcp/MCPLocalServer.kt#L770-L792)

`_serverStatus` 的三处更新均为读快照、修改、整体写回，而同类 `_mcpConfig` 一律使用原子的 `MutableStateFlow.update`。这些方法从多个 IO 协程并发调用。并发更新只保留后写者，丢失工具缓存导致重复连接 MCP 服务器，丢失错误状态。`removeServerStatus` 与 `updateServerStatus` 竞态时会把已删除服务器的状态复活，并向 `server_status.json` 持久化脏数据。

### H7 桥接启动器在 Linux 无条件覆盖 NODE_OPTIONS

位置：[spawn-helper.ts](../../../tools/mcp_bridge/spawn-helper.ts#L45-L52)

合并环境变量时，其他变量都采用未提供才设置的展开模式，唯独 `NODE_OPTIONS` 在 Linux 上被无条件置于展开链末尾，覆盖 `process.env` 与 `serviceInfo.env` 中已有的任何值。服务自定义的 `NODE_OPTIONS` 被静默丢弃。`--openssl-legacy-provider` 仅在 Node 17 以上存在，Linux 上 command 为 node 的 MCP 服务若运行在 Node 16，会因 bad option 直接启动失败。

### H8 Markdown 导出导入往返截断含逗号标题

位置：[MarkdownExporter.kt](../../../app/src/main/java/com/ai/assistance/operit/data/exporter/MarkdownExporter.kt#L24-L32)、[MarkdownConverter.kt](../../../app/src/main/java/com/ai/assistance/operit/data/converter/MarkdownConverter.kt#L211-L237)

导出侧把 `title=${chatHistory.title}` 以逗号拼接进 `chat-info` 注释，无任何转义。导入侧用 `Regex("[,;]")` 切分属性，且 chat-info 中的 title 覆盖先解析的 YAML front matter。标题含逗号或分号时，重新导入的标题在第一个分隔符处被截断，完整的 YAML 标题也被覆盖，属于确定性的往返数据损坏。模型名与分组名同理。

### H9 桥接 unspawn 后百毫秒重注册竞态，被杀服务意外自动重启

位置：[index.ts](../../../tools/mcp_bridge/index.ts#L868-L881)

`unspawn` 删除注册表条目、发出 kill 信号，再用固定 100 毫秒的 setTimeout 恢复注册。进程退出是异步的，退出事件在 100 毫秒之后才触发时，exit handler 执行时注册表已恢复该服务，`handleServiceClosure` 按意外退出处理并安排自动重启。unspawn 的语义是只关闭进程等待手动 spawn，实际变成杀掉后又被自动拉起，闲置回收同样失效，重启计数被无谓消耗，5 次后服务被标记为永久失败。

## 中危问题

### M1 终端命令收集协程在调用方取消时泄漏

位置：[Terminal.kt](../../../app/src/main/java/com/ai/assistance/operit/core/tools/system/Terminal.kt#L112-L136)

`job.cancel()` 排在 `deferred.await()` 之后且不在 finally 中。调用方协程被取消时 await 抛 CancellationException，cancel 被跳过，收集协程在类级 `CoroutineScope(Dispatchers.Main)` 上无限期 collect 一个 SharedFlow，连同捕获的输出缓冲一起泄漏。每次中断一个命令执行泄漏一个。

### M2 工具执行 Flow.catch 捕获取消异常，用户停止被误报为工具错误

位置：[ToolExecutionManager.kt](../../../app/src/main/java/com/ai/assistance/operit/api/chat/enhance/ToolExecutionManager.kt#L401-L412)

`Flow.catch` 会捕获上游的 CancellationException，官方要求在 catch 内重新抛出。用户在工具执行中点停止时，取消异常进入 catch，触发工具执行错误通知与错误日志，并构造错误 ToolResult 尝试在已取消的 collector 上 emit。

### M3 同会话并发请求的取消注册被误删，运行中的请求无法取消

位置：[AIMessageManager.kt](../../../app/src/main/java/com/ai/assistance/operit/core/chat/AIMessageManager.kt#L358)

注册直接按 chatKey 覆盖，完成回调按 key 移除且不校验实例身份。同一 chatKey 两个并发请求时，先完成者会移除后注册者的注册，此后 `cancelOperation` 找不到正在运行的 service，取消无法送达，流继续消耗 token 与后台资源。

### M4 会话服务多处 catch 吞掉协程取消异常

位置：[ConversationService.kt](../../../app/src/main/java/com/ai/assistance/operit/api/chat/enhance/ConversationService.kt#L370-L373)

L370、L1145、L1197、L1240、L1283 五处 catch 不区分 CancellationException，协程被取消时任务以空串或错误文本收尾而非传播取消，取消后空串被当作生成的标题使用。

### M5 前台服务在主线程 runBlocking 读 DataStore

位置：[AIForegroundService.kt](../../../app/src/main/java/com/ai/assistance/operit/api/chat/AIForegroundService.kt#L871-L877)

`onStartCommand` 与通知回调路径上共有 6 处以上 `runBlocking { ...first() }` 读取 DataStore。首次加载需要磁盘 IO，磁盘繁忙或该 DataStore 正被其他协程写入时阻塞可达秒级，叠加主线程其他工作即触发 ANR。`SpeechServiceFactory` 的 `getInstance` 路径存在同模式。

### M6 流式内容恰好等于 null 的分块被静默丢弃

位置：[OpenAIProvider.kt](../../../app/src/main/java/com/ai/assistance/operit/api/chat/llmprovider/OpenAIProvider.kt#L1383)

`isNotNullOrEmpty` 把 `this != "null"` 与非空判断绑定，本意是过滤 org.json 的 optString 对 JSON null 返回字面 null 的副作用，但同时把模型真实输出的 null 文本分块静默丢弃。模型输出代码或 JSON 时单个 delta 恰好为 null 的概率不低，输出内容损坏且无任何日志。

### M7 悬浮窗服务替换进程级默认异常处理器

位置：[FloatingChatService.kt](../../../app/src/main/java/com/ai/assistance/operit/services/FloatingChatService.kt#L86-L90)

服务存活期间任何线程的未捕获异常都走自定义 handler，被记为服务崩溃、保存状态并重启服务，主 Activity 与无关线程的崩溃被误伤。`crashCount` 是实例字段，进程崩溃后归零，`crashCount > 3` 永久禁用服务的分支几乎不可能触发，防护形同虚设。

### M8 导入统计从 Lazily StateFlow 取已有会话 ID，冷启动后立即导入全被计为新增

位置：[ChatHistoryManager.kt](../../../app/src/main/java/com/ai/assistance/operit/data/repository/ChatHistoryManager.kt#L2159)

`chatHistoriesFlow` 以 `SharingStarted.Lazily` 共享，`first()` 立即返回当前值。冷启动后立即导入时拿到初始值空列表，所有覆盖更新都被统计为新增。数据本身因 REPLACE 语义不受影响，仅 `ChatImportResult` 数字错误。

## 低危问题

### L1 工具进度总线非原子 check-then-act

位置：[ToolProgressBus.kt](../../../app/src/main/java/com/ai/assistance/operit/core/tools/ToolProgressBus.kt#L44-L55)

`shouldReplace` 判断与 `_progress.value` 赋值之间无原子性，并行工具的进度事件交错时低优先级事件可覆盖高优先级事件，仅进度 UI 错乱。

### L2 删除记忆空间后缓存仓库仍持有已关闭的 Store

位置：[ObjectBox.kt](../../../app/src/main/java/com/ai/assistance/operit/data/db/ObjectBox.kt#L43-L52)、[MemoryQueryToolExecutor.kt](../../../app/src/main/java/com/ai/assistance/operit/core/tools/memory/MemoryQueryToolExecutor.kt#L94-L95)

`delete` 关闭并删除 store 目录，`MemoryQueryToolExecutor` 的 computeIfAbsent 缓存与 `MemoryDocumentsProvider` 的 repositoryCache 永不清理，飞行中请求解析到已删除 profile 时对已关闭 store 调用 put 或 get 抛出 ObjectBox 异常。

### L3 热补丁应用脚本的 --keep 参数是死代码

位置：[apply_patch.py](../../../tools/hotbuild/apply_patch.py#L670-L677)

清理循环的循环体是 pass，两个分支都直接 return 0。无论是否传 `--keep`，下载的临时 patch 与 meta 文件既不会被清理也不会被区分处理，CLI 声明的功能失效。

### L4 热补丁脚本 subprocess 解码未指定编码

位置：[build_patch.py](../../../tools/hotbuild/build_patch.py#L326-L328)、[apply_patch.py](../../../tools/hotbuild/apply_patch.py#L40-L42)

`subprocess.run(..., text=True)` 未传 `encoding="utf-8"`，Windows 上按 ANSI 代码页解码 gh CLI 的 UTF-8 输出。当前 tag 与 repo 名恰好全 ASCII 未爆发，输出包含非 ASCII 时直接 UnicodeDecodeError 崩溃。同目录 `commit_ai.py` 已正确使用 bytes 加 decode。

### L5 桥接启动器 send 后立即 exit，IPC 消息可能丢失

位置：[spawn-helper.ts](../../../tools/mcp_bridge/spawn-helper.ts#L73-L78)

`process.send` 对 IPC 通道是异步写，紧随其后的 `process.exit(1)` 可能在消息 flush 之前终止进程。父进程依赖 closed 事件中的 error 文本判定 fatalServices，消息丢失后只能靠 exit 事件兜底，丢失 fatal 错误细节。

### L6 技能导入脚本硬编码开发机缓存目录

位置：[import_anthropic_skills.py](../../../tools/github/import_anthropic_skills.py#L450)

L450、L1412、L1509、L1563 四处硬编码 `D:\Code\prog\assistance\temp`。其他 Windows 机器没有 D 盘时直接失败，Linux 上 `Path(r"D:\...")` 是相对路径，会在当前目录创建出 `D:\Code\prog\assistance\temp` 目录树污染工作区。

## 审核中排除的项

以下疑点经深入阅读后确认不构成缺陷：

- `JsTools.kt` 的 17 处 `!!` 均为 JavaScript 源码中的逻辑非，不是 Kotlin 非空断言
- `ApkEditor.kt` 与 `StandardWorkflowTools.kt` 的 `getOrNull()!!` 处于 isSuccess 判定之后，repository 不会返回 success null
- Room 迁移链 v1 到 v21 含 MIGRATION_20_21 的唯一索引与遗留数据搬运，逻辑正确
- `ChatContentDao` 的分块物化边界、`ChatMessageTimestampAllocator` 的 CAS 单调递增、`TokenStatsTimeRange` 的本地时区与 DST 处理均自洽
- 三个 Provider 的 SSE 行解析用 readLine 不存在跨 TCP 分块的行截断，重试路径正确重抛用户取消
- `JsEngine` 的 runBlocking 快照、引擎池 Channel 加 finally 归还、`BluetoothSessionManager` 的锁一致、`WorkflowExecutor` 的拓扑排序与环检测未发现确定性缺陷
- `ci/script` 下的 zip 校验、本地化占位符比对、markdown 链接检查、双亲校验逻辑均验证无误
- `RootAuthorizer` 先读后 waitFor 的模式与 S1 同类，但执行的都是固定短命令，输出不会接近管道缓冲区，实际不可触发

## 修复优先级

第一阶段处理 S1 到 S9，基础工具正确性、主线程死锁与数据丢失。第二阶段处理 H1 到 H9，连接泄漏、取消失效与流式解析。第三阶段处理中低危，取消语义清理与工具脚本修复。各条目的具体修复方式见 [1_FixPlan.md](1_FixPlan.md)。

2026-08-26 修复完成记录：三个阶段共 32 项全部按修复方案实施并通过逐项 diff 复核，实施摘要见 1_FixPlan.md 各节的 [DONE] 行。实施中的两处方案修正：H4 重抛判定豁免 429（429 同为 NonRetriableException，多密钥轮询依赖重试循环）；H8 转义实体采用无分号形式（`&#44` 而非 `&#44;`，分号同为切分符，带分号实体会被切散）。未执行任何编译、构建与测试命令，验证以静态检查为主。
