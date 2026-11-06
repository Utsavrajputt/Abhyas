# Abhyas — PROJECT.md

> Learn. Practice. Improve.
> Offline-first Android app for NEET (Class 11/12 PCB) and JEE Main (PCM) practice.

This file is the single source of truth for scope and milestones. Hand **one milestone at a time** to your coding AI (Kimi etc.). Do not start a milestone until the previous one builds in CI.

---

## Fixed decisions (already done in the scaffold)

| Item | Value |
|---|---|
| Package | `com.invictus.abhyas` |
| minSdk / target / compile | 26 / 36 / 37 |
| Stack | Kotlin 2.3.0, AGP 9.1.0, Gradle 9.3.1, compileSdk 37, Compose (BOM 2025.09.01), Material 3 1.5.0-rc01 (Expressive APIs are public only from 1.5), Navigation Compose, Room 2.6.1 (KSP), DataStore, WorkManager, kotlinx-serialization |
| Navigation | Bottom bar: Home, Library, Create, Progress, Settings |
| Signing secrets (names only) | `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` |
| CI | `.github/workflows/android.yml` — builds release APKs, signs with `apksigner`, uploads `Abhyas-arm64-v8a` and `Abhyas-armeabi-v7a` separately (unsigned if secrets missing, clearly named) |

Signing happens only in CI via `apksigner` using the repository secrets (no signing config in Gradle). Never commit keystores or passwords.

## Material 3 Expressive — mandatory UI rules (every milestone)

All UI must use **Material 3 Expressive** (material3 1.5.0-rc01+, `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`). Already in the scaffold: `MaterialExpressiveTheme`, expressive motion scheme, dynamic colour, `ShortNavigationBar`, shape-morphing buttons, `ToggleButton` groups, `MaterialShapes`, emphasized typography.

Rules for new screens:
1. Keep the simple screen header (title + subtitle); do not use large collapsing top app bars.
2. Buttons use `ButtonDefaults.shapes()` / `ToggleButtonDefaults.shapes()`; choice groups use `ToggleButton` or `ButtonGroup`, not plain chips.
3. Use emphasized typography styles for titles and key numbers.
4. Use `MaterialShapes` + `toShape()` for hero/status elements (scores, counts, progress badges).
5. Use `LoadingIndicator` / `ContainedLoadingIndicator` for waiting states (imports, downloads, OCR) instead of a plain spinner.
6. Use `FloatingActionButtonMenu` for multi-action entry points (e.g. Create).
7. Motion comes from `MaterialTheme.motionScheme` (spring-based), no hard-coded tweens.
8. Practice/exam screens stay uncluttered: the question comes first; expressive styling must not reduce readability or contrast.
9. If an Expressive API is missing or unstable, wrap it in one small composable so it can be swapped.

---

## Global rules for every milestone

1. Real functionality, no fake questions, no invented PYQs, no fabricated NCERT page refs.
2. Missing answer in a source -> mark `answerVerified = false`, never guess.
3. AI is always optional; the app must work fully offline.
4. Business logic outside composables (ViewModel + repository + domain).
5. Keep the build green after each step; add unit tests for every engine piece.
6. Follow the **Material 3 Expressive** rules above for all UI.
7. Update README and the checklist in this file when done.

---

## Milestone 1 — Question Engine + Practice + Results (core, offline)

**Goal:** a student can import a question bank JSON, practise, take a timed test, and see results — without any PDF or AI.

### Scope
- **Domain models** with type-specific payloads (sealed classes, validated): Single MCQ, Multi MCQ, Numerical (integer/decimal + tolerance), True/False, Assertion-Reason, Match the Following, Fill in the Blanks, Image-based.
- **Room v1 -> schema**: questions (exists), banks, attempts, attempt_questions (frozen snapshot of order + shuffled options + question content at attempt time), bookmarks, mistakes. Export schemas + a migration test.
- **Question-bank package format**: `manifest.json` (id, version, exam, class, subject, kind `theory|numericals|pyqs|mixed`, sha256 checksum, question count) + `questions.json`. Importer = transactional, checksum-verified, idempotent (dedupe by `contentHash`), reports per-question errors. Physics theory and numericals are separate banks, same engine.
- **Import UI**: pick a package folder/zip via Storage Access Framework; Library tab lists installed banks with version and update/remove.
- **Filtering + search** (DB-backed, paged): exam, class, subject, chapter, topic, type, difficulty, year, theory/numerical.
- **Randomizer**: no duplicates in an attempt, honours filters, independent option shuffling with correct-answer remapping, excludes recently attempted, handles "fewer than requested".
- **Practice modes**: quick, chapter, topic, subject, random, PYQ, theory-only, numerical-only, incorrect-only, bookmarked, custom.
- **Exam mode**: countdown, palette (answered / not answered / marked / answered+marked), prev/next, submit confirmation, auto-submit on timeout, resume after process death, configurable marks and negative marking via **JSON exam templates** (not hard-coded).
- **Results + analytics**: totals, accuracy, score, time per question, subject/chapter/topic/difficulty breakdown, physics theory vs numerical, history, weak topics. **Mistake notebook** and bookmarks with retry.
- **Math rendering**: pick one approach (e.g. a lightweight LaTeX renderer or WebView+KaTeX offline asset) and wrap it in one composable so it can be swapped.

