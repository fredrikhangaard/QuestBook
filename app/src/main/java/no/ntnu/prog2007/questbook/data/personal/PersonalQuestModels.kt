package no.ntnu.prog2007.questbook.data.personal

import java.time.*
import java.time.temporal.TemporalAdjusters
import no.ntnu.prog2007.questbook.data.tracking.QuestResultEntity

/** Quantities keep their real units: repetitions never become kilometres or minutes. */
enum class ActivityMetric { WALK_METERS, ACTIVE_MINUTES, PUSHUPS, SQUATS, BODYWEIGHT_REPS, WATER_VISITS, PARK_VISITS, OUTDOOR_VISITS, ACTIVE_DAYS }
enum class QuestCadence { DAILY, WEEKLY }
data class QuestTemplate(val id: String, val title: String, val description: String, val metric: ActivityMetric, val target: Double)
data class QuestWindow(val key: String, val start: Long, val end: Long, val zone: String)

/** Calendar boundaries use local midnights (including DST); weeks begin on Monday. */
fun questWindow(now: Long, zone: ZoneId, cadence: QuestCadence, weekOffset: Long = 0): QuestWindow {
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val date = if (cadence == QuestCadence.DAILY) today
        else today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(weekOffset)
    val end = if (cadence == QuestCadence.DAILY) date.plusDays(1) else date.plusWeeks(1)
    return QuestWindow(date.toString(), date.atStartOfDay(zone).toInstant().toEpochMilli(),
        end.atStartOfDay(zone).toInstant().toEpochMilli(), zone.id)
}

object PersonalQuestIds {
    fun group(userId: String) = "personal:$userId"
    fun isPersonalGroup(groupId: String) = groupId.startsWith("personal:")
    fun isPersonalQuest(questId: String) = questId.startsWith("personal-")
}

/** Finite local pool: one daily draw, and a random weekly target within each of five families. */
object PersonalQuestPool {
    val daily = listOf(
        QuestTemplate("walk-2", "Walk 2 km", "Take a walk outdoors. Track with GPS or report your distance manually.", ActivityMetric.WALK_METERS, 2000.0),
        QuestTemplate("walk-3", "Walk 3 km", "Explore your surroundings on foot.", ActivityMetric.WALK_METERS, 3000.0),
        QuestTemplate("walk-5", "Walk 5 km", "Build up today's walking distance.", ActivityMetric.WALK_METERS, 5000.0),
        QuestTemplate("push-10", "Do 10 push-ups", "Use a variation that suits you. You can record repetitions in smaller sets.", ActivityMetric.PUSHUPS, 10.0),
        QuestTemplate("push-20", "Do 20 push-ups", "Record your sets manually; repetitions are self-reported.", ActivityMetric.PUSHUPS, 20.0),
        QuestTemplate("push-30", "Do 30 push-ups", "Split the repetitions into comfortable sets.", ActivityMetric.PUSHUPS, 30.0),
        QuestTemplate("squat-20", "Do 20 squats", "Record completed repetitions in one or more sets.", ActivityMetric.SQUATS, 20.0),
        QuestTemplate("squat-30", "Do 30 squats", "Choose a comfortable pace and record your completed sets.", ActivityMetric.SQUATS, 30.0),
        QuestTemplate("squat-40", "Do 40 squats", "You can spread the repetitions across your day.", ActivityMetric.SQUATS, 40.0),
        QuestTemplate("stretch-5", "Move or stretch for 5 minutes", "Record the time you actually spent moving or stretching.", ActivityMetric.ACTIVE_MINUTES, 5.0),
        QuestTemplate("move-10", "Be active for 10 minutes", "Choose an activity you enjoy and report the active minutes.", ActivityMetric.ACTIVE_MINUTES, 10.0),
        QuestTemplate("move-20", "Be active for 20 minutes", "Walking, a workout, or another activity can contribute.", ActivityMetric.ACTIVE_MINUTES, 20.0),
        QuestTemplate("water", "Visit a body of water", "Visit a lake, river, or the sea. Confirm the visit yourself; a photo is optional.", ActivityMetric.WATER_VISITS, 1.0),
        QuestTemplate("park", "Visit a park or green space", "Spend some time in a park or green space. Confirm your visit; a photo is optional.", ActivityMetric.PARK_VISITS, 1.0)
    )
    val weeklyFamilies = linkedMapOf(
        ActivityMetric.WALK_METERS to listOf(15000.0, 20000.0, 25000.0),
        ActivityMetric.ACTIVE_MINUTES to listOf(90.0, 120.0, 150.0),
        ActivityMetric.BODYWEIGHT_REPS to listOf(100.0, 150.0, 200.0),
        ActivityMetric.OUTDOOR_VISITS to listOf(2.0, 3.0, 4.0),
        ActivityMetric.ACTIVE_DAYS to listOf(3.0, 4.0, 5.0)
    )
    fun weekly(metric: ActivityMetric, target: Double): QuestTemplate {
        val title = when (metric) {
            ActivityMetric.WALK_METERS -> "Walk ${(target / 1000).toInt()} km this week"
            ActivityMetric.ACTIVE_MINUTES -> "Be active for ${target.toInt()} minutes"
            ActivityMetric.BODYWEIGHT_REPS -> "Do ${target.toInt()} bodyweight repetitions"
            ActivityMetric.OUTDOOR_VISITS -> "Make ${target.toInt()} outdoor visits"
            else -> "Be active on ${target.toInt()} days"
        }
        return QuestTemplate("weekly-${metric.name}-${target.toInt()}", title,
            "Saved daily and group activities contribute to matching goals. Push-ups and squats both count as bodyweight repetitions.", metric, target)
    }
}

