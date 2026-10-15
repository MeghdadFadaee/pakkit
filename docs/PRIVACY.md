# Privacy disclosure draft

Pakkit transfers the APK you select in a browser directly to your Wear OS watch over local Wi-Fi. The browser assets and QR generation are bundled with the watch application. No account, backend, analytics, advertising, remote font or cloud relay is used.

The watch displays an ordinary local address and QR code. A browser requests access and the user approves it on the watch. The watch keeps a random access credential plus the approval decision in process memory. The browser stores the credential in localStorage for Pakkit’s exact origin (scheme, host and port), and sends it automatically only to this app’s API. Browser/device descriptions are inferred from request headers, and the peer's network address is shown for approval. There is no manual key entry. Approvals are cleared when receiving stops or the process ends. Browsers control their own site-storage retention. A stored credential becomes unusable when the watch clears its receiving session, even if it remains in browser storage.

Local HTTP is unencrypted: someone able to observe or alter network traffic may access it. Use Wi-Fi you trust. Watch approval is access control, not encryption.

APKs and package-review metadata are stored in private watch storage. Pakkit reads package/signing metadata and computes a hash to detect corruption or changed staged files. This is not malware certification. Installation requires your watch action and Android’s system consent. Android can independently verify or block packages under its own policies.

Partial/rejected uploads are deleted. Successful installation or dismissing the review deletes the staged file. Pending APKs do not expire by age. Uncommitted files left by process interruption are removed at the next process startup. A new upload may replace an installed or failed review. Pakkit retains one durable review. Backups and device transfer of private app data are disabled. Uninstalling Pakkit removes its private data; installed apps are separate.

Receiving remains enabled until the user stops it or the operating system ends it. Network loss suspends the listener and interrupts an active upload; receiving resumes when Wi-Fi returns. Ending network access does not cancel an already committed Android installation. Process death requires starting receiving and approving browser access again.

Before publication, supply the legal developer identity, support contact and hosted privacy-policy URL. Reconcile the final binary, system behavior and Google Play Data safety declaration. This file is the product disclosure source, not a completed Play submission.

The application includes an offline disclosure under **Privacy** on the watch home screen. The browser serves the same text and open-source acknowledgements from bundled assets. AndroidX profile initialization runs locally; it adds no analytics backend. The hosted publication policy still needs a real support contact. Local HTTP’s compatibility with the final User Data/Data safety assessment must be resolved before publication; transparency about plaintext transport alone is not a compliance determination. See [product review](PRODUCT_REVIEW.md).
