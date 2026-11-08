# Changelog

All notable changes to Abhyas. Versions follow `versionName` in `app/build.gradle.kts`.

## [Unreleased] - xmd-style UI

### Changed
- Navigation: xmd-style floating pill bar (Home, Library, Progress) with a separate round Create (+) button. Settings moved to the top bar.
- Top bar on every screen like xmd: flat emphasized title, dark/light toggle (also on title tap) and Settings gear; sub-screens show a back arrow.
- Icons: Material Symbols (rounded, filled), the same set xmd uses (`icons-material-symbols` 2.2.1).
- Themes: xmd's picker with Aurora (default), Amethyst, Catppuccin, Nord, Tokyo Night and Rosé Pine, each with light, dark and AMOLED schemes, plus xmd's circular reveal when the theme changes. Replaces System/Light/Dark + dynamic colour. An earlier explicit Light/Dark choice is kept.
- Settings: xmd-style root with Appearance, AI, NCERT & data and About sub-screens.
- About: xmd's About (hero, Libraries, GitHub, support, GitHub contributors, licence) without the update check.
- Space Grotesk headings and xmd corner radii.
- The round + button opens the create options (Custom practice, Practice from NCERT, Import PDF, Import question bank) in a menu; the Create screen is gone.
- Top bars draw behind the status bar (fixes the gap above the title).
- Home redesigned around the app-wide exam: big NEET / JEE Main cards, exam hero card with question count, practice by subject, quick practice, resume.
- Settings > Exam: NEET or JEE Main. Home, Library (banks), PYQ store and the default Browse / Custom practice filter follow it. Progress still shows all attempts.

### Added
- Previous year papers store (package icon in the Library top bar): NEET 2016-2025 as one downloadable question bank per year, installed through the normal importer after a SHA-256 check. Source is an HTTPS `index.json` set in Settings > NCERT & data (build it with `tools/build_pyq_index.py`).

## [0.2.0] - Milestone 3 (NCERT Connect, optional AI, polish)

### Added
- NCERT Connect: add book PDFs from the phone or a hosted HTTPS index (resumable, SHA-256 verified), background text index, reader with search, bookmarks, saved passages and per-chapter progress.
- Question to NCERT references (`ncertRef`) shown only when the passage is found again by its hash, with an "Open in book" link.
- Optional AI (off by default) with your own Gemini API key: Explain / Simplify / Ask on a paragraph, Notes / Ask on a page, "Explain with AI" on a reviewed question (grounded in the book passage when available).
- Practice from NCERT: fill-in-the-blank and true/false made from the book text by rules (scored), book exercises (not scored), AI assertion-reason (not scored).
- "Save to bank" on the result screen: keeps generated questions in "NCERT practice (saved)" so bookmarks, the mistake notebook and retry work for them. Never automatic.
- Accessibility: question palette cells and the timer are announced with their state.
- `AI-written - unverified, not scored` badge on AI questions in practice and review.
- Unit tests for the AI gateway (AI off or no key means no provider call), Gemini request/response handling, prompts, generators and parsers.
- CI warns on `main` when APKs are not signed.

### Security and privacy
- API key is AES-GCM encrypted with an Android Keystore key and excluded from backup and device transfer. The key goes in a request header, never in a URL, and is scrubbed from error messages.
- `INTERNET` permission added (book downloads from an index and the optional Gemini calls). With AI off, no AI request is ever made.

### Changed
- Room schema v4 (migration 3 to 4 added in Part 1).

## [0.1.0] - Milestones 0 to 2
- Foundation: Material 3 Expressive shell, Room, DataStore settings, CI with `apksigner` signing.
- Question engine: validated models, bank packages (manifest + checksum), scoring, option shuffle, dedupe by content hash.
- Practice and exam modes, timed attempts that survive process death, results with breakdowns, progress, mistake notebook and bookmarks.
- PDF Studio: digital extraction and offline OCR, deterministic parsers, review screen, import and export as a package.
