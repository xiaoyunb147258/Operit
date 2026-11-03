package com.ai.assistance.operit.ui.features.logs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.core.logging.InMemoryLogBuffer
import kotlinx.coroutines.delay

/**
 * 运行日志查看器
 *
 * - 默认只显示 WARN / ERROR（低开销）
 * - 可切换到“全部日志”
 * - 日志只存内存，不写本地
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuntimeLogScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val entries by InMemoryLogBuffer.entries.collectAsState()
    var captureAll by remember { mutableStateOf(InMemoryLogBuffer.captureAll) }
    var filterText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // 周期性刷新，保证高频日志也能实时显示
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            InMemoryLogBuffer.flushNow()
        }
    }

    val filtered = remember(entries, filterText) {
        if (filterText.isBlank()) entries
        else entries.filter {
            it.tag.contains(filterText, true) || it.message.contains(filterText, true)
        }
    }

    // 自动滚到底部
    LaunchedEffect(filtered.size) {
        if (filtered.isNotEmpty()) {
            listState.animateScrollToItem(filtered.size - 1)
        }
    }

    Column(modifier = modifier.fillMaxSize().background(Color(0xFF141414))) {
        // 顶部控制栏
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "运行日志",
                    color = Color(0xFFE8E8E8),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "${filtered.size} 条 · 仅内存 · 不落盘",
                    color = Color(0xFF808080),
                    fontSize = 11.sp
                )
            }

            IconButton(onClick = {
                copyToClipboard(context, InMemoryLogBuffer.exportText())
            }) {
                Icon(Icons.Default.Share, contentDescription = "复制全部", tint = Color(0xFFB0B0B0))
            }
            IconButton(onClick = { InMemoryLogBuffer.clear() }) {
                Icon(Icons.Default.Delete, contentDescription = "清空", tint = Color(0xFFB0B0B0))
            }
        }

        // 全部日志开关
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Switch(
                checked = captureAll,
                onCheckedChange = {
                    captureAll = it
                    InMemoryLogBuffer.setCaptureAll(it)
                }
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    if (captureAll) "全部日志（含 V/D/I）" else "仅错误日志（W/E）",
                    color = Color(0xFFD0D0D0),
                    fontSize = 13.sp
                )
                Text(
                    if (captureAll) "注意：开销更高，内存占用更大" else "默认模式，开销极低",
                    color = if (captureAll) Color(0xFFFFA726) else Color(0xFF6A9955),
                    fontSize = 10.sp
                )
            }
        }

        // 搜索框
        OutlinedTextField(
            value = filterText,
            onValueChange = { filterText = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            placeholder = { Text("筛选 tag 或内容", fontSize = 12.sp) },
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
        )

        HorizontalDivider(color = Color(0xFF2A2A2A))

        // 日志列表
        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (captureAll) "暂无日志" else "暂无错误日志（运行正常）",
                    color = Color(0xFF606060),
                    fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp)
            ) {
                items(filtered) { entry ->
                    LogEntryRow(entry)
                }
            }
        }
    }
}

@Composable
private fun LogEntryRow(entry: InMemoryLogBuffer.LogEntry) {
    val levelColor = when (entry.levelName()) {
        "E", "A" -> Color(0xFFFF6B6B)
        "W" -> Color(0xFFFFD93D)
        "I" -> Color(0xFF6BCB77)
        "D", "V" -> Color(0xFF808080)
        else -> Color(0xFFAAAAAA)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(Color(0xFF1C1C1C), RoundedCornerShape(4.dp))
            .padding(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                entry.levelName(),
                color = levelColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.width(6.dp))
            Text(
                entry.formatTime(),
                color = Color(0xFF707070),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.width(6.dp))
            Text(
                entry.tag,
                color = Color(0xFF7FB3D5),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            entry.message,
            color = Color(0xFFD8D8D8),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        entry.throwable?.let {
            Spacer(Modifier.height(3.dp))
            Text(
                it,
                color = Color(0xFF9A6A6A),
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Operit Logs", text))
}
