from svglib import *

WOOD = "#a9794a"


def wood_grad(S, base=WOOD, vertical=False):
    if vertical:
        return S.lin([(0, shade(base, 1.18)), (1, shade(base, 0.78))], 0, 0, 1, 0)
    return S.lin([(0, shade(base, 1.18)), (1, shade(base, 0.78))])


def flames(S, cx, cy, s=1.0):
    out = ""
    out += circ(cx, cy, 34 * s, fill=S.rad([(0, "#ffb347", 0.5), (1, "#ff6a00", 0)]), stroke=None)
    out += path(f"M{cx} {cy - 26 * s}C{cx + 16 * s} {cy - 8 * s} {cx + 20 * s} {cy + 8 * s} {cx} {cy + 18 * s}C{cx - 20 * s} {cy + 8 * s} {cx - 16 * s} {cy - 8 * s} {cx} {cy - 26 * s}Z",
                fill=S.lin([(0, "#ffe27a"), (0.5, "#f0861c"), (1, "#c2300c")]), stroke="#8a2a08", sw=1.2)
    out += path(f"M{cx} {cy - 14 * s}C{cx + 9 * s} {cy - 2 * s} {cx + 10 * s} {cy + 6 * s} {cx} {cy + 12 * s}C{cx - 10 * s} {cy + 6 * s} {cx - 9 * s} {cy - 2 * s} {cx} {cy - 14 * s}Z",
                fill=S.lin([(0, "#fff6c2"), (1, "#ffc83a")]), stroke=None)
    return out


@asset("camp/tent", "Tent", "Camp", 2, 2, seed=81)
def tent(S):
    cloth = "#cdb98a"
    # guy ropes and stakes
    for (x, y) in ((6, 6), (194, 6), (6, 194), (194, 194)):
        S.add(line(x, y, 100 + (x - 100) * 0.7, 100 + (y - 100) * 0.7, stroke="#6b5a3a", sw=1.6))
        S.add(circ(x, y, 3.5, fill="#5e4a2c", stroke=OUT, sw=1.2))
    body = (poly([(14, 14), (186, 14), (186, 100), (14, 100)], fill=S.lin([(0, "#e6d6aa"), (1, cloth)]), stroke=OUT, sw=2.6)
            + poly([(14, 100), (186, 100), (186, 186), (14, 186)], fill=S.lin([(0, shade(cloth, 0.86)), (1, shade(cloth, 0.62))]), stroke=OUT, sw=2.6))
    S.add(S.shadowed(body, 5, 6, 4.5, 0.45))
    for y in (40, 70, 130, 158):
        S.add(path(f"M16 {y}H184", stroke="#a8946a", sw=1.2, extra='opacity="0.7" stroke-dasharray="6 5"'))
    S.add(rect(14, 96, 172, 8, fill=S.lin([(0, "#8a7448"), (1, "#5e4a2c")]), stroke=OUT, sw=2, r=3))
    # open front flap
    S.add(path("M66 186L100 120L134 186Z", fill="#1f1a15", stroke=OUT, sw=2.2))
    S.add(path("M66 186L100 120L84 186Z", fill=shade(cloth, 0.7), stroke=OUT, sw=1.6))
    S.add(path("M134 186L100 120L116 186Z", fill=shade(cloth, 0.7), stroke=OUT, sw=1.6))
    S.add(S.grained(rect(14, 14, 172, 172, fill="#000", stroke=None), 0.2, 0.9, 7))


