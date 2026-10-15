# Repository Guidelines

## Project Structure & Module Organization

Pakkit is a standalone Wear OS APK receiver and installer. The production module is `app`; Kotlin sources live in `app/src/main/java/meghdad/pakkit/`, grouped into `apk`, `data`, `install`, `transfer`, and `ui`. Android resources are in `app/src/main/res/`; bundled browser files and legal text are in `app/src/main/assets/`. JVM tests use `app/src/test/`, and device tests use `app/src/androidTest/`. `qa/fixture` (`:qaFixture`) supplies a test APK only for instrumentation assets. `web-tests/` contains browser tooling, `scripts/` contains validation helpers, and `docs/` explains architecture, development, and release requirements.

## Build, Test, and Development Commands

Use JDK 21, Android SDK 36.1, and the Gradle wrapper. Run the Android Studio `app` configuration on a Wear OS emulator (API 31+).

- `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lint` builds the debug APK, runs JVM tests, and checks Android lint.
- `./gradlew :app:assembleDebugAndroidTest` builds instrumentation tests and stages the fixture.
- `python3 scripts/test-emulators.py --watch emulator-5554 --skip-build` runs native validation; substitute your emulator serial. Add `--small` for small-screen checks.
- `npm ci --prefix web-tests` and `npm exec --prefix web-tests -- playwright install chromium` prepare browser tests; run them with `npm test --prefix web-tests`.
- `./gradlew :app:bundleRelease` builds the release AAB.

## Coding Style & Naming Conventions

Follow `.editorconfig`: four-space indentation by default, two spaces for HTML/CSS/JavaScript/JSON/YAML, UTF-8, and LF endings. Use standard Kotlin formatting, PascalCase class names, camelCase functions/properties, and descriptive `*Test.kt` test classes. Run `npm run format:check --prefix web-tests`; `npm run format --prefix web-tests` applies Prettier to browser assets and tests.

## Testing Guidelines

Use JUnit 4 for JVM tests, AndroidX JUnit/AndroidJUnitRunner for instrumentation, and Playwright for browser interactions. Add behavioral regression tests near the affected subsystem. No numeric coverage threshold is configured. Follow `docs/DEVELOPMENT.md` for real browser-to-watch transfers. Routine validation uses emulators; generated reports and screenshots stay under ignored `app/build/reports/validation/`.

## Commit & Pull Request Guidelines

Recent commits use concise imperative subjects with `feat:` or `refactor:` prefixes. Follow that pattern where applicable. PRs should describe the behavior change, link relevant issues, report validation performed, and include screenshots for watch or browser UI changes. Keep generated artifacts and signing keys out of commits; follow `docs/RELEASE.md` for signing configuration.
