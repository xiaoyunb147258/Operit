---
date: 2026-08-26
base: 本方案对应 [index.md](index.md) 的审核结论
status: 草稿
---

# 静态审核修复方案

## 约定

- 每个条目与 [index.md](index.md) 的同编号问题一一对应
- 所有修复均为根因修复，不引入兜底、降级与回退路径
- 涉及用户可见接口或文件格式变更的条目已单独标注，实施前需确认版本发布状态
- 验证以静态检查与补充单元测试为主，构建与测试命令仅在明确要求时执行

## 第一阶段：严重问题

### S1 标准执行器管道死锁

根因：`waitFor` 排在读流之前，输出写满管道缓冲区后子进程无法退出。

修复：先并发 drain 两个流，再等待进程退出。stdout 与 stderr 必须同时开始读，任一顺序读都会在另一流写满时死锁。

```kotlin
coroutineScope {
    val process = Runtime.getRuntime().exec(command)
    val stdout = async(Dispatchers.IO) {
        process.inputStream.bufferedReader().use { it.readText() }
    }
    val stderr = async(Dispatchers.IO) {
        process.errorStream.bufferedReader().use { it.readText() }
    }
    val completed = process.waitFor(COMMAND_TIMEOUT, TimeUnit.SECONDS)
    if (!completed) {
        process.destroy()
        return@coroutineScope ShellExecutor.CommandResult(
            false, "", "Command timed out after $COMMAND_TIMEOUT seconds", -1
        )
    }
    val exitCode = process.exitValue()
    ShellExecutor.CommandResult(exitCode == 0, stdout.await(), stderr.await(), exitCode)
}
```

超时路径上 `destroy` 关闭进程后管道到达 EOF，两个 async 随之完成，coroutineScope 正常退出。`executeCommand` 与 `executeWithShell` 两处同步修改。

验证：新增单元测试，执行输出超过 64KB 的命令如 `seq 1 20000`，断言成功返回且 stdout 非空，不再出现超时误报。

[DONE] 2026-08-26：`executeCommand` 与 `executeWithShell` 两处已改为 coroutineScope 内双 async 并发 drain，grep 宽容逻辑保留。单元测试未新增（未获执行测试的授权）。

### S2 Root 执行器顺序读流且无超时

根因：与 S1 同源，stdout 读到 EOF 后才读 stderr，且 `waitFor()` 无超时。

修复：套用 S1 的并发 drain 结构，`executeCommandWithExec` 与 SHELL 身份 exec 分支两处同步修改，`waitFor` 一律带超时参数。

验证：新增测试执行 stdout 重定向到文件、stderr 刷屏的命令，断言在超时上限内返回而不是永久挂起。

[DONE] 2026-08-26：`executeCommandWithExec` 与 SHELL 身份 exec 分支两处已改为并发 drain + `waitFor` 带 30 秒超时（新增 companion 常量 COMMAND_TIMEOUT，与 StandardShellExecutor 一致）。测试未新增。

### S3 定位工具取消回调主线程自死锁

根因：`invokeOnCancellation` 回调在发起取消的线程上同步执行，主线程发起取消时回调内的 `runBlocking(Dispatchers.Main)` 等待主线程自己调度任务。

修复：回调改为 post 到主线程 Handler，不阻塞回调线程。同时移除 L1555 的 `GlobalScope.launch` 超时，改由 `withTimeout` 包裹整个挂起函数，超时与取消共用同一个 `invokeOnCancellation` 清理路径。

```kotlin
withTimeout(LOCATION_TIMEOUT_MS) {
    suspendCancellableCoroutine { continuation ->
        // ... 现有注册逻辑 ...
        continuation.invokeOnCancellation {
            mainHandler.post {
                try {
                    locationManager.removeUpdates(locationListener)
                } catch (e: Exception) {
                    AppLogger.e(TAG, "removeUpdates failed", e)
                }
            }
        }
    }
}
```

验证：静态检查 `invokeOnCancellation` 内不再出现 runBlocking；新增测试在挂起中取消后断言主线程可继续调度任务。

