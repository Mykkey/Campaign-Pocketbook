from svglib import *

# Path tiles connect through the middle of their edges: the road band is y 18..82 (dirt) or 14..86
# (stone) at x = 0 / 100 and likewise at the top/bottom edges.

DIRT = "#c4a068"
STONE_ROAD = "#9a968e"


def dirt_ground(S, shape_fn, bbox, seed_off=0):
    """Packed dirt with ruts, pebbles and speckle inside any shape."""
    r = random.Random(900 + seed_off)
    fill = S.lin([(0, shade(DIRT, 1.08)), (1, shade(DIRT, 0.86))], 0, 0, 1, 1)
    x, y, w, h = bbox
    clip = shape_fn("#000", None, 0)
    inner = rect(x, y, w, h, fill=fill, stroke=None)
    inner += scatter(S, r, x, y, w, h, 60, ["#9c7a48", "#d6b784", "#8a6a3c"], 0.8, 2.2, 0.75)
    for _ in range(14):
        px, py = x + r.random() * w, y + r.random() * h
        inner += poly(rockpoly(r, px, py, 2 + r.random() * 3, 1.5 + r.random() * 2.5, 6), fill="#a09a8e", stroke="#5a5348", sw=0.8)
    cid = S.uid("c")
    S.defs.append(f'<clipPath id="{cid}">{clip}</clipPath>')
    edge = shape_fn(None, "#8a6a3c", 3)
    return g(inner, extra=f'clip-path="url(#{cid})"') + g(edge, extra='opacity="0.8"')


def stone_ground(S, shape_fn, bbox):
    tile = cobble_pattern(S, STONE_ROAD, scale=0.62)
    return textured_shape(S, tile, bbox, shape_fn, stroke="#55514a", sw=3)


@asset("paths/dirt-straight", "Dirt road", "Paths", 1, 1, seed=61)
def dirt_straight(S):
    fn = lambda f, s, w: rect(-2, 18, 104, 64, fill=f, stroke=s, sw=w)
    S.add(dirt_ground(S, fn, (0, 18, 100, 64)))
    r = S.rnd
    # wheel ruts
    S.add(path("M0 38H100M0 62H100", stroke="#8a6a3c", sw=3, extra='opacity="0.35" stroke-dasharray="26 8"'))
    S.add(tufts(r, 0, 12, 100, 8, 8, ["#6a9a44", "#88b858"], 7))
    S.add(tufts(r, 0, 80, 100, 8, 8, ["#6a9a44", "#88b858"], 7))


@asset("paths/dirt-corner", "Dirt road corner", "Paths", 1, 1, seed=62)
def dirt_corner(S):
    fn = lambda f, s, w: path("M0 18Q82 18 82 100H18Q18 82 0 82Z", fill=f, stroke=s, sw=w)
    S.add(dirt_ground(S, fn, (0, 18, 82, 82), 1))
    S.add(path("M0 38Q62 38 62 100M0 62Q38 62 38 100", stroke="#8a6a3c", sw=3, extra='opacity="0.35" stroke-dasharray="26 8"'))


@asset("paths/dirt-cross", "Dirt crossroads", "Paths", 1, 1, seed=63)
def dirt_cross(S):
    fn = lambda f, s, w: path("M0 18H18V0H82V18H100V82H82V100H18V82H0Z", fill=f, stroke=s, sw=w)
    S.add(dirt_ground(S, fn, (0, 0, 100, 100), 2))
    S.add(ell(50, 50, 22, 18, fill="#b88e56", stroke=None, extra='opacity="0.5"'))


@asset("paths/dirt-t", "Dirt T-junction", "Paths", 1, 1, seed=64)
def dirt_t(S):
    fn = lambda f, s, w: path("M0 18H100V82H82V100H18V82H0Z", fill=f, stroke=s, sw=w)
    S.add(dirt_ground(S, fn, (0, 18, 100, 82), 3))


