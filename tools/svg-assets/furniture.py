from svglib import *
from camp import flames


def wood(S, base="#a9794a", horizontal=False):
    return S.lin([(0, shade(base, 1.18)), (1, shade(base, 0.78))], 0, 0, 1, 0) if horizontal else S.lin([(0, shade(base, 1.18)), (1, shade(base, 0.78))])


def glow(S, cx, cy, r, color="#ff9a2a", strength=0.5):
    return circ(cx, cy, r, fill=S.rad([(0, color, strength), (1, color, 0)]), stroke=None)


@asset("furniture/bed", "Bed", "Furniture", 1, 2, seed=171)
def bed(S):
    S.add(S.shadowed(rect(10, 8, 80, 184, fill=wood(S, "#8a5a2b", True), stroke=OUT, sw=2.6, r=6), 4, 5, 4, 0.5))
    S.add(rect(16, 14, 68, 172, fill=S.lin([(0, "#f4efe2"), (1, "#d8d0b8")]), stroke=OUT, sw=1.8, r=4))
    S.add(rect(22, 20, 56, 40, fill=S.lin([(0, "#fffdf6"), (1, "#e0d8c0")]), stroke=OUT, sw=1.8, r=10))
    S.add(path("M30 36h40M30 46h28", stroke="#cfc6a8", sw=1.2, extra='opacity="0.8"'))
    S.add(rect(16, 84, 68, 102, fill=S.lin([(0, "#a8283a"), (1, "#6a1422")], 0, 0, 1, 1), stroke=OUT, sw=2, r=4))
    S.add(path("M16 84Q50 96 84 84", stroke="#e8dcc0", sw=5, extra='opacity="0.9"'))
    for y in (110, 134, 158):
        S.add(path(f"M18 {y}Q50 {y + 6} 82 {y}", stroke="#4a0e18", sw=1.4, extra='opacity="0.55"'))


@asset("furniture/double-bed", "Double bed", "Furniture", 2, 2, seed=172)
def double_bed(S):
    S.add(S.shadowed(rect(10, 8, 180, 184, fill=wood(S, "#8a5a2b", True), stroke=OUT, sw=2.6, r=6), 4, 5, 4, 0.5))
    S.add(rect(18, 16, 164, 170, fill=S.lin([(0, "#f4efe2"), (1, "#d8d0b8")]), stroke=OUT, sw=1.8, r=4))
    for x in (30, 108):
        S.add(rect(x, 24, 62, 38, fill=S.lin([(0, "#fffdf6"), (1, "#e0d8c0")]), stroke=OUT, sw=1.8, r=10))
    S.add(rect(18, 84, 164, 102, fill=S.lin([(0, "#2f5aa8"), (1, "#1c3a78")], 0, 0, 1, 1), stroke=OUT, sw=2, r=4))
    S.add(path("M18 84Q100 98 182 84", stroke="#e8dcc0", sw=6, extra='opacity="0.9"'))
    for y in (112, 138, 164):
        S.add(path(f"M20 {y}Q100 {y + 6} 180 {y}", stroke="#12295a", sw=1.4, extra='opacity="0.55"'))
    S.add(path("M100 98V184", stroke="#12295a", sw=1.4, extra='opacity="0.4"'))


@asset("furniture/round-table", "Round table", "Furniture", 2, 2, seed=173)
def round_table(S):
    S.add(S.shadowed(circ(100, 100, 84, fill=S.rad([(0, "#c89a64"), (1, "#7a5230")], 0.4, 0.35, 0.9), stroke=OUT, sw=2.8), 5, 6, 5, 0.5))
    for rr in (72, 52, 30):
        S.add(circ(100, 100, rr, fill="none", stroke="#6e4a28", sw=1.2, extra='opacity="0.65"'))
    S.add(path("M100 16V184M16 100H184", stroke="#6e4a28", sw=1.2, extra='opacity="0.35"'))
    for i in range(8):
        a = 2 * math.pi * i / 8
        x, y = 100 + math.cos(a) * 54, 100 + math.sin(a) * 54
        S.add(S.shadowed(circ(x, y, 10, fill=S.rad([(0, "#f5f1e6"), (1, "#c9c2ae")]), stroke=OUT, sw=1.5), 1.5, 2, 1.5, 0.5))
    S.add(candle_(S, 100, 100))


def candle_(S, x, y):
    return (glow(S, x, y, 18, "#ffb347", 0.5) + S.shadowed(circ(x, y, 5, fill=S.rad([(0, "#fff8e0"), (1, "#d8c89a")]), stroke=OUT, sw=1.2), 1.5, 2, 1.2, 0.5)
            + circ(x, y, 2, fill="#ffb02a", stroke=None))


