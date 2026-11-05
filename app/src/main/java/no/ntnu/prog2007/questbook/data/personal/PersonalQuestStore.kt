package no.ntnu.prog2007.questbook.data.personal

import androidx.room.withTransaction
import java.time.*
import java.util.UUID
import kotlin.random.Random
import no.ntnu.prog2007.questbook.data.local.QuestEntity
import no.ntnu.prog2007.questbook.data.tracking.QuestResultEntity
import no.ntnu.prog2007.questbook.location.database.QuestBookDatabase
import no.ntnu.prog2007.questbook.model.*

data class PersonalQuestSnapshot(val quests: List<PersonalQuestProgress>)

/** Local assignment and activity transactions; this class has no Firebase dependency. */
class PersonalQuestStore(private val db: QuestBookDatabase, private val random: Random = Random.Default) {
    private val dao get() = db.personalQuestDao()

    suspend fun refresh(uid: String, now: Long, zone: ZoneId): PersonalQuestSnapshot = db.withTransaction {
        require(uid.isNotBlank())
        var assignments = dao.getAssignments(uid)
        suspend fun assign(template: QuestTemplate, cadence: QuestCadence, window: QuestWindow, slot: String) {
            if (assignments.any { it.cadence == cadence.name && it.periodKey == window.key && it.slot == slot }) return
            val id = "personal-" + UUID.nameUUIDFromBytes("$uid|${cadence.name}|${window.key}|$slot".toByteArray(Charsets.UTF_8))
            val row = PersonalQuestEntity(id, uid, template.id, cadence.name, window.key, slot, template.title,
                template.description, template.metric.name, template.target, window.start, window.end, window.zone)
            dao.insertAssignment(row)
            // Reuse existing detail/GPS/result/photo storage; personal quests never need group membership.
            val type = if (cadence == QuestCadence.DAILY && template.metric == ActivityMetric.WALK_METERS) QuestType.DISTANCE
                else if (template.metric in listOf(ActivityMetric.WATER_VISITS, ActivityMetric.PARK_VISITS)) QuestType.VISIT_PLACE
                else QuestType.CUSTOM
            db.questDao().insertQuest(QuestEntity.fromQuest(Quest(id, PersonalQuestIds.group(uid), type,
                template.title, template.description, "You", QuestStatus.NOT_STARTED,
                requiredDistanceKm = if (type == QuestType.DISTANCE) template.target / 1000 else null,
                createdByUserId = uid, assignedUserId = uid, createdAtMillis = now)))
            assignments = assignments + row
        }
        val dailyWindow = questWindow(now, zone, QuestCadence.DAILY)
        val previous = assignments.firstOrNull { it.cadence == "DAILY" && it.periodKey != dailyWindow.key }?.templateId
        // Avoid yesterday's exact template without changing an already-persisted draw.
        val dailyPool = PersonalQuestPool.daily.filter { it.id != previous }
        if (assignments.none { it.cadence == "DAILY" && it.periodKey == dailyWindow.key })
            assign(dailyPool.random(random), QuestCadence.DAILY, dailyWindow, "daily")
        val week = questWindow(now, zone, QuestCadence.WEEKLY)
        PersonalQuestPool.weeklyFamilies.forEach { (metric, targets) ->
            if (assignments.none { it.cadence == "WEEKLY" && it.periodKey == week.key && it.slot == metric.name })
                assign(PersonalQuestPool.weekly(metric, targets.random(random)), QuestCadence.WEEKLY, week, metric.name)
        }
        reconcile(uid)
        snapshot(uid)
    }

    /** Computes awards from physical activities; an award itself never produces another activity. */
    private suspend fun reconcile(uid: String) {
        val results = db.trackingDao().getResults(uid)
        val events = questActivities(results, dao.getEntries(uid))
        for (assignment in dao.getAssignments(uid).filter { it.completedAtMillis == null }) {
            val eligible = if (assignment.cadence == "ACTIVITY") events.filter { it.questId == assignment.questId } else events
            val at = reachedAt(assignment, eligible) ?: continue
            // Do not invalidate a retained GPS writer; finish that saved activity first.
            if (db.trackingDao().getSession(assignment.questId) != null) continue
            val quest = db.questDao().getQuest(assignment.questId) ?: continue
            if (db.trackingDao().getResult(assignment.questId) == null) {
                val ownEvents = events.filter { it.questId == assignment.questId }
                val manualTime = ownEvents.sumOf { it.activeMillis }
                val ownDistance = ownEvents.sumOf { it.distanceMeters }
                db.trackingDao().insertResult(QuestResultEntity("award-${assignment.questId}", assignment.questId,
                    quest.groupId, uid, assignment.title, "You", at,
                    if (assignment.cadence != "WEEKLY") manualTime else 0,
                    if (assignment.cadence == "ACTIVITY" && ownDistance > 0) ownDistance else null, null))
            }
            db.questDao().updateQuest(quest.copy(status = QuestStatus.COMPLETED.name))
            dao.updateAssignment(assignment.copy(completedAtMillis = at))
        }
    }

