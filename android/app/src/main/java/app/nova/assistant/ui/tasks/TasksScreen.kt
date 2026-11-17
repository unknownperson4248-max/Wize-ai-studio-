@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.nova.assistant.ui.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nova.assistant.data.TaskStatus
import app.nova.assistant.ui.ConfirmDialog
import app.nova.assistant.ui.Controller
import app.nova.assistant.ui.Dot
import app.nova.assistant.ui.GhostButton
import app.nova.assistant.ui.Panel
import app.nova.assistant.ui.Pill
import app.nova.assistant.ui.SectionTitle
import app.nova.assistant.ui.statusColor
import app.nova.assistant.ui.statusLabel
import app.nova.assistant.ui.theme.J
import app.nova.assistant.ui.whenText

private val FILTERS = listOf("" to "All") + TaskStatus.values().filter { it != TaskStatus.PENDING }.map { it.name to statusLabel(it.name) }

/** Searchable history of every request: status, time, and a detail page with each step. */
@Composable
fun TasksScreen(c: Controller, open: (String) -> Unit) {
    var q by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("") }
    var askClear by remember { mutableStateOf(false) }
    val tasks by remember(q, filter) { c.db.tasks().search(q.trim(), filter) }.collectAsState(initial = emptyList())

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Tasks", style = MaterialTheme.typography.headlineMedium, color = J.Text, modifier = Modifier.weight(1f))
            TextButton(onClick = { askClear = true }) { Text("Clear all", color = J.SilverDim) }
        }
        OutlinedTextField(
            value = q, onValueChange = { q = it }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            placeholder = { Text("Search tasks", color = J.SilverDim) }, shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = J.Blue, unfocusedBorderColor = J.Line, focusedTextColor = J.Text, unfocusedTextColor = J.Text),
        )
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FILTERS.forEach { (key, label) ->
                FilterChip(
                    selected = filter == key, onClick = { filter = key }, label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = J.BlueDeep, selectedLabelColor = J.Text, labelColor = J.SilverDim),
                )
            }
        }
        if (tasks.isEmpty()) Text(if (q.isBlank() && filter.isEmpty()) "No tasks yet. Everything you ask JARVIS appears here." else "No matching tasks.", color = J.SilverDim, modifier = Modifier.padding(top = 20.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(tasks, key = { it.id }) { t ->
                Row(Modifier.fillMaxWidth().clickable { open(t.id) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Dot(statusColor(t.status), 10)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.description, color = J.Text, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${statusLabel(t.status)} · ${whenText(t.createdAt)}" + if (t.source == "bubble") " · bubble" else "", color = J.SilverDim, fontSize = 12.sp)
                    }
                }
            }
        }
    }
    if (askClear) ConfirmDialog("Clear task history?", "Deletes every task and its steps from this phone. This cannot be undone.", "Clear", { c.clearTasks() }) { askClear = false }
}

@Composable
fun TaskDetailScreen(c: Controller, id: String, back: () -> Unit) {
    val task by remember(id) { c.db.tasks().task(id) }.collectAsState(initial = null)
    val steps by remember(id) { c.db.tasks().steps(id) }.collectAsState(initial = emptyList())
    var askDelete by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            TextButton(onClick = back, modifier = Modifier.padding(top = 6.dp)) { Text("← Tasks", color = J.Blue) }
            val t = task
            if (t == null) { Text("This task was deleted.", color = J.SilverDim); return@item }
            Text(t.description, color = J.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Pill(statusLabel(t.status), statusColor(t.status))
            Spacer(Modifier.height(10.dp))
            Text("Started ${whenText(t.createdAt)} · updated ${whenText(t.updatedAt)}", color = J.SilverDim, fontSize = 12.sp)
            Text("Task ID ${t.id.take(8)} · from the ${if (t.source == "bubble") "floating bubble" else "app"}", color = J.SilverDim, fontSize = 12.sp)
            if (t.result.isNotBlank()) {
                SectionTitle("Result")
                Panel(Modifier.fillMaxWidth()) { Text(t.result, color = J.Text) }
            }
            if (t.error.isNotBlank()) {
                SectionTitle("Error")
                Panel(Modifier.fillMaxWidth()) { Text(t.error, color = J.Red) }
            }
            SectionTitle("Steps")
            if (steps.isEmpty()) Text("No steps recorded.", color = J.SilverDim)
        }
        items(steps, key = { it.id }) { st ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Dot(if (st.ok) J.Blue else J.Red)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(st.text, color = if (st.ok) J.Text else J.Red, fontSize = 14.sp)
                    Text(whenText(st.at), color = J.SilverDim, fontSize = 11.sp)
                }
            }
        }
        item {
            Spacer(Modifier.height(16.dp))
            if (task != null) GhostButton("Delete this task") { askDelete = true }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (askDelete) ConfirmDialog("Delete this task?", "Removes the task and its steps from this phone.", "Delete", { c.deleteTask(id); back() }) { askDelete = false }
}
