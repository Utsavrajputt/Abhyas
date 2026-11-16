#!/usr/bin/env python3
"""Turn a two-column "Answers & Solutions" paper PDF (Aakash style: "1. question ... Answer (3) Sol. ...")
into an Abhyas question-bank zip, with no typing.

Questions are read as TEXT (statement, four options, and the solution as the explanation). Greek letters
and symbols stored in the PDF's Symbol font are mapped to Unicode, and subscripts/superscripts become
Unicode (P₁, cos²). A question whose layout cannot be read safely as text (stacked fractions, big
brackets, figures, unknown symbols) is kept as an IMAGE question instead: the question and options are
cropped from the page, so nothing is ever wrong, only less pretty. Answers come from "Answer (n)"; an answer
with several options or a star (e.g. "Answer (2 & 3)*") is kept but flagged unverified (shown, not scored).

Needs: poppler-utils (pdftotext, pdftoppm) and Pillow.

  python3 tools/pdf_to_bank.py NEET_2017.pdf --id neet-pyq-2017 --year 2017 --exam NEET \
      --subjects 1-45:Physics,46-90:Chemistry,91-180:Biology --title "NEET 2017 (Code R)"
Use --images to force image questions everywhere.
"""
import argparse, hashlib, io, json, os, re, statistics, subprocess, sys, tempfile, zipfile
from html import unescape
from PIL import Image

HEADER0, FOOTER0 = 42.0, 775.0
NUM_RE = re.compile(r"^(\d{1,3})\.(?:\s|$)")
NUM_LOOSE = re.compile(r"^(\d{1,3})\.?(?:\s|$)")  # OCR often drops the dot
LOOSE = False
ANS_RE = re.compile(r"(?:Answer|Ans\.?|Solution\s*:?)\s*\(\s*([^)]*?)\s*\)\s*(\*?)")
ANS_START_RE = re.compile(r"^(Answer|Ans\.?|Solution:?)\(")


class _AnsStart:
    """Matches an answer line even when the PDF spaces the letters out ("Solutio n : (C )")."""
    @staticmethod
    def match(text):
        return ANS_START_RE.match(re.sub(r"\s+", "", text))


ANS_START = _AnsStart()
OPT_RE = re.compile(r"^\(([1-4A-D])\)$")

GREEK_UPPER = dict(zip("ABCDEFGHIJKLMNOPQRSTUVWXYZ", "ΑΒΧΔΕΦΓΗΙϑΚΛΜΝΟΠΘΡΣΤΥςΩΞΨΖ"))
GREEK_LOWER = dict(zip("abcdefghijklmnopqrstuvwxyz", "αβχδεφγηιϕκλμνοπθρστυϖωξψζ"))
SYMBOL_HIGH = {0xA2: "′", 0xA3: "≤", 0xA5: "∞", 0xAB: "↔", 0xAC: "←", 0xAD: "↑", 0xAE: "→", 0xAF: "↓",
               0xB0: "°", 0xB1: "±", 0xB2: "″", 0xB3: "≥", 0xB4: "×", 0xB5: "∝", 0xB6: "∂", 0xB7: "•",
               0xB8: "÷", 0xB9: "≠", 0xBB: "≈", 0xD6: "√", 0xD7: "⋅", 0xDE: "⇒", 0xDB: "⇔", 0xC5: "⊕",
               0xF2: "∫", 0xA0: "€", 0x5C: "∴", 0x5E: "⊥", 0x40: "≅"}
# Symbols we cannot place on one line safely (fraction bars, bracket pieces, uncertain glyphs).
UNSAFE_SYMBOLS = {0xBE, 0xBA, 0xBD, 0xBC}
UNSAFE_CHARS = set("⎜⎟⎡⎤⎢⎥⎣⎦⎛⎞⎝⎠⎧⎨⎩⎫⎬⎭⎪")
SUB = {**dict(zip("0123456789+-=()", "₀₁₂₃₄₅₆₇₈₉₊₋₌₍₎")), **dict(zip("aehijklmnoprstuvx", "ₐₑₕᵢⱼₖₗₘₙₒₚᵣₛₜᵤᵥₓ"))}
SUP = {**dict(zip("0123456789+-=()", "⁰¹²³⁴⁵⁶⁷⁸⁹⁺⁻⁼⁽⁾")), "n": "ⁿ", "i": "ⁱ", "s": "ˢ", "t": "ᵗ", "h": "ʰ", "r": "ʳ", "d": "ᵈ"}


