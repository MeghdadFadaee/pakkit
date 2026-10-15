package meghdad.pakkit.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.widget.ScrollView

/**
 * Scroll position indicator follows the circular bezel and redraws only in response to scrolling.
 */
class RoundScrollView(c: Context) : ScrollView(c) {
    private val track =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = WatchDesign.dp(c, 2).toFloat()
            strokeCap = Paint.Cap.ROUND
        }

    init {
        isVerticalScrollBarEnabled = false
        isFillViewport = true
        clipToPadding = false
    }

    override fun onDrawForeground(canvas: Canvas) {
        super.onDrawForeground(canvas)
        val range = computeVerticalScrollRange().toFloat()
        val extent = height.toFloat()
        if (range <= extent + 1) return
        val inset = WatchDesign.dp(context, 4).toFloat()
        track.color = 0x3356557F
        canvas.drawArc(
            inset,
            scrollY + inset,
            width - inset,
            scrollY + height - inset,
            -32f,
            64f,
            false,
            track,
        )
        val sweep = (64f * extent / range).coerceAtLeast(12f)
        val position = (scrollY / (range - extent)).coerceIn(0f, 1f)
        track.color = 0xFFBAA6FF.toInt()
        canvas.drawArc(
            inset,
            scrollY + inset,
            width - inset,
            scrollY + height - inset,
            -32f + (64f - sweep) * position,
            sweep,
            false,
            track,
        )
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_SCROLL) {
            scrollBy(
                0,
                (-event.getAxisValue(MotionEvent.AXIS_SCROLL) * WatchDesign.dp(context, 42))
                    .toInt(),
            )
            return true
        }
        return super.onGenericMotionEvent(event)
    }
}
