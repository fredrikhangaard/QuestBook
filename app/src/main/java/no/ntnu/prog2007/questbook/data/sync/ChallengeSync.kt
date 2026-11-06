package no.ntnu.prog2007.questbook.data.sync

import kotlinx.coroutines.awaitCancellation
import no.ntnu.prog2007.questbook.model.Group

/**
 * Keeps the group quests in Room and in the cloud in step.
 *
 * Room stays the source the screens read from. The sync only adds rows to Room
 * (download) and copies this account's own rows to the cloud (upload).
 */
interface ChallengeSync {

    /** Downloads the cloud rows for [groups] into Room and keeps doing so until cancelled. */
    suspend fun listen(uid: String, groups: List<Group>)

    /**
     * Uploads this account's rows that the cloud does not have yet. Safe to call often:
     * a row that is already uploaded is skipped. [name] is the username others will see.
     */
    suspend fun push(uid: String, name: String, groups: List<Group>)

    /** Does nothing. For tests that must not touch Firebase. */
    object None : ChallengeSync {
        override suspend fun listen(uid: String, groups: List<Group>) = awaitCancellation()
        override suspend fun push(uid: String, name: String, groups: List<Group>) = Unit
    }
}
