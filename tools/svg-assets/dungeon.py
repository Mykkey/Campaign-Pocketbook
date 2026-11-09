from svglib import *
from nature import faceted_rock

STONE = "#a9a498"


def glow(S, cx, cy, r, color="#ff9a2a", strength=0.5):
    return circ(cx, cy, r, fill=S.rad([(0, color, strength), (1, color, 0)]), stroke=None)


def candle(S, x, y):
    return (S.shadowed(circ(x, y, 4.2, fill=S.rad([(0, "#fff8e0"), (1, "#d8c89a")]), stroke=OUT, sw=1.2), 1.5, 2, 1.2, 0.5)
            + circ(x, y, 1.8, fill="#ffb02a", stroke=None) + glow(S, x, y, 14, "#ffb347", 0.55))


@asset("dungeon/pillar", "Stone pillar", "Dungeon", 1, 1, seed=141)
def pillar(S):
    S.add(S.shadowed(circ(50, 50, 32, fill=S.rad([(0, "#d2cec3"), (1, "#8a867b")], 0.38, 0.33, 0.85), stroke=OUT, sw=2.6), 4, 5, 4, 0.5))
    S.add(circ(50, 50, 24, fill=S.rad([(0, "#c4c0b4"), (1, "#8f8b80")], 0.4, 0.35, 0.8), stroke="#5a564d", sw=1.6))
    S.add(circ(50, 50, 14, fill=None, stroke="#6f6b60", sw=1.2, extra='opacity="0.8"'))
    S.add(S.grained(circ(50, 50, 32, fill="#000", stroke=None), 0.25, 0.9, 4))


@asset("dungeon/pillar-square", "Square pillar", "Dungeon", 1, 1, seed=142)
def pillar_square(S):
    fn = lambda f, s, w: rect(20, 20, 60, 60, fill=f, stroke=s, sw=w, r=2)
    body = textured_shape(S, stone_pattern(S, STONE, scale=0.5), (20, 20, 60, 60), fn, sw=2.6) + lit(S, fn, diagonal=True)
    S.add(S.shadowed(body, 4, 5, 3.5, 0.5))
    S.add(rect(30, 30, 40, 40, fill=S.lin([(0, "#c9c5ba"), (1, "#8a867b")], 0, 0, 1, 1), stroke="#5a564d", sw=1.6, r=1))


@asset("dungeon/statue", "Statue", "Dungeon", 1, 1, seed=143)
def statue(S):
    fn = lambda f, s, w: rect(16, 16, 68, 68, fill=f, stroke=s, sw=w, r=3)
    S.add(S.shadowed(textured_shape(S, stone_pattern(S, "#b2ada0", scale=0.5), (16, 16, 68, 68), fn, sw=2.6) + lit(S, fn, diagonal=True), 4, 5, 3.5, 0.5))
    stone = S.rad([(0, "#d7d3c8"), (1, "#8a867b")], 0.4, 0.35, 0.85)
    # a knight standing guard: broad shoulders, helmeted head, greatsword held point-down in front
    S.add(S.shadowed(ell(50, 54, 25, 13, fill=stone, stroke=OUT, sw=2), 2, 3, 2, 0.5))
    S.add(path("M30 50Q27 62 36 70M70 50Q73 62 64 70", stroke="#b6b2a6", sw=5))
    S.add(S.shadowed(circ(50, 48, 10.5, fill=stone, stroke=OUT, sw=2), 2, 3, 2, 0.5))
    S.add(path("M50 38V58", stroke="#6f6b60", sw=1.4, extra='opacity="0.8"'))
    S.add(S.shadowed(rect(48, 54, 4, 28, fill=S.lin([(0, "#e6e2d6"), (1, "#9a9689")], 0, 0, 1, 0), stroke=OUT, sw=1.4) + rect(41, 56, 18, 4, fill="#b6b2a6", stroke=OUT, sw=1.4, r=1.5), 1.5, 2, 1.5, 0.5))
    S.add(circ(46, 44, 3, fill="#ffffff", stroke=None, extra='opacity="0.4"'))


