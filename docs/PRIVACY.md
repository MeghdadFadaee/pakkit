# Privacy disclosure draft

Pakkit transfers the APK you select in a browser directly to your Wear OS watch over local Wi-Fi. The browser assets and QR generation are bundled with the watch application. No account, backend, analytics, advertising, remote font or cloud relay is used.

Receiving sessions generate a private URL/key that stays in process memory. Keep the link private and use trusted Wi-Fi. Local HTTP is unencrypted: someone able to observe or alter network traffic may access it. Pakkit removes the key from visible browser history after loading; the browser and operating system still control their own browsing data and behavior.

APKs and package-review metadata are stored in private watch storage. Pakkit reads package/signing metadata and computes a hash to detect corruption or changed staged files. This is not malware certification. Installation requires your watch action and Android’s system consent. Android can independently verify or block packages under its own policies.

Partial/rejected uploads are deleted. Successful installation or dismissing the review deletes the staged file. Pending APKs expire for installation after 24 hours and are cleaned on the next app launch; they may remain in private storage until then. Pakkit retains one durable review. Backups and device transfer of private app data are disabled. Uninstalling Pakkit removes its private data; installed apps are separate.

Sessions end within ten minutes, on Wi-Fi loss or when stopped. Ending network access does not cancel an already committed Android installation. Process death requires a new session and key.

Before publication, supply the legal developer identity, support contact and hosted privacy-policy URL. Reconcile the final binary, system behavior and Google Play Data safety declaration. This file is the product disclosure source, not a completed Play submission.
