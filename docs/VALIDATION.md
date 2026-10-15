# Validation

Routine validation uses a Wear OS Emulator and an Android Emulator browser. Hardware is reserved for final compatibility and power testing. This document distinguishes completed automated coverage from release requirements.

## Reproducible checks

See DEVELOPMENT.md for the commands and environment setup. Results are generated under app/build rather than committed. Current architecture: one production Android module (`app`) and the instrumentation-only signed fixture (`qaFixture`).

| Check                    | Scope                                                                                                                                                                                                                                                                                                  |
| ------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| JVM tests                | Streaming copy/digest beyond the previous limit, IDs, SDK/ABI/available-storage policy, strict HTTP framing/authority/64-bit lengths, watch-gated browser access, exact upload-body reads and progress throttling, native watch contrast                                                                                                                                      |
| Wear instrumentation     | Signed archive inspection, self-update/invalid archive rejection, typed storage persistence without expiry and failed-write rollback, late callback rejection, changed-file rejection, official system consent, real socket upload/access/Host/Origin/storage checks, stop cleanup/revocation, upload wake-lock release, stale-worker isolation, Wi-Fi reconnection, IPv6 listener/port collision behavior, circular QR and reachable actions |
| Small round screen       | Review controls and QR geometry at 192 dp with 1.3x text, restoring emulator configuration afterward                                                                                                                                                                                                   |
| Browser UI               | Four widths, dark/light, keyless approval/denial/retry, reload and cross-port credential isolation, invalid files, error/retry, watch/system-status transitions and cancellation, using actual assets with an API fixture                                                                                                                                      |
| Android-to-Wear transfer | Chrome on Android Emulator -> actual Wi-Fi HTTP listener on Wear OS Emulator -> verified signed APK waiting for watch review                                                                                                                                                                           |
| Release                  | Clean debug/test/release builds, Android lint, dependency and merged-manifest inspection, fixture exclusion from consumer artifacts                                                                                                                                                                    |

One install-consent test waits for Android’s STATUS_PENDING_USER_ACTION and abandons the session. A separate full-journey test taps the actual watch action and system confirmation through the UI. Neither grants source trust or installs through a shell command. Tests that require source approval use the emulator’s normal system setting; skipped assumptions are reported as incomplete validation by the test runner.

Browser UI fixtures exercise interactions independently; the separate Android Emulator test proves that the actual browser/XHR path reaches the actual watch listener. These are not substituted for one another. The receiving protocol continues to have no browser-triggered installation endpoint.

## Latest completed run

Final product review: October 15, 2026. No physical device was accessed. Evidence is generated under `app/build/reports/validation` and `app/build/test-results`.

| Environment | Verified coverage |
| --- | --- |
| JVM | **27 tests**, no failures/errors: streaming, digest, disk/ABI/SDK checks, HTTP framing/64-bit lengths, browser approval, progress and watch contrast. |
| ARM64 round Wear OS API 37 | **26 native tests**, no skipped assumptions: real signed socket uploads, watch-gated access, malformed/interrupted/storage failures, Wi-Fi loss/recovery, IPv6/fixed-port behavior, wake-lock release, metadata/removal rollback, old-review preservation, focused progress updates and offline privacy. |
| Public Android installer | The native suite taps Pakkit’s Install action, taps the real system Install/Update confirmation and receives successful installation with staged-file removal. Separate tests verify required consent, digest mutation and rejected stale/forged callbacks. The QA fixture remains installed on the emulator. No shell installer or source-permission grant is used for this path. |
| Ongoing activity | Official OngoingActivity data and an immutable return intent are present. Review status updates the actual notification; its Stop action closes receiving and removes the notification. Optional notification access is granted only on the emulator for this test. |
| Small round Wear | **Eight UI tests** at 192 dp / 1.3x font: receiving and review actions, full decodable QR, progress/focus, approval-intent isolation and offline privacy. Settings restored afterward. |
| Desktop Chromium 140 | **23 browser checks** across 320/390/768/1440 px and dark/light: keyless access/denial/retry, credential isolation, keyboard focus, theme persistence, reduced motion, 200% zoom, cancellation, state transitions, legal links, stalled headers/body recovery and a streamed 64 MiB upload. Zero JavaScript errors. API fixtures test the UI separately from real transport. |
| Android Emulator Chrome 145 → Wear Wi-Fi | **270,914,884-byte signed APK**, containing a stored 256 MiB asset, reaches READY through the real browser File/XHR path and real watch approval. No host-memory File buffer or production debug endpoint. |
| Native swipe/resume | Real left-to-right swipe dismisses the watch activity while receiving continues; reopening preserves the active address. |
| Background process recovery | After that receipt, the test backgrounds and kills Pakkit’s debug process using its own UID, reopens the application, finds the durable review, explicitly restarts receiving and reuses port 8787. Process death revokes browser access. This is not a long-duration Doze or battery benchmark. |
| Build/artifact | Debug app, instrumentation APK, JVM tests, Android lint and optimized release AAB pass. Lint: **zero errors**, four tool/library-update notices. Release checker verifies the merged manifest and exact five web/legal assets, no test APK or native `.so` payload, and records the bundle digest. Local AAB is **unsigned**. |

The complete test commands and recovery option are in [development](DEVELOPMENT.md). Run emulator automation sequentially; simultaneous instrumentation and shell `uiautomator` can interfere with each other. Watch screenshots cover actual state layouts, including offline privacy; browser screenshots cover both themes. Raw circular screenshots are development evidence, not automatically Play-compatible listing artwork.

Only API 37 emulator images were available in this environment. Minimum API 31 runtime validation remains outstanding. No elapsed-time test certifies indefinite availability or measured power quality. Large receipt proves transfer/review; it does not prove every large app installs. The successful system install uses the small signed QA fixture.

## Hardware-specific release matrix

- Fresh Google Play delivery, source approval and system confirmation on supported Galaxy Watch 7 and other firmware/regions, without preexisting source trust or developer setup. Include denied/revoked permission and cancellation.
- Transfers near actual free-storage capacity, prolonged receiving, interruption, repeated sessions, IP/network changes, guest/client-isolated networks, VPNs and browser local-network permission prompts.
- Long screen-off/Doze behavior, low battery/battery saver, charging transitions, memory/thermal behavior and measured battery consumption. Scoped locks and bounded buffers alone are not measurements.
- Actual low storage and processor/SDK/package/update conflicts; system developer verification and managed-device restrictions. The OS is the final compatibility/security authority.
- TalkBack, rotary interaction, large font and physical small-round displays; camera scanning of QR on real displays under varied lighting.
- Safari on iOS/macOS, Firefox, Edge/Windows, Linux browsers and Android browser variants. Emulator Chrome and desktop Chromium do not certify the whole browser matrix.
- Minimum supported API 31 devices and supported newer OS versions. Native lint checks API usage but cannot replace that runtime matrix.
- Google Play restricted-permission/foreground-service declarations, Wear quality review, signing/identity and hosted privacy policy.

Do not claim universal APK/firmware compatibility, encrypted transport, guaranteed Google Play approval or measured power quality from emulator results.
