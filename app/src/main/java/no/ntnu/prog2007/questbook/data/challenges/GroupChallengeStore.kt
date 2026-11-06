package no.ntnu.prog2007.questbook.data.challenges

import androidx.room.withTransaction
import kotlinx.coroutines.flow.first
import java.time.ZoneId
import java.util.UUID
import kotlin.random.Random
import no.ntnu.prog2007.questbook.data.local.QuestEntity
import no.ntnu.prog2007.questbook.data.personal.*
import no.ntnu.prog2007.questbook.data.photo.MemoryPhotoEntity
import no.ntnu.prog2007.questbook.data.tracking.QuestResultEntity
import no.ntnu.prog2007.questbook.location.database.QuestBookDatabase
import no.ntnu.prog2007.questbook.model.*

/** UI receives only data for the current user's current groups, never another account's private routes. */
data class GroupChallengeSnapshot(val challenges: List<GroupChallengeEntity> = emptyList(),
    val attempts: List<QuestAttemptEntity> = emptyList(), val progress: Map<String, Double> = emptyMap(),
    val contributions: Map<String, Map<String, Double>> = emptyMap(),
    val results: List<QuestResultEntity> = emptyList(), val photos: List<MemoryPhotoEntity> = emptyList(),
    val pendingWalks: Map<String, String> = emptyMap())

/** Pure projection: an event contributes once, even if several membership spells cover its timestamp. */
fun groupContribution(metric: ActivityMetric, events: List<QuestActivity>, spells: List<GroupMembershipSpell>,
    start: Long, end: Long, zone: ZoneId): Double = activityProgress(metric,
    events.distinctBy { it.id }.filter { event -> spells.any { (event.startedAtMillis ?: event.at) >= it.joinedAtMillis &&
        (it.leftAtMillis == null || event.at < it.leftAtMillis) } }, start, end, zone)

/** Room transactions define draws, membership and attempts; Firestore can mirror these explicit records later. */
class GroupChallengeStore(private val db: QuestBookDatabase, private val random: Random = Random.Default) {
    private val dao get() = db.groupChallengeDao()
    private fun stable(text: String) = UUID.nameUUIDFromBytes(text.toByteArray(Charsets.UTF_8)).toString()

