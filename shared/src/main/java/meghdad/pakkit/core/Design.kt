package meghdad.pakkit.core

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.PowerManager
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.*
import kotlin.math.min
import kotlin.math.pow

/** Semantic colors: mint is an accent, never low-contrast body text on a light surface. */
data class BrandPalette(
    val dark: Boolean, val canvas: Int, val surface: Int, val raised: Int,
    val text: Int, val secondary: Int, val border: Int, val accent: Int,
    val accentSoft: Int, val mint: Int, val mintSoft: Int, val error: Int, val errorSoft: Int
) {
    companion object {
        val Light = BrandPalette(false, 0xFFF6F5FC.toInt(), 0xFFFFFFFF.toInt(), 0xFFEEEBFA.toInt(),
            0xFF19172C.toInt(), 0xFF646078.toInt(), 0xFFDCD8ED.toInt(), 0xFF6140D8.toInt(),
            0xFFEAE3FF.toInt(), 0xFF09665E.toInt(), 0xFFDDF7F0.toInt(), 0xFFA32E4B.toInt(), 0xFFFCE8EE.toInt())
        val Dark = BrandPalette(true, 0xFF090B17.toInt(), 0xFF141727.toInt(), 0xFF1C2035.toInt(),
            0xFFF4F2FF.toInt(), 0xFFB7B6CE.toInt(), 0xFF32364E.toInt(), 0xFFBAA6FF.toInt(),
            0xFF30234E.toInt(), 0xFF68E9CF.toInt(), 0xFF123931.toInt(), 0xFFFFA7BC.toInt(), 0xFF422330.toInt())
    }
}

object Design {
    const val PURPLE = 0xFF7150EF.toInt()
    const val MIDNIGHT = 0xFF090B17.toInt()
    const val MINT = 0xFF68E9CF.toInt()
    const val CYAN = 0xFF37D7F2.toInt()
    private const val THEME = "theme"
    fun themeMode(c: Context): String = c.getSharedPreferences("appearance", Context.MODE_PRIVATE).getString(THEME, "system") ?: "system"
    fun setThemeMode(c: Context, mode: String) {
        require(mode in setOf("system", "light", "dark"))
        c.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit().putString(THEME, mode).apply()
    }
    fun isDark(c: Context): Boolean = when (themeMode(c)) {
        "dark" -> true
        "light" -> false
        else -> c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }
    fun palette(c: Context): BrandPalette = if (Link.isWatch(c) || isDark(c)) BrandPalette.Dark else BrandPalette.Light
    fun dp(c: Context, n: Int) = (n * c.resources.displayMetrics.density).toInt()
    fun motionAllowed(c: Context) = ValueAnimator.areAnimatorsEnabled() && !c.getSystemService(PowerManager::class.java).isPowerSaveMode
    fun shape(color: Int, radius: Float = 24f, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color); cornerRadius = radius; stroke?.let { setStroke(1, it) }
    }
    fun gradient(colors: IntArray, radius: Float) = GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply { cornerRadius = radius }
    fun column(c: Context) = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
    fun text(c: Context, value: String, size: Float, color: Int = palette(c).text, bold: Boolean = false) = TextView(c).apply {
        text = value; textSize = size; setTextColor(color)
        typeface = Typeface.create(Typeface.create("sans-serif", Typeface.NORMAL), if (bold) 650 else 400, false)
        setLineSpacing(dp(c, 2).toFloat(), 1f)
        includeFontPadding = false
    }
    fun button(c: Context, label: String, primary: Boolean = true, action: () -> Unit) = Button(c).apply {
        val p = palette(c)
        text = label; isAllCaps = false; textSize = 15f; minHeight = dp(c, 52)
        typeface = Typeface.create(Typeface.create("sans-serif", Typeface.NORMAL), 600, false)
        setTextColor(ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(p.secondary, if (primary) Color.WHITE else p.text)))
        val fill = if (primary) gradient(intArrayOf(0xFF7854EE.toInt(), 0xFF6038D6.toInt()), dp(c, 20).toFloat())
            else shape(p.raised, dp(c, 20).toFloat(), p.border)
        val states = android.graphics.drawable.StateListDrawable().apply {
            addState(intArrayOf(-android.R.attr.state_enabled), shape(p.raised, dp(c, 20).toFloat(), p.border))
            addState(intArrayOf(), fill)
        }
        background = RippleDrawable(ColorStateList.valueOf(if (primary) 0x33FFFFFF else 0x226F4EF0), states, null)
        stateListAnimator = null
        setPadding(dp(c, 18), dp(c, 12), dp(c, 18), dp(c, 12))
        setOnClickListener { performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM); action() }
    }
    fun badge(c: Context, label: String, positive: Boolean = false, warning: Boolean = false) = text(c, label, 12f,
        if (warning) palette(c).error else if (positive) palette(c).mint else palette(c).accent, true).apply {
        val p = palette(c)
        setPadding(dp(c, 12), dp(c, 7), dp(c, 12), dp(c, 7))
        background = shape(if (warning) p.errorSoft else if (positive) p.mintSoft else p.accentSoft, dp(c, 16).toFloat())
    }
    fun progress(c: Context) = ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal).apply {
        isIndeterminate = true
        indeterminateTintList = ColorStateList.valueOf(palette(c).accent)
        contentDescription = c.getString(R.string.transfer_waiting)
    }
    fun entrance(v: View) {
        if (!motionAllowed(v.context)) return
        v.alpha = 0f; v.translationY = dp(v.context, 6).toFloat()
        v.animate().alpha(1f).translationY(0f).setDuration(240).setInterpolator(DecelerateInterpolator()).start()
    }
    fun contrast(a: Int, b: Int): Double {
        fun luminance(color: Int): Double {
            fun channel(shift: Int): Double {
                val v = ((color shr shift) and 255) / 255.0
                return if (v <= 0.04045) v / 12.92 else ((v + .055) / 1.055).pow(2.4)
            }
            return .2126 * channel(16) + .7152 * channel(8) + .0722 * channel(0)
        }
        val x = luminance(a); val y = luminance(b)
        return (maxOf(x, y) + .05) / (minOf(x, y) + .05)
    }
}

