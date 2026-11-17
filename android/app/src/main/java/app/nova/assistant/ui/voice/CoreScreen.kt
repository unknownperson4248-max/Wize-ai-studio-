package app.nova.assistant.ui.voice

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nova.assistant.Mood
import app.nova.assistant.ui.ChatMsg
import app.nova.assistant.ui.Controller
import app.nova.assistant.ui.GhostButton
import app.nova.assistant.ui.MicGlyph
import app.nova.assistant.ui.Pill
import app.nova.assistant.ui.Visualizer
import app.nova.assistant.ui.theme.J

/** Talk to JARVIS: the core visualizer, the conversation, and the text and mic controls. */
@Composable
fun CoreScreen(c: Controller) {
    val s = c.state
    val list = rememberLazyListState()
    var text by remember { mutableStateOf("") }
    LaunchedEffect(s.messages.size) { if (s.messages.isNotEmpty()) list.animateScrollToItem(s.messages.size - 1) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("JARVIS", style = androidx.compose.material3.MaterialTheme.typography.headlineMedium, color = J.Text, modifier = Modifier.weight(1f))
            if (s.listening) Pill("MIC ON", J.Red)
            else if (s.paused) Pill("Automation paused", J.Amber)
            else if (s.wakeOn) Pill("Say \"Hey Jarvis\"", J.Blue)
        }
        Visualizer(s.mood, s.level, Modifier.fillMaxWidth().height(230.dp).clickable { c.toggleMic() })
        val line = when {
            s.listening -> s.partial.ifEmpty { s.status.ifEmpty { "Listening…" } }
            s.status.isNotEmpty() -> s.status
            else -> "Tap the core or the mic and speak"
        }
        Text(line, color = if (s.listening) J.Text else J.SilverDim, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), maxLines = 3)
        if (s.busy || s.mood == Mood.SPEAKING) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
                GhostButton(if (s.busy) "■  Stop task" else "■  Stop talking") { if (s.busy) c.emergencyStop() else c.hush() }
            }
        }

        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp), state = list) {
            items(s.messages, key = { it.id }) { Message(it) }
        }

        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                placeholder = { Text("Type or speak…", color = J.SilverDim) },
                modifier = Modifier.weight(1f), maxLines = 4, shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (text.isNotBlank()) { c.send(text.trim()); text = "" } }),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = J.Blue, unfocusedBorderColor = J.Line, focusedTextColor = J.Text, unfocusedTextColor = J.Text, cursorColor = J.Blue),
            )
            IconButton(onClick = { if (text.isNotBlank()) { c.send(text.trim()); text = "" } }) {
                Icon(Icons.Filled.Send, contentDescription = "Send", tint = J.Blue)
            }
            Box(
                Modifier.size(58.dp).clip(CircleShape)
                    .background(if (s.listening) Brush.linearGradient(listOf(J.Red, J.Amber)) else Brush.linearGradient(listOf(J.Cyan, J.Blue)))
                    .clickable { c.toggleMic() },
                contentAlignment = Alignment.Center,
            ) { MicGlyph(J.Black, Modifier.size(30.dp)) }
        }
    }
}

@Composable
private fun Message(m: ChatMsg) {
    when (m.role) {
        "note" -> Text(m.text, color = J.SilverDim, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), textAlign = TextAlign.Center)
        "user" -> Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.End) {
            Spacer(Modifier.width(48.dp))
            Text(
                m.text, color = J.Text, fontSize = 15.sp,
                modifier = Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(18.dp)).background(J.BlueDeep)
                    .border(1.dp, J.Blue.copy(alpha = 0.5f), RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
        else -> Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text(
                m.text, color = J.Text, fontSize = 15.sp, fontWeight = FontWeight.Normal,
                modifier = Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(18.dp)).background(J.Panel2)
                    .border(1.dp, J.Line, RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
            )
            Spacer(Modifier.width(48.dp))
        }
    }
}
