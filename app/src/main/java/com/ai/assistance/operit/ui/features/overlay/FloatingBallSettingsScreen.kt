package com.ai.assistance.operit.ui.features.overlay

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ai.assistance.operit.core.overlay.FloatingBallConfig
import com.ai.assistance.operit.core.overlay.FloatingBallService
import com.ai.assistance.operit.core.overlay.OverlayPermissionHelper

/**
 * 悬浮球保护设置页
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloatingBallSettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var enabled by remember { mutableStateOf(FloatingBallConfig.isEnabled(context)) }
    var sizeDp by remember { mutableStateOf(FloatingBallConfig.getSizeDp(context)) }
    var keepAlive by remember { mutableStateOf(FloatingBallConfig.isKeepAlive(context)) }
    var hasPermission by remember { mutableStateOf(OverlayPermissionHelper.canDrawOverlays(context)) }

    // 从系统设置返回时重新检查权限
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = OverlayPermissionHelper.canDrawOverlays(context)
                if (hasPermission && enabled) {
                    FloatingBallService.start(context)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Adjust, null, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text("悬浮球保护", fontSize = 18.sp)
                Text(
                    "桌面悬浮图标，随时拖动缩放，用于后台保活",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // 权限提示
        if (!hasPermission) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("需要悬浮窗权限", fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "请先在系统设置中允许本应用显示在其他应用上层",
                        fontSize = 11.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { OverlayPermissionHelper.requestOverlayPermission(context) }) {
                        Text("去授权")
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // 开关
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("开启悬浮球", fontSize = 14.sp)
                    Text(
                        "关闭后悬浮球消失，保活服务停止",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { on ->
                        if (on && !hasPermission) {
                            OverlayPermissionHelper.requestOverlayPermission(context)
                            enabled = true
                            FloatingBallConfig.setEnabled(context, true)
                        } else {
                            enabled = on
                            FloatingBallConfig.setEnabled(context, on)
                            if (on) FloatingBallService.start(context)
                            else FloatingBallService.stop(context)
                        }
                    }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // 尺寸
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text("图标大小：${sizeDp.toInt()} dp", fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                Slider(
                    value = sizeDp,
                    onValueChange = { sizeDp = it },
                    onValueChangeFinished = {
                        FloatingBallConfig.saveSizeDp(context, sizeDp)
                        if (enabled) {
                            FloatingBallService.stop(context)
                            FloatingBallService.start(context)
                        }
                    },
                    valueRange = FloatingBallConfig.MIN_SIZE_DP..FloatingBallConfig.MAX_SIZE_DP
                )
                Text(
                    "也可以直接在桌面上双指缩放悬浮球",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // 保活
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("后台保活", fontSize = 14.sp)
                    Text(
                        "持有前台服务，降低被系统回收概率",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = keepAlive,
                    onCheckedChange = {
                        keepAlive = it
                        FloatingBallConfig.setKeepAlive(context, it)
                    }
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Text(
            "提示：拖动时无需长按，手指按住图标即可移动；松手后自动贴边。",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
