# Product and release review

Review date: October 15, 2026. This is a release-candidate assessment, not a Google Play approval or a certification of all watch firmware.

## Product refinements implemented

- Watch backgrounds are black while purple, mint, the parcel mark and the official **Pack it. Send it. Wear it.** identity remain consistent. The launcher-matched 48 dp splash uses AndroidX’s Wear-aware sizing; native swipe dismissal remains enabled.
- Upload progress updates the existing watch view tree, preserving rotary/keyboard/accessibility focus and avoiding repeated full-screen layouts. Percentage arithmetic handles 64-bit lengths without integer overflow.
- Receiving has a Wear Ongoing Activity with a static, battery-conscious icon, a return-to-app intent and a Stop action. Notification copy changes at meaningful phases, not on every byte. Optional Enable alerts is available while receiving; denied notification access does not block transfers.
- The browser keeps keyboard focus when panels change, preserves result-button focus across polls, remembers theme preference and recovers from stalled connection requests. Uploads still have no total deadline. Reduced motion, responsive layout and both themes remain supported.
- Privacy is available offline from the watch home screen. Browser Privacy and Open-source licenses links serve bundled text from an explicit route allowlist. APKs and browser credentials never appear in those documents.
- Failed file-removal persistence restores the previous review and retains its APK. The watch shows a recoverable error instead of crashing. The exported launcher no longer accepts installer intents in arbitrary incoming extras; only the private, session-matched installer callback supplies approval state.

## Evidence and limits

See [validation](VALIDATION.md) for the final test counts, emulator configuration and generated evidence. The build audit is reproducible with `python3 scripts/check-release.py`. It checks the merged release manifest, exact bundled assets, permissions, exported components, native-library absence, lint errors and bundle signature presence. It records a SHA-256 digest. It does not cryptographically verify signing, decode the bundle’s protobuf manifest or submit the app to Play.

AndroidX Core, Wear Ongoing and SplashScreen are intentional dependencies for official platform presentation. Core adds a signature-protected dynamic-receiver permission. Its profile installer contributes a private startup provider and a DUMP-protected receiver. These do not add user data collection or ordinary third-party access. There are no native `.so` libraries, so this artifact has no 32-bit-only native payload; testing uses a 64-bit ARM Wear image. Reassess native ABI and 16 KB page compatibility if native libraries are added.

## Policy review

| Requirement | Assessment and remaining action |
| --- | --- |
| Target API | Target 36 exceeds the Wear minimum of 35 effective August 31, 2026. [Current target API policy](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en). The Wear quality overview still references the 2025 API threshold; the dedicated policy is the source for the current submission threshold. |
| Wear quality | Black background, scaled text, 48 dp actions, 192/227 dp round testing, scroll indicator, swipe dismissal, state restoration, splash and Ongoing Activity are implemented. TalkBack, physical rotary/QR and long-duration power behavior still need final device validation. [Quality checklist](https://developer.android.com/docs/quality-guidelines/wear-app-quality), [splash](https://developer.android.com/training/wearables/apps/splash-screen), [ongoing work](https://developer.android.com/training/wearables/notifications/ongoing-activity). |
| Restricted installation | Local package transfer is core functionality and user-initiated installation is explicit. This appears to fit the file-sharing/transfer category; approval remains a Play review decision. Submit an accurate permission declaration and demonstration. Self-updates remain blocked. [Installation policy](https://support.google.com/googleplay/android-developer/answer/12085295?hl=en). |
| Foreground service | connectedDevice is declared for explicit receiving over a network, has an ongoing indicator and Stop action, and does not start at boot. Declare its user-triggered use and supply a video. Review must cover extended waiting while the user leaves receiving enabled. [FGS requirements](https://support.google.com/googleplay/android-developer/answer/13392821?hl=en). |
| Privacy and transport | No developer backend, analytics, account or remote assets. Local HTTP carries APKs and credentials without encryption. Watch approval protects access but does not protect traffic. The User Data policy requires secure handling of personal/sensitive data; disclosure alone does not establish compliance. Confirm the final local-transfer use case and Data safety answers before publication. If stronger transport protection is required, implement and validate a trusted-browser solution before shipping. [User Data](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en), [Data safety](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en). |
| Developer verification | Do not promise universal installation of unregistered apps. Current regional enforcement and supported advanced flows are system/store decisions; firmware and regional applicability require final checking. Pakkit does not bypass them. [Developer verification](https://developer.android.com/developer-verification). |
| Signing | Optimized AAB builds; incomplete signing inputs fail. Current local artifact is unsigned. Supply the upload key, verify its certificate and enroll in Play App Signing. Protect the upload key outside Git; increment versionCode per submission. |
| Listing and access | English listing draft is prepared in [store listing](STORE_LISTING.md). Supply a real support contact, hosted policy, icon/feature graphic and actual unmasked square Wear screenshots. Give reviewers same-Wi-Fi setup steps and a redistributable test APK. Complete content rating, audience/ads, app access, Data safety and permission/FGS forms. [Wear distribution](https://developer.android.com/training/wearables/packaging), [preview assets](https://support.google.com/googleplay/android-developer/answer/9866151?hl=en). |

## Publication gates

1. Supply and verify production signing, developer/support identity, hosted privacy policy and store artwork. Rebuild through trusted dependency repositories; this environment required the existing optional Google Maven mirror for new AndroidX artifacts.
2. Complete the concise hardware/browser/runtime matrix in [validation](VALIDATION.md), including minimum API 31. Only API 37 system images were available locally for this run; lint is not a substitute for minimum-version execution.
3. Resolve the transport/Data safety policy assessment and obtain restricted-permission, FGS and Wear review acceptance. Exercise fresh Play delivery through internal/closed testing and review its pre-launch report before public promotion.

No physical watch access is needed for routine development. There is no claim of measured hardware battery life, indefinite receiving under all OS conditions, malware safety or universal APK compatibility.
