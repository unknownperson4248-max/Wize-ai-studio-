package app.nova.assistant

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.RecognizerIntent
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity(), Brain.Ui, Ears.Listener {

    companion object {
        const val EXTRA_ASK = "ask"
        const val EXTRA_PERMISSION = "permission"
        private const val REQ_MIC = 1
        private const val REQ_ACTION = 2
        private const val REQ_OTHER = 3
        private const val REQ_VOICE = 7
    }

    private lateinit var prefs: Prefs
    private lateinit var brain: Brain
    private lateinit var ears: Ears
    private lateinit var orb: OrbView
    private lateinit var statusView: TextView
    private lateinit var setupChip: TextView
    private lateinit var list: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private lateinit var mic: TextView
    private val main = Handler(Looper.getMainLooper())
    private var listening = false
    private var permissionAnswer: ((Boolean) -> Unit)? = null

    private val cyan = Color.parseColor("#38E8FF")
    private val violet = Color.parseColor("#8B5CF6")
    private val ink = Color.parseColor("#EAF2FF")
    private val muted = Color.parseColor("#8A97AD")
    private val panel = Color.parseColor("#0E1420")

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()
    private fun round(color: Int, radius: Int, stroke: Int = 0) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat(); if (stroke != 0) setStroke(dp(1), stroke)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        brain = Brain(this, this)
        prefs = brain.prefs
        ears = Ears(this, this)
        val frame = FrameLayout(this)
        frame.addView(buildUi())
        setContentView(frame)
        val assist = intent?.action == Intent.ACTION_ASSIST
        val ready = {
            if (!prefs.paired) showSettings(true)
            else {
                loadHistory()
                if (assist) main.postDelayed({ startListening() }, 300) else handleExtras(intent)
            }
            updateChip()
        }
        if (assist || savedInstanceState != null) ready()
        else frame.addView(IntroView(this, autoClose = prefs.paired) { ready() })
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent?.action == Intent.ACTION_ASSIST && prefs.paired) startListening() else handleExtras(intent)
    }

    override fun onResume() {
        super.onResume()
        updateChip()
    }

    override fun onDestroy() {
        ears.stop(); brain.close()
        super.onDestroy()
    }

    private fun handleExtras(i: Intent?) {
        if (i == null) return
        i.getStringExtra(EXTRA_ASK)?.let { i.removeExtra(EXTRA_ASK); send(it) }
        i.getStringExtra(EXTRA_PERMISSION)?.let { i.removeExtra(EXTRA_PERMISSION); requestPermissions(arrayOf(it), REQ_OTHER) }
    }

    /* ---------------- layout ---------------- */

    private fun buildUi(): View {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; fitsSystemWindows = true; setPadding(dp(16), dp(8), dp(16), dp(12)) }

        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(TextView(this).apply {
            setText(R.string.app_name); setTextColor(ink); textSize = 22f; letterSpacing = 0.35f; typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(TextView(this).apply {
            text = "⚙"; textSize = 22f; setTextColor(muted); setPadding(dp(12), dp(6), dp(4), dp(6))
            contentDescription = "Settings"; setOnClickListener { showSettings(false) }
        })
        root.addView(top)

        orb = OrbView(this)
        root.addView(orb, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(220)))
        statusView = TextView(this).apply { gravity = Gravity.CENTER; setTextColor(muted); textSize = 13f; text = "Tap the mic and speak" }
        root.addView(statusView)
        setupChip = TextView(this).apply {
            text = "✦ Phone control is off. Tap to turn it on"; setTextColor(cyan); textSize = 13f; gravity = Gravity.CENTER
            setPadding(dp(14), dp(7), dp(14), dp(7)); background = round(Color.parseColor("#0F1E2A"), 16, Color.parseColor("#1E6E84"))
            visibility = View.GONE; setOnClickListener { showControlHelp() }
        }
        root.addView(setupChip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(8)
        })

        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), 0, dp(8)) }
        scroll = ScrollView(this).apply { addView(list); isFillViewport = true }
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        input = EditText(this).apply {
            hint = "Type or speak…"; setHintTextColor(muted); setTextColor(ink); textSize = 16f
            background = round(panel, 24, Color.parseColor("#22304A")); setPadding(dp(16), dp(12), dp(16), dp(12))
            imeOptions = EditorInfo.IME_ACTION_SEND; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setOnEditorActionListener { _, id, _ -> if (id == EditorInfo.IME_ACTION_SEND) { sendTyped(); true } else false }
        }
        bar.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(TextView(this).apply {
            text = "➤"; textSize = 20f; setTextColor(cyan); gravity = Gravity.CENTER; contentDescription = "Send"
            setPadding(dp(14), dp(8), dp(10), dp(8)); setOnClickListener { sendTyped() }
        })
        mic = TextView(this).apply {
            text = "🎤"; textSize = 22f; gravity = Gravity.CENTER; contentDescription = "Speak"
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(cyan, violet)).apply { shape = GradientDrawable.OVAL }
            setOnClickListener { if (listening) stopListening() else startListening() }
        }
        bar.addView(mic, LinearLayout.LayoutParams(dp(56), dp(56)))
        root.addView(bar)
        return root
    }

    private fun bubble(role: String, msg: String) {
        val mine = role == "user"
        val tv = TextView(this).apply {
            text = msg; textSize = 15.5f; setTextColor(if (role == "note") muted else ink)
            setPadding(dp(14), dp(10), dp(14), dp(10)); setTextIsSelectable(true)
            background = when (role) {
                "user" -> round(Color.parseColor("#123A4A"), 18, Color.parseColor("#1E6E84"))
                "note" -> round(Color.TRANSPARENT, 12, Color.parseColor("#22304A"))
                else -> round(panel, 18, Color.parseColor("#22304A"))
            }
            if (role == "note") textSize = 13f
        }
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = if (mine) Gravity.END else Gravity.START; topMargin = dp(6)
            if (mine) leftMargin = dp(48) else rightMargin = dp(48)
        }
        list.addView(tv, lp)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun updateChip() {
        if (::setupChip.isInitialized) setupChip.visibility = if (prefs.paired && NovaAccessibility.instance == null) View.VISIBLE else View.GONE
    }

    /* ---------------- conversation ---------------- */

    private fun loadHistory() {
        brain.background {
            try {
                val h = brain.api.history()
                main.post { list.removeAllViews(); h.forEach { bubble(it.first, it.second) } }
            } catch (e: Exception) {
                main.post { bubble("note", e.message ?: "Could not load the conversation.") }
            }
        }
    }

    private fun sendTyped() {
        val t = input.text.toString().trim()
        if (t.isEmpty()) return
        input.setText("")
        send(t)
    }

    private fun send(t: String) {
        if (!prefs.paired) { showSettings(true); return }
        if (brain.busy) { bubble("note", "One moment, I'm still working on the last thing."); return }
        bubble("user", t)
        brain.ask(t)
    }

    override fun mood(m: Mood) { orb.state = m }
    override fun status(text: String) { statusView.text = text }
    override fun reply(text: String) = bubble("assistant", text)
    override fun note(text: String) = bubble("note", text)

    override fun confirm(question: String, answer: (Boolean) -> Unit) {
        AlertDialog.Builder(this, R.style.NovaDialog)
            .setTitle("Confirm")
            .setMessage(question)
            .setPositiveButton("Yes") { _, _ -> answer(true) }
            .setNegativeButton("No") { _, _ -> answer(false) }
            .setCancelable(false)
            .show()
    }

    override fun permission(permission: String, answer: (Boolean) -> Unit) {
        permissionAnswer = answer
        requestPermissions(arrayOf(permission), REQ_ACTION)
    }

    /* ---------------- voice ---------------- */

    private fun startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC); return
        }
        brain.hush()
        listening = true; orb.state = Mood.LISTENING; mic.alpha = 0.6f; statusView.text = "Starting the mic…"
        ears.start(prefs.voiceLang)
    }

    private fun stopListening() {
        ears.stop()
        micOff()
        statusView.text = ""
    }

    private fun micOff() {
        listening = false; mic.alpha = 1f; input.setText("")
        if (!brain.busy) orb.state = Mood.IDLE
    }

    override fun onReady() { statusView.text = "Listening…" }
    override fun onLevel(level: Float) { orb.level = level }
    override fun onPartial(text: String) { input.setText(text) }
    override fun onHeard(text: String) { micOff(); send(text) }
    override fun onNothing() { micOff(); statusView.text = "Didn't catch that. Tap the mic again." }

    /** The phone's speech service refused: use the system voice dialog instead. */
    override fun onBroken(code: Int) {
        micOff(); statusView.text = ""
        try {
            startActivityForResult(Ears.dialogIntent(prefs.voiceLang, "Bolo…"), REQ_VOICE)
        } catch (e: ActivityNotFoundException) {
            bubble("note", "Speech recognition is not available on this phone (error $code). Install or update the Google app, then try again.")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_VOICE || resultCode != RESULT_OK) return
        val said = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim().orEmpty()
        if (said.isNotEmpty()) send(said)
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        val granted = results.isNotEmpty() && results[0] == PackageManager.PERMISSION_GRANTED
        when (code) {
            REQ_MIC -> if (granted) startListening() else bubble("note", "The microphone permission is needed to talk to me.")
            REQ_ACTION -> { val a = permissionAnswer; permissionAnswer = null; a?.invoke(granted) }
            REQ_OTHER -> bubble("note", if (granted) "Allowed. Ask me again." else "Not allowed.")
        }
    }

    /* ---------------- phone control setup ---------------- */

    private fun showControlHelp() {
        val on = NovaAccessibility.instance != null
        AlertDialog.Builder(this, R.style.NovaDialog)
            .setTitle(if (on) "Phone control is on ✓" else "Turn on phone control")
            .setMessage(
                (if (on) "NOVA can read the screen, tap, type and scroll for you, and the floating bubble is available.\n\n" else "") +
                    "Android calls this permission Accessibility. It lets NOVA see the screen and tap, type and scroll in other apps, and show the floating bubble.\n\n" +
                    "1. Tap Open settings.\n" +
                    "2. Find NOVA (sometimes under Downloaded apps or Installed services) and turn it on.\n" +
                    "3. If Android says \"Restricted setting\": tap App info, then the ⋮ menu at the top right, then \"Allow restricted settings\", and try step 2 again.\n\n" +
                    "NOVA only acts when you ask. Sending, paying, buying, deleting and posting always ask you first. It never types into password fields."
            )
            .setPositiveButton("Open settings") { _, _ -> openSettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            .setNeutralButton("App info") { _, _ -> openSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun openSettings(i: Intent) {
        try { startActivity(i) } catch (e: ActivityNotFoundException) { startActivity(Intent(Settings.ACTION_SETTINGS)) }
    }

    /** Some phones (Honor, Xiaomi, Oppo) stop background services to save battery; this asks to keep NOVA running. */
    private fun askBattery() {
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        } catch (e: Exception) {
            openSettings(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    /* ---------------- settings ---------------- */

    private fun showSettings(firstRun: Boolean) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        fun label(s: String) = TextView(this).apply { text = s; setTextColor(muted); textSize = 12f; setPadding(0, dp(12), 0, dp(2)) }
        fun action(s: String, onClick: () -> Unit) = TextView(this).apply {
            text = s; setTextColor(cyan); textSize = 15f; setPadding(0, dp(10), 0, dp(10)); setOnClickListener { onClick() }
        }
        val server = EditText(this).apply { setText(prefs.server); setSingleLine(); textSize = 14f }
        val code = EditText(this).apply { hint = "Pairing code"; setSingleLine(); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS }
        val name = EditText(this).apply { setText(Build.MODEL); setSingleLine() }
        val langs = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        val bn = RadioButton(this).apply { text = "বাংলা"; id = View.generateViewId() }
        val en = RadioButton(this).apply { text = "English"; id = View.generateViewId() }
        langs.addView(bn); langs.addView(en); langs.check(if (prefs.voiceLang == "en-US") en.id else bn.id)
        val speakBox = CheckBox(this).apply { text = "Speak replies aloud"; isChecked = prefs.speakReplies }
        val bubbleBox = CheckBox(this).apply { text = "Floating NOVA bubble over other apps"; isChecked = prefs.bubble }
        box.addView(label("Server")); box.addView(server)
        if (!prefs.paired || firstRun) { box.addView(label("Pairing code (from the setup message)")); box.addView(code); box.addView(label("This phone's name")); box.addView(name) }
        else box.addView(label("Paired ✓"))
        box.addView(label("Voice language")); box.addView(langs); box.addView(speakBox)
        box.addView(label("Phone control"))
        box.addView(action(if (NovaAccessibility.instance != null) "✓ Phone control is on" else "Turn on phone control →") { showControlHelp() })
        box.addView(bubbleBox)
        box.addView(action("Keep NOVA running in the background →") { askBattery() })

        val d = AlertDialog.Builder(this, R.style.NovaDialog)
            .setTitle(if (firstRun) "Connect ${getString(R.string.app_name)}" else "Settings")
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton(if (!prefs.paired || firstRun) "Pair" else "Save", null)
            .setCancelable(!firstRun)
        if (prefs.paired && !firstRun) {
            d.setNeutralButton("Memory & chat") { _, _ -> showMemory() }
            d.setNegativeButton("Close", null)
        }
        val dlg = d.create()
        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                prefs.server = server.text.toString().trim().ifEmpty { BuildConfig.DEFAULT_SERVER }
                prefs.voiceLang = if (langs.checkedRadioButtonId == en.id) "en-US" else "bn-BD"
                prefs.speakReplies = speakBox.isChecked
                prefs.bubble = bubbleBox.isChecked
                NovaAccessibility.instance?.refreshBubble()
                if (prefs.paired && !firstRun) { dlg.dismiss(); return@setOnClickListener }
                val c = code.text.toString().trim()
                if (c.isEmpty()) { code.error = "Enter the pairing code"; return@setOnClickListener }
                brain.background {
                    try {
                        val tok = brain.api.pair(c, name.text.toString().trim().ifEmpty { "Phone" })
                        prefs.token = tok
                        main.post {
                            dlg.dismiss(); updateChip()
                            bubble("note", "Connected. Try: \"torch jalao\", \"open YouTube\", \"set an alarm for 7\".")
                            loadHistory()
                        }
                    } catch (e: Exception) {
                        main.post { code.error = e.message }
                    }
                }
            }
        }
        dlg.show()
    }

    private fun showMemory() {
        brain.background {
            val mem = try { brain.api.memories() } catch (e: Exception) { emptyList() }
            main.post {
                val items = mem.map { it.second }.toTypedArray()
                AlertDialog.Builder(this, R.style.NovaDialog)
                    .setTitle(if (items.isEmpty()) "Nothing remembered yet" else "What I remember (tap to delete)")
                    .setItems(items) { _, i ->
                        val (id, t) = mem[i]
                        AlertDialog.Builder(this, R.style.NovaDialog).setMessage("Forget \"$t\"?")
                            .setPositiveButton("Forget") { _, _ -> brain.background { runCatching { brain.api.deleteMemory(id) }; main.post { showMemory() } } }
                            .setNegativeButton("Keep", null).show()
                    }
                    .setPositiveButton("Close", null)
                    .setNegativeButton("Clear chat") { _, _ ->
                        brain.background { runCatching { brain.api.clearHistory() }; main.post { list.removeAllViews(); bubble("note", "Chat cleared. Memories are kept.") } }
                    }
                    .show()
            }
        }
    }
}
