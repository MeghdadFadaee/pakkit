package meghdad.pakkit.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

object WatchColors {
    const val canvas = Color.BLACK
    const val raised = 0xFF1C2035.toInt()
    const val text = 0xFFF4F2FF.toInt()
    const val secondary = 0xFFB7B6CE.toInt()
    const val border = 0xFF32364E.toInt()
    const val accent = 0xFFBAA6FF.toInt()
    const val accentSoft = 0xFF30234E.toInt()
    const val mint = 0xFF68E9CF.toInt()
    const val mintSoft = 0xFF123931.toInt()
    const val error = 0xFFFFA7BC.toInt()
    const val errorSoft = 0xFF422330.toInt()
}

object WatchDesign {
    const val PURPLE = 0xFF7150EF.toInt()

    fun dp(c: Context, n: Int) = (n * c.resources.displayMetrics.density).toInt()

    fun shape(color: Int, radius: Float = 24f, stroke: Int? = null) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
            stroke?.let { setStroke(1, it) }
        }

    fun gradient(colors: IntArray, radius: Float) =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply { cornerRadius = radius }

    fun column(c: Context) = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }

    fun text(
        c: Context,
        value: String,
        size: Float,
        color: Int = WatchColors.text,
        bold: Boolean = false,
    ) =
        TextView(c).apply {
            text = value
            textSize = size
            setTextColor(color)
            typeface =
                Typeface.create(
                    Typeface.create("sans-serif", Typeface.NORMAL),
                    if (bold) 650 else 400,
                    false,
                )
            setLineSpacing(dp(c, 2).toFloat(), 1f)
            includeFontPadding = false
        }

    fun button(c: Context, label: String, primary: Boolean = true, action: () -> Unit) =
        Button(c).apply {
            val p = WatchColors
            text = label
            isAllCaps = false
            textSize = 15f
            minHeight = dp(c, 48)
            typeface = Typeface.create(Typeface.create("sans-serif", Typeface.NORMAL), 600, false)
            setTextColor(
                ColorStateList(
                    arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
                    intArrayOf(p.secondary, if (primary) Color.WHITE else p.text),
                )
            )
            val fill =
                if (primary)
                    gradient(
                        intArrayOf(0xFF7854EE.toInt(), 0xFF6038D6.toInt()),
                        dp(c, 24).toFloat(),
                    )
                else shape(p.raised, dp(c, 24).toFloat(), p.border)
            val states =
                android.graphics.drawable.StateListDrawable().apply {
                    addState(
                        intArrayOf(-android.R.attr.state_enabled),
                        shape(p.raised, dp(c, 24).toFloat(), p.border),
                    )
                    addState(intArrayOf(), fill)
                }
            background =
                RippleDrawable(
                    ColorStateList.valueOf(if (primary) 0x33FFFFFF else 0x226F4EF0),
                    states,
                    null,
                )
            stateListAnimator = null
            setPadding(dp(c, 18), dp(c, 12), dp(c, 18), dp(c, 12))
            setOnClickListener {
                performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                action()
            }
        }

    fun badge(c: Context, label: String, positive: Boolean = false) =
        text(
                c,
                label,
                12f,
                if (positive) WatchColors.mint else WatchColors.accent,
                true,
            )
            .apply {
                val p = WatchColors
                setPadding(dp(c, 12), dp(c, 7), dp(c, 12), dp(c, 7))
                background = shape(if (positive) p.mintSoft else p.accentSoft, dp(c, 16).toFloat())
            }
}
