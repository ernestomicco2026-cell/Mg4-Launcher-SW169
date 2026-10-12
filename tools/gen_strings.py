#!/usr/bin/env python3
"""Genera app/res/values*/strings.xml da tools/strings.py (inglese = lingua di riserva in values/)."""
import os
import sys
from xml.sax.saxutils import escape

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from strings import LANGS, S  # noqa: E402

RES = os.path.join(HERE, "..", "app", "res")


def android_escape(s):
    s = escape(s)
    s = s.replace("\\", "\\\\").replace("'", "\\'").replace('"', '\\"')
    if s.startswith("@") or s.startswith("?"):
        s = "\\" + s
    return s


def write(folder, idx):
    os.makedirs(os.path.join(RES, folder), exist_ok=True)
    lines = ['<?xml version="1.0" encoding="utf-8"?>',
             '<!-- Generato da tools/gen_strings.py: non modificare a mano, modifica tools/strings.py -->',
             "<resources>"]
    for key, vals in S.items():
        if len(vals) != len(LANGS):
            raise SystemExit("traduzioni mancanti per " + key)
        v = vals[idx]
        fmt = "%" in v
        attr = "" if fmt else ' formatted="false"'
        lines.append('    <string name="%s"%s>%s</string>' % (key, attr, android_escape(v)))
    lines.append("</resources>")
    with open(os.path.join(RES, folder, "strings.xml"), "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")


for i, lang in enumerate(LANGS):
    write("values" if lang == "en" else "values-" + lang, i)
print("ok: %d testi x %d lingue" % (len(S), len(LANGS)))
