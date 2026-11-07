# QuestBook

QuestBook is a social activity app for individuals and small groups of friends. Users complete walking, exercise and outdoor quests, then save results and optional photos in Memories.

Developed for PROG2007 Mobile Programming at NTNU, autumn 2026, by Jesper, Fredrik and Espen.

**Repository:** https://github.com/fredrikhangaard/QuestBook

## Features

- **Accounts:** email and password registration and sign-in through Firebase Authentication.
- **Private groups:** create groups, join using invite codes and view members.
- **Leave group:** members can leave online. Ownership transfers to a remaining member. The last member closes the group and disables its invite code.
- **Group quests:** create an open challenge for members to complete independently, or draw a quest assigned to yourself.
- **Weekly group goal:** one automatic goal scaled to the stored member count. Its target remains fixed for that week.
- **Personal quests:** one daily quest and four weekly goals covering walking, bodyweight repetitions, outdoor visits and active days.
- **GPS tracking:** explicit Start, Pause and Resume controls. Tracking stops when the distance target is reached.
- **Manual activity:** save repetition sets or confirm visits. Photos are optional.
- **Memories:** browse completions, photos and personal activity totals by week.
- **Saved routes:** view an offline drawing of a completed GPS route.

One saved activity can advance several matching goals without being counted twice in personal activity totals.

## Main screens

| Screen | Purpose |
|---|---|
| Home | Group shortcuts and active quests |
| Groups | Memberships, group details and quest actions |
| Quests | Personal and group goals, with completed history |
| Quest details | Progress, activity controls and completion |
| Memories | Weekly results, photos and statistics |
| Profile | Account information and settings |

Supporting screens handle registration, sign-in, group creation, joining, quest creation, photos and saved routes.

## Architecture

QuestBook uses MVVM, unidirectional data flow and the Repository pattern.

- Compose screens display observable state and send user actions to ViewModels.
- ViewModels coordinate actions through repositories and stores.
- Room supplies local quest data to the screens.
- Firebase Authentication handles accounts.
- Firestore stores groups and synchronizes shared group quest records.
- Navigation Compose connects the screens.
- Manual dependency containers allow tests to substitute fake repositories.

```text
app/src/main/java/no/ntnu/prog2007/questbook/
├── model/          User, Group, Quest and QuestResult
├── data/
│   ├── auth/       Authentication
│   ├── groups/     Groups and invite codes
│   ├── repository/ Observable quest storage
│   ├── tracking/   Tracking checkpoints and results
│   ├── challenges/ Group definitions and member attempts
│   ├── personal/   Personal quests and activity accounting
│   ├── photo/      Photo processing and persistence
│   └── sync/       Group data synchronization
├── location/       GPS, foreground service and Room database
└── ui/             Compose screens, ViewModels and navigation
```

## Data storage

| Data | Storage |
|---|---|
| Accounts | Firebase Authentication |
| Public usernames | Firestore |
| Groups and invite codes | Firestore |
| Group challenges, attempts and completions | Room and Firestore |
| Shared group activity and completion photos | Room and Firestore |
| Personal quests and personal photos | Room |
| GPS routes and tracking checkpoints | Room only |

`FirestoreChallengeSync` exchanges shared group records with Room. Fixed document IDs prevent duplicate documents when retrying the same upload.

Existing local quests, tracking and results can be used without a connection. Initial sign-in and operations such as joining or leaving a group require connectivity. Queued uploads are not proof that the server has accepted a write.

Room schema files are stored in `app/schemas`. Keep versions 1 through 7 because migration tests use them to verify upgrades preserve existing data.

## GPS behavior

GPS only starts after an explicit Start or Resume action and precise location permission.

`GpsRepository` filters inaccurate fixes and measures accepted movement. `QuestTrackingService` keeps an active walk running during navigation and screen locking.

Pause excludes movement and active time until Resume. Route sections remain separate. Reaching the target stops location updates.

Finish waits for pending database writes before saving the result. After process death, an unfinished session restores paused and requires explicit Resume.

Saved routes are offline path drawings, not street maps. Precise route coordinates are not uploaded by the group synchronization flow.

## Firebase access

Access rules are defined in [firestore.rules](firestore.rules).

The rules restrict group data to members and validate ownership of submitted attempts, completions and photos. Users can query groups they belong to. Invite codes support direct lookup rather than listing.

Changes to the rules must be published separately through Firebase. A successful Android build does not deploy or verify the cloud rules.

## Getting started

### Requirements

- Android Studio with the JDK required by the project’s Gradle configuration
- Android SDK 37
- An emulator or phone running Android 8.0, API 26, or newer
- Google Play services for the location provider

### Firebase configuration

The submitted ZIP excludes `app/google-services.json`, as required by the assignment.

Before building, obtain the configuration for the team’s Firebase project through the agreed course submission channel, or configure your own project:

1. Register an Android application with package name `no.ntnu.prog2007.questbook`.
2. Download its `google-services.json` into `app/`.
3. Enable Email/Password authentication.
4. Create a Firestore database and publish the project’s `firestore.rules`.

A replacement Firebase project starts with no accounts or groups. Create these through the app.

### Build and run

Open the project in Android Studio, allow Gradle to sync and run the `app` configuration.

To build from the terminal:

```bash
./gradlew assembleDebug
```

On Windows PowerShell, use `.\gradlew` instead of `./gradlew`.

### Permissions

- Precise location is requested when starting or resuming a GPS quest.
- Notification permission is requested for tracking notifications on supported Android versions.
- Gallery images use the system photo picker.

Opening a quest or saved route does not start location tracking.

## Testing

Run JVM unit tests:

```bash
./gradlew testDebugUnitTest
```

Run instrumented tests with an emulator or phone connected:

```bash
./gradlew connectedDebugAndroidTest
```

Build the application and instrumented test APK:

```bash
./gradlew assembleDebug assembleDebugAndroidTest
```

Unit tests cover validation, ViewModels, GPS behavior, quest rules and cloud data mapping. Instrumented tests cover Room persistence, migrations and screen flows.

Local automated checks do not replace Firebase access-rule testing, physical GPS checks or testing across separate installations. User testing and findings are described in the project report.

See [testing.md](docs/testing.md) for acceptance checks.

## Documentation

- [Architecture](docs/architecture.md)
- [Testing](docs/testing.md)
- KDoc comments in the source code
- UML diagrams in the project report

## Known limitations

- Personal quests and personal photos remain on the originating phone.
- Simultaneous group quest use by the same account on multiple phones is not supported.
- Group contribution history is limited by the available membership records.
- Saved routes do not include a street-map background.
- Exercise repetitions and visits are self-reported.
- Discovery prompts and photos are optional and are not automatically verified.

## Submission

The submission consists of the Android project ZIP, report PDF and demonstration video. The report includes individual contributions, user testing, UML diagrams and the AI declaration.

Exclude generated `build/` folders, `.gradle/`, `.idea/`, `google-services.json`, `.env` files and API keys from the ZIP. Verify that the extracted project opens in Android Studio and document any required configuration.

## Team

| Name | GitHub |
|---|---|
| Jesper | JesperSHal |
| Fredrik | fredrikhangaard |
| Espen | EspenG01 |