@asset("camp/tent-round", "Pavilion", "Camp", 2, 2, seed=82)
def tent_round(S):
    cx = cy = 100
    wedges = 12
    body = ""
    for i in range(wedges):
        a0, a1 = 2 * math.pi * i / wedges, 2 * math.pi * (i + 1) / wedges
        l = 0.5 + 0.5 * math.cos((a0 + a1) / 2 + math.radians(135))
        base = "#c0392b" if i % 2 == 0 else "#efe3c2"
        # slightly scalloped outer edge
        pts = f"M{cx} {cy}L{n(cx + math.cos(a0) * 90)} {n(cy + math.sin(a0) * 90)}A90 90 0 0 1 {n(cx + math.cos(a1) * 90)} {n(cy + math.sin(a1) * 90)}Z"
        body += path(pts, fill=mix(shade(base, 0.7), shade(base, 1.12), l), stroke=OUT, sw=1.4)
    S.add(S.shadowed(body, 5, 6, 4.5, 0.45))
    S.add(circ(cx, cy, 90, fill=S.rad([(0, "#ffffff", 0.2), (0.6, "#ffffff", 0), (1, "#000000", 0.28)], 0.4, 0.35, 0.9), stroke=OUT, sw=2.6))
    S.add(circ(cx, cy, 11, fill=S.rad([(0, "#f0d27a"), (1, "#a8842c")]), stroke=OUT, sw=2))
    S.add(poly([(86, 188), (100, 160), (114, 188)], fill="#1f1a15", stroke=OUT, sw=1.8))
    S.add(circ(cx, 12, 5, fill="#f0d27a", stroke=OUT, sw=1.4))


@asset("camp/tent-small", "Small tent", "Camp", 1, 2, seed=83)
def tent_small(S):
    cloth = "#7f9a6a"
    for (x, y) in ((6, 12), (94, 12), (6, 188), (94, 188)):
        S.add(line(x, y, 50 + (x - 50) * 0.55, 100 + (y - 100) * 0.7, stroke="#5a4a30", sw=1.4))
        S.add(circ(x, y, 3, fill="#5e4a2c", stroke=OUT, sw=1))
    body = (poly([(14, 16), (86, 16), (86, 100), (14, 100)], fill=S.lin([(0, shade(cloth, 1.2)), (1, cloth)], 0, 0, 1, 0), stroke=OUT, sw=2.4)
            + poly([(14, 100), (86, 100), (86, 184), (14, 184)], fill=S.lin([(0, cloth), (1, shade(cloth, 0.7))], 0, 0, 1, 0), stroke=OUT, sw=2.4))
    S.add(S.shadowed(body, 4, 5, 4, 0.45))
    S.add(rect(14, 96, 72, 8, fill="#5e4a2c", stroke=OUT, sw=1.8, r=3))
    S.add(poly([(34, 184), (50, 140), (66, 184)], fill="#1f1a15", stroke=OUT, sw=1.8))


@asset("camp/campfire", "Campfire", "Camp", 1, 1, seed=84)
def campfire(S):
    r = S.rnd
    S.add(circ(50, 50, 46, fill=S.rad([(0, "#ffb347", 0.35), (1, "#ff6a00", 0)]), stroke=None))
    S.add(circ(50, 50, 38, fill=S.rad([(0, "#4a3826"), (1, "#2a2018")]), stroke=None, extra='opacity="0.85"'))
    for i in range(10):
        a = 2 * math.pi * i / 10 + 0.2
        x, y = 50 + math.cos(a) * 33, 50 + math.sin(a) * 33
        S.add(S.shadowed(path(blob(r, x, y, 8.5, 7, 7, 0.15), fill=S.rad([(0, "#c4c0b8"), (1, "#7d796f")], 0.4, 0.35, 0.8), stroke=OUT, sw=1.6), 1.5, 2, 1.5, 0.5))
    S.add(S.shadowed(path("M28 62L70 36", stroke="#5b3d22", sw=9) + path("M28 38L70 62", stroke="#6b4a2b", sw=9), 1.5, 2, 1.5, 0.5))
    S.add(path("M28 62L70 36", stroke="#8a6238", sw=2.4, extra='opacity="0.7"'))
    S.add(flames(S, 50, 50, 0.78))
    S.add(scatter(S, r, 24, 24, 52, 52, 14, ["#ffcf5a", "#ff8a2a"], 0.6, 1.3, 0.9))


