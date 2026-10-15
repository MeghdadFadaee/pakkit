# Product restrictions and their reasons

Pakkit should finish useful work, not stop because a product timer or file-size preset ran out. This audit covers receiving, browser access, files, lifecycle, installation and resource use.

## Removed or redesigned

| Previous behavior | Current behavior |
| --- | --- |
| Receiving ends after ten minutes | No app-imposed session deadline. Receiving stays on until stopped or the OS ends it. |
| Wi-Fi loss ends receiving | The listener closes, interrupted uploads are discarded, and the service waits for Wi-Fi. It binds again automatically. Browser approvals survive a rebind in the same service. |
| A new port for every session | Port **8787**, including after restarts. A genuine port collision is reported rather than silently changing the address. |
| URL secrets and manual key entry | A normal address and QR code. The browser requests access automatically; the watch approves it once per receiving session. A session-only credential stored in browser-origin storage handles subsequent requests and reloads. No keys appear in the interface, address or history. |
| 50 MiB APK limit | No application size ceiling. Positive 64-bit lengths, streaming, available storage and Android's installer determine what fits. |
| Five-minute browser upload deadline | No total XHR deadline. An upload can continue as long as data keeps arriving. |
| 24-hour expiry of a received APK | A pending review remains available until removed or installed. Process-start cleanup only deletes uncommitted/orphaned files. |
| Three-times-file-size reserve plus 20 MiB | Upload checks the bytes needed for the incoming file. Installation checks space for its staging copy; Android handles actual extraction/allocation failures. No multiplier or arbitrary reserve. |
| ZIP entry count, individual-entry, expanded-total and manifest-size caps | No preset archive caps. Inspection reads ZIP directory metadata and Android package metadata without unpacking all assets or loading the APK into memory. Runtime/parser limits remain real hardware constraints. |
| Rejection based on the browser filename | File extension is a picker hint, not an acceptance rule. The watch validates actual APK content. Empty files still cannot be installed. |
| Private IPv4 address required | Wi-Fi IPv4 or reachable, unscoped IPv6 addresses. Equivalent IPv6 spellings are accepted by the Host/Origin checks. |
| Repeated archive inspection at install | One inspection at receipt. A streamed digest verifies that those bytes are unchanged before installation; current SDK/ABI checks and Android verification still apply. |
| Separate Continue tap for normal system consent | A visible Pakkit activity opens Android's returned approval intent once. Continue remains a recovery action, not an obligatory extra step. Returning from granting source permission continues the requested installation. |
| Clear a finished/failed review on the watch before sending another | The browser can choose another app immediately. A new upload replaces terminal reviews. |
| Watch-layout warning based on a missing optional feature flag | Removed. Absence of that declaration does not prove the app will fail or have an unsuitable layout. |
| Duplicate trust warnings in the review | Installation remains an explicit decision, without repeating warnings at each step. Connection privacy details remain accessible in the browser. |

## Retained protections

