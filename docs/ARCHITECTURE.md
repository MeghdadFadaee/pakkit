# Architecture

Pakkit is a single Android application for Wear OS. The watch hosts both the native review/install experience and a static HTML/CSS/JavaScript interface delivered to a browser over local Wi-Fi.

## Ownership

| Package    | Responsibility                                                                               |
| ---------- | -------------------------------------------------------------------------------------------- |
| `ui`       | WatchActivity, round scrolling, status glyphs and watch design tokens                        |
| `transfer` | Receiving state, Wi-Fi service, local HTTP endpoint, framing and bounded upload body         |
| `apk`      | APK metadata, package inspection, compatibility/space/integrity rules and typed review state |
| `data`     | PackageStore: one staged APK and its durable review/installation state                       |
| `install`  | ApkInstaller, validated system callback and pending system approval intent                   |

The native UI composes Android platform views. The web app is served directly from `app/src/main/assets/web`; there is no frontend build step, CDN, WebView, account, backend or internet requirement for transfer. The only explicit runtime library is ZXing Core. A separate Gradle application under `qa/fixture` supplies a signed test APK exclusively to the instrumentation APK.

## Receiving lifecycle

WatchActivity starts ReceiveService only after an explicit action. The non-exported connectedDevice foreground service owns the notification/stop action, a requested Wi-Fi network, a timed partial CPU wake lock and a ten-minute deadline. It waits for link properties and binds LocalHttpServer to that network’s private IPv4 address and an ephemeral port. Network loss or address change ends the session. START_NOT_STICKY and absence of boot receivers prevent automatic resurrection.

LocalHttpServer owns the listener, two workers, four queued connections, tracked sockets, HTTP routing and one streamed upload. Service teardown closes the listener and accepted sockets, stops workers, unregisters the Wi-Fi callback, removes callbacks and releases the CPU lock. The display is not held awake. The session can continue while the display sleeps, subject to firmware and OS constraints.

`Receiving` holds an immutable, volatile snapshot in memory. Updates are tagged with the owning service instance, so a stopped session’s workers cannot change a later session. UI notifications are coalesced onto the main thread and observers are removed on pause. Private links and byte counters are never written to preferences. Process death invalidates the session; the user starts a fresh one.

## Durable package state

PackageStore retains one PendingApk: UUID, creation time, inspected metadata, an InstallState, detail and optional installer session ID. It preserves a stable private storage schema across application updates. All writers share a lock; staging refuses replacement of an existing review, and updates require the current package ID. Dismissed reviews cannot be resurrected by late callbacks.

Files use app-private UUID filenames. Complete APKs remain eligible for review for 24 hours. Launch cleanup removes expired reviews and old unreferenced files. Partial/rejected uploads are deleted in a finally block. Installation success or dismissal deletes the staged APK. Expiry is an eligibility deadline with lazy disk cleanup, not a scheduled background deletion promise.

## HTTP and trust

Each session generates a 192-bit random key embedded in the private URL fragment. JavaScript removes it from visible history after page load and keeps it in memory for Authorization headers. A reload without the fragment requires the key again. A fixed asset allowlist prevents filesystem traversal. APIs require the key; uploads also require the exact Origin. Exact Host validation resists DNS rebinding. No CORS, framing, external scripts/styles/fonts, cached responses or browser-controlled installation endpoints are enabled.

HttpProtocol accepts a small HTTP/1.1 subset: GET/POST, one request per connection, 8 KiB total headers, 4 KiB lines, unique headers and no chunked/Expect framing. Uploads require decimal Content-Length in 1..50 MiB and application/octet-stream. UploadBody reads exactly that length, treats truncation as failure and reports progress at most four times per second. A 64 KiB buffer writes the private file while computing SHA-256. Storage reserve and bounded ZIP/manifest inspection precede review. SDK, ABI, complete-package and self-update checks are centralized in the APK package.

Local HTTP is unencrypted. Link secrecy and Host/Origin/hash checks do not protect against a LAN observer or active attacker. Use a trusted network. Possession of the link allows file submission, while watch review and the Android installer remain separate consent boundaries.

## Installation

InstallState progresses from READY to INSTALLING, then CONFIRM when Android requires user action, and finally INSTALLED or FAILED. ApkInstaller runs off the main thread only after the watch’s Install action. It rechecks source trust, expiry, private-file digest and compatibility before creating a MODE_FULL_INSTALL session with USER_ACTION_REQUIRED. It never bypasses source approval, verification or system consent.

The explicit mutable PendingIntent targets a non-exported InstallReceiver. The receiver requires the active package ID, a non-null matching session ID and an installation/confirmation state. A pending action exposes only Android’s returned confirmation intent, through the watch UI or an immutable notification PendingIntent. State changes refresh the UI through PackageStore preferences; the receiver does not hold an Activity reference. Interrupted confirmation can be retried.

Optional notification denial leaves the in-app consent flow usable. Firmware that cannot expose usable source approval or installation consent is unsupported for external installs and directs users to Google Play. Read PLATFORM.md and RELEASE.md before making distribution claims.
