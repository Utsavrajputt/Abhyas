#!/usr/bin/env python3
"""Turn a two-column "Answers & Solutions" paper PDF (Aakash style: "1. question ... Answer (3) Sol. ...")
into an Abhyas question-bank zip, with no typing.

Each question becomes an IMAGE question: the question and its options are cropped from the PDF page
(so formulas, structures and figures are exact), the four options are answer buttons, and the correct one
comes from the "Answer (n)" line. Solutions are not included. Answers printed with several options or a
star (e.g. "Answer (2 & 3)*") are kept but flagged unverified, so they are shown but not scored.

Needs: poppler-utils (pdftotext, pdftoppm) and Pillow.

  python3 tools/pdf_to_bank.py NEET_2017.pdf --id neet-pyq-2017 --year 2017 --exam NEET \
      --subjects 1-45:Physics,46-90:Chemistry,91-180:Biology --title "NEET 2017 (Code R)"
"""
import argparse, hashlib, io, json, os, re, subprocess, sys, tempfile, zipfile
from html import unescape
from PIL import Image

HEADER_Y, FOOTER_Y = 42.0, 775.0
NUM_RE = re.compile(r"^(\d{1,3})\.(?:\s|$)")
ANS_RE = re.compile(r"Answer\s*\(([^)]*)\)\s*(\*?)")


def parse_pages(pdf):
    """Return [(width, height, [(x0, y0, x1, y1, text), ...]), ...] using pdftotext -bbox-layout."""
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
            words = [unescape(x) for x in re.findall(r"<word[^>]*>(.*?)</word>", lm.group(5), re.S)]
            lines.append((float(lm.group(1)), float(lm.group(2)), float(lm.group(3)), float(lm.group(4)), " ".join(words)))
        pages.append((w, h, lines))
    return pages


def find_questions(pages, first=1):
    """Walk columns in reading order. Returns {n: {"segs": [(page, col, y0, y1)], "answer": str|None}}."""
    found, expected, cur, seg_start = {}, first, None, 0.0
    for pi, (w, h, lines) in enumerate(pages):
        mid = w / 2
        for col in (0, 1):
            left = 0 if col == 0 else mid
            reach = 75 if col == 0 else 45  # numbers sit this far in from the column edge
            right = mid if col == 0 else w
            # Page numbers sit alone in a box at the foot of the page: not part of any question.
            items = sorted((l for l in lines if left <= l[0] < right and HEADER_Y <= l[1] < FOOTER_Y
                            and not (l[4].strip().isdigit() and l[1] > 735 and abs((l[0] + l[2]) / 2 - mid) < 40)),
                           key=lambda l: l[1])
            if cur is not None:
                seg_start = HEADER_Y - 2
            last_y = HEADER_Y
            for (x0, y0, x1, y1, text) in items:
                m = NUM_RE.match(text.strip())
                # A numbered list on the instructions page also starts at 1: restart once a real "1." shows up.
                if m and int(m.group(1)) == first and expected > first and not any(v["answer"] for v in found.values()) and x0 < left + reach:
                    found, expected, cur = {}, first, None
                if m and int(m.group(1)) == expected and x0 < left + reach:
                    if cur is not None:  # previous question never reached its Answer line
                        found[cur]["segs"].append((pi, col, seg_start, y0 - 2))
                    cur, seg_start = expected, y0 - 3
                    found[cur] = {"segs": [], "answer": None}
                    expected += 1
                elif cur is not None and text.startswith("Answer") and x0 < left + reach:
                    found[cur]["segs"].append((pi, col, seg_start, y0 - 2))
                    found[cur]["answer"] = text
                    cur = None
                last_y = max(last_y, y1)
            if cur is not None:
                found[cur]["segs"].append((pi, col, seg_start, min(last_y + 4, FOOTER_Y)))
    return found


def parse_answer(text):
    """-> (option id '1'-'4' or None, verified)."""
    if not text:
        return None, False
    m = ANS_RE.search(text)
    if not m:
        return None, False
    inner = m.group(1)
    star = bool(m.group(2)) or "*" in inner
    letters = re.findall(r"[1-4]|[A-Da-d]", inner)
    if not letters:
        return None, False
    first = letters[0]
    opt = first if first in "1234" else str("abcd".index(first.lower()) + 1)
    return opt, len(letters) == 1 and not star


def render(pdf, dpi, tmp):
    subprocess.run(["pdftoppm", "-r", str(dpi), "-gray", "-png", pdf, os.path.join(tmp, "p")], check=True)
    files = sorted(f for f in os.listdir(tmp) if f.startswith("p") and f.endswith(".png"))
    return [os.path.join(tmp, f) for f in files]


def crop_question(images, pages, segs, scale):
    parts = []
    for (pi, col, y0, y1) in segs:
        w = pages[pi][0]
        x0, x1 = (30, w / 2 - 3) if col == 0 else (w / 2 + 3, w - 30)
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
    ap.add_argument("-o", "--out")
    a = ap.parse_args()

    ranges = []
    for part in a.subjects.split(","):
        rng, name = part.split(":")
        lo, hi = (int(x) for x in rng.split("-"))
        ranges.append((lo, hi, name.strip()))

    pages = parse_pages(a.pdf)
    found = find_questions(pages)
    scale = a.dpi / 72.0
    with tempfile.TemporaryDirectory() as tmp:
        imgs = [Image.open(p).convert("L") for p in render(a.pdf, a.dpi, tmp)]
        if len(imgs) != len(pages):
            sys.exit("page count mismatch between pdftotext and pdftoppm")
        questions, images, problems = [], {}, []
        for n in sorted(found):
            opt, verified = parse_answer(found[n]["answer"])
            img = crop_question(imgs, pages, found[n]["segs"], scale)
            if img is None or opt is None:
                problems.append(n)
                continue
            name = f"q{n:03d}.png"
            buf = io.BytesIO()
            img.save(buf, "PNG", optimize=True)
            images[name] = buf.getvalue()
            questions.append({
                "id": f"{a.id}-{n:03d}", "type": "IMAGE",
                "statement": f"Question {n}. Choose the correct option.",
                "payload": {"options": [{"id": str(i), "text": f"({i})"} for i in range(1, 5)], "correctId": opt},
                "imagePath": f"images/{name}", "exam": a.exam, "subject": subject_for(n, ranges),
                "year": a.year, "source": f"{a.exam} {a.year} - {os.path.basename(a.pdf)}",
                "tags": ["pyq", f"{a.exam.lower()}-{a.year}"],
                "marks": a.marks, "negativeMarks": a.negative, "language": "en", "answerVerified": verified,
            })
    raw = json.dumps(questions, ensure_ascii=False, indent=1).encode("utf-8")
    manifest = {"id": a.id, "version": a.version, "title": a.title or a.id, "exam": a.exam,
                "subject": ", ".join(dict.fromkeys(r[2] for r in ranges)), "kind": "pyqs",
                "sha256": hashlib.sha256(raw).hexdigest(), "questionCount": len(questions), "formatVersion": 1}
    out = a.out or f"{a.id}.zip"
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("manifest.json", json.dumps(manifest, indent=2))
        z.writestr("questions.json", raw)
        for name, data in images.items():
            z.writestr(f"images/{name}", data)
    unverified = [int(q["id"].rsplit("-", 1)[1]) for q in questions if not q["answerVerified"]]
    print(f"wrote {out}: {len(questions)} questions, {os.path.getsize(out) // 1024} KB")
    if problems:
        print("could not read question(s):", problems)
    if unverified:
        print("unscored (answer shown with several options or a star):", unverified)


if __name__ == "__main__":
    main()