| Protection | Concrete reason |
| --- | --- |
| Start/Stop receiving and foreground-service visibility | User control over a network listener and its battery/network use. No boot start or automatic restart after process death. |
| Watch approval for a new browser | An address on a shared network is not authentication. Nearby devices must not be able to upload unsolicited files. Browser descriptions are hints; they are not verified identities. |
| Revocation on Stop/process death | Explicitly closing receiving must revoke access. No trust survives a new receiving service. |
| One pending browser request | An unauthenticated peer cannot allocate an unbounded request list or replace the prompt under consideration. Each additional decision record requires a watch action. Existing approvals are not evicted by a time or browser-count quota. |
| Literal Host and same-origin POST checks; no CORS; origin-scoped credentials; CSP | Protects against DNS rebinding, cross-site submissions and unnecessary script access to credentials. IPv4/IPv6 normalization does not permit arbitrary hostnames. |
| 8 KiB headers, 4 KiB lines, unique headers, exact Content-Length | Bounds unauthenticated parsing memory and prevents ambiguous framing, request smuggling and unbounded body reads. Browser File/XHR supplies a length; chunked/Expect/pipelined bodies are not part of this endpoint. |
| Five-second header parsing budget and 15-second read inactivity timeout | Abandoned or deliberately stalled sockets must not occupy the watch's small worker pool forever. These measure stalled request I/O, not receiving-session age or total transfer duration. A slow but continuously progressing upload has no total deadline. |
| Five-second browser status probe | Detects an unreachable receiver and retries automatically. It neither cancels uploads nor expires access. |
| Two workers, four queued sockets, one active upload | Bounds threads, open sockets, concurrent disk writes and CPU wake ownership on the watch. Extra connections retry; no package size limit follows from this resource bound. |
| One unfinished package review | Prevents a new upload from overwriting an app currently under review or installation. Finished/failed reviews do not block the next upload. This is Pakkit's bounded workflow, not an Android package-count restriction. |
| Complete, readable, signed APK; SDK/processor checks | A partial/split APK cannot be installed alone by this single-APK flow, and Android rejects incompatible or unsigned packages. Package inspection is not malware certification. |
| Private UUID paths, digest check, matched installer callback/session | Prevents traversal, changed staged files and stale/forged callbacks changing the current review. |
| Pakkit self-updates use Google Play | The [REQUEST_INSTALL_PACKAGES policy](https://support.google.com/googleplay/android-developer/answer/12085295?hl=en) restricts self-update use. Removing this check would compromise the intended distribution model. |
| Source approval, explicit watch install action and system consent | Supported [PackageInstaller](https://developer.android.com/reference/android/content/pm/PackageInstaller) behavior and user control. Browser access never authorizes installation. |
| Minimum API 31 | The implementation explicitly uses USER_ACTION_REQUIRED and other API 31 native behavior. Supporting older systems would require a separately tested compatibility path, not deleting a guard. |

## Actual limits and recovery

No arbitrary byte ceiling does not mean infinite capacity. Android file lengths and installer sizes use signed 64-bit values; actual disk capacity, package format, parser memory, firmware security policy and available system installation UI remain authoritative. Storage checks are snapshots: another app can consume space afterward. ENOSPC failures produce a storage recovery message and partial files are deleted.

The worker hashes and writes using a 64 KiB buffer. APK assets are not inflated in memory. CPU wake ownership is limited to an actual upload/inspection and released in finally; idle receiving holds no CPU wake lock. Wi-Fi is requested for as long as receiving is enabled. Long screen-off availability, firmware power management, thermal behavior and battery consumption still require hardware measurement. The OS may suspend or stop background execution; Pakkit does not promise to defeat that behavior. See [Wear networking](https://developer.android.com/training/wearables/data/network-communication) and [foreground-service types](https://developer.android.com/develop/background-work/services/fgs/service-types#connected-device).

Partial uploads restart from the beginning, with the selected browser File retained for retry. Byte-range resume is not implemented. Link-local IPv6 addresses requiring a client-specific zone identifier cannot be advertised portably; the receiver prefers IPv4, then unscoped IPv6. Wi-Fi isolation, firewalls, browser permissions and address changes are network/platform constraints. The port is fixed; the router still assigns the watch's IP address.

HTTP remains unencrypted. Watch approval and credentials do not defend against a party able to read or alter LAN traffic. The product is for trusted networks; no remote relay, router port-forwarding feature or internet exposure is configured by Pakkit. The browser's connection details explain this without an additional confirmation dialog.

Credentials are origin-scoped rather than cookies: [HTTP cookies share a host across ports](https://www.rfc-editor.org/rfc/rfc6265#section-8.5), while [Web Storage is origin-isolated](https://html.spec.whatwg.org/multipage/webstorage.html). Another app serving a different port on the watch must not inherit Pakkit access. The bundled script uses automatic Authorization headers only for its relative API requests; no credential appears in the address or interface. Local storage is not encryption and remains readable by same-origin script, which is why external scripts, inline script and untrusted HTML rendering are excluded.
