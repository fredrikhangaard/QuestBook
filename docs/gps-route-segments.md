# Pause/resume route sections

Previously GPS totals excluded paused movement, but saved positions did not indicate where pauses happened. The route viewer could therefore draw a line across a pause even though that movement was never counted.

Every first accepted point after Start or Resume now has `startsSegment = true`. Normal accepted points use false. The marker is part of the same immutable Room point/checkpoint write as the position; the existing ordered queue, retry handling, and completion flush remain in use. Rejected/inaccurate/old fixes do not consume the marker. A pause without accepted points creates no empty section.

This applies to manual pauses, leaving the tracking screen, backgrounding, GPS failures that stop tracking, and resuming a restored session. All of those paths already reset the distance anchor. The marker uses that exact anchor reset, keeping the drawing and measured distance aligned. Distance and active-time calculations are unchanged.

The viewer fits all sections in one preview, draws a separate path for each, and displays the tracked-section count when there is more than one. Single-point sections appear as dots. Start and Finish still refer to the whole activity. A filtered invalid point does not discard the break before the next valid point.

## Existing data

Room migration 4 → 5 adds the non-null Boolean column with a default of false. Existing routes, checkpoints, results, and photo bytes are preserved. Old recordings render as one section because their original pause locations were not stored; the app does not guess boundaries using timestamps. If an old active session is resumed after upgrading, its newly accepted resume anchor starts a new section.

## Validation

- Unit tests verify marker placement after pause and restore, rejected resume fixes, ordered retry, and segmentation including isolated points.
- Room tests exercise tracking → pause → restore → resume → complete → database reopen and verify both boundaries and unchanged distance/time totals.
- Migration tests cover versions 1, 2, 3, and 4, including preservation of routes, quest rows, checkpoints, completion results, and photos where supported by each historical schema.
- Compose tests capture a route with an explicit gap and verify the section count and saved metrics.

For a manual check, start an assigned distance quest, record some movement, pause, move elsewhere, resume, and record more movement. Complete it and open View route: the paused interval should be a gap. Reopen the app and verify that the gap remains.

No Firestore schema or rules are changed. A future route uploader must include the section marker in its cloud contract too; the earlier proposed cloud rules do not yet allow that new field.
