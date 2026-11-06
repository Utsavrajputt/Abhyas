# Abhyas

**Learn. Practice. Improve.**

Offline-first Android app for NEET (Class 11/12 PCB) and JEE Main (PCM) question practice, with planned NCERT Connect, PDF Studio and optional AI.

See [PROJECT.md](PROJECT.md) for the 3-milestone plan.

## Status
Foundation scaffold only: Compose + Material 3 theme, 5-tab bottom navigation, Room v1 skeleton (`questions` table), CI workflow. Practice engine, PDF import and NCERT are **not implemented yet** (Milestones 1-3).

## Tech stack
Kotlin, Jetpack Compose, Material 3, Navigation Compose, Room (KSP), DataStore, WorkManager, kotlinx-serialization, Gradle Kotlin DSL (version catalog). minSdk 26.

## Build
```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```
Requires JDK 17 and Android SDK 35.

## GitHub Actions & signing
Workflow: `.github/workflows/android.yml` (tests, lint, debug APK, release APK).

Repository secrets used (names only): `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

- All four present -> `Abhyas-release-signed.apk` (verified with `apksigner`).
- Any missing -> `Abhyas-release-UNSIGNED.apk` and a workflow warning.
- Keystore is decoded into the runner temp dir only; nothing secret is committed or printed.

## Known limitations
Everything beyond the scaffold is pending; see PROJECT.md.
