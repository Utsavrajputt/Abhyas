# Abhyas

**Learn. Practice. Improve.**

Offline-first Android app for NEET (Class 11/12 PCB) and JEE Main (PCM) question practice, with planned NCERT Connect, PDF Studio and optional AI.

See [PROJECT.md](PROJECT.md) for the 3-milestone plan.

## Status
Version 0.2.0. Milestones 1 to 3 are implemented: question engine and practice/exam modes, results and progress, PDF Studio, NCERT Connect, practice made from NCERT text, and optional AI with your own Gemini key. Still needs a device run (not yet measured): large-PDF behaviour, Gemini calls with a real key, quality of generated questions on real NCERT text. See [CHANGELOG.md](CHANGELOG.md) and [PROJECT.md](PROJECT.md).

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
Library tab imports a bank from a zip or a folder (Storage Access Framework) and lists installed banks with update/remove; "Browse" searches and filters (exam, class, subject, chapter, topic, type, difficulty, year, theory/numerical) with Paging 3. Debug builds also offer a clearly labelled `DEMO` bank (`app/src/debug/assets/demo`), never shipped in release. LaTeX renders through `MathText` using offline KaTeX; drop the KaTeX files into `app/src/main/assets/katex/` (see the README there), otherwise plain text is shown. Package images (`images/<file>`, referenced as `imagePath: "images/<file>"`) are copied to app storage on import and shown in practice and results.

## Practice and exams
Home has Quick practice (20 random questions, recent ones avoided) and a Resume button; Create -> Custom practice offers modes (quick, chapter, topic, subject, random, PYQ, theory, numerical, incorrect-only, bookmarked, custom), filters, count and an optional timer; full tests come from JSON templates in `app/src/main/assets/templates/` (duration, sections, marking; edit freely). All modes show answers only after submit. Timed attempts use a wall-clock deadline stored in Room: reopening after it passed auto-submits. Every attempt freezes its questions (shuffled options included) so later bank edits never change past results. Only one attempt can be in progress at a time. 

## Results and progress
After submit the Result screen shows score, accuracy, average time, a breakdown you can group by subject / chapter / topic / difficulty / type / theory-vs-numerical, and a per-question review (your answer, correct answer, explanation, bookmark) filterable by wrong / skipped / correct. The Progress tab has Overview (accuracy, subject, theory vs numerical, difficulty, weak topics), History (open or delete past results), Mistakes (unresolved wrong answers, resolve or retry up to 30) and Saved (bookmarks, practise up to 30). Weak topic = at least 3 answered with accuracy under 60%.

## Known limitations
- Generated NCERT practice is only as good as the book text: scanned books have no text, so nothing can be made from them.
- Book exercises and AI questions are never scored (NCERT prints no answers; AI output is unverified).
- Screenshots are not in the repo yet; add them under `docs/` from a device build.

## PDF Studio (Milestone 2)
Create -> Import PDF. Pick a PDF, set exam/class/subject (and optional chapter, year, answer layout, NCERT numbering). A WorkManager job reads each page with PdfBox-Android and falls back to offline ML Kit OCR for scanned or garbled pages; progress is saved per page so a cancelled/killed job resumes where it stopped. Text is then parsed deterministically (no AI): questions are split by their original numbering, options and the answer key (at the end, or inline `Ans.`/`Sol.`) are matched, and anything unclear is flagged rather than guessed - a missing answer stays `answerVerified=false`.

The review screen lists questions lowest-confidence first with status (OK / no answer / check / duplicate / invalid). Tap one to edit text, options, answer, type, chapter/topic/difficulty, or crop its figure from the page. Bulk actions select/deselect, delete, set chapter, apply *suggested* chapters, attach figures and re-parse. "Import" installs the selected questions as a new bank through the normal transactional importer; "Export zip" writes the same set as an Abhyas package (with `images/`). The report screen lists every problem. Duplicates are found by content hash and word-bigram similarity against your library.

OCR notes: the bundled model reads Latin script; maths and Devanagari in scanned pages need manual correction in the review screen. `tools/pack_bank.py` checksums and zips a hand-made `questions.json` for hosting.

## NCERT Connect (Milestone 3, part 1)
Library -> NCERT Connect. Add a book PDF from your phone, or load a hosted index (a small JSON `{"formatVersion":1,"books":[{"id","title","class","subject","url"(https),"sha256","sizeBytes","note"}]}`) and download from it; downloads resume and are verified against the listed SHA-256. Books are indexed in the background (paragraphs, chapters) so you can read straight away and search once indexing finishes. In the reader: search, bookmarks, tap a paragraph to save or highlight it, chapter progress. Questions can carry `"ncertRef": {"bookId","page","paragraph","textHash","chapter"}`; the result screen shows "In NCERT" only if that book is installed and the passage is found again by its hash.

## Practice from NCERT and AI (Milestone 3, part 2)
Create -> Practice from NCERT (or NCERT Connect -> Practice from your books). Pick a book, optionally a chapter, the question types and a count. Fill-in-the-blank uses sentences that define a term ("... is called X"); true/false uses sentences from the book, some changed to be false by swapping a word for its opposite or one defined term for another (the original sentence is shown in the explanation). Both are made with plain rules, carry an `ncertRef`, and are scored. Book exercises are read from the chapter's "Exercises" section with the PDF Studio parser and are not scored. Assertion-reason questions need AI and are never scored.

Generated questions run as a normal attempt. On the result screen, "Save to bank" keeps them in "NCERT practice (saved)" (Library lists it, so it can be removed) and adds your wrong answers to the mistake notebook. Nothing is saved unless you tap it.

### AI (optional, off by default)
Settings -> AI. Turn it on, paste your own Gemini API key (free keys: aistudio.google.com), pick a model and tap Test connection. No key is shipped in the app. The key is encrypted with an Android Keystore key and is excluded from backup and device transfer, so it must be re-entered on a new phone. Text you send (a paragraph, a page, or a question with its book passage) is processed by Google under your key. With AI off, or on without a key, the app makes no AI request at all (covered by `AiGatewayTest`). AI answers are labelled as unchecked; AI-made questions are flagged unverified and never scored. The `INTERNET` permission is used only for book downloads from an index you provide and for these calls.