@asset("dungeon/altar", "Altar", "Dungeon", 2, 1, seed=144)
def altar(S):
    fn = lambda f, s, w: rect(12, 20, 176, 60, fill=f, stroke=s, sw=w, r=3)
    S.add(S.shadowed(textured_shape(S, stone_pattern(S, "#b8b3a6", scale=0.55), (12, 20, 176, 60), fn, sw=2.8) + lit(S, fn), 5, 6, 4, 0.5))
    S.add(rect(30, 32, 140, 36, fill=S.lin([(0, "#7a1f2a"), (1, "#4a1018")]), stroke=OUT, sw=1.8, r=2))
    S.add(path("M30 44H170M30 56H170", stroke="#d4b246", sw=1.6, extra='opacity="0.85"'))
    S.add(candle(S, 42, 38))
    S.add(candle(S, 158, 38))
    S.add(candle(S, 60, 62))
    S.add(S.shadowed(circ(100, 50, 9, fill=S.rad([(0, "#ffe27a"), (1, "#a8842c")]), stroke=OUT, sw=1.8) + circ(100, 50, 5, fill="#6a1220", stroke=None), 2, 3, 2, 0.5))


@asset("dungeon/sarcophagus", "Sarcophagus", "Dungeon", 1, 2, seed=145)
def sarcophagus(S):
    fn = lambda f, s, w: path("M26 12H74Q84 12 84 24V176Q84 188 74 188H26Q16 188 16 176V24Q16 12 26 12Z", fill=f, stroke=s, sw=w)
    S.add(S.shadowed(textured_shape(S, stone_pattern(S, "#aaa497", scale=0.7), (16, 12, 68, 176), fn, sw=2.8) + lit(S, fn, vertical=True), 4, 6, 4, 0.5))
    S.add(circ(50, 38, 12, fill=S.rad([(0, "#c9c5ba"), (1, "#8a867b")]), stroke="#4a463f", sw=1.6))
    S.add(path("M50 50V150M36 70H64M40 150H60", stroke="#5a564d", sw=1.8))
    S.add(path("M30 24H70M30 176H70", stroke="#5a564d", sw=1.4, extra='opacity="0.8"'))
    S.add(path("M40 100L56 118L48 140", stroke="#2b2b2e", sw=1.4, extra='opacity="0.6"'))


@asset("dungeon/trapdoor", "Trapdoor", "Dungeon", 1, 1, seed=146)
def trapdoor(S):
    S.add(S.shadowed(textured(S, plank_pattern(S, "#8a5a2b", vertical=True), 18, 18, 64, 64, sw=2.6), 3, 4, 3, 0.5))
    S.add(rect(18, 18, 64, 64, fill=S.lin([(0, "#ffffff", 0.14), (1, "#000000", 0.26)], 0, 0, 1, 1), stroke=None))
    S.add(rect(24, 24, 52, 52, fill="none", stroke="#2b2b2e", sw=3))
    for y in (30, 70):
        S.add(path(f"M18 {y}H34", stroke="#2b2b2e", sw=4))
    S.add(S.shadowed(circ(62, 50, 8, fill="none", stroke="#6b7078", sw=3.4), 1.5, 2, 1.2, 0.6))
    S.add(circ(62, 42, 2.4, fill="#8a929b", stroke=OUT, sw=1))


@asset("dungeon/pit", "Pit", "Dungeon", 1, 1, seed=147)
def pit(S):
    r = S.rnd
    S.add(circ(50, 50, 44, fill=S.rad([(0, "#050506"), (0.65, "#15151a"), (1, "#3a3a40")]), stroke=OUT, sw=2.6))
    for i in range(14):
        a = 2 * math.pi * i / 14 + r.random() * 0.3
        S.add(poly(rockpoly(r, 50 + math.cos(a) * 42, 50 + math.sin(a) * 42, 7 + r.random() * 4, 5 + r.random() * 3, 6), fill=jitter("#8d897f", r, 0.14), stroke="#3a362f", sw=1.2))
    S.add(circ(50, 50, 28, fill=S.rad([(0, "#000000", 0.0), (1, "#000000", 0.5)]), stroke=None))


@asset("dungeon/spike-trap", "Spike trap", "Dungeon", 1, 1, seed=148)
def spike_trap(S):
    S.add(S.shadowed(rect(12, 12, 76, 76, fill=S.lin([(0, "#8d8a82"), (1, "#5e5b54")], 0, 0, 1, 1), stroke=OUT, sw=2.4, r=3), 3, 4, 3, 0.5))
    for i in range(5):
        for j in range(5):
            x, y = 24 + i * 13, 24 + j * 13
            S.add(circ(x, y, 3.6, fill="#3a3a3e", stroke=None))
            S.add(poly([(x, y - 5.5), (x - 3.4, y + 2.4), (x + 3.4, y + 2.4)], fill=S.lin([(0, "#e8ecf2"), (1, "#7a8089")], 0, 0, 1, 1), stroke="#2b2b2e", sw=0.9))