    /** Snapshot membership before generating exactly one weekly goal; never resize a stored weekly target. */
    suspend fun refresh(uid: String, groups: List<Group>, now: Long, zone: ZoneId): GroupChallengeSnapshot = db.withTransaction {
        require(uid.isNotBlank())
        val visible = groups.filter { uid in it.memberIds }
        val spells = dao.spells()
        // Only an observation by the account itself can close all its absent groups. Other members
        // are updated only within groups this account can currently observe.
        spells.filter { it.leftAtMillis == null && (it.userId == uid || visible.any { g -> g.id == it.groupId }) }
            .filter { spell -> visible.none { it.id == spell.groupId && spell.userId in it.memberIds } }
            .forEach { dao.updateSpell(it.copy(leftAtMillis = now)) }
        for (group in visible) {
            group.memberIds.distinct().forEach { member ->
                if (spells.none { it.groupId == group.id && it.userId == member && it.leftAtMillis == null })
                    dao.insertSpell(GroupMembershipSpell(UUID.randomUUID().toString(), group.id, member, now))
            }
            val knownParents = dao.challenges().map { it.questId }.toSet()
            val knownAttempts = dao.attempts().map { it.attemptId }.toSet()
            for (old in db.questDao().observeQuests().first().filter { it.groupId == group.id &&
                it.id !in knownParents && it.id !in knownAttempts && !it.id.startsWith("attempt-") }) {
                val metric = when(QuestType.valueOf(old.type)) {
                    QuestType.DISTANCE -> ActivityMetric.WALK_METERS; QuestType.WORKOUT -> ActivityMetric.CONFIRMATIONS
                    QuestType.VISIT_PLACE -> ActivityMetric.OUTDOOR_VISITS; else -> ActivityMetric.CONFIRMATIONS }
                val target = if (metric == ActivityMetric.WALK_METERS) (old.requiredDistanceKm ?: continue) * 1000
                    else if (metric == ActivityMetric.BODYWEIGHT_REPS) 20.0 else 1.0
                if (!target.isFinite() || target <= 0) continue
                val result = db.trackingDao().getResult(old.id)
                val session = db.trackingDao().getSession(old.id)
                val owner = result?.userId ?: session?.userId
                val parentId = if (owner == null) old.id else "group-open-legacy-${old.id}"
                val definition = GroupChallengeEntity(parentId, group.id, "OPEN", metric.name, target,
                    "", old.createdAtMillis, Long.MAX_VALUE, zone.id, group.memberIds.distinct().size, "legacy")
                if (owner == null) {
                    db.questDao().updateQuest(old.copy(assignedTo = "Everyone", assignedUserId = null))
                    dao.insertChallenge(definition)
                } else {
                    // Preserve the original route/result/photo IDs as the first member's attempt.
                    insert(old.toQuest().copy(id = parentId, assignedUserId = null, assignedTo = "Everyone", status = QuestStatus.NOT_STARTED), definition)
                    dao.insertAttempt(QuestAttemptEntity(old.id, parentId, owner))
                    if (db.personalQuestDao().getAssignment(old.id) == null)
                        db.personalQuestDao().insertAssignment(PersonalQuestEntity(old.id, owner, "legacy", "ATTEMPT", "", old.id,
                            old.title, old.description, metric.name, target, old.createdAtMillis, Long.MAX_VALUE, zone.id, result?.completedAtMillis))
                }
            }
            // Keep one local group calendar even if the phone's timezone changes during a week.
            val calendar = dao.challenges().filter { it.groupId == group.id && it.kind == "WEEKLY" }
                .maxByOrNull { it.startMillis }?.zoneId?.let(ZoneId::of) ?: zone
            val window = questWindow(now, calendar, QuestCadence.WEEKLY)
            val id = "group-weekly-${stable("${group.id}|${window.key}")}"
            if (dao.challenges().none { it.groupId == group.id && it.kind == "WEEKLY" && it.periodKey == window.key }) {
                val choices = listOf(ActivityMetric.WALK_METERS, ActivityMetric.BODYWEIGHT_REPS, ActivityMetric.OUTDOOR_VISITS)
                // Stable choice avoids repeated rerolls. The persisted row is authoritative for this device.
                val metric = choices[Math.floorMod(id.hashCode(), choices.size)]
                val perMember = when (metric) { ActivityMetric.WALK_METERS -> 5000.0; ActivityMetric.BODYWEIGHT_REPS -> 50.0; else -> 1.0 }
                val count = group.memberIds.distinct().size.coerceAtLeast(1)
                val template = PersonalQuestPool.weekly(metric, perMember * count)
                insert(Quest(id, group.id, if (metric == ActivityMetric.WALK_METERS) QuestType.DISTANCE else QuestType.CUSTOM,
                    template.title, "Together as a group", "Everyone", QuestStatus.NOT_STARTED, createdAtMillis = now),
                    GroupChallengeEntity(id, group.id, "WEEKLY", metric.name, template.target, window.key,
                        window.start, window.end, window.zone, count, template.id))
            }
        }
        val beforeAwards = snapshot(uid, visible)
        // A group achievement is a separate award, not a second physical activity. Each contributor
        // gets one owned result for optional photos without fabricating someone else's completion.
        beforeAwards.challenges.filter { it.kind == "WEEKLY" && now >= it.startMillis && now < it.endMillis &&
            (beforeAwards.progress[it.questId] ?: 0.0) >= it.target &&
            (beforeAwards.contributions[it.questId]?.get(uid) ?: 0.0) > 0 }.forEach { goal ->
            if (dao.attempt(goal.questId, uid) == null) {
                val parent = checkNotNull(db.questDao().getQuest(goal.questId))
                val child = "attempt-${stable("${goal.questId}|$uid") }"
                db.questDao().insertQuest(parent.copy(id = child, type = QuestType.CUSTOM.name,
                    assignedUserId = uid, assignedTo = "You", status = QuestStatus.COMPLETED.name))
                db.personalQuestDao().insertAssignment(PersonalQuestEntity(child, uid, goal.templateId, "GROUP_AWARD", goal.periodKey,
                    child, parent.title, parent.description, goal.metric, goal.target, goal.startMillis, goal.endMillis, goal.zoneId, now))
                db.trackingDao().insertResult(QuestResultEntity("award-$child", child, goal.groupId, uid, parent.title, "You", now, 0, null, null))
                dao.insertAttempt(QuestAttemptEntity(child, goal.questId, uid))
            }
        }
        snapshot(uid, visible)
    }

