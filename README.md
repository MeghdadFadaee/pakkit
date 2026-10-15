# Pakkit — Pocket to Wrist.

One standalone Wear OS app. A browser on your Wi-Fi. No companion app.

Pakkit starts a ten-minute receiving session on the watch, shows a private URL and QR code, and serves its own responsive web interface. Select a complete APK in the browser, send it directly to the watch, review it there, and approve the official Android installer. No ADB, root or developer options are used by the application. Play publication remains subject to permission declaration and review.

## Use

1. Install Pakkit on a supported Wear OS watch. The current build is a development APK; a Play listing has not been published.
2. Allow Pakkit to request installations using the watch’s system permission screen. If firmware does not expose this permission, use Google Play instead.
3. Connect the watch and browser device to the same trusted Wi-Fi, then tap **Start receiving**.
4. Scan the QR code or enter the full private link. If you enter just the address, enter the session key shown after `#` on the watch.
5. Select one complete `.apk` up to 50 MiB. Transfer progress is shown on both devices. Review the package and approve installation on the watch.
6. End the session or let it expire. A fresh session has a new address/key. Turn off development wireless debugging after testing; it is unrelated to Pakkit’s receiving session.

Local HTTP is **not encrypted**. The private link restricts access but does not protect against an attacker on the network. Use trusted Wi-Fi, keep the link private and install only apps you trust. Guest/client-isolated networks, VPNs and browser local-network restrictions can prevent access. Internet access and a paired phone are not needed for the transfer itself.

## Build

Java 21, Android SDK 36.1, min SDK 31, target SDK 36. Application ID `meghdad.pakkit`, version `0.2.0` / `100003`.

```sh
./gradlew :wear:assembleDebug :shared:testDebugUnitTest :wear:lint :wear:bundleRelease
```

Optional mirror when Google Maven is unavailable: `-PpakkitGoogleMavenMirror=https://maven.aliyun.com/repository/google`. Review dependency provenance in your release environment.

- Debug APK: `wear/build/outputs/apk/debug/wear-debug.apk`
- Release AAB: `wear/build/outputs/bundle/release/wear-release.aab`
- Release signing: see [release preparation](docs/RELEASE.md). No signing secrets are included.

## Structure

- `wear`: foreground Wi-Fi session, local HTTP server, bundled web assets, round watch UI, source approval and PackageInstaller consent.
- `shared`: bounded HTTP parsing, package inspection, integrity, storage, native design primitives and unit tests.
- `qa/fixture`: harmless signed test app, staged only into instrumentation assets; never included in the consumer app.

The former phone module and Data Layer/WorkManager transport have been removed. Historical companion screenshots are retained as design history; current screenshots are in `docs/screenshots/standalone`.

[Feasibility and platform evidence](docs/FEASIBILITY.md) · [Architecture/security](docs/ARCHITECTURE.md) · [Validation](docs/VALIDATION.md) · [Brand](design/README.md) · [Privacy](docs/PRIVACY.md)
