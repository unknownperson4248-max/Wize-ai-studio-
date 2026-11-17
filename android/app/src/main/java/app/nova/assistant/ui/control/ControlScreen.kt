package app.nova.assistant.ui.control

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nova.assistant.ui.ConfirmDialog
import app.nova.assistant.ui.Controller
import app.nova.assistant.ui.Dot
import app.nova.assistant.ui.GhostButton
import app.nova.assistant.ui.Panel
import app.nova.assistant.ui.Pill
import app.nova.assistant.ui.PrimaryButton
import app.nova.assistant.ui.SectionTitle
import app.nova.assistant.ui.SettingRow
import app.nova.assistant.ui.theme.J
import app.nova.assistant.ui.whenText

@Composable
fun jSwitchColors() = SwitchDefaults.colors(checkedThumbColor = J.Black, checkedTrackColor = J.Blue, uncheckedThumbColor = J.SilverDim, uncheckedTrackColor = J.Panel2, uncheckedBorderColor = J.Line)

/** Phone control: turn the Accessibility service on or off, pause automation, the bubble, and the action history. */
@Composable
fun ControlScreen(c: Controller) {
    val s = c.state
    val log by remember { c.db.actions().recent(80) }.collectAsState(initial = emptyList())
    var askOff by remember { mutableStateOf(false) }
    var askClear by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Text("Phone control", style = MaterialTheme.typography.headlineMedium, color = J.Text, modifier = Modifier.padding(top = 14.dp, bottom = 12.dp))
            Panel(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Accessibility service", color = J.Text, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    Pill(if (s.controlOn) "On" else "Off", if (s.controlOn) J.Green else J.Amber)
                }
                if (s.controlOn && s.foregroundApp.isNotEmpty()) Text("On screen now: ${s.foregroundApp}", color = J.SilverDim, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                Text(
                    "Lets JARVIS open apps, read the screen, tap, type, scroll, swipe and press Back or Home when you ask, and shows the floating bubble.",
                    color = J.SilverDim, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp),
                )
                Spacer(Modifier.height(12.dp))
                if (s.controlOn) GhostButton("Turn phone control off", Modifier.fillMaxWidth()) { askOff = true }
                else {
                    PrimaryButton("Turn on in Accessibility settings", Modifier.fillMaxWidth()) { c.openAccessibilitySettings() }
                    Text(
                        "Find \"JARVIS phone control\" and switch it on. If Android says \"Restricted setting\", open App info below, tap ⋮ at the top right, choose \"Allow restricted settings\", then try again.",
                        color = J.SilverDim, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp),
                    )
                    GhostButton("App info", Modifier.fillMaxWidth().padding(top = 6.dp)) { c.openAppInfo() }
                }
            }
            Spacer(Modifier.height(10.dp))
            Panel(Modifier.fillMaxWidth()) {
                SettingRow("Pause automation", "While paused, no app, tap, type, call or message action runs.") {
                    Switch(checked = s.paused, onCheckedChange = { c.setPaused(it) }, colors = jSwitchColors())
                }
                SettingRow("Floating bubble", "Tap to talk over any app, hold to open JARVIS, drag to move.") {
                    Switch(checked = s.bubble, onCheckedChange = { c.setBubble(it) }, colors = jSwitchColors())
                }
            }
            SectionTitle("Safety rules")
            Panel(Modifier.fillMaxWidth()) {
                listOf(
                    "Sending, paying, buying, deleting and posting always ask you first.",
                    "Calls and SMS open filled in; you press call or send.",
                    "Never types into password fields.",
                    "Text on the screen is never treated as an instruction.",
                    "Cannot unlock the phone or pass fingerprint or PIN screens.",
                ).forEach { Text("•  $it", color = J.Silver, fontSize = 13.sp, modifier = Modifier.padding(vertical = 2.dp)) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Action history", Modifier.weight(1f))
                if (log.isNotEmpty()) TextButton(onClick = { askClear = true }) { Text("Clear", color = J.SilverDim) }
            }
            if (log.isEmpty()) Text("No actions yet.", color = J.SilverDim, fontSize = 14.sp)
        }
        items(log, key = { it.id }) { a ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.Start) {
                Column(Modifier.padding(top = 6.dp)) { Dot(if (a.ok) J.Green else J.Red) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("${a.tool}  ${a.detail}", color = J.Text, fontSize = 14.sp, maxLines = 2)
                    Text(a.result, color = if (a.ok) J.SilverDim else J.Red, fontSize = 12.sp, maxLines = 3)
                    Text((if (a.app.isNotEmpty()) a.app + " · " else "") + whenText(a.at), color = J.SilverDim, fontSize = 11.sp)
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
    if (askOff) ConfirmDialog("Turn phone control off?", "JARVIS will stop reading the screen and tapping, and the bubble will close. You can turn it on again in Accessibility settings.", "Turn off", { c.turnOffControl() }) { askOff = false }
    if (askClear) ConfirmDialog("Clear the action history?", "Deletes the list of actions JARVIS ran on this phone.", "Clear", { c.clearActions() }) { askClear = false }
}