@asset("paths/dirt-end", "Dirt road end", "Paths", 1, 1, seed=65)
def dirt_end(S):
    fn = lambda f, s, w: path("M0 18H60Q82 18 82 40V60Q82 82 60 82H0Z", fill=f, stroke=s, sw=w)
    S.add(dirt_ground(S, fn, (0, 18, 82, 64), 4))


@asset("paths/stone-straight", "Stone road", "Paths", 1, 1, seed=66)
def stone_straight(S):
    fn = lambda f, s, w: rect(-2, 14, 104, 72, fill=f, stroke=s, sw=w)
    S.add(S.shadowed(stone_ground(S, fn, (0, 14, 100, 72)), 1.5, 2, 2, 0.3))
    S.add(rect(-2, 14, 104, 72, fill=S.lin([(0, "#ffffff", 0.14), (1, "#000000", 0.16)], 0, 0, 0, 1), stroke=None))


@asset("paths/stone-corner", "Stone road corner", "Paths", 1, 1, seed=67)
def stone_corner(S):
    fn = lambda f, s, w: path("M0 14Q86 14 86 100H14Q14 86 0 86Z", fill=f, stroke=s, sw=w)
    S.add(S.shadowed(stone_ground(S, fn, (0, 14, 86, 86)), 1.5, 2, 2, 0.3))
    S.add(fn(S.lin([(0, "#ffffff", 0.14), (1, "#000000", 0.16)], 0, 0, 1, 1), None, 0))


@asset("paths/stone-cross", "Stone crossroads", "Paths", 1, 1, seed=68)
def stone_cross(S):
    fn = lambda f, s, w: path("M0 14H14V0H86V14H100V86H86V100H14V86H0Z", fill=f, stroke=s, sw=w)
    S.add(S.shadowed(stone_ground(S, fn, (0, 0, 100, 100)), 1.5, 2, 2, 0.3))
    S.add(circ(50, 50, 16, fill=S.rad([(0, "#c9c5bc"), (1, "#8f8b83")]), stroke="#55514a", sw=2))
    S.add(circ(50, 50, 7, fill=None, stroke="#55514a", sw=1.4))


@asset("paths/stone-t", "Stone T-junction", "Paths", 1, 1, seed=69)
def stone_t(S):
    fn = lambda f, s, w: path("M0 14H100V86H86V100H14V86H0Z", fill=f, stroke=s, sw=w)
    S.add(S.shadowed(stone_ground(S, fn, (0, 14, 100, 86)), 1.5, 2, 2, 0.3))


@asset("paths/bridge", "Bridge", "Paths", 2, 1, seed=70)
def bridge(S):
    S.add(S.shadowed(rect(0, 14, 200, 72, fill="#8f6a3e", stroke=None), 3, 5, 3, 0.5))
    S.add(S.tiles(plank_pattern(S, "#a9794a", vertical=True), 0, 14, 200, 72))
    S.add(rect(0, 14, 200, 72, fill=S.lin([(0, "#ffffff", 0.16), (1, "#000000", 0.22)], 0, 0, 0, 1), stroke=None))
    for y in (14, 86):
        S.add(rect(0, y - 5, 200, 10, fill=S.lin([(0, "#c8975a"), (1, "#7a5230")]), stroke=OUT, sw=2, r=3))
    for x in (0, 50, 100, 150, 200):
        for y in (14, 86):
            S.add(S.shadowed(circ(x, y, 7.5, fill=S.rad([(0, "#cfa070"), (1, "#6e4528")], 0.4, 0.35, 0.8), stroke=OUT, sw=1.8), 2, 3, 2, 0.5))