@asset("dungeon/bones", "Bones", "Dungeon", 1, 1, seed=149)
def bones(S):
    bone = "#efe8d6"
    S.add(S.shadowed(path("M22 70L70 30", stroke=bone, sw=6) + circ(18, 74, 5, fill=bone, stroke=OUT, sw=1.2) + circ(24, 78, 5, fill=bone, stroke=OUT, sw=1.2)
                     + circ(76, 26, 5, fill=bone, stroke=OUT, sw=1.2) + circ(70, 22, 5, fill=bone, stroke=OUT, sw=1.2), 2, 3, 2, 0.5))
    S.add(S.shadowed(path("M30 28L74 74", stroke=bone, sw=6) + circ(26, 24, 5, fill=bone, stroke=OUT, sw=1.2) + circ(34, 24, 5, fill=bone, stroke=OUT, sw=1.2), 2, 3, 2, 0.5))
    S.add(S.shadowed(circ(52, 50, 14, fill=S.rad([(0, "#fffaf0"), (1, "#cfc7b0")], 0.4, 0.35, 0.85), stroke=OUT, sw=1.8), 2, 3, 2, 0.5))
    S.add(circ(46, 47, 3.6, fill="#1a1612", stroke=None) + circ(58, 47, 3.6, fill="#1a1612", stroke=None) + path("M48 60h8M50 58v4M54 58v4", stroke="#1a1612", sw=1.2))


@asset("dungeon/treasure-pile", "Treasure pile", "Dungeon", 1, 1, seed=150)
def treasure_pile(S):
    r = S.rnd
    S.add(S.shadowed(path(blob(r, 50, 56, 36, 30, 10, 0.1), fill=S.rad([(0, "#ffe27a"), (1, "#b8902c")]), stroke=OUT, sw=2), 3, 4, 3, 0.5))
    for _ in range(46):
        a, d = r.random() * 2 * math.pi, r.random() * 30
        x, y = 50 + math.cos(a) * d, 56 + math.sin(a) * d * 0.8
        S.add(ell(x, y, 4.4, 3.6, fill=S.rad([(0, "#fff0a0"), (1, "#d4a42c")]), stroke="#8a6a18", sw=0.8))
    for (x, y, c) in ((34, 44, "#d03a5a"), (62, 40, "#3a8ad0"), (70, 62, "#3ac070")):
        S.add(S.shadowed(poly(regular(x, y, 6.5, 6), fill=S.lin([(0, shade(c, 1.4)), (1, shade(c, 0.7))], 0, 0, 1, 1), stroke=OUT, sw=1.2), 1.5, 2, 1.2, 0.5))
    S.add(S.shadowed(path("M44 52h12l-2 12h-8z", fill=S.lin([(0, "#fff0a0"), (1, "#a8842c")]), stroke=OUT, sw=1.2), 1.5, 2, 1.2, 0.5))


@asset("dungeon/spiderweb", "Spider web", "Dungeon", 1, 1, seed=151)
def spiderweb(S):
    web = "#f3f3f6"
    spokes = "".join(path(f"M0 0L{n(100 * math.cos(math.radians(a)))} {n(100 * math.sin(math.radians(a)))}", stroke=web, sw=1.4, extra='opacity="0.85"') for a in range(0, 91, 15))
    rings = ""
    for k in range(1, 7):
        rr = k * 15
        pts = [(rr * math.cos(math.radians(a)), rr * math.sin(math.radians(a))) for a in range(0, 91, 15)]
        d = f"M{n(pts[0][0])} {n(pts[0][1])}" + "".join(f"Q{n((pts[i][0] + pts[i + 1][0]) / 2 * 0.9)} {n((pts[i][1] + pts[i + 1][1]) / 2 * 0.9)} {n(pts[i + 1][0])} {n(pts[i + 1][1])}" for i in range(len(pts) - 1))
        rings += path(d, stroke=web, sw=1.1, extra='opacity="0.8"')
    S.add(S.shadowed(spokes + rings, 1, 1.5, 1, 0.35))
    S.add(circ(36, 34, 5, fill="#1f1a15", stroke=OUT, sw=1) + circ(36, 27, 3, fill="#1f1a15", stroke=OUT, sw=1)
          + path("M30 30l-8 -4M30 36l-8 6M42 30l8 -4M42 36l8 6", stroke="#1f1a15", sw=1.3))


