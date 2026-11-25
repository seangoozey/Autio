# Managed library and recording reliability (0.6)

The selected managed folder is now the authoritative library. Phone and car playback open its document URIs directly. The library is re-read when reopened; the media browser refreshes its catalog periodically. Audio still awaiting delivery appears as private/pending. Existing recordings from other apps in the selected folder are also visible.

## Ownership and migration

New recordings stage AAC and M4A privately. Stop finalizes the M4A, copies it into the managed folder using a Syncthing-excluded temporary name, verifies its bytes with SHA-256, and stores a durable delivery receipt. Only then is private staging released. A receipt prevents a retry from recreating a file that has subsequently been deleted or moved out of the managed folder.

Existing private originals are delivered/verified through the same path and released. If a managed file with the same name has different bytes or size, the private original is retained with an error; the managed file is not overwritten. Copies left in Music/Autio by older builds are unchanged. New saves and shares do not create Music/Autio copies. Pending notes use a FileProvider for sharing.

## Recovery and retries

Private staged files are the persistent queue. On app/media-service entry and during a persisted periodic JobScheduler job, interrupted AAC is recovered and finalized to M4A, then delivery is retried. The job survives reboot; Android controls when it runs (15-minute periodic request, not a timing guarantee). Force-stopping the app suspends its background work until it is opened again.

Failed finalization reports recovery needed rather than Saved. Successful finalization with failed delivery reports Saved privately / delivery pending. The notification of successful folder delivery makes no claim that another device has received the file; Syncthing owns transfer.

Recording is blocked below 32 MiB of usable private storage and pauses below 16 MiB during capture, allowing the user to stop/save. No notes are automatically deleted to free space.

Cancel writes a durable discard marker before encoder shutdown, so process interruption cannot recover intentionally abandoned audio. The phone shows a discard confirmation; Cancel sits beside Stop & save. Car templates allow only two pane actions, so Pause/Resume and Finish note are the main controls; Finish opens Stop & save / Discard choices, with Back keeping the note open. Confirmation applies only to the same note generation.

## Interface

The recorder's gear opens Settings, containing Note delivery and Android Auto controls. Those buttons have been removed from Saved notes. The library footer describes the managed-folder model. Duration metadata for older imported recordings is read on demand rather than opening every audio file when loading the catalog.

## Verification — October 29, 2026

- Build and lint pass; updated APK installed on Pixel 8a and copied to dist/Autio-Notes-Android-Auto-test.apk.
- Recorder integration passes: capture, pause gaps, focus interruption, manual resume, save, AAC duration/size, managed-folder delivery, private cleanup, and cancel while recording/paused.
- Media integration passes: real managed-folder catalog, Record/Pause/Resume, no automatic Play microphone resume, system Stop, playback/seek, persistent drive session, and invalid ID rejection.
- Isolated reliability integration passes: interrupted AAC with broken MP4 remuxes; failed folder access retains staging; restored access retries; delivered bytes match; private staging is released; crash after finalization recovers; managed-folder deletion stays deleted despite private residue; discard marker wins over recovery.
- Phone gear/settings, library without settings buttons, Cancel beside Stop, and the discard dialog were checked on device. Confirming discard removed the test audio and private files directory was empty afterward.
- User verified a real incoming phone call pauses recording correctly. Full-hour screen-off capture and the revised car Finish menu on actual Binize hardware remain field checks.

The new isolated test uses `-e reliability true` with the existing instrumentation runner. It uses isolated staging/preferences and uniquely named `.syncthing` test documents, and removes its test documents afterward.
