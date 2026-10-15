# Architecture

Pakkit is a Wear OS application that serves its bundled HTML/CSS/JavaScript interface to a browser over Wi-Fi. The watch owns access approval, package review and Android installation. The browser can transfer files and observe state; it cannot request installation.

| Package | Responsibility |
| --- | --- |
| `ui` | Native watch views, circular scrolling, QR, browser approval and package review |
| `transfer` | Live receiving state, Wi-Fi lifecycle, fixed-port server, browser access and streamed HTTP bodies |
| `apk` | Inspected metadata, content/compatibility checks, streaming integrity and durable state model |
| `data` | Private files and one durable package review |
| `install` | User-requested Android sessions, system consent and verified installer callbacks |

There is no frontend build step, CDN, cloud relay, account or transfer backend. ZXing Core generates QR codes. AndroidX Core, Wear Ongoing and SplashScreen supply platform notification and launch behavior. `qa/fixture` supplies a signed test APK to instrumentation only; browser tools and large generated fixtures never ship.

## Receiving lifecycle

An explicit watch action starts the non-exported connectedDevice foreground service. It requests Wi-Fi without a product timeout and listens on **8787**. IPv4 is preferred; unscoped IPv6 is supported. Only an actual local interface address is bound. A busy port is a clear failure, not an instruction to fall back to a random port.

Network loss closes the listener and active sockets, resets live transfer progress and leaves receiving enabled. The outstanding network request supplies new link properties; the listener rebinds automatically. Browser access belongs to the service and survives a listener rebind. An IP change still requires opening the new address. Stop, OS timeout and destruction close network resources and clear access. START_NOT_STICKY and the absence of boot receivers prevent silent reopening after process death.

Idle receiving holds no CPU wake lock. An actual upload/inspection owns one, released in finally. Two workers, four queued sockets, exact HTTP framing and idle-read protection bound concurrent resource use. A header parsing budget prevents slow headers from retaining a worker indefinitely. These do not impose a transfer-duration or package-size ceiling. Screen-off operation remains subject to the OS and firmware.

`Receiving` publishes an immutable in-memory snapshot through coalesced main-thread observers. Service ownership and listener-close guards prevent stale workers from modifying later state. The server's close/commit lock prevents a file being staged after Stop. UI observers are removed on pause; byte counters and browser credentials are not persisted.

## Browser access

The QR and displayed address are ordinary URLs without secrets. On page load, a same-origin POST requests browser access. The watch shows the browser/device description and peer address. Its Allow/Not now actions approve or deny a random, session-only access credential. The browser stores the credential in localStorage isolated to this scheme/host/port and sends it in Authorization headers. Reloads and tabs in the same browser profile do not ask again. Storage-denied browsers use memory and may need approval after reload. An explicit retry can request approval after denial. Unknown peers cannot replace the prompt already under consideration. One pending slot bounds unauthenticated request allocation; further decision records require a watch action. Approvals have no time or browser-count quota.

Only approved credentials can observe package status or upload. Host and POST Origin must resolve numerically to this listener's address/port; IPv6 compression is normalized without allowing hostnames. Static assets come from a fixed allowlist. CSP, no-cache/no-referrer/nosniff, no CORS and no framing remain in force. HTTP is unencrypted and requires trusted networking; authentication does not provide transport confidentiality.

## Streaming and storage

Uploads use File/XHR and raw octet-stream with a positive, 64-bit Content-Length. There is no application byte ceiling or total-duration timeout. A 64 KiB buffer writes to an app-private UUID file while computing SHA-256. UploadBody reads exactly the declared length and throttles progress updates. Available disk bytes are checked before writing; ENOSPC and interrupted reads fail with recovery copy and delete the partial file.

Receipt inspects ZIP directory and Android metadata without unpacking assets into memory. Complete-package, SDK, processor and Play self-update policy checks precede review. There are no arbitrary directory-entry, expanded-data or manifest-size caps. Android's parser, package format and device resources still impose real constraints.

PackageStore retains PendingApk with UUID, receipt time, metadata, state, detail and installer session ID. The private schema remains stable across updates. A review never expires by age. PakkitApplication cleans unreferenced files at process startup, before any upload exists; activity recreation cannot delete a transfer in progress. Success/removal deletes the staged file. A new upload can replace a terminal installed/failed review. An unfinished review is preserved. Updates require the matching package ID, so dismissed reviews cannot be resurrected.

## Installation

Only a watch action starts ApkInstaller. Returning from granting source permission continues that specific request. The worker verifies source trust, the saved file's digest, current SDK/ABI compatibility and space for the staging copy. It reuses the receipt metadata proven by the digest, rather than repeating archive inspection. Android's installer determines actual installation allocation and verifies the package.

MODE_FULL_INSTALL and USER_ACTION_REQUIRED preserve supported system consent. The non-exported receiver accepts only the active job, matching non-null session ID and expected state. When Pakkit is visible, Android's returned consent intent opens once automatically; Continue is available for recovery. Optional notification denial does not block in-app approval. Native INSTALLED state only follows Android STATUS_SUCCESS.

See [restriction rationale](RESTRICTIONS.md), [platform constraints](PLATFORM.md) and [validation](VALIDATION.md) for the distinction between product behavior and OS/distribution requirements.

## Platform presentation and disclosures

Watch upload progress mutates existing views so rotary and accessibility focus survive updates. AndroidX SplashScreen supplies Wear launch sizing. ReceiveService attaches a Wear OngoingActivity to its foreground notification; actual phase changes schedule a coalesced notification update, with no repeating idle timer. The static indicator and content intent return to the live receiving flow. Notification denial leaves in-app receiving available.

AboutActivity is private and exposes bundled privacy text and third-party notices with receiving off. The web server’s fixed asset allowlist additionally exposes `/privacy.txt` and `/licenses.txt`; those routes reveal no package or access data. The launcher accepts no installer approval extras. A valid private installer callback owns in-memory PendingApproval; after process loss, the watch offers an explicit retry.