/** Static ambient lighting; shaders are rebuilt only when bounds change. */
class AuroraDrawable(private val p: BrandPalette) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var glow: Shader? = null
    override fun onBoundsChange(bounds: Rect) {
        glow = RadialGradient(bounds.width() * .9f, 0f, bounds.width() * 1.1f,
            if (p.dark) 0xFF211738.toInt() else 0xFFEDE5FF.toInt(), p.canvas, Shader.TileMode.CLAMP)
    }
    override fun draw(canvas: Canvas) { paint.shader = glow; canvas.drawRect(bounds, paint) }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(filter: ColorFilter?) { paint.colorFilter = filter }
    @Deprecated("Drawable API") override fun getOpacity() = PixelFormat.OPAQUE
}

enum class Glyph { TRANSFER, HISTORY, GUIDE, APPEARANCE, WATCH, FILE, CHECK, ALERT, SHIELD }

/** One stroke family for navigation, metadata and status. No font-dependent symbol icons. */
class GlyphDrawable(private val glyph: Glyph, private val color: Int) : Drawable() {
    override fun getIntrinsicWidth() = 24
    override fun getIntrinsicHeight() = 24
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.8f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val path = Path()
    override fun draw(c: Canvas) {
        val save = c.save(); c.translate(bounds.left.toFloat(), bounds.top.toFloat()); c.scale(bounds.width() / 24f, bounds.height() / 24f)
        paint.color = color
        path.rewind()
        when (glyph) {
            Glyph.TRANSFER -> { path.moveTo(4f, 8f); path.lineTo(19f, 8f); path.lineTo(15f, 4f); path.moveTo(20f, 16f); path.lineTo(5f, 16f); path.lineTo(9f, 20f) }
            Glyph.HISTORY -> { c.drawCircle(12f, 12f, 8f, paint); path.moveTo(12f, 7f); path.lineTo(12f, 12f); path.lineTo(16f, 14f) }
            Glyph.GUIDE -> { c.drawRoundRect(4f, 3f, 20f, 21f, 4f, 4f, paint); path.moveTo(9f, 8f); path.lineTo(15f, 8f); path.moveTo(9f, 12f); path.lineTo(15f, 12f); path.moveTo(9f, 16f); path.lineTo(12f, 16f) }
            Glyph.APPEARANCE -> { c.drawCircle(12f, 12f, 8f, paint); paint.style = Paint.Style.FILL; c.drawArc(4f, 4f, 20f, 20f, 90f, 180f, true, paint); paint.style = Paint.Style.STROKE }
            Glyph.WATCH -> { c.drawRoundRect(5f, 6f, 19f, 18f, 5f, 5f, paint); path.moveTo(8f, 6f); path.lineTo(9f, 2f); path.lineTo(15f, 2f); path.lineTo(16f, 6f); path.moveTo(8f, 18f); path.lineTo(9f, 22f); path.lineTo(15f, 22f); path.lineTo(16f, 18f) }
            Glyph.FILE -> { path.moveTo(14f, 3f); path.lineTo(5f, 3f); path.lineTo(5f, 21f); path.lineTo(19f, 21f); path.lineTo(19f, 8f); path.close(); path.moveTo(14f, 3f); path.lineTo(14f, 8f); path.lineTo(19f, 8f); path.moveTo(9f, 13f); path.lineTo(15f, 13f); path.moveTo(9f, 17f); path.lineTo(13f, 17f) }
            Glyph.CHECK -> { path.moveTo(5f, 12f); path.lineTo(10f, 17f); path.lineTo(19f, 7f) }
            Glyph.ALERT -> { path.moveTo(12f, 3f); path.lineTo(22f, 20f); path.lineTo(2f, 20f); path.close(); path.moveTo(12f, 9f); path.lineTo(12f, 13f); path.moveTo(12f, 16f); path.lineTo(12f, 16.2f) }
            Glyph.SHIELD -> { path.moveTo(12f, 2f); path.lineTo(20f, 6f); path.lineTo(19f, 15f); path.quadTo(16f, 20f, 12f, 22f); path.quadTo(8f, 20f, 5f, 15f); path.lineTo(4f, 6f); path.close(); path.moveTo(8f, 12f); path.lineTo(11f, 15f); path.lineTo(16f, 9f) }
        }
        c.drawPath(path, paint); c.restoreToCount(save)
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(filter: ColorFilter?) { paint.colorFilter = filter }
    @Deprecated("Drawable API") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** A reusable parcel illustration. Geometry and shaders are allocated once, never per frame. */
class ParcelSceneView(c: Context, private val compact: Boolean = false) : View(c) {
    private val p = Design.palette(c)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val left = polygon(106f, 111f, 180f, 140f, 180f, 211f, 106f, 180f)
    private val right = polygon(180f, 140f, 254f, 111f, 254f, 180f, 180f, 211f)
    private val inside = polygon(106f, 111f, 180f, 81f, 254f, 111f, 180f, 143f)
    private val flapLeft = polygon(106f, 111f, 180f, 143f, 137f, 166f, 63f, 132f)
    private val flapRight = polygon(180f, 143f, 254f, 111f, 297f, 132f, 223f, 166f)
    private val flapBack = polygon(106f, 111f, 180f, 81f, 153f, 61f, 82f, 92f)
    private val flapBackRight = polygon(180f, 81f, 254f, 111f, 278f, 92f, 207f, 61f)
    private val faceLeft = LinearGradient(106f, 111f, 186f, 213f, intArrayOf(0xFF36D5F4.toInt(), 0xFF7762FA.toInt(), 0xFFA66CF2.toInt()), null, Shader.TileMode.CLAMP)
    private val faceRight = LinearGradient(180f, 140f, 260f, 190f, intArrayOf(0xFF6845EC.toInt(), 0xFF8D52F5.toInt(), 0xFFE79BD3.toInt()), null, Shader.TileMode.CLAMP)
    private val mintFlap = LinearGradient(63f, 112f, 180f, 157f, 0xFF8EF7E2.toInt(), 0xFF6B80FF.toInt(), Shader.TileMode.CLAMP)
    private val violetFlap = LinearGradient(180f, 133f, 284f, 148f, 0xFFE3CAFF.toInt(), 0xFFA98CFF.toInt(), Shader.TileMode.CLAMP)
    private val tile = LinearGradient(-23f, -23f, 24f, 23f, intArrayOf(0xFF6EEFD8.toInt(), 0xFF56CCF4.toInt(), 0xFF8554F3.toInt()), null, Shader.TileMode.CLAMP)
    private val glow = RadialGradient(180f, 126f, 135f, if (p.dark) 0x665D3ACF else 0x335F3BE2, Color.TRANSPARENT, Shader.TileMode.CLAMP)
    private val ground = RadialGradient(180f, 211f, 100f, if (p.dark) 0x55533DA7 else 0x22715AC1, Color.TRANSPARENT, Shader.TileMode.CLAMP)
    private var lift = 0f
    private var played = false
    var active = false
        set(value) { if (field != value) { field = value; play() } }
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1100
        interpolator = DecelerateInterpolator()
        addUpdateListener { lift = it.animatedValue as Float; invalidate() }
    }
    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
    private fun polygon(vararg xy: Float) = Path().apply { moveTo(xy[0], xy[1]); for (i in 2 until xy.size step 2) lineTo(xy[i], xy[i + 1]); close() }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); play() }
    override fun onDetachedFromWindow() { animator.cancel(); super.onDetachedFromWindow() }
    override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(visibility); if (visibility != VISIBLE) animator.cancel() else if (isAttachedToWindow) play() }
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) { super.onWindowFocusChanged(hasWindowFocus); if (!hasWindowFocus) animator.cancel() else play() }
    private fun play() {
        if (!isAttachedToWindow || compact || !hasWindowFocus() || windowVisibility != VISIBLE || played || !Design.motionAllowed(context)) { lift = 1f; return }
        played = true; animator.start()
    }
    private fun face(c: Canvas, path: Path, shader: Shader) {
        paint.style = Paint.Style.FILL; paint.alpha = 255; paint.shader = shader; c.drawPath(path, paint); paint.shader = null
        paint.style = Paint.Style.STROKE; paint.strokeWidth = .8f; paint.color = 0x88FFFFFF.toInt(); c.drawPath(path, paint); paint.style = Paint.Style.FILL
    }
    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val save = c.save(); c.translate(width / 2f, height / 2f)
        val scale = min(width / 350f, height / 235f)
        c.scale(scale, scale); c.translate(-180f, -120f)
        if (!compact) {
            paint.shader = glow; c.drawCircle(180f, 126f, 135f, paint); paint.shader = ground; c.drawOval(65f, 181f, 295f, 236f, paint); paint.shader = null
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 1f; paint.color = if (p.dark) 0x4458DCCB else 0x336749C9
            c.drawOval(24f, 148f, 336f, 220f, paint); c.drawOval(42f, 137f, 318f, 231f, paint)
            paint.style = Paint.Style.FILL
        }
        paint.color = 0xFF352355.toInt(); c.drawPath(inside, paint)
        face(c, flapBack, violetFlap); face(c, flapBackRight, mintFlap)
        face(c, left, faceLeft); face(c, right, faceRight); face(c, flapLeft, mintFlap); face(c, flapRight, violetFlap)
        c.save(); c.translate(184f, 46f - 8f * lift); c.rotate(18f)
        paint.alpha = 255; paint.shader = tile; c.drawRoundRect(-23f, -23f, 23f, 23f, 11f, 11f, paint); paint.shader = null
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.4f; paint.color = 0xDDFFFFFF.toInt(); c.drawRoundRect(-23f, -23f, 23f, 23f, 11f, 11f, paint)
        paint.style = Paint.Style.FILL; paint.color = 0xCCFFFFFF.toInt(); c.drawRoundRect(-9f, -7f, 9f, 7f, 4f, 4f, paint)
        c.restore()
        if (!compact) {
            spark(c, 70f, 65f, 4f, p.accent); spark(c, 282f, 52f, 5f, Design.MINT); spark(c, 308f, 189f, 3f, p.accent)
            paint.color = Design.CYAN; c.drawCircle(44f, 186f, 3f, paint)
            paint.color = 0xFFFFB980.toInt(); c.drawCircle(296f, 97f, 3f, paint)
        }
        c.restoreToCount(save)
    }
    private fun spark(c: Canvas, x: Float, y: Float, r: Float, color: Int) {
        paint.color = color; paint.strokeWidth = 1.5f; paint.strokeCap = Paint.Cap.ROUND
        c.drawLine(x - r, y, x + r, y, paint); c.drawLine(x, y - r, x, y + r, paint)
    }
}

