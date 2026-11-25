# Android Auto recorder interface investigation

The experiment has now been integrated into the production application module as version 0.4. See [ANDROID-AUTO.md](ANDROID-AUTO.md) for actual recording and playback verification. The separate lab remains a sensor-free layout preview; the findings below describe that initial experiment.

Tested October 28, 2026 with Android Auto 17.9.664044, a Pixel 8a on API 37, and Google's Desktop Head Unit 2.0.

## Result

The separate `car-ui-lab` application successfully renders a Car App Library `PaneTemplate` in the MEDIA category. The simulator accepts a large primary action with a red background and a text label. The tested sequence is:

| State | Primary button | Secondary button |
| --- | --- | --- |
| Ready | Record (red) | None |
| Recording | Pause | Stop & save |
| Paused | Resume | Stop & save |

Record, Pause, Resume, and Stop were clicked in the simulator and changed to the expected layout. All controls fit without scrolling at the simulator's 800 by 480 content resolution. Screenshots are in `verification/ui-lab-ready.png`, `ui-lab-recording.png`, and `ui-lab-paused.png`.

This is a layout experiment: the timer is static, the app has no microphone permission, and Stop does not create a file. Production Autio Notes and its recording service were not changed. The playback controls still visible in the Android Auto system taskbar belong to the existing Autio Notes media session; the lab does not replace that session.

## Reproduce

Build with `./gradlew.bat :car-ui-lab:assembleDebug :car-ui-lab:lintDebug`, install `car-ui-lab/build/outputs/apk/debug/car-ui-lab-debug.apk`, and open the phone launcher activity once. The review copy is `dist/Autio-UI-Lab.apk`.

On this phone, Android Auto developer settings now have Application Mode set to Developer and CAL beta features enabled. Unknown sources was already enabled. Reconnecting the Desktop Head Unit was required before the installed and checked Autio UI Lab entry appeared in the car launcher. The simulator is left open on its ready screen for review.

## Recommended next implementation

Use this template as a dedicated recording screen backed by the existing RecorderService, retaining its AAC/M4A capture, pause/resume, interruption handling, and delivery. Keep saved-note playback on its own screen. Recorder state should drive the buttons, including pauses caused by calls or audio focus changes. Display elapsed recorded time and a simple input indicator within the host's permitted refresh behavior; do not assume that a rapidly animated meter will behave like the native phone view.

Before replacing the prototype, validate real start from the car, pause/resume/commit, permission failures, calls and media interruptions, reconnection, and the Binize's projected host. Also test the Android Auto taskbar/session behavior when recording and playback share one production package. Rendering in a developer host establishes technical feasibility, not Play distribution eligibility or compatibility with every host configuration.

References: [templated media apps](https://developer.android.com/training/cars/apps/media), [car app testing](https://developer.android.com/training/cars/testing), [Desktop Head Unit](https://developer.android.com/training/cars/testing/dhu).
