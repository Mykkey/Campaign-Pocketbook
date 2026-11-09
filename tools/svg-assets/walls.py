from svglib import *

STONE = "#aaa597"
T = 40          # wall thickness in units (a wall fills y 30..70 of its cell)
Y0, Y1 = 30, 70


def stone_tile(S, base=STONE):
    return stone_pattern(S, base, scale=0.5)


def wall_shape_fn(pts):
    return lambda fill, stroke, sw: poly(pts, fill=fill, stroke=stroke, sw=sw)


def stone_poly(S, pts, bbox, base=STONE, diagonal=True, shadow=True):
    """A stone wall piece with block texture, lighting and a ground shadow."""
    fn = wall_shape_fn(pts)
    body = textured_shape(S, stone_tile(S, base), bbox, fn, sw=2.6) + lit(S, fn, diagonal=diagonal)
    return S.shadowed(body, 4, 5, 3.5, 0.5) if shadow else body


def stone_rect(S, x, y, w, h, base=STONE, vertical=False, shadow=True):
    fn = lambda fill, stroke, sw: rect(x, y, w, h, fill=fill, stroke=stroke, sw=sw)
    body = textured_shape(S, stone_tile(S, base), (x, y, w, h), fn, sw=2.6) + lit(S, fn, vertical=vertical)
    return S.shadowed(body, 4, 5, 3.5, 0.5) if shadow else body


def merlons(x0, y, count, size=18, gap=None, base="#b6b1a3", horizontal=True, step=None):
    out = ""
    step = step or size * 2
    for i in range(count):
        out += rect(x0 + i * step, y, size, size * 0.7, fill=base, stroke=OUT, sw=1.8, r=1)
    return out


@asset("walls/straight", "Wall", "Walls", 2, 1, seed=31)
def straight(S):
    S.add(stone_rect(S, 0, Y0, 200, T))


@asset("walls/corner", "Wall corner", "Walls", 1, 1, seed=32)
def corner(S):
    pts = [(0, Y0), (Y1, Y0), (Y1, 100), (Y0, 100), (Y0, Y1), (0, Y1)]
    S.add(stone_poly(S, pts, (0, 0, 100, 100)))


@asset("walls/t-junction", "Wall T-junction", "Walls", 1, 1, seed=33)
def t_junction(S):
    pts = [(0, Y0), (100, Y0), (100, Y1), (Y1, Y1), (Y1, 100), (Y0, 100), (Y0, Y1), (0, Y1)]
    S.add(stone_poly(S, pts, (0, 0, 100, 100)))


@asset("walls/crossing", "Wall crossing", "Walls", 1, 1, seed=34)
def crossing(S):
    pts = [(0, Y0), (Y0, Y0), (Y0, 0), (Y1, 0), (Y1, Y0), (100, Y0), (100, Y1), (Y1, Y1), (Y1, 100), (Y0, 100), (Y0, Y1), (0, Y1)]
    S.add(stone_poly(S, pts, (0, 0, 100, 100)))


@asset("walls/end", "Wall end", "Walls", 1, 1, seed=35)
def wall_end(S):
    body = (textured_shape(S, stone_tile(S), (0, 0, 100, 100),
                           lambda f, s, w: path(f"M0 {Y0}H62a20 20 0 0 1 0 {T}H0Z", fill=f, stroke=s, sw=w))
            + lit(S, lambda f, s, w: path(f"M0 {Y0}H62a20 20 0 0 1 0 {T}H0Z", fill=f, stroke=s, sw=w)))
    S.add(S.shadowed(body, 4, 5, 3.5, 0.5))


@asset("walls/gate", "Gatehouse", "Walls", 3, 1, seed=36)
def gate(S):
    S.add(stone_rect(S, 0, Y0, 100, T))
    S.add(stone_rect(S, 200, Y0, 100, T))
    # the passage: cobbled floor with heavy wooden doors
    S.add(rect(100, 36, 100, 28, fill="#7b7468", stroke=None))
    S.add(S.tiles(cobble_pattern(S, "#97918a", scale=0.5), 100, 36, 100, 28))
    for x0 in (100, 150):
        S.add(S.shadowed(textured(S, plank_pattern(S, "#6f4b2b", vertical=True), x0 + 2, 40, 46, 20, r=1, sw=2), 2, 3, 2, 0.5))
        S.add(path(f"M{x0 + 6} 42V58M{x0 + 42} 42V58", stroke="#2b211c", sw=1.6))
    S.add(circ(146, 50, 3.4, fill="#d4b246", stroke=OUT, sw=1.2))
    S.add(circ(154, 50, 3.4, fill="#d4b246", stroke=OUT, sw=1.2))
    # flanking towers
    for cx in (92, 208):
        fn = lambda f, s, w, cx=cx: rect(cx - 30, 10, 60, 80, fill=f, stroke=s, sw=w, r=6)
        tower = textured_shape(S, stone_tile(S, "#b4af9f"), (cx - 30, 10, 60, 80), fn, sw=2.8) + lit(S, fn, diagonal=True)
        S.add(S.shadowed(tower, 5, 6, 4, 0.5))
        S.add(rect(cx - 20, 20, 40, 60, fill=S.lin([(0, "#8a929b"), (1, "#5a626b")], 0, 0, 1, 1), stroke=OUT, sw=2, r=3))
        S.add(path(f"M{cx - 20} 50H{cx + 20}M{cx} 20V80", stroke="#3d444c", sw=1.4, extra='opacity="0.7"'))