@asset("paths/stone-bridge", "Stone bridge", "Paths", 2, 1, seed=71)
def stone_bridge(S):
    S.add(S.shadowed(rect(0, 12, 200, 76, fill="#8b877c", stroke=None), 3, 5, 3, 0.5))
    S.add(S.tiles(cobble_pattern(S, "#a39f96", scale=0.62), 0, 20, 200, 60))
    S.add(rect(0, 20, 200, 60, fill=S.lin([(0, "#ffffff", 0.12), (0.5, "#ffffff", 0.0), (1, "#000000", 0.18)], 1, 0, 0, 0), stroke=None))
    fn_top = lambda f, s, w: rect(0, 8, 200, 14, fill=f, stroke=s, sw=w, r=2)
    fn_bot = lambda f, s, w: rect(0, 78, 200, 14, fill=f, stroke=s, sw=w, r=2)
    for fn in (fn_top, fn_bot):
        S.add(S.shadowed(textured_shape(S, stone_pattern(S, "#b3aea0", scale=0.4), (0, 8, 200, 84), fn, sw=2.2) + lit(S, fn), 2, 3, 2, 0.5))
    for x in (0, 200):
        for y in (15, 85):
            S.add(rect(x - 8, y - 9, 16, 18, fill="#c2bdaf", stroke=OUT, sw=2, r=2))


@asset("paths/rope-bridge", "Rope bridge", "Paths", 2, 1, seed=72)
def rope_bridge(S):
    r = S.rnd
    for i in range(18):
        x = 6 + i * 10.6
        S.add(S.shadowed(rect(x, 24, 8.4, 52, fill=jitter("#a67c4a", r, 0.1), stroke=OUT, sw=1.3, r=1), 1, 2, 1.2, 0.4))
    for y in (22, 78):
        S.add(path(f"M0 {y}Q50 {y + (-6 if y < 50 else 6)} 100 {y}T200 {y}", stroke="#d7c79b", sw=4))
        S.add(path(f"M0 {y}Q50 {y + (-6 if y < 50 else 6)} 100 {y}T200 {y}", stroke="#6e5a34", sw=1.2))
    for x in (4, 196):
        for y in (22, 78):
            S.add(circ(x, y, 7, fill=S.rad([(0, "#cfa070"), (1, "#6e4528")]), stroke=OUT, sw=1.8))


@asset("paths/stairs", "Stairs", "Paths", 1, 1, seed=73)
def stairs(S):
    S.add(S.shadowed(rect(6, 6, 88, 88, fill="#bdb9b0", stroke=OUT, sw=2.4, r=2), 3, 4, 3, 0.45))
    for i in range(7):
        y = 9 + i * 12.2
        shade_f = 1.1 - i * 0.045
        S.add(rect(9, y, 82, 11.4, fill=S.lin([(0, shade("#cfcbc2", shade_f + 0.1)), (1, shade("#cfcbc2", shade_f - 0.12))]), stroke="#6f6b63", sw=1.2))
    S.add(path("M50 14V86", stroke="#4a463f", sw=2.6))
    S.add(path("M42 78L50 90L58 78", stroke="#4a463f", sw=2.6))


@asset("paths/spiral-stairs", "Spiral stairs", "Paths", 1, 1, seed=74)
def spiral_stairs(S):
    S.add(S.shadowed(circ(50, 50, 44, fill="#bdb9b0", stroke=OUT, sw=2.6), 3, 4, 3, 0.45))
    for i in range(14):
        a0, a1 = 2 * math.pi * i / 14, 2 * math.pi * (i + 1) / 14
        l = 0.5 + 0.5 * math.cos((a0 + a1) / 2 + math.radians(135))
        S.add(poly([(50, 50), (50 + math.cos(a0) * 42, 50 + math.sin(a0) * 42), (50 + math.cos(a1) * 42, 50 + math.sin(a1) * 42)],
                   fill=mix("#a3a097", "#e2dfd6", l), stroke="#6f6b63", sw=1))
    S.add(circ(50, 50, 9, fill=S.rad([(0, "#d8d4cb"), (1, "#8a867c")]), stroke=OUT, sw=2))


