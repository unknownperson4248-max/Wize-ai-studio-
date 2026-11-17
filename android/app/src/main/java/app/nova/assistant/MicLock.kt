package app.nova.assistant

import android.os.Handler
import android.os.Looper

/**
 * Who is using the microphone or the speaker right now. While anyone holds it (speech recognition,
 * the listening card, JARVIS speaking), the "Hey Jarvis" listener lets go of the mic, so the two never
 * fight over it and JARVIS never wakes itself up by saying its own name. All changes happen on the main thread.
 */
object MicLock {
    private val main = Handler(Looper.getMainLooper())
    private val holders = mutableSetOf<String>()
    private val listeners = mutableListOf<(Boolean) -> Unit>()

    val busy: Boolean get() = holders.isNotEmpty()

    fun acquire(tag: String) {
        main.post {
            val was = holders.isNotEmpty()
            holders.add(tag)
            if (!was) listeners.toList().forEach { it(true) }
        }
    }

    fun release(tag: String) {
        main.post {
            if (holders.remove(tag) && holders.isEmpty()) listeners.toList().forEach { it(false) }
        }
    }

    fun listen(l: (Boolean) -> Unit) { main.post { listeners.add(l) } }
    fun unlisten(l: (Boolean) -> Unit) { main.post { listeners.remove(l) } }
}