@asset("walls/tower", "Wall tower", "Walls", 1, 1, seed=37)
def wall_tower(S):
    S.add(S.shadowed(textured_circle(S, stone_tile(S, "#b4af9f"), 50, 50, 46, sw=2.8), 4, 5, 3.5, 0.5))
    S.add(circ(50, 50, 46, fill=S.rad([(0, "#ffffff", 0.2), (0.6, "#ffffff", 0), (1, "#000000", 0.3)], 0.4, 0.35, 0.8), stroke=None))
    for i in range(12):
        a = 2 * math.pi * i / 12
        x, y = 50 + math.cos(a) * 41, 50 + math.sin(a) * 41
        S.add(rot(rect(x - 4.5, y - 3.5, 9, 7, fill="#bdb8aa", stroke=OUT, sw=1.2), math.degrees(a), x, y))
    cone = ""
    for i in range(10):
        a0, a1 = 2 * math.pi * i / 10, 2 * math.pi * (i + 1) / 10
        l = 0.5 + 0.5 * math.cos((a0 + a1) / 2 + math.radians(135))
        cone += poly([(50, 50), (50 + math.cos(a0) * 31, 50 + math.sin(a0) * 31), (50 + math.cos(a1) * 31, 50 + math.sin(a1) * 31)],
                     fill=mix("#566170", "#9eaabb", l), stroke="#3d4652", sw=0.9)
    S.add(cone)
    S.add(circ(50, 50, 31, fill=None, stroke=OUT, sw=1.8))
    S.add(circ(50, 50, 4, fill="#d8d3c8", stroke=OUT, sw=1.2))


@asset("walls/battlement", "Battlement", "Walls", 2, 1, seed=38)
def battlement(S):
    S.add(stone_rect(S, 0, 40, 200, 38))
    for i in range(5):
        x = 6 + i * 40
        fn = lambda f, s, w, x=x: rect(x, 24, 26, 18, fill=f, stroke=s, sw=w, r=1.5)
        m = textured_shape(S, stone_tile(S, "#b9b4a6"), (x, 24, 26, 18), fn, sw=2) + lit(S, fn)
        S.add(S.shadowed(m, 3, 4, 2.5, 0.5))
    S.add(path("M0 66H200", stroke="#6f6a5f", sw=1.5, extra='opacity="0.6"'))


@asset("walls/fence", "Fence", "Walls", 2, 1, seed=39)
def fence(S):
    rail = S.lin([(0, "#b98a58"), (1, "#7a5230")])
    S.add(S.shadowed(rect(2, 39, 196, 7, fill=rail, stroke=OUT, sw=1.6, r=2) + rect(2, 54, 196, 7, fill=rail, stroke=OUT, sw=1.6, r=2), 3, 4, 2.5, 0.45))
    for x in (10, 60, 110, 160, 190):
        S.add(S.shadowed(circ(x, 50, 8.5, fill=S.rad([(0, "#cfa070"), (1, "#7a5230")], 0.4, 0.35, 0.8), stroke=OUT, sw=1.8), 2, 3, 2, 0.5))
        S.add(circ(x, 50, 3, fill="#8a5a2b", stroke=None, extra='opacity="0.6"'))


@asset("walls/door", "Door", "Walls", 1, 1, seed=40)
def door(S):
    S.add(S.shadowed(rect(10, 26, 80, 48, fill="#7a746a", stroke=OUT, sw=2.2, r=2), 3, 4, 2.5, 0.45))
    S.add(S.tiles(plank_pattern(S, "#8a5a2b", vertical=True), 16, 31, 68, 38))
    S.add(rect(16, 31, 68, 38, fill=None, stroke=OUT, sw=2.2, r=1))
    S.add(rect(16, 31, 68, 38, fill=S.lin([(0, "#ffffff", 0.18), (1, "#000000", 0.25)], 0, 0, 1, 1), stroke=None))
    for x in (30, 70):
        S.add(path(f"M{x - 6} 38H{x + 6}M{x - 6} 62H{x + 6}", stroke="#2b2b2e", sw=3))
    S.add(circ(74, 50, 4, fill=S.rad([(0, "#ffe27a"), (1, "#b8902c")]), stroke=OUT, sw=1.4))


