# Standalone feasibility decision

Research and implementation: October 15, 2026. Pakkit 0.2 replaces the companion architecture.

## Decision and evidence boundary

Use a user-started local HTTP receiving session hosted by the watch, followed by explicit on-watch review and a public PackageInstaller session requiring user action. Receiving a file does not install it. Support is conditional on firmware exposing source approval and a functioning system installer.

Android’s [Wear network guidance](https://developer.android.com/training/wearables/data/network-communication) explicitly supports HTTP/TCP/UDP and independent network access. This supports implementing a server using ordinary Android sockets; the documentation does not certify inbound reachability on every network. A Bluetooth-proxied internet route is not an inbound Wi-Fi listener, so Pakkit requests a Wi-Fi network and binds only its private IPv4 address. Guest-network isolation and firewalls remain outside application control.

The [standalone app definition](https://developer.android.com/training/wearables/apps/standalone-apps) permits QR/browser alternatives without a companion phone app. The manifest now declares standalone=true. “Any device” means a supported modern browser on a reachable local network, not independence from the watch’s original OEM onboarding requirements. A Galaxy Watch 7’s initial setup requirements are separate from Pakkit’s browser support.

## Installation is a separate capability

[PackageInstaller](https://developer.android.com/reference/android/content/pm/PackageInstaller) permits ordinary apps to create sessions with REQUEST_INSTALL_PACKAGES. Pakkit uses MODE_FULL_INSTALL and USER_ACTION_REQUIRED, handles STATUS_PENDING_USER_ACTION and starts only the returned system confirmation intent following a watch action. Android decides the final outcome. Readable signing metadata, hashes, SDK, ABI and space checks are preflight, not malware scanning or proof that an app will work on a round display.

[canRequestPackageInstalls](https://developer.android.com/reference/android/content/pm/PackageManager#canRequestPackageInstalls()) reports source trust. [ACTION_MANAGE_UNKNOWN_APP_SOURCES](https://developer.android.com/reference/android/provider/Settings#ACTION_MANAGE_UNKNOWN_APP_SOURCES) is the public settings route. A resolving activity alone does not prove a usable permission UI. Pakkit checks actual trust and gates session start; if approval is unavailable it explains the limit and offers Google Play. No appops changes, hidden components, privileged APIs or debugging client exist in application code.

### Galaxy Watch 7 evidence

Reference hardware observed: SM-L310, Android 16, fingerprint `samsung/fresh7blue/fresh7bl:16/BP2A.250325.020/L310XXS2BZF4:user/release-keys`.

The user reports that the previous Pakkit build completed installation normally without ADB, including with ADB debugging disabled. The development inspection found REQUEST_INSTALL_PACKAGES already allowed; no shell permission grant or security-setting modification was made. This is useful hardware evidence but does not independently reproduce a fresh Play-installed permission setup. See VALIDATION.md for the separately observed browser/upload/installer results. Do not generalize this result to every Samsung firmware, region or security configuration.

## Lifecycle and power

A [connectedDevice foreground service](https://developer.android.com/develop/background-work/services/fgs/service-types#connected-device) fits an explicit local transfer with an external browser device. CHANGE_NETWORK_STATE is used to request Wi-Fi and meets this service type’s declared prerequisite. The foreground notification has a stop action. A timed partial wake lock keeps the CPU available without holding the display on. Wi-Fi requests and CPU lock are released at session stop, network loss, service destruction or ten-minute expiry. Nothing starts at boot; START_NOT_STICKY prevents automatic session resurrection. Process death invalidates the session and the user starts another.

No continuous decorative watch animation, discovery broadcasts, cloud relay or analytics runs. Browser polling pauses while hidden. Watch byte updates are throttled to four per second. This bounds resource use but is not a battery consumption measurement; screen-off longevity, thermal and energy measurements remain release tests.

[Local network protections](https://developer.android.com/privacy-and-security/local-network-permission) cover incoming TCP too. At the current target SDK 36, INTERNET provides implicit local access. Before targeting 37, implement and test ACCESS_LOCAL_NETWORK runtime permission; official guidance says not to request it on lower targets. Browsers can independently require local-network permission.

## Google Play

The [REQUEST_INSTALL_PACKAGES policy](https://support.google.com/googleplay/android-developer/answer/12085295?hl=en) permits file sharing/transfer/management when package receipt and user-initiated installation are core functionality. Pakkit appears aligned with that category; this is an eligibility assessment, not approval. Accurately declare the final standalone purpose in Play Console and show source approval, transfer, watch review, system consent and denial handling. Do not reuse a companion-sync justification.

Declare the [foreground service use](https://support.google.com/googleplay/android-developer/answer/13392821) and provide the required demonstration. Publish only the watch artifact, with the required watch feature and standalone metadata, following [Wear packaging guidance](https://developer.android.com/training/wearables/packaging).

[Android developer verification](https://developer.android.com/developer-verification/guides) can add system restrictions to external apps. Some unverified-developer flows require developer settings and a delay. Those flows conflict with the product’s setup goal; Pakkit does not bypass them. Describe the supported compatible/verified package path rather than guaranteeing installation of every APK.

## Alternatives and limits

- **Google Play installation:** the compliant fallback when source trust, security policy, verification or firmware blocks external installation. Pakkit cannot silently elevate itself.
- **Trusted HTTPS relay:** could avoid LAN reachability and certificate setup, but requires a backend, retention policy, hosting cost, internet connectivity and additional privacy work. It would not fix a blocked PackageInstaller. Not implemented.
- **Local HTTPS:** a self-signed watch certificate produces browser trust warnings; ordinary public trust cannot be issued to an arbitrary private IP without additional infrastructure. Current local HTTP avoids certificate setup, openly limits use to trusted networks and does not claim encryption.
- **ADB/root/device-owner hacks:** excluded; they defeat the product goal or require privileges consumers do not have.
- **Cloud APK catalog/self-updater:** excluded; no third-party catalog, bundled consumer APK or Pakkit self-update path.

Current scope: private IPv4 Wi-Fi, one complete APK up to 50 MiB, one pending review, no split/APKS/APKM/XAPK/AAB support, no resumable upload. The OS performs final signature/update/verification checks. A failed firmware matrix or rejected permission declaration blocks production publication; a working upload alone does not change that decision.
