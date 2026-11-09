#!/usr/bin/env python3
"""Build index.json for Abhyas from a folder of question-bank zips (and optional book PDFs).

Publish the zips + index.json on GitHub (a repo, or Release assets), then add the index.json link
(or just the repo link) in the app: Settings > NCERT & data > Other links. The app fills the name,
descriptions, versions, subject, coaching etc. from this file.

Usage:
  python3 tools/build_index.py <folder> --base-url https://github.com/<you>/<repo>/releases/download/<tag> \\
      --name "My Abhyas packs" [--meta meta.json] [-o index.json]

Every <name>.zip must be an Abhyas bank (manifest.json + questions.json, see tools/pack_bank.py) whose
manifest id equals the file name without .zip. Fields come from the manifest; --meta overrides/extends
them per id (or per file name for PDFs):
  {"neet-2024": {"coaching": "Allen", "description": "Full paper with solutions", "year": 2024},
   "ncert-phys-11.pdf": {"type": "book", "subject": "Physics", "class": 11, "name": "NCERT Physics 11"}}
Raw GitHub URLs work too: --base-url https://raw.githubusercontent.com/<you>/<repo>/main/packs
"""
import argparse, hashlib, json, os, re, sys, zipfile


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("folder")
    ap.add_argument("--base-url", required=True, help="HTTPS folder the files will be served from")
    ap.add_argument("--name", default="Abhyas packages")
    ap.add_argument("--meta", help="JSON file with per-id extra fields (coaching, description, year, ...)")
    ap.add_argument("-o", "--out", default="index.json")
    a = ap.parse_args()
    if not a.base_url.startswith("https://"):
        sys.exit("--base-url must start with https://")
    base = a.base_url.rstrip("/")
    meta = json.load(open(a.meta, encoding="utf-8")) if a.meta else {}

    packages = []
    for fname in sorted(os.listdir(a.folder)):
        path = os.path.join(a.folder, fname)
        stem, ext = os.path.splitext(fname)
        if ext == ".zip":
            with zipfile.ZipFile(path) as z:
                names = {n.rsplit("/", 1)[-1]: n for n in z.namelist()}
                if "manifest.json" not in names:
                    sys.exit(f"{fname}: no manifest.json inside")
                manifest = json.loads(z.read(names["manifest.json"]).decode("utf-8"))
            if manifest.get("id") != stem:
                sys.exit(f"{fname}: manifest id '{manifest.get('id')}' must equal the file name '{stem}'")
            kind = str(manifest.get("kind", "")).upper()
            y = re.search(r"(19|20)\d{2}", stem)
            entry = {
                "id": stem,
                "name": manifest.get("title") or stem,
                "description": "",
                "version": manifest.get("version", 1),
                "type": "paper",
                "exam": manifest.get("exam"),
                "subject": manifest.get("subject"),
                "kind": "pyq" if kind in ("PYQS", "PYQ") else kind.lower() or None,
                "year": int(y.group(0)) if y else None,
                "questions": manifest.get("questionCount", 0),
            }
        elif ext == ".pdf":
            m = meta.get(fname) or meta.get(stem)
            if not m or m.get("type") != "book" or not m.get("subject"):
                print(f"skip {fname}: needs a meta entry with type=book and subject")
                continue
            entry = {"id": stem, "name": stem, "description": "", "version": 1, "type": "book"}
        else:
            continue
        entry.update(meta.get(stem, {}))
        entry.update(meta.get(fname, {}) if ext == ".pdf" else {})
        entry["url"] = f"{base}/{fname}"
        entry["sha256"] = sha256(path)
        entry["sizeBytes"] = os.path.getsize(path)
        packages.append({k: v for k, v in entry.items() if v not in (None, "")})

    with open(a.out, "w", encoding="utf-8") as f:
        json.dump({"name": a.name, "packages": packages}, f, indent=2, ensure_ascii=False)
    print(f"wrote {a.out}: {len(packages)} packages")


if __name__ == "__main__":
    sys.exit(main())