@asset("furniture/banquet-table", "Banquet table", "Furniture", 3, 1, seed=174)
def banquet_table(S):
    r = S.rnd
    for y in (6, 80):
        S.add(S.shadowed(rect(14, y, 272, 14, fill=wood(S, "#8a5a2b"), stroke=OUT, sw=2, r=5), 2, 3, 2, 0.45))
    S.add(S.shadowed(textured(S, plank_pattern(S, "#a9794a"), 10, 22, 280, 56, r=5, sw=2.6), 4, 5, 3.5, 0.5))
    S.add(rect(10, 22, 280, 56, fill=S.lin([(0, "#ffffff", 0.16), (1, "#000000", 0.22)]), stroke=None, r=5))
    S.add(rect(30, 34, 240, 32, fill=S.lin([(0, "#f4efe2"), (1, "#dcd4be")]), stroke="#b8ae92", sw=1.2, r=2, extra='opacity="0.9"'))
    for i in range(8):
        x = 40 + i * 31
        for y in (40, 62):
            S.add(S.shadowed(circ(x, y, 7.5, fill=S.rad([(0, "#ffffff"), (1, "#cfc8b4")]), stroke=OUT, sw=1.2), 1.2, 1.6, 1.2, 0.4))
    for x in (90, 150, 210):
        S.add(S.shadowed(circ(x, 50, 7, fill=S.rad([(0, "#d9b36a"), (1, "#8a6a2c")]), stroke=OUT, sw=1.2), 1.5, 2, 1.2, 0.5))
        S.add(circ(x + 14, 50, 4.4, fill=r.choice(["#d03a2c", "#78a83c", "#e8a02c"]), stroke=OUT, sw=1))


@asset("furniture/bookshelf", "Bookshelf", "Furniture", 2, 1, seed=175)
def bookshelf(S):
    r = S.rnd
    S.add(S.shadowed(rect(8, 30, 184, 40, fill=wood(S, "#7a4a28"), stroke=OUT, sw=2.6, r=2), 4, 5, 3.5, 0.5))
    S.add(rect(12, 34, 176, 32, fill="#2a1a10", stroke=None))
    x = 14
    while x < 184:
        w = 4 + r.random() * 6
        h = 22 + r.random() * 8
        c = r.choice(["#a8283a", "#2f5aa8", "#3a7a46", "#d8a838", "#6a3a8a", "#8a5a2b", "#c0502c"])
        S.add(rect(x, 36 + (28 - h) * 0.5, w, h, fill=S.lin([(0, shade(c, 1.2)), (1, shade(c, 0.75))], 0, 0, 1, 0), stroke=OUT, sw=0.9, r=0.8))
        x += w + 0.6
    S.add(rect(8, 30, 184, 40, fill=S.lin([(0, "#ffffff", 0.14), (0.4, "#ffffff", 0), (1, "#000000", 0.25)]), stroke=None, r=2))


@asset("furniture/fireplace", "Fireplace", "Furniture", 2, 1, seed=176)
def fireplace(S):
    fn = lambda f, s, w: rect(8, 18, 184, 64, fill=f, stroke=s, sw=w, r=3)
    S.add(S.shadowed(textured_shape(S, stone_pattern(S, "#a9a496", scale=0.55), (8, 18, 184, 64), fn, sw=2.8) + lit(S, fn), 5, 6, 4, 0.5))
    S.add(rect(32, 32, 136, 40, fill=S.lin([(0, "#0e0b09"), (1, "#2a1a12")]), stroke=OUT, sw=2.2, r=2))
    S.add(glow(S, 100, 56, 70, "#ff8a2a", 0.5))
    S.add(rot(rect(60, 56, 80, 11, fill=wood(S, "#5a3d22", True), stroke=OUT, sw=1.6, r=5), 6, 100, 60))
    S.add(rot(rect(64, 50, 74, 10, fill=wood(S, "#6b4a2b", True), stroke=OUT, sw=1.6, r=5), -8, 100, 55))
    S.add(g(flames(S, 80, 52, 0.55) + flames(S, 120, 52, 0.55) + flames(S, 100, 48, 0.7)))
    S.add(rect(8, 18, 184, 8, fill=S.lin([(0, "#cfcabd"), (1, "#8f8a7e")]), stroke=OUT, sw=2, r=2))


