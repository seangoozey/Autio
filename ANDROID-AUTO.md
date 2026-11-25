# Android Auto prototype

## Current controls and storage (0.6)

The selected managed folder is now the authoritative library, including for car playback. Private originals are retained only until verified delivery; deleted managed notes are not recreated. See [RELIABILITY.md](RELIABILITY.md) for migration and recovery behavior.

While a note is open, the main controls are Pause/Resume and **Finish note…**. Finish opens **Stop & save** and **Discard**; Back keeps the note open. Discard is no longer an immediate header X. Phone settings are consolidated behind the recorder's gear. Earlier version notes below describe previous layouts and storage behavior.

## Dedicated recorder screen (0.4)

The installed development build now opens a Car App Library recording screen on the tested host. Use the large red **Record** button; recording shows **Pause** and **Stop & save**, and paused notes show **Resume** and **Stop & save**. The timer reflects recorded audio only. Actual input dBFS and input route update approximately once per second, subject to host refresh behavior. **Saved notes** in the header opens the library with a separate, explicitly labeled playback screen.

The Pixel is configured with Android Auto Application Mode **Developer** and **CAL beta features** enabled. Reconnect Android Auto after installation if the launcher uses the old surface. The original media-browser implementation remains available for hosts that use that surface; its controls are described below. This version still needs verification in the Binize projection session.

Verified through Desktop Head Unit: actual Record → Pause → Resume → Stop, paused timer at 10 seconds, final M4A duration about 17 seconds, sync-folder delivery and arrival on this workstation, saved-note playback and Pause/Stop. Build, lint, and the existing media integration test pass. The host's MediaPlaybackTemplate failed to load its controller despite audio playback starting, so the development build uses a PaneTemplate playback screen instead. This is a developer prototype and does not establish Play distribution compliance. System media playback intents are handled by the car Session.

Review APK: `dist/Autio-Notes-Android-Auto-test.apk`. Screenshots: `verification/car-screen-recording.png`, `car-screen-paused.png`, and `car-screen-ready.png`.

Autio runs on the Pixel and appears in the Android Auto media launcher. Direct APK installation on the Binize is a fallback, not the primary setup.

## Before a drive

1. Grant Autio microphone access once on the phone if it has not already been granted.
2. Connect the Pixel to the Binize using Android Auto and open **Autio Notes** from the car launcher.
3. Tap the ring-and-dot **Record** action beside Play. It now starts the foreground recording service and enables the drive session automatically.

The direct start was verified in Google's Desktop Head Unit with the phone activity in the background and the manual drive setup disabled. If a different host or Android state blocks the start, Autio reports that restriction. The fallback is **Saved notes → Android Auto controls → Enable recording manually** on the phone, then retry Record in the car.

