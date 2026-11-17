package app.nova.assistant

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.TileService
import android.widget.Toast
import app.nova.assistant.wake.WakeService

/**
 * JARVIS on/off ("sleep"). Off: the bubble disappears, "Hey Jarvis" stops and frees the mic, any running task stops,
 * and nothing listens again until the user turns JARVIS back on (Quick Settings tile, the app, or the bubble switch).
 */
object Power {

    fun awake(context: Context) = !Prefs(context).sleeping

    fun set(context: Context, awake: Boolean, toast: Boolean = false) {
        val prefs = Prefs(context)
        prefs.sleeping = !awake
        val control = NovaAccessibility.instance
        if (!awake) {
            control?.stopTask()
            WakeService.stop(context)
        } else if (prefs.wakeWord) {
            // a microphone service may only start while the user is looking at JARVIS; the app retries when opened
            try { WakeService.start(context) } catch (_: Exception) {}
        }
        control?.refreshBubble()
        try { TileService.requestListeningState(context, ComponentName(context, JarvisTile::class.java)) } catch (_: Exception) {}
        if (toast) Toast.makeText(
            context,
            if (awake) "JARVIS is on" else "JARVIS is off. Turn it on from the Quick Settings tile or the app.",
            Toast.LENGTH_LONG,
        ).show()
    }
}