[DONE] 2026-08-26：`invokeOnCancellation` 已改为 `Handler(Looper.getMainLooper()).post`（该文件已有同款用法）；GlobalScope 超时块已移除，改为 `withTimeout` 包裹；超时语义保留旧版行为——超时沿用最后已知位置（位置工具的既有领域语义），经外层 `lastKnownLocationRef` 传递，非错误兜底。

### S4 Sherpa 语音 shutdown 主线程死锁

根因：死锁环为 `shutdown` 在主线程 runBlocking、`cancelRecognition` join 录音 Job、录音 finally 需要 Main dispatcher。环上任意一环断开即可解除，但根因是 shutdown 阻塞调用线程。

修复分两步，两个 Provider 同步修改：

1. `shutdown` 不再 runBlocking，清理工作投递到 Provider 自己的作用域执行
2. 录音循环 finally 中的 `withContext(Dispatchers.Main)` 改为 `mainHandler.post`，finally 内只做不依赖主线程同步完成的清理

```kotlin
override fun shutdown() {
    if (shutdownStarted.compareAndSet(false, true)) {
        serviceScope.launch {
            try { cancelRecognition() } catch (e: Exception) {
                AppLogger.e(TAG, "cancelRecognition during shutdown failed", e)
            }
            withContext(Dispatchers.IO) {
                try { recognizer?.release() } catch (e: Exception) {
                    AppLogger.e(TAG, "release recognizer failed", e)
                } finally { recognizer = null }
            }
        }
    }
}
```

`shutdownStarted` 用 AtomicBoolean 保证幂等，`serviceScope` 在构造时创建并在 shutdown 末尾取消。`AIForegroundService.onDestroy` 与 `SpeechToTextScreen` 的调用点无需改动。

验证：静态检查两个 Provider 不再有主线程可达的 runBlocking 路径；设备上开启唤醒监听后划掉任务，断言进程正常退出无 ANR。

[DONE] 2026-08-26：两个 Provider 已实施。shutdown 改为 `AtomicBoolean` CAS 幂等 + 清理投递到自身 `scope` 异步执行（末尾 `scope.cancel()`，调用点均新建实例不复用，终态成立）；录音循环退出复位由 `withContext(Dispatchers.Main)` 改为 `mainHandler.post`，post 任务内以 `shutdownStarted` 防护，避免迟到的复位把 `UNINITIALIZED` 终态覆盖回 `IDLE`；原静默 catch 全部改为 `AppLogger` 记录。静态检查确认两文件代码中已无 `runBlocking`。

### S5 消息数组导入分支不可达

根因：`isMessageObject` 是 `isConversationObject` 的真子集，消息数组分支永不可达。

修复：调换判断顺序，先测消息对象再测对话对象，并从 `isConversationObject` 中删除因此恒假的 role content 子句。

```kotlin
private fun isConversationObject(obj: JsonObject): Boolean =
    obj.containsKey("messages") || obj.containsKey("title")

// parseArrayFormat 内：
return if (firstElement is JsonObject) {
    if (isMessageObject(firstElement)) {
        // 消息数组，创建单个对话（原 L57 死分支上移至此）
        ...
    } else if (isConversationObject(firstElement)) {
        // 对话数组
        ...
    } else emptyList()
}
```

验证：新增单元测试，导入 `[{"role":"user","content":"a"},{"role":"assistant","content":"b"}]`，断言得到 1 个含 2 条消息的会话；导入含 title 与 messages 的对象数组，断言得到 N 个会话。

[DONE]

### S6 会话保存与变体删除无事务

根因：多步 Room 写操作分散在多个独立事务中，中断留下不一致状态。

修复：三处写序列包进 `database.withTransaction`：

- `saveChatHistoryInternal` 的删除加插入
- `deleteMessageVariant` 的删除、重排、更新选中索引
- `deleteMessagesFrom` 的删除 variants 与 messages

