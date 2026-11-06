package no.ntnu.prog2007.questbook.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
fun QuestBookNavHost(trackingRequest: Pair<String, String>? = null, onTrackingOpened: () -> Unit = {}) {

    val navController = rememberNavController()
    val context = androidx.compose.ui.platform.LocalContext.current
    val personal: no.ntnu.prog2007.questbook.ui.personal.PersonalQuestsViewModel = viewModel(
        factory = androidx.compose.runtime.remember { no.ntnu.prog2007.questbook.ui.personal.PersonalQuestsViewModel.Factory(context) })
    val personalState by personal.state.collectAsStateWithLifecycle()
    val challenges: no.ntnu.prog2007.questbook.ui.challenges.GroupChallengesViewModel = viewModel(
        factory = androidx.compose.runtime.remember { no.ntnu.prog2007.questbook.ui.challenges.GroupChallengesViewModel.Factory(context) })
    val challengeState by challenges.state.collectAsStateWithLifecycle()
    fun openQuest(id: String) {
        val parent = challengeState.snapshot.attempts.find { it.attemptId == id }?.challengeId ?: id
        if (challengeState.snapshot.challenges.any { it.questId == parent }) {
            navController.navigate("challenge/${Uri.encode(parent)}")
        } else if (no.ntnu.prog2007.questbook.data.personal.PersonalQuestIds.isPersonalQuest(id)) {
            navController.navigate("personal/${Uri.encode(id)}")
        } else {
            // Quest cards and shared-definition observers can update in different frames after Create.
            challenges.resolveParent(id) { savedParent ->
                navController.navigate(if (savedParent != null) "challenge/${Uri.encode(savedParent)}" else "quest/${Uri.encode(id)}")
            }
        }
    }
    // One source supplies matching group summaries to Home, overview, and detail pages.
    val groupsViewModel: GroupsViewModel = viewModel()
    val groups by groupsViewModel.groups.collectAsStateWithLifecycle()
    // Observe quest changes too, so an asynchronously loaded quest gets its correct group in the picker.
    val quests by no.ntnu.prog2007.questbook.data.repository.QuestDependencies.questRepository.quests.collectAsStateWithLifecycle()
    val entry by navController.currentBackStackEntryAsState()
    val currentRoute = entry?.destination?.route
    val signedInUser by AppServices.authRepository.currentUserIdFlow.collectAsStateWithLifecycle(
        initialValue = AppServices.authRepository.currentUserId)
    androidx.compose.runtime.LaunchedEffect(trackingRequest, signedInUser, quests) {
        trackingRequest?.let { (user, id) ->
            // An old notification is never a shortcut into a different account's route.
            if (user != signedInUser) onTrackingOpened()
            else if (quests.any { it.id == id }) {
                navController.navigate("quest/${Uri.encode(id)}") { launchSingleTop = true }
                onTrackingOpened()
            }
        }
    }

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
        // Restoring a tab can also restore its former detail stack. A tab tap opens its overview.
        navController.popBackStack(route, inclusive = false)
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
                    onQuestClick = ::openQuest,
                    onQuestBookClick = { openTab("questbook") },
                    onAllQuestsClick = { openTab("quests") },
                    groups = groups,
                    onGroupClick = { id -> navController.navigate("group/$id") },
                    onAddGroup = { navController.navigate("addgroup") },
                    challengeState = challengeState,
                    personalState = personalState, onPersonalQuest = ::openQuest, onRefreshPersonal = personal::refresh
                )
            }

            composable(
                route = "quest/{questId}?start={start}",
                arguments = listOf(
                    navArgument("questId") { type = NavType.StringType },
                    navArgument("start") { type = NavType.BoolType; defaultValue = false }
                )
            ) { backStackEntry ->
                val questId = backStackEntry.arguments?.getString("questId").orEmpty()
                QuestScreen(
                    questId = questId,
                    startOnOpen = backStackEntry.arguments?.getBoolean("start") == true,
                    onAddPhotoClick = { navController.navigate("addphoto/${Uri.encode(questId)}") },
                    onMemoriesClick = { openTab("questbook") },
                    onViewRoute = { navController.navigate("savedroute/${Uri.encode(questId)}") },
                    onBack = { navController.popBackStack() },
                    canAccessGroup = { groupId -> groupsViewModel.groups.value.any { it.id == groupId } ||
                        AppServices.authRepository.currentUserId?.let { groupId == no.ntnu.prog2007.questbook.data.personal.PersonalQuestIds.group(it) } == true },
                    group = groups.find { it.id == quests.find { quest -> quest.id == questId }?.groupId }
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
                    onDone = { openTab("questbook") }
                )
            }

            composable("questbook") {
                QuestBookScreen(
                    shared = challengeState,
                    onQuestClick = ::openQuest,
                    onRouteClick = { id -> navController.navigate("savedroute/${Uri.encode(id)}") })
            }

            composable("groups") {
                GroupsScreen(groups,
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

                val group = groups.find {
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
                    onQuestClick = ::openQuest,
                    challengeState = challengeState,
                    onGetQuest = { challenges.draw(groupId) { id -> navController.navigate("challenge/${Uri.encode(id)}") } }
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

                val group = groups.find {
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
                        saveQuest = challenges::saveCreated,
                        onBack = { navController.popBackStack() },
                        onCreated = { navController.popBackStack() }
                    )
                }
            }

            composable("personal/{questId}", arguments = listOf(navArgument("questId") { type = NavType.StringType })) { backStackEntry ->
                val id = backStackEntry.arguments?.getString("questId").orEmpty()
                no.ntnu.prog2007.questbook.ui.personal.PersonalQuestDetail(id, personalState,
                    onBack = { navController.popBackStack() },
                    onTrack = { personal.track(id) { target -> navController.navigate("quest/${Uri.encode(target)}?start=true") } },
                    onLog = { quantity, minutes -> personal.logManual(id, quantity, minutes) },
                    onPhoto = { navController.navigate("addphoto/${Uri.encode(id)}") },
                    onMemories = { openTab("questbook") }, onRefresh = personal::refresh)
            }

            composable("challenge/{questId}", arguments = listOf(navArgument("questId") { type = NavType.StringType })) { backStackEntry ->
                val id = backStackEntry.arguments?.getString("questId").orEmpty()
                no.ntnu.prog2007.questbook.ui.challenges.GroupChallengeScreen(id, challengeState, personalState,
                    onBack = { navController.popBackStack() }, onOpen = { challenges.open(id) },
                    onTrack = { child -> personal.track(child) { target -> navController.navigate("quest/${Uri.encode(target)}?start=true") } },
                    onLog = challenges::logAttempt,
                    onPhoto = { child -> navController.navigate("addphoto/${Uri.encode(child)}") },
                    onMemories = { openTab("questbook") },
                    onWeeklyTrack = { challenges.startWeekly(id) { child -> navController.navigate("quest/${Uri.encode(child)}?start=true") } },
                    onWeeklyLog = { amount -> challenges.logWeekly(id, amount) }, onRetry = { challenges.retry(); personal.refresh() })
            }

            // Compatibility for old saved back stacks: the unrestricted logger has been removed.
            composable("logactivity") {
                no.ntnu.prog2007.questbook.ui.personal.LogPersonalActivityScreen { openTab("quests") }
            }

            composable("addgroup") {
                AddGroupScreen(onBack = { navController.popBackStack() },
                    onCreate = { navController.navigate("creategroup") },
                    onJoin = { navController.navigate("joingroup") })
            }
            composable("creategroup") {
                CreateGroupScreen(
                    onBack = { navController.popBackStack() },
                    onCreated = { groupId ->
                        navController.navigate("group/${Uri.encode(groupId)}") {
                            popUpTo("addgroup") { inclusive = true }
                        }
                    }
                )
            }
            composable("joingroup") {
                JoinGroupScreen(
                    onBack = { navController.popBackStack() },
                    onJoined = { groupId ->
                        navController.navigate("group/${Uri.encode(groupId)}") {
                            popUpTo("addgroup") { inclusive = true }
                        }
                    }
                )
            }
            composable("quests") {
                QuestsScreen(
                    onQuestClick = ::openQuest, personalState = personalState, challengeState = challengeState,
                    onPersonalQuest = ::openQuest, onRefreshPersonal = personal::refresh
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

            // Route IDs come from the quest; the reader checks ownership before loading positions.
            composable("savedroute/{questId}", arguments = listOf(navArgument("questId") {
                type = NavType.StringType
            })) { backStackEntry ->
                no.ntnu.prog2007.questbook.ui.quest.route.SavedRouteScreen(
                    questId = backStackEntry.arguments?.getString("questId").orEmpty(),
                    onBack = { navController.popBackStack() })
            }

            composable("gps_test") {
                GpsTestScreen()
            }
        }
    }
}
