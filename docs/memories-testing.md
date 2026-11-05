# Completion and local Memories

Complete an assigned distance quest and tap Finish. The success page appears only after Room commits the result. Check its distance, active duration, and pace, then select Add a photo, choose a gallery image, add a caption, and Save. The app opens Memories after the local write succeeds. Reopen the app to check that the result and photo remain. View quest reopens its saved completion; View Memories also works without adding a photo.

Sign out and use a different account: the previous user's local results/photos must not appear. Cancel the photo picker: no photo is saved. Saving an image for an incomplete quest or another user's result must fail.

These memories are personal and stored on this device. Shared group history and cloud photo/result synchronization are future integration work. The Firestore photo uploader is not invoked by this new local completion flow. Version 4 adds memory_photos while preserving existing quest, route, session, and result tables.

## Automated flow and previews

`MemoriesFlowPreviewTest` uses a separate, disposable Room database. It completes two assigned quests with deterministic GPS checkpoints, runs the real image compressor and AddPhotoViewModel, saves a captioned photo, closes and reopens the database, checks both results and photo ownership, and captures the actual Compose layouts. One completion intentionally has no photo. Its sample image is labelled and contains no personal media.

Run with `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=no.ntnu.prog2007.questbook.MemoriesFlowPreviewTest`.

Screenshots are written under the test device's app external files directory, `memories-previews`. The test composes the production screen content with a small test navigation wrapper; it does not launch the real Android gallery, receive live GPS, exercise the production NavHost, or contact Firestore. Those remain separate integration checks.
