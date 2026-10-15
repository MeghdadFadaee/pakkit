package meghdad.pakkit.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextClock
import java.util.concurrent.Executors
import kotlin.math.roundToInt
import meghdad.pakkit.R
import meghdad.pakkit.apk.ApkRules
import meghdad.pakkit.apk.InstallState
import meghdad.pakkit.apk.PendingApk
import meghdad.pakkit.data.PackageStore
import meghdad.pakkit.install.ApkInstaller
import meghdad.pakkit.install.PendingApproval
import meghdad.pakkit.transfer.ReceiveService
import meghdad.pakkit.transfer.Receiving

class WatchActivity : Activity(), SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var store: PackageStore
    private lateinit var column: LinearLayout
    private lateinit var scroll: RoundScrollView
    private val colors = WatchColors
    private val io = Executors.newSingleThreadExecutor()
    private val sessionObserver: () -> Unit = { refresh() }
    private var busy = false
    private var error: String? = null
    private var renderedState = ""
    private var showingLink = false
    private var backRegistered = false
    private val linkBack =
        if (Build.VERSION.SDK_INT >= 33)
            android.window.OnBackInvokedCallback {
                showingLink = false
                refresh()
            }
        else null
    private var qrUrl = ""
    private var qrBitmap: android.graphics.Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = PackageStore(this)
        store.clean()
        acceptIntent(intent)
        showingLink = savedInstanceState?.getBoolean("showingLink") ?: false
        refresh()
        savedInstanceState?.getInt("scroll")?.let { y -> scroll.post { scroll.scrollTo(0, y) } }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("scroll", scroll.scrollY)
        outState.putBoolean("showingLink", showingLink)
        super.onSaveInstanceState(outState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptIntent(intent)
        refresh()
    }

    private fun acceptIntent(intent: Intent) {
        val job = store.pending()
        @Suppress("DEPRECATION") val approval = intent.getParcelableExtra<Intent>("approval")
        if (
            approval != null &&
                job != null &&
                job.id == intent.getStringExtra("job") &&
                job.state == InstallState.CONFIRM
        ) {
            PendingApproval.intent = approval
            PendingApproval.id = job.id
        }
    }

    override fun onResume() {
        super.onResume()
        store.preferences.registerOnSharedPreferenceChangeListener(this)
        Receiving.observe(sessionObserver)
        refresh()
    }

    override fun onPause() {
        Receiving.removeObserver(sessionObserver)
        store.preferences.unregisterOnSharedPreferenceChangeListener(this)
        super.onPause()
    }

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= 33 && backRegistered) {
            onBackInvokedDispatcher.unregisterOnBackInvokedCallback(requireNotNull(linkBack))
        }
        io.shutdown()
        super.onDestroy()
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
        if (key == PackageStore.PENDING_KEY || key == null) runOnUiThread { refresh() }
    }

    private fun dp(n: Int) = WatchDesign.dp(this, n)

    private fun line(
        value: String,
        size: Float = 14f,
        color: Int = colors.text,
        bold: Boolean = false,
    ) =
        WatchDesign.text(this, value, size, color, bold).apply {
            gravity = Gravity.CENTER
            isAccessibilityHeading = bold && size >= 18f
            breakStrategy = android.graphics.text.LineBreaker.BREAK_STRATEGY_BALANCED
        }

    private fun add(v: View, top: Int = 0) {
        column.addView(
            v,
            LinearLayout.LayoutParams(-1, v.layoutParams?.height ?: -2).apply {
                topMargin = dp(top)
            },
        )
    }

    private fun button(label: String, primary: Boolean = true, action: () -> Unit) {
        add(
            WatchDesign.button(this, label, primary, action).apply {
                textSize = 15f
                minHeight = dp(48)
                isEnabled = !busy
            },
            10,
        )
    }

    private fun parcel(size: Int = 48) {
        add(
            ImageView(this).apply {
                setImageResource(R.drawable.pakkit_icon)
                scaleType = ImageView.ScaleType.FIT_CENTER
                layoutParams = LinearLayout.LayoutParams(-1, dp(size))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            },
            9,
        )
    }

    private fun statusIcon(glyph: Glyph, success: Boolean) {
        add(
            FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(-1, dp(56))
                addView(
                    ImageView(this@WatchActivity).apply {
                        setImageDrawable(
                            GlyphDrawable(glyph, if (success) colors.mint else colors.error)
                        )
                        setPadding(dp(12), dp(12), dp(12), dp(12))
                        background =
                            WatchDesign.shape(
                                if (success) colors.mintSoft else colors.errorSoft,
                                dp(24).toFloat(),
                            )
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    },
                    FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER),
                )
            },
            8,
        )
    }

    private fun badge(value: String, positive: Boolean = false) {
        val text = WatchDesign.badge(this, value, positive).apply { gravity = Gravity.CENTER }
        column.addView(
            text,
            LinearLayout.LayoutParams(-2, -2).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(10)
            },
        )
    }

    fun refresh() {
        if (isDestroyed || isFinishing) return
        val job = store.pending()
        val live = Receiving.state
        if (live.url == null || job != null || live.total > 0) showingLink = false
        val stateKey =
            job?.let { "${it.id}:${it.state}" }
                ?: when {
                    showingLink && live.url != null -> "link"
                    live.total > 0 -> "uploading"
                    live.url != null -> "receiving"
                    live.active -> "connecting"
                    else -> "home"
                }
        if (Build.VERSION.SDK_INT >= 33) {
            val handleBack = showingLink && job == null && live.url != null && live.total == 0L
            if (handleBack && !backRegistered) {
                onBackInvokedDispatcher.registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    requireNotNull(linkBack),
                )
            } else if (!handleBack && backRegistered) {
                onBackInvokedDispatcher.unregisterOnBackInvokedCallback(requireNotNull(linkBack))
            }
            backRegistered = handleBack
        }
        val previousState = renderedState
        val y = if (::scroll.isInitialized && stateKey == renderedState) scroll.scrollY else 0
        renderedState = stateKey
        scroll = RoundScrollView(this).apply { setBackgroundColor(colors.canvas) }
        val width = resources.configuration.screenWidthDp
        val side = (width * .15f).roundToInt().coerceAtLeast(26)
        column =
            WatchDesign.column(this).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(
                    dp(side),
                    dp(12),
                    dp(side),
                    dp((width * .24f).roundToInt().coerceAtLeast(44)),
                )
            }
        scroll.addView(column)
        add(
            TextClock(this).apply {
                format12Hour = "h:mm"
                format24Hour = "HH:mm"
                gravity = Gravity.CENTER
                textSize = 12f
                setTextColor(colors.secondary)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
        )
        if (job == null) home()
        else {
            incoming(job)
            if (Receiving.state.url != null)
                button(getString(R.string.watch_end_session), false) {
                    stopService(Intent(this, ReceiveService::class.java))
                    refresh()
                }
        }
        error?.let { add(line(it, 13f, colors.error), 12) }
        scroll.isFocusable = true
        scroll.isFocusableInTouchMode = true
        setContentView(scroll)
        scroll.requestFocus()
        if (
            y == 0 &&
                stateKey != previousState &&
                android.animation.ValueAnimator.areAnimatorsEnabled()
        ) {
            column.alpha = 0f
            column.animate().alpha(1f).setDuration(180).start()
        }
        scroll.post { scroll.scrollTo(0, y) }
    }

    private fun home() {
        val live = Receiving.state
        val url = live.url
        if (url != null) {
            if (showingLink && live.total == 0L) {
                showLink(url)
                return
            }
            if (live.total > 0) {
                parcel(48)
                val percent = (live.received * 100 / live.total).coerceAtMost(100)
                add(
                    line(
                        if (percent == 100L) getString(R.string.watch_just_checking)
                        else getString(R.string.watch_on_its_way),
                        22f,
                        bold = true,
                    ),
                    10,
                )
                add(line("$percent%", 30f, colors.mint, true), 10)
                add(
                    ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                        max = 100
                        progress = percent.toInt()
                        progressTintList = android.content.res.ColorStateList.valueOf(colors.mint)
                        contentDescription = getString(R.string.watch_percent_received, percent)
                    },
                    12,
                )
                add(
                    line(
                        if (percent == 100L) getString(R.string.watch_getting_your_app_ready)
                        else getString(R.string.watch_keep_your_browser_open),
                        13f,
                        colors.secondary,
                    ),
                    10,
                )
            } else {
                if (qrUrl != url || qrBitmap == null) {
                    val matrix =
                        com.google.zxing.qrcode
                            .QRCodeWriter()
                            .encode(
                                url,
                                com.google.zxing.BarcodeFormat.QR_CODE,
                                420,
                                420,
                                mapOf(com.google.zxing.EncodeHintType.MARGIN to 3),
                            )
                    val bitmap =
                        android.graphics.Bitmap.createBitmap(
                            420,
                            420,
                            android.graphics.Bitmap.Config.RGB_565,
                        )
                    for (y in 0 until 420) for (x in 0 until 420) bitmap.setPixel(
                        x,
                        y,
                        if (matrix[x, y]) Color.BLACK else Color.WHITE,
                    )
                    qrUrl = url
                    qrBitmap = bitmap
                }
                add(
                    ImageView(this).apply {
                        setImageBitmap(qrBitmap)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        layoutParams =
                            LinearLayout.LayoutParams(
                                -1,
                                dp(
                                    (resources.configuration.screenWidthDp * .6f)
                                        .roundToInt()
                                        .coerceAtMost(138)
                                ),
                            )
                        contentDescription = getString(R.string.scan_code_description)
                    },
                    14,
                )
                add(line(getString(R.string.watch_scan_to_send), 20f, bold = true), 12)
                add(line(getString(R.string.watch_scan_description), 13f, colors.secondary), 8)
                add(line(getString(R.string.watch_ready_for_10_minutes), 12f, colors.mint), 10)
                button(getString(R.string.watch_view_link), false) {
                    showingLink = true
                    refresh()
                }
            }
            button(getString(R.string.watch_end_session), false) {
                stopService(Intent(this, ReceiveService::class.java))
                refresh()
            }
            return
        }
        add(
            LinearLayout(this).apply {
                gravity = Gravity.CENTER
                addView(
                    ImageView(this@WatchActivity).apply {
                        setImageResource(R.drawable.pakkit_icon)
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    },
                    LinearLayout.LayoutParams(dp(30), dp(30)),
                )
                addView(
                    line(getString(R.string.pakkit_name), 22f, bold = true),
                    LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(6) },
                )
            },
            12,
        )
        if (live.active) {
            badge(getString(R.string.watch_finding_wi_fi))
            add(line(getString(R.string.watch_connecting_description), 13f, colors.secondary), 12)
            button(getString(R.string.watch_cancel), false) {
                stopService(Intent(this, ReceiveService::class.java))
            }
        } else {
            button(getString(R.string.watch_start_receiving)) {
                error = null
                runCatching { startForegroundService(Intent(this, ReceiveService::class.java)) }
                    .onFailure {
                        error = getString(R.string.watch_connection_error)
                        refresh()
                    }
            }
            add(line(getString(R.string.brand_tagline), 12f, colors.accent), 14)
            if (live.detail != Receiving.State().detail)
                add(line(live.detail, 13f, colors.secondary), 12)
            button(getString(R.string.watch_wi_fi), false) {
                runCatching {
                        startActivity(Intent(android.provider.Settings.ACTION_WIFI_SETTINGS))
                    }
                    .onFailure {
                        error = getString(R.string.watch_wifi_unavailable)
                        refresh()
                    }
            }
        }
    }

    private fun showLink(url: String) {
        add(line(getString(R.string.watch_your_watch_link), 20f, bold = true), 14)
        add(line(getString(R.string.watch_link_description), 13f, colors.secondary), 12)
        add(
            line(url, 12f, colors.mint).apply {
                textDirection = View.TEXT_DIRECTION_LTR
                setTextIsSelectable(true)
            },
            14,
        )
        add(line(getString(R.string.watch_connection_privacy), 12f, colors.secondary), 14)
        button(getString(R.string.watch_back_to_code), false) {
            showingLink = false
            refresh()
        }
    }

    // Android 12 uses this callback; Android 13+ registers the platform callback above.
    @Suppress("DEPRECATION")
    @android.annotation.SuppressLint("GestureBackNavigation")
    @Deprecated("Legacy callback for Android 12")
    override fun onBackPressed() {
        if (showingLink) {
            showingLink = false
            refresh()
        } else super.onBackPressed()
    }

    private fun incoming(job: PendingApk) {
        val apk = job.apk
        val state = job.state
        when (state) {
            InstallState.INSTALLED -> statusIcon(Glyph.CHECK, success = true)
            InstallState.FAILED -> statusIcon(Glyph.ALERT, success = false)
            else -> parcel(32)
        }
        when (state) {
            InstallState.INSTALLED -> {
                add(line(getString(R.string.watch_all_yours), 22f, bold = true), 5)
                add(line(apk.label, 16f, bold = true), 10)
            }
            InstallState.FAILED -> {
                add(line(getString(R.string.watch_couldn_t_install), 22f, bold = true), 5)
                add(line(apk.label, 16f), 10)
            }
            else -> add(line(apk.label, 20f, bold = true), 5)
        }
        add(
            line(
                getString(
                    R.string.watch_app_summary,
                    apk.version,
                    "%.1f".format(apk.bytes / 1048576.0),
                ),
                12f,
                colors.secondary,
            ),
            7,
        )
        if (busy) add(line(getString(R.string.watch_getting_ready), 13f, colors.secondary), 12)
        else if (state == InstallState.READY)
            badge(getString(R.string.watch_ready_to_install), positive = true)
        else if (state != InstallState.INSTALLED) add(line(job.detail, 13f, colors.secondary), 12)
        when (state) {
            InstallState.READY,
            InstallState.FAILED -> {
                if (!ApkRules.fresh(job.created, System.currentTimeMillis())) {
                    add(
                        line(
                            getString(R.string.watch_file_expired),
                            13f,
                            colors.error,
                        ),
                        12,
                    )
                } else if (store.file(job.id).exists()) {
                    if (!apk.wear)
                        add(
                            line(
                                getString(R.string.watch_fit_notice),
                                12f,
                                colors.error,
                            ),
                            10,
                        )
                    if (packageManager.canRequestPackageInstalls())
                        button(
                            if (state == InstallState.FAILED) getString(R.string.watch_try_again)
                            else getString(R.string.watch_install)
                        ) {
                            install(job)
                        }
                    else {
                        add(
                            line(
                                getString(R.string.watch_permission_description),
                                13f,
                                colors.secondary,
                            ),
                            10,
                        )
                        button(getString(R.string.watch_allow_installs)) { settings() }
                        button(getString(R.string.watch_google_play), false) { play() }
                    }
                }
                add(line(getString(R.string.watch_trust_notice), 12f, colors.secondary), 10)
                button(getString(R.string.watch_remove_file), false) { dismiss(job) }
            }
            InstallState.CONFIRM -> {
                if (PendingApproval.intent != null && PendingApproval.id == job.id)
                    button(getString(R.string.watch_continue)) {
                        val confirm = PendingApproval.intent ?: return@button
                        runCatching { startActivity(confirm) }
                            .onFailure {
                                error = getString(R.string.watch_approval_error)
                                refresh()
                            }
                    }
                else
                    add(
                        line(
                            getString(R.string.watch_approval_recovery),
                            12f,
                            colors.secondary,
                        ),
                        10,
                    )
                button(getString(R.string.watch_try_again), false) { install(job) }
                if (
                    Build.VERSION.SDK_INT >= 33 &&
                        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                            PackageManager.PERMISSION_GRANTED
                ) {
                    button(getString(R.string.watch_enable_alerts), false) {
                        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 52)
                    }
                }
            }
            InstallState.INSTALLING -> {
                add(
                    line(
                        getString(R.string.watch_installing_description),
                        12f,
                        colors.secondary,
                    ),
                    10,
                )
                button(getString(R.string.watch_try_again), false) { install(job) }
            }
            InstallState.INSTALLED -> {
                button(getString(R.string.watch_open_app)) { launch(apk.packageName) }
                button(getString(R.string.watch_done), false) {
                    store.dismiss(job.id)
                    refresh()
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 52) {
            meghdad.pakkit.install.InstallReceiver.notifyApproval(this)
            refresh()
        }
    }

    private fun settings() {
        runCatching { startActivity(ApkInstaller.settings(this)) }
            .onFailure {
                error = getString(R.string.watch_permission_unavailable)
                refresh()
            }
    }

    private fun install(job: PendingApk) {
        if (busy) return
        if (!packageManager.canRequestPackageInstalls()) {
            settings()
            return
        }
        busy = true
        error = null
        refresh()
        io.execute {
            ApkInstaller(applicationContext).install(job)
            runOnUiThread {
                busy = false
                refresh()
            }
        }
    }

    private fun dismiss(job: PendingApk) {
        PendingApproval.clear()
        store.dismiss(job.id)
        refresh()
    }

    private fun launch(packageName: String) {
        runCatching {
            if (Build.VERSION.SDK_INT >= 33)
                packageManager
                    .getLaunchIntentSenderForPackage(packageName)
                    .sendIntent(this, 0, null, null, null)
            else
                startActivity(
                    packageManager.getLaunchIntentForPackage(packageName) ?: error("No launcher")
                )
        }
            .onFailure {
                error = getString(R.string.watch_launch_unavailable)
                refresh()
            }
    }

    private fun play() {
        runCatching {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=wear%20os&c=apps"))
            )
        }
            .onFailure {
                error = getString(R.string.watch_play_unavailable)
                refresh()
            }
    }
}
