package com.ai.assistance.operit.core.overlay

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/** 悬浮窗权限检查与跳转 */
object OverlayPermissionHelper {

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun requestOverlayPermission(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:" + context.packageName)
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }
}