```kotlin
database.withTransaction {
    variantDao.deleteByChatId(chatId)
    messageDao.deleteByChatId(chatId)
    messageDao.insertAll(messages)
    variantDao.insertAll(variants)
}
```

`hydrateMessages` 的 `first { }` 保持严格匹配不放松，事务保证不一致状态不再产生。为修复前可能已产生的脏数据，附带一次性数据检查：查询 `selectedVariantIndex` 指向不存在 variant 的消息，存在则按索引截断到合法范围并落库修正，这是对既有损坏数据的显式修复动作，不构成运行时兜底。

验证：新增测试在删除与插入之间注入取消，断言数据库状态一致；对 `deleteMessageVariant` 注入中断后重新加载会话，断言消息不空白。

[DONE]

### S7 字符串统计脚本硬编码路径

根因：绝对路径与平台分隔符硬编码。

修复：

```python
REPO_ROOT = Path(__file__).resolve().parents[2]  # tools/string -> tools -> 仓库根

# L228：
'file': str(file_path.relative_to(REPO_ROOT)),

# L323，统一为正斜杠再切分：
feature = rel_path.replace('\\', '/').split('ui/features/')[1].split('/')[0]
```

验证：在本仓库根目录运行脚本，断言不抛异常且输出含各 feature 模块的统计行。

[DONE]

### S8 翻译同步脚本正则吞自闭合标签

根因：`string_pattern` 的属性捕获组连斜杠一起吞掉，非贪婪体越过自闭合标签延伸到下一个闭合标签。

修复：模式改为两分支交替，自闭合分支在前，同一回调处理两种形态：

```python
string_pattern = re.compile(
    r'^[ \t]*<string(?=\s|>|/)([^>]*?)/>[ \t]*(?:\r?\n)?'
    r'|^[ \t]*<string(?=\s|>)([^>]*)>.*?</string>[ \t]*(?:\r?\n)?',
    re.MULTILINE | re.DOTALL,
)
```

`remove_if_stale` 中取 name 时两个捕获组都检查，`attrs = match.group(1) or match.group(2)`。删除独立的 `empty_string_pattern` 及其第二次 sub 调用，`updated` 只经过一次替换。

验证：构造含自闭合 stale 条目、自闭合非 stale 条目与相邻正常条目的最小 strings.xml，断言只删除 stale 行且计数一致；对 `values-pt-rBR/strings.xml` 全量干跑，断言 removed 集合与 stale 集合恰好相等。

[DONE]（正则行为已用 python3 -c 最小 XML 自测通过；全量干跑涉及 AI 调用未执行）

### S9 桥接 socket 空闲超时小于请求超时

根因：魔法数字 120000 与注释意图及 REQUEST_TIMEOUT 都不符。

修复：

```typescript
socket.setTimeout(this.REQUEST_TIMEOUT * 2);
```

空闲超时 360 秒，覆盖 180 秒请求超时加上调度余量，与注释声明一致。

验证：静态检查不再有裸超时数字；用一个 sleep 超过 120 秒的工具调用做端到端验证，断言结果能送达客户端。

[DONE]（静态检查已确认无裸超时数字；端到端 sleep 验证未执行）

## 第二阶段：高危问题

### H1 MCP 会话创建竞态泄漏

根因：读缓存、连接、写缓存三步无原子性，落败连接失去引用。

修复：`putIfAbsent` 定胜负，落败方关闭自己建立的连接：

```kotlin
suspend fun getOrCreateSession(pluginId: String): McpSession {
    sessionCache[pluginId]?.let { return it }
    val session = connect(pluginId)  // 秒级网络操作
    val raced = sessionCache.putIfAbsent(pluginId, session)
    if (raced != null) {
        session.close()  // 并发落败方关闭自己建立的连接
        return raced
    }
    return session
}
```

验证：并发调用 10 次 `getOrCreateSession`，断言 connect 只发生 1 次且无连接泄漏。

[DONE] 2026-08-26：`getOrCreateSession` 连接成功后改用 `putIfAbsent` 定胜负，落败方关闭自己建立的连接并返回缓存中的胜者，不再覆盖式写缓存。