/** Scroll position indicator follows the circular bezel and redraws only in response to scrolling. */
class RoundScrollView(c: Context) : ScrollView(c) {
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = Design.dp(c, 2).toFloat(); strokeCap = Paint.Cap.ROUND }
    init { isVerticalScrollBarEnabled = false; isFillViewport = true; clipToPadding = false }
    override fun onDrawForeground(canvas: Canvas) {
        super.onDrawForeground(canvas)
        val range = computeVerticalScrollRange().toFloat(); val extent = height.toFloat()
        if (range <= extent + 1) return
        val inset = Design.dp(context, 4).toFloat()
        track.color = 0x3356557F
        canvas.drawArc(inset, scrollY + inset, width - inset, scrollY + height - inset, -32f, 64f, false, track)
        val sweep = (64f * extent / range).coerceAtLeast(12f)
        val position = (scrollY / (range - extent)).coerceIn(0f, 1f)
        track.color = 0xFFBAA6FF.toInt()
        canvas.drawArc(inset, scrollY + inset, width - inset, scrollY + height - inset, -32f + (64f - sweep) * position, sweep, false, track)
    }
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_SCROLL) {
            scrollBy(0, (-event.getAxisValue(MotionEvent.AXIS_SCROLL) * Design.dp(context, 42)).toInt()); return true
        }
        return super.onGenericMotionEvent(event)
    }
}
