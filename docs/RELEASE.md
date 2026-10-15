# Release preparation

## Artifact

Publish the app module’s Wear OS AAB: application ID `meghdad.pakkit`, version 0.3.0/code 100004, min 31/target 36/compile 36.1. It declares required watch hardware and standalone=true. Increment versionCode for each upload. R 8 and resource shrinking are enabled.

```sh
./gradlew :app:bundleRelease
```

Output: app/build/outputs/bundle/release/app-release.aab. Production signing uses PAKKIT_KEYSTORE, PAKKIT_KEYSTORE_PASSWORD, PAKKIT_KEY_ALIAS and PAKKIT_KEY_PASSWORD. Set all four; incomplete/blank signing configuration fails clearly. With all unset, the bundle is unsigned and must be signed through the release pipeline. Keep keys and passwords outside source control. Application ID and private review schema are stable across updates; source class/package refactoring does not require data migration.

## Runtime permission surface

| Permission                                               | Use                                                                       |
| -------------------------------------------------------- | ------------------------------------------------------------------------- |
| INTERNET                                                 | Local listener and HTTP responses                                         |
| ACCESS_NETWORK_STATE / CHANGE_NETWORK_STATE              | Request/watch the Wi-Fi network and handle connectivity lifecycle         |
| WAKE_LOCK                                                | Timed partial CPU lock during a receiving session                         |
| FOREGROUND_SERVICE / FOREGROUND_SERVICE_CONNECTED_DEVICE | Explicit local receiving service with stop action                         |
| REQUEST_INSTALL_PACKAGES                                 | Watch-initiated system installation after review                          |
| POST_NOTIFICATIONS                                       | Optional session/approval alerts; in-app consent remains usable if denied |

No package-visibility queries, broad storage, location, Bluetooth, boot receiver or unrestricted installed-app enumeration is required. Inspect merged release permissions and verify fixture/test assets are absent. Release runtime dependencies are ZXing Core and the Kotlin standard library with its annotations dependency; browser development tools are separate.

## Publication gates

1. Complete unit/native/browser checks in DEVELOPMENT.md and the hardware-specific matrix in VALIDATION.md.
2. Submit REQUEST_INSTALL_PACKAGES as core local package receipt plus user-initiated installation, with source approval, transfer, watch review, system consent, denial and cancellation evidence. [Permission policy](https://support.google.com/googleplay/android-developer/answer/12085295).
3. Submit the connectedDevice foreground-service declaration and required demonstration. [Foreground-service requirements](https://support.google.com/googleplay/android-developer/answer/13392821).
4. Provide real developer/support identity, hosted privacy policy, Data safety answers, content rating, audience/ads declarations and current screenshots. Generate screenshots from the product; do not commit development artifacts as source assets.
5. Describe trusted same-Wi-Fi, unencrypted HTTP, complete APK limit, source approval and firmware/package restrictions accurately. Do not promise universal compatibility, malware certification, silent installation or guaranteed permission approval.
6. Recheck target API, Wear app quality and developer-verification requirements against current Play Console guidance. Target 37 requires explicit local-network permission implementation/testing.
7. Run internal/closed testing and Play review before public promotion. Successful development/emulator tests do not constitute Google Play acceptance.