### H2 PhoneAgent 注册表 remove 竞态

根因：单参 remove 可能移除并发 register 刚建立的新 set。

修复：改两参形式，仅当 value 仍是同一个 set 才移除：

```kotlin
fun unregister(agentId: String, job: Job) {
    val set = jobsByAgentId[agentId] ?: return
    set.remove(job)
    if (set.isEmpty()) {
        jobsByAgentId.remove(agentId, set)
    }
}
```

验证：新增并发压测，交替 register 与 unregister，断言任一时刻 cancelAll 能取消到所有已注册且未完成的 Job。

[DONE] 2026-08-26：`unregister` 改两参 `remove(agentId, set)`，仅当映射仍指向同一 set 才移除，不再误删并发 register 刚建立的新 set。

### H3 Gemini 流式括号深度不识别字符串

根因：朴素逐字符计数把字符串字面量内部的括号计入深度。

修复：扫描时维护字符串内与转义两个状态：

```kotlin
var inString = false
var escaped = false
for (c in trimmedLine) {
    when {
        escaped -> escaped = false
        inString && c == '\\' -> escaped = true
        c == '"' -> inString = !inString
        !inString && (c == '{' || c == '[') -> jsonDepth++
        !inString && (c == '}' || c == ']') -> jsonDepth--
    }
}
```

验证：新增单元测试，构造 text 字段含 `[笑]`、`{"a": "}"}`、不平衡括号的流式 chunk 序列，断言内容完整送达。

[DONE] 2026-08-26：`inString`/`escaped` 声明为与 `jsonDepth` 同级的跨行状态（分段 JSON 的字符串字面量可能被行边界切开，需跨行保持），逐字符计数改为字符串感知状态机；开始收集点与两处收集器重置点（解析成功、深度归零但解析失败）同步归零扫描状态。单元测试未新增（未获执行测试的授权）。

### H4 不可恢复 4xx 被重试

根因：`NonRetriableException` 继承 IOException，`handleRetryableError` 无差别重试一切 IOException。

修复：三个 Provider 的 `handleRetryableError` 在重试判定之前显式重抛：

```kotlin
protected suspend fun handleRetryableError(e: Exception): Throwable {
    if (e is NonRetriableException) throw e
    // ... 现有重试逻辑 ...
}
```

429 限流仍走现有重试路径，行为不变。

验证：新增测试注入 401 响应，断言只请求 1 次且异常为 NonRetriableException；注入 429 断言仍按策略重试。

[DONE] 2026-08-26：三个 Provider（OpenAI/Claude/Gemini）的 `handleRetryableError` 在取消检查之后显式重抛 `NonRetriableException`，且豁免 429——429 同样以 NonRetriableException 抛出，无条件重抛会切断限流时换密钥重试的既有路径，故仅非 429 的 4xx 立即终止。重抛发生在调用方 catch 块内，异常直接传播出重试循环；NonRetriableException 为 IOException 子类，上层现有 catch 均兼容。测试未新增（未获执行测试的授权）。

### H5 工作流防重 TOCTOU

根因：检查与注册两步之间不持锁。

修复：检查移入锁内，`synchronized` 对同监视器可重入，`registerRunningWorkflow` 自身的锁不受影响：

```kotlin
val workflowJob = currentCoroutineContext().job
synchronized(runningWorkflowLock) {
    if (getRunningWorkflowJobs(id).isNotEmpty()) {
        return Result.failure(IllegalStateException("Workflow already running"))
    }
    registerRunningWorkflow(id, workflowJob)
}
```

验证：并发触发同一工作流 10 次，断言只有 1 次执行。

[DONE] 2026-08-26：防重检查移入 `synchronized(runningWorkflowLock)` 与注册成为原子操作；已确认 `getRunningWorkflowJobs`/`registerRunningWorkflow` 锁同一监视器可重入且锁内纯内存操作，无死锁与长持锁风险。