@asset("walls/window", "Window", "Walls", 1, 1, seed=41)
def window(S):
    S.add(S.shadowed(rect(12, 30, 76, 40, fill="#8d887c", stroke=OUT, sw=2.2, r=2), 3, 4, 2.5, 0.45))
    glass = S.lin([(0, "#d6eef9"), (0.55, "#8cc0de"), (1, "#5b8fb4")], 0, 0, 1, 1)
    S.add(rect(19, 36, 62, 28, fill=glass, stroke=OUT, sw=1.8, r=1))
    S.add(path("M50 36V64M19 50H81", stroke="#4a3a2a", sw=3))
    S.add(path("M25 42L40 40M57 42L72 40", stroke="#ffffff", sw=2.4, extra='opacity="0.7"'))


@asset("walls/double-door", "Double door", "Walls", 2, 1, seed=42)
def double_door(S):
    S.add(S.shadowed(rect(8, 24, 184, 52, fill="#7a746a", stroke=OUT, sw=2.4, r=3), 3, 4, 3, 0.45))
    for x in (14, 102):
        S.add(S.tiles(plank_pattern(S, "#845428", vertical=True), x, 30, 84, 40))
        S.add(rect(x, 30, 84, 40, fill=None, stroke=OUT, sw=2.2, r=1))
        S.add(rect(x, 30, 84, 40, fill=S.lin([(0, "#ffffff", 0.18), (1, "#000000", 0.25)], 0, 0, 1, 1), stroke=None))
        for xx in (x + 18, x + 66):
            S.add(path(f"M{xx - 7} 38H{xx + 7}M{xx - 7} 62H{xx + 7}", stroke="#2b2b2e", sw=3))
    S.add(circ(92, 50, 4.5, fill=S.rad([(0, "#ffe27a"), (1, "#b8902c")]), stroke=OUT, sw=1.4))
    S.add(circ(108, 50, 4.5, fill=S.rad([(0, "#ffe27a"), (1, "#b8902c")]), stroke=OUT, sw=1.4))


@asset("walls/portcullis", "Portcullis gate", "Walls", 2, 1, seed=43)
def portcullis(S):
    S.add(stone_rect(S, 0, Y0, 44, T))
    S.add(stone_rect(S, 156, Y0, 44, T))
    S.add(rect(44, 34, 112, 32, fill="#2d2b29", stroke=None))
    bars = "".join(line(52 + i * 12, 36, 52 + i * 12, 64, stroke="#6b7078", sw=3.4) for i in range(9))
    bars += "".join(line(46, 42 + i * 8, 154, 42 + i * 8, stroke="#575c63", sw=2.6) for i in range(3))
    S.add(S.shadowed(bars, 1.5, 2.5, 1.5, 0.6))
    for x in (44, 156):
        S.add(S.shadowed(rect(x - 8, 24, 16, 52, fill=S.lin([(0, "#bdb8aa"), (1, "#8a857a")], 0, 0, 1, 1), stroke=OUT, sw=2.2, r=2), 3, 4, 3, 0.5))


@asset("walls/broken", "Broken wall", "Walls", 2, 1, seed=44)
def broken(S):
    r = S.rnd
    pts1 = [(0, 30), (70, 30), (82, 40), (66, 48), (84, 60), (70, 70), (0, 70)]
    pts2 = [(122, 30), (200, 30), (200, 70), (116, 70), (130, 56), (118, 44)]
    S.add(stone_poly(S, pts1, (0, 0, 100, 100)))
    S.add(stone_poly(S, pts2, (100, 0, 100, 100)))
    for _ in range(18):
        x, y = 84 + r.random() * 36, 28 + r.random() * 44
        S.add(poly(rockpoly(r, x, y, 4 + r.random() * 5, 3 + r.random() * 4, 6), fill=jitter(STONE, r, 0.14), stroke="#4a443c", sw=1.2))
    S.add(tufts(r, 70, 30, 60, 40, 8, ["#5c8a3c", "#7aa84c"], 8))


@asset("walls/palisade", "Wooden palisade", "Walls", 2, 1, seed=45)
def palisade(S):
    r = S.rnd
    for i in range(10):
        x = 10 + i * 20
        rr = 11 + r.random() * 1.5
        S.add(S.shadowed(circ(x, 50 + (r.random() - 0.5) * 4, rr, fill=S.rad([(0, "#c89a64"), (0.7, "#8a5f35"), (1, "#5e3f22")], 0.38, 0.35, 0.85),
                          stroke=OUT, sw=1.8), 3, 4, 2.5, 0.5))
        S.add(circ(x - 1, 49, rr * 0.45, fill=None, stroke="#5e3f22", sw=1, extra='opacity="0.7"'))
        S.add(circ(x - 1, 49, rr * 0.2, fill="#e0b888", stroke=None, extra='opacity="0.8"'))


