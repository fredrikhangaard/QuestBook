# QuestBook

QuestBook is a private, social health and wellbeing app for small groups of friends. Members create and complete real-life quests together, such as walks, workouts and outdoor visits. GPS tracking decides when a distance quest is done, and completed quests, photos and weekly summaries build a shared history for the group.

Made for PROG2007 Mobile Programming at NTNU, autumn 2026, by Jesper, Fredrik and Espen.

## Features

- **Accounts:** sign up and log in with email and password (Firebase Authentication).
- **Private groups:** create a group, join with a six-character invite code and see the members. A user can be in several groups.
- **Group quests:**
  - Open quests that every member can complete.
  - Drawn quests that belong to the member who drew them.
  - A weekly group goal that grows with the number of members.
- **Personal quests:** daily and weekly solo quests with progress from your own activity.
- **GPS tracking:** distance quests are tracked in a foreground service. The route is saved as it is walked, the quest can be paused and resumed, and tracking stops automatically when the distance target is reached.
- **Results and photos:** finishing a quest saves a result. You can add a photo from the gallery, with an optional caption.
- **Memories (QuestBook):** your completed quests, photos and weekly activity, week by week.
- **Works offline:** quests, tracking and results are stored in Room. Group data is shared through Firestore when the phone is online.

## Core screens

| Screen | What it does |
|---|---|
| **Today** (Home) | Your groups, personal quests and active group quests in one place |
| **Quest** | Quest details, live GPS tracking, finishing and adding a photo |
| **QuestBook** (Memories) | Weekly history, statistics and photos |

Supporting screens: login and sign-up, Groups (create and join), group page, create quest, group quest page with member completions, saved route and profile.

## Architecture

The app follows **MVVM with unidirectional data flow (UDF) and the Repository pattern**:

- Every screen has a ViewModel that exposes one `StateFlow<UiState>` (state goes down) and functions for user actions (events go up).
- ViewModels depend on repository interfaces such as `AuthRepository`, `GroupRepository`, `QuestRepository` and `QuestTrackingStore`, not on Firebase or Room directly.
- `data/AppServices` is a small manual dependency container. Tests replace the real implementations with fakes.
- Navigation between all screens uses Navigation Compose (`ui/navigation/QuestBookNavHost.kt`).

```
app/src/main/java/no/ntnu/prog2007/questbook/
├── model/          Domain models: User, Group, Quest, QuestResult
├── data/
│   ├── auth/       AuthRepository + Firebase implementation
│   ├── groups/     GroupRepository + Firestore implementation
│   ├── repository/ QuestRepository + Room implementation
│   ├── tracking/   Tracking sessions and results (Room)
│   ├── challenges/ Group quests: definitions, attempts, membership
│   ├── personal/   Personal daily and weekly quests
│   ├── photo/      Photo compression and local storage
│   └── sync/       Room <-> Firestore sync for group quests
├── location/       GPS source, filtering, foreground service, Room database
└── ui/             Compose screens and ViewModels, one package per feature
```

### Where data is stored

| Data | Room (on the phone) | Firestore (shared) |
|---|---|---|
| User profile | | `users/{uid}`: username only, no email |
| Groups and invite codes | | `groups/{id}`, `inviteCodes/{code}` |
| Group quests, attempts, completions | yes (the screens read from Room) | `groups/{id}/challenges`, `attempts`, `completions` |
| Activity for weekly group goals | yes | `groups/{id}/activity`: amounts and time only |
| Photos for group quest completions | yes | `groups/{id}/photos`: Base64, max about 675 kB |
| Personal quests, personal photos | yes | not shared |
| GPS routes and tracking sessions | yes | **never leaves the phone** |

Room is the single source the screens read from. `FirestoreChallengeSync` listens to the user's groups and copies other members' rows into Room. It uploads the user's own rows with fixed document IDs, so a retry or a second phone never creates a duplicate. Firestore's offline queue sends the writes when the phone is back online.

