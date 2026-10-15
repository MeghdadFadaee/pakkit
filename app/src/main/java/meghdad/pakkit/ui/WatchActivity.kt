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
    private var qrUrl = ""
    private var qrBitmap: android.graphics.Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = PackageStore(this)
        store.clean()
        acceptIntent(intent)
        refresh()
        savedInstanceState?.getInt("scroll")?.let { y -> scroll.post { scroll.scrollTo(0, y) } }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("scroll", scroll.scrollY)
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
                            GlyphDrawable(glyph, if (success) colors.mint else colors.error),
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
            job?.let { "${it.id}:${it.state}" }
                ?: when {
                    live.total > 0 -> "uploading"
                    live.url != null -> "receiving"
                    live.active -> "connecting"
                    else -> "home"
                }
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
            },
        )
        if (job == null) home()
        else {
            incoming(job)
            if (Receiving.state.url != null)
                button("End Wi-Fi session", false) {
                    stopService(Intent(this, ReceiveService::class.java))
                    refresh()
                }
        }
        error?.let { add(line(it, 13f, colors.error), 12) }
        scroll.isFocusable = true
        scroll.isFocusableInTouchMode = true
        setContentView(scroll)
        scroll.requestFocus()
        scroll.post { scroll.scrollTo(0, y) }
    }

    private fun home() {
        val url = Receiving.state.url
        if (url == null) {
            parcel(54)
            add(line("Pakkit", 25f, bold = true), 4)
            add(line(getString(R.string.brand_tagline), 12f, colors.secondary), 5)
        }
        if (url != null) {
            val received = Receiving.state.received
            val total = Receiving.state.total
            if (total > 0) {
                add(
                    line(
                        "${(received * 100 / total).coerceAtMost(100)}% received",
                        21f,
                        bold = true,
                    ),
                    12,
                )
                add(
                    ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                        max = 100
                        progress = (received * 100 / total).toInt()
                        progressTintList = android.content.res.ColorStateList.valueOf(colors.mint)
                    },
                    12,
                )
                add(line("Keep your browser open.", 12f, colors.secondary), 8)
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
                                        .coerceAtMost(138),
                                ),
                            )
                        contentDescription =
                            "QR code for your private receiving link. The full link is below."
                    },
                    14,
                )
                add(line("Scan to send", 18f, bold = true), 12)
                badge("Private Wi-Fi session", positive = true)
                add(
                    line(
                        "Use the same Wi-Fi. Session ends automatically in 10 minutes.",
                        12f,
                        colors.secondary,
                    ),
                    8,
                )
                add(
                    line(url, 11f, colors.mint).apply {
                        textDirection = View.TEXT_DIRECTION_LTR
                        setTextIsSelectable(true)
                    },
                    14,
                )
                add(
                    line(
                        "Local HTTP · use a trusted network. Anyone with this link can send a file; only you can approve installation.",
                        11f,
                        colors.secondary,
                    ),
                    12,
                )
            }
            button("End session", false) {
                stopService(Intent(this, ReceiveService::class.java))
                refresh()
            }
            return
        }
        if (!packageManager.canRequestPackageInstalls()) {
            badge("Allow installation")
            add(
                line(
                    "Pakkit needs your permission to request installs. You approve every app on your watch.",
                    13f,
                    colors.secondary,
                ),
                12,
            )
            button("Allow installs") { settings() }
            add(
                line(
                    "If your watch does not offer this permission, use Google Play. Pakkit cannot bypass its security settings.",
                    12f,
                    colors.secondary,
                ),
                12,
            )
            button("Open Google Play", false) { play() }
        } else {
            badge("Any device. Any browser.", positive = true)
            add(
                line(
                    "Choose an APK in your browser. Review it here before installing.",
                    14f,
                    colors.secondary,
                ),
                12,
            )
            if (!Receiving.state.active)
                button("Start receiving") {
                    error = null
                    runCatching { startForegroundService(Intent(this, ReceiveService::class.java)) }
                        .onFailure {
                            error = "Could not start receiving. Check Wi-Fi and try again."
                            refresh()
                        }
                }
            add(line(Receiving.state.detail, 12f, colors.secondary), 12)
            if (Receiving.state.active)
                button("Cancel session", false) {
                    stopService(Intent(this, ReceiveService::class.java))
                }
            button("Wi-Fi settings", false) {
                runCatching {
                    startActivity(Intent(android.provider.Settings.ACTION_WIFI_SETTINGS))
                }
                    .onFailure {
                        error = "Open Wi-Fi from your watch’s Settings app."
                        refresh()
                    }
            }
        }
        if (
            Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
        ) {
            button("Enable session alerts", false) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 52)
            }
        }
    }

    private fun incoming(job: PendingApk) {
        val apk = job.apk
        val state = job.state
        when (state) {
            InstallState.INSTALLED -> statusIcon(Glyph.CHECK, success = true)
            InstallState.FAILED -> statusIcon(Glyph.ALERT, success = false)
            else -> parcel(42)
        }
        when (state) {
            InstallState.INSTALLED -> {
                add(line("All yours.", 25f, bold = true), 5)
                add(line(apk.label, 17f, bold = true), 10)
            }
            InstallState.FAILED -> {
                add(line("A small detour.", 22f, bold = true), 5)
                add(line(apk.label, 16f), 10)
            }
            else -> add(line(apk.label, 21f, bold = true), 5)
        }
        add(
            line(
                "${apk.version} · ${"%.1f".format(apk.bytes / 1048576.0)} MiB",
                12f,
                colors.secondary,
            ),
            7,
        )
        if (busy) add(line("Preparing installer…", 13f, colors.secondary), 12)
        else if (state == InstallState.READY) badge("Ready to install", positive = true)
        else add(line(job.detail, 13f, colors.secondary), 12)
        when (state) {
            InstallState.READY,
            InstallState.FAILED -> {
                if (!ApkRules.fresh(job.created, System.currentTimeMillis())) {
                    add(
                        line(
                            "This APK expired. Upload it again from your browser.",
                            13f,
                            colors.error,
                        ),
                        12,
                    )
                } else if (store.file(job.id).exists()) {
                    add(line(apk.packageName, 12f, colors.secondary), 12)
                    if (!apk.wear)
                        add(
                            line(
                                "Watch support is unverified. The layout may not fit.",
                                12f,
                                colors.error,
                            ),
                            10,
                        )
                    add(line("Only install apps you trust.", 13f, colors.secondary), 10)
                    if (packageManager.canRequestPackageInstalls())
                        button(
                            if (state == InstallState.FAILED) "Try installation" else "Install",
                        ) {
                            install(job)
                        }
                    else button("Allow installs") { settings() }
                }
                button("Dismiss", false) { dismiss(job) }
            }
            InstallState.CONFIRM -> {
                if (PendingApproval.intent != null && PendingApproval.id == job.id)
                    button("Continue") {
                        val confirm = PendingApproval.intent ?: return@button
                        runCatching { startActivity(confirm) }
                            .onFailure {
                                error =
                                    "The system approval screen could not open. Try the installation again."
                                refresh()
                            }
                    }
                else
                    add(
                        line(
                            "Open the approval notification, or retry if interrupted.",
                            12f,
                            colors.secondary,
                        ),
                        10,
                    )
                button("Retry approval", false) { install(job) }
            }
            InstallState.INSTALLING -> {
                add(
                    line(
                        "Follow the system approval. If interrupted, you can retry.",
                        12f,
                        colors.secondary,
                    ),
                    10,
                )
                button("Retry installation", false) { install(job) }
            }
            InstallState.INSTALLED -> {
                button("Open app") { launch(apk.packageName) }
                button("Done", false) {
                    store.dismiss(job.id)
                    refresh()
                }
            }
        }
    }

    private fun settings() {
        runCatching { startActivity(ApkInstaller.settings(this)) }
            .onFailure {
                error =
                    "This watch does not offer that setting. Install compatible apps through Google Play."
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
                    packageManager.getLaunchIntentForPackage(packageName) ?: error("No launcher"),
                )
        }
            .onFailure {
                error = "This app has no available watch launcher. Look in your watch’s app list."
                refresh()
            }
    }

    private fun play() {
        runCatching {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=wear%20os&c=apps")),
            )
        }
            .onFailure {
                error = "Open Google Play from your watch’s app list."
                refresh()
            }
    }
}
