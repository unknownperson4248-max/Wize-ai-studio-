@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.nova.assistant.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nova.assistant.BuildConfig
import app.nova.assistant.ui.ConfirmDialog
import app.nova.assistant.ui.Controller
import app.nova.assistant.ui.GhostButton
import app.nova.assistant.ui.Panel
import app.nova.assistant.ui.PrimaryButton
import app.nova.assistant.ui.SectionTitle
import app.nova.assistant.ui.SettingRow
import app.nova.assistant.ui.control.jSwitchColors
import app.nova.assistant.ui.theme.J

@Composable
fun jFieldColors() = OutlinedTextFieldDefaults.colors(focusedBorderColor = J.Blue, unfocusedBorderColor = J.Line, focusedTextColor = J.Text, unfocusedTextColor = J.Text, cursorColor = J.Blue)

/** Voice, connection, memory and about. */
@Composable
fun SettingsScreen(c: Controller, go: (String) -> Unit) {
    val p = c.prefs
    var lang by remember { mutableStateOf(p.voiceLang) }
    var speak by remember { mutableStateOf(p.speakReplies) }
    var rate by remember { mutableFloatStateOf(p.voiceRate) }
    var volume by remember { mutableFloatStateOf(p.voiceVolume) }
    var server by remember { mutableStateOf(p.server) }
    var memories by remember { mutableStateOf<List<Pair<Int, String>>?>(null) }
    var forget by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var askUnpair by remember { mutableStateOf(false) }
    var askClearChat by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, color = J.Text, modifier = Modifier.padding(top = 14.dp))

        SectionTitle("Hands-free")
        Panel(Modifier.fillMaxWidth()) {
            val s = c.state
            SettingRow("\"Hey Jarvis\"", "Say \"Hey Jarvis\" instead of tapping. Listens offline on this phone; a notification shows while it is on. Uses some battery.") {
                Switch(checked = s.wakeOn || (s.wakeProgress != null), onCheckedChange = { c.setWakeWord(it) }, enabled = s.wakeProgress == null, colors = jSwitchColors())
            }
            s.wakeProgress?.let { pr ->
                LinearProgressIndicator(progress = { pr }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), color = J.Blue, trackColor = J.Line)
            }
            if (s.wakeMessage.isNotEmpty()) Text(s.wakeMessage, color = J.SilverDim, fontSize = 12.sp)
            SettingRow("Keep the conversation going", "After JARVIS answers out loud, it listens for your reply. Stays quiet if you say nothing.") {
                Switch(checked = s.conversation, onCheckedChange = { c.setConversation(it) }, colors = jSwitchColors())
            }
        }

        SectionTitle("Voice")
        Panel(Modifier.fillMaxWidth()) {
            Text(c.state.voiceInfo.ifEmpty { "Voice engine starting…" }, color = if (c.state.banglaMissing) J.Amber else J.SilverDim, fontSize = 12.sp)
            if (c.state.banglaMissing) TextButton(onClick = { c.installVoice() }) { Text("Install the Bangla voice →", color = J.Blue) }
            Text("Recognition language", color = J.Text, fontSize = 15.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                listOf("bn-BD" to "বাংলা", "en-US" to "English").forEach { (code, label) ->
                    FilterChip(
                        selected = lang == code, onClick = { lang = code; p.voiceLang = code }, label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = J.BlueDeep, selectedLabelColor = J.Text, labelColor = J.SilverDim),
                    )
                }
            }
            SettingRow("Speak replies aloud") {
                Switch(checked = speak, onCheckedChange = { speak = it; p.speakReplies = it }, colors = jSwitchColors())
            }
            Text("Voice speed: ${"%.1f".format(rate)}×", color = J.Text, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
            Slider(value = rate, onValueChange = { rate = it }, onValueChangeFinished = { p.voiceRate = rate }, valueRange = 0.5f..2f,
                colors = SliderDefaults.colors(thumbColor = J.Blue, activeTrackColor = J.Blue, inactiveTrackColor = J.Line))
            Text("Voice volume: ${(volume * 100).toInt()}%", color = J.Text, fontSize = 14.sp)
            Slider(value = volume, onValueChange = { volume = it }, onValueChangeFinished = { p.voiceVolume = volume }, valueRange = 0f..1f,
                colors = SliderDefaults.colors(thumbColor = J.Blue, activeTrackColor = J.Blue, inactiveTrackColor = J.Line))
            GhostButton("Test voice", Modifier.fillMaxWidth()) { p.voiceRate = rate; p.voiceVolume = volume; c.testVoice() }
        }

        SectionTitle("Connection")
        Panel(Modifier.fillMaxWidth()) {
            OutlinedTextField(server, { server = it }, label = { Text("JARVIS server") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp), colors = jFieldColors())
            if (server.trim() != p.server) TextButton(onClick = { p.server = server.trim().ifEmpty { BuildConfig.DEFAULT_SERVER }; server = p.server; c.checkAi() }) { Text("Save server", color = J.Blue) }
            Spacer(Modifier.height(8.dp))
            if (c.state.paired) {
                Text("This phone is paired ✓", color = J.Green, fontSize = 14.sp)
                GhostButton("Unpair this phone", Modifier.fillMaxWidth().padding(top = 8.dp)) { askUnpair = true }
            } else PairForm(c)
        }

        SectionTitle("Memory")
        Panel(Modifier.fillMaxWidth()) {
            val list = memories
            if (list == null) GhostButton("Show what JARVIS remembers", Modifier.fillMaxWidth()) { c.loadMemories { memories = it } }
            else {
                if (list.isEmpty()) Text("Nothing remembered yet. Say \"remember that …\".", color = J.SilverDim, fontSize = 14.sp)
                list.forEach { m ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(m.second, color = J.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { forget = m }) { Text("Forget", color = J.Red) }
                    }
                }
            }
            GhostButton("Clear chat history", Modifier.fillMaxWidth().padding(top = 8.dp)) { askClearChat = true }
        }

        SectionTitle("Security")
        Panel(Modifier.fillMaxWidth(), onClick = { go("security") }) {
            Text("Security & permissions  →", color = J.Text, fontSize = 15.sp)
            Text("Emergency stop, pause, permissions, your data", color = J.SilverDim, fontSize = 12.sp)
        }

        SectionTitle("About")
        Panel(Modifier.fillMaxWidth()) {
            Text("JARVIS ${BuildConfig.VERSION_NAME}", color = J.Text)
            Text(
                "AI: Cloudflare Workers AI (Llama 3.3 70B) through your own JARVIS server. Choosing Claude or OpenAI with your own API key comes in phase 2.",
                color = J.SilverDim, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
    }

    forget?.let { m -> ConfirmDialog("Forget this?", m.second, "Forget", { c.deleteMemory(m.first) { c.loadMemories { memories = it } } }) { forget = null } }
    if (askUnpair) ConfirmDialog("Unpair this phone?", "JARVIS on this phone stops working until you pair again with the pairing code.", "Unpair", { c.unpair { } }) { askUnpair = false }
    if (askClearChat) ConfirmDialog("Clear the chat?", "Deletes the conversation on your JARVIS server. Memories and task history are kept.", "Clear", { c.clearChat() }) { askClearChat = false }
}

/** Pairing with the code from the setup message. */
@Composable
fun PairForm(c: Controller) {
    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf(Build.MODEL ?: "Phone") }
    var error by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    Column {
        OutlinedTextField(code, { code = it.uppercase(); error = null }, label = { Text("Pairing code") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp), colors = jFieldColors(), isError = error != null)
        OutlinedTextField(name, { name = it }, label = { Text("This phone's name") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            shape = RoundedCornerShape(14.dp), colors = jFieldColors())
        error?.let { Text(it, color = J.Red, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)) }
        PrimaryButton(if (working) "Pairing…" else "Pair", Modifier.fillMaxWidth().padding(top = 10.dp)) {
            if (working) return@PrimaryButton
            if (code.isBlank()) { error = "Enter the pairing code"; return@PrimaryButton }
            working = true
            c.pair(code.trim(), name.trim().ifEmpty { "Phone" }) { e -> working = false; error = e }
        }
    }
}
