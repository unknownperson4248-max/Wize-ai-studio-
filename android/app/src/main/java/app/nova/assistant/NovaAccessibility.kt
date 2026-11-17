package app.nova.assistant

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

/**
 * "Phone control": turned on by the user in Android's Accessibility settings.
 * - Lets NOVA read the screen and tap, type, scroll and press Back/Home in other apps (only when asked).
 * - Hosts the floating NOVA bubble over every app: tap to talk, hold to open the app, drag to move.
 * Screen methods block (they wait for the screen to settle), so they run on Brain's background thread.
 */
class NovaAccessibility : AccessibilityService(), Brain.Ui {

    companion object {
        @Volatile
        var instance: NovaAccessibility? = null
            private set
    }

    private val main = Handler(Looper.getMainLooper())
    private lateinit var wm: WindowManager
    private lateinit var prefs: Prefs
    private var brain: Brain? = null
    private var face: AvatarView? = null
    private var faceLp: WindowManager.LayoutParams? = null
    private var balloon: TextView? = null
    private var balloonLp: WindowManager.LayoutParams? = null
    private var balloonIsReply = false
    private var confirmView: View? = null
    private val hideBalloon = Runnable { removeBalloon() }

    private val ink = Color.parseColor("#EAF2FF")
    private val muted = Color.parseColor("#8A97AD")
    private val cyan = Color.parseColor("#38E8FF")

    override fun onServiceConnected() {
        super.onServiceConnected()
        wm = getSystemService(WindowManager::class.java)
        prefs = Prefs(this)
        brain = Brain(this, this, "bubble")
        instance = this
        refreshBubble()
    }

    /* ---------------- game mode ---------------- */

    /** True while a game is on screen: the bubble hides and the microphone is left to the game. */
    @Volatile var inGame = false
        private set
    private val gameCache = HashMap<String, Boolean?>()   // null = not a normal app (keyboard, system panel): ignore

    private fun kindOf(pkg: String): Boolean? = gameCache.getOrPut(pkg) {
        try {
            if (packageManager.getLaunchIntentForPackage(pkg) == null) return@getOrPut null
            val info = packageManager.getApplicationInfo(pkg, 0)
            info.category == android.content.pm.ApplicationInfo.CATEGORY_GAME ||
                (info.flags and android.content.pm.ApplicationInfo.FLAG_IS_GAME) != 0 ||
                pkg in Safety.KNOWN_GAMES
        } catch (e: Exception) {
            null
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return
        val game = kindOf(pkg) ?: return
        if (game == inGame) return
        inGame = game
        if (game) {
            MicLock.acquire("game")          // "Hey Jarvis" lets go of the mic for the game's voice chat
            brain?.hush()
        } else MicLock.release("game")
        refreshBubble()
    }
    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        if (inGame) { inGame = false; MicLock.release("game") }
        main.post { removeAll() }
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        removeAll()
        brain?.close()
        super.onDestroy()
    }

    /** Emergency stop from the app: drop the bubble's running task and close any approval card. */
    fun stopTask() {
        main.post { brain?.stop(); removeConfirm(); removeBalloon() }
    }

    /** Switches phone control off from inside the app (the user asked to). */
    fun turnOff() {
        main.post { brain?.stop(); removeAll(); disableSelf() }
    }

    /** Name of the app on screen now (for the action log and the Phone Control screen). */
    fun currentApp(): String = rootInActiveWindow?.packageName?.toString()?.let { appLabel(it) }.orEmpty()

    /** Something the user said through the bubble. */
    fun ask(text: String, spoken: Boolean = true) {
        val b = brain ?: return
        if (b.busy) { showBalloon("One moment, I'm still working on the last thing.", true, 3000); return }
        b.ask(text, spoken)
    }

    /** Conversation mode: open the listening card again for the follow-up. */
    override fun listenAgain() {
        if (prefs.conversation && !prefs.sleeping && !inGame) listen()
    }

    /** Refuses screen actions in system settings, permission screens and JARVIS itself (see [Safety.isProtectedApp]). */
    private fun guard(root: AccessibilityNodeInfo): String? {
        val pkg = root.packageName?.toString().orEmpty()
        return if (Safety.isProtectedApp(pkg, packageName))
            "Failed: for safety I do not tap, type or scroll in ${appLabel(pkg)}. Ask the user to do it themselves."
        else null
    }

