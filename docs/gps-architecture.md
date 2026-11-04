# How quest assignment and GPS tracking fit together

Start with the flow below, then use the file table to find the code responsible for each step. The UI, GPS filtering, and database responsibilities are deliberately separated.

## Reading order

| File | Responsibility |
| --- | --- |
| `ui/quest/QuestScreen.kt` | Renders quest details, requests precise location permission, and reports screen lifecycle events. |
| `ui/quest/assignment/AssignQuestDialog.kt` | Shows group members and sends selection/save actions to the assignment ViewModel. |
| `ui/quest/assignment/AssignQuestViewModel.kt` | Checks group membership and quest status, loads usernames, and requests local assignment persistence. |
| `data/repository/QuestRepository.kt` | Interface for observable quest data and assignment. Room is the current implementation. |
| `ui/quest/tracking/QuestTrackingViewModel.kt` | Coordinates loading, starting, pausing, and finishing the selected assigned quest. |
| `location/GpsRepository.kt` | Filters position samples, measures active duration, and serializes writes. It does not request permission or select a quest. |
| `location/AndroidGpsSource.kt` and `LocationTracker.kt` | Adapt Android's Fused Location callbacks into the plain route-point model. |
| `data/tracking/RoomQuestTrackingStore.kt` | Performs the database transactions for starting, checkpointing, and completing a quest. |
| `data/tracking/TrackingDao.kt` and `TrackingEntities.kt` | Define the SQL operations and database rows. |
| `location/database/QuestBookDatabase.kt` | Registers the tables and migrations that preserve old route/quest data. |

A **ViewModel** handles user actions and keeps screen state. A **StateFlow** is an observable value: Compose redraws when it changes. A **DAO** declares Room's SQL operations. A **transaction** commits several database changes together or rolls all of them back.

## 1. Assigning a quest

1. The screen receives the current group summary from navigation. `rememberUpdatedState` lets a remembered ViewModel factory read the latest membership rather than the first snapshot it saw.
2. `AssignQuestViewModel.open()` checks that the signed-in user belongs to the quest's group and the quest is NOT_STARTED.
3. Usernames are read through the existing group repository. The picker uses stable user IDs for keys and selection, so two people named Alex remain distinct.
4. `assign()` rechecks membership and sends the selected UID and actual username to `QuestRepository.assignQuest()`.
5. Room updates only `assignedUserId` and `assignedTo`, and only while status is still NOT_STARTED. If GPS started while the picker was open, the SQL update affects zero rows and saving fails safely.
6. The observable quest list updates details and cards. Selecting yourself enables GPS start; selecting another member does not let you track on their behalf.

The `(You)` label is for presentation only. It is never written as the person's username. Assignment is local for now; this code performs no Firestore assignment writes.

## 2. Starting and resuming GPS

1. Start requests precise Android location permission. Opening quest details alone does not request permission or start tracking.
2. After permission is granted, `QuestTrackingViewModel.start()` checks the current quest, UID, group access, type and distance target again.
3. `RoomQuestTrackingStore.begin()` creates the session and marks the quest IN_PROGRESS in one transaction. A resume reuses the existing session ID.
4. `GpsRepository.resumeTracking()` starts the provider. It clears the current segment's anchor so movement during a pause is not counted.
5. Each callback carries a request-generation token. Stop increments that token, so late callbacks from an old request cannot alter the route.

The test screen has its own `GpsTrackingViewModel`. It reuses the engine with the original 5 km default and point-only persistence; it does not create quest results.

## 3. Receiving and saving positions

The GPS engine shows the newest fix, but only accepted fixes contribute route points and distance. It rejects missing/poor accuracy, invalid coordinates, old timestamps, speeds above 8 m/s, and movement below 3 metres.

The first accepted point anchors a segment. Each later accepted point adds its distance from the previous accepted point. A rejected point does not replace that anchor.

Every accepted point and its checkpoint enter one write queue. Quest tracking saves them together in a Room transaction. If that transaction fails, the command remains first in the pending queue; later commands cannot skip it. `flush()` inserts a barrier and waits until all earlier commands have succeeded, or reports the write failure.

The screen timer updates active elapsed time once per second. The engine queues a time checkpoint every five active seconds even if no accurate GPS fix has arrived. Pause queues the final elapsed time immediately.

## 4. Leaving and reopening details

This implementation is foreground-only. Leaving details, backgrounding the app, or locking the device pauses tracking. Returning makes Resume available; it does not resume automatically.

A paused controller is retained per user and quest during the app process so reopening details cannot read an older Room snapshot while writes are pending. After process restart, the controller is gone and the latest successfully persisted checkpoint plus its stored route are loaded instead. Recovery does not recreate distance/time that had not yet been saved before termination.

## 5. Completing the quest

Reaching the target stops GPS and displays Finish quest. Reaching 100% does not yet save a completion result.

Finish pauses tracking and awaits `gps.flush()`. The store then verifies durable distance and, in one transaction:

- inserts the result snapshot;
- changes the quest to COMPLETED;
- removes the active tracking summary.

Route points remain stored. The result keeps their GPS session ID. A retry for the same completed session returns its existing result, and a unique quest index prevents duplicate completion rows.

The screen displays success only after this transaction returns. It then releases the retained controller. If saving fails, the user can retry without starting a new route.

## Units and database links

| Value | Meaning |
| --- | --- |
| `Quest.requiredDistanceKm` | Target entered in kilometres. |
| Session target and distance | Metres; converted from kilometres when the session starts. |
| `elapsedMillis` / result duration | Active milliseconds, excluding pauses. |
| `startedAtMillis` / `completedAtMillis` | Wall-clock timestamps since the Unix epoch. |
| `RoutePoint.timestamp` | Android location fix time, used to order fixes and calculate segment speed. |
| `assignedUserId` / `userId` | Actual Firebase UID, used for identity checks. |
| `assignedTo` / participant name | Display-name snapshots for readable UI/history. |
| `sessionId` / `gpsSessionId` | Join the active summary, recorded route and saved result. |

## Tests and current limits

- GPS engine unit tests cover target completion, filtering, pause/resume, restore, write ordering and retries.
- Tracking ViewModel tests cover assignment/group eligibility, in-flight start cancellation, completion and retry behavior.
- Assignment ViewModel tests cover membership changes, UID/name persistence, cancellation and save failures.
- Emulator tests exercise the actual Room migrations/transactions and the member-picker interaction.

See [the testing guide](gps-testing.md) for manual verification. Continuous background tracking, map rendering, shared result/assignment synchronization and the Memories/photo feed are separate work. Existing photo navigation is preserved, but linking photos to the new result model is not part of this branch.