/** One physical activity is represented once, even if it advances several goals. */
data class QuestActivity(val id: String, val questId: String, val at: Long, val distanceMeters: Double = 0.0,
    val activeMillis: Long = 0, val pushups: Int = 0, val squats: Int = 0, val visit: String? = null)

fun questActivities(results: List<QuestResultEntity>, manual: List<ActivityEntryEntity>): List<QuestActivity> =
    (results.filter { it.gpsSessionId != null }.map { QuestActivity("gps:${it.id}", it.questId,
        it.completedAtMillis, it.distanceMeters?.takeIf { d -> d.isFinite() && d >= 0 } ?: 0.0,
        it.durationMillis.coerceAtLeast(0)) } + manual.map { QuestActivity("manual:${it.id}", it.questId,
        it.occurredAtMillis, it.distanceMeters, it.activeMillis, it.pushups, it.squats, it.visitKind) })
        .distinctBy { it.id }.sortedBy { it.at }

fun activityProgress(metric: ActivityMetric, events: List<QuestActivity>, start: Long, end: Long, zone: ZoneId): Double {
    val eligible = events.filter { it.at >= start && it.at < end }
    return when (metric) {
        ActivityMetric.WALK_METERS -> eligible.sumOf { it.distanceMeters }
        ActivityMetric.ACTIVE_MINUTES -> eligible.sumOf { it.activeMillis }.toDouble() / 60000
        ActivityMetric.PUSHUPS -> eligible.sumOf { it.pushups }.toDouble()
        ActivityMetric.SQUATS -> eligible.sumOf { it.squats }.toDouble()
        ActivityMetric.BODYWEIGHT_REPS -> eligible.sumOf { it.pushups + it.squats }.toDouble()
        ActivityMetric.WATER_VISITS -> eligible.count { it.visit == "WATER" }.toDouble()
        ActivityMetric.PARK_VISITS -> eligible.count { it.visit == "PARK" }.toDouble()
        ActivityMetric.OUTDOOR_VISITS -> eligible.count { it.visit != null }.toDouble()
        ActivityMetric.ACTIVE_DAYS -> eligible.filter { it.distanceMeters > 0 || it.activeMillis > 0 || it.pushups > 0 || it.squats > 0 || it.visit != null }
            .map { Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate() }.distinct().size.toDouble()
    }
}

data class PersonalQuestProgress(val assignment: PersonalQuestEntity, val value: Double,
    val hasResult: Boolean = false, val hasSavedSession: Boolean = false) {
    val complete get() = assignment.completedAtMillis != null
    val fraction get() = (value / assignment.target).toFloat().coerceIn(0f, 1f)
}

data class WeeklyActivitySummary(val start: Long, val end: Long, val completedQuests: Int,
    val distanceMeters: Double, val activeMillis: Long, val activeDays: Int, val weeklyGoals: Int,
    val bodyweightReps: Int = 0, val outdoorVisits: Int = 0)

fun weeklyActivitySummary(results: List<QuestResultEntity>, assignments: List<PersonalQuestEntity>,
    events: List<QuestActivity>, window: QuestWindow): WeeklyActivitySummary {
    val weeklyIds = assignments.filter { it.cadence == "WEEKLY" }.map { it.questId }.toSet()
    val weekResults = results.filter { it.completedAtMillis >= window.start && it.completedAtMillis < window.end }
    return WeeklyActivitySummary(window.start, window.end, weekResults.count { it.questId !in weeklyIds },
        activityProgress(ActivityMetric.WALK_METERS, events, window.start, window.end, ZoneId.of(window.zone)),
        (activityProgress(ActivityMetric.ACTIVE_MINUTES, events, window.start, window.end, ZoneId.of(window.zone)) * 60000).toLong(),
        activityProgress(ActivityMetric.ACTIVE_DAYS, events, window.start, window.end, ZoneId.of(window.zone)).toInt(),
        weekResults.count { it.questId in weeklyIds },
        activityProgress(ActivityMetric.BODYWEIGHT_REPS, events, window.start, window.end, ZoneId.of(window.zone)).toInt(),
        activityProgress(ActivityMetric.OUTDOOR_VISITS, events, window.start, window.end, ZoneId.of(window.zone)).toInt())
}