### Suggestions
- Sample *clearly labelled* demo bank only in `androidTest`/debug assets (e.g. `demo_` prefix), never in release.
- Store the question snapshot per attempt so later edits never change past results.
- Use `Flow` + `PagingSource`-style paging for large banks (tens of thousands).

### Acceptance checklist
- [ ] Unit tests: validation, randomizer, shuffle remap, scoring, negative marking, numeric tolerance, dedupe
- [ ] Room migration test, attempt persistence, restart-during-test test
- [ ] Empty bank and invalid package handled gracefully
- [ ] CI green (both ABI APKs built and signed)

---

## Milestone 2 — PDF Studio (import pipeline)

**Goal:** turn user-supplied PDFs into reviewed, verified question banks, deterministic first, no AI needed.

### Scope
- **PDF pick** via SAF; keep source file name + page number on every question.
- **Digital text extraction** (PdfBox-Android or PDFium-based lib; evaluate size/licence) in a WorkManager background job with progress + cancel + resume.
- **OCR for scanned pages** (ML Kit on-device text recognition, fully offline).
- **Parsers** (pure Kotlin, unit-testable): question boundary detection, option extraction (A-D / 1-4 / (a)-(d)), answer-key section extraction, explanation extraction, numerical-type detection, original numbering preserved.
- **Chapter/subject assignment**: user picks defaults per import, per-question override, optional keyword suggestions marked *suggested*.
- **Image/diagram extraction** where feasible: crop region or embedded image saved to app storage, linked to question.
- **Review screen**: list of parsed questions with status (OK / missing answer / low confidence / duplicate), inline edit, bulk actions, import only selected. Missing answers stay `answerVerified = false`.
- **Duplicate detection** against the existing DB (normalised text hash + similarity).
- **Export** reviewed set as an Abhyas bank package (`manifest.json` + `questions.json`) so the same content can be shipped as an update without rebuilding the APK.
- **Validation + error report** screen; interrupted import must resume or roll back cleanly.

### Suggestions
- Build a small **fixtures folder** of real sample PDFs *you* provide; write parser tests against them. Do not invent sample questions.
- Keep a `confidence` score per parsed question and sort the review list by lowest confidence first.
- Add a desktop-side script (`tools/pack_bank.py`) to checksum + zip packages for GitHub Releases / hosting.

### Acceptance checklist
- [ ] Parser unit tests on real fixture PDFs (digital + scanned)
- [ ] Invalid PDF, missing answers, interrupted import tests
- [ ] Large PDF (hundreds of pages) does not block UI or OOM
- [ ] Exported package re-imports identically (round-trip test)

---

## Milestone 3 — NCERT Connect + Optional AI + Release polish

**Goal:** textbook reading linked to questions, optional AI helpers, and a release-ready build.

### Scope
- **NCERT Connect**: Class 11/12 x Physics/Chemistry/Botany/Zoology library; downloadable book packages (manifest + checksum) from **official or explicitly permitted** sources only; offline PDF reader; chapter navigation; in-book search; page bookmarks; saved passages; reading progress per chapter.
- **Stable passage index**: `(bookId, page, paragraphIndex, textHash)` instead of raw line numbers.
- **Question -> NCERT reference** card (book, class, subject, chapter, page, paragraph, highlight) and **"Find in NCERT"** deep link that opens the page and highlights the passage. No reference if none is verified.
- **Practice from NCERT**: pick chapter/pages, generate by mode (line-based, concept, statement, assertion-reason, diagram, exercise-based, mixed) — rule-based extraction first (exercises, bold terms, fill-blanks); AI optional.
- **Optional AI layer**: provider interface (`AiProvider`), structured JSON output, schema validation, everything saved as **unverified** until reviewed, global off switch, graceful offline/error handling. **No API key in the APK**: user enters their own key (stored encrypted) or points to their own proxy.
- AI features: explain answer / wrong options, simplify concept, short revision notes, grounded Q&A over a selected PDF or chapter, generate questions.
- **Polish**: light/dark/system theme in Settings, accessibility pass, empty/error states, app icon, README screenshots, versioned changelog, release signing verified in CI.

### Suggestions
- Check NCERT redistribution terms before bundling any PDF; default to link-out/download-on-device.
- Keep AI behind a single `Settings -> AI` screen with a clear "unverified" badge in the question UI.
- Add in-app **bank/book update checker** (WorkManager) that reads a small JSON index from your GitHub Releases.

### Acceptance checklist
- [ ] Reader opens correct page + highlights passage from a question reference
- [ ] AI disabled = zero network calls (test)
- [ ] Generated questions validated and flagged unverified
- [ ] Signed release APK verified in CI, README complete

---

## Prompt template for your coding AI

```
Repo: Abhyas (Android, Kotlin, Compose, Room). Read PROJECT.md and README.md first.
Implement ONLY "Milestone N". Follow "Global rules". Keep CI green.
Work step by step, write tests with each feature, update checklist items in PROJECT.md when done.
Do not touch signing secrets or commit keystores.
```
