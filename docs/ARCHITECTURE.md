# Standalone architecture and security

## Components and flow

WatchActivity → explicit Start receiving → ReceiveService → Wi-Fi network request → private IPv4 listener → private URL/QR → browser HTML/CSS/JS from watch → authenticated raw APK POST → bounded private file → Android archive inspection → ready state → watch Install → PackageInstaller → system consent → result receiver → browser status.

`shared` contains protocol/inspection/storage/design code. `wear` owns the server and installer. The phone module, Play Services Data Layer and WorkManager have been removed. No Google services runtime, backend, account, mDNS or WebView is required. ZXing Core encodes the QR locally.

## Session ownership

The user starts a connectedDevice foreground service while WatchActivity is visible. Wi-Fi is requested for up to 25 seconds; absent connectivity yields a recoverable message. A socket binds exclusively to the chosen Wi-Fi private IPv4 address and ephemeral port. The service is never exported, restarted automatically or started at boot. The ten-minute deadline starts at user initiation; a timed CPU wake lock covers the session without keeping the screen on. Losing that network ends the session. Stop closes accepted sockets, shuts down the bounded worker pool, unregisters the network callback, removes the URL and releases power resources.

The browser may continue watching installation results while the session remains active. Ending the network session does not cancel an already committed Android installation or delete a pending user review. A completed upload remains eligible for review for 24 hours; expired packages are removed when the app next opens. This is lazy cleanup, not a guaranteed background deletion deadline. Partial and rejected uploads are deleted immediately. Reboot/process death requires a new receiving session. No resume tokens are persisted for transfer recovery.

## HTTP boundary

- Independent 192-bit SecureRandom URL-safe key per session, in the URL fragment so the initial HTTP request does not include it. JS removes the fragment from visible history after loading, keeps it only in memory and sends an Authorization bearer header for APIs. Reload without the key requires re-entering it or rescanning.
- All content is local. Strict self-only script/style/connect CSP, no framing, no referrer, no cache, no MIME sniffing, no CORS or external fonts/trackers.
- Exact Host check protects against DNS rebinding. Upload requires the watch page’s exact Origin and bearer token. No browser install/cancel-install endpoint exists.
- Parser limits headers to 8 KiB and lines to 4 KiB, rejects duplicate headers, invalid framing, transfer encoding, expectations and unsupported methods. Each socket serves one request then closes. Only fixed public asset routes are served; there is no user-controlled filesystem lookup.
- Raw octet-stream POST with positive Content-Length <=50 MiB. No multipart buffering, user filenames or extraction into filesystem paths. UUID private filename. One upload at a time; another review must be dismissed/completed first. Free-space preflight, streamed SHA-256, 64 KiB buffer, ZIP expansion/entry limits, full-package/split and self-update rejection, SDK/ABI/storage checks.
- Two workers, four queued connections, 15-second read inactivity deadline and hard session lifetime. CPU/memory/storage are bounded. Early rejected large requests may be observed by a browser as a connection error because the server intentionally does not consume an unauthorized body.
- Progress is bytes actually read on the watch and bytes sent by browser XHR. 100% sent changes to verification wording and never implies installation. Installation success appears only after STATUS_SUCCESS.

## Trust model

Local HTTP is unencrypted. Link secrecy, Host/Origin checks and SHA-256 do not stop an active LAN attacker, protect against passive capture, or authenticate the server with a certificate. Use a trusted private Wi-Fi and keep the private URL secret. Anyone holding the link can submit a package, but only an explicit watch action can request its installation and Android requires consent. The app does not claim malware scanning or universal app compatibility. Source approval and system restrictions remain authoritative.

ReceiveService has INTERNET, ACCESS_NETWORK_STATE, CHANGE_NETWORK_STATE, WAKE_LOCK and foreground-service permissions; these support the explicit Wi-Fi session. REQUEST_INSTALL_PACKAGES is limited to the watch. Optional notification permission enables visible receiving/approval alerts; denying it does not authorize hidden background starts or bypass the in-app consent flow. The system may still expose the running foreground service in its task UI.

## Installation state

`LocalInstall.status` writes watch-local ready/installing/confirm/installed/failed state. Store persists the current package/review across activity recreation. Session callbacks validate job and session IDs in a non-exported receiver. The pending system intent is kept in process or carried by the app’s immutable notification PendingIntent; interrupted approval can be retried. Installation re-hashes the private file and rechecks compatibility before writing a PackageInstaller session. Dismiss deletes the staged package. Success deletes it and cancels the consent notification.

No appops mutation, permission grant shell command, ADB client, root, hidden installer component, device-owner privilege, automatic installation, browser-driven installation or Pakkit self-update exists in the product.
