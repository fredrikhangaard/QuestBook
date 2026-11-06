# Solo daily and weekly quests

Every signed-in user receives one daily quest and four weekly goals, even without a group. Home and Quests show active personal challenges separately from group challenges; completed quests appear below the active section on Quests.

## Pool and calendar rules

The adventure pool contains 122 daily templates: playful 1/2/3/5 km discovery walks, push-up/squat/generic bodyweight sets, and water/park visits. The prompt adds variety while the measurable requirement stays within the existing tracking systems. Finding an object, having company and adding a photo are optional. The 154-template group-draw pool also includes social walking prompts. A daily draw avoids the last daily's exact template.

Four weekly families remain: walking distance (15/20/25 km), bodyweight repetitions (100/150/200), outdoor visits (2/3/4), and active days (3/4/5). Active-minute quests are no longer generated or shown as current goals; existing time-quest history and recorded GPS duration are retained.

Assignments and targets are saved before they appear. Reopening does not reroll them. Daily windows end at local midnight; weekly windows run Monday to Monday. Local midnights handle DST, and each assignment retains its timezone/window. New periods are generated when the app is running/opened; missed days are not backfilled and there are no scheduled notifications.

## Recording activity from a quest

- Walking has Start quest, Pause and Resume, with precise-location permission requested only after Start. There is no unrestricted activity selector and no new manual distance entry. Earlier manually recorded walks remain valid historical data.
- Daily/open/drawn walking attempts use their own finite target and automatically stop GPS when it is reached. Pausing before the target keeps the same session and route for Resume. Finish saves the physical result once.
- Weekly walking buttons start a bounded session of at most 5 km, or the smaller remaining target. Its source-goal link keeps an unfinished walk resumable after returning to the weekly page, including after app restart. A new bounded walk is created only after the previous one has been saved.
- Repetition quests have +1/+5/+10, a typed-number field and Save. Buttons edit the unsaved set; only Save writes it. Specific push-ups/squats contribute to bodyweight totals. Generic bodyweight input does not pretend to be a specific exercise.
- Visit quests require explicit confirmation, not image recognition or automatic location classification. A generic outdoor visit does not pretend to be a water/park-specific visit. Custom task confirmations do not become outdoor visits.
- Active days are derived automatically from saved activity on distinct calendar dates. There is no direct edit to an active-day counter.
- Every completed quest can have optional photos through the existing result-linked photo flow. A photo is never required to complete a task.

## Contributions and duplicate prevention

One physical event can advance the daily goal, personal weeklies and eligible group weeklies, while adding its distance/repetitions/time once to the user's activity summary. GPS events come from immutable saved GPS results; manual events come from activity_entries. Daily/weekly/group awards do not produce new physical events. Manual submission IDs and transactions make retrying the same operation idempotent.

Progress is credited when a physical activity is saved, using its completion/log timestamp. Unfinished GPS checkpoints do not yet contribute to other goals. A walk spanning midnight/Monday is credited to the period when it is saved, not retrospectively split. An already-started session may still be resumed and saved after expiry, but earns no expired daily award. The physical result/route remain. See `group-quest-adventures.md` for conservative membership eligibility and shared attempts.

The GPS service, sequential writer, failure retry, resume anchors and completion barrier are unchanged. A pending session must be finished before its own automatic daily award can finalize, preventing an award from invalidating pending GPS writes.

## Summary and storage

Memories shows personal weekly totals and a dated completion feed, with previous/next week controls. Shared group completion/photo cards are included when present on this device, while precise routes remain owner-only. Other members' activity never inflates the current user's summary. Weekly achievements are counted separately from completed activity/daily quests.

Room version 7 preserves all earlier data and archives active-minute assignments. The new generic repetition field distinguishes unspecified bodyweight work from push-ups/squats. Personal identifiers are local owner scopes, not Firestore groups. Draws, progress, attempts, results and photos still need the teammate's cloud synchronization contract before they appear across phones. Device time is not a trusted anti-cheat clock.

## Validation

Unit tests cover calendar/DST boundaries, real units, distinct active days, pool coverage, membership projection and physical-event/award separation. Room tests cover persistence, rollover, concurrent refresh, owner checks, direct weekly sets, bounded walk resume, legacy manual distance, independent group attempts and schema upgrades. Real-screen tests cover optional-photo visit completion, repetition buttons/typed input, summary browsing and explicitly starting/pausing GPS from a weekly quest.