@asset("furniture/rug-red", "Red rug", "Furniture", 2, 3, seed=177)
def rug_red(S):
    S.add(S.shadowed(rect(14, 14, 172, 272, fill="#8a1c28", stroke=OUT, sw=2.4, r=6), 3, 4, 3, 0.4))
    S.add(rect(14, 14, 172, 272, fill=S.lin([(0, "#ffffff", 0.08), (1, "#000000", 0.18)], 0, 0, 1, 1), stroke=None, r=6))
    S.add(rect(26, 26, 148, 248, fill="none", stroke="#e8c24a", sw=3.4, r=4))
    S.add(rect(38, 38, 124, 224, fill="#6a1220", stroke="#d4a838", sw=1.6, r=3))
    # diamond medallion pattern
    S.add(poly([(100, 80), (150, 150), (100, 220), (50, 150)], fill="#2f4a8a", stroke="#e8c24a", sw=2.4))
    S.add(poly([(100, 108), (130, 150), (100, 192), (70, 150)], fill="#d4a838", stroke="#8a6a18", sw=1.6))
    S.add(circ(100, 150, 12, fill="#8a1c28", stroke="#e8c24a", sw=1.6))
    for y in (62, 238):
        for x in range(56, 150, 22):
            S.add(poly([(x, y - 7), (x + 7, y), (x, y + 7), (x - 7, y)], fill="#e8c24a", stroke=None, extra='opacity="0.9"'))
    S.add("".join(line(14 + i * 7.2, 10, 14 + i * 7.2, 14, stroke="#e8dcc0", sw=1.4) + line(14 + i * 7.2, 286, 14 + i * 7.2, 290, stroke="#e8dcc0", sw=1.4) for i in range(25)))


@asset("furniture/rug-round", "Round rug", "Furniture", 2, 2, seed=178)
def rug_round(S):
    S.add(S.shadowed(circ(100, 100, 86, fill="#2f6a58", stroke=OUT, sw=2.4), 3, 4, 3, 0.4))
    S.add(circ(100, 100, 86, fill=S.rad([(0, "#ffffff", 0.1), (1, "#000000", 0.22)], 0.4, 0.35, 0.9), stroke=None))
    for rr, c in ((76, "#e8c24a"), (62, "#a8283a"), (46, "#efe3c2"), (30, "#2f4a8a")):
        S.add(circ(100, 100, rr, fill=c if rr in (62, 30) else "none", stroke=c, sw=3))
    for i in range(12):
        a = 2 * math.pi * i / 12
        S.add(poly(regular(100 + math.cos(a) * 54, 100 + math.sin(a) * 54, 6, 4, 0), fill="#e8c24a", stroke=None))
    S.add(poly(regular(100, 100, 20, 8), fill="#e8c24a", stroke="#8a6a18", sw=1.4))


@asset("furniture/wardrobe", "Wardrobe", "Furniture", 2, 1, seed=179)
def wardrobe(S):
    S.add(S.shadowed(rect(10, 22, 180, 56, fill=wood(S, "#7a4a28"), stroke=OUT, sw=2.6, r=3), 4, 5, 3.5, 0.5))
    S.add(rect(18, 28, 164, 44, fill=S.lin([(0, "#8a5a32"), (1, "#5e3b1a")]), stroke="#3a2614", sw=1.8, r=2))
    S.add(path("M100 28V72M18 50H182", stroke="#3a2614", sw=1.6, extra='opacity="0.6"'))
    for x in (60, 140):
        S.add(rect(x - 18, 34, 36, 32, fill="none", stroke="#c89a64", sw=1.3, r=2, extra='opacity="0.7"'))
    S.add(circ(94, 50, 3.6, fill=S.rad([(0, "#ffe27a"), (1, "#b8902c")]), stroke=OUT, sw=1) + circ(106, 50, 3.6, fill=S.rad([(0, "#ffe27a"), (1, "#b8902c")]), stroke=OUT, sw=1))
    S.add(rect(10, 22, 180, 56, fill=S.lin([(0, "#ffffff", 0.14), (1, "#000000", 0.22)]), stroke=None, r=3))


