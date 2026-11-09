"""Generates every battlemap SVG plus index.json.

Usage:  python build.py [output-dir]
Default output is ../../src/main/resources/assets (relative to this file).
To add a piece, write a function decorated with @asset(...) in one of the category modules.
"""
import json
import os
import sys

from svglib import REGISTRY, Svg

# importing a module registers its pieces
import buildings  # noqa: F401
for _m in ("walls", "paths", "camp", "nature", "dungeon", "furniture"):
    try:
        __import__(_m)
    except ModuleNotFoundError as e:
        if e.name != _m:
            raise


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    out = os.path.abspath(sys.argv[1]) if len(sys.argv) > 1 else os.path.abspath(os.path.join(here, "..", "..", "src", "main", "resources", "assets"))
    only = sys.argv[2:]  # optional category filter for quick previews
    index = []
    ids = set()
    for asset_id, name, cat, wc, hc, seed, fn in REGISTRY:
        if only and cat not in only:
            continue
        assert asset_id not in ids, f"duplicate id {asset_id}"
        ids.add(asset_id)
        S = Svg(wc, hc, seed)
        fn(S)
        path = os.path.join(out, asset_id + ".svg")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            f.write(S.build())
        index.append({"id": asset_id, "name": name, "category": cat, "w": wc, "h": hc})
    with open(os.path.join(out, "index.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(index, f, indent=2)
    print(f"generated {len(index)} pieces into {out}")


if __name__ == "__main__":
    main()