def map_char(ch):
    """-> (text, safe). Maps Symbol-font private-use characters to Unicode."""
    o = ord(ch)
    if 0xF000 <= o <= 0xF0FF:
        code = o - 0xF000
        if code in UNSAFE_SYMBOLS:
            return "", False
        if code in SYMBOL_HIGH:
            return SYMBOL_HIGH[code], True
        c = chr(code)
        if c in GREEK_UPPER and code != 0x5C and code != 0x5E:
            return GREEK_UPPER[c], True
        if c in GREEK_LOWER:
            return GREEK_LOWER[c], True
        if 0x20 <= code <= 0x7E:
            return ("−" if c == "-" else c), True
        return "", False
    if 0xE000 <= o <= 0xF8FF:
        return "", False
    if ch in UNSAFE_CHARS:
        return "", False
    return ch, True


def convert(text):
    out, safe = [], True
    for ch in text:
        t, ok = map_char(ch)
        out.append(t)
        safe = safe and ok
    return "".join(out), safe


def script(text, table, mark):
    text = text.replace("–", "-").replace("−", "-").replace("—", "-")
    if all(c in table for c in text):
        return "".join(table[c] for c in text)
    return f"{mark}({text})" if len(text) > 1 else f"{mark}{text}"


class Line:
    def __init__(self, x0, y0, x1, y1, words):
        self.x0, self.y0, self.x1, self.y1, self.words = x0, y0, x1, y1, words
        self.text = " ".join(w[4] for w in words)
        self.hn = 9.5


def parse_pages(pdf):
    with tempfile.NamedTemporaryFile(suffix=".html", delete=False) as t:
        path = t.name
    subprocess.run(["pdftotext", "-bbox-layout", pdf, path], check=True)
    html = open(path, encoding="utf-8").read()
    os.unlink(path)
    pages = []
    for pm in re.finditer(r'<page width="([\d.]+)" height="([\d.]+)">(.*?)</page>', html, re.S):
        w, h, body = float(pm.group(1)), float(pm.group(2)), pm.group(3)
        lines = []
        for lm in re.finditer(r'<line xMin="([\d.]+)" yMin="([\d.]+)" xMax="([\d.]+)" yMax="([\d.]+)">(.*?)</line>', body, re.S):
            words = [(float(a), float(b), float(c), float(d), unescape(e))
                     for a, b, c, d, e in re.findall(r'<word xMin="([\d.]+)" yMin="([\d.]+)" xMax="([\d.]+)" yMax="([\d.]+)">(.*?)</word>', lm.group(5), re.S)]
            lines.append(Line(float(lm.group(1)), float(lm.group(2)), float(lm.group(3)), float(lm.group(4)), words))
        hs = [wd[3] - wd[1] for l in lines for wd in l.words if len(wd[4]) >= 3]
        typical = statistics.median(hs) if hs else 9.5
        for l in lines:
            l.hn = typical
        pages.append((w, h, lines))
    return pages


def _ocr_page(args):
    path, dpi = args
    out = subprocess.run(["tesseract", path, "stdout", "--psm", "3", "tsv"], capture_output=True, text=True).stdout
    k = 72.0 / dpi
    groups = {}
    for row in out.splitlines()[1:]:
        c = row.split("\t")
        if len(c) < 12 or c[0] != "5" or not c[11].strip() or float(c[10]) < 0:
            continue
        x, y, w, h = (int(c[6]) * k, int(c[7]) * k, int(c[8]) * k, int(c[9]) * k)
        groups.setdefault((c[2], c[3], c[4]), []).append((x, y, x + w, y + h, c[11].strip()))
    lines = []
    for ws in groups.values():
        ws.sort(key=lambda t: t[0])
        lines.append(Line(min(t[0] for t in ws), min(t[1] for t in ws), max(t[2] for t in ws), max(t[3] for t in ws), ws))
    return lines


def parse_pages_ocr(pdf, dpi=150):
    """Scanned PDF (no text layer): find question numbers and "Answer" lines with tesseract. Image questions only.
    Set ABHYAS_OCR_CACHE=file.pkl to keep the (slow) OCR result between runs."""
    import pickle
    from concurrent.futures import ThreadPoolExecutor
    cache = os.environ.get("ABHYAS_OCR_CACHE")
    if cache and os.path.exists(cache):
        return [(w, h, [Line(*t) for t in ls]) for w, h, ls in pickle.load(open(cache, "rb"))]
    tmp = tempfile.mkdtemp()
    subprocess.run(["pdftoppm", "-r", str(dpi), "-gray", "-png", pdf, os.path.join(tmp, "o")], check=True)
    files = sorted(os.path.join(tmp, f) for f in os.listdir(tmp) if f.startswith("o") and f.endswith(".png"))
    sizes = [Image.open(f).size for f in files]
    with ThreadPoolExecutor(max_workers=os.cpu_count() or 2) as ex:
        results = list(ex.map(_ocr_page, [(f, dpi) for f in files]))
    pages = []
    for (iw, ih), lines in zip(sizes, results):
        for l in lines:
            l.hn = 9.5
        pages.append((iw * 72.0 / dpi, ih * 72.0 / dpi, lines))
    if cache:
        pickle.dump([(w, h, [(l.x0, l.y0, l.x1, l.y1, l.words) for l in ls]) for w, h, ls in pages], open(cache, "wb"))
    return pages


