# Repository Guidelines

## Product Direction

Pakkit is a standalone, watch-first Wear OS APK receiver and installer. A browser on another device transfers a compatible APK to the watch; the user reviews it and approves installation on the watch. Preserve this complete journey. Do not introduce a companion phone app, accounts, cloud relay, or frontend build pipeline without a demonstrated product need.

- Product name: **Pakkit**. Official tagline: **Pack it. Send it. Wear it.** Use it consistently in appropriate branding contexts.
- Treat Pakkit as a public consumer product, never an internal tool or a personalized prototype. Do not expose architectural history, debugging terminology, or development instructions in product copy.
- Make UI/UX a primary engineering concern. The watch and browser are equally important product surfaces. Use short, natural copy, clear actions, thoughtful feedback and complete loading, empty, success and failure states.
- Preserve the playful but sophisticated identity: electric purple, mint/cyan accents, rounded geometry, elegant typography and the package-inspired mark. Wear backgrounds are black; the browser supports light and dark themes.
- Implement visuals as real assets and components. Support circular displays, scaled text, accessible contrast, 48 dp watch touch targets, rotary input, native swipe dismissal and keyboard/screen-reader access in the browser.
- Keep motion smooth, finite and respectful of reduced-motion settings. Avoid decorative render loops, repeated screen reconstruction during progress, and unnecessary battery use.

## Project Structure & Module Organization

The production module is `app`; Kotlin sources live in `app/src/main/java/meghdad/pakkit/`, grouped into `apk`, `data`, `install`, `transfer`, and `ui`. Android resources are in `app/src/main/res/`; bundled browser files and legal text are in `app/src/main/assets/`. JVM tests use `app/src/test/`, and device tests use `app/src/androidTest/`. `qa/fixture` (`:qaFixture`) supplies a test APK only for instrumentation assets. `web-tests/` contains browser tooling, `scripts/` contains validation helpers, and `docs/` explains architecture, development, validation and release requirements.

Prefer the smallest maintainable solution. Consolidate duplicated logic, remove dead code and unused resources, and avoid speculative abstractions or dependencies. Keep changes focused and preserve unrelated work. Browser tooling and generated fixtures must never ship in production artifacts.

## Receiving, Installation & Security

- Keep the receiving port fixed at **8787**. Report port conflicts clearly; do not silently choose another port.
- Receiving stays enabled until the user stops it or the OS ends it. Handle Wi-Fi loss/reconnection and activity lifecycle changes gracefully. Process death must not silently reopen receiving or restore browser authorization.
- Do not add arbitrary session/file expiry, APK size caps, total upload deadlines, or manual authentication keys. A restriction needs a concrete platform, resource or security reason; document that reason in [docs/RESTRICTIONS.md](docs/RESTRICTIONS.md).
- Preserve unobtrusive watch approval for browser access, origin-scoped credentials, Host/Origin checks, bounded request resources and stalled-I/O protection. These are distinct from arbitrary limits on active transfers.
- Stream files with bounded buffers. Check actual storage, clean partial files, retain recoverable reviews, and handle interrupted transfers and persistence failures without data loss. Do not load entire APKs into memory.
- Only an explicit watch action may initiate installation through supported Android APIs. Preserve source permission, required system consent, file-integrity checks and session-matched callbacks. Never add silent/browser-triggered installation, ADB/root workarounds or permission bypasses to the product. Pakkit self-updates go through Google Play.
- Local HTTP is unencrypted. Do not describe browser approval as encryption or promise universal APK compatibility, malware safety or guaranteed Play acceptance. Keep privacy disclosures accurate when behavior changes.
- Validate critical platform capabilities before committing to an architectural change. Recheck current official Android, Wear OS and Google Play sources for platform/policy decisions; do not infer installability from successful file transfer.

## Build, Test, and Development Commands

Use JDK 21, Android SDK 36.1, and the Gradle wrapper. The app currently has min SDK 31 and target SDK 36; verify the build configuration before changing either. Run the Android Studio `app` configuration on a Wear OS emulator (API 31+).

- `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lint` builds the debug APK, runs JVM tests, and checks Android lint.
- `./gradlew :app:assembleDebugAndroidTest` builds instrumentation tests and stages the fixture.
- `python3 scripts/test-emulators.py --watch emulator-5554 --skip-build` runs native validation. Substitute actual emulator serials; add `--small` for 192 dp / 1.3x font checks.
- `npm ci --prefix web-tests` and `npm exec --prefix web-tests -- playwright install chromium` prepare browser tests; run them with `npm test --prefix web-tests`.
- For real browser-to-watch transfers, initialize Chrome on a separate Android emulator and add `--browser emulator-5556`. Generate a large fixture with `python3 scripts/create-large-fixture.py`; add `--large --restart` to the transfer runner for large-file and process-recovery validation.
- `./gradlew :app:bundleRelease :app:lintDebug :app:lintRelease` builds and checks release output. Then run `python3 scripts/check-release.py` to audit packaging, the merged manifest, assets and signing presence.

