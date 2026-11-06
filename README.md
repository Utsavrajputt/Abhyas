# Abhyas

**Learn. Practice. Improve.**

Offline-first Android app for NEET (Class 11/12 PCB) and JEE Main (PCM) question practice, with planned NCERT Connect, PDF Studio and optional AI.

See [PROJECT.md](PROJECT.md) for the 3-milestone plan.

## Status
Foundation scaffold with Material 3 Expressive on every screen: `MaterialExpressiveTheme`, `ShortNavigationBar`, connected `ToggleButton` groups, `FloatingActionButtonMenu`, `LoadingIndicator`, `MaterialShapes`, emphasized typography. Settings (theme mode, dynamic colour, optional AI switch, off by default) persist via DataStore. Room v1 skeleton (`questions` table) and CI workflow in place. Practice engine, PDF import and NCERT are **not implemented yet** (Milestones 1-3); Create/Quick-practice/Import buttons are UI only.

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

## Known limitations
Everything beyond the scaffold is pending; see PROJECT.md.
