# Validation

Routine validation uses a Wear OS Emulator and an Android Emulator browser. Hardware is reserved for final compatibility and power testing. This document distinguishes completed automated coverage from release requirements.

## Reproducible checks

See DEVELOPMENT.md for the commands and environment setup. Results are generated under app/build rather than committed. Current architecture: one production Android module (`app`) and the instrumentation-only signed fixture (`qaFixture`).

| Check                    | Scope                                                                                                                                                                                                                                                                                                  |
| ------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| JVM tests                | Bounded copy/digest, IDs, expiry, SDK/ABI/storage policy, strict HTTP framing/auth/length, exact upload-body reads and progress throttling, native watch contrast                                                                                                                                      |
| Wear instrumentation     | Signed archive inspection, self-update/invalid archive rejection, typed storage persistence/expiry, late callback rejection, changed-file rejection, official system consent, real socket upload/auth/Host/Origin/size checks, stop cleanup, stale-worker isolation, circular QR and reachable actions |
| Small round screen       | Review controls and QR geometry at 192 dp with 1.3x text, restoring emulator configuration afterward                                                                                                                                                                                                   |
| Browser UI               | Four widths, dark/light, private-key entry, invalid files, error/retry, watch/system-status transitions and cancellation, using actual assets with an API fixture                                                                                                                                      |
| Android-to-Wear transfer | Chrome on Android Emulator -> actual Wi-Fi HTTP listener on Wear OS Emulator -> verified signed APK waiting for watch review                                                                                                                                                                           |
| Release                  | Clean debug/test/release builds, Android lint, dependency and merged-manifest inspection, fixture exclusion from consumer artifacts                                                                                                                                                                    |

The install-consent test calls the same ApkInstaller used by the explicit watch action, waits for Android’s STATUS_PENDING_USER_ACTION and abandons the test session afterward. It does not grant permission or install through a shell command. Tests that require source approval use the emulator’s normal system setting; skipped assumptions are reported as incomplete validation by the test runner.

Browser UI fixtures exercise interactions independently; the separate Android Emulator test proves that the actual browser/XHR path reaches the actual watch listener. These are not substituted for one another. The receiving protocol continues to have no browser-triggered installation endpoint.

## Latest completed run

Refactoring validation date: October 15, 2026. No physical device was accessed.

- **19 JVM tests passed**, with zero failures/errors.
- **14 native tests passed** on the round Wear OS API 37 emulator, with no skipped assumptions. This includes the public installer’s required-consent callback and refusal of requests after a session deadline.
- **Two round UI tests passed again** at 192 dp / 1.3x text: reachable review actions and a decodable QR whose full square fits inside the circular bezel. Size, density and font scale were restored.
- **13 browser UI checks passed** on Chromium 140.0.7339.16 across four viewport widths and both themes, with zero JavaScript errors.
- **Real Android Emulator Chrome 145.0.7632.218 uploaded the signed fixture directly to the Wear OS Emulator over virtual Wi-Fi**, receiving READY review state. The test dismissed its package, ended receiving and removed its local debug forward.
- **Clean builds succeeded** for the debug app, instrumentation APK, unit tests, lint and optimized release AAB. Lint has zero errors; three remaining warnings are SDK/Gradle/library update notices, not unused code/resources.
- Release inspection found exactly the three bundled web assets, no fixture APK/test payload, no package-visibility queries, one exported launcher and non-exported service/installer receiver. The release dependency graph contains ZXing Core, Kotlin stdlib and its annotations dependency.
- The unchanged Gradle distribution version is checksum-pinned to its official download. Release output is unsigned when signing inputs are absent; incomplete signing configuration fails clearly.

Detailed XML, text logs, screenshots and browser summaries are generated under app/build/reports and app/build/test-results. They are not source assets or claims of universal hardware/browser support.

## Hardware-specific release matrix

- Fresh Google Play delivery, source approval and system confirmation on supported Galaxy Watch 7 and other firmware/regions, without preexisting source trust or developer setup. Include denied/revoked permission and cancellation.
- Real maximum-size transfers, interruption, ten-minute expiry, repeated sessions, IP/network changes, guest/client-isolated networks, VPNs and browser local-network permission prompts.
- Long screen-off/Doze behavior, low battery/battery saver, charging transitions, memory/thermal behavior and measured battery consumption. Timed locks and bounded buffers alone are not measurements.
- Actual low storage and processor/SDK/package/update conflicts; system developer verification and managed-device restrictions. The OS is the final compatibility/security authority.
- TalkBack, rotary interaction, large font and physical small-round displays; camera scanning of QR on real displays under varied lighting.
- Safari on iOS/macOS, Firefox, Edge/Windows, Linux browsers and Android browser variants. Emulator Chrome and desktop Chromium do not certify the whole browser matrix.
- Minimum supported API 31 devices and supported newer OS versions. Native lint checks API usage but cannot replace that runtime matrix.
- Google Play restricted-permission/foreground-service declarations, Wear quality review, signing/identity and hosted privacy policy.

Do not claim universal APK/firmware compatibility, encrypted transport, guaranteed Google Play approval or measured power quality from emulator results.
