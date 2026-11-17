package app.nova.assistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
        Scaffold(containerColor = J.Bg, bottomBar = { BottomBar(nav) }) { pad ->
            NavHost(nav, startDestination = "home", modifier = Modifier.padding(pad)) {
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

@Composable
private fun BottomBar(nav: NavHostController) {
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    NavigationBar(containerColor = J.Panel, tonalElevation = 0.dp) {
        TABS.forEach { t ->
            NavigationBarItem(
                selected = route == t.route || (t.route == "tasks" && route == "task/{id}") || (t.route == "settings" && route == "security"),
                onClick = { go(nav, t.route) },
                icon = { Icon(t.icon, contentDescription = t.label) },
                label = { Text(t.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = J.Blue, selectedTextColor = J.Blue, indicatorColor = J.BlueDeep,
                    unselectedIconColor = J.SilverDim, unselectedTextColor = J.SilverDim,
                ),
            )
        }
    }
}
