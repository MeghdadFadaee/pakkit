package meghdad.pakkit

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.work.*
import com.google.android.gms.wearable.*
import meghdad.pakkit.core.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MainActivity : Activity(), SharedPreferences.OnSharedPreferenceChangeListener {
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val colors by lazy { Design.palette(this) }
    private lateinit var store: Store
    private lateinit var content: LinearLayout
    private lateinit var scroll: ScrollView
    private var renderedPage = -1
    private var page = 0
    private var finding = false
    private var importing = false
    private var watch: Node? = null
    private var capabilities: DataMap? = null
    private var capabilitiesReceivedAt = 0L
    private var connectionText = "Open Pakkit on your paired watch to begin."
    private var message: String? = null
    private var nonce = ""
    private var appearanceDialog: Dialog? = null
    private val redraw = Runnable { if (!isDestroyed) render() }
    private val listener = DataClient.OnDataChangedListener { events ->
        for (e in events) {
            if (e.type == DataEvent.TYPE_CHANGED && e.dataItem.uri.path?.startsWith("/pakkit/cap/") == true) {
                val map = DataMapItem.fromDataItem(e.dataItem).dataMap
                if (e.dataItem.uri.host == watch?.id && map.getString("nonce") == nonce) {
                    capabilities = map
                    capabilitiesReceivedAt = android.os.SystemClock.elapsedRealtime()
                    connectionText = "Your watch is connected. You’re ready to send."
                    finding = false
                    render()
                }
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(if (Design.isDark(this)) R.style.Theme_Pakkit_Dark else R.style.Theme_Pakkit_Light)
        super.onCreate(savedInstanceState)
        store = Store(this)
        store.clean()
        page = savedInstanceState?.getInt("page") ?: 0
        render()
        savedInstanceState?.getInt("scroll")?.let { y -> scroll.post { scroll.scrollTo(0, y) } }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("page", page)
        outState.putInt("scroll", scroll.scrollY)
        super.onSaveInstanceState(outState)
    }
    override fun onResume() {
        super.onResume()
        store.prefs.registerOnSharedPreferenceChangeListener(this)
        Wearable.getDataClient(this).addListener(listener).addOnFailureListener {
            connectionText = "Google Play services is unavailable. Reopen Pakkit after it’s available."
            render()
        }
        discover()
    }
    override fun onPause() {
        store.prefs.unregisterOnSharedPreferenceChangeListener(this)
        Wearable.getDataClient(this).removeListener(listener)
        super.onPause()
    }
    override fun onDestroy() { appearanceDialog?.dismiss(); main.removeCallbacksAndMessages(null); io.shutdown(); super.onDestroy() }
    override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
        if (key == "outgoing" || key == "history" || key == null) {
            main.removeCallbacks(redraw)
            main.postDelayed(redraw, 32)
        }
    }
    private fun dp(n: Int) = Design.dp(this, n)
    private fun text(value: String, size: Float = 16f, color: Int = colors.text, bold: Boolean = false) = Design.text(this, value, size, color, bold)
    private fun add(v: View, parent: LinearLayout = content, top: Int = 0) {
        parent.addView(v, LinearLayout.LayoutParams(-1, v.layoutParams?.height ?: -2).apply { topMargin = dp(top) })
    }
    private fun card(color: Int = colors.surface) = Design.column(this).apply {
        setPadding(dp(20), dp(20), dp(20), dp(20))
        background = Design.shape(color, dp(26).toFloat(), colors.border)
    }
    private fun action(label: String, primary: Boolean = true, callback: () -> Unit) = Design.button(this, label, primary, callback)
    private fun icon(glyph: Glyph, tint: Int = colors.accent, size: Int = 24) = ImageView(this).apply {
        setImageDrawable(GlyphDrawable(glyph, tint))
        layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private fun heading(value: String, caption: String) {
        add(text(value, 38f, bold = true).apply { id = R.id.page_heading; letterSpacing = -.045f }, top = 26)
        add(text(caption, 15f, colors.secondary), top = 10)
    }
    private fun render(animate: Boolean = false) {
        if (isDestroyed || isFinishing) return
        val y = if (::scroll.isInitialized && renderedPage == page) scroll.scrollY else 0
        renderedPage = page
        val root = Design.column(this).apply { background = AuroraDrawable(colors) }
        root.setOnApplyWindowInsetsListener { v, insets ->
            val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false; isVerticalScrollBarEnabled = false }
        val frame = FrameLayout(this)
        content = Design.column(this).apply { setPadding(dp(20), dp(18), dp(20), dp(24)) }
        frame.addView(content, FrameLayout.LayoutParams(minOf(resources.displayMetrics.widthPixels, dp(640)), -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        scroll.addView(frame)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        header()
        when (page) { 0 -> transferPage(); 1 -> activityPage(); else -> guidePage() }
        root.addView(navigation())
        setContentView(root)
        root.requestApplyInsets()
        scroll.post { scroll.scrollTo(0, y) }
        if (animate) Design.entrance(content)
    }
    private fun header() {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(ImageView(this).apply {
            setImageResource(meghdad.pakkit.core.R.drawable.pakkit_icon)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginStart = -dp(8) })
        row.addView(text("Pakkit", 27f, bold = true).apply { letterSpacing = -.05f }, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(ImageButton(this).apply {
            id = R.id.appearance
            setImageDrawable(GlyphDrawable(Glyph.APPEARANCE, colors.text))
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x227150EF), Design.shape(colors.surface, dp(18).toFloat(), colors.border), null)
            contentDescription = "Appearance: ${Design.themeMode(this@MainActivity)}. Change theme"
            setOnClickListener { appearance() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        add(row)
    }
    private fun navigation(): View {
        val frame = FrameLayout(this).apply { background = Design.shape(colors.surface, 0f, colors.border) }
        val row = LinearLayout(this).apply { setPadding(dp(16), dp(8), dp(16), dp(8)) }
        val labels = listOf("Transfer", "Activity", "Guide")
        val glyphs = listOf(Glyph.TRANSFER, Glyph.HISTORY, Glyph.GUIDE)
        val ids = listOf(R.id.nav_transfer, R.id.nav_activity, R.id.nav_guide)
        labels.forEachIndexed { index, label ->
            row.addView(action(label, false) { if (page != index) { page = index; render(animate = true) } }.apply {
                id = ids[index]
                isSelected = page == index
                textSize = 12f
                minHeight = dp(60)
                setPadding(dp(8), dp(8), dp(8), dp(8))
                background = android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x227150EF), Design.shape(if (isSelected) colors.accentSoft else Color.TRANSPARENT, dp(18).toFloat()), null)
                setTextColor(if (isSelected) colors.accent else colors.secondary)
                val glyph = GlyphDrawable(glyphs[index], if (isSelected) colors.accent else colors.secondary).apply { setBounds(0, 0, dp(21), dp(21)) }
                setCompoundDrawables(null, glyph, null, null)
                compoundDrawablePadding = dp(5)
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(5) })
        }
        frame.addView(row, FrameLayout.LayoutParams(minOf(resources.displayMetrics.widthPixels, dp(640)), -2, Gravity.CENTER))
        return frame
    }
    private fun appearance() {
        val dialog = Dialog(this)
        appearanceDialog = dialog
        val sheet = Design.column(this).apply { setPadding(dp(24), dp(26), dp(24), dp(20)) }
        add(text("Make it yours.", 25f, bold = true), sheet)
        add(text("Choose your atmosphere.", 14f, colors.secondary), sheet, 8)
        listOf("system" to "Follow device theme", "light" to "Lavender light", "dark" to "Midnight dark").forEach { (mode, label) ->
            val selected = Design.themeMode(this) == mode
            add(action(label, false) {
                Design.setThemeMode(this, mode)
                dialog.dismiss()
                recreate()
            }.apply {
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                isSelected = selected
                if (selected) {
                    background = Design.shape(colors.accentSoft, dp(18).toFloat(), colors.accent)
                    setTextColor(colors.accent)
                    setCompoundDrawablesRelative(null, null, GlyphDrawable(Glyph.CHECK, colors.accent).apply { setBounds(0, 0, dp(20), dp(20)) }, null)
                }
            }, sheet, 14)
        }
        add(action("Close", false) { dialog.dismiss() }, sheet, 20)
        dialog.setContentView(ScrollView(this).apply { addView(sheet) })
        dialog.window?.setBackgroundDrawable(Design.shape(colors.surface, dp(28).toFloat()))
        dialog.show()
        dialog.window?.setLayout(minOf(resources.displayMetrics.widthPixels - dp(40), dp(420)), -2)
    }

    private fun transferPage() {
        val job = store.read("outgoing")
        if (job != null) {
            heading("A little closer.", getString(meghdad.pakkit.core.R.string.brand_secondary_tagline))
            fileCard(job)
            connectionCard(compact = true)
        } else {
            heading(getString(meghdad.pakkit.core.R.string.brand_tagline), "Your next favorite app. A little closer.")
            connectionCard()
            val box = card()
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            row.addView(icon(Glyph.FILE, size = 30))
            row.addView(Design.column(this).apply {
                add(text("A new wrist essential.", 19f, bold = true), this)
                add(text("Single APK · up to 50 MB", 13f, colors.secondary), this, 6)
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(14) })
            add(row, box)
            add(action(if (importing) "Reading your APK…" else "Choose an APK") { pick() }.apply { isEnabled = !importing }, box, 18)
            add(box, top = 16)
        }
        message?.let { notice("A small detour", it, warning = true) }
        add(text(getString(meghdad.pakkit.core.R.string.brand_secondary_tagline), 13f, colors.secondary, true).apply { gravity = Gravity.CENTER }, top = 24)
        add(text("Only send apps you trust. You approve installation on your watch.", 12f, colors.secondary).apply { gravity = Gravity.CENTER }, top = 8)
    }
    private fun connectionCard(compact: Boolean = false) {
        val box = card()
        if (!compact) box.background = Design.gradient(if (colors.dark) intArrayOf(0xFF24203E.toInt(), 0xFF112A31.toInt()) else intArrayOf(0xFFF0E9FF.toInt(), 0xFFE2F7F6.toInt()), dp(26).toFloat())
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(icon(Glyph.WATCH, if (capabilities != null) colors.mint else colors.accent, 20))
        row.addView(text(if (capabilities != null) "Connected" else if (finding) "Finding your watch" else "Your watch", 12f, colors.secondary, true), LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(8) })
        row.addView(text("WEAR OS", 10f, colors.secondary, true).apply { letterSpacing = .12f })
        add(row, box)
        if (!compact) add(ParcelSceneView(this).apply { active = finding; layoutParams = LinearLayout.LayoutParams(-1, dp(174)) }, box, 4)
        add(text(if (finding) "Making the introduction…" else watch?.displayName ?: "Let’s find your watch.", if (compact) 17f else 20f, bold = true), box, if (compact) 14 else 0)
        add(text(connectionText, 13f, colors.secondary), box, 8)
        add(action(if (finding) "Looking for Pakkit…" else if (capabilities != null) "Refresh connection" else "Connect a watch", false) { discover() }.apply { isEnabled = !finding }, box, 16)
        add(box, top = 20)
        capabilities?.let { cap ->
            if (!supported(cap)) notice("A different route", "This watch’s software does not expose the approval controls Pakkit needs. Install compatible apps through Google Play on the watch.", true)
            else if (!cap.getBoolean("allowed")) notice("One permission, on your watch", "Open Pakkit on your watch and tap “Allow installs”. Then refresh the connection here.")
        }
    }
    private fun notice(title: String, description: String, warning: Boolean = false) {
        val box = card(if (warning) colors.errorSoft else colors.accentSoft)
        val tint = if (warning) colors.error else colors.accent
        add(text(title, 16f, tint, true), box)
        add(text(description, 14f, if (warning) colors.error else colors.text), box, 8)
        add(box, top = 16)
    }
    private fun fileCard(job: JSONObject) {
        val apk = Apk.from(job.getJSONObject("apk"))
        val state = job.optString("state", "draft")
        val box = card()
        val success = state == "installed"
        val failed = state == "failed"
        val title = when (state) {
            "draft" -> "Packed and ready"
            "sending" -> "Preparing your parcel"
            "syncing" -> "On its way"
            "receiving" -> "A final check, on your watch"
            "ready" -> "Ready on your wrist"
            "confirm" -> "Over to you"
            "installing" -> "With the system installer"
            "installed" -> "Right where it belongs."
            "declined" -> "Dismissed on your watch"
            else -> "A small detour"
        }
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(icon(if (success) Glyph.CHECK else if (failed) Glyph.ALERT else Glyph.FILE, if (success) colors.mint else if (failed) colors.error else colors.accent, 28))
        row.addView(Design.badge(this, if (success) "Installed" else if (failed) "Needs attention" else "Your APK", positive = success, warning = failed), LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(12) })
        add(row, box)
        add(text(title, 23f, bold = true), box, 18)
        add(text(apk.label, 19f, bold = true), box, 18)
        add(text("${apk.version} · ${"%.1f".format(apk.bytes / 1048576.0)} MB", 13f, colors.secondary), box, 6)
        add(text(apk.packageName, 12f, colors.secondary), box, 6)
        if (state == "draft") {
            add(text(if (apk.wear) "Watch support declared. Hardware is checked before sending." else "Watch support is unverified. This app may not fit or work on a round screen.", 13f, if (apk.wear) colors.secondary else colors.error), box, 16)
            add(text("Package details are readable. This is not a malware scan or a guarantee of compatibility.", 12f, colors.secondary), box, 10)
            add(action("Send to my watch") { send(job) }.apply { isEnabled = capabilities?.let { supported(it) && it.getBoolean("allowed") } == true }, box, 20)
            add(action("Choose another file", false) { discard(job); pick() }, box, 8)
        } else {
            add(text(job.optString("detail"), 14f, colors.secondary), box, 16)
            transferSteps(state, box)
            if (state in setOf("installed", "failed", "declined")) add(action("Choose another APK") { discard(job); pick() }, box, 20)
            else {
                if (state in setOf("sending", "syncing", "receiving")) add(Design.progress(this), box, 14)
                add(text("Open Pakkit on your watch to continue. Brief disconnections are handled by syncing. Files expire 24 hours after selection.", 12f, colors.secondary), box, 14)
                add(action("Transfer help", false) { page = 2; render(animate = true) }, box, 12)
            }
        }
        add(box, top = 22)
    }
    private fun transferSteps(state: String, parent: LinearLayout) {
        val current = when (state) { "sending", "syncing", "receiving" -> 1; "ready", "confirm", "installing" -> 2; "installed" -> 3; else -> 0 }
        val row = LinearLayout(this)
        listOf("Choose", "Send", "Approve", "Wear").forEachIndexed { index, label ->
            row.addView(Design.column(this).apply {
                gravity = Gravity.CENTER
                if (index < current) addView(icon(Glyph.CHECK, colors.accent, 18))
                else addView(text("0${index + 1}", 13f, if (index == current) colors.accent else colors.secondary, true).apply { gravity = Gravity.CENTER })
                addView(text(label, 11f, if (index <= current) colors.text else colors.secondary).apply { gravity = Gravity.CENTER; setPadding(0, dp(6), 0, 0) })
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        add(row, parent, 22)
    }
    private fun activityPage() {
        heading("Little journeys.", "Your last 20 transfers, kept on this phone.")
        val entries = JSONArray(store.prefs.getString("history", "[]"))
        if (entries.length() == 0) {
            add(ParcelSceneView(this).apply { layoutParams = LinearLayout.LayoutParams(-1, dp(170)) }, top = 24)
            add(text("The first one is yours.", 24f, bold = true).apply { gravity = Gravity.CENTER }, top = 18)
            add(text("Send an APK and follow its journey here, from your pocket to your wrist.", 15f, colors.secondary).apply { gravity = Gravity.CENTER }, top = 12)
            add(action("Choose your first APK") { page = 0; render(animate = true); pick() }, top = 24)
        } else {
            for (i in 0 until entries.length()) {
                val entry = entries.getJSONObject(i)
                val box = card()
                val state = entry.optString("state")
                add(Design.badge(this, when (state) { "installed" -> "Installed"; "failed" -> "Needs attention"; "declined" -> "Dismissed"; else -> "In progress" }, state == "installed", state == "failed"), box)
                add(text(entry.getString("label"), 20f, bold = true), box, 14)
                add(text(entry.getString("detail"), 14f, colors.secondary), box, 8)
                add(text(android.text.format.DateFormat.format("MMM d · HH:mm", entry.getLong("time")).toString(), 12f, colors.secondary), box, 12)
                add(box, top = 16)
            }
            add(action("Clear activity", false) { store.prefs.edit().remove("history").apply() }, top = 20)
        }
    }
    private fun guidePage() {
        heading("Good to know.", "A little bridge. You’re always in control.")
        val sections = listOf(
            "Make the introduction" to "Pair your watch with its normal companion app, then install Pakkit from Google Play on both devices. Open Pakkit on your watch and allow installs if its software offers that setting.",
            "Pack it. Send it." to "Choose a complete APK from a source you trust. Pakkit checks readable signing metadata, file size, Android version and processor support. Split APKs, APKM, XAPK and app bundles aren’t supported.",
            "Wear it. With your approval." to "When the APK is ready, review it in Pakkit on your watch and tap Install. Follow the system’s approval screen. Receiving a file never installs it automatically.",
            "Every watch is different" to "Some watch firmware does not offer external installation controls. Pakkit cannot enable them. Google Play on the watch is the supported alternative. Phone apps may also be difficult to use on a round screen.",
            "Give your parcel a moment" to "Keep the devices charged and connected. Google Play services manages syncing; a byte percentage isn’t available. Files expire 24 hours after selection. If a connection is lost, reopen both apps and refresh.",
            "Your files, thoughtfully handled" to "No Pakkit account, ads or analytics. APKs are stored privately and shared using Google Play services. The Data Layer may route encrypted data through Google servers. Temporary APKs are removed after completion or expiry. You can clear activity here.",
            "Trust comes first" to "Pakkit does not certify an APK’s safety. System protections, developer verification, device policy or Play Protect may block installation. Pakkit respects those decisions and does not offer debugging or security bypasses."
        )
        sections.forEachIndexed { index, (title, body) ->
            val box = card()
            add(text("0${index + 1}", 12f, colors.accent, true), box)
            add(text(title, 19f, bold = true), box, 12)
            add(text(body, 14f, colors.secondary), box, 10)
            add(box, top = 16)
        }
        add(action("Open Google Play", false) { openPlay() }, top = 20)
        add(text(getString(meghdad.pakkit.core.R.string.brand_secondary_tagline), 14f, colors.accent, true).apply { gravity = Gravity.CENTER }, top = 28)
        add(text("Pakkit 0.1 · Device validation build", 12f, colors.secondary).apply { gravity = Gravity.CENTER }, top = 10)
    }
    private fun supported(cap: DataMap) = cap.getBoolean("installer") && (cap.getBoolean("settings") || cap.getBoolean("allowed"))
    private fun discover() {
        if(finding) return
        finding=true; capabilities=null; connectionText="Looking for Pakkit on nearby paired watches."; render()
        Wearable.getCapabilityClient(this).getCapability(Link.CAPABILITY,CapabilityClient.FILTER_REACHABLE)
            .addOnSuccessListener { info ->
                if (isDestroyed || isFinishing) return@addOnSuccessListener
                val nodes=info.nodes.sortedBy { it.displayName }
                if(nodes.isEmpty()) { finding=false; watch=null; connectionText="No watch found. Install and open Pakkit on your paired watch, then try again."; render() }
                else if(nodes.size==1) probe(nodes.first())
                else AlertDialog.Builder(this).setTitle("Choose your watch").setItems(nodes.map { it.displayName }.toTypedArray()) { _,i -> probe(nodes[i]) }
                    .setOnCancelListener { finding=false; render() }.show()
            }.addOnFailureListener { finding=false; connectionText="Couldn’t reach your watch. Check Google Play services and the companion connection."; render() }
    }
    private fun probe(node: Node) {
        watch=node; nonce=UUID.randomUUID().toString(); val request=nonce
        Wearable.getMessageClient(this).sendMessage(node.id,"/pakkit/probe",nonce.toByteArray()).addOnFailureListener {
            if(nonce==request) { finding=false; connectionText="Watch connection interrupted. Reopen Pakkit on your watch and retry."; render() }
        }
        main.postDelayed({ if(nonce==request && finding) { finding=false; connectionText="Your watch hasn’t replied. Open Pakkit there, then try again."; render() } },15000)
    }
    @Suppress("DEPRECATION")
    private fun pick() {
        message=null
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type="application/vnd.android.package-archive"; addCategory(Intent.CATEGORY_OPENABLE) },41)
    }
    @Deprecated("Platform result bridge")
    override fun onActivityResult(requestCode: Int,resultCode: Int,data: Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode!=41 || resultCode!=RESULT_OK) return
        val uri=data?.data ?: return
        importing=true; render()
        io.execute {
            val id=UUID.randomUUID().toString(); val part=File(store.directory,"$id.part")
            try {
                require(Packages.free(this)>TransferRules.MAX_BYTES+20*1024*1024) { "Free some storage on your phone first." }
                val hash=contentResolver.openInputStream(uri)?.use { input -> part.outputStream().use { TransferRules.copyBounded(input,it).second } } ?: error("Could not read this file.")
                val apk=Packages.inspect(this,part,hash)
                require(part.renameTo(store.file(id))) { "Could not save this file." }
                store.write("outgoing",JSONObject().put("id",id).put("apk",apk.json()).put("created",System.currentTimeMillis()).put("state","draft"))
                WorkManager.getInstance(this).enqueueUniqueWork("expire-$id",ExistingWorkPolicy.KEEP,
                    OneTimeWorkRequestBuilder<ExpireWorker>().setInitialDelay(24,TimeUnit.HOURS).setInputData(workDataOf("id" to id)).build())
            } catch(e: Exception) { part.delete(); main.post { message=e.message ?: "Couldn’t read this APK." } }
            main.post { importing=false; if(!isDestroyed) render() }
        }
    }
    private fun send(job: JSONObject) {
        val cap=capabilities ?: return
        val node=watch ?: return
        try {
            require(android.os.SystemClock.elapsedRealtime()-capabilitiesReceivedAt < 120000) { "Refresh your watch connection before sending." }
            require(supported(cap) && cap.getBoolean("allowed")) { "Allow installations in Pakkit on your watch first." }
            val apk=Apk.from(job.getJSONObject("apk"))
            Packages.checkForWatch(apk,cap.getInt("sdk"),cap.getStringArrayList("abis").orEmpty().toSet(),cap.getLong("free"))
            require(TransferRules.fresh(job.optLong("created"),System.currentTimeMillis())) { "This file expired. Choose it again." }
            val sendNow = {
                val now=job.getLong("created")
                job.put("target",node.id).put("created",now).put("state","sending").put("offer",Uri.Builder().scheme("wear").authority("*").path("/pakkit/offer/${job.getString("id")}").build().toString())
                    .put("detail","Preparing your file for Google Play services.")
                store.write("outgoing",job)
                store.history(job.getString("id"),apk.label,"sending","Preparing your file for transfer.")
                WorkManager.getInstance(this).enqueueUniqueWork("send-${job.getString("id")}",ExistingWorkPolicy.KEEP,
                    OneTimeWorkRequestBuilder<SendWorker>().setInputData(workDataOf("id" to job.getString("id"))).build())
                render()
            }
            AlertDialog.Builder(this).setTitle("Send ${apk.label}?")
                .setMessage("Send this APK to ${node.displayName} using Google Play services. Only continue if you trust its source.${if(!apk.wear) "\n\nThis app does not declare watch support. It may not fit or work on your watch." else ""}\n\nYou’ll review and approve installation on the watch.")
                .setNegativeButton("Keep here",null).setPositiveButton("Send") { _,_ -> sendNow() }.show()
        } catch(e: Exception) { message=e.message; render() }
    }
    private fun discard(job: JSONObject) { store.file(job.getString("id")).delete(); store.clear("outgoing"); render() }
    private fun openPlay() { runCatching { startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://play.google.com/store/search?q=Wear%20OS&c=apps"))) }.onFailure { message="Open Google Play on your watch and search for compatible apps."; render() } }
}
