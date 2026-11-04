# Assigned-quest GPS tracking

GPS is foreground-only. Leaving quest details, backgrounding the app or locking the device pauses tracking. Return to the same quest and choose Resume tracking. Moving while paused is excluded from distance and elapsed time. After process restart, the latest local checkpoint is loaded paused.

## Preconditions

- Sign in and open a group you belong to.
- Use a DISTANCE quest with a finite positive distance target.
- `assignedUserId` must equal the signed-in Firebase user ID. A display name alone does not grant assignment.
- Open the quest and tap Assign quest. Pick a member (choose yourself for a GPS test) and confirm Assign. The actual UID and username are saved locally. Unassigned quests stay disabled; GPS does not claim them automatically.
- Any current group member can assign or reassign a NOT_STARTED quest. Once started or completed, assignment is locked. The member list comes from the existing group repository, but assignment performs no Firestore writes.

## Device / emulator check

1. Open the assigned quest. Verify its own target is displayed (not a fixed 5 km).
2. Tap Start Quest. Deny precise permission: no session should start. Retry and grant precise location.
3. Play a walking-speed emulator route (less than 8 m/s), or walk outdoors. Distance, percentage, elapsed time and pace should update.
4. Stop route playback: received positions can continue, but stationary movement must not add distance.
5. Pause tracking in the app: distance and time freeze. Move the emulator while paused, resume, and check that this gap is not counted.
6. Leave details and return: tracking is paused with the same route/distance. Resume explicitly.
7. Background/restart the app: the last saved checkpoint returns paused, without requesting permission automatically.
8. Reach the target: GPS stops and Finish quest becomes available. Finish saves once, marks the quest COMPLETED and shows confirmation.
9. Reopen the quest: its saved distance and duration remain visible. The existing Add photo route still works.

## Database checks

Room version 3 adds `tracking_sessions` and `quest_results`; migrations from versions 1 and 2 preserve `route_points` and existing quests. Never uninstall or clear app data to perform the migration check.

During activity, `tracking_sessions.sessionId` matches `route_points.sessionId`. Accepted points and their checkpoint are written in one transaction, through one ordered writer. On completion, `quest_results.gpsSessionId` retains that link, `quests.status` becomes COMPLETED, and the active checkpoint is deleted in one transaction. Recorded route points remain.

Failed writes stay queued for retry. Completion waits for the queue to drain and reports a failure instead of silently saving an incomplete route. The latest successful checkpoint is the recovery point if the process is terminated before an outstanding write completes.

## Scope of this branch

This branch saves results locally. It does not add a foreground location service, continuous background tracking, a map, Firestore quest/assignment/result synchronization, or a Memories/photo feed. Existing photo code is reused without changing its schema; result/photo linking belongs to the next integration step.

## Firestore handoff

Local assignment is exposed as `QuestRepository.assignQuest(questId, groupId, userId, displayName)`. A shared-storage implementation should provide the same observable quest update and enforce group membership remotely. It must atomically reject assignments after status leaves NOT_STARTED. Save the actual UID as `assignedUserId`; `assignedTo` is a username snapshot, never the UI label "You". Local assignment is not yet delivered to another member's phone.
