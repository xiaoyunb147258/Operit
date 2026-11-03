package com.ai.assistance.operit.ui.features.termux

import android.annotation.SuppressLint
import android.content.Context
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * B 模式：Termux 终端界面
 *
 * 使用内嵌的 Termux 内核（terminal-emulator / terminal-view），
 * 提供真正的 pkg / apt 用户空间，而不是 proot 容器。
 */
@SuppressLint("ClickableViewAccessibility")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermuxTerminalScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var session by remember { mutableStateOf<TerminalSession?>(null) }
    var termView by remember { mutableStateOf<TerminalView?>(null) }
    var statusText by remember { mutableStateOf("初始化中…") }
    var extraKeysVisible by remember { mutableStateOf(true) }

    // 初始化 Termux 环境 + 创建首个会话
    LaunchedEffect(Unit) {
        statusText = "准备 Termux 环境…"
        session = withContext(Dispatchers.IO) { TermuxRuntime.createSession(context) }
        statusText = if (session != null) "" else "Termux 环境不可用"
    }

    Column(modifier = modifier.fillMaxSize().background(Color(0xFF1E1E1E))) {
        // 顶部状态条
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (statusText.isEmpty()) "Termux" else statusText,
                color = Color(0xFFB0B0B0),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { extraKeysVisible = !extraKeysVisible }) {
                Icon(
                    if (extraKeysVisible) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                    contentDescription = "切换扩展键"
                )
            }
            IconButton(onClick = {
                session?.let { s ->
                    termView?.let { v ->
                        v.post {
                            v.requestFocus()
                        }
                    }
                }
            }) {
                Icon(Icons.Default.Add, contentDescription = "聚焦")
            }
        }

        // 终端视图
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { ctx ->
                TerminalView(ctx, null).apply {
                    setTerminalViewClient(TermuxViewClient(ctx, this))
                    termView = this
                }
            },
            update = { view ->
                val s = session
                if (s != null && view.currentSession != s) {
                    view.attachSession(s)
                    view.requestFocus()
                }
            }
        )

        // 扩展键栏：Ctrl / Alt / Esc / Tab / 方向键
        if (extraKeysVisible) {
            TermuxExtraKeysRow(
                onKey = { key, ctrl, alt ->
                    sendKey(session, key, ctrl, alt)
                }
            )
        }
    }
}

/** 扩展键栏 */
@Composable
private fun TermuxExtraKeysRow(onKey: (String, Boolean, Boolean) -> Unit) {
    var ctrl by remember { mutableStateOf(false) }
    var alt by remember { mutableStateOf(false) }
    val keys = listOf("ESC", "TAB", "CTRL", "ALT", "↑", "↓", "←", "→", "/", "-", "|")

    ScrollableTabRowLike {
        keys.forEach { k ->
            val active = (k == "CTRL" && ctrl) || (k == "ALT" && alt)
            TextButton(
                onClick = {
                    when (k) {
                        "CTRL" -> ctrl = !ctrl
                        "ALT" -> alt = !alt
                        "↑" -> onKey("\u001b[A", ctrl, alt)
                        "↓" -> onKey("\u001b[B", ctrl, alt)
                        "→" -> onKey("\u001b[C", ctrl, alt)
                        "←" -> onKey("\u001b[D", ctrl, alt)
                        "ESC" -> onKey("\u001b", ctrl, alt)
                        "TAB" -> onKey("\t", ctrl, alt)
                        else -> onKey(k, ctrl, alt)
                    }
                },
                colors = if (active) ButtonDefaults.textButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ) else ButtonDefaults.textButtonColors(),
                modifier = Modifier.height(36.dp)
            ) {
                Text(k, color = Color(0xFFE0E0E0), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun ScrollableTabRowLike(content: @Composable () -> Unit) {
    val scrollState = androidx.compose.foundation.rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF252525))
            .horizontalScroll(scrollState)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        content()
    }
}

/** 发送按键到会话 */
private fun sendKey(session: TerminalSession?, key: String, ctrl: Boolean, alt: Boolean) {
    if (session == null) return
    var text = key
    if (ctrl && text.length == 1) {
        val c = text[0].uppercaseChar()
        if (c in 'A'..'Z') text = (c.code - 64).toChar().toString()
    }
    if (alt) text = "\u001b$text"
    session.write(text)
}

/** TerminalViewClient 实现 */
private class TermuxViewClient(
    private val context: Context,
    private val view: TerminalView
) : TerminalViewClient {

    override fun onScale(scale: Float): Float = scale

    override fun onSingleTapUp(e: MotionEvent) {
        view.requestFocus()
    }

    override fun shouldBackButtonBeMappedToEscape(): Boolean = false

    override fun shouldEnforceCharBasedInput(): Boolean = true

    override fun shouldUseCtrlSpaceWorkaround(): Boolean = false

    override fun isTerminalViewSelected(): Boolean = true

    override fun copyModeChanged(copyMode: Boolean) {}

    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean {
        if (keyCode == KeyEvent.KEYCODE_ENTER && !e.isShiftPressed) {
            session.write("\r")
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_DEL && e.isCtrlPressed) {
            session.write("\u0017")
            return true
        }
        return false
    }

    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = false

    override fun onLongPress(event: MotionEvent): Boolean = false

    override fun readControlKey(): Boolean = false

    override fun readAltKey(): Boolean = false

    override fun readShiftKey(): Boolean = false

    override fun readFnKey(): Boolean = false

    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean = false

    override fun onEmulatorSet() {}

    override fun logError(tag: String?, message: String?) {}

    override fun logWarn(tag: String?, message: String?) {}

    override fun logInfo(tag: String?, message: String?) {}

    override fun logDebug(tag: String?, message: String?) {}

    override fun logVerbose(tag: String?, message: String?) {}

    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {}

    override fun logStackTrace(tag: String?, e: Exception?) {}
}

/** TerminalSessionClient 实现 */
internal class TermuxSessionClient : TerminalSessionClient {

    override fun onTextChanged(changedSession: TerminalSession) {}

    override fun onTitleChanged(changedSession: TerminalSession) {}

    override fun onSessionFinished(finishedSession: TerminalSession) {}

    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {}

    override fun onPasteTextFromClipboard(session: TerminalSession?) {}

    override fun onBell(session: TerminalSession) {}

    override fun onColorsChanged(session: TerminalSession) {}

    override fun onTerminalCursorStateChange(state: Boolean) {}

    override fun setTerminalShellPid(session: TerminalSession, pid: Int) {}

    override fun getTerminalCursorStyle(): Int? = null

    override fun logError(tag: String?, message: String?) {}

    override fun logWarn(tag: String?, message: String?) {}

    override fun logInfo(tag: String?, message: String?) {}

    override fun logDebug(tag: String?, message: String?) {}

    override fun logVerbose(tag: String?, message: String?) {}

    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {}

    override fun logStackTrace(tag: String?, e: Exception?) {}
}