@asset("paths/stepping-stones", "Stepping stones", "Paths", 1, 1, seed=75)
def stepping_stones(S):
    r = S.rnd
    for cx, cy, rx, ry in ((22, 28, 15, 11), (52, 52, 16, 12), (80, 76, 14, 11), (78, 24, 10, 8), (24, 78, 9, 7)):
        S.add(S.shadowed(path(blob(r, cx, cy, rx, ry, 9, 0.1), fill=S.rad([(0, "#c4c0b6"), (1, "#8d897f")], 0.4, 0.35, 0.8), stroke=OUT, sw=2), 2, 3, 2, 0.5))
        S.add(ell(cx - rx * 0.25, cy - ry * 0.3, rx * 0.4, ry * 0.3, fill="#ffffff", stroke=None, extra='opacity="0.35"'))


@asset("paths/plank-walkway", "Plank walkway", "Paths", 2, 1, seed=76)
def plank_walkway(S):
    fn = lambda f, s, w: rect(0, 30, 200, 40, fill=f, stroke=s, sw=w)
    body = textured_shape(S, plank_pattern(S, "#a37a4a", vertical=True), (0, 30, 200, 40), fn, sw=2.2) + lit(S, fn)
    S.add(S.shadowed(body, 3, 4, 2.5, 0.45))


@asset("paths/river-straight", "River", "Paths", 1, 1, seed=77)
def river_straight(S):
    r = S.rnd
    water = S.lin([(0, "#4f9ad8"), (0.5, "#2f78bd"), (1, "#4f9ad8")])
    S.add(rect(0, 12, 100, 76, fill="#7a9a54", stroke=None))
    S.add(rect(0, 18, 100, 64, fill=water, stroke="#2c5f8f", sw=2.4))
    S.add(path("M0 18H100M0 82H100", stroke="#a8d4f4", sw=5, extra='opacity="0.45"'))
    for i in range(7):
        x, y = 8 + i * 14 + r.random() * 6, 28 + r.random() * 44
        S.add(path(f"M{n(x)} {n(y)}q6 -4 12 0", stroke="#e6f4ff", sw=1.8, extra='opacity="0.75"'))


@asset("paths/river-bend", "River bend", "Paths", 1, 1, seed=78)
def river_bend(S):
    r = S.rnd
    bank = "M0 12Q88 12 88 100H12Q12 88 0 88Z"
    S.add(path("M0 12Q88 12 88 100H12Q12 88 0 88Z", fill="#7a9a54", stroke=None))
    S.add(path("M0 18Q82 18 82 100H18Q18 82 0 82Z", fill=S.lin([(0, "#4f9ad8"), (1, "#2f78bd")], 0, 0, 1, 1), stroke="#2c5f8f", sw=2.4))
    for i in range(5):
        t = 0.15 + i * 0.17
        a = t * math.pi / 2
        x, y = 82 * math.sin(a) * 0.62 + 4, 100 - 82 * math.cos(a) * 0.62 + 4
        S.add(path(f"M{n(x)} {n(y)}q5 -3 10 0", stroke="#e6f4ff", sw=1.8, extra='opacity="0.75"'))


@asset("paths/ford", "River ford", "Paths", 1, 1, seed=79)
def ford(S):
    r = S.rnd
    S.add(rect(0, 12, 100, 76, fill="#7a9a54", stroke=None))
    S.add(rect(0, 18, 100, 64, fill=S.lin([(0, "#6fb0de"), (1, "#4f9ad8")]), stroke="#2c5f8f", sw=2.4))
    S.add(path("M44 18V82", stroke="#cdbb8a", sw=26, extra='opacity="0.75"'))
    for i in range(8):
        S.add(poly(rockpoly(r, 36 + r.random() * 28, 24 + i * 7, 4 + r.random() * 3, 3 + r.random() * 2, 6), fill=jitter("#9a968c", r, 0.15), stroke="#4a463f", sw=1))
