package no.ntnu.prog2007.questbook.data.personal

import java.time.*
import no.ntnu.prog2007.questbook.data.tracking.QuestResultEntity
import org.junit.Assert.*
import org.junit.Test

/** Calendar boundaries and contribution rules are independent of Android and Firebase. */
class PersonalQuestRulesTest {
    private val zone = ZoneId.of("Europe/Oslo")
    private fun instant(date: String) = ZonedDateTime.parse(date).toInstant().toEpochMilli()

    @Test fun daysUseLocalMidnightAndWeeksStartMondayAcrossDst() {
        val day = questWindow(instant("2026-03-29T12:00:00+02:00[Europe/Oslo]"), zone, QuestCadence.DAILY)
        assertEquals(23 * 3600000L, day.end - day.start)
        val week = questWindow(instant("2026-11-08T12:00:00+01:00[Europe/Oslo]"), zone, QuestCadence.WEEKLY)
        assertEquals("2026-11-02", week.key)
        assertEquals("2026-10-26", questWindow(week.start, zone, QuestCadence.WEEKLY, -1).key)
    }
    @Test fun contributionsUseRealUnitsAndOneActiveDayPerCalendarDay() {
        val start = instant("2026-11-06T00:00:00+01:00[Europe/Oslo]")
        val end = start + 86400000
        val events = listOf(QuestActivity("walk", "q", start + 1000, 5000.0, 3600000),
            QuestActivity("push", "p", start + 2000, pushups = 30),
            QuestActivity("squat", "s", start + 3000, squats = 20),
            QuestActivity("water", "v", start + 4000, visit = "WATER"))
        fun progress(metric: ActivityMetric) = activityProgress(metric, events, start, end, zone)
        assertEquals(5000.0, progress(ActivityMetric.WALK_METERS), 0.0)
        assertEquals(60.0, progress(ActivityMetric.ACTIVE_MINUTES), 0.0)
        assertEquals(50.0, progress(ActivityMetric.BODYWEIGHT_REPS), 0.0)
        assertEquals(30.0, progress(ActivityMetric.PUSHUPS), 0.0)
        assertEquals(1.0, progress(ActivityMetric.OUTDOOR_VISITS), 0.0)
        assertEquals(1.0, progress(ActivityMetric.ACTIVE_DAYS), 0.0)
    }
    @Test fun startIsInclusiveAndNextPeriodIsExclusive() {
        val events = listOf(QuestActivity("a", "q", 100, 5000.0), QuestActivity("b", "q", 200, 9000.0))
        assertEquals(5000.0, activityProgress(ActivityMetric.WALK_METERS, events, 100, 200, zone), 0.0)
    }
    @Test fun gpsResultsAndGoalAwardsCannotDuplicatePhysicalDistance() {
        val gps = QuestResultEntity("gps", "walk", "group", "me", "Walk", "Me", 150, 60000, 5000.0, "session")
        val award = gps.copy(id = "award", questId = "weekly", gpsSessionId = null)
        val events = questActivities(listOf(gps, gps, award), emptyList())
        assertEquals(1, events.size)
        assertEquals(5000.0, activityProgress(ActivityMetric.WALK_METERS, events, 100, 200, zone), 0.0)
    }
    @Test fun weeklyPoolRemovesTimeAndDailyPoolHasVariedPromptsUsingExistingMetrics() {
        assertEquals(4, PersonalQuestPool.weeklyFamilies.size)
        assertFalse(PersonalQuestPool.weeklyFamilies.containsKey(ActivityMetric.ACTIVE_MINUTES))
        assertTrue(PersonalQuestPool.daily.size >= 100)
        assertTrue(PersonalQuestPool.groupDraws.any { it.description.contains("friend") })
        assertTrue(PersonalQuestPool.daily.none { it.metric == ActivityMetric.ACTIVE_MINUTES })
        assertEquals(PersonalQuestPool.daily.size, PersonalQuestPool.daily.map { it.id }.distinct().size)
        assertTrue(PersonalQuestPool.daily.any { it.metric == ActivityMetric.WATER_VISITS })
        assertTrue(PersonalQuestPool.daily.any { it.metric == ActivityMetric.PUSHUPS && it.target == 30.0 })
        assertTrue(PersonalQuestPool.daily.all { it.target > 0 })
    }
    @Test fun awardsCountSeparatelyInSummaryAndDoNotInflateActivityTime() {
        val window = QuestWindow("test", 100, 200, zone.id)
        val rows = listOf(QuestResultEntity("gps", "walk", "g", "me", "Walk", "Me", 150, 60000, 5000.0, "s"),
            QuestResultEntity("award", "weekly", "g", "me", "Weekly", "Me", 150, 0, null, null))
        val weekly = PersonalQuestEntity("weekly", "me", "t", "WEEKLY", "key", "WALK_METERS", "Weekly", "", "WALK_METERS", 5000.0, 100, 200, zone.id, 150)
        val summary = weeklyActivitySummary(rows, listOf(weekly), questActivities(rows, emptyList()), window)
        assertEquals(1, summary.completedQuests)
        assertEquals(1, summary.weeklyGoals)
        assertEquals(60000L, summary.activeMillis)
        assertEquals(5000.0, summary.distanceMeters, 0.0)
    }
    @Test fun summaryIncludesManualRepetitionsAndOutdoorVisits() {
        val manual = listOf(ActivityEntryEntity("push", "me", "p", 150, pushups = 30),
            ActivityEntryEntity("visit", "me", "v", 160, visitKind = "WATER"))
        val summary = weeklyActivitySummary(emptyList(), emptyList(), questActivities(emptyList(), manual), QuestWindow("w", 100, 200, zone.id))
        assertEquals(30, summary.bodyweightReps)
        assertEquals(1, summary.outdoorVisits)
        assertEquals(0.0, summary.distanceMeters, 0.0)
    }

}
