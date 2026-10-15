# Architecture and operating boundaries

## Protocol v1

Google Play services enforces matching app ID and certificate. The watch advertises `pakkit_watch_v1`. A reachable node is queried using `/pakkit/probe` with a random nonce. The watch publishes `/pakkit/cap/{node}` with that nonce, API, ABIs, available bytes and installation controls. The phone accepts only the selected node's fresh response. Capabilities expire for send purposes after two minutes.

A selected document is copied from its content URI into app-private storage. The temporary filename is a generated UUID; URI-supplied paths and display names never determine a filesystem path. SHA-256 is computed while enforcing the size cap. The original document is not modified. Persisted URI permission and broad storage access are unnecessary.

`SendWorker` publishes `/pakkit/offer/{UUID}`, containing target node, creation time, APK metadata, digest and an Asset. This publication means queued for sync, not delivered. Google Play services owns transport and retransmission; it can route through Google infrastructure. There is no percentage or speed estimate. There is no automatic installation.

`LinkService` schedules unique `ReceiveWorker` work. The worker checks target, identifier and age; rejects a busy watch; bounds the received bytes; verifies digest and parsed package information; and renames the complete private file. Readiness is sent only after verification. The watch keeps a small recent-ID replay guard. Terminal and ready states are idempotent for a given active transfer. Assets are deleted after verification or a permanent failure, rather than retained as storage.

The watch publishes `/pakkit/status/{UUID}` with target phone and an explicit state. The phone only accepts the current transfer ID from its selected destination node and monotonically increasing status revisions. A status publication worker retries temporary API errors. Phone and watch hold one current job each, plus phone-local history capped at 20 entries. SharedPreferences commits are used for the small durable records; files are not placed in preferences or WorkManager input. WorkManager persists bounded jobs and handles process restart.

## Installation boundary

Only a foreground button in the watch app creates a PackageInstaller session. The file's age, digest, metadata and hardware compatibility are checked again. The session requests `USER_ACTION_REQUIRED`. The status receiver is not exported. Its explicit mutable PendingIntent is scoped to the session and transfer ID; mutable extras are required for Android's installer result.

Pending approval is presented inside Pakkit, with an optional notification containing the system confirmation Intent. No background activity launch is attempted by the receiver. Notification permission is optional and requested only from an explanatory watch button. If approval state is interrupted and cannot be recovered in memory or from its notification, the user can explicitly retry, abandoning the previous session first. A transfer can only be reported as installed after `STATUS_SUCCESS`.

The system owns final package validation, update signatures, policy checks, verification and approval UI. No install result is inferred from a file transfer, countdown or launch attempt. Installation failures are mapped to understandable messages without logging APK contents or private paths.

## Storage and battery

- 50 MiB maximum package, 64 KiB buffers, no entire APK byte arrays, no ZIP extraction.
- Approximate watch space budget: three APK sizes plus 20 MiB. This is a preflight, not a reservation; the system may still report insufficient storage.
- TTL: 24 hours from file selection. Workers remove retained offers/files when scheduled; background scheduling can be deferred by the OS. Stale files are additionally cleaned on app startup and after receive.
- One outstanding offer per phone, one reviewable file per watch; no parallel sync queue. Keep the watch app open for the most predictable receiving experience.
- No always-on service, app-managed wake lock, polling scan loop, custom networking, analytics or account system.
- The parcel artwork uses a short finite entrance, respects system animation and battery saver settings, and stops when its window is hidden or detached. Decorative watch artwork remains static. See the semantic palettes and components in Design.kt and the brand guide in design/README.md.

## Known engineering limits before production

Native Android views keep the runtime and dependencies small. Touch controls and text remain semantic views; the parcel art is decorative. The first locale is English; RTL mirroring uses platform layouts but localized copy and real RTL QA remain release work. Ordinary refreshes preserve scroll position. The watch returns to the heading when its transfer stage changes so the next decision is visible.

Single-file inspection does not fully classify all unusual/vendor split manifests, malicious parser edge cases or expanded installed size. Android remains the security authority. Asset transfer latency, cloud behavior, retry timing, UI interruption and consent recovery require paired-device testing. The initial implementation intentionally excludes automatic recovery that might open an installer without another user action. Cross-device cancellation of an already queued offer is deferred; current jobs can be dismissed on the watch after receipt or expire.
