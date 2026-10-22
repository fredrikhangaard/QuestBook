
package no.ntnu.prog2007.questbook.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import no.ntnu.prog2007.questbook.ui.quest.QuestScreen
import no.ntnu.prog2007.questbook.ui.questbook.QuestBookScreen
import no.ntnu.prog2007.questbook.ui.today.TodayScreen
import no.ntnu.prog2007.questbook.location.GpsTestScreen

@Composable
fun QuestBookNavHost() {
    val navController = rememberNavController()
    val isLoggedIn = false

  5-pb-05-quests
    NavHost(
        navController = navController,
        startDestination = "today"
    ) {
        composable("today") {
            TodayScreen(
                onQuestClick = { questId ->
                    navController.navigate("quest/$questId")
                },
                onQuestBookClick = {
                    navController.navigate("questbook")
                }
            )
        }

        composable(
            route = "quest/{questId}",
            arguments = listOf(
                navArgument("questId") {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->
            val questId = backStackEntry.arguments
                ?.getString("questId")
                .orEmpty()

            QuestScreen(questId = questId)
            
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = if (isLoggedIn) "today" else "launch",
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
                    onQuestClick = { navController.navigate("quest") },
                    onQuestBookClick = { navController.navigate("questbook") }
                )
            }

            composable("quest") {
                QuestScreen()
            }

            composable("questbook") {
                QuestBookScreen()
            }
            
            composable("gps_test") {
              GpsTestScreen()
            }
        }
    }
}
