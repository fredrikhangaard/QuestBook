package no.ntnu.prog2007.questbook

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import no.ntnu.prog2007.questbook.data.AppServices
import no.ntnu.prog2007.questbook.data.auth.FakeAuthRepository
import no.ntnu.prog2007.questbook.data.groups.FakeGroupRepository
import no.ntnu.prog2007.questbook.data.personal.*
import no.ntnu.prog2007.questbook.data.repository.QuestDependencies
import no.ntnu.prog2007.questbook.data.tracking.RoomQuestTrackingStore
import no.ntnu.prog2007.questbook.location.*
import no.ntnu.prog2007.questbook.location.database.QuestBookDatabase
import no.ntnu.prog2007.questbook.model.User
import no.ntnu.prog2007.questbook.ui.quest.tracking.QuestTrackingViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.ZoneId
import java.util.UUID

/**
 * Runs the real Android service/notifications and Room writer with deterministic GPS fixes.
 * This verifies lifetime and recovery without depending on satellite reception. Physical-phone
 * reception and manufacturer battery restrictions still need the manual checks in the GPS guide.
 */
class BackgroundTrackingServiceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext
    private val uid = "background-service-fixture"
    private lateinit var activity: ActivityScenario<MainActivity>
    private var scenarioClosed = false
    private lateinit var auth: FakeAuthRepository
    private val engines = mutableListOf<GpsRepository>()

    private class Source : GpsSource {
        lateinit var point: (RoutePoint, Boolean) -> Unit
        var starts = 0
        override fun start(onPoint: (RoutePoint, Boolean) -> Unit) { point = onPoint; starts++ }
        override fun stop() {}
    }
    private data class Walk(val quest: String, val gps: GpsRepository, val source: Source)

    @Before fun setup() {
        auth = FakeAuthRepository(User(uid, "GPS tester", "gps@example.test"), signedIn = true)
        AppServices.authRepository = auth
        AppServices.groupRepository = FakeGroupRepository()
        // Permissions are granted only to this disposable test installation, not a user's phone.
        shell("pm grant ${context.packageName} ${Manifest.permission.ACCESS_COARSE_LOCATION}")
        shell("pm grant ${context.packageName} ${Manifest.permission.ACCESS_FINE_LOCATION}")
        if (android.os.Build.VERSION.SDK_INT >= 33)
            shell("pm grant ${context.packageName} ${Manifest.permission.POST_NOTIFICATIONS}")
        shell("cmd location set-location-enabled true")
        shell("input keyevent KEYCODE_WAKEUP")
        shell("wm dismiss-keyguard")
        activity = ActivityScenario.launch(MainActivity::class.java)
    }

    @After fun cleanup() = runBlocking {
        shell("input keyevent KEYCODE_WAKEUP")
        shell("wm dismiss-keyguard")
        shell("cmd location set-location-enabled true")
        main { context.stopService(Intent(context, QuestTrackingService::class.java)) }
        eventually { engines.none { it.state.value.isTracking } }
        engines.forEach { engine -> engine.flush(); main { engine.close() } }
        // A notification can launch an Activity outside the Scenario. Close those fixtures too.
        main {
            val monitor = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
            listOf(androidx.test.runner.lifecycle.Stage.CREATED, androidx.test.runner.lifecycle.Stage.STARTED,
                androidx.test.runner.lifecycle.Stage.RESUMED, androidx.test.runner.lifecycle.Stage.PAUSED,
                androidx.test.runner.lifecycle.Stage.STOPPED).flatMap { monitor.getActivitiesInStage(it) }
                .filterIsInstance<MainActivity>().distinct().forEach { it.finish() }
        }
        if (!scenarioClosed) activity.close()
        AppServices.reset()
    }

    private fun shell(command: String) {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
    }
    private fun main(action: () -> Unit) = instrumentation.runOnMainSync(action)
    private suspend fun eventually(message: String = "Condition did not become true", condition: () -> Boolean) {
        try { withTimeout(15000) { while (!condition()) delay(100) } }
        catch (error: TimeoutCancellationException) { throw AssertionError(message, error) }
    }
    private suspend fun walk(targetKm: Double = 1.0): Walk {
        val db = QuestBookDatabase.getDatabase(context)
        val quest = PersonalQuestStore(db).createActivity(uid, ActivityMetric.WALK_METERS, targetKm, 0.0,
            UUID.randomUUID().toString(), true, System.currentTimeMillis(), ZoneId.systemDefault())
        QuestDependencies.questRepository.quests.first { list -> list.any { it.id == quest } }
        val store = RoomQuestTrackingStore(db)
        val snapshot = store.begin(quest, uid, System.currentTimeMillis())
        val source = Source()
        val gps = GpsRepository(source, {}, persistCheckpoint = store::checkpoint)
        engines += gps
        main { gps.restore(snapshot) }
        return Walk(quest, gps, source)
    }
    private fun start(walk: Walk) = main {
        QuestTrackingService.start(context, "$uid:${walk.quest}", walk.quest, uid, walk.gps)
    }
    private fun notification() = context.getSystemService(NotificationManager::class.java).activeNotifications
        .firstOrNull { it.id == 701 }?.notification

    @Test fun lockedPhoneKeepsTimeAndRouteThenNotificationPausePreservesCheckpoint() = runBlocking {
        val walk = walk()
        start(walk)
        eventually { walk.gps.state.value.isTracking && notification() != null }
        val timestamp = System.currentTimeMillis()
        main { walk.source.point(RoutePoint(60.0, 10.0, timestamp, 5f), true) }
        activity.moveToState(Lifecycle.State.CREATED)
        shell("input keyevent KEYCODE_SLEEP")
        // Waiting for a durable timer checkpoint proves the screen timer is no longer required.
        val store = RoomQuestTrackingStore(QuestBookDatabase.getDatabase(context))
        withTimeout(15000) { while ((store.load(walk.quest, uid)?.elapsedMillis ?: 0) < 5000) delay(200) }
        assertTrue(walk.gps.state.value.isTracking)
        main { walk.source.point(RoutePoint(60.00005, 10.0, timestamp + 3000, 5f), true) }
        assertTrue(walk.gps.state.value.distanceMeters > 5)
        val activeNotification = checkNotNull(notification())
        assertNotNull(activeNotification.contentIntent)
        activeNotification.actions.first().actionIntent.send() // Exercise the real Pause PendingIntent.
        eventually { !walk.gps.state.value.isTracking && notification() == null }
        walk.gps.flush()
        val paused = walk.gps.state.value
        main { walk.source.point(RoutePoint(60.001, 10.0, timestamp + 6000, 5f), true) }
        assertEquals(paused.distanceMeters, walk.gps.state.value.distanceMeters, 0.0)
        assertEquals(paused.elapsedMillis, walk.gps.state.value.elapsedMillis)
        assertEquals(paused.distanceMeters, store.load(walk.quest, uid)!!.distanceMeters, 0.0)

        // A new engine models process recreation: load is paused, and the first resume fix is an anchor.
        val restored = GpsRepository(Source(), {}, persistCheckpoint = store::checkpoint)
        engines += restored
        val checkpoint = checkNotNull(store.load(walk.quest, uid))
        main { restored.restore(checkpoint) }
        assertFalse(restored.state.value.isTracking)
        assertEquals(paused.sessionId, restored.state.value.sessionId)
        assertEquals(paused.elapsedMillis, restored.state.value.elapsedMillis)
    }

    @Test fun signOutStopsLocationAndAnotherQuestCannotTrackAtTheSameTime() = runBlocking {
        val first = walk()
        val second = walk()
        start(first)
        eventually { first.gps.state.value.isTracking }
        start(second)
        eventually { second.gps.state.value.status.contains("other active walk") }
        assertTrue(first.gps.state.value.isTracking)
        assertEquals(0, second.source.starts)
        main { auth.signOut() }
        eventually { !first.gps.state.value.isTracking && notification() == null }
        assertTrue(first.gps.state.value.status.contains("account changed"))
    }

    @Test fun deviceLocationOffPausesAndTargetReachedRemovesNotification() = runBlocking {
        val walk = walk(0.005)
        start(walk)
        eventually("Initial start failed: ${walk.gps.state.value.status}") { walk.gps.state.value.isTracking }
        shell("cmd location set-location-enabled false")
        eventually("Turning location off should pause GPS and dismiss notification") { !walk.gps.state.value.isTracking && notification() == null }
        assertTrue(walk.gps.state.value.status.contains("Enable device location"))
        shell("cmd location set-location-enabled true")
        eventually("Device location should be enabled before Resume") {
            androidx.core.location.LocationManagerCompat.isLocationEnabled(context.getSystemService(android.location.LocationManager::class.java))
        }
        start(walk)
        eventually("Resume failed") { walk.gps.state.value.isTracking }
        val timestamp = System.currentTimeMillis()
        main {
            walk.source.point(RoutePoint(60.0, 10.0, timestamp, 5f), true)
            walk.source.point(RoutePoint(60.00005, 10.0, timestamp + 3000, 5f), true)
        }
        eventually("Reaching target should stop GPS and dismiss notification") { walk.gps.state.value.distanceCompleted && notification() == null }
        assertEquals(2, walk.source.starts)
        walk.gps.flush()
        val store = RoomQuestTrackingStore(QuestBookDatabase.getDatabase(context))
        val result = store.finish(walk.quest, uid, walk.gps.state.value.sessionId, System.currentTimeMillis())
        assertTrue(result.distanceMeters!! >= 5)
    }

    @Test fun productionFactorySurvivesActivityRecreationAndNotificationOpensSameWalk() = runBlocking {
        val fixture = walk()
        val models = ViewModelStore()
        lateinit var tracking: QuestTrackingViewModel
        main {
            tracking = ViewModelProvider(models, QuestTrackingViewModel.Factory(context, fixture.quest) { true })
                .get(QuestTrackingViewModel::class.java)
        }
        try {
            eventually { !tracking.uiState.value.isLoading }
            main { tracking.start() }
            eventually("Production factory should start real Fused Location service") { tracking.gpsState.value.isTracking && notification() != null }
            val session = tracking.gpsState.value.sessionId
            activity.recreate()
            assertTrue(tracking.gpsState.value.isTracking)
            notification()!!.contentIntent.send()
            // The reopened details factory must attach to the retained controller, preserving its session.
            fun showsPause(node: android.view.accessibility.AccessibilityNodeInfo?): Boolean {
                if (node == null) return false
                if (node.text?.toString() == "Pause tracking") return true
                return (0 until node.childCount).any { showsPause(node.getChild(it)) }
            }
            fun revealPause(): Boolean {
                val root = instrumentation.uiAutomation.rootInActiveWindow
                if (showsPause(root)) return true
                fun scroll(node: android.view.accessibility.AccessibilityNodeInfo?): Boolean {
                    if (node == null) return false
                    if (node.isScrollable && node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) return true
                    return (0 until node.childCount).any { scroll(node.getChild(it)) }
                }
                // A smaller test display may place the button below the fold; opening the right
                // quest must remain verifiable without assuming a particular screen height.
                scroll(root)
                return false
            }
            eventually("Notification tap should open the actively tracking quest") { revealPause() }
            // Also close the entire app UI and reopen it through the same ongoing notification.
            activity.close()
            scenarioClosed = true
            assertTrue(tracking.gpsState.value.isTracking)
            notification()!!.contentIntent.send()
            eventually("Notification should reopen the walk after all app screens were closed") {
                revealPause()
            }
            eventually { tracking.gpsState.value.elapsedMillis >= 1000 }
            assertEquals(session, tracking.gpsState.value.sessionId)
            main { tracking.pause() }
            eventually { notification() == null }
        } finally {
            main { tracking.pause(); models.clear() }
        }
    }
}
