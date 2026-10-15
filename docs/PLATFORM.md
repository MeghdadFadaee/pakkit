# Platform and distribution constraints

## Supported flow

Pakkit requests a local Wi-Fi connection, hosts its browser assets and accepts a complete APK. Watch review initiates the public Android PackageInstaller session with user action required. Receiving a file is not proof that firmware permits installation.

[Wear OS network guidance](https://developer.android.com/training/wearables/data/network-communication) documents independent HTTP/TCP/UDP access. Pakkit uses a requested Wi-Fi network and a listener bound to its private IPv4 address; a proxied/default internet route is insufficient for inbound LAN access. Guest-network isolation, VPNs, firewalls and browser local-network restrictions can prevent access. IPv6-only networks and resumable/split-package uploads are outside the current scope.

[Standalone Wear apps](https://developer.android.com/training/wearables/apps/standalone-apps) support a QR/browser flow. The manifest declares required watch hardware and standalone=true. A watch’s OEM onboarding requirements remain independent of Pakkit’s transfer functionality.

## Source approval and installation

[PackageManager.canRequestPackageInstalls](<https://developer.android.com/reference/android/content/pm/PackageManager#canRequestPackageInstalls()>) reports trust as an installation source. Pakkit opens the public [unknown-app sources setting](https://developer.android.com/reference/android/provider/Settings#ACTION_MANAGE_UNKNOWN_APP_SOURCES) for the user to approve; it never changes that setting itself.

[PackageInstaller](https://developer.android.com/reference/android/content/pm/PackageInstaller) supports sessions, pending system user action and final success/failure results. Pakkit explicitly requires user action and handles Android’s returned consent intent. A source setting or API existing on Android does not establish that every Wear OS firmware exposes usable permission/confirmation UI. Unsupported firmware directs the user to Google Play. No root, privileged installer, hidden component, debugging client or verification bypass is implemented.

APK inspection checks size, bounded archive structure, readable signing metadata, complete-package status, SDK, ABI and storage reserve. It rejects Pakkit self-updates. It does not certify malware safety, final signature validity, runtime behavior, every hardware feature or a suitable circular layout. Android makes the final installation decision. Verification/security policy can block an otherwise well-formed upload.

## Lifecycle and permissions

A user-started [connectedDevice foreground service](https://developer.android.com/develop/background-work/services/fgs/service-types#connected-device) supports a bounded local transfer with an external device. CHANGE_NETWORK_STATE supports the Wi-Fi request and this service type’s prerequisite. The service exposes a stop action and releases its network/CPU resources at termination. This design bounds resource use but does not replace hardware battery/thermal testing.

[Local network protections](https://developer.android.com/privacy-and-security/local-network-permission) apply to incoming TCP too. Target 36 uses INTERNET for implicit local access. Before raising the target to 37, implement/test ACCESS_LOCAL_NETWORK runtime consent. Browser permissions can impose separate constraints. Keep this guidance aligned with the target SDK and supported firmware.

## Google Play

The [REQUEST_INSTALL_PACKAGES policy](https://support.google.com/googleplay/android-developer/answer/12085295?hl=en) permits file transfer/management where package receipt and user-initiated installation are core functionality. This makes Pakkit’s use potentially eligible; it does not guarantee approval. Accurately declare this purpose and submit the source-approval/review/consent flow. Also declare the [foreground-service use](https://support.google.com/googleplay/android-developer/answer/13392821) and meet [Wear packaging/quality guidance](https://developer.android.com/training/wearables/packaging).

[Android developer verification](https://developer.android.com/developer-verification/guides) may add restrictions to externally installed packages. Pakkit respects system blocks and does not promise to install every APK without additional platform requirements.

Local HTTP avoids a self-signed certificate setup flow but is unencrypted and limited to trusted networks. A relay or local HTTPS architecture would require separate certificate/backend/privacy work and would not make a blocked installer available. Google Play is the supported fallback when firmware or security policy prevents external installation.
