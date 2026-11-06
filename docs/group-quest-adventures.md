# Group quest adventures

## What the app does

- One automatic goal per group per calendar week (Monday to Monday). It chooses walking, bodyweight repetitions or outdoor visits. The first local draw snapshots membership and target; reopening, joining or leaving never resizes it. Walking is 5 km per member, repetitions 50 per member and visits one per member.
- Create quest makes an open challenge. Every member has an independent attempt, progress, GPS session, result and optional photos. Completing an attempt never completes the shared definition or locks another member out.
- Get quest draws an additional daily-sized challenge with this week's deadline, automatically owned by the drawer. Retries reuse an operation ID and never draw two challenges for one request.
- The pool has 122 daily templates and 154 group-draw templates. The playful discovery/social prompt adds variety; only GPS distance, reported repetitions or explicit manual confirmation determines completion. Finding a rock, company and photos are optional and are not automatically recognized.
- Direct +1/+5/+10 buttons edit an unsaved repetition set; Save commits it once. Generic bodyweight reps do not become push-ups or squats. A custom confirmation does not become an outdoor visit.
- Walking starts only from an explicit Start quest action. That action opens the existing GPS screen and starts its permission/service flow, with no second Start required. The session stops automatically at its finite distance target, or can be paused manually and resumed later. Notification/history navigation alone never starts GPS.
- Weekly distance buttons start a finite walk of at most 5 km (or the smaller remaining goal), avoiding unrestricted location logging. Daily and open challenges use their own target. Pausing before reaching the target preserves the session; it does not falsely complete the quest or save an extra physical event.
- Weekly inputs create physical sets/visits, not editable totals. A saved activity advances matching daily, personal weekly and eligible group goals while appearing once in activity totals. Active days are derived automatically. Active-minute quests are retired, while recorded GPS duration and old history remain.
- The Quests tab has active goals above and completed quests below, with no empty All/Active/Completed filter section. Weekly pages omit repeated explanations and normal progress updates automatically.
- Shared quest pages show member completions and optional photos. Shared Memories cards use the same result/photo links; other members' precise GPS routes remain private. A contributor can add an optional photo to a completed weekly group achievement too.

## Reading the implementation

`GroupChallengeStore` owns group definitions, membership spells, automatic goals, draws, attempt creation and projections. `GroupChallengeDao` contains the three new Room tables. `GroupChallengesViewModel` observes the current account/groups and database changes. `GroupChallengeScreens` wraps the current member's attempt with the group's completion gallery.

An owned attempt is a separate quest row, linked by `quest_attempts`. This reuses the existing one-result-per-quest and one-session-per-quest guarantees instead of weakening their uniqueness. Manual input and personal/weekly projections remain in `PersonalQuestStore`. Both UI types use `QuestActivityControls`, so the same input has the same meaning everywhere.

A physical GPS event comes from an immutable saved GPS result. A manual event comes from `activity_entries`. Weekly/personal awards never create physical events, preventing cascading duplicate distance or repetitions. Group membership projection checks each event once against eligible membership intervals. Departed members' earlier contributions stay counted; activity before joining, after leaving or during a gap does not count. A GPS walk begun before joining is conservatively excluded in full rather than attributing pre-join distance to the group.

Version 7 adds group definitions, owned attempt links, membership spells and a separate generic-repetition counter. It archives time quests without deleting their results or activities. Existing group quests are lifted into open definitions on observation; old active/completed IDs remain the first owner's attempt so saved sessions, result IDs and photos survive unchanged. Untargeted legacy workouts use confirmation rather than inventing a repetition count.

## Local storage and Firestore handoff — required before cross-device sharing

These new records are local Room data. Existing Firestore accounts/groups are unchanged. Another phone will not yet receive these group quests, contributions, completions or photo galleries. Two-account tests demonstrate the local model and access boundaries, not a deployed shared backend.

The teammate's synchronization layer must:

1. Create the canonical weekly definition atomically under a stable group/week key, using an agreed group timezone and server membership snapshot. Local first observation cannot reconstruct how many people belonged to a group at Monday midnight. Read the canonical server definition instead of independently rerolling/resizing it on each phone.
2. Mirror open/drawn definitions and the child-to-parent attempt link. Draws must be owned by request.auth.uid; open quests permit one attempt per member. Never use global quest COMPLETED to block other members.
3. Supply trusted membership join/leave timestamps. Until then, local eligibility begins at first observation and is conservative; unknown earlier membership does not backfill earlier activity. Devices that were offline cannot infer exact historical leave/rejoin boundaries.
4. Sync immutable physical event IDs and owned completion results idempotently. Group projections consume eligible events by original ID, not copied personal-goal awards. Keep per-user summaries owner-scoped.
5. Link optional photos to result ID, attempt ID, group ID and owner UID. Enforce owner writes and current-group-member reads; retain precise route access as owner-only unless a separate explicit route-sharing feature is agreed.
6. Test rejection, concurrent draws/completions, membership removal, offline reconnect and account switching with Firebase emulator rules before deployment. No Firestore rules or cloud data are deployed by this branch.

## Verification

Run unit tests and build both APKs:

```bash
./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest
```

Use a disposable emulator for fixture-based integration tests:

```bash
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=no.ntnu.prog2007.questbook.GroupChallengeStoreTest,no.ntnu.prog2007.questbook.GroupChallengeNavigationTest,no.ntnu.prog2007.questbook.PersonalQuestNavigationTest,no.ntnu.prog2007.questbook.PersonalQuestStoreTest,no.ntnu.prog2007.questbook.QuestTrackingStoreTest
```

Store tests cover frozen targets, independent completions/sessions, owner-only drawn quests, optional photos, membership boundaries, idempotent weekly input and legacy recovery. Navigation tests cover the real group page, both members' completion cards, photos in Memories, direct repetitions and Start/Pause GPS. Device tests still do not prove cross-device synchronization, satellite reception or physical-phone battery behavior.
