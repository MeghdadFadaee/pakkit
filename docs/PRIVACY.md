# Pakkit privacy text — draft for developer review

Pakkit helps you transfer an APK you select on your Android phone to your paired Wear OS watch and request its installation. You decide which file to send and approve installation on the watch.

Pakkit stores the selected APK, package details, file-integrity digest and transfer state in its private app storage. The phone keeps the latest state of up to 20 transfers. You can clear this activity within Pakkit. Temporary package files are removed after completion or expiry (24 hours from selection); cleanup may be delayed by Android background scheduling. Removing Pakkit removes its local app data.

Transfers use the Wearable Data Layer provided by Google Play services. Depending on device connectivity, data may travel over Bluetooth or encrypted cloud transport through Google infrastructure. Pakkit does not operate its own APK server, require an account, contain advertising or include analytics SDKs.

The watch requests permission to ask Android to install a package. It does not silently install applications. Optional watch notifications help you return to a pending system approval screen. You can decline notifications and continue in the app.

Only send software from a source you trust. Pakkit's file checks are not a malware scan or a guarantee of safety or watch compatibility. Android and the watch's security policy control whether installation can proceed.

Before publication, the developer must add its legal identity, effective date, support contact and any required jurisdiction-specific terms to a publicly accessible policy. This repository draft is not itself a published policy or completed Play Data safety declaration.
