package app.nova.assistant.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nova.assistant.data.TaskStatus
import app.nova.assistant.ui.Controller
import app.nova.assistant.ui.Dot
import app.nova.assistant.ui.Panel
import app.nova.assistant.ui.Pill
import app.nova.assistant.ui.PrimaryButton
import app.nova.assistant.ui.SectionTitle
import app.nova.assistant.ui.Visualizer
import app.nova.assistant.ui.control.jSwitchColors
import app.nova.assistant.ui.startOfToday
import app.nova.assistant.ui.statusColor
import app.nova.assistant.ui.statusLabel
import app.nova.assistant.ui.theme.J
import app.nova.assistant.ui.whenText

/** Home: JARVIS status, system status, today's tasks, low stock and recent activity. Everything shown is live. */
@Composable
fun HomeScreen(c: Controller, go: (String) -> Unit) {
    val s = c.state
    val since = remember { startOfToday() }
    val counts by remember { c.db.tasks().countsSince(since) }.collectAsState(initial = emptyList())
    val recent by remember { c.db.tasks().recent(6) }.collectAsState(initial = emptyList())
    val low by remember { c.db.products().lowStock() }.collectAsState(initial = emptyList())
    val products by remember { c.db.products().count() }.collectAsState(initial = 0)
    fun n(st: TaskStatus) = counts.firstOrNull { it.status == st.name }?.n ?: 0

    val (overall, overallColor) = when {
        s.paused -> "Paused" to J.Amber
        s.aiOnline == false -> "Offline" to J.Red
        s.busy -> "Working" to J.Blue
        s.aiOnline == null -> "Connecting" to J.SilverDim
        else -> "Online" to J.Green
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("JARVIS", style = MaterialTheme.typography.headlineMedium, color = J.Text)
                Text("Personal & business assistant", color = J.SilverDim, fontSize = 13.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Pill(if (s.awake) overall else "Off", if (s.awake) overallColor else J.SilverDim)
                Switch(checked = s.awake, onCheckedChange = { c.setAwake(it) }, colors = jSwitchColors())
            }
        }
        Spacer(Modifier.height(14.dp))
        Panel(Modifier.fillMaxWidth(), onClick = { go("core") }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Visualizer(s.mood, s.level, Modifier.size(96.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (s.listening) "Listening…" else s.status.ifEmpty { "Ready" }, color = J.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    val last = s.messages.lastOrNull { it.role == "assistant" }?.text
                    Text(last ?: "Tap to talk to JARVIS", color = J.SilverDim, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        if (s.busy || s.paused) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (s.busy) PrimaryButton("Emergency stop", Modifier.weight(1f), color = J.Red) { c.emergencyStop() }
                if (s.paused) PrimaryButton("Resume automation", Modifier.weight(1f), color = J.Amber) { c.setPaused(false) }
            }
        }

        SectionTitle("System")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile("Microphone", if (s.listening) "On now" else if (s.micGranted) "Ready" else "Not allowed",
                if (s.listening) J.Red else if (s.micGranted) J.Green else J.Amber, Modifier.weight(1f)) { if (!s.micGranted) c.requestMic() else go("core") }
            Tile("AI connection", when (s.aiOnline) { true -> "Online"; false -> "Offline"; null -> "Checking…" },
                when (s.aiOnline) { true -> J.Green; false -> J.Red; null -> J.SilverDim }, Modifier.weight(1f), s.aiInfo) { c.checkAi() }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile("Phone control", if (s.controlOn) "On" else "Off", if (s.controlOn) J.Green else J.Amber, Modifier.weight(1f),
                if (s.controlOn && s.foregroundApp.isNotEmpty()) "On screen: ${s.foregroundApp}" else "") { go("control") }
            Tile("Hands-free", if (s.wakeOn) "Hey Jarvis on" else "Off", if (s.wakeOn) J.Green else J.SilverDim, Modifier.weight(1f),
                if (s.batteryFree) "Battery: unrestricted" else "Battery: may be stopped") { go("settings") }
        }

        SectionTitle("Today")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Count("Completed", n(TaskStatus.COMPLETED), J.Green, Modifier.weight(1f)) { go("tasks") }
            Count("Waiting", n(TaskStatus.WAITING_APPROVAL), J.Amber, Modifier.weight(1f)) { go("tasks") }
            Count("Failed", n(TaskStatus.FAILED), J.Red, Modifier.weight(1f)) { go("tasks") }
        }

        if (products > 0) {
            SectionTitle("Low stock")
            Panel(Modifier.fillMaxWidth()) {
                if (low.isEmpty()) Text("All $products products are above their minimum.", color = J.SilverDim, fontSize = 14.sp)
                low.take(8).forEach { p ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Dot(J.Amber); Spacer(Modifier.width(8.dp))
                        Text(p.name, color = J.Text, modifier = Modifier.weight(1f))
                        Text("${fmt(p.quantity)} / min ${fmt(p.minStock)} ${p.unit}", color = J.Amber, fontSize = 13.sp)
                    }
                }
            }
        }

        SectionTitle("Recent activity")
        Panel(Modifier.fillMaxWidth()) {
            if (recent.isEmpty()) Text("Nothing yet. Ask JARVIS something.", color = J.SilverDim, fontSize = 14.sp)
            recent.forEach { t ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Dot(statusColor(t.status))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.description, color = J.Text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${statusLabel(t.status)} · ${whenText(t.createdAt)}", color = J.SilverDim, fontSize = 12.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

private fun fmt(d: Double) = if (d % 1.0 == 0.0) d.toLong().toString() else "%.2f".format(d)

@Composable
private fun Tile(title: String, value: String, color: Color, modifier: Modifier, sub: String = "", onClick: () -> Unit) {
    Panel(modifier, onClick = onClick) {
        Text(title.uppercase(), color = J.SilverDim, fontSize = 11.sp, letterSpacing = 1.2.sp)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(color); Spacer(Modifier.width(8.dp))
            Text(value, color = J.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (sub.isNotEmpty()) Text(sub, color = J.SilverDim, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun Count(label: String, n: Int, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Panel(modifier, onClick = onClick) {
        Text(n.toString(), color = color, fontSize = 28.sp, fontWeight = FontWeight.Light)
        Text(label, color = J.SilverDim, fontSize = 12.sp)
    }
}