    /** Create is open to every member; the parent is never marked completed when one attempt finishes. */
    suspend fun create(uid: String, group: Group, quest: Quest, metric: ActivityMetric, target: Double, now: Long) = db.withTransaction {
        require(uid in group.memberIds && quest.groupId == group.id && quest.createdByUserId == uid)
        require(quest.title.isNotBlank() && target.isFinite() && target > 0)
        require(metric in listOf(ActivityMetric.WALK_METERS, ActivityMetric.BODYWEIGHT_REPS, ActivityMetric.OUTDOOR_VISITS, ActivityMetric.CONFIRMATIONS))
        require(target <= if (metric == ActivityMetric.WALK_METERS) 100000 else 10000)
        if (metric != ActivityMetric.WALK_METERS) require(target % 1.0 == 0.0)
        insert(quest.copy(assignedUserId = null, assignedTo = "Everyone", status = QuestStatus.NOT_STARTED),
            GroupChallengeEntity(quest.id, group.id, "OPEN", metric.name, target, "", now, Long.MAX_VALUE,
                ZoneId.systemDefault().id, group.memberIds.distinct().size, "custom"))
    }

    /** A draw has a daily-sized target, a weekly deadline, and is automatically owned by the drawer. */
    suspend fun draw(uid: String, group: Group, name: String, operationId: String, now: Long, zone: ZoneId): String = db.withTransaction {
        require(uid in group.memberIds)
        val id = "group-draw-${stable("$uid|${group.id}|$operationId")}"
        dao.challenge(id)?.let { return@withTransaction id }
        val recent = dao.challenges().filter { it.groupId == group.id && it.assigneeId == uid }.takeLast(12).map { it.templateId }.toSet()
        val template = PersonalQuestPool.groupDraws.filter { it.id !in recent }.random(random)
        val calendar = dao.challenges().filter { it.groupId == group.id && it.kind == "WEEKLY" }
            .maxByOrNull { it.startMillis }?.zoneId?.let(ZoneId::of) ?: zone
        val window = questWindow(now, calendar, QuestCadence.WEEKLY)
        val quest = Quest(id, group.id, if (template.metric == ActivityMetric.WALK_METERS) QuestType.DISTANCE else QuestType.CUSTOM,
            template.title, template.description, name, QuestStatus.NOT_STARTED,
            requiredDistanceKm = if (template.metric == ActivityMetric.WALK_METERS) template.target / 1000 else null,
            createdByUserId = uid, assignedUserId = uid, createdAtMillis = now)
        insert(quest, GroupChallengeEntity(id, group.id, "DRAW", template.metric.name, template.target,
            window.key, now, window.end, window.zone, group.memberIds.distinct().size, template.id, uid))
        open(uid, group, id, name, now)
        id
    }

    private suspend fun insert(quest: Quest, definition: GroupChallengeEntity) {
        db.questDao().insertQuest(QuestEntity.fromQuest(quest))
        dao.insertChallenge(definition)
    }

    /** Each member gets a stable child quest. Existing Room sessions/results remain unique per child. */
    suspend fun open(uid: String, group: Group, id: String, name: String, now: Long): String = db.withTransaction {
        require(uid in group.memberIds)
        val row = checkNotNull(dao.challenge(id))
        require(row.groupId == group.id && row.kind != "WEEKLY")
        require(row.assigneeId == null || row.assigneeId == uid) { "This drawn quest belongs to another member." }
        dao.attempt(id, uid)?.let { return@withTransaction it.attemptId }
        check(now < row.endMillis) { "This quest has ended." }
        val parent = checkNotNull(db.questDao().getQuest(id))
        val child = "attempt-${stable("$id|$uid") }"
        val metric = ActivityMetric.valueOf(row.metric)
        db.questDao().insertQuest(parent.copy(id = child, assignedUserId = uid, assignedTo = name,
            status = QuestStatus.NOT_STARTED.name, requiredDistanceKm = if (metric == ActivityMetric.WALK_METERS) row.target / 1000 else null,
            type = if (metric == ActivityMetric.WALK_METERS) QuestType.DISTANCE.name else QuestType.CUSTOM.name))
        db.personalQuestDao().insertAssignment(PersonalQuestEntity(child, uid, row.templateId, "ATTEMPT", row.periodKey,
            child, parent.title, parent.description, row.metric, row.target, now, row.endMillis, row.zoneId))
        dao.insertAttempt(QuestAttemptEntity(child, id, uid))
        child
    }

