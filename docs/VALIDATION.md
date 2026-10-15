# Validation record

Date: October 15, 2026. This record separates what ran from what still needs paired physical devices and Play review.

## Brand integration update — October 15, 2026

The official Pakkit identity is now implemented in both consumer apps. See [the brand guide](../design/README.md) and [current previews](screenshots/brand/). Earlier screenshots outside that subfolder document the previous visual direction. Brand test results are saved in [validation/brand](validation/brand/).

- Custom open-parcel mark, adaptive/monochrome launcher assets, unified midnight splash and branded approval notification.
- Lavender light and midnight dark phone themes with a native custom appearance dialog, persisted selection and actual chooser/recreation testing.
- New phone navigation, connection illustration, file review, truthful transfer stages, history, empty states and guide.
- Midnight watch home, review, receiving, consent, success and error components, with proportional round-screen margins, a bezel scroll indicator and native clock.
- 9 shared JVM tests pass, including 3 new contrast checks; 5 phone instrumentation tests and 1 strengthened watch review/target-size test pass. The watch test also passed at an emulated 192-DP round size with 1.3× text, checking that install/dismiss actions remain fully reachable and at least 48 DP tall. This is an additional tested configuration, not physical-device certification.
- Both debug builds and optimized unsigned release bundles build; Android lint reports zero errors. Existing informational dependency/SDK/style warnings remain.
- A local signed QA fixture was updated through the redesigned watch review, genuine system confirmation and success screen.
- Android’s real document picker was used to select the signed local QA APK and verify the phone review screen.
- Package transfer, source approval, explicit install/session consent and policy boundaries are preserved. No capability or fake progress state was added from the creative reference.

## Completed

| Check | Result |
| --- | --- |
| Phone debug APK | Built successfully |
| Wear OS debug APK | Built successfully |
| Optimized phone release bundle | Built successfully; unsigned without production signing variables |
| Optimized watch release bundle | Built successfully; unsigned without production signing variables |
| Android lint, phone | 0 errors; informational SDK/dependency/style warnings remain |
| Android lint, watch | 0 errors; informational SDK/style warnings remain |
| Bounded copy, known SHA-256, empty/oversize rejection, identifier safety, expiry, ABI matching | 6 JVM tests passed |
| Phone device tests: navigation and recreation, signed complete APK inspection, self-update rejection, invalid archive/path rejection | 4 passed on Pixel 10 Pro emulator, API 37 |
| Watch review test: signed fixture can be reviewed without creating any install session | 1 passed on round Wear OS 7 emulator, API 37, 454×454 |
| Visual inspection | Phone home and round watch review/controls inspected; explicit illustration height corrected |
| System source approval | Opened via public settings intent; normal system warning and approval completed on watch emulator |
| Watch PackageInstaller path | Explicit Install → pending-user-action callback → Continue → genuine system confirmation → system Install → STATUS_SUCCESS and Pakkit “Installed on your watch” → Open app launched the fixture |
| Installed-file cleanup | Confirmed the successful transfer’s private APK was deleted after installation |
| Merged release manifests | Installation and notification permissions watch-only; no broad storage/visibility/Bluetooth/location permission; unused foreground service and its permission removed |
| Release contents | QA APK absent from consumer bundles; no production signature created in this workspace |
| Git whitespace check | Passed |

The manual install used `qaFixture`, a small signed local test application. Instrumentation staged it into the watch's private review state to isolate the installation boundary. **This was not a test of Data Layer delivery.** The fixture is packaged only into Android test assets, never consumer APKs or bundles. The UI uses the exact same file revalidation, PackageInstaller session and result receiver as the consumer install flow. Developer tooling deployed Pakkit's debug builds; the fixture itself was installed through ordinary watch UI and system consent, without an ADB package install, app-op grant, root or hidden API.

Raw device test summaries: [phone](validation/phone-tests.txt), [watch](validation/watch-tests.txt). Screenshots are in [screenshots](screenshots/): phone home, watch launch/home/controls, real system approval and successful installation. Test fixture screens are labelled accordingly and are not store marketing screenshots.

## Not yet verified — release blockers

1. **Paired Data Layer integration:** the available phone/watch emulators were not configured as a paired Wear network, and discovery on the phone returned an availability/connection failure. Capability nonce exchange, real asset delivery, background receive, acknowledgement routing, delayed retries and cloud routing are implemented but remain unverified end-to-end.
2. **Galaxy Watch 7:** test exact One UI Watch / Wear OS / Android build and region. Confirm source settings, resolver behavior, consent UI, blocked cases, normal launch and developer-verification enforcement. No compatibility claim should be made before this.
3. **Interruptions and limits:** Bluetooth off/on, phone process killed, watch process killed, reboot during sync, missed status acknowledgement, system approval dismissed, user denied source/notification permission, second phone or second watch, no space, low battery and 50 MiB boundary. Test expiry while a job or install session is active. Background workers are OS-scheduled and may run later than their target time.
4. **Package coverage:** real compatible standalone APK, wrong ABI, newer minimum SDK, wrong update signature, downgrade, invalid certificate, isolated/config split, base requiring splits, corrupt resource table and restricted packages. Preflight is intentionally not a full certification or malware scan.
5. **Accessibility and layout:** TalkBack, large fonts, touch exploration, reduced animation, rotary input on hardware, 192/208/227 dp round displays and an actual tablet/phone rotation. Semantic controls and scrolling are implemented; full accessibility certification has not been performed.
6. **Performance:** battery and transfer latency on real Bluetooth/cloud links, background job timing and memory on low-RAM watches. The 50 MiB cap, bounded buffers and lack of continuous watch animation are implementation constraints, not measured performance claims.
7. **Release identity and policy:** production signing identity shared across form factors, verified dependency provenance, public privacy URL/support contact, listing assets, completed permission/Data safety declarations, internal/closed testing and Wear OS review. Google Play approval is not guaranteed.

## Intentional v1 boundaries

Single complete APK, 50 MiB maximum, one in-flight job per phone and one reviewable APK per watch, expiry 24 hours from selection, no percentage estimate, no direct third-party downloads or catalog. Waiting transfers do not currently offer a cross-device cancel action; dismiss on the watch when delivered, or let the job expire. English is the first locale. Background transfers and approval recovery require device validation before public rollout.

## Reproduce

```sh
./gradlew :app:assembleDebug :wear:assembleDebug :shared:testDebugUnitTest :app:lintDebug :wear:lintDebug :app:bundleRelease :wear:bundleRelease
./gradlew :app:assembleDebugAndroidTest :wear:assembleDebugAndroidTest
```

In environments where Google Maven returns HTTP 404, append `-PpakkitGoogleMavenMirror=https://maven.aliyun.com/repository/google` after reviewing the mirror's suitability. Run each instrumentation APK against its matching phone/watch app, rather than deploying both variants to the same device: they intentionally share an application ID. Standard Android Studio device-test tooling can run the tests. The watch test leaves its fixture ready for manual consent checks.