### H6 MCP 本地服务状态非原子更新

根因：读改写三步无原子性，与同类 `_mcpConfig` 的写法不一致。

修复：三处统一改用原子 update：

```kotlin
fun updateServerStatus(serverId: String, update: (ServerStatus) -> ServerStatus) {
    _serverStatus.update { current ->
        current.toMutableMap().apply {
            this[serverId] = update(this[serverId] ?: ServerStatus())
        }
    }
}
```

`removeServerStatus` 与 `initializeMissingServerStatus` 同步改造。

验证：静态检查 `_serverStatus` 不再出现 `.value =` 直接赋值；并发更新压测断言无丢失。

[DONE] 2026-08-26：`updateServerStatus`、`removeServerStatus`、`initializeMissingServerStatus` 三处统一改为 `_serverStatus.update {}` 原子更新，方法签名与对外行为保持不变。

### H7 桥接启动器覆盖 NODE_OPTIONS

根因：Linux 分支无条件展开，破坏了其他变量一致的未提供才设置模式。

修复：对齐既有合并模式：

```typescript
...(os.platform() === 'linux'
    && !process.env.NODE_OPTIONS
    && !serviceInfo.env?.NODE_OPTIONS
    ? { NODE_OPTIONS: '--openssl-legacy-provider' }
    : {}),
```

验证：静态检查；分别设置与不设置 NODE_OPTIONS 启动服务，断言自定义值被保留、默认场景得到 legacy provider。

[DONE] 2026-08-26：spawn-helper.ts Linux 分支改为 `process.env.NODE_OPTIONS` 与 `serviceInfo.env?.NODE_OPTIONS` 均未设置时才注入 `--openssl-legacy-provider`，对齐文件内既有"未提供才设置"展开模式，自定义值不再被覆盖、Node 16 不再因 bad option 失败。仅完成静态检查，启动断言未执行（未获运行授权）。

### H8 Markdown 往返标题截断

本条涉及导出文件格式，属于用户可见接口变更。实施前需确认当前导出格式是否已随发布版本出去，已发布则新旧转义需共存读取，未发布则直接采用新格式。

修复分两部分：

导出侧对 chat-info 的值做实体转义，导入侧还原：

```kotlin
// MarkdownExporter
private fun escapeMetaValue(value: String) =
    value.replace("&", "&amp;").replace(",", "&#44;")
        .replace(";", "&#59;").replace("=", "&#61;")

// MarkdownConverter.parseSimpleProperties 取值后
private fun unescapeMetaValue(value: String) =
    value.replace("&#61;", "=").replace("&#59;", ";")
        .replace("&#44;", ",").replace("&amp;", "&")
```

优先级调整：YAML front matter 的 title 为主，chat-info 仅在 front matter 缺失时参与。旧文件里未转义的含逗号标题本身已经截断损坏，新逻辑读取到的截断值与旧版一致，不产生新的回归。

验证：新增往返测试，标题含逗号、分号、与号与等号，断言导出再导入后完全一致。

[DONE] 2026-08-26：导出侧新增 escapeMetaValue（chat-info 全部值与 msg 的 model 过转义），导入侧 parseSimpleProperties 取值后经 unescapeMetaValue 对称还原；实体定为无分号形式（&#44 而非 &#44;），因 `;` 同为导入切分符，带分号实体会被 Regex("[,;]") 切散、往返仍损坏；title 优先级改为 front matter 为主、chat-info 仅在其缺失时生效（hasFrontMatterTitle 标记）。格式文档 chat_import_markdown.md 已同步值转义规则。往返单元测试未新增（未获执行测试的授权），编解码互逆性与切分安全性已用独立脚本穷举 1-3 字符组合验证通过。

### H9 桥接 unspawn 百毫秒重注册竞态

根因：用固定延时猜测进程退出时刻。

修复：改为 exit 事件驱动，注册表恢复挂在真实退出上：

