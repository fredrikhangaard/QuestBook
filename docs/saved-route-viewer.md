# Saved route viewer

Open a completed GPS quest and choose **View route**, or choose **View route** on its card in Memories. Back returns to the page you came from. The screen displays the completion's stored distance, active duration and average pace alongside its recorded path, start/finish markers, and GPS point count.

This is an offline, north-up route diagram, not a street map or navigation tool. It requires no map key, network connection, or location permission. It does not start GPS or change any saved tracking data. The drawing scales both axes equally and adjusts longitude spacing for latitude; a local projection is suitable for walking routes. The displayed metrics always come from the saved completion, rather than recalculating them from the simplified drawing.

## Data and privacy

`RoomSavedRouteReader` resolves the quest's completion, checks the signed-in user ID, then loads only that result's GPS session. Positions retain the DAO's timestamp/id order. Invalid coordinates are ignored. Missing results use the same unavailable message whether absent or owned by someone else. Account changes cancel pending reads and clear the old route. The existing database schema and migration chain are unchanged.

No GPS session: the screen explains that this quest has no GPS route. A session with no available valid points shows a missing-points message while retaining completion statistics. A single point places start and finish at the same location. A failed database read offers Retry.

## Validation

- JVM tests cover north-up orientation, Norwegian-latitude aspect ratio, date-line crossings, single/repeated/straight routes, account changes during a pending read, and retry after a database failure.
- Room instrumentation verifies ownership, the linked session, chronological position order, and missing points without removing the result.
- Compose instrumentation verifies entry from Memories, Back, the completion route action, statistics, single-point/empty/error states, and sample screenshots. The existing local Memories/photo flow is also exercised.

The screenshots use sample data and isolated screen content. Live GPS, cloud synchronization, and a street-map service are outside this feature.