    private fun reachedAt(row: PersonalQuestEntity, events: List<QuestActivity>): Long? {
        val metric = ActivityMetric.valueOf(row.metric)
        val dates = mutableSetOf<LocalDate>()
        var progress = 0.0
        for (event in events.filter { it.at >= row.periodStartMillis && it.at < row.periodEndMillis }) {
            val increment = activityProgress(metric, listOf(event), row.periodStartMillis, row.periodEndMillis, ZoneId.of(row.zoneId))
            if (metric == ActivityMetric.ACTIVE_DAYS) {
                if (increment > 0) dates.add(Instant.ofEpochMilli(event.at).atZone(ZoneId.of(row.zoneId)).toLocalDate())
                progress = dates.size.toDouble()
            } else progress += increment
            if (progress >= row.target) return event.at
        }
        return null
    }

    private suspend fun snapshot(uid: String): PersonalQuestSnapshot {
        val results = db.trackingDao().getResults(uid)
        val events = questActivities(results, dao.getEntries(uid))
        val resultIds = results.map { it.questId }.toSet()
        val sessions = db.trackingDao().getSessions(uid).map { it.questId }.toSet()
        return PersonalQuestSnapshot(dao.getAssignments(uid).map { row -> PersonalQuestProgress(row,
            activityProgress(ActivityMetric.valueOf(row.metric), if (row.cadence == "ACTIVITY") events.filter { it.questId == row.questId } else events, row.periodStartMillis,
                row.periodEndMillis, ZoneId.of(row.zoneId)), row.questId in resultIds, row.questId in sessions) })
    }

    /** Idempotency key prevents a double tap/retry from adding the same manual submission twice. */
    suspend fun logManual(uid: String, questId: String, quantity: Double, minutes: Double,
        operationId: String, now: Long) = db.withTransaction {
        val row = checkNotNull(dao.getAssignment(questId)) { "Quest not found." }
        require(row.userId == uid) { "This quest belongs to another account." }
        dao.getEntry(operationId)?.let {
            check(it.userId == uid && it.questId == questId)
            return@withTransaction
        }
        check(row.cadence != "WEEKLY" && now >= row.periodStartMillis && now < row.periodEndMillis) { "This daily quest has expired." }
        check(row.completedAtMillis == null) { "This daily quest is already complete." }
        check(db.trackingDao().getSession(questId) == null) { "Finish your saved GPS activity before adding manual progress." }
        require(quantity.isFinite() && quantity > 0 && minutes.isFinite() && minutes in 0.0..1440.0) { "Enter a valid amount and active time." }
        val metric = ActivityMetric.valueOf(row.metric)
        val allEvents = questActivities(db.trackingDao().getResults(uid), dao.getEntries(uid))
        val events = if (row.cadence == "ACTIVITY") allEvents.filter { it.questId == questId } else allEvents
        val remaining = (row.target - activityProgress(metric, events, row.periodStartMillis, row.periodEndMillis, ZoneId.of(row.zoneId))).coerceAtLeast(0.0)
        val amount = if (metric == ActivityMetric.WALK_METERS) quantity * 1000 else quantity
        require(amount <= remaining + 0.000001) { "Enter no more than the remaining target." }
        if (metric in listOf(ActivityMetric.PUSHUPS, ActivityMetric.SQUATS))
            require(amount % 1.0 == 0.0) { "Repetitions must be a whole number." }
        if (metric in listOf(ActivityMetric.WATER_VISITS, ActivityMetric.PARK_VISITS)) require(amount == 1.0)
        val activeMillis = ((if (metric == ActivityMetric.ACTIVE_MINUTES) quantity else minutes) * 60000).toLong()
        if (metric == ActivityMetric.ACTIVE_MINUTES) require(activeMillis > 0) { "Enter an active duration of at least one millisecond." }
        dao.insertEntry(ActivityEntryEntity(operationId, uid, questId, now,
            distanceMeters = if (metric == ActivityMetric.WALK_METERS) amount else 0.0,
            activeMillis = activeMillis,
            pushups = if (metric == ActivityMetric.PUSHUPS) amount.toInt() else 0,
            squats = if (metric == ActivityMetric.SQUATS) amount.toInt() else 0,
            visitKind = when (metric) { ActivityMetric.WATER_VISITS -> "WATER"; ActivityMetric.PARK_VISITS -> "PARK"; else -> null }))
        reconcile(uid)
    }

