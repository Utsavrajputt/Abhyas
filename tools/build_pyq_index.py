#!/usr/bin/env python3
"""Build index.json for the Abhyas previous-year-paper store from a folder of pack zips.

Each zip is a normal Abhyas question-bank package (see tools/pack_bank.py) built from one
year's paper, named e.g. neet-pyq-2024.zip, with manifest id == file name without .zip.

Usage: python3 tools/build_pyq_index.py <folder with zips> --base-url https://raw.githubusercontent.com/<you>/<repo>/main/packs \\
         [--exam NEET] [-o index.json]

Host the zips and index.json anywhere that serves HTTPS, then put the index.json URL in
Settings > NCERT & data > PYQ index URL. The app verifies each download against the sha256
written here, so re-run this script whenever a zip changes.
"""
import argparse, hashlib, json, os, re, sys, zipfile


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("folder")
    ap.add_argument("--base-url", required=True, help="HTTPS folder URL the zips will be served from")
    ap.add_argument("--exam", default="NEET", choices=["NEET", "JEE_MAIN"])
    ap.add_argument("-o", "--out", default="index.json")
    a = ap.parse_args()
    if not a.base_url.startswith("https://"):
        sys.exit("--base-url must start with https://")
    base = a.base_url.rstrip("/")

    packs = []
    for name in sorted(os.listdir(a.folder)):
        if not name.endswith(".zip"):
            continue
        path = os.path.join(a.folder, name)
        m = re.search(r"(19|20)\d{2}", name)
        if not m:
            print(f"skip {name}: no year in the file name")
            continue
        with zipfile.ZipFile(path) as z:
            manifest = json.loads(z.read("manifest.json").decode("utf-8"))
        pack_id = name[:-4]
        if manifest.get("id") != pack_id:
            sys.exit(f"{name}: manifest id '{manifest.get('id')}' must equal the file name '{pack_id}'")
        year = int(m.group(0))
        packs.append({
            "id": pack_id,
            "exam": manifest.get("exam", a.exam),
            "year": year,
            "title": manifest.get("title") or f"{a.exam} {year}",
            "url": f"{base}/{name}",
            "sha256": hashlib.sha256(open(path, "rb").read()).hexdigest(),
            "questions": manifest.get("questionCount", 0),
        })
    packs.sort(key=lambda p: -p["year"])
    with open(a.out, "w", encoding="utf-8") as f:
        json.dump({"packs": packs}, f, indent=2, ensure_ascii=False)
    print(f"wrote {a.out}: {len(packs)} packs")


if __name__ == "__main__":
    sys.exit(main())
