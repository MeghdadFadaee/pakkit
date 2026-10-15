# Google Play release preparation

This is a release checklist, not an assertion that the app is already approved or ready for a public production rollout.

## Technical artifacts

- Phone and watch use application ID `meghdad.pakkit`; namespaces differ by module.
- Phone version code: 100001; watch: 100002. Increment monotonically and keep codes unique across form factors.
- Compile SDK 36.1; min SDK 31; target SDK 36. Confirm supported-device filtering in Play Console.
- Separate watch artifact declares required `android.hardware.type.watch` and `standalone=false`.
- Release minification and resource-safe platform UI. Worker constructors are kept for background reconstruction.
- Use the **same app-signing certificate** across phone and watch. Different debug/release certificates cannot communicate through the Data Layer. Play App Signing key upgrades must be evaluated on both devices.
- Optional release signing environment: `PAKKIT_KEYSTORE`, `PAKKIT_KEYSTORE_PASSWORD`, `PAKKIT_KEY_ALIAS`, `PAKKIT_KEY_PASSWORD`. Keep the keystore outside the repository. No secret defaults or debug signing for release. Without a complete set of variables, output is unsigned and must be signed through the release pipeline.
- Build with `./gradlew :app:bundleRelease :wear:bundleRelease`, inspect merged manifests and upload each artifact to the proper form factor/test track. Check version code handling in Play Console before promoting.

## Permission declaration

`REQUEST_INSTALL_PACKAGES` exists only in the watch app. Justification for review: the core function transfers a user-selected APK from the paired phone to the user's watch and requests installation after the user explicitly reviews it on that watch. Submit the permissions declaration and an accurate screencast showing selection, transfer, source approval, app review, system approval, cancellation and unsupported-device handling.

Explain in the listing that firmware and package restrictions apply. Do not advertise one-tap installation, compatibility with all Wear OS devices, malware certification, silent installation or guaranteed approval. No packaged third-party APK, in-app self-update, package catalog or download service is included.

`POST_NOTIFICATIONS` is optional and watch-only, used for pending installer consent. Declining it leaves in-app consent usable. WorkManager contributes its standard boot-rescheduling/network-state/wake permissions through manifest merging; its unused foreground service and permission are explicitly removed. Review the final manifest rather than just source manifests. No broad storage, package visibility, location or Bluetooth runtime permissions are requested.

## Listing and disclosures

1. Finalize legal developer identity, support email and public privacy-policy URL. No invented identity or placeholder contact should be published.
2. Publish privacy text based on [PRIVACY.md](PRIVACY.md); confirm SDK behavior and the final Data safety answers. Google Play services may route encrypted APK data through Google's infrastructure. “No Pakkit server” does not mean all transport is local.
3. Upload genuine phone and round-watch screenshots, adaptive launcher assets and required store artwork. The same premium identity should appear in both listings.
4. State Wear OS support and the companion requirement clearly. Reference-device claims require recorded results for the specific model and firmware.
5. Complete content rating, target audience, ads declaration (none), Data safety, external-package permission declaration and Wear OS quality review.
6. Run internal/closed testing, pre-launch reports and physical-device compatibility matrix. Check current target API and developer-verification requirements again.
7. Promote only after every blocker in VALIDATION.md is resolved and the permission declaration is accepted. Google Play approval cannot be guaranteed.
