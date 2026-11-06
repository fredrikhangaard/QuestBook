# Functional readiness

This checklist separates local behavior that can be verified independently from the shared backend and physical-device checks still needed before calling the app finished.

## Local reliability

- GPS uses one location foreground service and one ordered Room writer. A started walk continues across navigation, Activity recreation and screen locking. Pause is available in details and the ongoing notification.
- Service start requires the explicit Start/Resume flow, precise permission, enabled device location and a persisted assigned session. There is no automatic restart after process death, reboot or Android Stop.
- Account changes, observed quest access changes and disabled device location pause tracking. Only one quest may track at once. Checkpoint recovery remains paused and preserves route segments.
- Failed GPS writes remain retryable; completion waits for durable writes. Existing migration, owner-access, photo persistence and personal quest rollover tests remain regression checks.
- Profile fetch failures show Retry loading profile, with logout still available. A late profile fetch cannot restore the old profile after logout.
- Only the newest selected photo can update its preview. Read/save failures remain retryable. A pending save keeps its original photo and caption even if another picker result arrives.
- Personal daily/weekly quests and physical activity accounting remain local per UID/device. See `personal-quests.md` for rollover, matching units and duplicate-credit rules.

## Shared backend handoff — still required

- Integrate the teammate's quest/assignment/result synchronization with the existing repository contracts. Group completion and photos must appear for another account, not just the completing phone.
- Define the cloud contract for personal assignments, activity events and weekly awards before syncing them; local namespace IDs alone do not establish server authorization.
- Enforce group membership, assignee permissions, immutable completion ownership and private route/photo access in deployed rules. Test unauthorized access in the Firebase emulator. Cached client checks alone are insufficient.
- Verify offline changes, reconnect, rejected writes, conflicts, account switching and duplicate completion handling across two devices. Do not show a cloud operation as successful before the relevant persistence contract is satisfied.
- Confirm the intended group management scope: leaving a group, removing a member and owner transfer/deletion need agreed behavior and corresponding backend rules/UI. They are not implemented by the GPS reliability work.

## Physical-device acceptance — still required

1. Use two real accounts on two installations. Create/join a group, assign a quest, complete it, add an optional photo and check the other member's view.
2. Walk outdoors with the screen locked for at least 10 minutes. Check plausible distance/time, notification controls and battery behavior. Repeat with battery saver and ordinary app switching.
3. Deny precise location, allow approximate only, deny notifications, disable location mid-walk and revoke permission in settings. Confirm clear recovery with no automatic restart.
4. Pause, move, resume and finish. The paused movement/time must be excluded and saved route segments must remain separate.
5. Stop/force-stop/reopen the app during an unfinished walk. Confirm paused checkpoint recovery and explicit Resume. Do not clear app data or uninstall as a recovery test.
6. Pick a real gallery photo and use the camera path if offered; cancel, retry, change images quickly and reopen Memories after restarting.
7. Verify daily/week rollover, previous-week summaries and completion with a solo account and a group member. One physical activity should advance matching goals without inflating summary totals.

## Release checks

- Run unit tests, debug build and relevant device tests after backend integration; repeat the end-to-end acceptance flow against the deployed rules.
- Confirm required assignment/account features against the course backlog. Features outside that agreed scope do not need to block this version.
- If distributing through Google Play, review current location foreground service/permission declarations, privacy/data disclosures and target-SDK requirements with the final implementation. No store submission or deployment is performed here.