For a development APK, Android Auto needs **Developer settings → Unknown sources** enabled. This has been enabled on the connected Pixel. To set it up on another phone, open Android Auto settings, expand Version and tap Version and permission info ten times, then open Developer settings from the overflow menu. [Google's testing instructions](https://developer.android.com/training/cars/testing).

## Controls

- The ring-and-dot custom action is **Record**. It enables the drive session and starts capture with one tap. Idle Previous/Next actions are omitted so Record sits beside the main control.
- While recording, only the main Pause button is advertised; the duplicate custom Pause has been removed.
- While paused, the custom triangular action is **Resume note**. Generic Play never resumes microphone capture, because Android Auto may issue Play automatically on connection.
- The square custom action is **Stop and save note**. It finalizes the M4A and copies it to the configured sync folder.
- The drive session remains enabled between notes. End it on the phone or using the recorder notification's **End car session** action when no note is open.
- Saved notes are genuine playable media items. Play/Pause, seeking, next and previous act on playback while no recording is open. Playback is blocked until an open recording is saved.

The Android Auto host chooses the layout and may place custom actions under an overflow menu on some screens. Recording metadata shows status, elapsed time, a text bar and RMS dBFS; it is not the phone's custom graphical meter. Unknown recording duration is declared as such rather than showing a zero-length finished track.

Autio provides a red accent theme, red record artwork and an enlarged ring-and-dot icon. Android Auto 17.9 still tints the actionable Record icon white in this media template; the app cannot independently force that control's tint to red. The large media Play button remains playback and is never repurposed to start recording.

## Verified

- Build and lint pass with AGP 9.5.0 and Gradle 9.6.0.
- The Pixel's browser/session integration test passes, including starting capture from the background after drive setup.
- Android Auto 17.9 discovers Autio in Google's Desktop Head Unit 2.0 emulator.
- The Android Auto screen displays Record, Pause, Resume and Stop controls, recording status and live input levels.
- Record → Pause → Resume → Stop was exercised through the projected car screen with the phone activity in the background. The completed compressed note arrived in `E:\Sync\Recordings` through Syncthing.

The input in this test was the Pixel's microphone. The media browser API does not itself forward car microphone audio. Version 0.8 adds an optional `CarAudioRecord` host path; the earlier tests above used phone capture.

## Next test on the Binize

While parked, connect Android Auto, enable the drive session and record from the car controls. Speak close to the car microphone and then the phone microphone separately; inspect the reported source on the phone and listen to the resulting note. Establish which microphone supplied audio before relying on this for driving notes.

Then test an actual incoming call and navigation/media interruptions. The phone audio-focus tests pass, but these have not yet been validated with this head unit. If the car microphone is not delivered through the current input route, that is a separate capture-path problem; the media interface has already been demonstrated to work.

## Run the car-screen simulator on this PC

Google's Desktop Head Unit is installed in `.tools/dhu`. Connect and unlock the Pixel by USB, open Android Auto settings on the phone, and select **More options → Start head unit server**. Developer mode has already been enabled on this phone. Double-click `Start-Android-Auto.cmd` in the project folder, then press a key in its console when the phone is ready. A visible Android Auto simulator window opens; use the mouse as the touchscreen and select Autio Notes. Recordings are real phone recordings and use the configured sync setup.

Close the simulator window or type `quit` in its console when finished, and select **Stop head unit server** on the phone. [Google's DHU instructions](https://developer.android.com/training/cars/testing/dhu).

## Automated device test command

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb shell am instrument -w -e media true com.autio.notes.test/com.autio.notes.RecorderSmokeTest
```

Run this only when no recording is open: instrumentation restarts the app. The test records a short note, plays a saved note, and ends its drive session afterward.

## Icons and cancellation (0.5)

Car actions now include circle Record, double-bar Pause, triangle Resume/Play, and square Stop icons alongside their labels. An X action in the recording screen header discards the open note; it is available during recording and while paused, and hidden during finalization. The phone screen also has Cancel in its header. Cancellation releases capture and encoder resources, deletes AAC and M4A staging, resets recorded time, and never calls publication or sync delivery. Stop retains its existing commit behavior.

The app launcher, car app icon, and media artwork now use the phone header's three-bar waveform in mint (#73E8C6) on black. A host reconnect may be needed to refresh a cached app icon.

Verified October 29: build/lint, recorder integration test including cancellation while recording and paused, and media integration suite pass. Simulator visually renders the circle, pause bars, square, and header X. Test temporary audio is removed.


## Experimental host microphone (0.8)

On the phone, choose **Settings → Microphone processing → Capture path → Android Auto host (experimental)**. Open Autio Notes in Android Auto, then Record. Incoming host PCM is mono, 16 kHz, signed little-endian 16-bit; streaming interpolation feeds the existing 44.1 kHz AAC encoder. This does not restore high-frequency detail. Saving, recovery, delivery and sync use the existing pipeline. No recording file is staged on the head unit by Autio.

Phone audio source and Android effect overrides are disabled for host capture; host/head-unit processing remains outside these controls. The selected path applies on start or resume. If the host is unavailable, capture pauses with an explanatory message and does not fall back to another microphone. Host termination, session destruction and audio-focus interruption pause the open note. Resume requests a new stream.

Validation November 5: build/lint and Pixel recording regression pass. Host-specific instrumentation verifies duration, pitch, PCM endianness and continuity across variable buffer boundaries, missing-host pause without fallback, and cancellation. Desktop Head Unit opened the host channel and exercised host-ended/focus-interruption pause behavior. Its PC microphone failed with PortAudio Device unavailable; `mic play` activates the assistant and did not establish an Autio audio transfer test. Actual host PCM delivery, saved audio quality and long recordings remain to be tested on the Binize. Existing phone/Bluetooth capture remains selected for the user.


## Host session registration recovery (0.9)

November 6: a user reported the missing-car-session error from the dedicated red Record screen. Host registration previously happened only during screen creation and was cleared by matching CarContext identity during session destruction. Version 0.9 registers the active session again on screen start and immediately before car recording commands, and tracks the owning Session so an old session cannot detach a replacement sharing its context. The original car-side lifecycle sequence was not captured, so the precise trigger remains unconfirmed.

The interactive Pixel/DHU regression clears registration after the dedicated screen is fully started, then presses its red Record button. It verifies that registration is restored and the host microphone opens without the original error or phone fallback. Build/lint, host-format/missing-host tests, and standard recording/pause/interruption/save/cancel regression pass. The test note is discarded and the prior capture-path setting is restored. Actual Binize audio transfer/quality remains a car test.