@asset("camp/campfire-cooking", "Cooking fire", "Camp", 1, 1, seed=85)
def campfire_cooking(S):
    r = S.rnd
    S.add(circ(50, 50, 44, fill=S.rad([(0, "#ffb347", 0.3), (1, "#ff6a00", 0)]), stroke=None))
    for i in range(9):
        a = 2 * math.pi * i / 9
        S.add(S.shadowed(path(blob(r, 50 + math.cos(a) * 34, 50 + math.sin(a) * 34, 8, 6.5, 7, 0.15), fill=S.rad([(0, "#c4c0b8"), (1, "#7d796f")]), stroke=OUT, sw=1.5), 1.5, 2, 1.5, 0.5))
    S.add(flames(S, 50, 52, 0.6))
    # spit with a hanging pot
    S.add(path("M10 20L90 20", stroke="#4a3220", sw=4))
    S.add(path("M16 12V50M84 12V50", stroke="#4a3220", sw=4))
    S.add(S.shadowed(circ(50, 46, 20, fill=S.rad([(0, "#6b7078"), (1, "#25272b")], 0.38, 0.35, 0.8), stroke=OUT, sw=2.4), 3, 4, 2.5, 0.5))
    S.add(circ(50, 46, 14, fill=S.rad([(0, "#6a4a2a"), (1, "#3a2a1a")]), stroke="#15161a", sw=1.6))
    S.add(ell(46, 42, 6, 3, fill="#c9a070", stroke=None, extra='opacity="0.6"'))
    S.add(path("M50 20V30", stroke="#25272b", sw=2.4))


@asset("camp/bonfire", "Bonfire", "Camp", 2, 2, seed=86)
def bonfire(S):
    r = S.rnd
    S.add(circ(100, 100, 98, fill=S.rad([(0, "#ffb347", 0.4), (0.7, "#ff7a00", 0.12), (1, "#ff6a00", 0)]), stroke=None))
    S.add(circ(100, 100, 66, fill=S.rad([(0, "#3a2c20"), (1, "#1c1510")]), stroke=None, extra='opacity="0.9"'))
    for i in range(14):
        a = 2 * math.pi * i / 14
        S.add(S.shadowed(path(blob(r, 100 + math.cos(a) * 66, 100 + math.sin(a) * 66, 13, 10, 7, 0.15), fill=S.rad([(0, "#c4c0b8"), (1, "#7d796f")]), stroke=OUT, sw=2), 2, 3, 2, 0.5))
    logs = ""
    for k in range(6):
        logs += rot(rect(46, 92, 108, 15, fill=wood_grad(S, "#6b4a2b"), stroke=OUT, sw=2, r=7), 30 * k, 100, 100)
    S.add(S.shadowed(logs, 3, 4, 3, 0.5))
    S.add(flames(S, 100, 98, 1.5))
    S.add(scatter(S, r, 50, 50, 100, 100, 30, ["#ffcf5a", "#ff8a2a"], 0.8, 1.8, 0.9))


@asset("camp/bedroll", "Bedroll", "Camp", 1, 2, seed=87)
def bedroll(S):
    S.add(S.shadowed(rect(18, 10, 64, 180, fill=S.lin([(0, "#8fa56e"), (1, "#5e7446")], 0, 0, 1, 0), stroke=OUT, sw=2.6, r=16), 3, 4, 3, 0.45))
    S.add(rect(24, 16, 52, 36, fill=S.lin([(0, "#fff8e4"), (1, "#d9cfae")]), stroke=OUT, sw=2, r=10))
    S.add(path("M32 30h36M32 38h26", stroke="#bfb48f", sw=1.2, extra='opacity="0.8"'))
    S.add(path("M18 72Q50 82 82 72M18 112Q50 122 82 112M18 152Q50 162 82 152", stroke="#3f5030", sw=1.6, extra='opacity="0.7"'))
    S.add(rect(18, 60, 64, 22, fill=S.lin([(0, "#a53a3a"), (1, "#7a2424")]), stroke=OUT, sw=1.8, r=4, extra='opacity="0.9"'))
    S.add(circ(50, 176, 6, fill="#6b4a2b", stroke=OUT, sw=1.4))


