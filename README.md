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

## Library, search and math
Library tab imports a bank from a zip or a folder (Storage Access Framework) and lists installed banks with update/remove; "Browse" searches and filters (exam, class, subject, chapter, topic, type, difficulty, year, theory/numerical) with Paging 3. Debug builds also offer a clearly labelled `DEMO` bank (`app/src/debug/assets/demo`), never shipped in release. LaTeX renders through `MathText` using offline KaTeX; drop the KaTeX files into `app/src/main/assets/katex/` (see the README there), otherwise plain text is shown. Image files referenced by `imagePath` are not extracted from packages yet.

## Practice and exams
Home has Quick practice (20 random questions, recent ones avoided) and a Resume button; Create -> Custom practice offers modes (quick, chapter, topic, subject, random, PYQ, theory, numerical, incorrect-only, bookmarked, custom), filters, count and an optional timer; full tests come from JSON templates in `app/src/main/assets/templates/` (duration, sections, marking; edit freely). All modes show answers only after submit. Timed attempts use a wall-clock deadline stored in Room: reopening after it passed auto-submits. Every attempt freezes its questions (shuffled options included) so later bank edits never change past results. Only one attempt can be in progress at a time. 

## Results and progress
After submit the Result screen shows score, accuracy, average time, a breakdown you can group by subject / chapter / topic / difficulty / type / theory-vs-numerical, and a per-question review (your answer, correct answer, explanation, bookmark) filterable by wrong / skipped / correct. The Progress tab has Overview (accuracy, subject, theory vs numerical, difficulty, weak topics), History (open or delete past results), Mistakes (unresolved wrong answers, resolve or retry up to 30) and Saved (bookmarks, practise up to 30). Weak topic = at least 3 answered with accuracy under 60%.

## Known limitations
Everything beyond the scaffold is pending; see PROJECT.md.
