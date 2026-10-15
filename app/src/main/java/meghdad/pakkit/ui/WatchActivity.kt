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
import android.widget.TextView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import java.util.concurrent.Executors
import kotlin.math.roundToInt
import meghdad.pakkit.R
import meghdad.pakkit.apk.InstallState
import meghdad.pakkit.apk.PendingApk
import meghdad.pakkit.apk.userMessage
import meghdad.pakkit.data.PackageStore
import meghdad.pakkit.install.ApkInstaller
import meghdad.pakkit.install.PendingApproval
import meghdad.pakkit.transfer.BrowserAccess
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
    private var resumed = false
    private var approvalLaunched: Int? = null
    private var requestedInstallId: String? = null
    private var progressTitle: TextView? = null
    private var progressPercent: TextView? = null
    private var progressDetail: TextView? = null
    private var progressBar: ProgressBar? = null
    private var qrUrl = ""
    private var qrBitmap: android.graphics.Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        store = PackageStore(this)
        requestedInstallId = savedInstanceState?.getString("requestedInstallId")
        approvalLaunched = savedInstanceState?.getInt("approvalLaunched", -1)?.takeIf { it >= 0 }
        refresh()
        savedInstanceState?.getInt("scroll")?.let { y -> scroll.post { scroll.scrollTo(0, y) } }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("scroll", scroll.scrollY)
        outState.putString("requestedInstallId", requestedInstallId)
        approvalLaunched?.let { outState.putInt("approvalLaunched", it) }
        super.onSaveInstanceState(outState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        refresh()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        store.preferences.registerOnSharedPreferenceChangeListener(this)
        Receiving.observe(sessionObserver)
        refresh()
        val requested = requestedInstallId
        requestedInstallId = null
        if (requested != null && packageManager.canRequestPackageInstalls()) {
            store.pending()?.takeIf { it.id == requested }?.let { install(it) }
        }
    }

    override fun onPause() {
        resumed = false
        Receiving.removeObserver(sessionObserver)
        store.preferences.unregisterOnSharedPreferenceChangeListener(this)
        super.onPause()
    }

    override fun onDestroy() {
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
        val stateKey =
            live.browserRequest?.let { "browser:${it.id}" }
                ?: job?.let { "${it.id}:${it.state}" }
                ?: when {
                    live.total > 0 -> "uploading"
                    live.url != null -> "receiving"
                    live.active -> "connecting"
                    else -> "home"
                }
        // Keep the same view tree while bytes arrive: TalkBack, rotary focus and
        // scroll position must survive progress updates without repeated layout work.
        if (stateKey == "uploading" && renderedState == stateKey && error == null) {
            updateProgress(live)
            return
        }
        val previousState = renderedState
        if (stateKey != previousState) error = null
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
        error?.let { add(line(it, 13f, colors.error), 12) }
        if (live.browserRequest != null) browserRequest(live.browserRequest)
        else if (job == null) home()
        else {
            incoming(job)
            if (live.active) {
                button(getString(R.string.watch_end_session), false) {
                    stopService(Intent(this, ReceiveService::class.java))
                }
            } else {
                button(getString(R.string.watch_start_receiving), false) { startReceiving() }
            }
        }
        if (
            live.active &&
                live.browserRequest == null &&
                job?.state != InstallState.CONFIRM &&
                Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
        ) {
            button(getString(R.string.watch_enable_alerts), false) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 52)
            }
        }

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
        scroll.post {
            scroll.scrollTo(0, y)
            val approval = PendingApproval.intent
            if (
                resumed &&
                    job?.state == InstallState.CONFIRM &&
                    job.sessionId != null &&
                    PendingApproval.id == job.id &&
                    approval != null &&
                    approvalLaunched != job.sessionId
            ) {
                approvalLaunched = job.sessionId
                runCatching { startActivity(approval) }
                    .onFailure {
                        error = getString(R.string.watch_approval_error)
                        refresh()
                    }
            }
        }
    }

    private fun home() {
        val live = Receiving.state
        val url = live.url
        if (url != null) {
            if (live.total > 0) {
                parcel(48)
                progressTitle = line(getString(R.string.watch_on_its_way), 22f, bold = true)
                add(requireNotNull(progressTitle), 10)
                progressPercent = line("0%", 30f, colors.mint, true)
                add(requireNotNull(progressPercent), 10)
                progressBar =
                    ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                        max = 100
                        progressTintList = android.content.res.ColorStateList.valueOf(colors.mint)
                    }
                add(requireNotNull(progressBar), 12)
                progressDetail =
                    line(getString(R.string.watch_keep_your_browser_open), 13f, colors.secondary)
                add(requireNotNull(progressDetail), 10)
                updateProgress(live)
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
                add(
                    line(url, 12f, colors.mint).apply {
                        textDirection = View.TEXT_DIRECTION_LTR
                        setTextIsSelectable(true)
                    },
                    10,
                )
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
            add(line(live.detail, 13f, colors.secondary), 12)
            button(getString(R.string.watch_end_session), false) {
                stopService(Intent(this, ReceiveService::class.java))
            }
        } else {
            button(getString(R.string.watch_start_receiving)) { startReceiving() }
            add(line(getString(R.string.brand_tagline), 12f, colors.accent), 14)
            if (live.detail != Receiving.State().detail && live.detail != "Receiving stopped")
                add(line(live.detail, 13f, colors.secondary), 12)
        }
        button(getString(R.string.watch_wi_fi), false) {
            runCatching {
                startActivity(Intent(android.provider.Settings.ACTION_WIFI_SETTINGS))
            }
                .onFailure {
                    error = getString(R.string.watch_wifi_unavailable)
                    refresh()
                }
        }
        if (!live.active)
            button(getString(R.string.watch_privacy), false) {
                startActivity(Intent(this, AboutActivity::class.java))
            }
    }

    private fun updateProgress(live: Receiving.State) {
        val percent = ((live.received.toDouble() / live.total) * 100).toInt().coerceIn(0, 100)
        val title =
            getString(
                if (percent == 100) R.string.watch_just_checking else R.string.watch_on_its_way
            )
        val detail =
            getString(
                if (percent == 100) R.string.watch_getting_your_app_ready
                else R.string.watch_keep_your_browser_open
            )
        if (progressTitle?.text != title) progressTitle?.text = title
        if (progressDetail?.text != detail) progressDetail?.text = detail
        val label = "$percent%"
        if (progressPercent?.text != label) {
            progressPercent?.text = label
            progressBar?.progress = percent
            progressBar?.contentDescription = getString(R.string.watch_percent_received, percent)
        }
    }

    private fun startReceiving() {
        error = null
        runCatching { startForegroundService(Intent(this, ReceiveService::class.java)) }
            .onFailure {
                error = getString(R.string.watch_connection_error)
                refresh()
            }
    }

    private fun browserRequest(request: BrowserAccess.Request) {
        add(line(getString(R.string.watch_connect_browser), 21f, bold = true), 8)
        add(line(request.label, 15f, colors.secondary), 10)
        add(line(request.address, 12f, colors.secondary), 5)
        button(getString(R.string.watch_allow_browser)) {
            Receiving.answerBrowser(request.id, true)
        }
        button(getString(R.string.watch_not_now), false) {
            Receiving.answerBrowser(request.id, false)
        }
        button(getString(R.string.watch_end_session), false) {
            stopService(Intent(this, ReceiveService::class.java))
        }
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
                if (store.file(job.id).exists()) {
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
                        button(getString(R.string.watch_allow_installs)) {
                            requestedInstallId = job.id
                            settings()
                        }
                        button(getString(R.string.watch_google_play), false) { play() }
                    }
                }
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
                    dismiss(job)
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
            if (Receiving.state.active) startService(Intent(this, ReceiveService::class.java))
            refresh()
        }
    }

    private fun settings() {
        runCatching { startActivity(ApkInstaller.settings(this)) }
            .onFailure {
                requestedInstallId = null
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
            val result = runCatching { ApkInstaller(applicationContext).install(job) }
            runOnUiThread {
                result.exceptionOrNull()?.let {
                    error = it.userMessage(getString(R.string.watch_save_error))
                }
                busy = false
                refresh()
            }
        }
    }

    private fun dismiss(job: PendingApk) {
        PendingApproval.clear()
        runCatching { store.dismiss(job.id) }
            .onFailure {
                error = it.userMessage("Couldn’t remove this app. Try again.")
            }
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