@asset("camp/barrel", "Barrel", "Camp", 1, 1, seed=88)
def barrel(S):
    S.add(S.shadowed(circ(50, 50, 38, fill=S.rad([(0, "#c89a64"), (1, "#7a5230")], 0.38, 0.33, 0.85), stroke=OUT, sw=2.6), 3, 4, 3, 0.5))
    S.add(circ(50, 50, 30, fill=S.rad([(0, "#d2a572"), (1, "#8a5f35")], 0.4, 0.35, 0.8), stroke="#5e3f22", sw=1.8))
    S.add(path("M50 20V80M36 23V77M64 23V77M25 33V67M75 33V67", stroke="#6e4a28", sw=1.2, extra='opacity="0.8"'))
    S.add(circ(50, 50, 38, fill=None, stroke="#3a3f46", sw=3.4, extra='opacity="0.9"'))
    S.add(circ(50, 50, 26, fill=None, stroke="#3a3f46", sw=2.2, extra='opacity="0.8"'))
    S.add(circ(50, 50, 5, fill="#4a3220", stroke=OUT, sw=1.2))


@asset("camp/barrels", "Barrel cluster", "Camp", 2, 1, seed=89)
def barrels(S):
    for (x, y, rr) in ((50, 54, 34), (140, 42, 30), (146, 76, 20)):
        S.add(S.shadowed(circ(x, y, rr, fill=S.rad([(0, "#c89a64"), (1, "#7a5230")], 0.38, 0.33, 0.85), stroke=OUT, sw=2.4), 3, 4, 3, 0.5))
        S.add(circ(x, y, rr * 0.78, fill=S.rad([(0, "#d2a572"), (1, "#8a5f35")]), stroke="#5e3f22", sw=1.5))
        S.add(circ(x, y, rr, fill=None, stroke="#3a3f46", sw=2.6))
        S.add(path(f"M{x} {y - rr * 0.7}V{y + rr * 0.7}M{x - rr * 0.35} {y - rr * 0.6}V{y + rr * 0.6}M{x + rr * 0.35} {y - rr * 0.6}V{y + rr * 0.6}", stroke="#6e4a28", sw=1, extra='opacity="0.8"'))


@asset("camp/crate", "Crate", "Camp", 1, 1, seed=90)
def crate(S):
    S.add(S.shadowed(textured(S, plank_pattern(S, "#b98a58"), 14, 14, 72, 72, sw=2.6), 3, 4, 3, 0.5))
    S.add(rect(14, 14, 72, 72, fill=S.lin([(0, "#ffffff", 0.16), (1, "#000000", 0.24)], 0, 0, 1, 1), stroke=None))
    S.add(rect(22, 22, 56, 56, fill=None, stroke="#6b4423", sw=4))
    S.add(path("M24 24L76 76M76 24L24 76", stroke="#7a5230", sw=5))
    S.add(path("M24 24L76 76M76 24L24 76", stroke="#c8975a", sw=1.4, extra='opacity="0.6"'))
    for (x, y) in ((20, 20), (80, 20), (20, 80), (80, 80)):
        S.add(circ(x, y, 2.2, fill="#2b2b2e", stroke=None))