@asset("dungeon/brazier", "Brazier", "Dungeon", 1, 1, seed=152)
def brazier(S):
    S.add(glow(S, 50, 50, 48, "#ff9a2a", 0.5))
    S.add(S.shadowed(circ(50, 50, 28, fill=S.rad([(0, "#6b7078"), (1, "#25272b")], 0.38, 0.33, 0.85), stroke=OUT, sw=2.6), 3, 4, 3, 0.5))
    S.add(circ(50, 50, 21, fill=S.rad([(0, "#c2300c"), (1, "#3a1410")]), stroke="#15161a", sw=1.8))
    from camp import flames
    S.add(flames(S, 50, 50, 0.7))
    for i in range(3):
        a = 2 * math.pi * i / 3 + 0.5
        S.add(circ(50 + math.cos(a) * 27, 50 + math.sin(a) * 27, 3.6, fill="#25272b", stroke=OUT, sw=1))


@asset("dungeon/throne", "Throne", "Dungeon", 1, 1, seed=153)
def throne(S):
    S.add(S.shadowed(rect(18, 14, 64, 18, fill=S.lin([(0, "#e8c24a"), (1, "#8a6a18")]), stroke=OUT, sw=2.2, r=4), 3, 4, 3, 0.5))
    S.add(poly([(24, 14), (30, 4), (36, 14)], fill="#e8c24a", stroke=OUT, sw=1.4) + poly([(64, 14), (70, 4), (76, 14)], fill="#e8c24a", stroke=OUT, sw=1.4))
    S.add(S.shadowed(rect(18, 30, 64, 52, fill=S.lin([(0, "#a8283a"), (1, "#6a1422")], 0, 0, 1, 1), stroke=OUT, sw=2.4, r=6), 3, 4, 3, 0.5))
    S.add(rect(14, 34, 12, 46, fill=S.lin([(0, "#e8c24a"), (1, "#8a6a18")], 0, 0, 1, 0), stroke=OUT, sw=1.8, r=4))
    S.add(rect(74, 34, 12, 46, fill=S.lin([(0, "#e8c24a"), (1, "#8a6a18")], 0, 0, 1, 0), stroke=OUT, sw=1.8, r=4))
    S.add(circ(50, 54, 6, fill=S.rad([(0, "#ffe27a"), (1, "#a8842c")]), stroke=OUT, sw=1.4))


@asset("dungeon/fountain", "Fountain", "Dungeon", 2, 2, seed=154)
def fountain(S):
    S.add(S.shadowed(textured_circle(S, stone_pattern(S, "#b8b3a6", scale=0.6), 100, 100, 88, sw=2.8), 5, 7, 5, 0.5))
    S.add(circ(100, 100, 88, fill=S.rad([(0, "#ffffff", 0.2), (0.6, "#ffffff", 0), (1, "#000000", 0.3)], 0.4, 0.35, 0.9), stroke=None))
    S.add(circ(100, 100, 70, fill=S.rad([(0, "#9fd4f4"), (0.7, "#4f9ad8"), (1, "#2f6fa8")]), stroke="#6f6b63", sw=3))
    for rr in (60, 48, 36):
        S.add(circ(100, 100, rr, fill="none", stroke="#d6eefc", sw=1.4, extra='opacity="0.7"'))
    S.add(S.shadowed(circ(100, 100, 20, fill=textured_circle(S, stone_pattern(S, "#c4bfb2", scale=0.4), 100, 100, 20, sw=2.2), stroke=None), 3, 4, 2, 0.5)
          if False else S.shadowed(textured_circle(S, stone_pattern(S, "#c4bfb2", scale=0.4), 100, 100, 20, sw=2.2), 3, 4, 2, 0.5))
    S.add(circ(100, 100, 8, fill=S.rad([(0, "#e6f6ff"), (1, "#8fc4ee")]), stroke="#4a8ab8", sw=1.4))


