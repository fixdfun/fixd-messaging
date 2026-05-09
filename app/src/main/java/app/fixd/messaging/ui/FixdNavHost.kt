package app.fixd.messaging.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.fixd.messaging.ui.screens.ComposeScreen
import app.fixd.messaging.ui.screens.ConversationListScreen
import app.fixd.messaging.ui.screens.GroupComposeScreen
import app.fixd.messaging.ui.screens.GroupThreadScreen
import app.fixd.messaging.ui.screens.SafetyNumberScreen
import app.fixd.messaging.ui.screens.SettingsScreen
import app.fixd.messaging.ui.screens.ThreadDetailScreen

@Composable
fun FixdNavHost(
    isDefaultSmsApp: Boolean,
    onRequestDefault: () -> Unit,
    onRequestPermissions: () -> Unit
) {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = "conversations") {

        //  Conversation list 
        composable("conversations") {
            ConversationListScreen(
                isDefaultSmsApp    = isDefaultSmsApp,
                onRequestDefault   = onRequestDefault,
                onRequestPermissions = onRequestPermissions,
                onOpenThread       = { threadId -> nav.navigate("thread/$threadId") },
                onCompose          = { nav.navigate("compose") },
                onSettings         = { nav.navigate("settings") },
                onNewGroup         = { nav.navigate("group_compose") }
            )
        }

        //  1-to-1 SMS/MMS thread 
        composable(
            route = "thread/{threadId}",
            arguments = listOf(navArgument("threadId") { type = NavType.LongType })
        ) { back ->
            val tid = back.arguments?.getLong("threadId") ?: -1L
            ThreadDetailScreen(
                threadId = tid,
                onBack   = { nav.popBackStack() },
                onSafetyNumber = { addr -> nav.navigate("safety/$addr") }
            )
        }

        //  1-to-1 compose 
        composable("compose") {
            ComposeScreen(onBack = { nav.popBackStack() })
        }

        //  Settings 
        composable("settings") {
            SettingsScreen(onBack = { nav.popBackStack() })
        }

        //  Safety Number 
        composable(
            route = "safety/{address}",
            arguments = listOf(navArgument("address") { type = NavType.StringType })
        ) { back ->
            val addr = back.arguments?.getString("address") ?: ""
            SafetyNumberScreen(address = addr, onBack = { nav.popBackStack() })
        }

        //  Group compose (create new group) 
        composable("group_compose") {
            GroupComposeScreen(
                onBack = { nav.popBackStack() },
                onGroupCreated = { groupId ->
                    nav.navigate("group_thread/$groupId") {
                        popUpTo("conversations")
                    }
                }
            )
        }

        //  Group thread 
        composable(
            route = "group_thread/{groupId}",
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) { back ->
            val gid = back.arguments?.getString("groupId") ?: ""
            GroupThreadScreen(groupId = gid, onBack = { nav.popBackStack() })
        }
    }
}
