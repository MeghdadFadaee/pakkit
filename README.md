# Pakkit

**Pocket to Wrist.** A phone-to-Wear OS APK companion with an original luminous parcel identity, electric violet, mint and midnight themes, and explicit watch-side consent.

Pack it. Send it. Wear it. See [the implemented brand system](design/README.md) and the supplied [creative reference](design/brand-concept.png).

**Status: implemented device-validation build, not a production-certified or Play-approved release.** No debugging, root, hidden APIs, automatic installation, APK marketplace or security bypass appears in the consumer flow. Universal no-setup sideloading on Wear OS is not technically guaranteed. See [the feasibility decision](docs/FEASIBILITY.md) before making product claims.

## What is implemented

- Cohesive vector launcher/splash/parcel artwork, themed native components, a persisted phone appearance chooser, finite motion, and a midnight round-watch interface.

- Separate phone (`app`) and round-screen watch (`wear`) applications with the same application ID and distinct version codes.
- Pakkit capability discovery over Google's Wearable Data Layer; nonce-based live capability checks and explicit selection when multiple watches are available.
- Android's file picker, private APK storage, bounded streaming (50 MiB), archive/package inspection, signing-metadata checks, SHA-256 transfer integrity, API/ABI/storage preflight.
- Persistent, retried work and Data Layer asset syncing. No fabricated percentages or success states. One active transfer per phone and one active received APK per watch.
- Watch review and explicit install action, public PackageInstaller sessions with required user action, system confirmation handoff, optional approval notification, and structured installation results.
- Hardware/firmware limitations, unsupported package formats, errors, transfer history, privacy explanation and a Google Play fallback.

## Build

Use Android Studio's Java 21 runtime, Android SDK 36.1, and the included Gradle wrapper. Both apps support Android API 31+ and target API 36.

```sh
./gradlew :app:assembleDebug :wear:assembleDebug :shared:testDebugUnitTest :app:lintDebug :wear:lintDebug
```

Google Maven returned HTTP 404 in the original development environment. An **optional**, explicitly selected mirror is supported; Google's repository remains the default:

```sh
./gradlew :app:assembleDebug :wear:assembleDebug -PpakkitGoogleMavenMirror=https://maven.aliyun.com/repository/google
```

Review mirror provenance and validate dependency checksums through your trusted build environment before production distribution. Dependency versions are pinned; no dynamically versioned libraries are used.

Open both app modules in Android Studio. Install the phone build on the phone and the watch build on a **paired** Wear OS device, using the same signing certificate. Open the watch app first and allow external installs if its firmware exposes that setting. Refresh the phone connection. A reachable watch with the install capability and source approval is required before sending.

APK outputs: `app/build/outputs/apk/debug/app-debug.apk` and `wear/build/outputs/apk/debug/wear-debug.apk`. Release bundles are produced with `:app:bundleRelease :wear:bundleRelease`. Release signing uses optional environment variables described in [RELEASE.md](docs/RELEASE.md); without them bundles remain unsigned.

## Development guide

- `shared`: package inspection, bounded IO, protocol, persistence, workers, and visual primitives.
- `app`: file selection, connection, file review, activity and guidance.
- `wear`: review, unknown-source settings, installation and result receiver.
- [Architecture and security](docs/ARCHITECTURE.md)
- [Feasibility and official sources](docs/FEASIBILITY.md)
- [Release preparation](docs/RELEASE.md)
- [Validation record and physical-device checklist](docs/VALIDATION.md)

Never replace a real capability, transfer result or installer result with a timed simulation. Do not introduce ADB pairing, privileged installation or security-setting automation to make an unsupported watch appear supported. Test-only emulator tooling is distinct from consumer functionality.
