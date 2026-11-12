package com.refluxcare.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.refluxcare.app.RefluxApp
import com.refluxcare.app.ui.screens.DietScreen
import com.refluxcare.app.ui.screens.ExerciseScreen
import com.refluxcare.app.ui.screens.GuideScreen
import com.refluxcare.app.ui.screens.InsightsScreen
import com.refluxcare.app.ui.screens.RoutineScreen
import com.refluxcare.app.ui.screens.RuleBreaksScreen
import com.refluxcare.app.ui.screens.SleepScreen
import com.refluxcare.app.ui.screens.TodayScreen
import com.refluxcare.app.ui.screens.WorkoutSessionScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { RefluxTheme { AppRoot() } }
    }

    override fun onResume() {
        super.onResume()
        // Detect last night's sleep and re-check rules every time the app comes to the front.
        val app = application as RefluxApp
        app.scope.launch { app.repo.dailyRefresh(applicationContext) }
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("today", "Today", Icons.Filled.Home),
    Tab("diet", "Diet", Icons.Filled.Favorite),
    Tab("exercise", "Exercise", Icons.Filled.PlayArrow),
    Tab("rules", "Breaks", Icons.Filled.Warning),
    Tab("insights", "Insights", Icons.Filled.Star)
)

@Composable
fun AppRoot() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    val go: (String) -> Unit = { target -> nav.navigate(target) }

    Scaffold(
        bottomBar = {
            if (tabs.any { it.route == route }) {
                NavigationBar {
                    tabs.forEach { t ->
                        NavigationBarItem(
                            selected = route == t.route,
                            onClick = {
                                nav.navigate(t.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(t.icon, contentDescription = t.label) },
                            label = { Text(t.label, maxLines = 1) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(navController = nav, startDestination = "today", modifier = Modifier.padding(padding)) {
            composable("today") { TodayScreen(onOpen = go) }
            composable("diet") { DietScreen() }
            composable("exercise") { ExerciseScreen(onStart = { type -> go("workout/$type") }) }
            composable("rules") { RuleBreaksScreen() }
            composable("insights") { InsightsScreen(onOpen = go) }
            composable("routine") { RoutineScreen(onBack = { nav.popBackStack() }) }
            composable("sleep") { SleepScreen(onBack = { nav.popBackStack() }) }
            composable("guide") { GuideScreen(onBack = { nav.popBackStack() }) }
            composable("workout/{type}") { entry ->
                WorkoutSessionScreen(
                    type = entry.arguments?.getString("type") ?: "YOGA",
                    onDone = { nav.popBackStack() }
                )
            }
        }
    }
}
