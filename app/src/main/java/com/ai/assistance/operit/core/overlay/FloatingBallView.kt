package com.ai.assistance.operit.core.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.WindowManager
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 悬浮球视图：只有一个 App 图标。
 *
 * - 任意时刻可直接拖动（无需长按）
 * - 双指缩放改变尺寸
 * - 单击触发 onTap
 */
@SuppressLint("ViewConstructor")
class FloatingBallView(
    context: Context,
    private val windowManager: WindowManager,
    private val params: WindowManager.LayoutParams,
    private val icon: Drawable?,
    private var sizePx: Int,
    private val onTap: () -> Unit,
    private val onSizeChanged: (Int) -> Unit,
    private val onPositionChanged: (Int, Int) -> Unit
) : View(context) {

    private val touchSlop = 8f
    private var downRawX = 0f
    private var downRawY = 0f
    private var startX = 0
    private var startY = 0
    private var moved = false
    private var scaling = false

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CC1E1E1E")
        style = Paint.Style.FILL
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#66FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                scaling = true
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val newSize = (sizePx * detector.scaleFactor).roundToInt()
                    .coerceIn(
                        (FloatingBallConfig.MIN_SIZE_DP * density).roundToInt(),
                        (FloatingBallConfig.MAX_SIZE_DP * density).roundToInt()
                    )
                if (newSize != sizePx) {
                    sizePx = newSize
                    params.width = newSize
                    params.height = newSize
                    windowManager.updateViewLayout(this@FloatingBallView, params)
                    onSizeChanged(newSize)
                }
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                scaling = false
            }
        }
    )

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(sizePx, sizePx)
    }

    override fun onDraw(canvas: Canvas) {
        val r = sizePx / 2f
        canvas.drawCircle(r, r, r - 2f, bgPaint)

        icon?.let {
            val inset = sizePx * 0.22f
            it.setBounds(
                inset.toInt(), inset.toInt(),
                (sizePx - inset).toInt(), (sizePx - inset).toInt()
            )
            it.draw(canvas)
        }
        canvas.drawCircle(r, r, r - 2f, ringPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                startX = params.x
                startY = params.y
                moved = false
                performClick()
            }

            MotionEvent.ACTION_MOVE -> {
                if (scaleDetector.isInProgress) return true
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                if (abs(dx) > touchSlop || abs(dy) > touchSlop) moved = true
                if (moved) {
                    params.x = startX + dx.toInt()
                    params.y = startY + dy.toInt()
                    windowManager.updateViewLayout(this, params)
                }
            }

            MotionEvent.ACTION_UP -> {
                if (!moved && !scaling) {
                    onTap()
                } else {
                    snapToEdge()
                    onPositionChanged(params.x, params.y)
                }
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    /** 松手后贴边吸附 */
    private fun snapToEdge() {
        val metrics = context.resources.displayMetrics
        val screenW = metrics.widthPixels
        val margin = (8 * density).roundToInt()
        val targetX = if (params.x + sizePx / 2 < screenW / 2) {
            margin
        } else {
            screenW - sizePx - margin
        }
        params.x = targetX
        windowManager.updateViewLayout(this, params)
    }
}
