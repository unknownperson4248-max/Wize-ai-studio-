package app.nova.assistant

import android.content.Context
import android.os.Handler
import android.os.Looper
import app.nova.assistant.data.TaskStatus
import app.nova.assistant.tasks.TaskRecorder
import java.util.concurrent.Executors

/** What JARVIS is doing right now: shown by the visualizer and by the bubble's face. */
enum class Mood { IDLE, LISTENING, THINKING, SPEAKING }

/**
 * The conversation loop, shared by the app screen and the floating bubble:
 * send what the user said, show and speak the reply, run the phone action the AI asks for,
 * send its result back, and repeat until the AI answers without an action.
 * Every request is recorded as a task with its steps; every phone action goes to the action log.
 */
class Brain(context: Context, private val ui: Ui, source: String) {

    interface Ui {
        fun mood(m: Mood)
        fun status(text: String)
        fun reply(text: String)
        fun note(text: String)
        fun confirm(question: String, answer: (Boolean) -> Unit)
        fun permission(permission: String, answer: (Boolean) -> Unit)
        /** Conversation mode: JARVIS finished answering a spoken question, so listen for the follow-up. */
        fun listenAgain() {}
    }

    val prefs = Prefs(context)
    val api = Api(prefs)
    private val actions = Actions(context)
    private val recorder = TaskRecorder(context, source)
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    var busy = false
        private set
    private var gen = 0                 // bumped by stop(): late results of a stopped task are ignored
    private var task: String? = null    // current task id
    private var lastReply = ""
    private var lastActionFailed = false
    private var byVoice = false          // the current request was spoken (not typed)
    private var followUp = false         // listen again once JARVIS has finished speaking
    private var narratedLook = false     // "let me have a look" is said once per task
    private val voice = Voice(context, prefs) { speaking ->
        if (speaking) ui.mood(Mood.SPEAKING)
        else if (!busy) {
            ui.mood(Mood.IDLE)
            if (followUp) { followUp = false; ui.listenAgain() }
        }
    }

    /** Voice engine description for Settings. */
    val voiceInfo get() = voice.info
    val banglaVoiceMissing get() = voice.banglaMissing

    fun ask(text: String, spoken: Boolean = false) {
        if (busy) return
        voice.stop()
        task = recorder.start(text)
        lastReply = ""; lastActionFailed = false
        byVoice = spoken; followUp = false; narratedLook = false
        call("Thinking…", 0) { api.say(text) }
    }

    /** Speaks a short line about what JARVIS is doing ("Opening WhatsApp"). */
    private fun narrate(a: Api.Action) {
        if (!prefs.speakReplies) return
        if (a.name == "read_screen") { if (narratedLook) return; narratedLook = true }
        val style = when {
            prefs.voiceLang == "en-US" -> "en"
            prefs.banglaVoice -> "bn"
            else -> "banglish"
        }
        Narration.line(a.name, a.args.optString("app"), style)?.let { voice.say(it) }
    }

    /** Runs other server work (history, memory) on the same background thread. */
    fun background(job: () -> Unit) { io.execute(job) }

    /** Stops talking (interrupt the voice reply). */
    fun hush() { voice.stop() }

    /** Emergency stop: silence, drop the running task, and ignore anything still on its way back. */
    fun stop() {
        gen++
        followUp = false
        voice.stop()
        if (busy) {
            task?.let { recorder.step(it, "Stopped by the user", false); recorder.finish(it, TaskStatus.CANCELLED, lastReply, "Stopped by the user") }
            task = null
            busy = false
            ui.status("Stopped"); ui.mood(Mood.IDLE)
        }
    }

    fun say(text: String) { voice.say(text) }

    fun close() { voice.shutdown(); io.shutdownNow() }

    private fun call(label: String, depth: Int, f: () -> Api.Reply) {
        busy = true; ui.mood(Mood.THINKING); ui.status(label)
        val g = gen
        io.execute {
            try {
                val r = f()
                main.post { if (g == gen) handle(r, depth) }
            } catch (e: Exception) {
                main.post { if (g == gen) fail(e.message ?: "Something went wrong.") }
            }
        }
    }

    private fun finish() {
        task?.let { recorder.finish(it, Safety.finalStatus(false, null, lastActionFailed), lastReply) }
        task = null
        busy = false; ui.status(""); ui.mood(Mood.IDLE)
        // conversation mode: once the spoken answer ends, listen for the follow-up
        if (byVoice && prefs.conversation && prefs.speakReplies && lastReply.isNotBlank()) followUp = true
    }

    private fun fail(error: String) {
        task?.let { recorder.step(it, error, false); recorder.finish(it, TaskStatus.FAILED, lastReply, error) }
        task = null
        busy = false; ui.status(""); ui.mood(Mood.IDLE)
        ui.note(error)
    }

    private fun handle(r: Api.Reply, depth: Int) {
        val id = task
        if (r.text.isNotBlank() && r.text != "…") {
            lastReply = r.text
            id?.let { recorder.step(it, "JARVIS: " + r.text) }
            ui.reply(r.text)
            if (prefs.speakReplies) voice.say(r.text)
        }
        val a = r.actions.firstOrNull()
        if (a == null) { finish(); return }
        if (depth >= MAX_STEPS) { fail("Stopped after $MAX_STEPS steps. Tell me what to do next."); return }
        id?.let { recorder.step(it, "Wants to: ${a.name} ${Safety.summarizeArgs(a.args.toString())}") }
        if (a.confirm || actions.risky(a.name, a.args)) {
            id?.let { recorder.status(it, TaskStatus.WAITING_APPROVAL) }
            val g = gen
            ui.confirm(actions.describe(a.name, a.args)) { yes ->
                if (g != gen) return@confirm
                id?.let { recorder.status(it, TaskStatus.RUNNING); recorder.step(it, if (yes) "Approved by the user" else "Declined by the user", yes) }
                if (yes) run(a, depth) else report(a, "The user said no. Do not do it.", depth)
            }
        } else run(a, depth)
    }

    private fun run(a: Api.Action, depth: Int) {
        if (prefs.automationPaused) {
            report(a, "Failed: automation is paused by the user. Tell them they can resume it in JARVIS, Security.", depth)
            return
        }
        ui.status(actions.label(a.name))
        narrate(a)
        val g = gen
        val id = task
        io.execute {
            try {
                val app = NovaAccessibility.instance?.currentApp().orEmpty()
                val result = actions.run(a.name, a.args)
                val ok = !result.startsWith("Failed")
                if (id != null) {
                    recorder.action(id, a.name, Safety.summarizeArgs(a.args.toString()), result, ok, app)
                    recorder.step(id, (if (ok) "Done: " else "") + actions.short(a.name, result), ok)
                }
                main.post {
                    if (g != gen) return@post
                    lastActionFailed = !ok
                    ui.note("⚡ " + actions.short(a.name, result))
                    report(a, result, depth)
                }
            } catch (e: Actions.NeedsPermission) {
                main.post {
                    if (g != gen) return@post
                    ui.permission(e.permission) { ok ->
                        if (ok) run(a, depth) else report(a, "Failed: the user did not allow ${e.permission.substringAfterLast('.')}.", depth)
                    }
                }
            }
        }
    }

    private fun report(a: Api.Action, result: String, depth: Int) = call("…", depth + 1) { api.sendResult(a, result) }

    companion object {
        const val MAX_STEPS = 10
    }
}
