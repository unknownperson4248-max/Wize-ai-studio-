@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.nova.assistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.nova.assistant.IntroView
import app.nova.assistant.ui.control.ControlScreen
import app.nova.assistant.ui.dashboard.HomeScreen
import app.nova.assistant.ui.security.SecurityScreen
import app.nova.assistant.ui.settings.PairForm
import app.nova.assistant.ui.settings.SettingsScreen
import app.nova.assistant.ui.tasks.TaskDetailScreen
import app.nova.assistant.ui.tasks.TasksScreen
import app.nova.assistant.ui.theme.J
import app.nova.assistant.ui.voice.CoreScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("home", "Home", Icons.Filled.Home),
    Tab("core", "JARVIS", Icons.Filled.Face),
    Tab("tasks", "Tasks", Icons.Filled.List),
    Tab("control", "Control", Icons.Filled.Build),
    Tab("settings", "Settings", Icons.Filled.Settings),
)

/** The whole app: bottom tabs, screens, the approval dialog, the pairing dialog and the intro. */
@Composable
fun JarvisApp(c: Controller) {
    val s = c.state
    val nav = rememberNavController()
    LaunchedEffect(s.goTo) { s.goTo?.let { route -> s.goTo = null; go(nav, route) } }

    Box(Modifier.fillMaxSize().background(J.Bg)) {
        Scaffold(containerColor = J.Bg, bottomBar = { if (!WindowInsets.isImeVisible) BottomBar(nav) }) { pad ->
            NavHost(nav, startDestination = "home", modifier = Modifier.padding(pad).consumeWindowInsets(pad).imePadding()) {
                composable("home") { HomeScreen(c) { go(nav, it) } }
                composable("core") { CoreScreen(c) }
                composable("tasks") { TasksScreen(c) { id -> nav.navigate("task/$id") } }
                composable("task/{id}") { TaskDetailScreen(c, it.arguments?.getString("id").orEmpty()) { nav.popBackStack() } }
                composable("control") { ControlScreen(c) }
                composable("settings") { SettingsScreen(c) { route -> nav.navigate(route) } }
                composable("security") { SecurityScreen(c) { nav.popBackStack() } }
            }
        }

        s.approval?.let { a ->
            AlertDialog(
                onDismissRequest = {},
                properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
                containerColor = J.Panel2,
                title = { Text("Approve this?", color = J.Text) },
                text = { Text(a.question, color = J.Silver) },
                confirmButton = { TextButton(onClick = { s.approval = null; a.answer(true) }) { Text("Yes, do it", color = J.Blue, fontWeight = FontWeight.SemiBold) } },
                dismissButton = { TextButton(onClick = { s.approval = null; a.answer(false) }) { Text("No", color = J.SilverDim) } },
            )
        }

        if (!s.paired && !s.showIntro) {
            AlertDialog(
                onDismissRequest = {},
                properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
                containerColor = J.Panel2,
                title = { Text("Connect JARVIS", color = J.Text) },
                text = { Column { Text("Enter the pairing code from your setup message.", color = J.SilverDim); PairForm(c) } },
                confirmButton = {},
            )
        }

        if (s.showIntro) {
            AndroidView(
                factory = { ctx -> IntroView(ctx, autoClose = c.prefs.paired, removeSelf = false) { s.showIntro = false } },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private fun go(nav: NavHostController, route: String) {
    nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Floating glass tab bar that sits above the gesture bar (or the buttons), whatever navigation the phone uses. */
@Composable
private fun BottomBar(nav: NavHostController) {
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(32.dp))
                .background(Brush.verticalGradient(listOf(J.Panel2.copy(alpha = 0.96f), J.Panel.copy(alpha = 0.96f))))
                .border(1.dp, Brush.horizontalGradient(listOf(J.Line, J.Blue.copy(alpha = 0.45f), J.Line)), RoundedCornerShape(32.dp))
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TABS.forEach { t ->
                val selected = route == t.route || (t.route == "tasks" && route == "task/{id}") || (t.route == "settings" && route == "security")
                Column(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(26.dp))
                        .clickable { go(nav, t.route) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier.size(width = 44.dp, height = 28.dp).clip(RoundedCornerShape(14.dp))
                            .background(if (selected) J.BlueDeep else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) { Icon(t.icon, contentDescription = t.label, tint = if (selected) J.Blue else J.SilverDim, modifier = Modifier.size(21.dp)) }
                    Text(t.label, color = if (selected) J.Blue else J.SilverDim, fontSize = 10.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}