@asset("furniture/desk", "Writing desk", "Furniture", 2, 1, seed=180)
def desk(S):
    S.add(S.shadowed(textured(S, plank_pattern(S, "#7a4a28"), 10, 20, 180, 60, r=4, sw=2.6), 4, 5, 3.5, 0.5))
    S.add(rect(10, 20, 180, 60, fill=S.lin([(0, "#ffffff", 0.14), (1, "#000000", 0.24)]), stroke=None, r=4))
    S.add(S.shadowed(rect(34, 30, 56, 40, fill="#f1e7c8", stroke=OUT, sw=1.4, r=1) + path("M42 40h40M42 48h40M42 56h28", stroke="#8a7a58", sw=1.1), 1.5, 2, 1.5, 0.45))
    S.add(S.shadowed(rect(100, 36, 26, 30, fill="#e3d6a8", stroke=OUT, sw=1.3, r=1), 1.5, 2, 1.5, 0.45))
    S.add(S.shadowed(circ(146, 46, 7, fill=S.rad([(0, "#6b7078"), (1, "#25272b")]), stroke=OUT, sw=1.4), 1.5, 2, 1.5, 0.5))
    S.add(path("M138 60L170 38", stroke="#efe8d6", sw=3.2) + path("M168 40l6 -3", stroke="#2b211c", sw=2))
    S.add(candle_(S, 170, 66))


@asset("furniture/bar-counter", "Bar counter", "Furniture", 3, 1, seed=181)
def bar_counter(S):
    r = S.rnd
    S.add(S.shadowed(textured(S, plank_pattern(S, "#6e4526"), 10, 22, 280, 56, r=8, sw=2.8), 5, 6, 4, 0.5))
    S.add(rect(10, 22, 280, 56, fill=S.lin([(0, "#ffffff", 0.16), (1, "#000000", 0.26)]), stroke=None, r=8))
    S.add(rect(18, 28, 264, 44, fill="none", stroke="#c89a64", sw=1.4, r=5, extra='opacity="0.6"'))
    for i in range(9):
        x = 34 + i * 29
        S.add(S.shadowed(circ(x, 40 + (i % 2) * 16, 6.5, fill=S.rad([(0, "#e8c070"), (1, "#a87828")]), stroke=OUT, sw=1.3), 1.5, 2, 1.2, 0.5))
        S.add(circ(x, 40 + (i % 2) * 16, 3.4, fill="#f6e8c0", stroke=None, extra='opacity="0.9"'))
    S.add(S.shadowed(circ(250, 50, 12, fill=S.rad([(0, "#9a6a38"), (1, "#5e3b1a")]), stroke=OUT, sw=1.8), 2, 3, 2, 0.5))


@asset("furniture/cauldron", "Cauldron", "Furniture", 1, 1, seed=182)
def cauldron(S):
    r = S.rnd
    S.add(glow(S, 50, 52, 46, "#ff7a1a", 0.35))
    S.add(S.shadowed(circ(50, 50, 34, fill=S.rad([(0, "#6b7078"), (1, "#1c1d21")], 0.38, 0.33, 0.85), stroke=OUT, sw=2.6), 3, 4, 3, 0.5))
    S.add(circ(50, 50, 27, fill=S.rad([(0, "#9aea6a"), (0.6, "#4aa03a"), (1, "#245a22")]), stroke="#15161a", sw=2))
    for _ in range(9):
        S.add(circ(32 + r.random() * 36, 32 + r.random() * 36, 1.8 + r.random() * 3.4, fill="none", stroke="#d6f8b0", sw=1.2, extra='opacity="0.85"'))
    for i in range(3):
        a = 2 * math.pi * i / 3 + 0.3
        S.add(S.shadowed(circ(50 + math.cos(a) * 36, 50 + math.sin(a) * 36, 5, fill="#25272b", stroke=OUT, sw=1.2), 1.5, 2, 1.2, 0.5))


@asset("furniture/stool", "Stool", "Furniture", 1, 1, seed=183)
def stool(S):
    S.add(S.shadowed(circ(50, 50, 22, fill=S.rad([(0, "#cfa070"), (1, "#7a5230")], 0.38, 0.33, 0.85), stroke=OUT, sw=2.2), 3, 4, 3, 0.5))
    S.add(circ(50, 50, 16, fill="none", stroke="#6e4a28", sw=1.2, extra='opacity="0.7"'))
    S.add(circ(50, 50, 7, fill="none", stroke="#6e4a28", sw=1, extra='opacity="0.7"'))


