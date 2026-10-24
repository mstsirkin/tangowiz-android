# Wear OS companion

The phone performs recognition with its existing CPU model and native C++ index scanner. The Wear app supplies controls, a Tile, a complication data source and an optional streaming microphone. The separate resource-only Watch Face Format face shows a large orchestra name, the song and a small clock. The face setup follows the working Run and Click project; it is an actual system watch face.

## Install

Install `TangoWiz-0.5.0.apk` on the phone, and both `TangoWiz-Watch-0.5.0.apk` and `TangoWiz-Face-0.5.0.apk` on the watch, for example with WearLoad or ADB. The companion and phone share the package ID and signing certificate required by the Wear OS Data Layer; the face has a separate package. Keep the phone's installed model/catalog. The watch needs neither of those large resources.

Add the TangoWiz Tile from the watch's Tile chooser. Select TangoWiz face in the normal face picker. Its large LONG_TEXT slot defaults to the TangoWiz orchestra/song provider; if the system requests a provider or permission, select TangoWiz orchestra and song. Companion minimum API 30; face minimum API 33/Wear OS 4. The face uses WFF 1 for broad compatibility.

## Use

- The phone's watch icon selects watch audio and starts listening; tap again to return to the phone microphone.
- The Tile shows the current result, start/pause and microphone controls, plus a history button opening the scrollable history view.
- Open the companion for recording/debug toggles and the update interval. The phone has the same recording/debug controls and a Watch updates menu.
- The first microphone use requests permission on the watch. Capture runs in a foreground service with a visible notification and Stop action, while the normal watch face remains available.
- If Android blocks opening either app remotely, a tap-to-open notification provides a visible recovery path. Startup is acknowledged separately from recognition readiness; a delivered command does not mean the microphone is running.
- Saved audio and debugging artifacts remain on the phone and use its existing export/cleanup features. Watch history displays the latest 40 grouped entries from the phone's bounded history view. History percentages retain the original scale; live result percentages follow the phone's doubled display scale and are not calibrated probabilities.

The result interval defaults to 5 seconds and is configurable from 2 to 60 seconds. It controls result communication, not fingerprint computation or audio capture. The phone pushes current state by message and persists a latest DataItem for reconnects. The watch requests a local complication refresh for changed text/staleness, and renews unchanged live text after 5 minutes. Face data expires after 10 minutes without a renewal. Wear OS may throttle complication refreshes; exact real-watch latency and battery cost need measurement. The interactive companion/Tile shows connection and stale-result status separately.

## Transport and recovery

Controls use explicit desired state, unique request IDs, acknowledgements and bounded retries. The phone owns setting revisions; stale commands are rejected instead of overwriting newer settings. Duplicate accepted commands return the cached acknowledgement. Network sending is separate from the phone's control queue, and state pushes are coalesced.

Watch microphone audio uses a nearby-only ChannelClient stream, fixed versioned AudioWire frames, stream UUIDs, sequence/sample positions and 8 kHz mono PCM16. Hardware 8 kHz capture is tried first; supported 44.1/48 kHz input is filtered/resampled when needed. The stream has bounded queues and ends on malformed frames, gaps, disconnection or excessive backlog. No old audio is replayed and there is no silent switch to the phone microphone. A new stream UUID starts a fresh recognition session while retaining the loaded model/index. AudioWire layout and checks are documented in `wear-common/PROTOCOL.txt`.

## Validation scope

The 0.5.0 validation completed with 10 shared framing tests, five Wear emulator checks, two WFF provider-policy regression tests, and the phone synthetic watch-input recognition/reconnect test passing. The face was selected and rendered by the actual Wear OS watch-face runtime. Phone recognition identified the known Tanturi recording from ten seconds of PCM16 input; reconnect produced two recorded sessions while loading model and index only once.

The shared framing tests exercise roundtrip/endianness, truncation, malformed formats/lengths, sequence and position errors, and fragmented reads. Phone instrumentation exercises settings atomicity/retries and injects framed event PCM through the watch-input capture path to test real recognition, recording and reconnect isolation. This synthetic input test is not proof of a paired Bluetooth/Data Layer session. Wear instrumentation covers resampling, stale snapshot handling and the round companion UI; the face XML is checked with Google's official WFF validator. Physical Xiaomi Watch 2 screen-off capture, end-to-end latency, remote launch behavior and battery life still require device trials.


## Paired emulator validation

The 0.5.0 phone and watch runtime payloads were tested through the real Google Play services Data Layer on two paired emulators (phone API 35, Wear API 34). A known music fixture sent by ChannelClient from the watch was recognized as Ricardo Tanturi, and the pushed orchestra/title rendered on the actual WFF face. Explicit command retries and stale revisions passed. The production AudioRecord microphone service continued while the watch was Dozing; the phone saved 19.5 seconds of mono 8 kHz audio during a 20-second test. Both nodes reported a deliberate disconnect and reconnect, then the known audio matched again with a new stream UUID. The phone loaded its model/index only once across these tests.

The fixture test injects music on the watch; the microphone-service test uses the emulator's synthetic/silent hardware input. These establish transport/control/face functionality, not physical acoustic quality, Bluetooth range, battery life or phone/watch latency. Reproduction instructions are in [watch/TESTING.md](watch/TESTING.md). No recognition implementation change or new APK is required for these results.
