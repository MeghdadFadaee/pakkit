package meghdad.pakkit.ui

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

enum class Glyph {
    CHECK,
    ALERT,
}

/** One stroke family for navigation, metadata and status. No font-dependent symbol icons. */
class GlyphDrawable(private val glyph: Glyph, private val color: Int) : Drawable() {
    override fun getIntrinsicWidth() = 24

    override fun getIntrinsicHeight() = 24

    private val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.8f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
    private val path = Path()

    override fun draw(c: Canvas) {
        val save = c.save()
        c.translate(bounds.left.toFloat(), bounds.top.toFloat())
        c.scale(bounds.width() / 24f, bounds.height() / 24f)
        paint.color = color
        path.rewind()
        when (glyph) {
            Glyph.CHECK -> {
                path.moveTo(5f, 12f)
                path.lineTo(10f, 17f)
                path.lineTo(19f, 7f)
            }
            Glyph.ALERT -> {
                path.moveTo(12f, 3f)
                path.lineTo(22f, 20f)
                path.lineTo(2f, 20f)
                path.close()
                path.moveTo(12f, 9f)
                path.lineTo(12f, 13f)
                path.moveTo(12f, 16f)
                path.lineTo(12f, 16.2f)
            }
        }
        c.drawPath(path, paint)
        c.restoreToCount(save)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(filter: ColorFilter?) {
        paint.colorFilter = filter
    }

    @Deprecated("Drawable API") override fun getOpacity() = PixelFormat.TRANSLUCENT
}