@asset("dungeon/portal", "Magic portal", "Dungeon", 2, 2, seed=155)
def portal(S):
    r = S.rnd
    S.add(glow(S, 100, 100, 100, "#8a5cff", 0.55))
    ring = ""
    for i in range(16):
        a = 2 * math.pi * i / 16
        ring += rot(rect(100 + 70, 100 - 9, 20, 18, fill="#8a867b", stroke=OUT, sw=1.8, r=2), math.degrees(a), 100, 100)
    S.add(S.shadowed(ring, 3, 4, 3, 0.5))
    S.add(circ(100, 100, 74, fill=S.rad([(0, "#f0e6ff"), (0.35, "#b48cff"), (0.75, "#5a30c8"), (1, "#2a1470")]), stroke="#1a0c50", sw=2.6))
    for k in range(5):
        pts = [(100 + math.cos(a / 8 + k) * (8 + a * 0.55), 100 + math.sin(a / 8 + k) * (8 + a * 0.55)) for a in range(0, 120, 6)]
        S.add(path("M" + "L".join(f"{n(x)} {n(y)}" for x, y in pts), stroke="#e6d6ff", sw=1.6, extra='opacity="0.55"'))
    S.add(scatter(S, r, 40, 40, 120, 120, 30, ["#ffffff", "#d6c4ff"], 0.8, 1.8, 0.8))
    S.add(circ(100, 100, 74, fill=None, stroke="#c4a8ff", sw=2, extra='opacity="0.8"'))


@asset("dungeon/rune-circle", "Summoning circle", "Dungeon", 2, 2, seed=156)
def rune_circle(S):
    r = S.rnd
    S.add(glow(S, 100, 100, 96, "#7a5cff", 0.4))
    col = "#b89cff"
    S.add(circ(100, 100, 84, fill="none", stroke=col, sw=3.4))
    S.add(circ(100, 100, 72, fill="none", stroke=col, sw=1.8))
    star = regular(100, 100, 70, 5)
    S.add(poly([star[0], star[2], star[4], star[1], star[3]], fill="none", stroke=col, sw=2.6))
    for i in range(24):
        a = 2 * math.pi * i / 24
        x, y = 100 + math.cos(a) * 78, 100 + math.sin(a) * 78
        S.add(rot(path(f"M{n(x)} {n(y - 4)}v8M{n(x - 2.4)} {n(y - 1)}h4.8", stroke=col, sw=1.5), math.degrees(a) + 90, x, y))
    for (x, y) in star:
        S.add(circ(x, y, 6, fill="#2a1860", stroke=col, sw=2))
        S.add(circ(x, y, 2.4, fill="#e6d6ff", stroke=None))
    S.add(circ(100, 100, 9, fill=S.rad([(0, "#f0e6ff"), (1, "#7a5cff")]), stroke=col, sw=1.6))
    S.add(g("".join(circ(100 + (r.random() - 0.5) * 150, 100 + (r.random() - 0.5) * 150, 1.1, fill="#ffffff", stroke=None) for _ in range(24)), extra='opacity="0.8"'))


@asset("dungeon/stairs-down", "Stairs down", "Dungeon", 1, 1, seed=157)
def stairs_down(S):
    S.add(S.shadowed(rect(8, 8, 84, 84, fill="#6f6b63", stroke=OUT, sw=2.6, r=2), 3, 4, 3, 0.45))
    for i in range(7):
        y = 12 + i * 11.4
        f = 0.95 - i * 0.12
        S.add(rect(12, y, 76, 10.6, fill=shade("#c4c0b4", max(0.2, f)), stroke="#3a362f", sw=1.2))
    S.add(rect(8, 8, 84, 84, fill=S.lin([(0, "#000000", 0.0), (1, "#000000", 0.6)]), stroke=None, r=2))
    S.add(path("M42 22L50 34L58 22", stroke="#e6e2d6", sw=2.6, extra='opacity="0.85"'))


@asset("dungeon/iron-door", "Iron door", "Dungeon", 1, 1, seed=158)
def iron_door(S):
    S.add(S.shadowed(rect(8, 24, 84, 52, fill="#6f6b63", stroke=OUT, sw=2.4, r=3), 3, 4, 3, 0.45))
    S.add(rect(14, 30, 72, 40, fill=S.lin([(0, "#7a8088"), (1, "#3c4148")], 0, 0, 1, 1), stroke=OUT, sw=2.2, r=1))
    for x in (28, 50, 72):
        S.add(path(f"M{x} 30V70", stroke="#2b2f34", sw=2.4))
    for (x, y) in ((20, 36), (20, 64), (44, 36), (44, 64), (80, 36), (80, 64)):
        S.add(circ(x, y, 2.2, fill="#a8aeb6", stroke="#1a1c20", sw=0.8))
    S.add(circ(60, 50, 5, fill="none", stroke="#a8aeb6", sw=2.4))