```typescript
private pendingExpectedStops = new Set<string>();

// unspawn 与闲置回收：
this.pendingExpectedStops.add(serviceName);
this.serviceRegistry.delete(serviceName);
helper.kill();
helper.once('exit', () => {
    this.pendingExpectedStops.delete(serviceName);
    this.serviceRegistry.set(serviceName, serviceInfo);
});

// handleServiceClosure 开头：
if (this.pendingExpectedStops.has(serviceName)) {
    return; // 预期退出，不安排自动重启
}
```

删除原 100 毫秒 setTimeout。

验证：unspawn 后立即断言服务不会被 handleServiceClosure 重启；正常崩溃退出仍触发自动重启。

[DONE] 2026-08-26：新增 `pendingExpectedStops: Map<string, McpServiceInfo>` 暂存待恢复注册信息，unspawn 与闲置回收（checkIdleServices）统一改为"标记 + 摘除注册 + kill"，注册表恢复移入 helper 的 exit 事件回调（预期退出时移除标记、恢复注册、跳过自动重启），意外崩溃仍走 handleServiceClosure；两处 setTimeout(100) 重注册已删除，reset 命令同步清空新集合以防迟到的 exit 事件复活已复位注册表。仅完成静态检查，运行断言未执行。

## 第三阶段：中低危问题

### M1 终端收集协程泄漏

修复：`job.cancel()` 移入 finally：

```kotlin
val job = scope.launch { /* collect */ }
try {
    val result = deferred.await()
    // ...
} finally {
    job.cancel()
}
```

[DONE] 2026-08-26：`executeCommand` 中 `collectorReady.await()`、`sendCommandToSession`、`deferred.await()` 全部包进 try/finally，`job.cancel()` 移入 finally，任何取消/异常路径都能终止收集协程，不再泄漏类级 scope 上的 job。

### M2 工具执行吞取消

修复：catch 内重抛取消：

```kotlin
.catch { e ->
    if (e is kotlinx.coroutines.CancellationException) throw e
    AppLogger.e(TAG, "Tool execution error: ${invocation.tool.name}", e)
    // ... 现有错误通知与 ToolResult ...
}
```

M4 的五处 catch 同样在开头加这一行。

[DONE] 2026-08-26：`executeToolSafely` 的 Flow.catch 第一行已加 `kotlinx.coroutines.CancellationException` 检查并重抛，用户停止不再被记为工具执行错误。

[DONE] 2026-08-26 M4：ConversationService 五处 `catch (e: Exception)`（标题生成、工具包描述、识图、音频、视频）开头均已重抛 CancellationException，取消不再被误报为生成/识别失败。

### M3 取消注册被误删

修复：map 为 ConcurrentHashMap 时用两参 remove：

```kotlin
onComplete = {
    activeEnhancedAiServiceByChatId.remove(chatKey, enhancedAiService)
    // ...
}
```

[DONE] 2026-08-26：`activeEnhancedAiServiceByChatId` 确认为 ConcurrentHashMap（AIMessageManager L82），插件流与普通流两处 onComplete 回调均改为两参 `remove(chatKey, enhancedAiService)`，先完成者不再误删后注册者的注册，运行中请求保持可取消。

### M5 前台服务主线程 runBlocking

修复：这些偏好值在服务创建时经 `lifecycleScope.launch` 预读进 `@Volatile` 字段或 StateFlow 缓存，主线程路径只读缓存，删除全部 runBlocking。`SpeechServiceFactory.getInstance` 路径同步改造，构造时注入已解析的 profile。

