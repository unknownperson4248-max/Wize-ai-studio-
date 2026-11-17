package app.nova.assistant

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/** What NOVA is doing right now: shown by the orb and by the bubble's face. */
enum class Mood { IDLE, LISTENING, THINKING, SPEAKING }

/**
 * The conversation loop, shared by the app screen and the floating bubble:
 * send what the user said, show and speak the reply, run the phone action the AI asks for,
 * send its result back, and repeat until the AI answers without an action.
 */
class Brain(context: Context, private val ui: Ui) {

    interface Ui {
        fun mood(m: Mood)
        fun status(text: String)
        fun reply(text: String)
        fun note(text: String)
        fun confirm(question: String, answer: (Boolean) -> Unit)
        fun permission(permission: String, answer: (Boolean) -> Unit)
    }

    val prefs = Prefs(context)
    val api = Api(prefs)
    private val actions = Actions(context)
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    var busy = false
        private set
    private val voice = Voice(context) { speaking -> if (speaking) ui.mood(Mood.SPEAKING) else if (!busy) ui.mood(Mood.IDLE) }

    fun ask(text: String) {
        if (busy) return
        voice.stop()
        call("Thinking…", 0) { api.say(text) }
    }

    /** Runs other server work (history, memory) on the same background thread. */
    fun background(task: () -> Unit) { io.execute(task) }

    fun hush() { voice.stop() }

    fun close() { voice.shutdown(); io.shutdownNow() }

    private fun call(label: String, depth: Int, f: () -> Api.Reply) {
        busy = true; ui.mood(Mood.THINKING); ui.status(label)
        io.execute {
            try {
                val r = f()
                main.post { handle(r, depth) }
            } catch (e: Exception) {
                main.post { done(); ui.note(e.message ?: "Something went wrong.") }
            }
        }
    }

    private fun done() { busy = false; ui.status(""); ui.mood(Mood.IDLE) }

    private fun handle(r: Api.Reply, depth: Int) {
        if (r.text.isNotBlank() && r.text != "…") { ui.reply(r.text); if (prefs.speakReplies) voice.say(r.text) }
        val a = r.actions.firstOrNull()
        if (a == null) { done(); return }
        if (depth >= MAX_STEPS) { done(); ui.note("Stopped after $MAX_STEPS steps. Tell me what to do next."); return }
        if (a.confirm || actions.risky(a.name, a.args)) {
            ui.confirm(actions.describe(a.name, a.args)) { yes ->
                if (yes) run(a, depth) else report(a, "The user said no. Do not do it.", depth)
            }
        } else run(a, depth)
    }

    private fun run(a: Api.Action, depth: Int) {
        ui.status(actions.label(a.name))
        io.execute {
            try {
                val result = actions.run(a.name, a.args)
                main.post { ui.note("⚡ " + actions.short(a.name, result)); report(a, result, depth) }
            } catch (e: Actions.NeedsPermission) {
                main.post {
                    ui.permission(e.permission) { ok ->
                        if (ok) run(a, depth) else report(a, "Failed: the user did not allow ${e.permission.substringAfterLast('.')}.", depth)
                    }
                }
            }
        }
    }

    private fun report(a: Api.Action, result: String, depth: Int) = call("…", depth + 1) { api.sendResult(a, result) }

    companion object {
        const val MAX_STEPS = 14
    }
}
