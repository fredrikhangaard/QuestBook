package no.ntnu.prog2007.questbook.location

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import no.ntnu.prog2007.questbook.MainActivity
import no.ntnu.prog2007.questbook.data.AppServices
import no.ntnu.prog2007.questbook.data.personal.PersonalQuestIds
import no.ntnu.prog2007.questbook.data.repository.QuestDependencies
import no.ntnu.prog2007.questbook.model.QuestStatus
import java.util.Locale

/**
 * Keeps a user-started walk alive while its screen is hidden or the phone is locked.
 *
 * The service owns lifetime and the notification, not distance calculations or database writes.
 * It shares the existing GPS engine and its ordered Room writer with quest details. No second
 * location listener or writer is created. Pausing anywhere therefore pauses the same session.
 */
class QuestTrackingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var request: Request? = null
    private var sessionJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_PAUSE) {
            request?.gps?.pauseWithReason("Tracking paused from notification")
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        val next = requests[intent?.getStringExtra(EXTRA_KEY)]
        if (next == null) {
            // Process death loses the in-memory request. Never restart GPS from a stale Intent:
            // reopening the quest restores the last durable checkpoint paused instead.
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        if (request?.gps?.state?.value?.isTracking == true) {
            if (request !== next) {
                next.gps.pauseWithReason("Pause your other active walk before starting this quest.")
                requests.remove(next.key)
            }
            return START_NOT_STICKY
        }
        sessionJob?.cancel()
        request = next
        try {
            check(AppServices.authRepository.currentUserId == next.userId) { "Sign in again before resuming tracking." }
            check(locationAvailable()) { "Enable device location and allow precise location before resuming." }
            createChannel()
            // Android requires foreground promotion before requesting location in the background.
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, notification(next), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            else startForeground(NOTIFICATION_ID, notification(next))
            next.gps.resumeTracking()
            sessionJob = scope.launch {
                try { supervise(next) }
                catch (error: CancellationException) { throw error }
                catch (_: Exception) {
                    // Repository/settings failures pause safely; they must not crash the app's service.
                    next.gps.pauseWithReason("Tracking paused because access could not be checked. Open the quest to resume.")
                    stopSelf()
                }
            }
        } catch (error: Exception) {
            next.gps.pauseWithReason(error.message ?: "Could not start tracking. Open the quest and try again.")
            stopSelfResult(startId)
        }
        // Respect force-stop, reboot and Android's Stop control. No alarm or sticky restart bypasses them.
        return START_NOT_STICKY
    }

    /** Checks identity and assignment even when no Compose screen is collecting repository updates. */
    private suspend fun supervise(active: Request): Unit = coroutineScope {
        val original = QuestDependencies.questRepository.getQuest(active.questId)
        launch {
            AppServices.authRepository.currentUserIdFlow.collectLatest { uid ->
                if (uid != active.userId) active.gps.pauseWithReason("Tracking paused because the signed-in account changed.")
            }
        }
        launch {
            QuestDependencies.questRepository.quests.collectLatest { quests ->
                val quest = quests.find { it.id == active.questId }
                if (quest == null || quest.assignedUserId != active.userId || quest.status == QuestStatus.COMPLETED ||
                    quest.groupId != original?.groupId || quest.type != no.ntnu.prog2007.questbook.model.QuestType.DISTANCE)
                    active.gps.pauseWithReason("Tracking paused because this quest is no longer available to you.")
            }
        }
        val quest = original
        if (quest != null && !PersonalQuestIds.isPersonalGroup(quest.groupId)) launch {
            AppServices.groupRepository.observeGroups(active.userId).collectLatest { groups ->
                if (groups.none { it.id == quest.groupId && active.userId in it.memberIds })
                    active.gps.pauseWithReason("Tracking paused because this group is no longer available to you.")
            }
        }
        launch {
            while (isActive) {
                // The service timer checkpoints elapsed time even when no position is accurate enough.
                if (!locationAvailable()) {
                    active.gps.pauseWithReason("Tracking paused. Enable device location and precise permission to resume.")
                    return@launch
                }
                // A pause can cancel this timer during a state emission. Do not repost its notification.
                if (!active.gps.state.value.isTracking) return@launch
                active.gps.tick()
                getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(active))
                delay(1000)
            }
        }
        active.gps.state.first { !it.isTracking }
        if (request === active) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        // Stop timers and access observers immediately; don't let a late timer repost a paused notification.
        coroutineContext.cancelChildren()
    }

    private fun locationAvailable(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
            LocationManagerCompat.isLocationEnabled(getSystemService(LocationManager::class.java))

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Active GPS tracking", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows an ongoing walk and lets you pause GPS without opening QuestBook."
                setShowBadge(false)
            })
    }

    private fun notification(active: Request): Notification {
        val open = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_QUEST, active.questId)
            putExtra(EXTRA_USER, active.userId)
        }
        val pause = PendingIntent.getService(this, 1, Intent(this, QuestTrackingService::class.java).setAction(ACTION_PAUSE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val state = active.gps.state.value
        val text = if (state.persistenceError != null) "GPS data could not be saved. Open QuestBook to retry."
            else String.format(Locale.US, "%.2f km • %d:%02d active • Tap to open", state.distanceMeters / 1000,
                state.elapsedMillis / 60000, state.elapsedMillis / 1000 % 60)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("QuestBook GPS tracking")
            .setContentText(text)
            .setContentIntent(PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .addAction(0, "Pause tracking", pause)
            .setOngoing(true).setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    override fun onDestroy() {
        // Normal service shutdown saves the final active time; abrupt process death uses the last checkpoint.
        request?.let { active ->
            active.gps.stopTracking()
            if (requests[active.key] === active) requests.remove(active.key)
        }
        scope.cancel()
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_QUEST = "trackingQuestId"
        const val EXTRA_USER = "trackingUserId"
        private const val EXTRA_KEY = "trackingKey"
        private const val ACTION_PAUSE = "no.ntnu.prog2007.questbook.PAUSE_TRACKING"
        private const val CHANNEL = "active-gps-tracking"
        private const val NOTIFICATION_ID = 701
        // Main-thread only. Requests are capabilities within this process, never persisted restart commands.
        private val requests = mutableMapOf<String, Request>()

        /** Called only from an explicit visible Start/Resume after permission and Room validation. */
        fun start(context: Context, key: String, questId: String, userId: String, gps: GpsRepository) {
            // Repeated taps must not replace the identity used by a running session's cleanup.
            requests.getOrPut(key) { Request(key, questId, userId, gps) }
            try {
                ContextCompat.startForegroundService(context, Intent(context, QuestTrackingService::class.java).putExtra(EXTRA_KEY, key))
            } catch (error: Exception) {
                requests.remove(key)
                throw error
            }
        }
    }

    private data class Request(val key: String, val questId: String, val userId: String, val gps: GpsRepository)
}