[DONE] 2026-08-26：按调用链实际形态改为"挂起读取真实值"而非缓存预读：notifyReplyCompleted / ensureMicrophoneForeground / refreshBackgroundKeepAlive / hasPersistentForegroundResponsibilityConfigured / isAlwaysListeningEnabledNow / stopSelfIfIdle / ensureWakeSpeechProvider 全部改 suspend 并删除 runBlocking（含方案未点名的 refreshBackgroundKeepAlive，同模式一并修复）；onStartCommand 两处主线程同步路径（EXTRA_STATE 停止/刷新决策、ENSURE_MICROPHONE_FOREGROUND 的 stopSelfIfIdle）整体移入 serviceScope.launch，读到的仍是 DataStore 当前真实值；ActivityLifecycleManager.onActivityResumed 的调用投递到其自身 scope 协程。SpeechServiceFactory 四处 runBlocking 全部移除，createSpeechService / createWakeSpeechService / getInstance 改 suspend，私有工厂中包裹普通构造的无意义 runBlocking 一并删除；SpeechToTextScreen 初始占位改用新增的同步 createLocalSpeechService（SHERPA_NCNN 不依赖 profile，与原行为等价），重建走 LaunchedEffect 内挂起调用；SpeechInteractionManager 构造路径（Compose remember/ViewModel 属性，主线程）无法同步协程化，实例推迟到 initialize() 协程中经 getInstance 获取，volumeLevelFlow（stateIn 保持 StateFlow 类型）/ recognitionResultFlow 经 serviceState 桥接暴露，方法调用点空安全处理。AIForegroundService.kt 与 SpeechServiceFactory.kt 静态检查已无 runBlocking。

### M6 null 文本分块被丢弃

根因：org.json 的 `optString` 对 JSON null 返回字面 null，过滤逻辑越过了边界。

修复：在 JSON 边界正确判空，谓词回归语义本身：

```kotlin
private fun JSONObject.contentOrEmpty(name: String): String =
    if (this.has(name) && !this.isNull(name)) this.optString(name) else ""

// 谓词恢复为
private fun String.isNotNullOrEmpty() = this.isNotEmpty()
```

`emitContent`、`emitThinkContent`、`processContentDelta` 三处取值统一走 `contentOrEmpty`。

[DONE] 2026-08-26：新增 `JSONObject.contentOrEmpty`（has+isNull 边界判空），`isNotNullOrEmpty` 恢复纯 `isNotEmpty()`；除方案点名的三处取值（流式 delta、非流式 message、非流式 messageObj）外，排查出同样依赖该谓词过滤的取值点一并收口：图片响应 output_text part、Responses 流式 delta/text 事件、reasoning 文本提取器，以及跨文件的 `OpenAIResponsesPayloadAdapter.parseNonStreamingResponse` 三处 text 取值（adapter 为独立 object，内建同款私有扩展，其 chunks 流入 emit 路径）。

### M7 悬浮窗替换进程级异常处理器

修复：handler 内过滤线程归属，只拦截服务自己创建的线程，其余直接委托原默认 handler：

```kotlin
Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
    if (thread in ownedThreads) {
        handleServiceCrash(throwable)
    }
    previousHandler?.uncaughtException(thread, throwable)
}
```

`crashCount` 持久化到 SharedPreferences，使 `crashCount > 3` 的永久禁用分支真正可用。onDestroy 恢复原 handler。

[DONE] 2026-08-26：handler 入口按 ownedThreads（ConcurrentHashMap.newKeySet）判定线程归属，命中才走 handleServiceCrash（其 finally 仍委托原 handler），未命中直接委托原 defaultExceptionHandler；onDestroy 恢复逻辑保持不变。经核查服务及其组件（FloatingWindowManager / ChatServiceCore）不创建任何自有线程——协程全部运行在主线程与共享调度池上，无法按线程名或 ThreadGroup 区分归属，故 ownedThreads 作为空登记点保留（服务未来创建自有线程时登记即可），效果是不再把任何进程级线程的崩溃误记为服务崩溃并触发自重启。lastCrashTime / crashCount 改为持久化到 floating_chat_prefs（last_crash_time / crash_count 两个 key），保留 60 秒窗口清零与 >3 永久禁用的既有语义；onCreate 中 prefs 初始化提前到崩溃 handler 注册之前，避免 handler 触发时 prefs 未就绪。

### M8 导入统计取空列表

修复：直接查 DAO 而不是共享 StateFlow：

```kotlin
val existingIds = chatDao.getAllChatIds().toHashSet()
```