@asset("camp/cart", "Cart", "Camp", 2, 1, seed=91)
def cart(S):
    S.add(S.shadowed(textured(S, plank_pattern(S, "#a77a46"), 30, 22, 140, 56, r=4, sw=2.6), 4, 5, 3.5, 0.5))
    S.add(rect(30, 22, 140, 56, fill=S.lin([(0, "#ffffff", 0.14), (1, "#000000", 0.24)], 0, 0, 0, 1), stroke=None, r=4))
    S.add(rect(34, 26, 132, 48, fill="none", stroke="#6b4423", sw=3, r=3))
    for x in (60, 120):
        S.add(S.shadowed(rect(x - 9, 6, 18, 14, fill="#4a3220", stroke=OUT, sw=2, r=3) + rect(x - 9, 80, 18, 14, fill="#4a3220", stroke=OUT, sw=2, r=3), 2, 3, 2, 0.5))
        S.add(path(f"M{x - 5} 10H{x + 5}M{x - 5} 88H{x + 5}", stroke="#8a6a3c", sw=1.4))
    S.add(path("M170 50H198", stroke="#6b4423", sw=7))
    S.add(path("M170 50H198", stroke="#c8975a", sw=2, extra='opacity="0.7"'))
    S.add(rect(48, 34, 30, 32, fill=S.lin([(0, "#d9c9a0"), (1, "#a89870")]), stroke=OUT, sw=1.6, r=8))
    S.add(rect(86, 36, 28, 28, fill=S.lin([(0, "#c9b38a"), (1, "#8f7a52")]), stroke=OUT, sw=1.6, r=8))


@asset("camp/wagon", "Covered wagon", "Camp", 2, 3, seed=92)
def wagon(S):
    for (x, y) in ((8, 40), (192, 40), (8, 240), (192, 240)):
        S.add(S.shadowed(rect(x - 8, y - 22, 16, 44, fill="#4a3220", stroke=OUT, sw=2, r=4), 2, 3, 2, 0.5))
        S.add(path(f"M{x - 4} {y - 14}V{y + 14}", stroke="#8a6a3c", sw=1.4))
    S.add(path("M100 270V298", stroke="#6b4423", sw=8))
    canvas_ = S.lin([(0, "#f0e6c8"), (0.5, "#e2d4ac"), (1, "#bfae80")], 0, 0, 1, 0)
    body = rect(24, 14, 152, 252, fill=canvas_, stroke=OUT, sw=2.8, r=24)
    S.add(S.shadowed(body, 5, 6, 4.5, 0.45))
    for y in range(38, 260, 30):
        S.add(path(f"M26 {y}Q100 {y + 5} 174 {y}", stroke="#8a7a50", sw=2.4, extra='opacity="0.75"'))
    S.add(path("M100 16V264", stroke="#a8946a", sw=1.4, extra='opacity="0.7" stroke-dasharray="7 6"'))
    S.add(rect(24, 14, 152, 252, fill=S.lin([(0, "#ffffff", 0.22), (0.5, "#ffffff", 0), (1, "#000000", 0.3)], 0, 0, 1, 0), stroke=None, r=24))
    S.add(ell(100, 270, 38, 8, fill="#2a2018", stroke=OUT, sw=1.6, extra='opacity="0.85"'))


@asset("camp/log-bench", "Log bench", "Camp", 2, 1, seed=93)
def log_bench(S):
    S.add(S.shadowed(rect(6, 34, 188, 32, fill=S.lin([(0, "#c99a64"), (0.5, "#9a6e3e"), (1, "#6b4a2b")]), stroke=OUT, sw=2.6, r=14), 3, 5, 3, 0.5))
    for x in (14, 186):
        S.add(circ(x, 50, 15, fill=S.rad([(0, "#e0b888"), (1, "#a67c4a")]), stroke=OUT, sw=2))
        S.add(circ(x, 50, 9, fill=None, stroke="#7a5230", sw=1.2))
    S.add(path("M30 42Q100 38 170 42M30 58Q100 62 170 58", stroke="#6b4a2b", sw=1.2, extra='opacity="0.7"'))


@asset("camp/haybale", "Hay bale", "Camp", 1, 1, seed=94)
def haybale(S):
    r = S.rnd
    S.add(S.shadowed(rect(14, 22, 72, 56, fill=S.lin([(0, "#e6c85a"), (1, "#b8962c")]), stroke=OUT, sw=2.4, r=4), 3, 4, 3, 0.5))
    S.add(tufts(r, 16, 24, 68, 52, 70, ["#f3dc7a", "#c9a23c", "#a98a22"], 12))
    S.add(path("M34 22V78M66 22V78", stroke="#6b4a2b", sw=2.6))


