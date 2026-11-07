# Architecture

## Structure

Compose screens display state from ViewModels and send user actions back to them. Repositories and stores handle data. Room supplies quest data to the screens. Firebase handles accounts, groups and group quest sharing.

| Package | Responsibility |
|---|---|
| `data/auth`, `data/groups` | Accounts, profiles, groups and invite codes |
| `data/repository` | Observable quests and legacy assignment |
| `data/tracking` | Tracking checkpoints and completion transactions |
| `data/personal` | Personal quests, activity matching and summaries |
| `data/challenges` | Group goals, member attempts and membership periods |
| `data/photo` | Image compression and result-linked photos |
| `data/sync` | Group data exchange between Room and Firestore |
| `location` | GPS source, filtering, service and database |
| `ui` | Screens, ViewModels and navigation |

## GPS and saved routes

1. Start or Resume requests precise location permission and checks account, quest and group access.
2. `QuestTrackingViewModel` creates or restores the session through `QuestTrackingStore`.
3. `QuestTrackingService` keeps tracking active during navigation and screen locking. Its notification opens the quest or pauses tracking.
4. `GpsRepository` filters fixes and measures distance and active time. One ordered writer saves accepted points and checkpoints together.
5. Reaching the target stops GPS. Finish waits for pending writes, then saves one result, completes the owned quest and removes its checkpoint in a Room transaction.

Pause excludes movement and time until Resume. Each resumed route section gets a new anchor, so the viewer does not connect movement during a pause. After process death, the latest durable checkpoint restores paused. GPS never restarts automatically.

Account changes, observed loss of access and disabled location pause tracking. Only one quest tracks at a time. Failed writes remain retryable.

The saved route viewer draws an offline path with statistics and section boundaries. It is not a street map. Routes and tracking sessions stay on the phone and are owner-only.

Distances use metres and durations use milliseconds. `Quest.requiredDistanceKm` uses kilometres. Session IDs link checkpoints, route points and results.

## Personal quests and activity

Each user receives one daily quest and four weekly goal families: walking, bodyweight repetitions, outdoor visits and active days. The pool contains 122 daily templates and 154 group-draw templates. Discovery prompts and photos are optional.

Walking requires GPS. Weekly walks have a target of at most 5 km or the smaller remaining amount. Repetition buttons edit a pending set and Save records it once. Visits and custom tasks require manual confirmation.

Push-ups and squats contribute to bodyweight goals. Generic repetitions do not become a specific exercise. Custom confirmations do not become outdoor visits. Active days come from qualifying saved activity on distinct dates. Active-minute quests are retired, while recorded duration remains in history.

Daily windows end at local midnight. Weekly windows run Monday to Monday. Assignments keep their target, timezone and window. Saved activity counts in its save period. A late walk keeps its result but does not complete an expired daily goal.

One physical activity can advance several matching goals but appears once in summary totals. Goal awards never create another physical event. Personal quests remain local to the account on that phone.

## Group quests

- Create quest makes an open definition with an independent attempt and result for each member.
- Get quest draws a daily-sized task owned by the drawer, with the current week's deadline.
- Each group gets one weekly walking, repetition or outdoor-visit goal. The fixed target is 5 km, 50 repetitions or one visit per member in its stored snapshot.

Shared definitions and owned attempts have separate IDs. One member completing an attempt does not complete the definition for everyone. Membership periods limit eligible contributions. Earlier valid contributions remain counted after departure.

## Photos and Memories

Photos link to a completion result, quest, group and owner UID. Completion never requires a photo. Memories shows dated completions, optional photos and the current user's weekly totals. Other members' activity does not inflate those totals.

## Firebase sharing

`FirestoreChallengeSync` uploads owned rows with fixed document IDs and inserts downloaded rows into Room if absent. Firestore queues offline writes. An upload queued locally is not proof of server acceptance.

Group subcollections contain `challenges`, `attempts`, `completions`, `activity`, `photos` and membership records. Group photos use compressed Base64 content. Personal photos and precise routes are not uploaded by this sync flow.

`firestore.rules` controls cloud access. Publishing and testing the deployed rules is separate from building the app. Verify cross-device behavior with real accounts before claiming release readiness.

Members can leave through a confirmed online transaction. Ownership passes to a remaining member. The last member closes the group and disables its invite code. Saved local results and cloud child history remain.

Current limitations include no supported simultaneous group quest use by one account on two phones and no group credit before the member's first sync. Weekly membership snapshots do not establish historical Monday membership by themselves. Full leave/rejoin history sharing remains limited by the first-join cloud membership record.

## Database history

Room is at version 7. Keep all schema files from `1.json` through `7.json` and the migrations. Migration tests recreate versions 1 through 6 to verify that upgrades preserve existing data.