Follow [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) for environment setup, supported options and the optional Maven mirror. Do not hardcode a machine-specific SDK/JDK path into project configuration.

## Coding Style & Naming Conventions

Follow `.editorconfig`: four-space indentation by default, two spaces for HTML/CSS/JavaScript/JSON/YAML, UTF-8, and LF endings (batch files use CRLF). Use standard Kotlin formatting, PascalCase class names, camelCase functions/properties, and descriptive `*Test.kt` test classes. Run `npm run format:check --prefix web-tests`; `npm run format --prefix web-tests` applies Prettier to browser assets and tests.

Keep watch UI strings in Android resources and share branding/design primitives where useful. Browser assets remain readable HTML/CSS/JavaScript. Show actionable, friendly errors to users; keep raw exceptions, paths and platform diagnostics in development logs. Never log or commit credentials, pairing codes, signing secrets or APK contents.

## Testing Guidelines

Use JUnit 4 for JVM tests, AndroidX JUnit/AndroidJUnitRunner for instrumentation, and Playwright for browser interactions. Add behavioral regression tests near the affected subsystem when they protect meaningful behavior; avoid tests that merely mirror implementation or unnecessary tests for documentation-only edits. No numeric coverage threshold is configured.

- Use Wear OS and Android emulators for routine development. Do not request physical Galaxy Watch access to unblock normal work. Record hardware-specific limits and continue independent validation.
- Always select devices explicitly. Validation scripts accept emulator serials only. When the user explicitly requests a physical-watch installation or final device validation, identify the actual watch, preserve app data for updates, and do not substitute an emulator or reinstall destructively.
- Exercise affected journey states, including denied access, interruption/retry, insufficient storage, large files, lifecycle/background behavior, process recovery and required installation consent. Grant installation-source trust through the normal system UI; do not mask missing consent with shell grants.
- Validate watch UI changes on large and small circular layouts with increased font size. Inspect settled screenshots, not only view-tree assertions. Validate browser changes across responsive widths, both themes, keyboard focus and reduced motion.
- Distinguish mocked browser UI coverage from real browser-to-watch transport and real system installation. A successful upload proves receipt, not installation. Skipped assumptions are incomplete validation, not passes.
- Run automation sequentially on each emulator: concurrent instrumentation and shell `uiautomator` can conflict. Restore temporary display/font settings and remove test forwards, partial files and disposable processes you created.
- Run checks appropriate to the change and required release checks when relevant. Once they pass, repeat or broaden testing only for new changes, failures or unresolved concerns. Generated reports and screenshots stay under ignored `app/build/reports/validation/`.

Keep [docs/VALIDATION.md](docs/VALIDATION.md) current with actual evidence and a concise remaining hardware/browser/runtime checklist. Do not claim measured battery life, full TalkBack/device coverage or minimum-API compatibility from lint or a single emulator image.

## Working Style & Release Boundaries

Make routine technical, design and copy decisions independently. Implement and validate authorized work rather than stopping at an audit, plan or suggestions. Ask only when essential information, a real blocker or authorization outside the requested scope requires it; explain the concrete reason. Give concise progress updates and a self-contained final report of changes, validation and remaining limits.

Keep architecture, privacy, development and release documentation aligned with the implementation. Clearly distinguish implemented behavior, automated/emulator verification, hardware validation still required and Google Play review dependencies. Use [docs/PRODUCT_REVIEW.md](docs/PRODUCT_REVIEW.md) and [docs/RELEASE.md](docs/RELEASE.md) for publication gates.

Never claim publication readiness from a successful build alone. Check current target API/Wear requirements, restricted installation and foreground-service declarations, privacy/Data safety and transport handling, store assets, developer identity and signing. Do not invent support contacts, hosted policies or credentials, or publish an unsigned artifact. Keep generated artifacts and signing keys out of commits.

## Commit & Pull Request Guidelines

Use concise imperative Conventional Commit subjects, such as `feat:`, `fix:` or `refactor:`, matching the actual change. When asked for a commit message, return the message; that request alone does not authorize committing or pushing. Do not commit, push or publish without authorization.

PRs should describe the resulting behavior for a reviewer without conversation context, link relevant issues where available, report validation performed, and include screenshots for watch or browser UI changes. Follow [docs/RELEASE.md](docs/RELEASE.md) for signing configuration.
