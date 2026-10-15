# Pakkit

**Pack it. Send it. Wear it.** Give your next app a place on your wrist.

Send an APK from your browser to your Wear OS watch, then approve installation on your watch.

## Use

1. Open Pakkit on a supported watch.
2. Connect the watch and browser device to the same trusted Wi-Fi.
3. Tap **Start receiving** and scan the QR code. For manual entry, tap **View link** on the watch and open the complete link in your browser.
4. Select one complete `.apk` up to **50 MiB**. Watch transfer progress in the browser and on the watch.
5. Review the app on your watch. If prompted, tap **Allow installs** to grant source permission, then tap **Install** and approve it.
6. End the receiving session when finished. Sessions automatically end after ten minutes or on Wi-Fi loss.

The local connection uses **unencrypted HTTP**. Use trusted Wi-Fi and keep the private link secret. Package inspection checks format and compatibility; it does not certify app safety. Source approval, firmware security policy and Android’s installer determine whether installation is available. Split packages and app bundles are unsupported. See [platform constraints](docs/PLATFORM.md).

The current artifacts are development builds. Public distribution requires the checks in [release preparation](docs/RELEASE.md).

## Project

| Path         | Responsibility                                                                                                     |
| ------------ | ------------------------------------------------------------------------------------------------------------------ |
| `app`        | The Wear OS application: native watch UI, bundled web interface, private storage, Wi-Fi receiving and installation |
| `qa/fixture` | Small signed test APK, included only in instrumentation assets                                                     |
| `web-tests`  | Optional browser test tooling; nothing from this directory ships in the app                                        |
| `scripts`    | Emulator-only test runner and ADB helpers                                                                          |
| `docs`       | Architecture, design, development, platform, privacy and release guidance                                          |

There is one production module and one test-fixture module. Production has one explicit library dependency: ZXing Core for local QR generation. Kotlin’s standard library is supplied by the Android Gradle plugin.

## Build

Use **JDK 21**, Android SDK **36.1**, Build Tools installed by the SDK, and the Gradle wrapper. Open the project in Android Studio and run the `app` configuration on a Wear OS Emulator. Minimum Android API is 31; target is 36.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lint :app:bundleRelease
```

- APK: `app/build/outputs/apk/debug/app-debug.apk`
- AAB: `app/build/outputs/bundle/release/app-release.aab`
- Signing: set all four documented `PAKKIT_*` variables or leave them all unset for unsigned release output.

If Google Maven is inaccessible in your environment, the optional build property `-PpakkitGoogleMavenMirror=https://maven.aliyun.com/repository/google` selects a mirror. Official Google Maven is the default; use a trusted dependency source for release builds.

## Test

Routine development uses emulators. No physical watch is required.

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest
python3 scripts/test-emulators.py --watch emulator-5554 --skip-build
python3 scripts/test-emulators.py --watch emulator-5554 --small --skip-build
```

For browser tests and real Android-Emulator-to-Wear-Emulator uploads, see [development and testing](docs/DEVELOPMENT.md). Results and screenshots are generated under `app/build/reports/validation`, not checked into source control.

[Architecture](docs/ARCHITECTURE.md) · [Design](docs/DESIGN.md) · [Platform](docs/PLATFORM.md) · [Privacy](docs/PRIVACY.md) · [Release](docs/RELEASE.md) · [Validation](docs/VALIDATION.md)
