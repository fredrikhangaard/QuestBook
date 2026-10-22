
package no.ntnu.prog2007.questbook.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import no.ntnu.prog2007.questbook.ui.quest.QuestScreen
import no.ntnu.prog2007.questbook.ui.questbook.QuestBookScreen
import no.ntnu.prog2007.questbook.ui.today.TodayScreen

@Composable
fun QuestBookNavHost() {
    val navController = rememberNavController()

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
        }

        composable("questbook") {
            QuestBookScreen()
        }
    }
}
