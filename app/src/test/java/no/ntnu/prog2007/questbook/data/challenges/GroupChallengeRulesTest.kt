package no.ntnu.prog2007.questbook.data.challenges

import java.time.ZoneId
import no.ntnu.prog2007.questbook.data.personal.*
import org.junit.Assert.*
import org.junit.Test

/** Real-unit aggregation and membership boundaries are independent of Firebase and Android. */
class GroupChallengeRulesTest {
    private val zone = ZoneId.of("Europe/Oslo")
    @Test fun membershipWindowsExcludeEarlierActivityAndLeaveRejoinGapsWithoutDuplicatingEvents() {
        val spells = listOf(GroupMembershipSpell("first", "g", "me", 100, 200), GroupMembershipSpell("again", "g", "me", 300))
        val events = listOf(QuestActivity("before", "q", 99, 9000.0), QuestActivity("joined", "q", 100, 1000.0),
            QuestActivity("left", "q", 200, 9000.0), QuestActivity("gap", "q", 250, 9000.0), QuestActivity("again", "q", 300, 2000.0))
        assertEquals(3000.0, groupContribution(ActivityMetric.WALK_METERS, events + events, spells, 0, 1000, zone), 0.0)
    }
    @Test fun walkStartedBeforeJoiningDoesNotGainRetroactiveCreditWhenItFinishesAfterJoining() {
        val event = QuestActivity("gps", "q", 150, 5000.0, startedAtMillis = 50)
        assertEquals(0.0, groupContribution(ActivityMetric.WALK_METERS, listOf(event), listOf(GroupMembershipSpell("s", "g", "me", 100)), 0, 200, zone), 0.0)
    }
    @Test fun genericRepsDoNotPretendToBePushupsAndCustomConfirmationIsNotAnOutdoorVisit() {
        val events = listOf(QuestActivity("reps", "q", 100, bodyweightReps = 30), QuestActivity("task", "c", 100, visit = "CONFIRMED"))
        assertEquals(30.0, activityProgress(ActivityMetric.BODYWEIGHT_REPS, events, 0, 200, zone), 0.0)
        assertEquals(0.0, activityProgress(ActivityMetric.PUSHUPS, events, 0, 200, zone), 0.0)
        assertEquals(0.0, activityProgress(ActivityMetric.OUTDOOR_VISITS, events, 0, 200, zone), 0.0)
        assertEquals(1.0, activityProgress(ActivityMetric.CONFIRMATIONS, events, 0, 200, zone), 0.0)
        assertEquals(0.0, activityProgress(ActivityMetric.ACTIVE_DAYS, events.filter { it.visit == "CONFIRMED" }, 0, 200, zone), 0.0)
    }
}