    /** A daily walk may already have progress from another saved activity; GPS tracks only the rest. */
    suspend fun prepareGps(uid: String, questId: String, now: Long) = db.withTransaction {
        val row = checkNotNull(dao.getAssignment(questId))
        require(row.userId == uid && row.cadence != "WEEKLY" && row.metric == ActivityMetric.WALK_METERS.name)
        if (db.trackingDao().getSession(questId) != null) return@withTransaction // An expired activity can still be saved.
        check(row.completedAtMillis == null && now >= row.periodStartMillis && now < row.periodEndMillis) { "This daily quest is no longer active." }
        val allEvents = questActivities(db.trackingDao().getResults(uid), dao.getEntries(uid))
        val events = if (row.cadence == "ACTIVITY") allEvents.filter { it.questId == questId } else allEvents
        val remaining = row.target - activityProgress(ActivityMetric.WALK_METERS, events, row.periodStartMillis, row.periodEndMillis, ZoneId.of(row.zoneId))
        check(remaining > 0) { "This goal has already been reached. Refresh the quest." }
        val quest = checkNotNull(db.questDao().getQuest(questId))
        db.questDao().updateQuest(quest.copy(requiredDistanceKm = remaining / 1000))
    }
    /** Extra activities make all weekly goals reachable without joining a group or rerolling a daily. */
    suspend fun createActivity(uid: String, metric: ActivityMetric, quantity: Double, minutes: Double,
        operationId: String, gps: Boolean, now: Long, zone: ZoneId): String = db.withTransaction {
        require(uid.isNotBlank())
        require(metric in listOf(ActivityMetric.WALK_METERS, ActivityMetric.PUSHUPS, ActivityMetric.SQUATS,
            ActivityMetric.ACTIVE_MINUTES, ActivityMetric.WATER_VISITS, ActivityMetric.PARK_VISITS))
        require(quantity.isFinite() && quantity > 0 && minutes.isFinite() && minutes in 0.0..1440.0)
        val amount = if (metric == ActivityMetric.WALK_METERS) quantity * 1000 else quantity
        require(amount <= when (metric) { ActivityMetric.WALK_METERS -> 100000.0; ActivityMetric.ACTIVE_MINUTES -> 1440.0; else -> 1000.0 })
        if (metric == ActivityMetric.ACTIVE_MINUTES) require(quantity >= 1)
        if (metric in listOf(ActivityMetric.PUSHUPS, ActivityMetric.SQUATS)) require(quantity % 1.0 == 0.0)
        if (metric in listOf(ActivityMetric.WATER_VISITS, ActivityMetric.PARK_VISITS)) require(quantity == 1.0)
        require(!gps || metric == ActivityMetric.WALK_METERS)
        val id = "personal-" + UUID.nameUUIDFromBytes("$uid|activity|$operationId".toByteArray(Charsets.UTF_8))
        dao.getAssignment(id)?.let { check(it.userId == uid); return@withTransaction id }
        val title = when (metric) {
            ActivityMetric.WALK_METERS -> "Walk $quantity km"
            ActivityMetric.PUSHUPS -> "${quantity.toInt()} push-ups"
            ActivityMetric.SQUATS -> "${quantity.toInt()} squats"
            ActivityMetric.ACTIVE_MINUTES -> "${quantity.toInt()} active minutes"
            ActivityMetric.WATER_VISITS -> "Visit a body of water"
            else -> "Visit a park or green space"
        }
        dao.insertAssignment(PersonalQuestEntity(id, uid, "extra-${metric.name}", "ACTIVITY",
            questWindow(now, zone, QuestCadence.DAILY).key, operationId, title,
            "A personal activity contributing to matching weekly goals. Photos are optional.", metric.name,
            amount, now, Long.MAX_VALUE, zone.id))
        db.questDao().insertQuest(QuestEntity.fromQuest(Quest(id, PersonalQuestIds.group(uid),
            if (gps) QuestType.DISTANCE else QuestType.CUSTOM, title, "Personal activity", "You", QuestStatus.NOT_STARTED,
            requiredDistanceKm = if (gps) quantity else null, createdByUserId = uid, assignedUserId = uid, createdAtMillis = now)))
        if (!gps) logManual(uid, id, quantity, minutes, operationId, now)
        id
    }

}