@asset("camp/sacks", "Sacks", "Camp", 1, 1, seed=95)
def sacks(S):
    for (x, y, rot_, c) in ((34, 38, -12, "#d4c08a"), (66, 40, 10, "#c8b27a"), (50, 70, 4, "#dccb98")):
        body = path(f"M{x - 18} {y + 14}Q{x - 24} {y - 6} {x - 8} {y - 16}H{x + 8}Q{x + 24} {y - 6} {x + 18} {y + 14}Q{x} {y + 22} {x - 18} {y + 14}Z",
                    fill=S.rad([(0, shade(c, 1.15)), (1, shade(c, 0.78))], 0.4, 0.35, 0.8), stroke=OUT, sw=2)
        S.add(S.shadowed(rot(body, rot_, x, y), 2, 3, 2, 0.5))
        S.add(rot(path(f"M{x - 8} {y - 14}H{x + 8}", stroke="#6b4a2b", sw=3), rot_, x, y))


@asset("camp/weapon-rack", "Weapon rack", "Camp", 2, 1, seed=96)
def weapon_rack(S):
    S.add(S.shadowed(rect(8, 40, 184, 20, fill=wood_grad(S, "#8a5a2b"), stroke=OUT, sw=2.2, r=3), 3, 4, 3, 0.5))
    S.add(rect(8, 40, 184, 20, fill=S.lin([(0, "#ffffff", 0.14), (1, "#000000", 0.2)]), stroke=None, r=3))
    # swords, a spear and an axe lying across the rack
    S.add(S.shadowed(path("M22 20V82", stroke="#c9ced6", sw=6) + path("M22 20V82", stroke="#8d939c", sw=2) + rect(14, 62, 16, 6, fill="#6b4a2b", stroke=OUT, sw=1.4, r=2), 2, 3, 2, 0.5))
    S.add(S.shadowed(path("M56 16V86", stroke="#c9ced6", sw=6) + path("M56 16V86", stroke="#8d939c", sw=2) + rect(48, 64, 16, 6, fill="#6b4a2b", stroke=OUT, sw=1.4, r=2), 2, 3, 2, 0.5))
    S.add(S.shadowed(path("M100 12V90", stroke="#8a5a2b", sw=5) + poly([(100, 8), (94, 24), (106, 24)], fill="#c9ced6", stroke=OUT, sw=1.4), 2, 3, 2, 0.5))
    S.add(S.shadowed(path("M140 18V88", stroke="#8a5a2b", sw=5) + path("M140 24Q164 22 164 40Q150 42 140 36Z", fill="#9aa1aa", stroke=OUT, sw=1.6), 2, 3, 2, 0.5))
    S.add(S.shadowed(circ(176, 50, 20, fill=S.rad([(0, "#a53a3a"), (1, "#6a1f1f")]), stroke=OUT, sw=2.2) + circ(176, 50, 6, fill="#c9ced6", stroke=OUT, sw=1.4), 2, 3, 2, 0.5))


@asset("camp/anvil", "Anvil", "Camp", 1, 1, seed=97)
def anvil(S):
    S.add(S.shadowed(rect(34, 40, 34, 36, fill=wood_grad(S, "#6b4a2b"), stroke=OUT, sw=2, r=3), 2, 3, 2, 0.5))
    shape = "M14 34H76L86 42L72 50H60V58H44V50H26Z"
    S.add(S.shadowed(path(shape, fill=S.lin([(0, "#8a929b"), (1, "#3c4148")]), stroke=OUT, sw=2.2), 3, 4, 3, 0.55))
    S.add(path("M20 38H72", stroke="#c9ced6", sw=2, extra='opacity="0.7"'))


