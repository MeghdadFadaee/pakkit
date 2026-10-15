# Feasibility decision

Research date: October 15, 2026. Reference hardware: Samsung Galaxy Watch 7; no physical hardware has been verified in this workspace.

## Decision

Build a **capability-gated companion**, rather than promise a universal installer. Official APIs support file transfer and user-initiated package installation. Their presence in Android does not prove that a particular Wear OS firmware provides usable unknown-source settings and installation confirmation. Pakkit never changes security settings itself.

The desired no-debugging path is conditional: paired Android phone and watch → install Pakkit through Play on both → approve Pakkit as an install source on supported watch firmware → choose a complete compatible APK → send → review on watch → explicitly request installation → approve the system screen. Source approval is an unavoidable extra setup step where Android requires it.

## Confirmed platform capabilities

1. The [Wearable Data Layer overview](https://developer.android.com/training/wearables/data/overview) documents app-private phone/watch communication and requires matching package names and signing certificates. The transport can use Bluetooth or encrypted cloud routing. Pakkit uses it without its own network backend, direct sockets, Bluetooth discovery permissions or accounts. [Asset synchronization](https://developer.android.com/training/wearables/data/sync) allows queued transfer when disconnected; it is not an application database. Pakkit keeps its own state and private files. Asset APIs do not provide Pakkit with an accurate network byte-progress signal.

2. [PackageInstaller](https://developer.android.com/reference/android/content/pm/PackageInstaller) supports sessions and reports success, failure or pending user action. Pakkit explicitly requires user action and handles the returned confirmation intent. This general Android capability is not proof of a usable Wear installation UI on every OEM firmware.

3. [PackageManager.canRequestPackageInstalls](https://developer.android.com/reference/android/content/pm/PackageManager#canRequestPackageInstalls()) reports whether the caller is trusted as an installation source. Pakkit checks this on the watch. If allowed-source settings are unavailable and source approval is absent, Pakkit blocks sending. Resolver availability is a conservative preflight, not a compatibility certification.

## Google Play eligibility is conditional

[Play's sensitive permissions policy](https://support.google.com/googleplay/android-developer/answer/16558241?hl=en) includes phone-to-wearable companion synchronization among permitted REQUEST_INSTALL_PACKAGES purposes. This makes the proposed purpose potentially eligible; it does **not** guarantee permission approval or listing acceptance. Core functionality must be accurately described, declared and reviewed. Pakkit requests this permission only on the watch, offers no self-update mechanism, bundles no third-party APK and never starts an installation just because a transfer arrived. A reviewer must assess the final implementation and listing.

[Wear app packaging guidance](https://developer.android.com/training/wearables/packaging) requires a separate watch artifact, watch feature declaration and form-factor versioning. Pakkit's watch app declares itself non-standalone because selecting and sending requires the phone. Both artifacts must use the same production signing identity for the Data Layer.

[Current target API policy](https://support.google.com/googleplay/android-developer/answer/11926878) requires API 36 for new phone apps and API 35 for Wear OS as of August 31, 2026. Both modules target 36. Recheck Play Console requirements at submission time.

## Developer verification changes the promise

[Android's developer-verification guidance](https://developer.android.com/developer-verification/guides) describes regional enforcement beginning September 30, 2026. Its [FAQ](https://developer.android.com/developer-verification/guides/faq) explains an advanced flow for unverified developers that includes developer mode and a protective delay. Pakkit does not implement or recommend this flow: it conflicts with the requested no-developer-options experience. Applicability to a particular Wear firmware must be tested; do not infer a verified exemption or rollout from the generic Android guidance. Respect any system block and direct users to a supported distribution of the app.

## Limits and assumptions

- **Unverified:** Galaxy Watch 7 firmware versions, region-specific security settings, actual approval activity behavior and developer-verifier enforcement. Run the physical-device matrix before advertising support.
- **Supported intentionally:** complete single APKs up to 50 MiB. No APKM/XAPK/APKS/AAB or split installation support.
- **Preflight only:** SDK minimum, native-library ABI, approximate space, readable signing information and integrity. These do not prove safety, runtime usability, feature availability, signature validity under all platform rules, successful update or a wearable layout. The OS makes the final install decision.
- **No bypass:** if watch installation is unavailable, Google Play on that watch is the fallback. Pakkit does not silently enable debugging, root, hidden services or elevated privileges.
- **Release gate:** if physical tests show the reference device cannot complete the public consent path, do not publish the installer listing as broadly compatible. Narrow support to tested firmware or reconsider the product's scope.
