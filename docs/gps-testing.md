# Assigned-quest GPS tracking

An explicitly started GPS walk runs in a location foreground service. Leaving quest details, backgrounding the app or locking the device keeps the same walk running. Its ongoing notification opens the active quest and has a Pause tracking action. Pause in either place freezes distance and active time; moving while paused is excluded. Only one quest can track at a time.

After process restart, the latest local checkpoint is loaded paused. Android's Stop control, force-stop, reboot and process termination do not automatically restart tracking. Return to the same quest and choose Resume tracking. A foreground service improves continuity but cannot guarantee survival of every system/manufacturer restriction.

## Preconditions

- Sign in. For a group quest, open a group you belong to. Personal daily walks and extra GPS activities work without a group and are already assigned to you.
- Use a DISTANCE quest with a finite positive distance target.
- `assignedUserId` must equal the signed-in Firebase user ID. A display name alone does not grant assignment.
- New open group quests create a separate owned attempt for the member viewing the quest; Get quest assigns its attempt to the drawer automatically. The shared definition is never passed directly into GPS. See `group-quest-adventures.md` for the participation model.
- The earlier assignment picker remains for legacy assigned-quest integration, not the new open/drawn quest flow. GPS still validates the actual assigned UID on every owned attempt.

## Device / emulator check

1. Open the assigned quest. Verify its own target is displayed (not a fixed 5 km).
2. Tap Start Quest. Deny precise permission: no session should start. Retry and grant precise location.
3. Play a walking-speed emulator route (less than 8 m/s), or walk outdoors. Distance, percentage, elapsed time and pace should update.
4. Stop route playback: received positions can continue, but stationary movement must not add distance.
5. Pause tracking in the app: distance and time freeze. Move the emulator while paused, resume, and check that this gap is not counted.
6. Leave details, open another tab, lock the phone and walk. Return: the same session should still be tracking, with no duplicate listener. Rotate the device too.
7. Open the GPS notification: it should reopen the active quest with Pause tracking visible. Pause from the notification while the app is hidden: distance/time freeze and the notification disappears. Resume explicitly from quest details; the paused gap must not be connected.
8. Stop the app through Android's active-app Stop control (or force-stop), then reopen: the last saved checkpoint returns paused, without requesting permission or starting GPS automatically. An abrupt termination can lose work newer than the latest successful checkpoint, normally up to five seconds of active time; pending failed writes cannot survive process death.
9. Reach the target: GPS and its ongoing notification stop; Finish quest becomes available. Finish saves once, marks the quest COMPLETED and shows confirmation.
10. Reopen the quest: its saved distance and duration remain visible. The existing Add photo route still works.

## Permissions and interruptions

- Fine and coarse location are requested together at Start/Resume. Approximate-only permission never starts a distance quest. App permissions and Location settings links allow recovery after repeated denial or disabled location. Returning from settings does not start GPS automatically.
- On Android 13+, notification permission is requested after precise location. Denying notification permission still allows tracking, but the notification drawer controls may be unavailable. The screen explains that the user must return to the quest to pause; Android also exposes its active-app controls.
- Turning device location off pauses the walk with feedback. Permission revocation may cause Android to terminate the process; reopening recovers the latest durable checkpoint paused.
- Signing out or changing accounts pauses tracking. Observed removal/reassignment/completion of the quest, or observed loss of group membership, pauses it too. These checks use the existing repositories and their cached/offline behavior; they do not replace server-side authorization.
- Starting a second walk leaves it paused with an explanation. Pause the first walk before resuming the other.
- A failed service start leaves the saved session available for retry. No boot receiver, alarm, sticky restart or background-location permission is added.

## Database checks

Tracking sessions/results were introduced in Room version 3; the current schema is version 7, including photos, segment boundaries, personal quests and independent group attempts. Migrations preserve existing routes and quests. Never uninstall or clear app data to perform the migration check.

During activity, `tracking_sessions.sessionId` matches `route_points.sessionId`. Accepted points and their checkpoint are written in one transaction, through one ordered writer. On completion, `quest_results.gpsSessionId` retains that link, `quests.status` becomes COMPLETED, and the active checkpoint is deleted in one transaction. Recorded route points remain.

Failed writes stay queued for retry. Completion waits for the queue to drain and reports a failure instead of silently saving an incomplete route. The latest successful checkpoint is the recovery point if the process is terminated before an outstanding write completes.

## Implementation and automated checks

`QuestTrackingService` owns the notification, elapsed-time timer and lifetime. `GpsRepository` still owns filtering, route segments and the single ordered database writer. `QuestTrackingViewModel` validates assignment and creates/restores the Room session before requesting the service. There is no database migration in this reliability change.

`BackgroundTrackingServiceTest` exercises the real service, notifications, screen-off checkpointing, location-off interruption, account sign-out, concurrent quest exclusion and restoration using controlled GPS fixes. A production-factory test also starts the real Fused Location adapter, recreates the Activity and opens the active quest through the notification. Controlled fixes do not verify satellite reception or battery behavior on a physical phone.

With an emulator connected, run:

```bash
./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=no.ntnu.prog2007.questbook.BackgroundTrackingServiceTest,no.ntnu.prog2007.questbook.PhotoSelectionRecoveryTest
```

Android references: [location foreground service requirements](https://developer.android.com/develop/background-work/services/fgs/service-types#location), [restrictions on starting a foreground service](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start), and [handling users stopping an active app](https://developer.android.com/develop/background-work/services/fgs/handle-user-stopping).

## Firestore handoff

Local assignment is exposed as `QuestRepository.assignQuest(questId, groupId, userId, displayName)`. A shared-storage implementation should provide the same observable quest update and enforce group membership remotely. It must atomically reject assignments after status leaves NOT_STARTED. Save the actual UID as `assignedUserId`; `assignedTo` is a username snapshot, never the UI label "You". Local assignment is not yet delivered to another member's phone.
