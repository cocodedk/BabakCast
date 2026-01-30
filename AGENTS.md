# Repository Guidelines

## Project Structure & Module Organization
- `app/` — Android application module.
  - `app/src/main/java/com/cocode/babakcast/` — Kotlin source (Compose UI, domain, data).
  - `app/src/main/res/` — Android resources (layouts, drawables, strings).
  - `app/src/test/` — JVM unit tests.
  - `app/src/androidTest/` — Instrumented/UI tests.
- `docs/` — Product notes and documentation assets.
- `website/` — Static site for the project landing page.
- `scripts/` — Utility scripts (e.g., git hooks install).
- `.githooks/` — Pre-commit hook definitions.

## Build, Test, and Development Commands
- `./gradlew assembleDebug` — Build a debug APK.
- `./gradlew installDebug` — Install the debug APK on a connected device/emulator.
- `./gradlew test` — Run JVM unit tests.
- `./gradlew connectedAndroidTest` — Run instrumented tests on a device/emulator.
- `./gradlew assembleRelease` — Build a release APK (requires signing env vars).
  - Required env vars: `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

## Coding Style & Naming Conventions
- Kotlin + Jetpack Compose; keep style consistent with existing files under `app/src/main/java`.
- Indentation: 4 spaces; braces on the same line; one statement per line.
- Naming: `PascalCase` for classes/composables, `camelCase` for functions/vars, `UPPER_SNAKE_CASE` for constants.
- Packages follow `com.cocode.babakcast.*`.

## Testing Guidelines
- Unit tests live in `app/src/test` using JUnit.
- Instrumented/UI tests live in `app/src/androidTest` (AndroidX/Espresso/Compose test APIs).
- Test file naming: `*Test.kt`.
- Enable the pre-commit hook to run tests automatically:
  - `./scripts/install-hooks.sh` (or `git config core.hooksPath .githooks`).
  - The hook runs `./gradlew test` and a signed `assembleRelease` when signing vars are set.

## Commit & Pull Request Guidelines
- Commit messages are short, imperative, sentence case (no prefixes observed).
  - Examples: “Refactor signing configuration…”, “Add footer text…”.
- PRs should include a concise summary, testing performed, and screenshots for UI changes.
- Update `docs/` or `website/` when user-facing behavior changes.

## Security & Configuration Notes
- Do not commit real keystores or API keys. Use environment variables for signing.
- `local.properties` is machine-specific (Android SDK path) and should remain local.