### Security

The project runs on the Firebase Spark plan, so there are no Cloud Functions. Instead, the important rules are enforced in [`firestore.rules`](firestore.rules):

- Only members can read a group and everything in it. Nobody can list groups or invite codes.
- Joining a group requires the invite code, and a user can only add themselves.
- Shared quest documents can be created once and never changed. A member can only write their own attempts, completions, activity and photos.
- The weekly goal must match the group's real member count, and a member's join time is stamped with the server's clock.

The rules must be published in the Firebase console (Firestore → Rules) after every change to `firestore.rules`.

### Sensor: GPS

GPS drives a decision, not just a map. `GpsRepository` filters inaccurate samples, adds up the accepted distance and stops tracking when the quest's distance target is reached. Each accepted point and the session progress are saved in a Room transaction, so a walk survives the app being closed. See [`docs/gps-architecture.md`](docs/gps-architecture.md).

## UML

- Class diagram (overview): [`docs/uml/class-diagram.png`](docs/uml/class-diagram.png)
- Sequence diagram (GPS quest from start to finish): [`docs/uml/gps-quest-sequence.png`](docs/uml/gps-quest-sequence.png)

## Getting started

**Requirements**

- Android Studio (recent stable version) with JDK 17 or newer for Gradle
- Android SDK 37; the app runs on Android 8.0 (API 26) and newer
- An emulator or phone with Google Play services (needed for location)

**Run the app**

1. Clone the repository and open the project folder in Android Studio.
2. `app/google-services.json` for the Firebase project `questbook-1b814` is included, so no extra setup is needed for the team's project. To use your own Firebase project, replace the file, enable Email/Password sign-in and Firestore, and publish `firestore.rules`.
3. Run the `app` configuration, or build from the terminal:

```
./gradlew assembleDebug
```

On Windows PowerShell, use `.\gradlew` instead of `./gradlew`.

**Permissions:** the app asks for precise location (GPS quests) and notifications (the tracking notification). Photos are picked with the system photo picker, which needs no permission. Location is only used while a quest is being tracked.

## Testing

```
./gradlew testDebugUnitTest            # unit tests (JVM)
./gradlew connectedDebugAndroidTest    # instrumented and Compose UI tests (emulator or phone)
```

- **Unit tests** (`app/src/test`) are written in Arrange-Act-Assert style with positive and negative cases. They cover ViewModel behaviour, validation, GPS distance logic, quest rules and the Firestore mapping.
- **Instrumented tests** (`app/src/androidTest`) cover Room stores, navigation and complete screen flows. They use fake login and group repositories, so they never talk to Firebase.
- **User tests:** see the project report.

More test notes: [`docs/gps-testing.md`](docs/gps-testing.md) and [`docs/memories-testing.md`](docs/memories-testing.md).

## Further documentation

| Document | Topic |
|---|---|
| [`docs/gps-architecture.md`](docs/gps-architecture.md) | How quest assignment and GPS tracking fit together |
| [`docs/gps-route-segments.md`](docs/gps-route-segments.md) | Pause and resume as separate route sections |
| [`docs/saved-route-viewer.md`](docs/saved-route-viewer.md) | Viewing a saved route, and route privacy |
| [`docs/group-quest-adventures.md`](docs/group-quest-adventures.md) | Open, drawn and weekly group quests |
| [`docs/personal-quests.md`](docs/personal-quests.md) | Solo daily and weekly quests |
| [`docs/app-readiness.md`](docs/app-readiness.md) | Readiness checklist and device acceptance tests |

## Known limitations

- Leaving or deleting a group is not available in the app yet.
- Using the same account on two phones at the same time is not supported for group quests.
- Activity done before a member's first sync does not count towards that week's group goal.
- Personal quests are stored on the phone only.

## Team

| Name | GitHub |
|---|---|
| Jesper | JesperSHal |
| Fredrik | fredrikhangaard |
| Espen | EspenG01 |