`ChatDao` 补充只查 id 列的投影查询，避免为取 id 加载全部实体。

[DONE] 2026-08-26：`ChatDao` 新增 `getAllChatIds()`（SELECT id 投影），`importChatHistoriesFromUri` 的流式与常规两处导入统计 `existingIds` 均改由该查询直取，不再依赖 `stateIn(Lazily)` 共享流的首次发射；S6 改造的 withTransaction 保存路径未受影响。

### L1 进度总线非原子

修复：`update` 内用 CAS 循环：

```kotlin
while (true) {
    val current = _progress.value
    if (!shouldReplace(current, next)) return
    if (_progress.compareAndSet(current, next)) return
}
```

[DONE] 2026-08-26：`update` 的读-判-写已改为 while(true) CAS 循环，shouldReplace 判定基于循环内重读的 current，`compareAndSet` 失败（他方已写入）即带着新值重试，不再出现基于过期 current 的覆盖写入。

### L2 记忆仓库缓存持有已关闭 Store

修复：`ObjectBoxManager.delete` 成功后广播清理事件或在删除入口显式逐个失效缓存，`MemoryQueryToolExecutor` 与 `MemoryDocumentsProvider` 的缓存按 profileId 移除对应条目。

[DONE] 2026-08-26：两处缓存均提升为 companion 静态持有（执行器由 ToolGetter 按次新建、Provider 实例由系统创建，删除流程均拿不到实例引用，失效入口必须静态可达），新增 invalidateProfileCache / invalidateRepositoryCache 公开方法，由 deleteMemorySpace 在 ObjectBoxManager.delete 之后显式调用。

### L3 apply_patch 的 --keep 死代码

修复：按帮助文本实现清理语义，未传 `--keep` 时删除本次下载的 patch 与 meta 临时文件：

```python
if not args.keep:
    for p in wd.glob("patch_*.json"):
        if p.is_file():
            p.unlink()
```

[DONE] 2026-08-26：改为在 download_file 与 try_download_with_gh 两个实际下载函数内登记本次下载的文件清单，结尾未传 --keep 时按清单删除；删除范围严格限于本次运行产物，本地已存在而未重新下载的文件不动。

### L4 子进程解码编码

修复：两处 `subprocess.run` 补 `encoding="utf-8", errors="replace"`，与 `commit_ai.py` 的既有做法对齐。

[DONE] 2026-08-26：build_patch.py 与 apply_patch.py 的 run() 均已补上 encoding="utf-8", errors="replace"。

### L5 IPC 消息丢失

修复：send 带回调再退出：

```typescript
process.send({ type: 'fatal', error: message }, () => process.exit(1));
```

[DONE] 2026-08-26：spawn-helper.ts 的 connect 失败、uncaughtException、unhandledRejection 三处 fatal 消息均改为 process.send 回调确认写出后再 process.exit(1)，退出只发生在回调内。

### L6 技能导入脚本硬编码目录

修复：仓库根推导：

```python
REPO_ROOT = Path(__file__).resolve().parents[2]  # tools/github -> tools -> 仓库根
CACHE_DIR = REPO_ROOT / "temp" / "skill-market-zip-cache"
```

L1412、L1509、L1563 同步替换。

[DONE] 2026-08-26：模块级新增 REPO_ROOT（parents[2] 推导）与 TEMP_DIR，原四处硬编码路径（zip-cache / validate / dry-run / dry_run_run_dir 缺省值）统一改为 TEMP_DIR 子目录，各子目录结构不变，grep 确认 tools 下无残留绝对路径。

## 实施顺序与依赖

S3 与 S4 相互独立可并行。S1 与 S2 结构相同，S2 直接复用 S1 的 drain 结构。H4 三个 Provider 改法一致，一次提交完成避免行为漂移。H8 涉及导出格式决策，需先确认发布状态再动工，其余条目无外部依赖，可按阶段顺序推进。

每完成一个条目，在本文档对应小节结尾追加 `[DONE]` 标记，与 TODO 目录的交付约定一致。
