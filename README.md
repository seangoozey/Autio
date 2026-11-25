# Autio Notes

Personal audio-note recorder prototype for the Pixel 8a. Open this directory in Android Studio.

## Current behavior

- Record creates a new note. Pause releases the microphone and audio focus but keeps the note open.
- Resume continues the same note, omitting the paused interval. Resume after an interruption is manual.
- Stop finalizes the current note, including while paused. Committed notes cannot be appended to.
- Audio-focus loss (including ducking requests) and Android silencing the microphone pause capture. Call/communication audio modes also prevent capture.
- A microphone foreground service keeps recording with the app in the background. Its notification provides Pause/Resume and Stop controls.
- The live RMS and peak meter measures the actual PCM samples being written. Five seconds below -55 dBFS shows a low-input warning; near-full-scale samples show clipping.
- The recorder uses a large timer, colored state badge, a segmented -60 to 0 dBFS meter, and a single Record/Pause/Resume button. Layout accounts for display density and system-bar insets.
- The recording screen has no scroll container. Landscape uses two columns: timer and controls on the left, microphone input on the right.
- Saved notes show dates and durations, with inline playback, pause/resume, seeking, and sharing. Playback requests audio focus and stops when leaving the library.
- New notes save as mono AAC-LC in an `.m4a` container at 64 kbps, approximately 29 MB per hour (plus container overhead), instead of roughly 318 MB/hour for WAV.
- Capture streams 44.1 kHz mono PCM samples directly into an AAC encoder while using those same samples for the input meter. Recoverable AAC frames and the M4A container are written throughout recording; no temporary WAV is created. Pauses add no samples. Stop flushes the encoder, finalizes the M4A container, and removes AAC staging after success. A finalization failure retains the AAC locally. Two compressed staging copies use roughly 60 MB/hour during recording, rather than a large PCM file.
- Stop retains a private compressed original and copies the completed M4A to `Music/Autio`. No unfinished file is published to that folder. Saved notes can be played or shared. Existing WAV notes remain supported and are not automatically converted.
- Compressed recording data is checkpointed every two seconds and on pause. On reopening the app, abandoned AAC `.pending` files are recovered as separate notes. Legacy WAV recordings remain recoverable as well. Recovered notes can be exported using Share.

## Sync and car status

Under Saved notes → Note delivery, select a folder already shared by Syncthing-Fork. Completed notes are copied there when stopped. Android's folder picker grants persistent access; Autio keeps its private original and the `Music/Autio` copy. Copy saved notes retries delivery and can backfill earlier notes without creating duplicate filenames. Copy failures leave the private original available for retry.

The connected phone is configured to use `syncthing/Recordings`, which syncs to `E:\Sync\Recordings` on this workstation. Autio asks Syncthing-Fork to start after the copy. Service Control by Broadcast must be enabled in Syncthing-Fork; it was already enabled on this phone. The setting can be disabled in Autio. START forces Syncthing to run outside its normal run conditions, and Autio does not stop it afterward. Cellular sync was enabled by the user.

Copies are staged under a unique Syncthing temporary name and renamed after writing, so incomplete copies are excluded from scans. Autio relies on the existing Syncthing filesystem watcher/scan schedule; it does not call the REST scan API or store an API key. The app reports a local folder copy, not remote delivery confirmation. A test recording was received on the workstation and its SHA-256 matched the phone copy on October 28, 2026.

