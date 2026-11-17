package app.nova.assistant.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nova.assistant.data.TaskStatus
import app.nova.assistant.ui.theme.J
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** A dark panel with a thin silver-blue edge. */
@Composable
fun Panel(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(J.Panel2, J.Panel)))
            .border(BorderStroke(1.dp, J.Line), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier = modifier.padding(top = 18.dp, bottom = 8.dp), color = J.SilverDim, fontSize = 11.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.Medium)
}

@Composable
fun Dot(color: Color, size: Int = 8) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(color))
}

/** A small rounded label with a colored dot, e.g. "● Online". */
@Composable
fun Pill(text: String, color: Color) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.12f)).border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Dot(color, 6)
        Spacer(Modifier.width(6.dp))
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, color: Color = J.Blue, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = J.Black)) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, J.Line)) {
        Text(text, color = J.Silver)
    }
}

/** A Yes/No question before something that cannot be undone. */
@Composable
fun ConfirmDialog(title: String, text: String, yes: String = "Yes", onYes: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = J.Panel2,
        title = { Text(title, color = J.Text) },
        text = { Text(text, color = J.Silver) },
        confirmButton = { TextButton(onClick = { onDismiss(); onYes() }) { Text(yes, color = J.Blue, fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = J.SilverDim) } },
    )
}

@Composable
fun SettingRow(title: String, subtitle: String = "", trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = J.Text, fontSize = 15.sp)
            if (subtitle.isNotEmpty()) Text(subtitle, color = J.SilverDim, fontSize = 12.sp, lineHeight = 16.sp)
        }
        trailing()
    }
}

fun statusColor(s: String): Color = when (s) {
    TaskStatus.COMPLETED.name -> J.Green
    TaskStatus.FAILED.name -> J.Red
    TaskStatus.CANCELLED.name -> J.SilverDim
    TaskStatus.WAITING_APPROVAL.name -> J.Amber
    else -> J.Blue
}

fun statusLabel(s: String): String = when (s) {
    TaskStatus.WAITING_APPROVAL.name -> "Waiting for approval"
    else -> s.lowercase().replaceFirstChar { it.uppercase() }
}

private val timeFmt = SimpleDateFormat("h:mm a", Locale.US)
private val dayFmt = SimpleDateFormat("d MMM, h:mm a", Locale.US)

fun startOfToday(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

fun whenText(at: Long): String = if (at >= startOfToday()) timeFmt.format(Date(at)) else dayFmt.format(Date(at))
