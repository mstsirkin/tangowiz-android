# Android app release validation

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