Hook implementation reference: [Syncthing-Fork AppConfigReceiver](https://github.com/researchxxl/syncthing-android/blob/main/app/src/main/java/com/nutomic/syncthingandroid/receiver/AppConfigReceiver.java). File watching and temporary-file behavior: [Syncthing synchronization](https://docs.syncthing.net/users/syncing.html).

Android Auto integration uses a genuine `MediaBrowserServiceCompat`/`MediaSessionCompat` saved-note player plus explicit Record, Resume and Stop-and-save custom actions. Standard Pause controls capture, with no duplicate custom Pause advertised while recording. The media host displays recording status, elapsed time and a text-based dBFS indicator. Record now starts and enables the drive session automatically; this was verified from the actual Android Auto emulator with the phone activity backgrounded and manual preparation disabled. If Android rejects a start, phone-side manual enabling under Saved notes → Android Auto controls remains a fallback. The service stays ready between notes until End car session. Generic Play and automatic media resumption never start or resume the microphone. Idle skip controls are omitted to put Record beside the main control. Red artwork and an app accent are supplied, although Android Auto still tints the actionable custom icon white.

The recorder still uses Android's normal microphone input, not `CarAudioRecord`. It reports the actual routed input category (built-in, Bluetooth, wired headset, USB or external input). Android Auto discovery and recording controls were verified with Google's Desktop Head Unit 2.0 using the connected Pixel, including recording while the phone activity was backgrounded. This does not establish that the Binize's microphone is routed into `AudioRecord`. Test the actual head unit while parked; car microphone capture remains unverified. See [ANDROID-AUTO.md](ANDROID-AUTO.md).

The user identified the head unit as Amazon ASIN **B0FJ2LKBW3**: the Binize 9-inch Android 13 radio for Toyota Prius 2010–2015. The [exact listing](https://www.amazon.com/dp/B0FJ2LKBW3) advertises downloading applications over Wi-Fi. This supports a native app installation test, separately from projecting the Pixel through Android Auto. The advertised Android version exceeds this app's minimum Android 10/API 29; the actual firmware/API level, app installation, microphone routing, AAC encoder and Bluetooth-call audio-focus behavior still need verification on the head unit.

The user prefers Android Auto projection from the Pixel as the primary deployment path; native installation on the Binize is a fallback only if that fails. The media integration is a development prototype, not a statement of Google Play approval for recording custom actions. On the Pixel, Android Auto developer mode and Unknown sources have been enabled for development-build discovery.

`dist/Autio-Notes-Binize-test.apk` is a copy of the current debug APK for a fallback head-unit test. See [BINIZE-TEST.md](BINIZE-TEST.md). Updating this copy after subsequent builds requires recopying the APK.

References: [car microphone](https://developer.android.com/training/cars/apps/library/car-microphone), [supported templated categories](https://developer.android.com/training/cars/apps/library), [audio focus](https://developer.android.com/media/optimize/audio-focus), [microphone sharing](https://developer.android.com/media/platform/sharing-audio-input).

## Managed library and reliability (0.6)

The selected managed folder is now the library, with direct playback and external deletions reflected on refresh. Private audio is retained only until verified delivery; new saves no longer create Music/Autio copies. Older copies in Music/Autio remain untouched. Recovery, persistent delivery retry, accurate save status, low-storage checks, Settings consolidation, and discard confirmation are described in [RELIABILITY.md](RELIABILITY.md). The user has verified that an incoming phone call pauses capture.

## Build

The separate, non-recording Android Auto interface experiment is documented in [INTERFACE-LAB.md](INTERFACE-LAB.md). It demonstrates labeled Record, Pause, Resume, and Stop controls in a Car App Library template without modifying the working recorder.

Requires Android SDK 37, Build Tools 36.0.0, and a suitable JDK. Android Studio Rabbit's bundled JDK 25 was used. The user's upgrade is preserved: the wrapper pins Gradle 9.6.0 and AGP is 9.5.0.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat assembleDebug assembleDebugAndroidTest lintDebug
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Verification

Build and lint pass (remaining lint warnings concern localization, backup rules, synchronous preference persistence on a background thread, and an available Gradle update).

The media integration test runs with `-e media true`. It covers browser startup without a visible activity, the real saved-note catalog, an automatic enable/start attempt (or a surfaced Android restriction), the manual preparation fallback, recording actions from the background, live input metadata, absence of duplicate Pause actions and idle skip buttons, protection against automatic Play resuming the microphone, system Pause/Stop, playback/seek, a car session persisting between notes, and invalid media ID rejection. The same controls were also exercised through the actual Android Auto screen in Google's Desktop Head Unit emulator.

The instrumented test captures a few seconds of real microphone audio. It verifies that pause omits time, resume continues the same note, a competing audio-focus request pauses recording, returning focus does not automatically resume, stopping while paused commits the expected duration, the output contains an AAC track, compression reduces size, compressed staging is removed, and export and delivery succeed. Delivery retries must not create duplicates and the delivered bytes must match the private original.

```powershell
& $adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
& $adb shell pm grant com.autio.notes android.permission.RECORD_AUDIO
& $adb shell pm grant com.autio.notes android.permission.POST_NOTIFICATIONS
& $adb shell am instrument -w com.autio.notes.test/com.autio.notes.RecorderSmokeTest
```

Passed on the connected Pixel 8a running API 37 on October 28, 2026, including after the interface redesign. The resulting WAV contained nonzero microphone samples. Recording, paused, saving, library, and playback layouts were visually inspected on the unlocked Pixel. The primary button and Stop control were also exercised manually. An actual incoming call, long-duration screen-off capture, crash recovery, and car hardware have not yet been tested.

Compressed recording and delivery tests also pass. A synthetic one-hour AAC stream assembled from repeated encoded packets produced a 29,424,431-byte M4A with a duration of 3,600,022 ms and working seeking near the end. This validates hour-long file handling, rather than an hour of real-time microphone capture. New recordings encode and mux during capture, so normal Stop does not need a full-file conversion pass. Existing WAV notes are left intact.

## Microphone processing (0.7)

Open Settings from the recorder gear, then Microphone processing. Audio sources include Microphone, Voice recognition, Voice communication, and Unprocessed when the phone advertises support. Noise suppression, echo cancellation, and automatic gain control offer Android default / Off / On when Android exposes that effect. Changes apply at the next start or resume. Defaults keep the original MIC source and leave system effects under Android control.

The current/last session report shows source, input route, stream information, effect requests and reported states, rejected or unavailable controls, and the proportion of PCM samples within 68 counts of full scale before AAC encoding. Android-default effects are observed through the capture-path report without attaching an override. Processing in the head unit or Bluetooth link remains outside these session controls.

Verified November 4, 2026 on Pixel 8a. Build/lint and real recording tests pass, including effect On/Off requests, switching sources on resume, sample continuity, restoring defaults, and diagnostic persistence. The Pixel exposes noise suppression and echo cancellation, but reports AGC unavailable. All four offered sources were exercised, with Unprocessed conditional on advertised support. Settings screen visually checked and version 0.7 installed. Android Auto test APK in dist updated.