class _Num:
    def __init__(self, v):
        self.v = v

    def group(self, _):
        return str(self.v)


def match_number(text):
    """Question number at the start of a line: "12.", "1 5 1 ." (letter-spaced PDFs) or "131.The"."""
    t = text.strip()
    if LOOSE:
        m = NUM_LOOSE.match(t)
        return _Num(int(m.group(1))) if m else None
    m = re.match(r"^(\d(?:\s?\d){0,2})\s?\.(?!\d)", t)
    return _Num(int(re.sub(r"\s", "", m.group(1)))) if m else None


def find_questions(pages, first=1):
    """Walk columns in reading order. -> {n: {"segs": [...], "sol": [...], "answer": str|None}} (segs = (page, col, y0, y1))."""
    found, expected, cur, sol, seg_start, sol_start = {}, first, None, None, 0.0, 0.0
    for pi, (w, h, lines) in enumerate(pages):
        mid = w / 2
        k = h / 792.0  # the page-furniture constants below are for US Letter
        HEADER_Y, FOOTER_Y = HEADER0 * k, FOOTER0 * k
        # One-column pages (no text starts in the right half) are walked as a single wide column.
        single = (sum(1 for l in lines if l.x0 >= mid - 2 and len(l.text) > 12) < 4
                  and not any(l.x0 >= mid - 2 and not LOOSE and match_number(l.text) for l in lines))
        for col in ((0,) if single else (0, 1)):
            left = 0 if col == 0 else mid
            right = (w if single else mid) if col == 0 else w
            reach = 95 if col == 0 else 45
            segcol = 2 if (single and col == 0) else col  # 2 = full-width crop
            items = sorted((l for l in lines if left <= l.x0 < right and HEADER_Y <= l.y0 < FOOTER_Y
                            and not (l.text.strip().isdigit() and l.y0 > 735 * k and abs((l.x0 + l.x1) / 2 - mid) < 40)),
                           key=lambda l: l.y0)
            if cur is not None:
                seg_start = HEADER_Y + 2
            if sol is not None:
                sol_start = HEADER_Y + 2
            last_y = HEADER_Y
            for l in items:
                m = match_number(l.text)
                if m and int(m.group(1)) == first and expected > first and not any(v["answer"] for v in found.values()) and l.x0 < left + reach:
                    found, expected, cur, sol = {}, first, None, None
                # A question number that the PDF never printed: skip the gap, the question is lost.
                if m and expected < int(m.group(1)) <= expected + 2 and cur is None and l.x0 < left + reach and found:
                    expected = int(m.group(1))
                if m and int(m.group(1)) == expected and l.x0 < left + reach:
                    if cur is not None:
                        found[cur]["segs"].append((pi, segcol, seg_start, l.y0 - 1))
                    if sol is not None:
                        found[sol]["sol"].append((pi, segcol, sol_start, l.y0 - 1))
                        sol = None
                    cur, seg_start = expected, l.y0 - 3
                    found[cur] = {"segs": [], "sol": [], "answer": None}
                    expected += 1
                elif cur is not None and ANS_START.match(l.text.strip()) and l.x0 < left + reach:
                    found[cur]["segs"].append((pi, segcol, seg_start, l.y0 - 1))
                    found[cur]["answer"] = l.text
                    sol, sol_start, cur = cur, l.y1 + 0.5, None
                last_y = max(last_y, l.y1)
            end = min(last_y + 4, FOOTER_Y)
            if cur is not None:
                found[cur]["segs"].append((pi, segcol, seg_start, end))
            if sol is not None:
                found[sol]["sol"].append((pi, segcol, sol_start, end))
    return found