@asset("furniture/armchair", "Armchair", "Furniture", 1, 1, seed=184)
def armchair(S):
    S.add(S.shadowed(rect(16, 16, 68, 20, fill=S.lin([(0, "#a8283a"), (1, "#6a1422")]), stroke=OUT, sw=2.2, r=8), 3, 4, 3, 0.5))
    S.add(S.shadowed(rect(14, 28, 18, 56, fill=S.lin([(0, "#b8303f"), (1, "#7a1a28")], 0, 0, 1, 0), stroke=OUT, sw=2.2, r=7), 3, 4, 3, 0.5))
    S.add(S.shadowed(rect(68, 28, 18, 56, fill=S.lin([(0, "#b8303f"), (1, "#7a1a28")], 0, 0, 1, 0), stroke=OUT, sw=2.2, r=7), 3, 4, 3, 0.5))
    S.add(rect(30, 32, 40, 50, fill=S.lin([(0, "#c43a4a"), (1, "#8a2230")], 0, 0, 1, 1), stroke=OUT, sw=2, r=6))
    S.add(circ(50, 56, 3, fill="#e8c24a", stroke=OUT, sw=1))


@asset("furniture/bathtub", "Bathtub", "Furniture", 1, 2, seed=185)
def bathtub(S):
    S.add(S.shadowed(rect(12, 10, 76, 180, fill=S.lin([(0, "#c98a52"), (1, "#8a5a2b")], 0, 0, 1, 0), stroke=OUT, sw=2.6, r=30), 4, 5, 4, 0.5))
    S.add(rect(20, 18, 60, 164, fill=S.lin([(0, "#8fd0f4"), (1, "#4f9ad8")], 0, 0, 0, 1), stroke="#5e3b1a", sw=2, r=24))
    S.add(ell(42, 60, 12, 30, fill="#e6f6ff", stroke=None, extra='opacity="0.5"'))
    for (x, y, rr) in ((34, 110, 6), (56, 134, 5), (46, 90, 4)):
        S.add(circ(x, y, rr, fill="#ffffff", stroke="#a8d4ee", sw=1, extra='opacity="0.85"'))
    S.add(S.shadowed(circ(50, 24, 5, fill=S.rad([(0, "#ffe27a"), (1, "#a8842c")]), stroke=OUT, sw=1.2), 1.5, 2, 1.2, 0.5))


@asset("furniture/stove", "Stove", "Furniture", 1, 1, seed=186)
def stove(S):
    S.add(S.shadowed(rect(14, 14, 72, 72, fill=S.lin([(0, "#4a4d54"), (1, "#1c1d21")], 0, 0, 1, 1), stroke=OUT, sw=2.6, r=4), 4, 5, 3.5, 0.5))
    for (x, y) in ((34, 34), (66, 34), (34, 66), (66, 66)):
        S.add(circ(x, y, 13, fill=S.rad([(0, "#5e626b"), (1, "#25272b")]), stroke="#0e0f12", sw=1.8))
        S.add(circ(x, y, 8, fill="none", stroke="#3a3d44", sw=1.4))
    S.add(circ(66, 34, 9, fill=S.rad([(0, "#ff8a2a", 0.55), (1, "#c2300c", 0)]), stroke=None))
    S.add(circ(50, 8, 7, fill=S.rad([(0, "#6b7078"), (1, "#25272b")]), stroke=OUT, sw=1.6))


@asset("furniture/candelabra", "Candelabra", "Furniture", 1, 1, seed=187)
def candelabra(S):
    S.add(glow(S, 50, 50, 46, "#ffb347", 0.5))
    S.add(S.shadowed(path("M20 56Q50 70 80 56", stroke="#d4a838", sw=4) + path("M50 40V66", stroke="#d4a838", sw=4) + circ(50, 68, 8, fill=S.rad([(0, "#ffe27a"), (1, "#a8842c")]), stroke=OUT, sw=1.6), 2, 3, 2, 0.5))
    for (x, y) in ((24, 54), (50, 38), (76, 54)):
        S.add(S.shadowed(circ(x, y, 6, fill=S.rad([(0, "#fff8e0"), (1, "#d8c89a")]), stroke=OUT, sw=1.2), 1.5, 2, 1.2, 0.5))
        S.add(circ(x, y, 2.6, fill="#ffb02a", stroke=None))


@asset("furniture/pew", "Church pew", "Furniture", 2, 1, seed=188)
def pew(S):
    S.add(S.shadowed(rect(8, 30, 184, 40, fill=wood(S, "#8a5a2b"), stroke=OUT, sw=2.6, r=3), 4, 5, 3.5, 0.5))
    S.add(rect(8, 30, 184, 14, fill=S.lin([(0, "#6e4526"), (1, "#4a2e16")]), stroke=OUT, sw=2, r=3))
    S.add(path("M14 56H186M14 62H186", stroke="#5e3b1a", sw=1.2, extra='opacity="0.7"'))
    S.add(rect(8, 30, 184, 40, fill=S.lin([(0, "#ffffff", 0.14), (1, "#000000", 0.22)]), stroke=None, r=3))
