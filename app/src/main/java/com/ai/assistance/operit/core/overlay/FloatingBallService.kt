package com.ai.assistance.operit.core.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.ai.assistance.operit.R
import com.ai.assistance.operit.util.AppLogger

/**
 * 悬浮球前台服务。
 *
 * 作用：
 * 1. 持有 FOREGROUND_SERVICE，把进程优先级拉到前台级，降低被系统回收概率
 * 2. 管理悬浮球 View 的创建与销毁
 */
class FloatingBallService : Service() {

    companion object {
        private const val TAG = "FloatingBallService"
        private const val CHANNEL_ID = "floating_ball_channel"
        private const val NOTIFICATION_ID = 8801

        fun start(context: Context) {
            val intent = Intent(context, FloatingBallService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingBallService::class.java))
        }
    }

    private var windowManager: WindowManager? = null
    private var ballView: FloatingBallView? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        try {
            createChannel()
            startForeground(NOTIFICATION_ID, buildNotification())
            if (OverlayPermissionHelper.canDrawOverlays(this)) {
                showBall()
            } else {
                AppLogger.w(TAG, "overlay permission missing, floating ball not shown")
            }
        } catch (t: Throwable) {
            AppLogger.e(TAG, "onCreate failed", t)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (ballView == null && OverlayPermissionHelper.canDrawOverlays(this)) {
            showBall()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        hideBall()
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "悬浮球保护",
                    NotificationManager.IMPORTANCE_MIN
                ).apply {
                    description = "保持应用在后台运行"
                    setShowBadge(false)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    private fun buildNotification(): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pi = if (launchIntent != null) {
            PendingIntent.getActivity(
                this, 0, launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else null

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("悬浮球保护运行中")
            .setContentText("点击返回应用")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .apply { pi?.let { setContentIntent(it) } }
            .build()
    }

    private fun showBall() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val sizeDp = FloatingBallConfig.getSizeDp(this)
        val sizePx = (sizeDp * resources.displayMetrics.density).toInt()

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = FloatingBallConfig.getX(this@FloatingBallService)
            y = FloatingBallConfig.getY(this@FloatingBallService)
        }
        layoutParams = params

        val icon = runCatching { packageManager.getApplicationIcon(packageName) }.getOrNull()

        val view = FloatingBallView(
            context = this,
            windowManager = wm,
            params = params,
            icon = icon,
            sizePx = sizePx,
            onTap = {
                runCatching {
                    packageManager.getLaunchIntentForPackage(packageName)?.let {
                        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(it)
                    }
                }
            },
            onSizeChanged = { px ->
                FloatingBallConfig.saveSizeDp(this, px / resources.displayMetrics.density)
            },
            onPositionChanged = { x, y ->
                FloatingBallConfig.savePosition(this, x, y)
            }
        )

        ballView = view
        runCatching { wm.addView(view, params) }
            .onFailure { AppLogger.e(TAG, "addView failed", it) }
    }

    private fun hideBall() {
        ballView?.let { view ->
            runCatching { windowManager?.removeView(view) }
        }
        ballView = null
        layoutParams = null
    }
}
