# Abhyas

**Learn. Practice. Improve.**

Offline-first Android app for NEET (Class 11/12 PCB) and JEE Main (PCM) question practice, with planned NCERT Connect, PDF Studio and optional AI.

See [PROJECT.md](PROJECT.md) for the 3-milestone plan.

## Status
Foundation scaffold with Material 3 Expressive on every screen: `MaterialExpressiveTheme`, `ShortNavigationBar`, connected `ToggleButton` groups, `FloatingActionButtonMenu`, `LoadingIndicator`, `MaterialShapes`, emphasized typography. Settings (theme mode, dynamic colour, optional AI switch, off by default) persist via DataStore. Room v1 skeleton (`questions` table) and CI workflow in place. Milestone 1 step 1 (question engine core: domain models, validation, Room v2, bank package parser/importer, scoring, option shuffle) is in; UI wiring, practice/exam modes and results follow in steps 2-4. PDF import and NCERT are **not implemented yet** (Milestones 2-3).

## Tech stack
Kotlin, Jetpack Compose, Material 3 Expressive (material3 1.5.0-rc01 pinned over BOM 2025.09.01), Navigation Compose, Room (KSP), DataStore, WorkManager, kotlinx-serialization, Gradle Kotlin DSL (version catalog). minSdk 26.

## Build
```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```
Requires JDK 17 and Android SDK 37.

## GitHub Actions & signing
Workflow: `.github/workflows/android.yml` (same layout as the xmd project). It builds the unsigned release APKs, signs them with `apksigner`, verifies them, and uploads one artifact per ABI:

- `Abhyas-arm64-v8a-<sha>`
- `Abhyas-armeabi-v7a-<sha>`

Repository secrets used (names only): `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

- `KEYSTORE_BASE64` not set (e.g. fork PRs) -> APKs are uploaded as `*-unsigned.apk`.
- `KEYSTORE_BASE64` set but another secret missing -> the build fails.
- Signing happens only in CI; nothing secret is stored in the repo or in Gradle.

## Question-bank package format
`manifest.json` + `questions.json` (JSON array). `sha256` in the manifest is the hex SHA-256 of the raw `questions.json` bytes; `questionCount` must match. Per-question `exam`/`subject`/`class` fall back to the manifest. Answer fields are optional: a question without an answer is imported with `answerVerified=false` and is never scored. Question ids are stored as `<bankId>:<id>`; identical content (type + statement + options) is deduped across banks.

## Known limitations
Everything beyond the scaffold is pending; see PROJECT.md.
