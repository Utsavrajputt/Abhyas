# Offline KaTeX (optional)

`MathText` renders LaTeX with KaTeX when these files are present here, otherwise it shows plain text.
Copy from a KaTeX release (https://github.com/KaTeX/KaTeX/releases, `katex.zip`) into this folder:

    katex.min.js
    katex.min.css
    auto-render.min.js     (from the zip's contrib/ folder)
    fonts/                 (the whole fonts folder, needed by katex.min.css)

Supported delimiters: `$...$`, `$$...$$`, `\(...\)`, `\[...\]`.
Check the KaTeX licence (MIT) is kept alongside the files.
