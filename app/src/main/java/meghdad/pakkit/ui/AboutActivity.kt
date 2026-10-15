package meghdad.pakkit.ui

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import meghdad.pakkit.R

/** Offline disclosures remain available even when receiving is off. */
class AboutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = RoundScrollView(this).apply { setBackgroundColor(WatchColors.canvas) }
        val column =
            WatchDesign.column(this).apply {
                val side =
                    WatchDesign.dp(
                        this@AboutActivity,
                        (resources.configuration.screenWidthDp * .15f).toInt(),
                    )
                setPadding(
                    side,
                    WatchDesign.dp(this@AboutActivity, 28),
                    side,
                    WatchDesign.dp(this@AboutActivity, 64),
                )
            }
        val licenses = intent.getBooleanExtra("licenses", false)
        val text =
            assets
                .open(if (licenses) "legal/licenses.txt" else "legal/privacy.txt")
                .bufferedReader()
                .use { it.readText() }
        text.trim().split("\n\n").forEachIndexed { index, paragraph ->
            val heading = index == 0 || (!licenses && paragraph.length < 40)
            column.addView(
                WatchDesign.text(
                        this,
                        paragraph,
                        if (index == 0) 20f else if (heading) 16f else 13f,
                        bold = heading,
                    )
                    .apply {
                        gravity = if (index == 0) Gravity.CENTER else Gravity.START
                        isAccessibilityHeading = heading
                    },
                android.widget.LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = WatchDesign.dp(this@AboutActivity, 14)
                },
            )
        }
        if (!licenses)
            column.addView(
                WatchDesign.button(this, getString(R.string.watch_licenses), false) {
                    startActivity(
                        android.content
                            .Intent(this, AboutActivity::class.java)
                            .putExtra("licenses", true)
                    )
                }
            )
        column.addView(WatchDesign.button(this, getString(R.string.watch_done), false) { finish() })
        scroll.addView(column)
        setContentView(scroll)
        scroll.isFocusableInTouchMode = true
        scroll.requestFocus()
        savedInstanceState?.getInt("scroll")?.let { y -> scroll.post { scroll.scrollTo(0, y) } }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        val content = findViewById<android.view.ViewGroup>(android.R.id.content)
        outState.putInt("scroll", (content.getChildAt(0) as RoundScrollView).scrollY)
        super.onSaveInstanceState(outState)
    }
}
