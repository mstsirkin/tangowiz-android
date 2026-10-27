# Paired phone/watch emulator tests

These tests use the real Google Play services Wear OS Data Layer, not a socket substitute or a mocked NodeClient. They do not establish physical microphone quality, Bluetooth radio range, battery life or device latency. Emulator timing is not an app performance benchmark.

## Pairing

Google documents the normal Android Studio pairing flow at https://developer.android.com/training/wearables/get-started/connect-phone . Install the Google Pixel Watch companion on the phone emulator and complete its emulator setup/consent flow. No personal Google account is stored in this application's test code.

For headless setup, Android Studio's open-source WearPairingManager uses this bridge (substitute your serials and a free host port):

```sh
adb -s emulator-5580 forward tcp:5602 tcp:5601
adb -s emulator-5582 reverse tcp:5601 tcp:5602
adb -s emulator-5582 shell am broadcast -a com.google.android.gms.wearable.EMULATOR --es operation get-pairing-status
```

Read the watch's `Local:[NODE_ID]`, then start the companion's `.EmulatorActivity` on the phone with `--es emulator-name TangoWizWearTest --es emulator-id NODE_ID`. Grant its Bluetooth connection permission and complete the visible setup. Refresh the watch connection with the same broadcast action and `--es operation refresh-emulator-connection`.

Both devices must report the other node as `Peer:[NODE_ID,true,true]`. Install the phone and watch TangoWiz packages with matching application IDs and signing certificates. The phone must already have its model and catalog. Keep the phone app visible for the initial test, and begin with listening paused: the test refuses to interrupt an active session.

## Device-side harness

```sh
./gradlew :watch:assembleDebugAndroidTest \
  -PpairedWearRunner=org.tangowiz.wear.PairedWearValidation
```

Sign `watch/build/outputs/apk/androidTest/debug/watch-debug-androidTest.apk` with the certificate of the installed watch companion, and install it on the watch. The default runner remains `WatchValidation` when the Gradle property is omitted.

The known-audio mode requires our private ten-second, float32 little-endian, mono 8 kHz Tanturi fixture at `/data/local/tmp/event.pcm` on the watch and its reference `palazzo-f57cf688973fbd86315e1d87` in the phone catalog. The recording is not bundled with source. Keep microphone permission ungranted for this mode so the normal microphone stream does not compete with the injected test stream.

```sh
adb -s emulator-5582 shell am instrument -w \
  org.tangowiz.app.test/org.tangowiz.wear.PairedWearValidation
```

It verifies nearby discovery, real command ACKs, identical-request retry, stale revision rejection, versioned PCM over ChannelClient, the expected phone recognition, and the production watch cache receiving the result. Input is deliberately paced slowly: timing is not a recognition-speed measurement. Stage `recognized_hold_for_face` allows the host to capture the actual face. It restores the phone settings and pauses capture afterward. Results are written to the watch app's `files/paired-validation.json`.

To test the production AudioRecord/foreground-service path, grant microphone and notification permissions on the watch, clear any microphone AppOps override, then run:

```sh
adb -s emulator-5582 shell am instrument -w -e mode microphone \
  org.tangowiz.app.test/org.tangowiz.wear.PairedWearValidation
```

If a remote Activity launch is blocked, open the watch companion as a user action with `requestCapture=true`. Once stage `microphone_capturing` appears, send `KEYCODE_SLEEP` (223). The harness verifies the capture remains active for 20 seconds. Separately verify the phone's saved WAV duration/sample rate and that watch power state was Dozing; an active service flag alone is insufficient evidence of received audio. The emulator's microphone supplies synthetic/silent hardware input, not a real acoustic test.

## Recorded 0.5.0 results

Two paired emulators (phone API 35, Wear API 34) passed both modes. The known reference was recognized and its pushed orchestra/song appeared on the actual WFF face. Duplicate/stale control checks passed. A 19.5-second mono 8 kHz WAV reached the phone during the 20-second microphone test while the watch was Dozing. Physical Watch 2 microphone quality, latency and battery remain unmeasured.

The transport was deliberately disconnected (both peers reported connected=false) and restored (both reported connected=true). Repeating the known-audio test with a new stream UUID passed. Phone lifecycle counters showed one model load and one index load across both fixture passes and the microphone-service test. This reconnect check interrupted the idle transport, not an actively captured physical microphone.

## Full-screen ambient validation (0.5.1)

Run the same paired runner with `-e mode ambient_local`. This test uses explicitly synthetic local labels and real AndroidX/OS ambient callbacks. At `waiting_for_sleep`, send KEYCODE_SLEEP (223); at `waiting_for_wake`, send KEYCODE_WAKEUP (224). It checks Ongoing Activity metadata, hidden interactive controls, deferred ambient rendering, 75 seconds of retention beyond the emulator's 60-second return timeout, latest cached text on wake, and Close display removing its notification without pausing identification. It saves/restores fixture state and refuses to interrupt an enabled display session. Results are in `files/ambient-validation.json`.

This passed on Wear OS 5 (API 34), followed by a real paired known-audio recognition test against the unchanged 0.5.0 phone. Ricardo Tanturi and the song rendered in the actual full-screen display. The emulator's ambient service had to be restarted after a system-service crash during boot. Its wrist-tilt sensor HAL also crashed, so tilt-to-wake was disabled on the test device and explicit sleep/wake keys were used. No APK workaround was added. These results do not validate physical wrist gestures or older Wear OS retention behavior.

With display mode enabled, microphone mode also checks that Close display leaves actual capture/listening active. Separately verify the phone receives a WAV while Dozing.

The paired 0.5.1 microphone/display check passed: capture remained active for 20 seconds while Dozing, Close display left microphone capture and identification running, and the phone received 21.5 seconds of mono 8 kHz WAV audio.

## Manual reset (0.5.5)

The phone runner supports `-e mode manual_reset`. This uses the real recognition engine and a synthetic watch-input queue with the private Tanturi fixture; it does not exercise physical Wear transport. It checks the visible phone reset action, exactly 16,000 retained 8 kHz samples, unchanged saved history, one model/index load across resets, continued recognition and an identical watch command retry applied once. Watch orchestra tap handlers compile into both controls and ambient display; the new physical-watch gesture/transport path still needs a device trial.

Recorded result: phone 0.5.5 integration passed with 16,000 samples retained, the actual phone reset button exercised, history preserved, one model/index load, duplicate watch-command retry applied once, and Ricardo Tanturi recognized after reset. Buffer unit tests (three cases) and both release builds passed. This is synthetic watch-input/command integration, not a new paired physical-watch transport test.
