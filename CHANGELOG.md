# Changelog

All notable changes to Abhyas. Versions follow `versionName` in `app/build.gradle.kts`.

## [Unreleased] - xmd-style UI

### Chapter-zip books and "Page N" on answers
- Packages > Online: a book entry may point to a zip of NCERT chapter PDFs (`keph101.pdf` ... `keph1ps.pdf` prelims, `keph1a1.pdf` appendices, `keph1an.pdf` answers). The app verifies the SHA-256, merges the files in book order and reads every chapter name from the first page (Contents lists Prelims, Ch 1..N, Appendices, Answers).
- Pages show the number printed in the textbook (read from the page headers; roman numerals in the prelims). The Reader header, Contents, bookmarks and search show it, with the PDF page beside it in the header.
- Result review: every question shows "Page N" and Open in book. The stored reference is used when it still resolves; otherwise the question text is searched in the installed books of its subject. Uncertain matches say "likely", no match shows nothing. Prelims, appendices and the answer key are not searched.
- tools/build_index.py indexes chapter zips (meta: type=book, subject, class).

### Library round 2
- Library shows the newest 7 papers (unfinished first) with View all, which opens a separate screen with dot filter chips (All, subject, PYQ / Theory, Completed, coaching). Search moved to the top bar, NCERT Connect to the Create (+) menu, new Packages icon.
- Books on the shelf show a first-page thumbnail (cached).
- Paper cards: Resume / Start / Retake on the right, PDF export beside it. A paper counts as completed when its test is submitted; an unsubmitted one is shown first in Library and as a Continue card (with progress) on Home. The manual completed icon is gone.
- Paper names are cleaned at import (`NEET_2016_Phase_1_Question_Paper_with_Solutions_Code_A_P_W_` becomes `NEET 2016 Phase 1 (Code A)`); already installed papers are shown cleaned too.

### Library and Packages
- Library: books shelf per subject on top, then question papers; each paper has Start, Save as PDF (to Downloads, with answer key) and a mark-as-completed icon. Sections only show when they have content.
- Filter sheet: Subject, PYQ / Theory, Completed, Coaching (coaching comes from the package index).
- Downloads became Packages with Offline (import zip, PDF, folder, installed banks and books) and Online (packages from every link, with name, description, version, Download / Update).
- Settings > NCERT & data: new Other links (any number of index.json links or GitHub repo links; the app fills the name and packages from the index).
- tools/build_index.py builds the index.json (packages list) from a folder of bank zips; old `packs` and `books` indexes still work.

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

### Changed (round 3)
- Home: removed the NEET/JEE selector and the "Your exam" card (the exam is chosen in Settings > Exam). New "Today's practice": three random questions, one per subject (Physics, Chemistry, Biology; Maths for JEE), stable for the day.
- Library: new Books slider (horizontal) above the question banks; tap a book to read it.
- The package icon now opens Downloads with two tabs: Books and Previous papers.

### Added
- Downloads > Previous papers > My links: add any https link yourself (a paper PDF, or an Abhyas question-bank zip). A PDF is downloaded and opened in PDF Studio for import settings (exam, subject, year pre-filled) and review; a zip is installed as a bank. No source is built in.
- "Find PDFs on a web page": paste a page address, the app lists the .pdf / .zip links in its source and you tick the ones to save as links. Simple pages only (no login, no JavaScript-built links).
- Downloads > Books: NCERT Physics, Chemistry, Biology and Maths for Class 11 and 12, fetched chapter by chapter from the official NCERT site (ncert.nic.in/textbook/pdf), merged into one book, then indexed for reading and search.
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
