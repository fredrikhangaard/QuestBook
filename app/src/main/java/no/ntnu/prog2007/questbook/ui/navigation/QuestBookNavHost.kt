package no.ntnu.prog2007.questbook.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.viewmodel.compose.viewModel
import no.ntnu.prog2007.questbook.ui.groups.*
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
import no.ntnu.prog2007.questbook.data.AppServices
import no.ntnu.prog2007.questbook.location.GpsTestScreen
import no.ntnu.prog2007.questbook.ui.login.CreateUserScreen
import no.ntnu.prog2007.questbook.ui.login.LoginScreen
import no.ntnu.prog2007.questbook.ui.photo.AddPhotoScreen
import no.ntnu.prog2007.questbook.ui.profile.ProfileScreen
import no.ntnu.prog2007.questbook.ui.quest.QuestsScreen
import no.ntnu.prog2007.questbook.ui.quest.QuestScreen
import no.ntnu.prog2007.questbook.ui.questbook.QuestBookScreen
import no.ntnu.prog2007.questbook.ui.today.TodayScreen
import no.ntnu.prog2007.questbook.ui.welcome.WelcomeScreen
import android.net.Uri
import no.ntnu.prog2007.questbook.ui.quest.create.CreateQuestScreen

@Composable
fun QuestBookNavHost() {

    val navController = rememberNavController()
    // One source supplies matching group summaries to Home, overview, and detail pages.
    val groupsViewModel: GroupsViewModel = viewModel()
    val entry by navController.currentBackStackEntryAsState()
    val currentRoute = entry?.destination?.route

    // Skip the welcome screen when Firebase remembers a signed-in user from last time.
    // Saved, so the start screen stays the same when the activity is recreated.
    val startDestination = rememberSaveable {
        if (AppServices.authRepository.currentUserId != null) "today" else "launch"
    }

    // Restore each tab without adding duplicate destinations to the back stack.
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
            startDestination = startDestination,
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
                    onSignedIn = {
                        navController.navigate("today") {
                            popUpTo("launch") { inclusive = true }
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable("createuser") {
                CreateUserScreen(
                    onCreated = {
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
                    onQuestBookClick = { openTab("questbook") },
                    onAllQuestsClick = { openTab("quests") },
                    groups = groupsViewModel.groups,
                    onGroupClick = { id -> navController.navigate("group/$id") },
                    onAddGroup = { navController.navigate("addgroup") }
                )
            }

            composable(
                route = "quest/{questId}",
                arguments = listOf(
                    navArgument("questId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val questId = backStackEntry.arguments?.getString("questId").orEmpty()
                QuestScreen(
                    questId = questId,
                    onAddPhotoClick = { navController.navigate("addphoto/$questId") },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "addphoto/{questId}",
                arguments = listOf(
                    navArgument("questId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val questId = backStackEntry.arguments?.getString("questId").orEmpty()
                AddPhotoScreen(
                    questId = questId,
                    onDone = { navController.popBackStack() }
                )
            }

            composable("questbook") {
                QuestBookScreen()
            }

            composable("groups") {
                GroupsScreen(groupsViewModel.groups,
                    onGroupClick = { id -> navController.navigate("group/$id") },
                    onAddGroup = { navController.navigate("addgroup") })
            }
            composable(
                route = "group/{groupId}",
                arguments = listOf(
                    navArgument("groupId") {
                        type = NavType.StringType
                    }
                )
            ) { backStackEntry ->
                val groupId = backStackEntry.arguments
                    ?.getString("groupId")
                    .orEmpty()

                val group = groupsViewModel.groups.find {
                    it.id == groupId
                }

                GroupDetailScreen(
                    group = group,
                    onBack = { navController.popBackStack() },
                    onCreateQuest = {
                        navController.navigate(
                            "createquest/${Uri.encode(groupId)}"
                        )
                    },
                    onQuestClick = { questId ->
                        navController.navigate(
                            "quest/${Uri.encode(questId)}"
                        )
                    }
                )
            }

            composable(
                route = "createquest/{groupId}",
                arguments = listOf(
                    navArgument("groupId") {
                        type = NavType.StringType
                    }
                )
            ) { backStackEntry ->
                val groupId = backStackEntry.arguments
                    ?.getString("groupId")
                    .orEmpty()

                val group = groupsViewModel.groups.find {
                    it.id == groupId
                }

                if (group == null) {
                    androidx.compose.material3.TextButton(
                        onClick = { navController.popBackStack() }
                    ) {
                        androidx.compose.material3.Text("Group not found. Go back")
                    }
                } else {
                    CreateQuestScreen(
                        groupId = group.id,
                        groupName = group.name,
                        onBack = { navController.popBackStack() },
                        onCreated = { navController.popBackStack() }
                    )
                }
            }

            composable("addgroup") {
                AddGroupScreen(onBack = { navController.popBackStack() },
                    onCreate = { navController.navigate("creategroup") },
                    onJoin = { navController.navigate("joingroup") })
            }
            composable("creategroup") { GroupSetupPlaceholder(false) { navController.popBackStack() } }
            composable("joingroup") { GroupSetupPlaceholder(true) { navController.popBackStack() } }
            composable("quests") {
                QuestsScreen(
                    onQuestClick = { questId -> navController.navigate("quest/$questId") }
                )
            }
            composable("profile") {
                ProfileScreen(
                    onSignedOut = {
                        navController.navigate("launch") {
                            popUpTo(navController.graph.id) { inclusive = true }
                        }
                    }
                )
            }

            composable("gps_test") {
                GpsTestScreen()
            }
        }
    }
}