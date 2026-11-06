package no.ntnu.prog2007.questbook.data.personal

import java.time.*
import java.time.temporal.TemporalAdjusters
import no.ntnu.prog2007.questbook.data.tracking.QuestResultEntity

/** Quantities keep their real units: repetitions never become kilometres or minutes. */
enum class ActivityMetric { WALK_METERS, ACTIVE_MINUTES, PUSHUPS, SQUATS, BODYWEIGHT_REPS, WATER_VISITS, PARK_VISITS, OUTDOOR_VISITS, ACTIVE_DAYS, CONFIRMATIONS }
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

/** Prompts add variety, while the metric is the only measured completion requirement. Photos are always optional. */
object PersonalQuestPool {
    private val discoveries = listOf(
        "Rock detective" to "Look for a weird-looking rock.",
        "Cloud collector" to "Find a cloud that reminds you of something.",
        "Colour hunt" to "Look for three things in your favourite colour.",
        "Tiny explorer" to "Notice a small detail you normally walk past.",
        "View finder" to "Look for a nice view along your route.",
        "Doorway detective" to "Find an interesting door or doorway.",
        "Tree character" to "Look for a tree with an unusual shape.",
        "Street art scout" to "Look for a mural, sculpture or other public art.",
        "Reflection hunter" to "Look for an interesting reflection.",
        "Pattern spotter" to "Find a pattern in nature or your neighbourhood.",
        "New perspective" to "Explore a path you do not usually take.",
        "Sound safari" to "Pause somewhere and notice the sounds around you.",
        "Season spotter" to "Look for a sign of the current season.",
        "Shadow shapes" to "Look for a funny-looking shadow.",
        "Local landmark" to "Look for a landmark in your neighbourhood.")
    private val social = listOf(
        "Walking catch-up" to "Invite a friend for a catch-up walk.",
        "Photo buddies" to "Walk with a friend and each choose a favourite view.",
        "Route swap" to "Ask a friend to suggest your walking route.",
        "Family adventure" to "Invite someone in your family for a walk.",
        "Neighbourhood tour" to "Show a friend a place you like nearby.",
        "Walking conversation" to "Invite someone for a walk and a conversation.",
        "Shared discovery" to "Look for something unusual with a friend.",
        "Meet halfway" to "Suggest meeting a friend during your walk.")
    private fun walks(prompts: List<Pair<String, String>>, tag: String) = prompts.flatMapIndexed { i, (name, prompt) ->
        listOf(1, 2, 3, 5).map { km -> QuestTemplate("$tag-$i-$km", "$name · Walk $km km",
            "$prompt The goal is the distance walked; the discovery, company and photo are optional.", ActivityMetric.WALK_METERS, km * 1000.0) }
    }
    private val exercises = listOf("Small sets", "Fresh start", "Little by little", "Rep rally", "Your pace", "Movement break")
        .flatMapIndexed { i, name -> listOf(ActivityMetric.PUSHUPS, ActivityMetric.SQUATS, ActivityMetric.BODYWEIGHT_REPS).flatMap { metric ->
            listOf(10, 20, 30).map { reps -> QuestTemplate("reps-$i-$metric-$reps",
                "$name · $reps ${when(metric) { ActivityMetric.PUSHUPS -> "push-ups"; ActivityMetric.SQUATS -> "squats"; else -> "bodyweight reps" }}",
                "Record your repetitions in one or more sets. A photo of your activity is optional.", metric, reps.toDouble()) }
        } }
    private val visits = listOf(
        "Water wanderer" to "Notice the colours or reflections at a lake, river or the sea.",
        "Water soundtrack" to "Visit a body of water and listen for a moment.",
        "Waterside detail" to "Look for an interesting detail beside the water.",
        "Water view" to "Find a view of a body of water.",
        "Green escape" to "Visit a park or green space and notice something new.",
        "Leaf detective" to "Look for an interesting leaf in a green space.",
        "Nature textures" to "Notice different textures in a park or green space.",
        "Park explorer" to "Explore a different part of a park or green space.")
        .mapIndexed { i, (name, prompt) -> QuestTemplate("visit-$i", name, "$prompt Confirm your visit; a photo is optional.",
            if (i < 4) ActivityMetric.WATER_VISITS else ActivityMetric.PARK_VISITS, 1.0) }
    val daily = walks(discoveries, "discover") + exercises + visits
    val groupDraws = walks(social, "social") + daily
    // Active duration remains a result statistic, but is no longer offered as a quest target.
    val weeklyFamilies = linkedMapOf(
        ActivityMetric.WALK_METERS to listOf(15000.0, 20000.0, 25000.0),
        ActivityMetric.BODYWEIGHT_REPS to listOf(100.0, 150.0, 200.0),
        ActivityMetric.OUTDOOR_VISITS to listOf(2.0, 3.0, 4.0),
        ActivityMetric.ACTIVE_DAYS to listOf(3.0, 4.0, 5.0))
    fun weekly(metric: ActivityMetric, target: Double): QuestTemplate {
        val title = when (metric) {
            ActivityMetric.WALK_METERS -> "Walk ${(target / 1000).toInt()} km this week"
            ActivityMetric.BODYWEIGHT_REPS -> "Do ${target.toInt()} bodyweight repetitions"
            ActivityMetric.OUTDOOR_VISITS -> "Make ${target.toInt()} outdoor visits"
            else -> "Be active on ${target.toInt()} days"
        }
        return QuestTemplate("weekly-${metric.name}-${target.toInt()}", title, "", metric, target)
    }
}

