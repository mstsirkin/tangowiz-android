# Android app release validation

Commit all APK source and build changes before starting an APK build. Build from
a clean checkout of the intended commit and record its revision with the APK.

Run matching experiments in the native host harness first. Integrate an
experiment into the application only after it meets its recognition acceptance
criteria on the relevant fixtures.

Always test changed Android apps in an emulator before distributing APKs for
physical-phone testing. Compilation and JVM tests alone are insufficient.
Exercise the changed recognition paths and fail validation on worker exceptions
or zero successful fallback batches; feed/drain checks alone do not establish
Adaptive functionality. Add a JVM regression for reproducible logic failures,
demonstrate it fails before fixing, then rerun it unchanged after the fix.
Emulator timing is not physical-phone performance.

Give physical-phone test apps and runners visibly distinct names. This phone
allows ADB updates but requires the user to install new packages manually once;
do not ask to weaken its updates-only installation policy.

Every release must pass `:app:validateRecognitionRelease`: the full release JVM
suite and the native recent-normal recovery corpus. Phone, watch and watchface
release packaging depend on this gate, including direct Gradle invocations.
The gate must rerun against current sources; do not substitute an old receipt or
silently skip missing private fixtures. It checks earlier recovery, preservation
of previously strong matches, and both unmatched controls. Fixture hashes and
the pinned historical catalog are verified. Keep phone embeddings and local
fixture configuration outside the public repository. See README release
validation setup. This native regression does not replace emulator validation
before distributing changed Android APKs.
