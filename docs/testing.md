# Testing

## Automated checks

Run unit tests and build both APKs:

```bash
./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest
```

Run device tests with an emulator or phone connected:

```bash
./gradlew connectedDebugAndroidTest
```

Use a disposable emulator for fixture-based tests. With multiple devices, set `ANDROID_SERIAL` to the intended device.

| Tests | Main coverage |
|---|---|
| `GpsRepositoryTest`, `QuestTrackingViewModelTest` | Filtering, access, pause/resume and write ordering |
| `QuestTrackingStoreTest`, `RouteSegmentsStoreTest` | Atomic completion, migrations and route sections |
| `BackgroundTrackingServiceTest` | Service lifetime, notifications and interruptions |
| `PersonalQuestRulesTest`, `PersonalQuestStoreTest` | Calendar boundaries, matching goals and duplicate prevention |
| `GroupChallengeRulesTest`, `GroupChallengeStoreTest` | Independent attempts, fixed targets and membership periods |
| `GroupChallengeNavigationTest`, `PersonalQuestNavigationTest` | Quest controls and real navigation |
| `MemoryPhotoStoreTest`, `PhotoSelectionRecoveryTest` | Photo ownership, persistence and selection recovery |
| `ChallengeCloudTest` | Cloud mapping and validation |

Local tests do not prove deployed Firebase rules, cross-device sharing, real GPS accuracy or battery behavior.

## Manual acceptance checks

1. Start a distance quest and grant precise location. Deny permission separately and confirm that tracking stays stopped.
2. Walk with the screen locked. Pause from the notification, move, then Resume. Paused movement and time must not count.
3. Reach the target. GPS must stop. Finish must save one result that survives reopening.
4. Disable location, change accounts or interrupt the process. Recovery must remain paused until explicit Resume.
5. Save repetition sets and confirm visits. Matching goals should advance without duplicating physical totals.
6. Complete a quest with and without a photo. Cancel and retry the picker. Check persistence and account separation.
7. Open a saved route. Check statistics, separate sections, empty routes and owner-only access.
8. Check daily and Monday rollover, late completion and previous-week summaries.
9. On two installations, create and draw group quests, complete independent attempts and check shared photos and weekly progress.
10. Test offline saves and reconnect. Verify no duplicates, appropriate error feedback and no previous-account data after logout.

## Leaving a group

Check Cancel, confirmed leave and a failed connection. Verify owner transfer and last-member closure. Other members must remain unchanged and the closed group's invite code must stop working.

Publish and test the updated Firebase rules before cloud use. Reject attempts to remove another member or close a group that still has members.

## Firebase and release checks

Test deployed access rules with member, outsider and owner accounts. Reject forged ownership, unauthorized target edits, another member's writes and private route access. Test concurrent weekly creation and repeated uploads.

Confirm the deployed rules match the checked-in file. Record which Firebase emulator checks and two-device checks actually passed. Keep unverified checks listed as remaining work.

Do not uninstall or clear app data during migration testing. Keep the exported schemas so older databases can be recreated.
