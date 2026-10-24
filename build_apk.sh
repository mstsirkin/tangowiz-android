#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
sdk_dir="${ANDROID_HOME:-/home/mst/android}"
build_tools="${ANDROID_BUILD_TOOLS:-$sdk_dir/build-tools/35.0.0}"
keystore_path="${ANDROID_KEYSTORE:-/home/mst/.android/debug.keystore}"
./gradlew :app:assembleRelease :watch:assembleRelease :watchface:assembleRelease "-Pandroid.aapt2FromMavenOverride=$build_tools/aapt2" "$@"
mkdir -p dist
for module in app watch watchface; do
 case "$module" in
  app) artifact="TangoWiz-0.5.0" ;;
  watch) artifact="TangoWiz-Watch-0.5.0" ;;
  watchface) artifact="TangoWiz-Face-0.5.0" ;;
 esac
 input_apk="$module/build/outputs/apk/release/$module-release.apk"
 if [[ "$module" == watchface ]]; then
  # WFF packages are resource-only; AGP may emit generated R-class DEX anyway.
  input_apk="dist/$artifact-resources.apk"
  python3 - "$module/build/outputs/apk/release/$module-release.apk" "$input_apk" <<'PYZIP'
import re, sys, zipfile
with zipfile.ZipFile(sys.argv[1]) as source, zipfile.ZipFile(sys.argv[2], 'w') as target:
    for entry in source.infolist():
        if not re.fullmatch(r'classes(?:[0-9]+)?\.dex', entry.filename):
            target.writestr(entry, source.read(entry.filename))
PYZIP
 fi
 "$build_tools/zipalign" -P 16 -f 8 "$input_apk" "dist/$artifact-aligned.apk"
 # A matching signing certificate is required for phone/watch Data Layer communication.
 "$build_tools/apksigner" sign --v4-signing-enabled false --ks "$keystore_path" \
  --ks-key-alias "${ANDROID_KEY_ALIAS:-androiddebugkey}" \
  --ks-pass "${ANDROID_KEY_PASSWORD_SOURCE:-pass:android}" \
  --key-pass "${ANDROID_PRIVATE_KEY_PASSWORD_SOURCE:-pass:android}" \
  --out "dist/$artifact.apk" "dist/$artifact-aligned.apk"
 "$build_tools/apksigner" verify --verbose "dist/$artifact.apk"
 sha256sum "dist/$artifact.apk" > "dist/$artifact.apk.sha256"
done