    /** Manual weekly input records a physical set/visit; it cannot overwrite the aggregate counter. */
    suspend fun recordWeekly(uid: String, group: Group, id: String, amount: Double, operation: String, now: Long) = db.withTransaction {
        require(uid in group.memberIds)
        val goal = checkNotNull(dao.challenge(id))
        require(goal.groupId == group.id && goal.kind == "WEEKLY")
        db.personalQuestDao().getEntry(operation)?.let {
            require(it.userId == uid && it.questId == id)
            return@withTransaction
        }
        check(now in goal.startMillis until goal.endMillis) { "This weekly goal has ended." }
        val metric = ActivityMetric.valueOf(goal.metric)
        require(metric in listOf(ActivityMetric.BODYWEIGHT_REPS, ActivityMetric.OUTDOOR_VISITS))
        val current = snapshot(uid, listOf(group)).progress[id] ?: 0.0
        require(amount.isFinite() && amount > 0 && amount % 1.0 == 0.0 && amount <= goal.target - current)
        if (metric == ActivityMetric.OUTDOOR_VISITS) require(amount == 1.0)
        db.personalQuestDao().insertEntry(ActivityEntryEntity(operation, uid, id, now,
            bodyweightReps = if (metric == ActivityMetric.BODYWEIGHT_REPS) amount.toInt() else 0,
            visitKind = if (metric == ActivityMetric.OUTDOOR_VISITS) "OUTDOOR" else null))
    }

    suspend fun snapshot(uid: String, groups: List<Group>): GroupChallengeSnapshot {
        val ids = groups.filter { uid in it.memberIds }.map { it.id }.toSet()
        val definitions = dao.challenges().filter { it.groupId in ids }
        val attempts = dao.attempts().filter { a -> definitions.any { it.questId == a.challengeId } }
        val allResults = dao.results()
        val entries = dao.entries()
        val spells = dao.spells()
        val questStarts = db.questDao().observeQuests().first().associate { it.id to it.startedAtMillis }
        val contribution = definitions.filter { it.kind == "WEEKLY" }.associate { goal -> goal.questId to
            spells.filter { it.groupId == goal.groupId }.map { it.userId }.distinct().associateWith { member ->
                groupContribution(ActivityMetric.valueOf(goal.metric), questActivities(allResults.filter { it.userId == member },
                    entries.filter { it.userId == member }).map { event ->
                        if (event.id.startsWith("gps:")) event.copy(startedAtMillis = questStarts[event.questId]) else event
                    }, spells.filter { it.groupId == goal.groupId && it.userId == member },
                    goal.startMillis, goal.endMillis, ZoneId.of(goal.zoneId))
            } }
        val results = allResults.filter { result -> result.groupId in ids && attempts.any { it.attemptId == result.questId } }
        return GroupChallengeSnapshot(definitions, attempts, contribution.mapValues { it.value.values.sum() }, contribution,
            results, dao.photos().filter { photo -> results.any { it.id == photo.resultId } },
            db.personalQuestDao().getAssignments(uid).filter { it.templateId.startsWith("bounded:") && it.completedAtMillis == null &&
                allResults.none { result -> result.questId == it.questId } }.associate { it.templateId.removePrefix("bounded:") to it.questId })
    }
}

/** Cards project the current member's completion; the shared quest's status is never mutated. */
fun groupQuestCards(quests: List<Quest>, snapshot: GroupChallengeSnapshot, uid: String?, groups: Set<String>): List<Quest> =
    quests.filter { quest -> quest.groupId in groups && !quest.id.startsWith("attempt-") &&
        snapshot.attempts.none { it.attemptId == quest.id } }.mapNotNull { quest ->
        val definition = snapshot.challenges.find { it.questId == quest.id }
        if (definition?.kind == "DRAW" && definition.assigneeId != uid) return@mapNotNull null
        val child = snapshot.attempts.find { it.challengeId == quest.id && it.userId == uid }
        val done = if (definition?.kind == "WEEKLY") (snapshot.progress[quest.id] ?: 0.0) >= definition.target
            else snapshot.results.any { it.questId == child?.attemptId }
        if (done) quest.copy(status = QuestStatus.COMPLETED) else quest
    }
