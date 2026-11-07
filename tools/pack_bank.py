#!/usr/bin/env python3
"""Pack an Abhyas question bank: compute sha256/questionCount for questions.json, write manifest.json, zip.

Usage: python3 tools/pack_bank.py <folder with questions.json [+ images/]> --id my_bank --version 1 \
         --exam NEET --subject Physics --kind pyqs [--class 11] [--title "My bank"] [-o out.zip]
"""
import argparse, hashlib, json, os, sys, zipfile


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("folder")
    ap.add_argument("--id", required=True)
    ap.add_argument("--version", type=int, default=1)
    ap.add_argument("--exam", required=True, choices=["NEET", "JEE_MAIN"])
    ap.add_argument("--subject", required=True)
    ap.add_argument("--kind", required=True, choices=["theory", "numericals", "pyqs", "mixed"])
    ap.add_argument("--class", dest="cls", type=int, choices=[11, 12])
    ap.add_argument("--title")
    ap.add_argument("-o", "--out")
    a = ap.parse_args()

    qpath = os.path.join(a.folder, "questions.json")
    raw = open(qpath, "rb").read()
    count = len(json.loads(raw.decode("utf-8")))
    manifest = {
        "id": a.id, "version": a.version, "title": a.title or a.id, "exam": a.exam,
        "subject": a.subject, "kind": a.kind, "sha256": hashlib.sha256(raw).hexdigest(),
        "questionCount": count, "formatVersion": 1,
    }
    if a.cls:
        manifest["class"] = a.cls
    out = a.out or f"{a.id}.zip"
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("manifest.json", json.dumps(manifest, indent=2))
        z.write(qpath, "questions.json")
        img = os.path.join(a.folder, "images")
        if os.path.isdir(img):
            for name in sorted(os.listdir(img)):
                z.write(os.path.join(img, name), f"images/{name}")
    print(f"wrote {out}: {count} questions, sha256 {manifest['sha256'][:12]}...")


if __name__ == "__main__":
    sys.exit(main())
