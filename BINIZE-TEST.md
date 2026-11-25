# Binize native installation test

Hardware: Binize 9-inch Android head unit for Toyota Prius 2010–2015, ASIN B0FJ2LKBW3. Its listing advertises Android 13 and downloading apps over Wi-Fi. Actual installed firmware and microphone access are not yet verified.

## Install

1. Copy `dist/Autio-Notes-Binize-test.apk` onto a USB drive.
2. With the car parked, insert the drive into the head unit's USB port and open the APK from its file manager.
3. If prompted, allow that file manager to install this APK. Open **Autio Notes** and grant microphone and notification access when requested.
4. Run the app from the head unit's own Android launcher. It is not an Android Auto dashboard app.

If the APK is refused, record the exact error and the Android version shown under Settings → About. Do not assume the advertised Android version matches the firmware.

## First checks

1. Record 15–30 seconds while speaking normally in the driver's seat. Confirm that the meter moves and note the reported input source. Android's device category does not by itself identify whether an OEM, included external, or internal microphone is wired to that input.
2. Pause for ten seconds, resume, then Stop & save. Play the note and verify that the paused interval is omitted and the voice is intelligible. The file should be M4A.
3. Repeat with the phone paired normally. Have an incoming call interrupt recording. Confirm the app pauses and stays paused after the call ends. Then resume explicitly and stop. Head-unit Bluetooth firmware may route calls outside Android's standard audio-focus mechanisms, so the Pixel's successful interruption test does not establish this behavior on the Binize.
4. Try the navigation/music combination normally used in the car and verify the expected interruption behavior. Keep testing while parked.
5. Record a longer note and check playback before relying on the setup for an hour-long note.

## Delivery

Installation on the Binize creates a separate app and recording library. It does not inherit the Pixel's Syncthing setup or folder permission. Local recording and playback work before sync is configured.

After the microphone test succeeds, configure a Syncthing client on the head unit, pair it with the workstation, and choose its shared recording folder under Autio → Saved notes → Note delivery. Internet access could come from Wi-Fi or the phone's hotspot; this has not been tested on the unit.

Sources: [exact ASIN listing](https://www.amazon.com/dp/B0FJ2LKBW3), [Binize Prius head-unit product page](https://www.binize.com/products/9-inch-android-car-stereo-compatible-for-2010-2015-toyota-prius-with-wireless-carplay-android-auto).