@asset("camp/bucket", "Bucket", "Camp", 1, 1, seed=98)
def bucket(S):
    S.add(S.shadowed(circ(50, 50, 24, fill=S.rad([(0, "#b98a58"), (1, "#7a5230")], 0.4, 0.35, 0.8), stroke=OUT, sw=2.2), 2, 3, 2, 0.5))
    S.add(circ(50, 50, 18, fill=S.rad([(0, "#4f9ad8"), (1, "#27608f")]), stroke="#3a3f46", sw=2.4))
    S.add(ell(46, 46, 6, 3, fill="#e6f4ff", stroke=None, extra='opacity="0.7"'))
    S.add(path("M26 50Q50 20 74 50", stroke="#3a3f46", sw=2.6))


@asset("camp/signpost", "Signpost", "Camp", 1, 1, seed=99)
def signpost(S):
    S.add(S.shadowed(path("M50 50L88 38L96 48L88 58Z", fill=wood_grad(S, "#b98a58"), stroke=OUT, sw=2) + path("M50 50L14 62L6 52L14 42Z", fill=wood_grad(S, "#a77a46"), stroke=OUT, sw=2), 3, 4, 3, 0.5))
    S.add(path("M56 48L82 41M18 53L42 58", stroke="#4a3220", sw=1.4, extra='opacity="0.7"'))
    S.add(S.shadowed(circ(50, 50, 9, fill=S.rad([(0, "#cfa070"), (1, "#6e4528")]), stroke=OUT, sw=2), 2, 3, 2, 0.5))


@asset("camp/rowboat", "Rowboat", "Camp", 1, 2, seed=100)
def rowboat(S):
    hull = "M50 8C86 40 88 150 50 192C12 150 14 40 50 8Z"
    S.add(S.shadowed(path(hull, fill=S.lin([(0, "#b98a58"), (1, "#7a5230")], 0, 0, 1, 0), stroke=OUT, sw=2.8), 4, 5, 4, 0.5))
    S.add(path("M50 22C76 52 77 142 50 178C23 142 24 52 50 22Z", fill=S.lin([(0, "#5e3f22"), (1, "#8a5f35")], 0, 0, 1, 0), stroke="#3a2614", sw=1.6))
    for y in (60, 100, 140):
        S.add(rect(24, y - 4, 52, 8, fill=wood_grad(S, "#c89a64"), stroke=OUT, sw=1.6, r=2))
    S.add(S.shadowed(path("M14 96L86 104", stroke="#d9c9a0", sw=4) + circ(14, 96, 4.5, fill="#6b4a2b", stroke=OUT, sw=1.2) + circ(86, 104, 4.5, fill="#6b4a2b", stroke=OUT, sw=1.2), 2, 3, 2, 0.5))


@asset("camp/table", "Table", "Camp", 2, 1, seed=101)
def table(S):
    S.add(S.shadowed(textured(S, plank_pattern(S, "#a9794a"), 10, 20, 180, 60, r=5, sw=2.6), 4, 5, 3.5, 0.5))
    S.add(rect(10, 20, 180, 60, fill=S.lin([(0, "#ffffff", 0.16), (1, "#000000", 0.22)]), stroke=None, r=5))
    for (x, y) in ((48, 38), (100, 64), (150, 36)):
        S.add(S.shadowed(circ(x, y, 9.5, fill=S.rad([(0, "#f5f1e6"), (1, "#c9c2ae")]), stroke=OUT, sw=1.6), 2, 3, 1.5, 0.5))
        S.add(circ(x, y, 5, fill=None, stroke="#a8a28c", sw=1))
    S.add(S.shadowed(circ(76, 34, 6, fill=S.rad([(0, "#d9b36a"), (1, "#8a6a2c")]), stroke=OUT, sw=1.5), 2, 3, 1.5, 0.5))
    S.add(S.shadowed(circ(172, 62, 5.5, fill=S.rad([(0, "#fff2b0"), (1, "#e8a02c")]), stroke=OUT, sw=1.4), 2, 3, 1.5, 0.5))