def locate_answer(img, scale, w, y0, y1):
    """OCR one page region and return (y, text) of an "Answer (n)" line inside it, or None."""
    crop = img.crop((int(30 * scale), int(y0 * scale), int((w - 30) * scale), int(y1 * scale)))
    crop = crop.resize((crop.width * 2, crop.height * 2), Image.LANCZOS)
    with tempfile.NamedTemporaryFile(suffix=".png", delete=False) as t:
        path = t.name
    crop.save(path)
    out = subprocess.run(["tesseract", path, "stdout", "--psm", "6", "tsv"], capture_output=True, text=True).stdout
    os.unlink(path)
    rows = {}
    for row in out.splitlines()[1:]:
        c = row.split("\t")
        if len(c) >= 12 and c[0] == "5" and c[11].strip():
            rows.setdefault((c[2], c[3], c[4]), []).append((int(c[6]), int(c[7]), c[11].strip()))
    for ws in rows.values():
        ws.sort()
        text = " ".join(t[2] for t in ws)
        if re.match(r"^Answ?e?r?\b", text) and re.search(r"\(\s*[1-4]", text):
            return y0 + min(t[1] for t in ws) / (2 * scale), text
    return None


def find_questions_scan(pages, imgs, scale):
    """Scanned papers: the question numbers are not read reliably by OCR, but each one is a small blob of ink in the
    left margin strip, just left of the "Answer (n)" lines. Pair those blobs, in reading order, with the answer lines."""
    xs = sorted(l.x0 for _, _, ls in pages for l in ls if ANS_START.match(l.text.strip()))
    if not xs:
        return {}
    ax = xs[len(xs) // 2]
    events = []  # (page, y, kind, text)
    for pi, (w, h, ls) in enumerate(pages):
        a, b = int((ax - 40) * scale), int((ax - 8) * scale)
        im = imgs[pi]
        rows = []
        for y in range(int(30 * scale), int((h - 30) * scale)):
            strip = im.crop((a, y, b, y + 1)).point(lambda v: 255 if v < 120 else 0)
            rows.append(strip.histogram()[255] > 0)
        y, n = 0, len(rows)
        while y < n:
            if rows[y]:
                y0 = y
                gap = 0
                while y < n and gap <= 3 * scale / 1.4:
                    gap = 0 if rows[y] else gap + 1
                    y += 1
                y1 = y - gap
                hpt = (y1 - y0) / scale
                if 5 <= hpt <= 16:
                    events.append((pi, (y0 + 30 * scale) / scale if False else y0 / scale + 30, "N", ""))
            else:
                y += 1
        for l in ls:
            if ANS_START.match(l.text.strip()) and l.x0 < ax + 25:
                events.append((pi, l.y0, "A", l.text))
            elif re.match(r"^Sol\b", l.text.strip()) and l.x0 < ax + 25:
                events.append((pi, l.y0, "S", ""))
    events.sort(key=lambda e: (e[0], e[1]))
    found, n, cur = {}, 0, None
    for pi, y, kind, text in events:
        if kind == "N":
            if cur is not None and cur["start"][0] == pi and y - cur["start"][1] > 60:
                # Answer and Sol. lines of the open question were both missed by the page OCR: look inside its region
                hit = locate_answer(imgs[pi], scale, pages[pi][0], cur["start"][1], y - 2)
                if hit:
                    found[n] = {"segs": [(pi, 2, cur["start"][1], hit[0] - 1)], "sol": [], "answer": hit[1]}
                    cur = None
            if cur is None:
                n += 1
                cur = {"start": (pi, y - 3)}
        elif kind == "S":  # "Sol." reached without an Answer line: OCR missed it, which sits ~20 pt above
            if cur is not None:
                sp, sy = cur["start"]
                segs = [(q, 2, sy if q == sp else 28, (y - 21) if q == pi else pages[q][1] - 28) for q in range(sp, pi + 1)]
                found[n] = {"segs": segs, "sol": [], "answer": None, "ans_box": (pi, y)}
                cur = None
        elif cur is not None:
            sp, sy = cur["start"]
            segs = []
            for q in range(sp, pi + 1):
                h = pages[q][1]
                y0 = sy if q == sp else 28
                y1 = y - 1 if q == pi else h - 28
                segs.append((q, 2, y0, y1))
            found[n] = {"segs": segs, "sol": [], "answer": text}
            cur = None
    if cur is not None:  # last question of the paper: its Answer line was not read
        sp, sy = cur["start"]
        last = len(pages) - 1
        segs = [(q, 2, sy if q == sp else 28, pages[q][1] - 28) for q in range(sp, last + 1)]
        found[n] = {"segs": segs, "sol": [], "answer": None}
    return found


def read_key(pdf, code):
    """NTA final answer key PDF -> {question number: "3" or "1,3"} for one booklet code."""
    txt = subprocess.run(["pdftotext", "-layout", pdf, "-"], capture_output=True, text=True).stdout.splitlines()
    out, inside = {}, False
    for line in txt:
        m = re.search(r"Test Booklet Code\s*:\s*(\S+)", line)
        if m:
            inside = m.group(1).upper() == code.upper()
            continue
        if inside:
            t = line.split()
            i = 0
            while i + 1 < len(t):
                if t[i].isdigit() and re.fullmatch(r"[1-4](?:,[1-4])*", t[i + 1]):
                    out[int(t[i])] = t[i + 1]
                    i += 2
                else:
                    i += 1
    return out


def read_answer_crop(imgs, scale, box):
    """OCR just the strip where an "Answer (n)" line should be (the page-wide OCR missed it). box = (page, y of the
    "Sol." line below it); the answer sits 7-22 pt above. Tries a few offsets and returns the first readable text."""
    pi, sy = box
    im = imgs[pi]
    last = ""
    for top, bottom in ((22, 7), (23, 8), (21, 6), (24, 9)):
        crop = im.crop((int(55 * scale), int(max(sy - top, 0) * scale), int(360 * scale), int((sy - bottom) * scale)))
        crop = crop.resize((crop.width * 3, crop.height * 3), Image.LANCZOS)
        padded = Image.new("L", (crop.width + 40, crop.height + 40), 255)
        padded.paste(crop, (20, 20))
        with tempfile.NamedTemporaryFile(suffix=".png", delete=False) as t:
            path = t.name
        padded.save(path)
        out = subprocess.run(["tesseract", path, "stdout", "--psm", "7", "-c", "tessedit_char_whitelist=Answer()1234 "],
                             capture_output=True, text=True).stdout.strip()
        os.unlink(path)
        last = out
        if parse_answer(out)[0] and "(" in out and ")" in out:
            return out
    return last


def parse_answer(text):
    """-> (first option id '1'-'4' or None, verified, other accepted option ids).
    "Answer (2 & 3)" or a key entry "2,3" means every listed option is accepted; a lone star ("4*") is unverified."""
    if not text:
        return None, False, []
    m = ANS_RE.search(re.sub(r"[ \t]+", "", text).replace("Solution:", "Solution :") if False else text)
    if not m:
        compact = re.sub(r"\s+", "", text)
        m = re.search(r"(?:Answer|Ans\.?|Solution:?)\(([^)]*)\)(\*?)", compact)
    if not m:
        return None, False, []
    inner = m.group(1)
    star = bool(m.group(2)) or "*" in inner
    letters = re.findall(r"[1-4]|[A-Da-d]", inner)
    if not letters:
        return None, False, []
    ids = []
    for c in letters:
        i = c if c in "1234" else str("abcd".index(c.lower()) + 1)
        if i not in ids:
            ids.append(i)
    return ids[0], (len(ids) > 1 or not star), ids[1:]


# ---------- text extraction ----------

def line_tokens(line):
    """Words of one PDF line as [x0, y0, x1, y1, text, safe]. Subscripts/superscripts become Unicode and are
    glued to the word before; words that touch (no real space between them) are merged."""
    ws = line.words
    hn = line.hn
    normal = [w for w in ws if (w[3] - w[1]) >= 0.8 * hn]
    base_top = statistics.median(w[1] for w in normal) if normal else line.y0
    out, pending = [], []  # pending: scripts after the last base word, as (kind, text, x0, x1)

    def flush():
        if out and pending:
            kinds = {p[0] for p in pending}
            seq = pending
            if kinds == {"sub", "sup"} and any(a[2] < b[3] and b[2] < a[3] for a in pending for b in pending if a is not b):
                seq = [p for p in pending if p[0] == "sub"] + [p for p in pending if p[0] == "sup"]
            out[-1][4] += "".join(p[1] for p in seq)
            out[-1][2] = max(out[-1][2], max(p[3] for p in pending))
        pending.clear()

    for w in ws:
        txt, safe = convert(w[4])
        small = (w[3] - w[1]) < 0.8 * hn
        if small and out and txt:
            kind = "sub" if w[1] > base_top + 1.2 else "sup"
            pending.append((kind, script(txt, SUB if kind == "sub" else SUP, "_" if kind == "sub" else "^"), w[0], w[2]))
            if not safe:
                out[-1][5] = False
            continue
        flush()
        if out and w[0] - out[-1][2] < 1.4 and out[-1][4] and txt and not small:
            out[-1][4] += txt
            out[-1][2] = w[2]
            out[-1][5] = out[-1][5] and safe
        else:
            out.append([w[0], w[1], w[2], w[3], txt, safe])
    flush()
    return out


def region_lines(pages, segs):
    out = []
    for (pi, col, y0, y1) in segs:
        w, _, lines = pages[pi]
        mid = w / 2
        left, right = (0, w) if col == 2 else ((0, mid) if col == 0 else (mid, w))
        got = [l for l in lines if left <= l.x0 < right and y0 <= l.y0 and l.y1 <= y1 + 3 and l.words]
        out.append(sorted(got, key=lambda l: (round(l.y0), l.x0)))
    return out


def looks_complex(seg_lines):
    """Big gaps in a column (a figure sits there) make the text incomplete."""
    for lines in seg_lines:
        for a, b in zip(lines, lines[1:]):
            if b.y0 - a.y1 > 38:
                return True
    return False


def stacked(rows):
    """Rows closer than a text line (about 13 pt apart) are the numerator/denominator of a fraction."""
    ys = [min(t[1] for t in row) for row in rows]
    return any(3 < b - a < 10.5 for a, b in zip(ys, ys[1:]))


def has_graphics(images, pages, segs, scale):
    """True when a region holds ink outside every text box: a figure, a structure, a fraction bar, a table rule."""
    from PIL import ImageDraw
    total = 0
    for (pi, col, y0, y1) in segs:
        w, _, lines = pages[pi]
        mid = w / 2
        x0, x1 = (30, w - 30) if col == 2 else ((30, mid - 3) if col == 0 else (mid + 3, w - 30))
        if y1 - y0 < 6:
            continue
        box = (int(x0 * scale), int(y0 * scale), int(x1 * scale), int(y1 * scale))
        crop = images[pi].crop(box).point(lambda v: 255 if v < 140 else 0)
        d = ImageDraw.Draw(crop)
        pad = 2.5 * scale
        for l in lines:
            for wd in l.words:
                d.rectangle(((wd[0] - x0) * scale - pad, (wd[1] - y0) * scale - pad, (wd[2] - x0) * scale + pad, (wd[3] - y0) * scale + pad), fill=0)
        total += crop.histogram()[255]
    return total > 40


def opt_no(tok):
    return int(tok) if tok.isdigit() else "ABCD".index(tok) + 1


def text_of(pages, segs, split_options, images=None, scale=1.0):
    """-> (stem, [opt1..opt4]) when split_options else (text, None); None if unsafe."""
    seg_lines = region_lines(pages, segs)
    if looks_complex(seg_lines):
        return None
    if images is not None and has_graphics(images, pages, segs, scale):
        return None
    rows = []  # one entry per visual row: tokens sorted by x
    for lines in seg_lines:
        cur_y, cur = None, []
        for l in lines:
            toks = line_tokens(l)
            if any(not t[5] for t in toks):
                return None
            if cur_y is not None and abs(l.y0 - cur_y) <= 3:
                cur.extend(toks)
            else:
                if cur:
                    rows.append(sorted(cur, key=lambda t: t[0]))
                cur_y, cur = l.y0, list(toks)
        if cur:
            rows.append(sorted(cur, key=lambda t: t[0]))
    if stacked(rows):
        return None
    toks = [(ri, t) for ri, row in enumerate(rows) for t in row]
    if not split_options:
        return join_rows(rows), None
    marks = [(i, opt_no(OPT_RE.match(t[4]).group(1))) for i, (ri, t) in enumerate(toks) if OPT_RE.match(t[4])]
    # options start at the first "(1)" that is followed by (2), (3), (4) in order
    start = None
    for k in range(len(marks)):
        if [m[1] for m in marks[k:k + 4]] == [1, 2, 3, 4]:
            start = k
            break
    if start is None:
        return None
    mk = marks[start:start + 4]
    first_tok = mk[0][0]
    stem_rows = {}
    for ri, t in toks[:first_tok]:
        stem_rows.setdefault(ri, []).append(t)
    stem = re.sub(r"^\d{1,3}\.\s*", "", join_rows([stem_rows[r] for r in sorted(stem_rows)]))
    mpos = [(toks[i][1][0], toks[i][1][1], toks[i][0], n) for i, n in mk]  # x, y, row, number
    opts = {1: [], 2: [], 3: [], 4: []}
    for i in range(first_tok, len(toks)):
        ri, t = toks[i]
        if any(i == m[0] for m in mk):
            continue
        cands = [m for m in mpos if m[2] <= ri]
        if not cands:
            return None
        lastrow = max(m[2] for m in cands)
        same = [m for m in cands if m[2] == lastrow and m[0] <= t[0] + 3]
        pick = max(same, key=lambda m: m[0]) if same else min((m for m in cands if m[2] == lastrow), key=lambda m: m[0])
        opts[pick[3]].append(t)
    texts = []
    for n in (1, 2, 3, 4):
        s = " ".join(t[4] for t in opts[n]).strip()
        if not s:
            return None
        texts.append(s)
    # Leftover "_" or "^" mean a sub/superscript we could not turn into Unicode: use the image instead.
    if re.search(r"[_^]", stem + " ".join(texts)):
        return None
    return stem, texts


def join_rows(rows):
    """Wrapped lines are joined with a space; a short row (or a row starting a new item) starts a new line."""
    out, prev_end = [], None
    max_end = max((t[2] for row in rows for t in row), default=0)
    for row in rows:
        s = row[0][4]
        for prev, t in zip(row, row[1:]):
            s += ("   " if t[0] - prev[2] > 25 else " ") + t[4]
        s = s.strip()
        if not s:
            continue
        starts_item = re.match(r"^(\([a-dA-D]\)|\([ivx]+\)|[A-D]\.|[a-d]\.|Column|Statement|Match|Assertion|Reason)\b", s)
        if out and prev_end is not None and prev_end > max_end - 22 and not starts_item:
            out[-1] += " " + s
        else:
            out.append(s)
        prev_end = max(t[2] for t in row)
    return "\n".join(out)


def clean_explanation(txt):
    txt = re.sub(r"^Sol\.?\s*", "", txt.strip())
    txt = re.sub(r"[ \t]+", " ", txt)
    return txt[:1500].strip() or None


# ---------- images ----------

def render(pdf, dpi, tmp):
    subprocess.run(["pdftoppm", "-r", str(dpi), "-gray", "-png", pdf, os.path.join(tmp, "p")], check=True)
    files = sorted(f for f in os.listdir(tmp) if f.startswith("p") and f.endswith(".png"))
    return [os.path.join(tmp, f) for f in files]


def crop_question(images, pages, segs, scale):
    parts = []
    for (pi, col, y0, y1) in segs:
        w = pages[pi][0]
        x0, x1 = (30, w - 30) if col == 2 else ((30, w / 2 - 3) if col == 0 else (w / 2 + 3, w - 30))
        if y1 - y0 < 6:
            continue
        parts.append(images[pi].crop((int(x0 * scale), int(y0 * scale), int(x1 * scale), int(y1 * scale))))
    if not parts:
        return None
    width = max(p.width for p in parts)
    gap = 6
    out = Image.new("L", (width, sum(p.height for p in parts) + gap * (len(parts) - 1)), 255)
    y = 0
    for p in parts:
        out.paste(p, (0, y))
        y += p.height + gap
    return out


def subject_for(n, ranges):
    for lo, hi, name in ranges:
        if lo <= n <= hi:
            return name
    return ranges[-1][2]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("pdf")
    ap.add_argument("--id", required=True)
    ap.add_argument("--year", type=int, required=True)
    ap.add_argument("--exam", required=True, choices=["NEET", "JEE_MAIN"])
    ap.add_argument("--subjects", required=True, help="e.g. 1-45:Physics,46-90:Chemistry,91-180:Biology")
    ap.add_argument("--title")
    ap.add_argument("--version", type=int, default=1)
    ap.add_argument("--marks", type=float, default=4.0)
    ap.add_argument("--negative", type=float, default=1.0)
    ap.add_argument("--dpi", type=int, default=130)
    ap.add_argument("--images", action="store_true", help="make every question an image")
    ap.add_argument("--fix", action="append", default=[], metavar="N=PAGE:Y",
                    help="the PDF never printed question N's number: it starts on PAGE (1-based) at Y points from the top")
    ap.add_argument("--key-pdf", help="official answer key PDF (NTA format); its answers replace the ones read from the paper")
    ap.add_argument("--key-code", help="booklet code to take from --key-pdf, e.g. Q1")
    ap.add_argument("-o", "--out")
    a = ap.parse_args()

    ranges = []
    for part in a.subjects.split(","):
        rng, name = part.split(":")
        lo, hi = (int(x) for x in rng.split("-"))
        ranges.append((lo, hi, name.strip()))

    pages = parse_pages(a.pdf)
    for fx in a.fix:  # insert the missing number as a line of text, so the normal logic finds the question
        num, where = fx.split("=")
        pg, y = where.split(":")
        w = pages[int(pg) - 1][0]
        pages[int(pg) - 1][2].append(Line(46.8, float(y), 55.0, float(y) + 9.2, [(46.8, float(y), 55.0, float(y) + 9.2, f"{num}.")]))
    if sum(len(l.words) for _, _, ls in pages for l in ls) < 15 * len(pages):
        print("no text layer: scanned PDF, reading it with OCR (image questions only)")
        pages = parse_pages_ocr(a.pdf)
        a.images = True
        global LOOSE
        LOOSE = True
    scale = a.dpi / 72.0
    found = None if LOOSE else find_questions(pages)
    with tempfile.TemporaryDirectory() as tmp:
        imgs = [Image.open(p).convert("L") for p in render(a.pdf, a.dpi, tmp)]
        if len(imgs) != len(pages):
            sys.exit("page count mismatch between pdftotext and pdftoppm")
        if found is None:
            found = find_questions_scan(pages, imgs, scale)
        questions, images, problems, as_image = [], {}, [], []
        key = read_key(a.key_pdf, a.key_code) if a.key_pdf and a.key_code else {}
        disagree = []
        for n in sorted(found):
            opt, verified, also = parse_answer(found[n]["answer"])
            if opt is None and found[n].get("ans_box"):
                opt, verified, also = parse_answer(read_answer_crop(imgs, scale, found[n]["ans_box"]))
            if n in key:
                k_opt, k_ok, k_also = parse_answer(f"Answer ({key[n]})")
                if opt and opt not in key[n].split(","):
                    # the paper's own answer disagrees with the key: the question was probably cut wrongly, drop it
                    disagree.append(n)
                    continue
                opt, verified, also = k_opt, k_ok, k_also
            if opt is None or not found[n]["segs"]:
                problems.append(n)
                continue
            base = {"exam": a.exam, "subject": subject_for(n, ranges), "year": a.year,
                    "source": f"{a.exam} {a.year} - {os.path.basename(a.pdf)}",
                    "tags": ["pyq", f"{a.exam.lower()}-{a.year}"], "marks": a.marks, "negativeMarks": a.negative,
                    "language": "en", "answerVerified": verified}
            parsed = None if a.images else text_of(pages, found[n]["segs"], True, imgs, scale)
            if parsed and len(parsed[0]) >= 8:
                stem, opts = parsed
                expl = None
                sol = text_of(pages, found[n]["sol"], False, imgs, scale) if found[n]["sol"] else None
                if sol:
                    expl = clean_explanation(sol[0])
                if base["subject"] in ("Physics", "Chemistry") and re.search(r"\d", stem) and sum(bool(re.search(r"\d", t)) for t in opts) >= 3:
                    base = {**base, "tags": base["tags"] + ["numerical"]}  # calculation question: keeps it out of "theory" sets
                q = {"id": f"{a.id}-{n:03d}", "type": "SINGLE_MCQ", "statement": stem,
                     "payload": {"options": [{"id": str(i + 1), "text": t} for i, t in enumerate(opts)], "correctId": opt, **({"alsoCorrectIds": also} if also else {})}, **base}
                if expl:
                    q["explanation"] = expl
                questions.append(q)
                continue
            img = crop_question(imgs, pages, found[n]["segs"], scale)
            if img is None:
                problems.append(n)
                continue
            as_image.append(n)
            if base["subject"] == "Physics":  # image questions are mostly formulas and graphs: treat as numerical
                base = {**base, "tags": base["tags"] + ["numerical"]}
            name = f"q{n:03d}.png"
            buf = io.BytesIO()
            img.save(buf, "PNG", optimize=True)
            images[name] = buf.getvalue()
            questions.append({"id": f"{a.id}-{n:03d}", "type": "IMAGE", "statement": f"Question {n}. Choose the correct option.",
                              "payload": {"options": [{"id": str(i), "text": f"({i})"} for i in range(1, 5)], "correctId": opt, **({"alsoCorrectIds": also} if also else {})},
                              "imagePath": f"images/{name}", **base})
    raw = json.dumps(questions, ensure_ascii=False, indent=1).encode("utf-8")
    manifest = {"id": a.id, "version": a.version, "title": a.title or a.id, "exam": a.exam,
                "subject": "Mixed" if len({r[2] for r in ranges}) > 1 else ranges[0][2], "kind": "pyqs",
                "sha256": hashlib.sha256(raw).hexdigest(), "questionCount": len(questions), "formatVersion": 1}
    out = a.out or f"{a.id}.zip"
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("manifest.json", json.dumps(manifest, indent=2))
        z.writestr("questions.json", raw)
        for name, data in images.items():
            z.writestr(f"images/{name}", data)
    unverified = [int(q["id"].rsplit("-", 1)[1]) for q in questions if not q["answerVerified"]]
    multi = [int(q["id"].rsplit("-", 1)[1]) for q in questions if q["payload"].get("alsoCorrectIds")]
    if multi:
        print("more than one accepted answer (all shown, either scores):", multi)
    print(f"wrote {out}: {len(questions)} questions ({len(questions) - len(as_image)} text, {len(as_image)} image), {os.path.getsize(out) // 1024} KB")
    if as_image:
        print("kept as image (layout not safe as text):", as_image)
    if disagree:
        print("dropped, the paper's own answer disagreed with the key (probably cut wrongly):", disagree)
    if problems:
        print("could not read question(s):", problems)
    if unverified:
        print("unscored (answer shown with several options or a star):", unverified)


if __name__ == "__main__":
    main()
