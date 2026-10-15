# Pakkit privacy disclosure draft

Pakkit transfers an APK that you select in a browser directly to your Wear OS watch over your local Wi-Fi. It does not require an account, companion app, cloud relay, analytics or advertising. The HTML, styles, JavaScript, icons and QR generation are bundled with the watch app; no external font or asset service is contacted.

A short receiving session exposes a private URL and key. Keep that link private and use trusted Wi-Fi. Local HTTP is not encrypted; people able to observe or alter your network traffic may access it. Your browser/operating system may maintain its own local browsing history. Pakkit does not log uploaded filenames, IP addresses, keys or APK contents to a remote service.

The APK is stored in the app’s private watch storage. Pakkit reads package metadata and signing information, checks size/compatibility and computes a hash. This does not certify that a package is safe. Nothing installs until you explicitly request it on the watch and complete Android’s system approval. Android may independently verify the app under its own policies.

Rejected/partial uploads are deleted. Successful installations and dismissed reviews delete the staged APK. Pending packages expire for installation after 24 hours and are cleaned up on the next app launch; they may remain in private storage until then. Local status history holds up to 20 entries of package label, state and timestamp. App backups/device transfer are disabled. Uninstalling Pakkit removes its private data; installed third-party apps are separate.

Sessions end automatically within ten minutes, on Wi-Fi loss or when you stop them. Ending a session closes network access; it does not cancel an already committed system installation.

Before publication, add the legal developer identity, support contact and hosted privacy policy URL. Reconcile the final binary, Android system behavior and Google Play Data safety answers; this draft is not a completed Play declaration.
