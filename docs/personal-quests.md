# Solo daily and weekly quests

Every signed-in user receives one daily quest and five weekly goals, even without a group. Home and Quests show personal challenges separately from group quests. **Log personal activity** lets a solo user keep progressing after today's daily is complete or when it covers a different activity.

## Pool and calendar rules

The first version contains 14 daily templates: 2/3/5 km walks, 10/20/30 push-ups, 20/30/40 squats, 5/10/20 active minutes, a water visit, and a park/green-space visit. A new draw avoids the last daily template. The five weekly families are walking distance (15/20/25 km), active time (90/120/150 minutes), bodyweight repetitions (100/150/200), outdoor visits (2/3/4), and active days (3/4/5).

Assignments and target snapshots are persisted before they appear. Reopening or refreshing does not reroll them. Daily windows end at local midnight; weekly windows run Monday to Monday. Local calendar midnights handle daylight-saving changes. An assignment retains its own timezone/window. New periods are generated when the app is running/opened; the app does not backfill missed days or send scheduled notifications.

## Activities and contributions

- Saved GPS results contribute their actual distance and active duration. Existing group GPS results count too.
- Manual walking, push-ups, squats, active minutes, and visits are explicitly self-reported. Repetitions can be logged in smaller sets. Optional active minutes can accompany other manual activities. Decimal commas and decimal points are accepted by the form.
- A water/park visit requires the user's confirmation, not photo recognition or automatic water detection. Photos are optional and can be added after completion using the existing local photo flow.
- One activity can advance several compatible goals: a 5 km/60 minute walk adds 5 km, 60 active minutes, and one active day. Thirty push-ups add 30 bodyweight repetitions and an active day, but no distance. Several activities on the same calendar date still produce only one active day.
- Physical activities are counted once. GPS activities come from immutable GPS results; manual activities come from activity_entries. Synthetic daily/weekly completion results do not create another physical activity, so achievements cannot recursively add progress or inflate distance/time.
- Manual submissions use an idempotency key and transactions. Retrying the same submission does not add the activity twice. Do not manually log an activity already saved with GPS: the app cannot detect duplicate real-world activities reported as separate submissions.
- Personal walking quests reuse precise-location permission, foreground pause/resume, sequential checkpoints, and saved route sections. If other saved activity already advanced a daily walk, a newly started GPS session targets the remaining distance. Manual entry is blocked while that quest has a saved GPS session. A retained GPS session is finalized through Finish before a daily award is finalized, so an automatic goal award cannot invalidate its pending GPS writer.

Progress is credited when an activity is saved, using its completion/log timestamp. Unfinished GPS sessions do not yet contribute to other goals. An activity spanning midnight/Monday is credited to the period in which it is saved; it is not split retrospectively across calendar boundaries. An already-started GPS activity can still be resumed and saved after daily expiry, but cannot earn the expired daily award. Its actual result/route are retained and contribute to the new period. Extra personal activities have no daily deadline.

## Weekly activity summary

Memories includes a current-week summary and previous/next week controls (no future weeks). It shows completed quests, distance, active time, active days, bodyweight repetitions, outdoor visits, and completed weekly goals. Weekly goal awards are reported separately from completed activity/daily quests. The feed follows the selected week. Totals include both GPS and self-reported activity and are personal, local-device totals.

## Storage and integration boundaries

Room version 6 adds personal_quests (stable owner-scoped assignments) and activity_entries (self-reported activity). Existing quests, sessions, results, photos, and route sections are preserved. The existing quests table holds the corresponding assigned personal quest so GPS and result/photo navigation can be reused; personal rows are excluded from group quest lists. Readers and write operations check the owning user ID. Personal group IDs are local identifiers, not real shared groups.

Everything remains local per signed-in account. The draw/progress history does not yet synchronize between devices. Device time is not a trusted anti-cheat clock. Firestore rules and deployment are untouched: personal assignments, manual events, goal award results, ownership, idempotency, and shared period/timezone policy need an explicit cloud contract with the teammate implementing synchronization. The earlier proposed group-only rules are not a drop-in contract for personal quests.

## Validation

JVM tests cover calendar/DST boundaries, exact contribution units, active-day deduplication, pool coverage, and physical-activity/award separation. Room tests cover stable assignment persistence and concurrent refreshes, rollover, per-account data, daily-to-weekly GPS credit, incremental manual sets and retry, visits without photos, manual+GPS remaining targets, extra activities, validation failures, and upgrades from previous database versions. A production MainActivity navigation test uses a fake signed-in solo account with no groups to complete a visit without a photo, save a workout, inspect the weekly summary, and open an assigned GPS activity.
