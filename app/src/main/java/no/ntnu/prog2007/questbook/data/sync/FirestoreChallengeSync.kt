package no.ntnu.prog2007.questbook.data.sync

import android.util.Log
import androidx.room.withTransaction
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import no.ntnu.prog2007.questbook.data.challenges.GroupChallengeEntity
import no.ntnu.prog2007.questbook.data.local.QuestEntity
import no.ntnu.prog2007.questbook.data.personal.questActivities
import no.ntnu.prog2007.questbook.data.sync.ChallengeCloud.ACTIVITY
import no.ntnu.prog2007.questbook.data.sync.ChallengeCloud.ATTEMPTS
import no.ntnu.prog2007.questbook.data.sync.ChallengeCloud.CHALLENGES
import no.ntnu.prog2007.questbook.data.sync.ChallengeCloud.COMPLETIONS
import no.ntnu.prog2007.questbook.data.sync.ChallengeCloud.SPELLS
import no.ntnu.prog2007.questbook.location.database.QuestBookDatabase
import no.ntnu.prog2007.questbook.model.Group

/**
 * [ChallengeSync] backed by Firestore (see [ChallengeCloud] for the document layout).
 *
 * Upload: this account's rows are written with fixed document IDs, and `firestore.rules` only
 * allows creating them, never changing them. A retry or a second phone therefore cannot make a
 * duplicate. Writes are not awaited: Firestore keeps them in its offline queue and sends them
 * when the phone is online again.
 *
 * Download: one snapshot listener per group and collection. Other members' rows are inserted
 * with "insert if absent", so applying the same snapshot twice changes nothing. Room's flows
 * then update the screens as before.
 *
 * GPS routes and photos never leave the phone in this phase.
 */
