# APK signing

Phone and watch updates must use the existing TangoWiz signing certificate. Its SHA-256 fingerprint is `7b5a096154dc754da64bf5077d07c11c158819987157b39424fde5928078d698`. Both companion apps must share that certificate for Wear Data Layer communication.

Use `build_apk.sh` with the established private keystore and verify the result with `apksigner verify --print-certs` before installation. A plain Gradle debug build may use a different key. If Android rejects an update because its signature differs, sign it with the established key; do not uninstall the existing app, which can remove its data. Keep private keys and credentials outside the repository.