    /* ---------------- floating bubble ---------------- */

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()
    private val screenW get() = resources.displayMetrics.widthPixels
    private val screenH get() = resources.displayMetrics.heightPixels
    private val faceSize get() = dp(64)

    private fun params(w: Int, h: Int, touchable: Boolean) = WindowManager.LayoutParams(
        w, h, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            (if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
        PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.START }

    /** Shows or hides the bubble to match the setting. */
    fun refreshBubble() {
        main.post {
            val show = prefs.bubble && !prefs.sleeping && !(inGame && prefs.hideInGames)
            if (show) showBubble() else removeAll()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showBubble() {
        if (face != null) return
        val size = faceSize
        val v = AvatarView(this).apply { disc = true; alpha = 0.9f; contentDescription = "JARVIS. Tap to talk, hold to open the app, drag to the cross to turn off." }
        val lp = params(size, size, true).apply {
            x = if (prefs.bubbleX >= 0) prefs.bubbleX.coerceAtMost(screenW - size) else screenW - size - dp(6)
            y = if (prefs.bubbleY >= 0) prefs.bubbleY.coerceAtMost(screenH - size) else screenH / 3
        }
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var downAt = 0L
        var dragging = false
        v.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startX = lp.x; startY = lp.y
                    downAt = SystemClock.uptimeMillis(); dragging = false
                    v.animate().scaleX(0.88f).scaleY(0.88f).setDuration(90).start()
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) dragging = true
                    if (dragging) {
                        lp.x = (startX + dx).toInt()
                        lp.y = (startY + dy).toInt().coerceIn(0, screenH - size)
                        safeUpdate(v, lp); placeBalloon()
                        showDismiss(overDismiss(e.rawX, e.rawY))
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    val drop = dragging && overDismiss(e.rawX, e.rawY)
                    hideDismiss()
                    if (drop) Power.set(this, false, toast = true)
                    else if (dragging) snap(v, lp)
                    else if (e.actionMasked == MotionEvent.ACTION_UP) {
                        if (SystemClock.uptimeMillis() - downAt > 550) openApp() else listen()
                    }
                }
            }
            true
        }
        try { wm.addView(v, lp) } catch (e: Exception) { return }
        face = v
        faceLp = lp
    }

    /** After a drag the bubble glides to the nearest side of the screen. */
    private fun snap(v: View, lp: WindowManager.LayoutParams) {
        val size = faceSize
        val target = if (lp.x + size / 2 < screenW / 2) dp(4) else screenW - size - dp(4)
        ValueAnimator.ofInt(lp.x, target).apply {
            duration = 220
            addUpdateListener { lp.x = it.animatedValue as Int; safeUpdate(v, lp); placeBalloon() }
            start()
        }
        prefs.bubbleX = target
        prefs.bubbleY = lp.y
    }

    /* drag-to-✕: a round target near the bottom while the bubble is dragged */
    private var dismiss: TextView? = null

    private fun overDismiss(x: Float, y: Float): Boolean {
        val cx = screenW / 2f
        val cy = screenH - dp(110).toFloat()
        val r = dp(70).toFloat()
        return (x - cx) * (x - cx) + (y - cy) * (y - cy) < r * r
    }

    private fun showDismiss(hot: Boolean) {
        val d = dismiss ?: TextView(this).apply {
            text = "✕"; textSize = 22f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#CC1A1F2B")); setStroke(dp(2), Color.parseColor("#88FFFFFF")) }
        }.also { t ->
            val lp = params(dp(64), dp(64), false).apply { gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; y = dp(78) }
            try { wm.addView(t, lp); dismiss = t } catch (_: Exception) { return }
        }
        d.animate().scaleX(if (hot) 1.25f else 1f).scaleY(if (hot) 1.25f else 1f).setDuration(100).start()
        (d.background as? GradientDrawable)?.setColor(Color.parseColor(if (hot) "#E6FF4D5E" else "#CC1A1F2B"))
    }

    private fun hideDismiss() {
        dismiss?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        dismiss = null
    }

    private fun safeUpdate(v: View, lp: WindowManager.LayoutParams) {
        try { wm.updateViewLayout(v, lp) } catch (_: Exception) {}
    }

