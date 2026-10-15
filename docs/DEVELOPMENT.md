# Development and testing

## Environment

- Android Studio with JDK 21 (set JAVA_HOME for shell builds).
- SDK platform 36.1, current platform-tools and Android Emulator 36.5+ for shared virtual Wi-Fi.
- A round Wear OS Emulator (API 31+); an Android Emulator with Chrome is used as a browser device.
- Python 3 for the test runner. Optional Node 22+ and npm for browser tests/formatting.

`local.properties`, IDE files, Gradle/Kotlin caches, node_modules, generated APKs/reports and signing keys are ignored. Android Studio regenerates its local project model. No generated reports or screenshots are committed.

## Build and native tests

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lint :app:bundleRelease
adb devices
python 3 scripts/test-emulators.py --watch emulator-5554 --skip-build
python 3 scripts/test-emulators.py --watch emulator-5554 --small --skip-build
```

Use actual serials from `adb devices`. The runner refuses serials that do not start with `emulator-` and requires the watch feature. It never selects a physical device. It connects the watch to the virtual AndroidWifi access point, installs only the app/test APKs and saves results under `app/build/reports/validation`. The small-screen run temporarily uses 192 dp and 1.3x font scaling, restoring the previous size, density and font setting in a finally block.

For install tests, first send an APK to Pakkit on the emulator and use **Allow installs** on its review screen, or open Android Settings → Apps → Special app access → Install unknown apps → Pakkit. Approve it through the normal system UI. No shell permission grant is needed. Tests mark missing source approval as an assumption; the runner treats skipped tests as incomplete validation. Native tests clean up staged files, pending system sessions, notifications and activities. The official-installer test stops at required system consent and abandons its test session afterward.

The optional Google Maven mirror property is documented in README.md. If needed, build with that property and pass --skip-build to the runner; its default build uses standard repositories.

## Browser UI tests

```sh
npm ci --prefix web-tests
npm exec --prefix web-tests -- playwright install chromium
npm test --prefix web-tests
npm run format:check --prefix web-tests
```

These tests serve the actual bundled assets with a local API fixture and exercise 320/390/768/1440 px widths, both themes, manual session-key entry, file rejection, upload errors/retry, system-status transitions and cancellation. This isolates browser interactions; it does not stand in for watch networking. An existing Chromium executable can be selected with PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH.

Playwright and Prettier are development dependencies only, locked in web-tests/package-lock.json. There is no Node toolchain in the Android runtime or asset build. `npm run format --prefix web-tests` formats web assets and browser tests; use standard Kotlin formatting for native source.

## Real browser-to-watch emulator transfer

Complete Chrome’s first-run setup in the Android Emulator, leave Chrome open, and run:

```sh
python 3 scripts/test-emulators.py --watch emulator-5554 --browser emulator-5556 --skip-build
```

Both emulators must use the shared virtual Wi-Fi network. Android Emulator 36.5+ [supports direct communication between emulator instances](https://developer.android.com/studio/run/emulator-networking-interconnect). The runner starts Pakkit through its visible receiving action, reads the private link from the actual watch UI, connects Playwright to Chrome on the Android Emulator through a local ADB forward, and submits the signed QA fixture. Chrome sends the real XHR directly to the watch’s private Wi-Fi address. The test asserts watch review, then dismisses the fixture and ends the session. Debug forwarding is test infrastructure; no debug endpoint or test mode ships in Pakkit. The forward is removed afterward and the watch app is stopped even on failure.

An uninitialized Chrome instance or isolated/older emulator network requires environment setup; the runner does not silently substitute mock transport. Keep private session URLs out of committed logs. Generated screenshots and API summaries live under app/build/reports/validation.

## Fixture and release inspection

`qa/fixture` builds a minimal signed test app. StageFixtureApk copies it into debug instrumentation assets only. The consumer APK/AAB must contain no fixture APK or test classes. Inspect release resources, merged permissions and runtime dependencies when changing Gradle configuration:

```sh
./gradlew :app:dependencies --configuration releaseRuntimeClasspath
./gradlew :app:bundleRelease
```

Hardware testing is a final compatibility/power gate, documented in VALIDATION.md, rather than a routine development requirement.