class FirestoreChallengeSync(
    private val db: QuestBookDatabase,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val clock: () -> Long = System::currentTimeMillis
) : ChallengeSync {

    private val dao get() = db.groupChallengeDao()

    /** Document paths the cloud already has, or that this session already wrote. */
    private val known = ConcurrentHashMap.newKeySet<String>()

    /** "groupId/collection" pairs whose first snapshot has arrived. Uploads wait for it. */
    private val loaded = ConcurrentHashMap.newKeySet<String>()

    override suspend fun listen(uid: String, groups: List<Group>) {
        val since = clock() - ChallengeCloud.ACTIVITY_WINDOW_MILLIS
        coroutineScope {
            for (group in groups.filter { uid in it.memberIds }) {
                for (collection in ChallengeCloud.COLLECTIONS) {
                    launch {
                        val key = "${group.id}/$collection"
                        try {
                            documents(group.id, collection, since).collect { docs ->
                                saveSnapshot(uid, group, collection, docs)
                                loaded += key
                            }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            // One broken listener must not stop the others or crash the screen.
                            Log.w(TAG, "Stopped syncing $collection for group ${group.id}", error)
                        } finally {
                            loaded -= key
                        }
                    }
                }
            }
        }
    }

    override suspend fun push(uid: String, name: String, groups: List<Group>) {
        val now = clock()
        val challenges = dao.challenges()
        val myAttempts = dao.attempts().filter { it.userId == uid }
        val myResults = db.trackingDao().getResults(uid)
        val myActivity = questActivities(myResults, db.personalQuestDao().getEntries(uid))
            .filter { it.at >= now - ChallengeCloud.ACTIVITY_WINDOW_MILLIS }
        val spells = dao.spells()

        for (group in groups.filter { uid in it.memberIds }) {
            val root = groupDocument(group.id)
            fun ready(collection: String) = "${group.id}/$collection" in loaded

            if (ready(SPELLS)) {
                write(root.collection(SPELLS).document(uid),
                    mapOf("userId" to uid, "joinedAt" to FieldValue.serverTimestamp()))
            }

            val definitions = challenges.filter { it.groupId == group.id }
            if (ready(CHALLENGES)) {
                for (definition in definitions) {
                    val quest = db.questDao().getQuest(definition.questId) ?: continue
                    if (ChallengeCloud.isUploadable(definition, quest, uid)) {
                        write(root.collection(CHALLENGES).document(definition.questId),
                            ChallengeCloud.challengeToMap(definition, quest))
                    }
                }
            }

            val attempts = myAttempts.filter { attempt -> definitions.any { it.questId == attempt.challengeId } }
            if (ready(ATTEMPTS)) {
                for (attempt in attempts) {
                    write(root.collection(ATTEMPTS).document(ChallengeCloud.attemptDocId(attempt.challengeId, uid)),
                        ChallengeCloud.attemptToMap(attempt))
                }
            }

            if (ready(COMPLETIONS)) {
                for (result in myResults) {
                    val attempt = attempts.find { it.attemptId == result.questId } ?: continue
                    write(root.collection(COMPLETIONS).document(result.id),
                        ChallengeCloud.completionToMap(result, attempt.challengeId, name))
                }
            }

            // Only activity from after this account joined the group is shared with it.
            val joinedAt = spells.filter { it.groupId == group.id && it.userId == uid }
                .minOfOrNull { it.joinedAtMillis } ?: continue
            if (ready(ACTIVITY)) {
                for (event in myActivity.filter { (it.startedAtMillis ?: it.at) >= joinedAt }) {
                    write(root.collection(ACTIVITY).document(ChallengeCloud.activityDocId(event)),
                        ChallengeCloud.activityToMap(event, uid))
                }
            }
        }
    }

    private fun groupDocument(groupId: String) = firestore.collection(GROUPS).document(groupId)

    /** Every document in one of the group's collections, sent again whenever something changes. */
    private fun documents(groupId: String, collection: String, since: Long): Flow<List<DocumentSnapshot>> = callbackFlow {
        var query: Query = groupDocument(groupId).collection(collection)
        if (collection == ACTIVITY) query = query.whereGreaterThanOrEqualTo("at", since)
        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "Could not sync $collection for group $groupId", error)
                return@addSnapshotListener
            }
            if (snapshot != null) trySend(snapshot.documents)
        }
        awaitClose { registration.remove() }
    }

    /** Writes [data] once. Firestore retries network errors itself; a rule refusal is only logged. */
    private fun write(document: DocumentReference, data: Map<String, Any?>) {
        if (!known.add(document.path)) return
        document.set(data).addOnFailureListener { Log.w(TAG, "Firestore refused ${document.path}", it) }
    }

    /** Copies one snapshot into Room in a single transaction. Own rows are skipped: Room already has them. */
    private suspend fun saveSnapshot(uid: String, group: Group, collection: String, docs: List<DocumentSnapshot>) {
        docs.forEach { known += it.reference.path }
        try {
            db.withTransaction {
                for (doc in docs) {
                    val data = doc.data ?: continue
                    when (collection) {
                        CHALLENGES -> ChallengeCloud.challengeFromMap(doc.id, group.id, data)
                            ?.let { (quest, definition) -> adopt(quest, definition) }
                        ATTEMPTS -> ChallengeCloud.attemptFromMap(doc.id, data)
                            ?.takeIf { it.userId != uid }?.let { dao.insertAttemptIfAbsent(it) }
                        COMPLETIONS -> ChallengeCloud.completionFromMap(doc.id, group.id, data)
                            ?.takeIf { it.userId != uid }?.let { dao.insertResultIfAbsent(it) }
                        ACTIVITY -> ChallengeCloud.activityFromMap(doc.id, data)
                            ?.takeIf { it.userId != uid }?.let { dao.insertEntryIfAbsent(it) }
                        SPELLS -> {
                            // ESTIMATE gives our own pending write a time before the server answers.
                            val joinedAt = doc.getTimestamp("joinedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                                ?.toDate()?.time ?: continue
                            if (doc.id !in group.memberIds || doc.getString("userId") != doc.id) continue
                            val local = dao.spells()
                            ChallengeCloud.adoptSpell(local, group.id, doc.id, joinedAt)?.let { row ->
                                if (local.any { it.id == row.id }) dao.updateSpell(row) else dao.insertSpell(row)
                            }
                        }
                    }
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "Could not save $collection for group ${group.id}", error)
        }
    }

    /**
     * A new definition is inserted. For this week's group goal the first phone to upload wins,
     * so if this phone made its own copy first, it switches to the cloud version.
     */
    private suspend fun adopt(quest: QuestEntity, definition: GroupChallengeEntity) {
        val local = dao.challenge(definition.questId)
        if (local == null) {
            dao.insertQuestIfAbsent(quest)
            dao.insertChallengeIfAbsent(definition)
        } else if (local.kind == "WEEKLY" && local != definition) {
            dao.updateChallenge(definition)
            db.questDao().getQuest(definition.questId)?.let {
                db.questDao().updateQuest(it.copy(title = quest.title, description = quest.description))
            }
        }
    }

    private companion object {
        const val TAG = "ChallengeSync"
        const val GROUPS = "groups"
    }
}