@asset("walls/palisade-corner", "Palisade corner", "Walls", 1, 1, seed=46)
def palisade_corner(S):
    r = S.rnd
    spots = [(10, 50), (30, 50), (50, 50), (50, 70), (50, 90)]
    for (x, y) in spots:
        S.add(S.shadowed(circ(x, y, 11.5, fill=S.rad([(0, "#c89a64"), (0.7, "#8a5f35"), (1, "#5e3f22")], 0.38, 0.35, 0.85), stroke=OUT, sw=1.8), 3, 4, 2.5, 0.5))
        S.add(circ(x - 1, y - 1, 5, fill=None, stroke="#5e3f22", sw=1, extra='opacity="0.7"'))
        S.add(circ(x - 1, y - 1, 2.2, fill="#e0b888", stroke=None, extra='opacity="0.8"'))


@asset("walls/hedge", "Hedge", "Walls", 2, 1, seed=47)
def hedge(S):
    r = S.rnd
    S.add(S.shadowed(path(blob(r, 100, 50, 98, 22, 16, 0.08), fill=S.lin([(0, "#6aa04a"), (1, "#356a2c")]), stroke="#264d20", sw=2.4), 3, 4, 3, 0.45))
    for _ in range(70):
        x, y = 8 + r.random() * 184, 34 + r.random() * 32
        S.add(circ(x, y, 3 + r.random() * 4, fill=jitter("#5a9440", r, 0.2), stroke=None, extra='opacity="0.9"'))
    for _ in range(55):
        S.add(circ(8 + r.random() * 184, 34 + r.random() * 32, 1 + r.random() * 1.4, fill=jitter("#9cd070", r, 0.12), stroke=None, extra='opacity="0.8"'))


@asset("walls/iron-fence", "Iron fence", "Walls", 2, 1, seed=48)
def iron_fence(S):
    S.add(S.shadowed(path("M0 42H200M0 58H200", stroke="#2c2f34", sw=3.4), 2, 3, 1.5, 0.5))
    for i in range(11):
        x = 6 + i * 18.8
        S.add(S.shadowed(circ(x, 50, 3.6, fill=S.rad([(0, "#6b7078"), (1, "#25272b")]), stroke="#15161a", sw=1.2), 2, 3, 1.5, 0.5))
        S.add(poly([(x, 42), (x - 2.2, 46), (x + 2.2, 46)], fill="#25272b", stroke=None))


@asset("walls/thin-straight", "Interior wall", "Walls", 2, 1, seed=49)
def thin_straight(S):
    fn = lambda f, s, w: rect(0, 42, 200, 16, fill=f, stroke=s, sw=w)
    body = textured_shape(S, plank_pattern(S, "#cdbf9c", gap="#8a7a58"), (0, 42, 200, 16), fn, sw=2.2) + lit(S, fn)
    S.add(S.shadowed(body, 3, 4, 2.5, 0.45))


@asset("walls/thin-corner", "Interior wall corner", "Walls", 1, 1, seed=50)
def thin_corner(S):
    pts = [(0, 42), (58, 42), (58, 100), (42, 100), (42, 58), (0, 58)]
    fn = wall_shape_fn(pts)
    body = textured_shape(S, plank_pattern(S, "#cdbf9c", gap="#8a7a58"), (0, 0, 100, 100), fn, sw=2.2) + lit(S, fn, diagonal=True)
    S.add(S.shadowed(body, 3, 4, 2.5, 0.45))


@asset("walls/thin-door", "Interior door", "Walls", 1, 1, seed=51)
def thin_door(S):
    S.add(S.shadowed(rect(0, 42, 12, 16, fill="#cdbf9c", stroke=OUT, sw=2.2) + rect(88, 42, 12, 16, fill="#cdbf9c", stroke=OUT, sw=2.2), 3, 4, 2.5, 0.45))
    # a door swung open
    S.add(S.shadowed(rect(12, 54, 16, 44, fill="#9a6a38", stroke=OUT, sw=2, r=1), 3, 4, 2.5, 0.5))
    S.add(path("M20 58V94", stroke="#6b4423", sw=1.2))
    S.add(circ(20, 92, 2.6, fill="#d4b246", stroke=OUT, sw=1))
    S.add(path("M12 56Q40 50 70 58", fill=None, stroke="#2b211c", sw=1.2, extra='stroke-dasharray="3 4" opacity="0.5"'))
