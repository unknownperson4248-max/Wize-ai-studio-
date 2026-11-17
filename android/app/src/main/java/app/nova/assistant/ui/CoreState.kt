package app.nova.assistant.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.nova.assistant.Mood
import app.nova.assistant.Prefs
import app.nova.assistant.data.JarvisDb
import java.util.concurrent.atomic.AtomicLong

private val ids = AtomicLong()

data class ChatMsg(val role: String, val text: String, val id: Long = ids.incrementAndGet())

/** An action waiting for the user's Yes or No. */
data class Approval(val question: String, val answer: (Boolean) -> Unit)

/** Everything the screens show. Changed only on the main thread. */
class CoreState {
    val messages = mutableStateListOf<ChatMsg>()
    var mood by mutableStateOf(Mood.IDLE)
    var status by mutableStateOf("")
    var busy by mutableStateOf(false)
    var listening by mutableStateOf(false)
    var level by mutableFloatStateOf(0f)
    var partial by mutableStateOf("")
    var approval by mutableStateOf<Approval?>(null)
    var showIntro by mutableStateOf(true)
    /** A screen to open, set from outside the UI (e.g. "core" when opened by the assistant button). */
    var goTo by mutableStateOf<String?>(null)

    // system status, refreshed on resume and every few seconds
    var paired by mutableStateOf(false)
    var micGranted by mutableStateOf(false)
    var contactsGranted by mutableStateOf(false)
    var controlOn by mutableStateOf(false)
    var batteryFree by mutableStateOf(false)
    var paused by mutableStateOf(false)
    var bubble by mutableStateOf(true)
    var foregroundApp by mutableStateOf("")
    var aiOnline by mutableStateOf<Boolean?>(null)   // null = checking
    var aiInfo by mutableStateOf("")

    // hands-free and voice
    var wakeOn by mutableStateOf(false)
    var wakeProgress by mutableStateOf<Float?>(null)   // model download, 0..1, null when not downloading
    var wakeMessage by mutableStateOf("")
    var conversation by mutableStateOf(true)
    var voiceInfo by mutableStateOf("")
    var banglaMissing by mutableStateOf(false)
}

/** What the screens can ask the app to do. Implemented by MainActivity. */
interface Controller {
    val state: CoreState
    val prefs: Prefs
    val db: JarvisDb

    fun send(text: String)
    fun toggleMic()
    fun hush()
    fun emergencyStop()
    fun setPaused(paused: Boolean)
    fun setBubble(on: Boolean)
    fun requestMic()
    fun requestContacts()
    fun openAccessibilitySettings()
    fun openAppInfo()
    fun turnOffControl()
    fun askBattery()
    fun checkAi()
    fun pair(code: String, name: String, done: (error: String?) -> Unit)
    fun unpair(done: () -> Unit)
    fun testVoice()
    fun setWakeWord(on: Boolean)
    fun setConversation(on: Boolean)
    fun installVoice()
    fun loadMemories(done: (List<Pair<Int, String>>) -> Unit)
    fun deleteMemory(id: Int, done: () -> Unit)
    fun clearChat()
    fun deleteTask(id: String)
    fun clearTasks()
    fun clearActions()
}
