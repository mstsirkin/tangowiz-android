#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
sdk_dir="${ANDROID_HOME:-/home/mst/android}"
build_tools="${ANDROID_BUILD_TOOLS:-$sdk_dir/build-tools/35.0.0}"
keystore_path="${ANDROID_KEYSTORE:-/home/mst/.android/debug.keystore}"
./gradlew :app:assembleRelease "-Pandroid.aapt2FromMavenOverride=$build_tools/aapt2" "$@"
mkdir -p dist
"$build_tools/zipalign" -P 16 -f 8 app/build/outputs/apk/release/app-release.apk dist/TangoWiz-0.1.1-aligned.apk
# Override these environment variables when using a dedicated release key.
"$build_tools/apksigner" sign --v4-signing-enabled false --ks "$keystore_path" \
 --ks-key-alias "${ANDROID_KEY_ALIAS:-androiddebugkey}" \
 --ks-pass "${ANDROID_KEY_PASSWORD_SOURCE:-pass:android}" \
 --key-pass "${ANDROID_PRIVATE_KEY_PASSWORD_SOURCE:-pass:android}" \
 --out dist/TangoWiz-0.1.1.apk dist/TangoWiz-0.1.1-aligned.apk
"$build_tools/apksigner" verify --verbose dist/TangoWiz-0.1.1.apk
sha256sum dist/TangoWiz-0.1.1.apk > dist/TangoWiz-0.1.1.apk.sha256
