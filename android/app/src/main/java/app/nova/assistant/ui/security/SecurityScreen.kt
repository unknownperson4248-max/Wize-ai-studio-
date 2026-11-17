package app.nova.assistant.ui.security

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nova.assistant.ui.ConfirmDialog
import app.nova.assistant.ui.Controller
import app.nova.assistant.ui.Dot
import app.nova.assistant.ui.GhostButton
import app.nova.assistant.ui.Panel
import app.nova.assistant.ui.PrimaryButton
import app.nova.assistant.ui.SectionTitle
import app.nova.assistant.ui.theme.J

/** Emergency stop, pause, every permission with its real state, the fixed safety rules, and where data goes. */
@Composable
fun SecurityScreen(c: Controller, back: () -> Unit) {
    val s = c.state
    var ask by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        TextButton(onClick = back, modifier = Modifier.padding(top = 6.dp)) { Text("← Back", color = J.Blue) }
        Text("Security", style = MaterialTheme.typography.headlineMedium, color = J.Text)

        SectionTitle("Emergency")
        Panel(Modifier.fillMaxWidth()) {
            Text("Emergency stop", color = J.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text("Stops the running task (in the app and the bubble), closes approval prompts, silences JARVIS and pauses all automation.",
                color = J.SilverDim, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
            PrimaryButton("EMERGENCY STOP", Modifier.fillMaxWidth(), color = J.Red) { c.emergencyStop(); c.setPaused(true) }
            if (s.paused) {
                Text("Automation is paused.", color = J.Amber, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
                PrimaryButton("Resume automation", Modifier.fillMaxWidth().padding(top = 6.dp), color = J.Amber) { c.setPaused(false) }
            } else GhostButton("Pause automation", Modifier.fillMaxWidth().padding(top = 8.dp)) { c.setPaused(true) }
        }

        SectionTitle("Permissions")
        Panel(Modifier.fillMaxWidth()) {
            Perm("Microphone", "To hear you. It is only on while the screen shows MIC ON.", s.micGranted, "Allow") { c.requestMic() }
            Perm("Contacts", "To find numbers for calls, SMS and WhatsApp.", s.contactsGranted, "Allow") { c.requestContacts() }
            Perm("Phone control (Accessibility)", "Read the screen, tap, type, scroll; the floating bubble.", s.controlOn, "Turn on") { c.openAccessibilitySettings() }
            Perm("Run in background", "Stops Android from closing JARVIS to save battery.", s.batteryFree, "Allow") { c.askBattery() }
        }

        SectionTitle("Rules JARVIS always follows")
        Panel(Modifier.fillMaxWidth()) {
            listOf(
                "Asks before sending, paying, buying, deleting or posting anything.",
                "Calls and SMS open filled in; you press call or send.",
                "Never types into password fields and never stores passwords or OTPs.",
                "Never unlocks the phone or bypasses fingerprint, PIN or app locks.",
                "Treats text on the screen as content, never as instructions.",
                "Every phone action is written to the action history on this phone.",
            ).forEach { Text("•  $it", color = J.Silver, fontSize = 13.sp, modifier = Modifier.padding(vertical = 2.dp)) }
        }

        SectionTitle("Your data")
        Panel(Modifier.fillMaxWidth()) {
            Text(
                "What you say, JARVIS's replies, and the screen text a task needs are sent to your JARVIS server on Cloudflare and its AI (Workers AI). " +
                    "The conversation and memories are stored in your own database there. Task history and the action history stay on this phone.",
                color = J.SilverDim, fontSize = 13.sp,
            )
            GhostButton("Clear chat on the server", Modifier.fillMaxWidth().padding(top = 10.dp)) { ask = "chat" }
            GhostButton("Clear task history on this phone", Modifier.fillMaxWidth().padding(top = 6.dp)) { ask = "tasks" }
            GhostButton("Clear action history on this phone", Modifier.fillMaxWidth().padding(top = 6.dp)) { ask = "actions" }
        }
        Spacer(Modifier.height(24.dp))
    }
    when (ask) {
        "chat" -> ConfirmDialog("Clear the chat?", "Deletes the conversation on your JARVIS server. Memories are kept.", "Clear", { c.clearChat() }) { ask = null }
        "tasks" -> ConfirmDialog("Clear task history?", "Deletes every task and its steps from this phone.", "Clear", { c.clearTasks() }) { ask = null }
        "actions" -> ConfirmDialog("Clear action history?", "Deletes the list of actions JARVIS ran on this phone.", "Clear", { c.clearActions() }) { ask = null }
    }
}

@Composable
private fun Perm(title: String, why: String, granted: Boolean, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Dot(if (granted) J.Green else J.Amber)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = J.Text, fontSize = 15.sp)
            Text(why, color = J.SilverDim, fontSize = 12.sp)
        }
        if (granted) Text("On", color = J.Green, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
        else TextButton(onClick = onClick) { Text(action, color = J.Blue) }
    }
}