/** One physical activity is represented once, even if it advances several goals. */
data class QuestActivity(val id: String, val questId: String, val at: Long, val distanceMeters: Double = 0.0,
    val activeMillis: Long = 0, val pushups: Int = 0, val squats: Int = 0, val visit: String? = null, val bodyweightReps: Int = 0, val startedAtMillis: Long? = null)

fun questActivities(results: List<QuestResultEntity>, manual: List<ActivityEntryEntity>): List<QuestActivity> =
    (results.filter { it.gpsSessionId != null }.map { QuestActivity("gps:${it.id}", it.questId,
        it.completedAtMillis, it.distanceMeters?.takeIf { d -> d.isFinite() && d >= 0 } ?: 0.0,
        it.durationMillis.coerceAtLeast(0)) } + manual.map { QuestActivity("manual:${it.id}", it.questId,
        it.occurredAtMillis, it.distanceMeters, it.activeMillis, it.pushups, it.squats, it.visitKind, it.bodyweightReps) })
        .distinctBy { it.id }.sortedBy { it.at }

fun activityProgress(metric: ActivityMetric, events: List<QuestActivity>, start: Long, end: Long, zone: ZoneId): Double {
    val eligible = events.filter { it.at >= start && it.at < end }
    return when (metric) {
        ActivityMetric.WALK_METERS -> eligible.sumOf { it.distanceMeters }
        ActivityMetric.ACTIVE_MINUTES -> eligible.sumOf { it.activeMillis }.toDouble() / 60000
        ActivityMetric.PUSHUPS -> eligible.sumOf { it.pushups }.toDouble()
        ActivityMetric.SQUATS -> eligible.sumOf { it.squats }.toDouble()
        ActivityMetric.BODYWEIGHT_REPS -> eligible.sumOf { it.pushups + it.squats + it.bodyweightReps }.toDouble()
        ActivityMetric.WATER_VISITS -> eligible.count { it.visit == "WATER" }.toDouble()
        ActivityMetric.PARK_VISITS -> eligible.count { it.visit == "PARK" }.toDouble()
        ActivityMetric.CONFIRMATIONS -> eligible.count { it.visit == "CONFIRMED" }.toDouble()
        ActivityMetric.OUTDOOR_VISITS -> eligible.count { it.visit != null && it.visit != "CONFIRMED" }.toDouble()
        ActivityMetric.ACTIVE_DAYS -> eligible.filter { it.distanceMeters > 0 || it.activeMillis > 0 || it.pushups > 0 || it.squats > 0 || it.bodyweightReps > 0 || (it.visit != null && it.visit != "CONFIRMED") }
            .map { Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate() }.distinct().size.toDouble()
    }
}

data class PersonalQuestProgress(val assignment: PersonalQuestEntity, val value: Double,
    val hasResult: Boolean = false, val hasSavedSession: Boolean = false) {
    val complete get() = assignment.completedAtMillis != null
    val fraction get() = if (complete) 1f else (value / assignment.target).toFloat().coerceIn(0f, 1f)
}

data class WeeklyActivitySummary(val start: Long, val end: Long, val completedQuests: Int,
    val distanceMeters: Double, val activeMillis: Long, val activeDays: Int, val weeklyGoals: Int,
    val bodyweightReps: Int = 0, val outdoorVisits: Int = 0)

fun weeklyActivitySummary(results: List<QuestResultEntity>, assignments: List<PersonalQuestEntity>,
    events: List<QuestActivity>, window: QuestWindow): WeeklyActivitySummary {
    val weeklyIds = assignments.filter { (it.cadence in listOf("WEEKLY", "GROUP_AWARD") || it.templateId.startsWith("weekly-")) }.map { it.questId }.toSet()
    val weekResults = results.filter { it.completedAtMillis >= window.start && it.completedAtMillis < window.end }
    return WeeklyActivitySummary(window.start, window.end, weekResults.count { it.questId !in weeklyIds },
        activityProgress(ActivityMetric.WALK_METERS, events, window.start, window.end, ZoneId.of(window.zone)),
        (activityProgress(ActivityMetric.ACTIVE_MINUTES, events, window.start, window.end, ZoneId.of(window.zone)) * 60000).toLong(),
        activityProgress(ActivityMetric.ACTIVE_DAYS, events, window.start, window.end, ZoneId.of(window.zone)).toInt(),
        weekResults.count { it.questId in weeklyIds },
        activityProgress(ActivityMetric.BODYWEIGHT_REPS, events, window.start, window.end, ZoneId.of(window.zone)).toInt(),
        activityProgress(ActivityMetric.OUTDOOR_VISITS, events, window.start, window.end, ZoneId.of(window.zone)).toInt())
}