@asset("dungeon/cage", "Cage", "Dungeon", 1, 1, seed=159)
def cage(S):
    S.add(S.shadowed(rect(14, 14, 72, 72, fill="#25272b", stroke=None, extra='opacity="0.35"'), 3, 4, 3, 0.5))
    bars = "".join(line(22 + i * 9.5, 16, 22 + i * 9.5, 84, stroke="#6b7078", sw=3.6) for i in range(7))
    bars += "".join(line(16, 22 + i * 14, 84, 22 + i * 14, stroke="#575c63", sw=2.8) for i in range(5))
    S.add(S.shadowed(rect(14, 14, 72, 72, fill="none", stroke="#3c4148", sw=5, r=2) + bars, 2, 3, 2, 0.55))
    S.add(circ(30, 66, 6, fill="#efe8d6", stroke=OUT, sw=1.2) + path("M38 70L60 60M40 74L64 70", stroke="#efe8d6", sw=3.4))


@asset("dungeon/torch", "Wall torch", "Dungeon", 1, 1, seed=160)
def torch(S):
    from camp import flames
    S.add(glow(S, 50, 50, 50, "#ff9a2a", 0.55))
    S.add(S.shadowed(rect(36, 40, 28, 28, fill=S.lin([(0, "#6b7078"), (1, "#25272b")]), stroke=OUT, sw=2, r=3), 2, 3, 2, 0.5))
    S.add(flames(S, 50, 48, 0.5))
    S.add(circ(50, 54, 5, fill="#3a1410", stroke=OUT, sw=1))


@asset("dungeon/grate", "Floor grate", "Dungeon", 1, 1, seed=161)
def grate(S):
    S.add(circ(50, 50, 38, fill="#050506", stroke=None))
    S.add(circ(50, 50, 38, fill="none", stroke="#4a4d54", sw=6))
    bars = "".join(line(24 + i * 13, 22, 24 + i * 13, 78, stroke="#6b7078", sw=3.4) for i in range(5))
    S.add(S.shadowed(bars + circ(50, 50, 38, fill="none", stroke=OUT, sw=2), 1.5, 2, 1.5, 0.5))


@asset("dungeon/blood", "Blood stain", "Dungeon", 1, 1, seed=162)
def blood(S):
    r = S.rnd
    S.add(path(blob(r, 50, 50, 28, 22, 11, 0.3), fill=S.rad([(0, "#7a1018"), (1, "#4a0a10")]), stroke=None, extra='opacity="0.85"'))
    for _ in range(16):
        a, d = r.random() * 2 * math.pi, 26 + r.random() * 20
        S.add(circ(50 + math.cos(a) * d, 50 + math.sin(a) * d, 1 + r.random() * 3, fill="#6a0c14", stroke=None, extra='opacity="0.85"'))
    S.add(ell(44, 44, 10, 5, fill="#b0303c", stroke=None, extra='opacity="0.35"'))


@asset("dungeon/ladder", "Ladder down", "Dungeon", 1, 1, seed=163)
def ladder(S):
    S.add(S.shadowed(circ(50, 50, 40, fill=S.rad([(0, "#000000"), (0.7, "#15151a"), (1, "#3a3a40")]), stroke=OUT, sw=2.6), 3, 4, 3, 0.45))
    S.add(S.shadowed(rect(36, 14, 6, 72, fill=wood_g(S), stroke=OUT, sw=1.6, r=2) + rect(58, 14, 6, 72, fill=wood_g(S), stroke=OUT, sw=1.6, r=2), 2, 3, 2, 0.5))
    for i in range(6):
        S.add(rect(36, 22 + i * 11, 28, 4.4, fill=wood_g(S), stroke=OUT, sw=1.2, r=1))


def wood_g(S):
    return S.lin([(0, "#c89a64"), (1, "#7a5230")], 0, 0, 1, 0)


@asset("dungeon/skull-pile", "Skull pile", "Dungeon", 1, 1, seed=164)
def skull_pile(S):
    for (x, y, rr) in ((36, 60, 14), (64, 62, 13), (50, 36, 14), (28, 34, 10), (74, 36, 10)):
        S.add(S.shadowed(circ(x, y, rr, fill=S.rad([(0, "#fffaf0"), (1, "#c9c1a8")], 0.4, 0.35, 0.85), stroke=OUT, sw=1.8), 2, 3, 2, 0.5))
        S.add(circ(x - rr * 0.33, y - rr * 0.1, rr * 0.24, fill="#1a1612", stroke=None) + circ(x + rr * 0.33, y - rr * 0.1, rr * 0.24, fill="#1a1612", stroke=None)
              + path(f"M{n(x - rr * 0.3)} {n(y + rr * 0.5)}h{n(rr * 0.6)}", stroke="#1a1612", sw=1.2))
