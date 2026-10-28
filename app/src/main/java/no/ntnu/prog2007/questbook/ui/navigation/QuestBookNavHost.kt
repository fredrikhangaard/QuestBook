package no.ntnu.prog2007.questbook.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import no.ntnu.prog2007.questbook.location.GpsTestScreen
import no.ntnu.prog2007.questbook.ui.login.CreateUserScreen
import no.ntnu.prog2007.questbook.ui.login.LoginScreen
import no.ntnu.prog2007.questbook.ui.quest.QuestScreen
import no.ntnu.prog2007.questbook.ui.questbook.QuestBookScreen
import no.ntnu.prog2007.questbook.ui.today.TodayScreen
import no.ntnu.prog2007.questbook.ui.welcome.WelcomeScreen

@Composable
fun QuestBookNavHost() {

    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    val currentRoute = entry?.destination?.route

    fun openTab(route: String) {
        navController.navigate(route) {
            popUpTo("today") { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (bottomDestinations.any { it.route == currentRoute }) {
                QuestBookBottomBar(currentRoute = currentRoute, onNavigate = ::openTab)
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "launch",
            modifier = Modifier.padding(padding)
        ) {

            composable("launch") {
                WelcomeScreen(
                    onSignInClick = { navController.navigate("login") },
                    onCreateUserClick = { navController.navigate("createuser") }
                )
            }

            composable("login") {
                LoginScreen(
                    onSignIn = { _, _ ->
                        navController.navigate("today") {
                            popUpTo("launch") { inclusive = true }
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable("createuser") {
                CreateUserScreen(
                    onCreateUser = { _, _, _ ->
                        navController.navigate("today") {
                            popUpTo("launch") { inclusive = true }
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable("today") {
                TodayScreen(
                    onQuestClick = { questId -> navController.navigate("quest/$questId") },
                    onQuestBookClick = { openTab("questbook") }
                )
            }

            composable(
                route = "quest/{questId}",
                arguments = listOf(
                    navArgument("questId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val questId = backStackEntry.arguments?.getString("questId").orEmpty()
                QuestScreen(questId = questId)
            }

            composable("questbook") {
                QuestBookScreen()
            }

            composable("groups") { GroupsScreen() }
            composable("quests") { QuestsScreen() }
            composable("profile") { ProfileScreen() }

            composable("gps_test") {
                GpsTestScreen()
            }
        }
    }
}