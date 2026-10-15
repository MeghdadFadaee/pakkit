# Standalone validation — October 15, 2026

Version 0.2.0 / 100003. The consumer app is standalone; old companion test results in `validation/brand` and older screenshots are historical evidence only.

## Automated coverage

- 13 shared JVM tests: six bounded-stream/expiry/ABI rules, three semantic contrast checks, four HTTP parsing/authentication/size tests. Results: `shared/build/test-results/testDebugUnitTest`.
- Two watch instrumentation tests on the Wear OS API37 round emulator: actual Wi-Fi socket GET/status/raw APK POST; missing-token and cross-origin rejection; invalid APK deletion; duplicate upload refusal; verified fixture metadata; no automatic PackageInstaller session; listener closes and private URL clears at explicit stop. The separate round-review test verifies 48dp actions are reachable and receipt does not create an installation session.
- The emulator needed its virtual Wi-Fi enabled and associated with AndroidWifi. Initial failures while Wi-Fi was disabled were environmental. A repeated run exposed an HTTP test-client bug: reading until EOF could observe a reset after an intentionally early rejected request; the test now reads the response’s exact Content-Length. Final results are saved in `validation/standalone/watch-tests.txt`.
- Debug APK, instrumentation APK, optimized release AAB, shared unit tests and Android lint build successfully. Lint has zero errors; remaining warnings concern SDK/library update suggestions and optional KTX/style changes. Release bundle is unsigned unless production signing inputs are supplied.

## Physical Galaxy Watch 7

SM-L310, Android16 firmware `L310XXS2BZF4`, full fingerprint in FEASIBILITY.md. Development ADB installed Pakkit and observed/clicked visible UI for QA. No `pm grant`, `appops set`, hidden installer, root or ADB package installation was used to install the QA fixture. Consumer code contains no ADB/debugging path.

1. Existing Pakkit source approval was already allowed. User reports normal prior source approval/installation without ADB, including with ADB debugging disabled. The agent did not reproduce a fresh Play-installed permission setup.
2. Started Pakkit’s explicit receiving session on real Wi-Fi. Watch issued a private IPv4 URL and locally generated QR.
3. Headless Chromium opened **the real watch-hosted site**, authenticated and rendered desktop1440x1100 and mobile390x844 layouts in light/dark modes. Zero JavaScript errors; no mobile horizontal overflow. Screenshots are actual product renders in `screenshots/standalone`.
4. Browser selected our signed `qaFixture-debug.apk` (2,473,486 bytes), uploaded it through XHR to the watch’s socket and received ready. Watch persisted package `meghdad.pakkit.fixture`, correct SHA-256 and source `local-browser`. Receiving stopped at review without an installation session.
5. Watch Install and Continue actions reached the pending system-consent flow. Subsequent InstallReceiver history recorded **installed** for that exact upload UUID, and Android’s package list confirmed the fixture. The user was interacting with the watch during this test. A separate screenshot of the system installer was not captured; the image initially taken after consent had already returned to Pakkit is not presented as system UI evidence.
6. Repeated live HTTP checks: public page200; status without token403; wrong token403; authenticated status200; foreign-Origin upload403; invalid APK400. Recorded in `validation/standalone/live-http.json`. These checks also passed with the physical display explicitly asleep. This is a short screen-off accessibility check, not a long-duration power/Doze certification.
7. Final QR layout centers its square in the circular safe area and sizes with screen width. ZXing decoded the actual physical screenshot after masking all pixels outside the display circle (`validation/standalone/qr-check.txt`); the watch remains scrollable for the URL, disclosure and End session action.

The temporary QA fixture was removed after verification.

No release claim is inferred solely from a successful file upload. The observed fixture installation is separate, stronger evidence for this device’s public installation route with trust already granted.

## Remaining release gates

- Fresh Play-delivered installation and source-approval flow without preexisting Pakkit trust, across supported Samsung firmware/regions and security settings. Record denial/revocation/cancellation and developer-verification behavior.
- Google Play restricted-permission and foreground-service declaration acceptance, listing/quality review, developer identity/signing and hosted privacy policy.
- Safari on real iOS/macOS, Firefox, Edge/Windows, Android browser and Linux coverage. The current browser run was Chromium on macOS, with mobile viewport emulation; it is not proof of all browser/platform combinations.
- Full ten-minute expiry on hardware, repeated sessions, long screen-off/Doze, network/IP change, client isolation, local-network permissions, interrupted large upload, low storage and process death. Explicit-stop cleanup is automated; expiry is implemented but not yet a completed elapsed-time hardware test.
- Full TalkBack/rotary/large-text/contrast review of the new receiving screen and web flow; small round physical displays. The prior review screen was tested at192dp/1.3x text, but this does not certify every new state.
- Measured battery/thermal/memory behavior under maximum50MiB transfers and adversarial bounded connections. Locks, buffers, threads and deadlines are bounded by implementation, not a measured battery guarantee.

Do not advertise universal firmware/APK compatibility, encrypted transport, unconditional no-setup installation, or Google Play approval.
