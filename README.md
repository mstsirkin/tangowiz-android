# TangoWiz Android

[Download the APK](https://github.com/mstsirkin/tangowiz-android/releases/latest)
and install it on an Android phone. Version 0.1.1 is a 1.35 GB self-contained
APK with the model and reference index bundled; no account or server connection
is needed for recognition. Allow microphone and notification permissions when
starting identification.

Self-contained CPU recognition app. Kotlin platform UI, microphone foreground service,
TFLite pretrained NMFP inference, native mel preprocessing, memory-mapped full-catalog
PQ8 index and ARM NEON scanner. No server needed. The bundled catalog snapshot has
46,493 references and 16,580,501 one-second fingerprint windows. Ongoing later imports
are not included until the next published index export.

Build with Python 3.11+, Java17, AndroidSDK35, NDK27.0.12077973,
CMake3.22.1 and the bundled Gradle8.10.2 wrapper. Download the v0.1.1 APK
from Releases and restore its verified large assets first:

```
python3 tools/extract_apk_assets.py /path/to/TangoWiz-0.1.1.apk
export ANDROID_HOME=/path/to/android-sdk
./build_apk.sh
```

The generated model/index files and their metadata are inside the release APK,
rather than Git
objects. The extraction helper checks the APK and both asset SHA256 values before
replacing local build assets. It also restores the generated metadata through a
fixed allowlist; it does not extract unrelated archive entries.
This repository contains the Android application, its native recognition engine,
instrumentation tests, build files and dependency notices. Collection, catalog
generation and model-training/export pipelines are maintained separately.
Gradle downloads the pinned TensorFlow Lite dependencies; compiled dependency AARs
are not committed.
The release
build is post-aligned and signed with the local Android development key; retain it to install updates. The final signing key path is explicit in build_apk.sh so it does not depend on Gradle's potentially different user.home debug key. Do not distribute the private key.
No credentials, source audio collection or private account files are bundled.

Main controls: play/pause identification; circle toggles microphone audio retention;
bug toggles timing logs; share exports local diagnostics. Fingerprint logs are always
retained while recognition is active. Android permission and active microphone
notification remain visible. Recordings and diagnostics are local until manual share.

Percentages represent raw model similarity scores expressed as percentages, **not
calibrated probabilities**. Their green color indicates increasing score. A strong
history marker uses an experimental score threshold, not a guaranteed identification.
Current real-time service processes the baseline speed stream continuously. Native
engine supports distinct contiguous speed streams, but thirteen simultaneous streams
have not been claimed real-time on the phone. PQ reconstructed reranking fits the
bundled index; it is not bit-identical to the server's float-vector reranker.

Source distribution includes GPL-3.0 text for the neural fingerprinting adaptation,
MIT scanner notices, TensorFlow Lite AAR licenses and build configuration.
App source is provided under GPL-3.0; separate dependency
and data licenses remain as documented in their notices.

Version 0.1.1 keeps the primary model/index resident between identification sessions. Use the ongoing notification to Pause/Resume, Open, or Exit. Pause stops capture, audio saving and inference, releases the wake lock, and clears session evidence. Exit releases the model/index and closes the app task. Android can reclaim a service under memory pressure. Notification permission is required so these controls remain available.
