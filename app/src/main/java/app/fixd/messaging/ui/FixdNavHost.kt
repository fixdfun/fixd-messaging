package app.fixd.messaging.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import app.fixd.messaging.ui.screens.ConversationListScreen
import app.fixd.messaging.ui.screens.ThreadDetailScreen
import app.fixd.messaging.ui.screens.ComposeScreen
import app.fixd.messaging.ui.screens.SettingsScreen

@Composable
fun FixdNavHost(
    isDefaultSmsApp: Boolean,
    onRequestDefault: () -> Unit,
    onRequestPermissions: () -> Unit
) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "conversations") {
        composable("conversations") {
            ConversationListScreen(
                isDefaultSmsApp = isDefaultSmsApp,
                onRequestDefault = onRequestDefault,
                onRequestPermissions = onRequestPermissions,
                onOpenThread = { threadId -> nav.navigate("thread/$threadId") },
                onCompose = { nav.navigate("compose") },
                onSettings = { nav.navigate("settings") }
            )
        }
        composable(
            route = "thread/{threadId}",
            arguments = listOf(navArgument("threadId") { type = NavType.LongType })
        ) { backStack ->
            val tid = backStack.arguments?.getLong("threadId") ?: -1L
            ThreadDetailScreen(threadId = tid, onBack = { nav.popBackStack() })
        }
        composable("compose") {
            ComposeScreen(onBack = { nav.popBackStack() })
        }
        composable("settings") {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
    }
}