    private fun listen() {
        val b = brain ?: return
        if (b.busy) { showBalloon("One moment, I'm still working on the last thing.", true, 3000); return }
        b.hush()
        removeBalloon()
        startActivity(Intent(this, ListenActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION))
    }

    private fun openApp() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** A speech balloon next to the bubble. Replies stay for a few seconds; tap to close. */
    private fun showBalloon(text: String, reply: Boolean, ms: Long) {
        if (face == null) return
        var tv = balloon
        if (tv == null) {
            tv = TextView(this).apply {
                setTextColor(ink); textSize = 14f; maxWidth = (screenW * 0.68).toInt()
                setPadding(dp(14), dp(10), dp(14), dp(10))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#EE0E1420")); cornerRadius = dp(16).toFloat(); setStroke(dp(1), Color.parseColor("#5538E8FF"))
                }
                setOnClickListener { removeBalloon() }
            }
            val lp = params(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, true)
            balloon = tv
            balloonLp = lp
            placeBalloon()
            try { wm.addView(tv, lp) } catch (e: Exception) { balloon = null; balloonLp = null; return }
        }
        tv.text = text
        tv.setTextColor(if (reply) ink else muted)
        balloonIsReply = reply
        main.removeCallbacks(hideBalloon)
        if (ms > 0) main.postDelayed(hideBalloon, ms)
    }

    private fun placeBalloon() {
        val f = faceLp ?: return
        val lp = balloonLp ?: return
        val right = f.x + faceSize / 2 > screenW / 2
        lp.gravity = Gravity.TOP or (if (right) Gravity.END else Gravity.START)
        lp.x = if (right) screenW - f.x + dp(6) else f.x + faceSize + dp(6)
        lp.y = f.y + dp(6)
        val v = balloon
        if (v != null && v.isAttachedToWindow) safeUpdate(v, lp)
    }

    private fun removeBalloon() {
        main.removeCallbacks(hideBalloon)
        balloon?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        balloon = null
        balloonLp = null
        balloonIsReply = false
    }

    private fun removeConfirm() {
        confirmView?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        confirmView = null
    }

    private fun removeAll() {
        hideDismiss()
        removeBalloon()
        removeConfirm()
        face?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        face = null
        faceLp = null
    }

    /* ---------------- Brain.Ui: what the bubble shows ---------------- */

    override fun mood(m: Mood) {
        face?.state = m
        face?.alpha = if (m == Mood.IDLE) 0.9f else 1f
    }

    override fun status(text: String) {
        if (text.isBlank() || text == "…") { if (!balloonIsReply) removeBalloon(); return }
        if (!balloonIsReply || balloon == null) showBalloon(text, false, 0)
    }

    override fun reply(text: String) = showBalloon(text, true, (2500L + text.length * 70L).coerceIn(3500L, 14000L))

    override fun note(text: String) {
        if (!text.startsWith("⚡")) showBalloon(text, true, 6000)
    }

    override fun confirm(question: String, answer: (Boolean) -> Unit) {
        removeConfirm()
        var decided = false
        fun decide(yes: Boolean) {
            if (decided) return
            decided = true
            removeConfirm()
            answer(yes)
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(14))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F40E1420")); cornerRadius = dp(22).toFloat(); setStroke(dp(1), Color.parseColor("#8838E8FF"))
            }
        }
        box.addView(TextView(this).apply { text = "NOVA wants to:"; setTextColor(muted); textSize = 12f })
        box.addView(TextView(this).apply { text = question; setTextColor(ink); textSize = 16f; setPadding(0, dp(6), 0, dp(14)) })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END }
        fun button(label: String, yes: Boolean) = TextView(this).apply {
            text = label; textSize = 15f; setPadding(dp(24), dp(10), dp(24), dp(10))
            setTextColor(if (yes) Color.parseColor("#05070C") else ink)
            background = GradientDrawable().apply {
                setColor(if (yes) cyan else Color.TRANSPARENT); cornerRadius = dp(20).toFloat()
                if (!yes) setStroke(dp(1), Color.parseColor("#44EAF2FF"))
            }
            setOnClickListener { decide(yes) }
        }
        row.addView(button("No", false))
        row.addView(View(this), LinearLayout.LayoutParams(dp(10), 1))
        row.addView(button("Yes", true))
        box.addView(row)
        val lp = params((screenW * 0.9).toInt(), WindowManager.LayoutParams.WRAP_CONTENT, true).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(90)
        }
        try {
            wm.addView(box, lp)
            confirmView = box
            main.postDelayed({ decide(false) }, 60_000)
        } catch (e: Exception) {
            decide(false)
        }
    }

    override fun permission(permission: String, answer: (Boolean) -> Unit) {
        answer(false)
        showBalloon("I need a permission for that. Tap Allow in NOVA, then ask me again.", true, 7000)
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(MainActivity.EXTRA_PERMISSION, permission))
    }

    /* ---------------- screen control (called on a background thread) ---------------- */

    private fun appLabel(pkg: String): String = try {
        packageManager.getApplicationInfo(pkg, 0).loadLabel(packageManager).toString()
    } catch (e: Exception) {
        pkg
    }

    /** The visible screen as short lines: [tap] for things that can be tapped, [field] for text boxes. */
    fun read(): String {
        Thread.sleep(700)
        val root = rootInActiveWindow ?: return "Failed: cannot see the screen right now."
        val out = StringBuilder("App: ").append(appLabel(root.packageName?.toString().orEmpty())).append('\n')
        var count = 0
        var last = ""
        fun walk(n: AccessibilityNodeInfo?, tappableParent: Boolean, depth: Int) {
            if (n == null || count >= 90 || out.length > 3300 || depth > 45) return
            val tappable = n.isClickable || n.isLongClickable
            if (n.isVisibleToUser) {
                val label = (n.text ?: n.contentDescription ?: "").toString().replace('\n', ' ').trim().take(90)
                val line = when {
                    n.isEditable -> {
                        val hint = n.hintText?.toString()?.trim().orEmpty()
                        val value = if (n.isPassword) "•••" else if (n.isShowingHintText) "" else label
                        "[field] " + (if (hint.isNotEmpty()) "hint=\"$hint\" " else "") + "value=\"$value\""
                    }
                    label.isEmpty() -> ""
                    tappable || tappableParent -> "[tap] \"$label\""
                    else -> "\"$label\""
                }
                if (line.isNotEmpty() && line != last) { out.append(line).append('\n'); last = line; count++ }
            }
            for (i in 0 until n.childCount) walk(n.getChild(i), tappable || tappableParent, depth + 1)
        }
        walk(root, false, 0)
        if (count == 0) out.append("(nothing readable on the screen)")
        return out.toString()
    }

    /** Best visible match for a label: exact, then starts-with, then contains. */
    private fun find(root: AccessibilityNodeInfo, target: String): AccessibilityNodeInfo? {
        val want = target.trim().lowercase()
        if (want.isEmpty()) return null
        var best: AccessibilityNodeInfo? = null
        var bestScore = 0
        fun walk(n: AccessibilityNodeInfo?, depth: Int) {
            if (n == null || depth > 45 || bestScore == 3) return
            if (n.isVisibleToUser) {
                for (s in listOf(n.text, n.contentDescription, n.hintText)) {
                    val l = s?.toString()?.trim()?.lowercase() ?: continue
                    if (l.isEmpty()) continue
                    val score = when { l == want -> 3; l.startsWith(want) -> 2; l.contains(want) -> 1; else -> 0 }
                    if (score > bestScore) { bestScore = score; best = n }
                }
            }
            for (i in 0 until n.childCount) walk(n.getChild(i), depth + 1)
        }
        walk(root, 0)
        return best
    }

    private fun click(node: AccessibilityNodeInfo): Boolean {
        var c: AccessibilityNodeInfo? = node
        while (c != null && !c.isClickable) c = c.parent
        return c?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true || tapAt(node)
    }

    private fun tapAt(n: AccessibilityNodeInfo): Boolean {
        val r = Rect()
        n.getBoundsInScreen(r)
        if (r.isEmpty) return false
        val p = Path().apply { moveTo(r.exactCenterX(), r.exactCenterY()) }
        return dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p, 0, 60)).build(), null, null)
    }

    fun tap(target: String): String {
        if (target.isBlank()) return "Failed: nothing to tap."
        val root = rootInActiveWindow ?: return "Failed: cannot see the screen right now."
        guard(root)?.let { return it }
        val node = find(root, target) ?: return "Failed: \"$target\" is not on the screen. Read the screen again or scroll."
        val ok = click(node)
        Thread.sleep(1000)
        return if (ok) "Tapped \"$target\"." else "Failed: could not tap \"$target\"."
    }

    fun type(text: String, field: String): String {
        val root = rootInActiveWindow ?: return "Failed: cannot see the screen right now."
        guard(root)?.let { return it }
        val fields = mutableListOf<AccessibilityNodeInfo>()
        fun walk(n: AccessibilityNodeInfo?, depth: Int) {
            if (n == null || depth > 45) return
            if (n.isEditable && n.isVisibleToUser) fields.add(n)
            for (i in 0 until n.childCount) walk(n.getChild(i), depth + 1)
        }
        walk(root, 0)
        val want = field.trim().lowercase()
        val named = if (want.isEmpty()) null else fields.firstOrNull { f ->
            listOf(f.hintText, f.text, f.contentDescription, f.viewIdResourceName).any { it?.toString()?.lowercase()?.contains(want) == true }
        }
        val target = named ?: fields.firstOrNull { it.isFocused } ?: fields.firstOrNull() ?: return "Failed: no text box on the screen."
        if (target.isPassword) return "Failed: I do not type into password fields."
        target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
        val ok = target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        Thread.sleep(500)
        return if (ok) "Typed \"${text.take(80)}\"." else "Failed: could not type there."
    }

    fun scroll(direction: String): String {
        val root = rootInActiveWindow ?: return "Failed: cannot see the screen right now."
        guard(root)?.let { return it }
        var best: AccessibilityNodeInfo? = null
        var area = 0
        val r = Rect()
        fun walk(n: AccessibilityNodeInfo?, depth: Int) {
            if (n == null || depth > 45) return
            if (n.isScrollable && n.isVisibleToUser) {
                n.getBoundsInScreen(r)
                val a = r.width() * r.height()
                if (a > area) { area = a; best = n }
            }
            for (i in 0 until n.childCount) walk(n.getChild(i), depth + 1)
        }
        walk(root, 0)
        val d = direction.trim().lowercase()
        val up = d.startsWith("up") || d.startsWith("back") || d == "previous"
        val ok = best?.performAction(if (up) AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) == true || swipe(up)
        Thread.sleep(700)
        return if (ok) "Scrolled ${if (up) "up" else "down"}." else "Failed: nothing to scroll."
    }

    private fun swipe(up: Boolean): Boolean {
        val x = screenW / 2f
        val low = screenH * 0.7f
        val high = screenH * 0.3f
        val p = Path().apply { if (up) { moveTo(x, high); lineTo(x, low) } else { moveTo(x, low); lineTo(x, high) } }
        return dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p, 0, 300)).build(), null, null)
    }

    fun press(button: String): String {
        val action = when (button.trim().lowercase()) {
            "back" -> GLOBAL_ACTION_BACK
            "home" -> GLOBAL_ACTION_HOME
            "recents", "recent", "recent apps" -> GLOBAL_ACTION_RECENTS
            "notifications" -> GLOBAL_ACTION_NOTIFICATIONS
            "quick_settings", "quick settings" -> GLOBAL_ACTION_QUICK_SETTINGS
            "lock", "lock_screen" -> if (Build.VERSION.SDK_INT >= 28) GLOBAL_ACTION_LOCK_SCREEN else return "Failed: this Android version cannot lock the screen from an app."
            "screenshot" -> if (Build.VERSION.SDK_INT >= 28) GLOBAL_ACTION_TAKE_SCREENSHOT else return "Failed: not supported on this Android version."
            else -> return "Failed: unknown button \"$button\"."
        }
        val ok = performGlobalAction(action)
        Thread.sleep(700)
        return if (ok) "Pressed $button." else "Failed: could not press $button."
    }

    /** Waits for the WhatsApp chat to open and presses its send button. */
    fun pressSend(pkg: String): Boolean {
        repeat(14) {
            Thread.sleep(500)
            val root = rootInActiveWindow
            if (root != null && root.packageName?.toString() == pkg) {
                val node = root.findAccessibilityNodeInfosByViewId("$pkg:id/send").firstOrNull { it.isVisibleToUser } ?: find(root, "Send")
                if (node != null && click(node)) { Thread.sleep(600); return true }
            }
        }
        return false
    }
}
