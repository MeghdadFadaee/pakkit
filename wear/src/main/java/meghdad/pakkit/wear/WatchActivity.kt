package meghdad.pakkit.wear

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.*
import android.view.*
import android.widget.*
import meghdad.pakkit.core.*
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class WatchActivity : Activity(), SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var store: Store
    private lateinit var column: LinearLayout
    private lateinit var scroll: RoundScrollView
    private val colors = BrandPalette.Dark
    private val io = Executors.newSingleThreadExecutor()
    private var busy = false
    private var error: String? = null
    private var renderedState = ""
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        acceptIntent(intent)
        refresh()
        savedInstanceState?.getInt("scroll")?.let { y -> scroll.post { scroll.scrollTo(0, y) } }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("scroll", scroll.scrollY)
        super.onSaveInstanceState(outState)
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); acceptIntent(intent); refresh() }
    private fun acceptIntent(intent: Intent) {
        val job = store.read("incoming")
        @Suppress("DEPRECATION") val approval = intent.getParcelableExtra<Intent>("approval")
        if (approval != null && job != null && job.optString("id") == intent.getStringExtra("job") && job.optString("state") == "confirm") {
            PendingApproval.intent = approval
            PendingApproval.id = job.getString("id")
        }
    }
    override fun onResume() {
        super.onResume()
        PendingApproval.activity = WeakReference(this)
        store.prefs.registerOnSharedPreferenceChangeListener(this)
        refresh()
        io.execute { runCatching { Link.capability(this) } }
    }
    override fun onPause() { store.prefs.unregisterOnSharedPreferenceChangeListener(this); PendingApproval.activity = null; super.onPause() }
    override fun onDestroy() { io.shutdown(); super.onDestroy() }
    override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
        if (key == "incoming" || key == null) runOnUiThread { refresh() }
    }
    private fun dp(n: Int) = Design.dp(this, n)
    private fun line(value: String, size: Float = 14f, color: Int = colors.text, bold: Boolean = false) = Design.text(this, value, size, color, bold).apply {
        gravity = Gravity.CENTER
        breakStrategy = android.graphics.text.LineBreaker.BREAK_STRATEGY_BALANCED
    }
    private fun add(v: View, top: Int = 0) {
        column.addView(v, LinearLayout.LayoutParams(-1, v.layoutParams?.height ?: -2).apply { topMargin = dp(top) })
    }
    private fun button(label: String, primary: Boolean = true, action: () -> Unit) {
        add(Design.button(this, label, primary, action).apply {
            textSize = 15f
            minHeight = dp(48)
            isEnabled = !busy
        }, 10)
    }
    private fun parcel(size: Int = 48) {
        add(ImageView(this).apply {
            setImageResource(meghdad.pakkit.core.R.drawable.pakkit_icon)
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(-1, dp(size))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, 9)
    }
    private fun statusIcon(glyph: Glyph, success: Boolean) {
        add(FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, dp(56))
            addView(ImageView(this@WatchActivity).apply {
                setImageDrawable(GlyphDrawable(glyph, if (success) colors.mint else colors.error))
                setPadding(dp(12), dp(12), dp(12), dp(12))
                background = Design.shape(if (success) colors.mintSoft else colors.errorSoft, dp(24).toFloat())
                scaleType = ImageView.ScaleType.FIT_CENTER
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER))
        }, 8)
    }
    private fun badge(value: String, positive: Boolean = false) {
        val text = Design.badge(this, value, positive).apply { gravity = Gravity.CENTER }
        column.addView(text, LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(10) })
    }
    fun refresh() {
        if (isDestroyed || isFinishing) return
        val job = store.read("incoming")
        val stateKey = job?.let { "${it.optString("id")}:${it.optString("state")}" } ?: "home"
        val y = if (::scroll.isInitialized && stateKey == renderedState) scroll.scrollY else 0
        renderedState = stateKey
        scroll = RoundScrollView(this).apply { setBackgroundColor(colors.canvas) }
        val width = resources.configuration.screenWidthDp
        val side = (width * .15f).roundToInt().coerceAtLeast(26)
        column = Design.column(this).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(side), dp(12), dp(side), dp((width * .24f).roundToInt().coerceAtLeast(44)))
        }
        scroll.addView(column)
        add(TextClock(this).apply {
            format12Hour = "h:mm"; format24Hour = "HH:mm"
            gravity = Gravity.CENTER; textSize = 12f; setTextColor(colors.secondary)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        })
        if (job == null) home() else incoming(job)
        error?.let { add(line(it, 13f, colors.error), 12) }
        scroll.isFocusable = true
        scroll.isFocusableInTouchMode = true
        setContentView(scroll)
        scroll.requestFocus()
        scroll.post { scroll.scrollTo(0, y) }
    }
    private fun home() {
        parcel(54)
        add(line("Pakkit", 25f, bold = true), 4)
        add(line(getString(meghdad.pakkit.core.R.string.brand_tagline), 12f, colors.secondary), 5)
        val supported = Link.installer(this)
        val allowed = packageManager.canRequestPackageInstalls()
        when {
            !supported -> {
                badge("Use Google Play")
                add(line("This watch does not expose a system installer.", 13f, colors.secondary), 12)
                button("Open Google Play") { play() }
            }
            allowed -> {
                badge("Ready to receive", positive = true)
                add(line("Choose an APK on your phone. Review it here before installing.", 13f, colors.secondary), 12)
            }
            Link.settings(this).resolveActivity(packageManager) != null -> {
                badge("One small permission")
                add(line("Allow Pakkit to request installs. You still approve every app.", 13f, colors.secondary), 12)
                button("Allow installs") { settings() }
            }
            else -> {
                badge("Use Google Play")
                add(line("External installation is not available on this watch’s software.", 13f, colors.secondary), 12)
                button("Open Google Play") { play() }
            }
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            add(line("Optional alerts bring you back to system approval.", 12f, colors.secondary), 18)
            button("Enable alerts", false) { requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 52) }
        }
    }
    private fun incoming(job: JSONObject) {
        val apk = Apk.from(job.getJSONObject("apk"))
        val state = job.optString("state")
        when (state) {
            "installed" -> statusIcon(Glyph.CHECK, success = true)
            "failed" -> statusIcon(Glyph.ALERT, success = false)
            else -> parcel(42)
        }
        when (state) {
            "installed" -> {
                add(line("All yours.", 25f, bold = true), 5)
                add(line(apk.label, 17f, bold = true), 10)
            }
            "failed" -> {
                add(line("A small detour.", 22f, bold = true), 5)
                add(line(apk.label, 16f), 10)
            }
            else -> add(line(apk.label, 21f, bold = true), 5)
        }
        add(line("${apk.version} · ${"%.1f".format(apk.bytes / 1048576.0)} MB", 12f, colors.secondary), 7)
        if (busy) add(line("Preparing installer…", 13f, colors.secondary), 12)
        else if (state == "ready") badge("Ready to install", positive = true)
        else add(line(job.optString("detail"), 13f, colors.secondary), 12)
        when (state) {
            "receiving" -> {
                add(Design.progress(this).apply { contentDescription = "Receiving and verifying the APK" }, 14)
                add(line("A final check before it’s yours.", 12f, colors.secondary), 10)
            }
            "ready", "failed" -> {
                if (!TransferRules.fresh(job.optLong("created"), System.currentTimeMillis())) {
                    add(line("This APK expired. Send it again from your phone.", 13f, colors.error), 12)
                } else if (store.file(job.getString("id")).exists()) {
                    add(line(apk.packageName, 12f, colors.secondary), 12)
                    if (!apk.wear) add(line("Watch support is unverified. The layout may not fit.", 12f, colors.error), 10)
                    add(line("Only install apps you trust.", 13f, colors.secondary), 10)
                    if (packageManager.canRequestPackageInstalls()) button(if (state == "failed") "Try installation" else "Install") { install(job) }
                    else button("Allow installs") { settings() }
                }
                button("Dismiss", false) { dismiss(job) }
            }
            "confirm" -> {
                if (PendingApproval.intent != null && PendingApproval.id == job.getString("id")) button("Continue") {
                    val confirm = PendingApproval.intent ?: return@button
                    runCatching { startActivity(confirm) }.onFailure {
                        error = "The system approval screen could not open. Try the installation again."
                        refresh()
                    }
                } else add(line("Open the approval notification, or retry if interrupted.", 12f, colors.secondary), 10)
                button("Retry approval", false) { install(job) }
            }
            "installing" -> {
                add(line("Follow the system approval. If interrupted, you can retry.", 12f, colors.secondary), 10)
                button("Retry installation", false) { install(job) }
            }
            "installed" -> {
                button("Open app") { launch(apk.packageName) }
                button("Done", false) { store.clear("incoming"); refresh() }
            }
            else -> button("Done") { store.clear("incoming"); refresh() }
        }
    }
    private fun settings() {
        runCatching { startActivity(Link.settings(this)) }.onFailure { error="This watch does not offer that setting. Install compatible apps through Google Play."; refresh() }
    }
    private fun install(job:JSONObject) {
        if(busy) return
        if(!packageManager.canRequestPackageInstalls()) { settings(); return }
        busy=true; error=null; refresh()
        io.execute {
            val installer=packageManager.packageInstaller
            var sessionId: Int?=null
            try {
                require(TransferRules.fresh(job.getLong("created"),System.currentTimeMillis())) { "This APK expired. Send it again." }
                val file=store.file(job.getString("id"))
                val digest=file.inputStream().use { TransferRules.copyBounded(it,object:java.io.OutputStream() { override fun write(b:Int) {} ; override fun write(b:ByteArray,off:Int,len:Int) {} }).second }
                require(digest==job.getJSONObject("apk").getString("sha256")) { "This APK changed. Send it again." }
                val apk=Packages.inspect(this,file,digest)
                Packages.checkForWatch(apk,Build.VERSION.SDK_INT,Build.SUPPORTED_ABIS.toSet(),Packages.free(this))
                job.optInt("session",-1).takeIf { it>=0 }?.let { runCatching { installer.abandonSession(it) } }
                val params=PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    setAppPackageName(apk.packageName); setSize(file.length())
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
                    if(Build.VERSION.SDK_INT>=33) setPackageSource(PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE)
                }
                sessionId=installer.createSession(params)
                job.put("session",sessionId)
                Link.status(this,job,"installing","Waiting for the watch’s system installer.")
                installer.openSession(sessionId).use { session ->
                    session.openWrite("base.apk",0,file.length()).use { output -> file.inputStream().use { it.copyTo(output,64*1024) }; session.fsync(output) }
                    val callback=Intent(this,InstallReceiver::class.java).putExtra("job",job.getString("id"))
                    val pending=PendingIntent.getBroadcast(this,sessionId,callback,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
                    session.commit(pending.intentSender)
                }
            } catch(e:Exception) {
                sessionId?.let { runCatching { installer.abandonSession(it) } }
                Link.status(this,job,"failed",e.message ?: "Could not open the system installer. Use Google Play.")
            }
            runOnUiThread { busy=false; refresh() }
        }
    }
    private fun dismiss(job:JSONObject) {
        Link.status(this,job,"declined","Dismissed on your watch.")
        store.file(job.getString("id")).delete(); store.clear("incoming"); refresh()
    }
    private fun launch(packageName:String) {
        runCatching {
            if(Build.VERSION.SDK_INT>=33) packageManager.getLaunchIntentSenderForPackage(packageName).sendIntent(this,0,null,null,null)
            else startActivity(packageManager.getLaunchIntentForPackage(packageName) ?: error("No launcher"))
        }.onFailure { error="This app has no available watch launcher. Look in your watch’s app list."; refresh() }
    }
    private fun play() {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("market://search?q=wear%20os&c=apps"))) }
            .onFailure { error="Open Google Play from your watch’s app list."; refresh() }
    }
}
