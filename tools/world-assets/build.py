"""Generates every world map symbol in all five art styles, plus the index.

Usage:  python build.py [output-dir] [category ...]
Default output is ../../src/main/resources/assets/world (relative to this file).
Each symbol is a function decorated with @feature(...) in one of the features_*.py modules; adding one here
adds it to the map editor in every style. Output files are world/<style>/<id>.svg and world-index.json.
"""
import json
import os
import sys

from worldlib import REGISTRY, Style, Svg, rng_for

import features_land  # noqa: F401  (importing registers the symbols)
import features_misc  # noqa: F401
for _m in ("features_settlements", "features_decor"):
    try:
        __import__(_m)
    except ModuleNotFoundError as e:
        if e.name != _m:
            raise

STYLES = ["classic", "watercolour", "fantasy", "pixel", "dark"]
CATEGORY_ORDER = ["Mountains and hills", "Forests", "Water", "Caves and holes", "Desert, arctic and swamp",
                  "Landmarks", "Settlements", "Decorations", "Banners"]


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    out = os.path.abspath(sys.argv[1]) if len(sys.argv) > 1 else os.path.abspath(
        os.path.join(here, "..", "..", "src", "main", "resources", "assets", "world"))
    only = sys.argv[2:]
    index = []
    seen = set()
    for fid, name, cat, w, h, family, anchor, fn in REGISTRY:
        if only and cat not in only:
            continue
        assert fid not in seen, "duplicate id " + fid
        seen.add(fid)
        for style in STYLES:
            st = Style(style)
            S = Svg(100, h, st)
            fn(S, rng_for(fid))
            path = os.path.join(out, style, fid + ".svg")
            os.makedirs(os.path.dirname(path), exist_ok=True)
            with open(path, "w", encoding="utf-8", newline="\n") as f:
                f.write(S.build())
        entry = {"id": fid, "name": name, "category": cat, "w": w, "h": round(w * h / 100.0, 2), "anchor": anchor}
        if family:
            entry["family"] = family
        index.append(entry)
    index.sort(key=lambda e: (CATEGORY_ORDER.index(e["category"]) if e["category"] in CATEGORY_ORDER else 99, e["name"]))
    with open(os.path.join(os.path.dirname(out), "world-index.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(index, f, indent=1)
    print("wrote %d symbols x %d styles to %s" % (len(index), len(STYLES), out))


if __name__ == "__main__":
    main()
