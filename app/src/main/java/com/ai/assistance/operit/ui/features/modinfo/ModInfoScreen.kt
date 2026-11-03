package com.ai.assistance.operit.ui.features.modinfo

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 关于本改版（NeonEdgeX Mod）
 *
 * 独立页面，不改动官方「关于」页面。
 * 联系方式置顶展示，醒目但不遮挡操作。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModInfoScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var copiedLabel by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // ---- 顶部标识 ----
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFF6C63FF), Color(0xFF00D4FF))
                    ),
                    RoundedCornerShape(16.dp)
                )
                .padding(20.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Favorite,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "NeonEdgeX 改版",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Operit 终端增强版 · A/B 双终端 · 智能体团队",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 12.sp
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // ---- 联系方式（置顶醒目） ----
        SectionTitle("联系方式")
        Spacer(Modifier.height(10.dp))

        ContactCard(
            title = "QQ 群",
            value = "1107158432",
            accent = Color(0xFF12B7F5),
            icon = { Icon(Icons.Default.Groups, null, tint = Color(0xFF12B7F5), modifier = Modifier.size(22.dp)) },
            onClick = {
                copyToClipboard(context, "1107158432")
                copiedLabel = "QQ 群号"
            },
            onOpen = {
                try {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("mqqopensdkapi://bizAgent/qm/qr?url=http%3A%2F%2Fqm.qq.com%2Fcgi-bin%2Fqm%2Fqr%3Ffrom%3Dapp%26p%3Dandroid%26k%3D1107158432"))
                    )
                } catch (_: Exception) {
                    copyToClipboard(context, "1107158432")
                    copiedLabel = "QQ 群号"
                }
            }
        )

        Spacer(Modifier.height(10.dp))

        ContactCard(
            title = "纸飞机频道",
            value = "@NeonEdgeX",
            accent = Color(0xFF29A9EB),
            icon = { Icon(Icons.Default.Send, null, tint = Color(0xFF29A9EB), modifier = Modifier.size(22.dp)) },
            onClick = {
                copyToClipboard(context, "@NeonEdgeX")
                copiedLabel = "纸飞机频道"
            },
            onOpen = {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/NeonEdgeX")))
                } catch (_: Exception) {
                    copyToClipboard(context, "@NeonEdgeX")
                    copiedLabel = "纸飞机频道"
                }
            }
        )

        if (copiedLabel != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                "已复制 $copiedLabel",
                color = Color(0xFF6BCB77),
                fontSize = 11.sp
            )
        }

        Spacer(Modifier.height(24.dp))

        // ---- 本版特性 ----
        SectionTitle("本次改版特性")
        Spacer(Modifier.height(10.dp))

        val features = listOf(
            "A/B 双终端" to "原生 Ubuntu 与 Termux 终端，右上角一键切换，各用各的数据目录",
            "智能体团队" to "多个 AI 成员在同一群聊协作，@成员 定向、@全体 广播",
            "悬浮球保护" to "桌面悬浮图标，随时拖动缩放，点击回 App，用于后台保活",
            "运行日志" to "默认只收错误日志，可切全部日志；只在内存，不写磁盘",
            "共存安装" to "包名独立（.clone），可与官方版同时装在手机上互不影响"
        )

        features.forEach { (title, desc) ->
            FeatureRow(title, desc)
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(24.dp))

        // ---- 版本信息 ----
        SectionTitle("版本信息")
        Spacer(Modifier.height(10.dp))
        InfoRow("基于", "Operit AI")
        InfoRow("改版", "NeonEdgeX")
        InfoRow("包名", context.packageName)

        Spacer(Modifier.height(32.dp))

        Text(
            "本改版为第三方修改版本，与官方无隶属关系。",
            color = Color(0xFF707070),
            fontSize = 10.sp
        )

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.primary,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun ContactCard(
    title: String,
    value: String,
    accent: Color,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    onOpen: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        onClick = onOpen
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon()
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                Spacer(Modifier.height(2.dp))
                Text(
                    value,
                    color = accent,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
            IconButton(onClick = onClick) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "复制",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun FeatureRow(title: String, desc: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(5.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp))
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(desc, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.width(64.dp))
        Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("NeonEdgeX", text))
}
