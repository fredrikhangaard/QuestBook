package no.ntnu.prog2007.questbook.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import no.ntnu.prog2007.questbook.ui.quest.QuestScreen
import no.ntnu.prog2007.questbook.ui.questbook.QuestBookScreen
import no.ntnu.prog2007.questbook.ui.today.TodayScreen
import no.ntnu.prog2007.questbook.location.GpsTestScreen
@Composable
fun QuestBookNavHost() {

    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "gps_test"
    ) {

        composable("today") {
            TodayScreen(
                onQuestClick = {
                    navController.navigate("quest")
                },
                onQuestBookClick = {
                    navController.navigate("questbook")
                }
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