@asset("camp/chair", "Chair", "Camp", 1, 1, seed=102)
def chair(S):
    S.add(S.shadowed(rect(26, 20, 48, 12, fill=wood_grad(S, "#8a5a2b"), stroke=OUT, sw=2, r=4), 2, 3, 2, 0.5))
    S.add(S.shadowed(rect(26, 32, 48, 44, fill=S.lin([(0, "#c89a64"), (1, "#8a5f35")], 0, 0, 1, 1), stroke=OUT, sw=2.2, r=6), 2, 3, 2, 0.5))
    S.add(path("M32 40H68M32 52H68M32 64H68", stroke="#6e4a28", sw=1.2, extra='opacity="0.6"'))
    for (x, y) in ((30, 78), (70, 78)):
        S.add(circ(x, y, 4, fill="#6b4a2b", stroke=OUT, sw=1.2))


@asset("camp/chest", "Chest", "Camp", 1, 1, seed=103)
def chest(S):
    S.add(S.shadowed(rect(12, 24, 76, 52, fill=S.lin([(0, "#a8733a"), (1, "#6b4423")]), stroke=OUT, sw=2.6, r=7), 3, 4, 3, 0.5))
    S.add(path("M16 36H84M16 64H84", stroke="#5e3b1a", sw=1.2, extra='opacity="0.6"'))
    for x in (30, 70):
        S.add(rect(x - 4, 24, 8, 52, fill=S.lin([(0, "#8a8f96"), (1, "#4a4f56")], 0, 0, 1, 0), stroke=OUT, sw=1.6))
    S.add(rect(42, 38, 16, 18, fill=S.lin([(0, "#ffe27a"), (1, "#b8902c")]), stroke=OUT, sw=1.8, r=3))
    S.add(circ(50, 46, 2.6, fill="#2b211c", stroke=None))
    S.add(rect(12, 24, 76, 52, fill=S.lin([(0, "#ffffff", 0.16), (1, "#000000", 0.2)]), stroke=None, r=7))


@asset("camp/logs", "Firewood", "Camp", 1, 1, seed=104)
def logs(S):
    S.add(S.shadowed("".join(circ(x, y, 18, fill=S.rad([(0, "#c89a64"), (1, "#8a5f35")], 0.4, 0.35, 0.85), stroke=OUT, sw=2.2) for (x, y) in ((30, 64), (70, 64), (50, 32))), 3, 4, 3, 0.5))
    for (x, y) in ((30, 64), (70, 64), (50, 32)):
        S.add(circ(x, y, 12, fill=S.rad([(0, "#e8c898"), (1, "#c49a62")]), stroke="#7a5230", sw=1.2))
        S.add(circ(x, y, 7, fill=None, stroke="#8a5f35", sw=1))
        S.add(circ(x, y, 2.4, fill="#8a5f35", stroke=None))


@asset("camp/supplies", "Supply stack", "Camp", 1, 1, seed=105)
def supplies(S):
    S.add(S.shadowed(textured(S, plank_pattern(S, "#b98a58"), 10, 20, 50, 44, sw=2.2), 3, 4, 2.5, 0.5))
    S.add(path("M12 22L58 62M58 22L12 62", stroke="#7a5230", sw=3.4))
    S.add(S.shadowed(textured(S, plank_pattern(S, "#a67c4a"), 44, 46, 44, 40, sw=2.2), 3, 4, 2.5, 0.5))
    S.add(path("M46 48L86 84M86 48L46 84", stroke="#7a5230", sw=3.4))
    S.add(S.shadowed(circ(24, 78, 14, fill=S.rad([(0, "#c89a64"), (1, "#7a5230")]), stroke=OUT, sw=2) + circ(24, 78, 14, fill=None, stroke="#3a3f46", sw=2.2), 2, 3, 2, 0.5))
