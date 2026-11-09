from svglib import *


def walls_base(S, x, y, w, h, wall="#dccdae", r=3, grain=0.22):
    """The house body: plaster walls that show as a border around the roof, with a shadow on the ground."""
    shape = rect(x, y, w, h, fill=S.lin([(0, shade(wall, 1.08)), (1, shade(wall, 0.86))], 0, 0, 1, 1), stroke=OUT, sw=2.6, r=r)
    S.add(S.shadowed(shape, 5, 6, 4.5, 0.45))
    S.add(S.grained(rect(x, y, w, h, fill="#000", stroke=None, r=r), grain, 0.9, 2))


@asset("buildings/cottage", "Cottage", "Buildings", 2, 2, seed=11)
def cottage(S):
    walls_base(S, 10, 10, 180, 180)
    S.add(gable_roof(S, 17, 17, 166, 166, "#b9563c", ridge="v"))
    S.add(skylight(S, 38, 62, 26, 34))
    S.add(skylight(S, 136, 108, 26, 34))
    S.add(chimney(S, 124, 32))


@asset("buildings/house", "House", "Buildings", 3, 2, seed=12)
def house(S):
    walls_base(S, 10, 10, 280, 180)
    S.add(gable_roof(S, 17, 17, 266, 166, "#a85a36", ridge="h"))
    S.add(skylight(S, 56, 36, 34, 26))
    S.add(skylight(S, 200, 36, 34, 26))
    S.add(skylight(S, 128, 128, 34, 26))
    S.add(chimney(S, 36, 98))
    S.add(chimney(S, 238, 98))


@asset("buildings/manor", "Manor", "Buildings", 4, 3, seed=13)
def manor(S):
    walls_base(S, 8, 8, 384, 284, wall="#d7cbb0")
    # two side wings (ridge running top-bottom) and a taller central block (ridge left-right)
    S.add(gable_roof(S, 16, 16, 128, 268, "#58687a", ridge="v"))
    S.add(gable_roof(S, 256, 16, 128, 268, "#58687a", ridge="v"))
    centre = gable_roof(S, 126, 60, 148, 180, "#6a7c90", ridge="h")
    S.add(S.shadowed(centre, 4, 5, 4, 0.5))
    S.add(skylight(S, 36, 50, 28, 34))
    S.add(skylight(S, 336, 50, 28, 34))
    S.add(skylight(S, 36, 200, 28, 34))
    S.add(skylight(S, 336, 200, 28, 34))
    S.add(chimney(S, 24, 124))
    S.add(chimney(S, 352, 124))
    S.add(chimney(S, 186, 26, 28))
    # grand entrance steps at the bottom of the central block
    S.add(rect(172, 252, 56, 30, fill="#bdb7ab", stroke=OUT, sw=2))
    S.add(path("M172 262H228M172 272H228", stroke="#8d887c", sw=1.5))


@asset("buildings/tavern", "Tavern", "Buildings", 3, 3, seed=14)
def tavern(S):
    walls_base(S, 10, 10, 280, 280, wall="#d3bf98")
    S.add(gable_roof(S, 18, 18, 264, 252, "#8a5a38", ridge="v"))
    S.add(S.shadowed(chimney(S, 196, 36, 30)))
    S.add(skylight(S, 52, 70, 30, 40))
    S.add(skylight(S, 218, 150, 30, 40))
    # covered porch with a hanging sign and lanterns
    porch = rect(70, 262, 160, 30, fill=S.lin([(0, "#7a5230"), (1, "#5e3b1a")]), stroke=OUT, sw=2.4, r=2)
    S.add(S.shadowed(porch, 3, 5, 3, 0.5))
    S.add(path("M80 267H220M80 277H220M80 286H220", stroke="#4a2e16", sw=1.3))
    S.add(rect(118, 232, 64, 24, fill=S.lin([(0, "#f0cf6a"), (1, "#c79a2c")]), stroke=OUT, sw=2, r=3))
    S.add(circ(130, 244, 5, fill="#3a2e28", stroke=None))
    S.add(path("M142 238h30M142 244h22M142 250h26", stroke="#5a3d12", sw=1.8))
    for lx in (78, 222):
        S.add(circ(lx, 276, 6, fill=S.rad([(0, "#fff2b0"), (1, "#f0a830")]), stroke=OUT, sw=1.6))


@asset("buildings/shop", "Shop", "Buildings", 2, 2, seed=15)
def shop(S):
    walls_base(S, 10, 10, 180, 180)
    S.add(gable_roof(S, 17, 17, 166, 112, "#4a6fa5", ridge="h"))
    S.add(chimney(S, 138, 28))
    # striped awning with scalloped edge over the shop front
    awning = ""
    for i in range(8):
        col = "#c0392b" if i % 2 == 0 else "#f2ead6"
        awning += path(f"M{22 + i * 19.5} 134H{22 + (i + 1) * 19.5}V162Q{22 + (i + 0.5) * 19.5} 176 {22 + i * 19.5} 162Z",
                       fill=col, stroke=OUT, sw=1.6)
    S.add(S.shadowed(awning, 2, 5, 3, 0.5))
    S.add(rect(78, 108, 44, 18, fill=S.lin([(0, "#f0cf6a"), (1, "#c79a2c")]), stroke=OUT, sw=1.8, r=2))
    S.add(path("M86 117h28", stroke="#5a3d12", sw=2))


@asset("buildings/barn", "Barn", "Buildings", 3, 2, seed=16)
def barn(S):
    walls_base(S, 10, 10, 280, 180, wall="#9c5a44", grain=0.3)
    S.add(gable_roof(S, 16, 16, 268, 168, "#b6402c", ridge="h", ridge_col="#8e2f20"))
    # hay-loft door and weathervane
    S.add(rect(124, 128, 52, 34, fill="#e7d9b0", stroke=OUT, sw=2.2, r=1))
    S.add(path("M124 128L176 162M176 128L124 162M150 128V162", stroke=OUT, sw=1.8))
    S.add(scatter(S, S.rnd, 70, 30, 40, 30, 22, ["#d9b84e", "#c9a23c"], 1.2, 2.4, 0.9))
    S.add(circ(240, 100, 4, fill="#2b211c", stroke=None))
    S.add(path("M222 100H258M240 90V110", stroke="#2b211c", sw=2))


@asset("buildings/tower", "Tower", "Buildings", 2, 2, seed=17)
def tower(S):
    cx = cy = 100
    S.add(S.shadowed(circ(cx, cy, 88, fill="#b9b4a8", stroke=OUT, sw=2.8), 5, 6, 4.5, 0.5))
    ring = circ(cx, cy, 88, fill=S.rad([(0, "#cbc6ba"), (1, "#8f8a7e")], 0.4, 0.35, 0.8), stroke=None)
    S.add(ring)
    # crenellations: merlons around the rim
    for i in range(20):
        a = 2 * math.pi * i / 20
        x, y = cx + math.cos(a) * 80, cy + math.sin(a) * 80
        S.add(rot(rect(x - 7, y - 6, 14, 12, fill="#a39e92", stroke=OUT, sw=1.6), math.degrees(a) + 90 - 90, x, y)
              .replace('rotate(', f'rotate(', 1))
    # conical roof: wedges
    roof_r = 62
    cone = ""
    wedges = 16
    for i in range(wedges):
        a0, a1 = 2 * math.pi * i / wedges, 2 * math.pi * (i + 1) / wedges
        pts = [(cx, cy), (cx + math.cos(a0) * roof_r, cy + math.sin(a0) * roof_r), (cx + math.cos(a1) * roof_r, cy + math.sin(a1) * roof_r)]
        lit = 0.5 + 0.5 * math.cos((a0 + a1) / 2 + math.radians(135))
        cone += poly(pts, fill=mix("#5a6676", "#aeb9c6", lit), stroke="#3d4652", sw=1.1)
    S.add(cone)
    S.add(circ(cx, cy, 62, fill=None, stroke=OUT, sw=2.2))
    S.add(circ(cx, cy, 7, fill="#d8d3c8", stroke=OUT, sw=1.6))
    S.add(S.grained(circ(cx, cy, 88, fill="#000", stroke=None), 0.22, 0.9, 5))


@asset("buildings/keep", "Keep", "Buildings", 4, 4, seed=18)
def keep(S):
    stone = stone_pattern(S, "#a9a396")
    S.add(S.shadowed(textured(S, stone, 36, 36, 328, 328, sw=2.8), 6, 7, 5, 0.5))
    S.add(rect(36, 36, 328, 328, fill=S.lin([(0, "#ffffff", 0.18), (1, "#000000", 0.25)], 0, 0, 1, 1), stroke=None))
    # merlons along each wall
    for i in range(14):
        p = 52 + i * 22
        for (x, y) in ((p, 26), (p, 364), (26, p), (364, p)):
            S.add(rect(x - 8, y - 6, 16, 12, fill="#bcb6a8", stroke=OUT, sw=1.5, r=1))
    # inner slate roof with ridges
    S.add(S.shadowed(gable_roof(S, 96, 96, 208, 208, "#6f7e8f", ridge="v"), 4, 5, 4, 0.5))
    S.add(rect(96, 190, 208, 20, fill=shade("#6f7e8f", 0.75), stroke=OUT, sw=1.6))
    # corner towers
    for (x, y) in ((46, 46), (354, 46), (46, 354), (354, 354)):
        S.add(S.shadowed(circ(x, y, 46, fill=S.rad([(0, "#cbc6ba"), (1, "#8f8a7e")], 0.4, 0.35, 0.8), stroke=OUT, sw=2.6), 4, 5, 4, 0.5))
        S.add(circ(x, y, 30, fill=S.rad([(0, "#9aa6b4"), (1, "#566170")]), stroke=OUT, sw=2))
        S.add(path(f"M{x} {y - 30}V{y + 30}M{x - 30} {y}H{x + 30}", stroke="#3d4652", sw=1.4, extra='opacity="0.7"'))
    S.add(circ(200, 200, 8, fill="#cfd0cb", stroke=OUT, sw=2))
    S.add(path("M200 200V170", stroke=OUT, sw=2))
    S.add(poly([(200, 170), (228, 178), (200, 186)], fill="#c0392b", stroke=OUT, sw=1.5))


@asset("buildings/windmill", "Windmill", "Buildings", 3, 3, seed=19)
def windmill(S):
    cx = cy = 150
    # sails (four lattice blades) rotated 45 degrees, drawn under the cap
    for k in range(4):
        blade = (rect(cx + 14, cy - 17, 122, 34, fill="#e5d8b8", stroke=OUT, sw=2.2, r=1)
                 + path("".join(f"M{cx + 14 + i * 15.2} {cy - 17}V{cy + 17}" for i in range(1, 8)), stroke="#8a6a3a", sw=1.4)
                 + path(f"M{cx + 14} {cy}H{cx + 136}", stroke="#8a6a3a", sw=1.4)
                 + rect(cx + 14, cy - 3.5, 122, 7, fill="#8a5a2b", stroke=OUT, sw=1.6))
        S.add(S.shadowed(rot(blade, 45 + 90 * k, cx, cy), 4, 5, 3.5, 0.4))
    S.add(S.shadowed(circ(cx, cy, 60, fill=S.rad([(0, "#cfc7b4"), (1, "#8f8776")], 0.4, 0.35, 0.8), stroke=OUT, sw=2.6), 5, 6, 4.5, 0.5))
    cap = ""
    for i in range(14):
        a0, a1 = 2 * math.pi * i / 14, 2 * math.pi * (i + 1) / 14
        lit = 0.5 + 0.5 * math.cos((a0 + a1) / 2 + math.radians(135))
        cap += poly([(cx, cy), (cx + math.cos(a0) * 50, cy + math.sin(a0) * 50), (cx + math.cos(a1) * 50, cy + math.sin(a1) * 50)],
                    fill=mix("#6f4b2b", "#c79a5a", lit), stroke="#4a2e16", sw=1)
    S.add(cap)
    S.add(circ(cx, cy, 50, fill=None, stroke=OUT, sw=2))
    S.add(circ(cx, cy, 11, fill=S.rad([(0, "#e8e0d0"), (1, "#7a6e5a")]), stroke=OUT, sw=2))


@asset("buildings/watchtower", "Watchtower", "Buildings", 2, 2, seed=20)
def watchtower(S):
    S.add(S.shadowed(textured(S, plank_pattern(S, "#a77a46"), 18, 18, 164, 164, sw=2.8), 6, 7, 5, 0.5))
    # posts and a pyramidal shingled roof
    col = ["#8a5a38", "#6e4528", "#b56f45", "#7a4d2f"]
    cx = cy = 100
    corners = [(18, 18), (182, 18), (182, 182), (18, 182)]
    roof = ""
    for i in range(4):
        a, b = corners[i], corners[(i + 1) % 4]
        roof += poly([a, b, (cx, cy)], fill=col[i], stroke=OUT, sw=2)
        mx, my = (a[0] + b[0]) / 2, (a[1] + b[1]) / 2
        for t in (0.3, 0.55, 0.78):
            p0 = (a[0] + (cx - a[0]) * t, a[1] + (cy - a[1]) * t)
            p1 = (b[0] + (cx - b[0]) * t, b[1] + (cy - b[1]) * t)
            roof += line(p0[0], p0[1], p1[0], p1[1], stroke=shade(col[i], 0.65), sw=1.2, extra='opacity="0.9"')
    S.add(S.shadowed(roof, 4, 5, 4, 0.45))
    S.add(circ(cx, cy, 6, fill="#d8d3c8", stroke=OUT, sw=1.8))
    for (x, y) in corners:
        S.add(circ(x, y, 8, fill="#6e4528", stroke=OUT, sw=1.8))


@asset("buildings/stable", "Stable", "Buildings", 3, 2, seed=21)
def stable(S):
    walls_base(S, 10, 10, 280, 180, wall="#b79a6c", grain=0.3)
    S.add(gable_roof(S, 16, 16, 268, 168, "#7d5a3a", ridge="h", ridge_col="#5e4129"))
    # hay bales beside the building and a trough
    for (x, y) in ((250, 156), (270, 138)):
        S.add(S.shadowed(rect(x, y, 26, 18, fill="#d9b84e", stroke=OUT, sw=1.8, r=2), 2, 3, 2, 0.5))
        S.add(path(f"M{x + 6} {y}V{y + 18}M{x + 13} {y}V{y + 18}M{x + 20} {y}V{y + 18}", stroke="#a98a2c", sw=1.2))
    S.add(skylight(S, 60, 40, 30, 22))
    S.add(skylight(S, 140, 128, 30, 22))
    S.add(chimney(S, 218, 28, 22))


@asset("buildings/blacksmith", "Blacksmith", "Buildings", 3, 2, seed=22)
def blacksmith(S):
    walls_base(S, 10, 10, 190, 180, wall="#b9b2a4")
    S.add(gable_roof(S, 16, 16, 178, 168, "#555d68", ridge="v", ridge_col="#3e444d"))
    chim = S.shadowed(rect(116, 76, 44, 44, fill="#7a7570", stroke=OUT, sw=2.2), 4, 5, 3, 0.5)
    S.add(chim)
    S.add(rect(124, 84, 28, 28, fill=S.rad([(0, "#ffb347"), (0.5, "#e8501a"), (1, "#2a1410")]), stroke=OUT, sw=1.8))
    # open lean-to forge on the right with anvil
    S.add(S.shadowed(textured(S, plank_pattern(S, "#6f5a42"), 204, 24, 86, 152, sw=2.4), 4, 5, 3.5, 0.45))
    S.add(rect(226, 52, 44, 36, fill="#4d4a46", stroke=OUT, sw=2, r=3))
    S.add(rect(232, 58, 32, 24, fill=S.rad([(0, "#ffd35a"), (0.55, "#e8601c"), (1, "#3a1c12")]), stroke=None, r=2))
    S.add(path("M222 128h52l-8 14h-36z", fill="#3a3f46", stroke=OUT, sw=2))
    S.add(rect(236, 112, 28, 16, fill="#454b53", stroke=OUT, sw=1.8, r=2))
    S.add(circ(216, 156, 9, fill="#8a929b", stroke=OUT, sw=1.8))
    S.add(circ(216, 156, 3, fill="#454b53", stroke=None))


@asset("buildings/chapel", "Chapel", "Buildings", 3, 4, seed=23)
def chapel(S):
    walls_base(S, 10, 10, 280, 380, wall="#d8d2c4")
    S.add(gable_roof(S, 18, 18, 264, 300, "#6c7a8c", ridge="v", ridge_col="#4d5a6a"))
    # bell tower at the front with a pyramid roof and cross
    S.add(S.shadowed(rect(96, 292, 108, 98, fill="#bdb7ab", stroke=OUT, sw=2.6), 4, 5, 4, 0.5))
    cx, cy = 150, 341
    cols = ["#7d8a9a", "#5d6978", "#8f9cab", "#6b7786"]
    corners = [(96, 292), (204, 292), (204, 390), (96, 390)]
    pyr = ""
    for i in range(4):
        pyr += poly([corners[i], corners[(i + 1) % 4], (cx, cy)], fill=cols[i], stroke=OUT, sw=1.8)
    S.add(pyr)
    S.add(rect(146, 331, 8, 20, fill="#e6d28a", stroke=OUT, sw=1.4))
    S.add(rect(140, 337, 20, 8, fill="#e6d28a", stroke=OUT, sw=1.4))
    # stained-glass rose window glints on the roof slopes
    S.add(skylight(S, 52, 100, 28, 40))
    S.add(skylight(S, 220, 180, 28, 40))
    S.add(circ(150, 50, 12, fill=S.rad([(0, "#ffd9a8"), (0.5, "#d26a5a"), (1, "#5a6ab0")]), stroke=OUT, sw=2))


@asset("buildings/thatched-cottage", "Thatched cottage", "Buildings", 2, 2, seed=24)
def thatched_cottage(S):
    th = thatch_pattern(S, "#cfa94f")
    S.add(S.shadowed(textured(S, th, 12, 12, 176, 176, r=22, sw=2.8), 5, 6, 4.5, 0.45))
    S.add(rect(12, 12, 176, 176, fill=S.lin([(0, "#ffffff", 0.22), (0.55, "#ffffff", 0), (1, "#000000", 0.34)], 0, 0, 1, 1), stroke=None, r=22))
    S.add(rect(34, 92, 132, 16, fill=shade("#cfa94f", 0.78), stroke=OUT, sw=1.6, r=8))
    S.add(path("M44 100H156", stroke=shade("#cfa94f", 1.25), sw=1.6, extra='opacity="0.8"'))
    S.add(skylight(S, 52, 44, 24, 24))
    S.add(chimney(S, 132, 128, 22))


@asset("buildings/round-hut", "Round hut", "Buildings", 2, 2, seed=25)
def round_hut(S):
    cx = cy = 100
    S.add(S.shadowed(textured_circle(S, thatch_pattern(S, "#c8a24c"), cx, cy, 86, sw=2.8), 5, 6, 4.5, 0.45))
    rays = ""
    for i in range(18):
        a = 2 * math.pi * i / 18
        rays += line(cx + math.cos(a) * 14, cy + math.sin(a) * 14, cx + math.cos(a) * 84, cy + math.sin(a) * 84,
                     stroke=shade("#c8a24c", 0.7), sw=1.3, extra='opacity="0.55"')
    S.add(rays)
    S.add(circ(cx, cy, 86, fill=S.rad([(0, "#ffffff", 0.18), (0.6, "#ffffff", 0), (1, "#000000", 0.34)], 0.4, 0.35, 0.9), stroke=None))
    S.add(circ(cx, cy, 13, fill=S.rad([(0, "#241a14"), (1, "#5a4a3a")]), stroke=OUT, sw=2))
    S.add(circ(cx, cy, 86, fill=None, stroke=OUT, sw=2.8))


@asset("buildings/longhouse", "Longhouse", "Buildings", 4, 2, seed=26)
def longhouse(S):
    S.add(S.shadowed(textured(S, thatch_pattern(S, "#b9984a"), 10, 20, 380, 160, r=40, sw=2.8), 5, 6, 4.5, 0.45))
    S.add(rect(10, 20, 380, 160, fill=S.lin([(0, "#ffffff", 0.2), (0.5, "#ffffff", 0), (1, "#000000", 0.34)], 0, 0, 0, 1), stroke=None, r=40))
    S.add(rect(60, 94, 280, 12, fill=shade("#b9984a", 0.75), stroke=OUT, sw=1.6, r=6))
    for x in (110, 200, 290):
        S.add(ell(x, 100, 13, 9, fill=S.rad([(0, "#2a1a12"), (1, "#5a4a3a")]), stroke=OUT, sw=1.8))
        S.add(ell(x - 2, 97, 5, 3, fill="#9a7a5a", stroke=None, extra='opacity="0.5"'))


@asset("buildings/ruined-house", "Ruined house", "Buildings", 2, 2, seed=27)
def ruined_house(S):
    r = S.rnd
    # floor: broken boards over dirt
    S.add(rect(16, 16, 168, 168, fill="#8b6b45", stroke=None))
    S.add(S.grained(rect(16, 16, 168, 168, fill="#000", stroke=None), 0.35, 0.7, 6))
    S.add(S.tiles(plank_pattern(S, "#9a7448"), 24, 24, 110, 92))
    S.add(rect(24, 24, 110, 92, fill=S.lin([(0, "#000000", 0.0), (1, "#000000", 0.3)], 0, 0, 1, 1), stroke=None))
    stone = stone_pattern(S, "#a39e92")
    segs = [(12, 12, 100, 18), (140, 12, 48, 18), (12, 12, 18, 80), (12, 120, 18, 68), (170, 12, 18, 54),
            (170, 100, 18, 88), (12, 170, 70, 18), (112, 170, 76, 18)]
    walls = "".join(textured(S, stone, x, y, w, h, sw=2.2) for x, y, w, h in segs)
    S.add(S.shadowed(walls, 4, 5, 3.5, 0.5))
    S.add(rect(12, 12, 176, 176, fill="none", stroke=None))
    # rubble and weeds
    for _ in range(26):
        x, y = 20 + r.random() * 160, 20 + r.random() * 160
        pts = rockpoly(r, x, y, 4 + r.random() * 5, 3 + r.random() * 4, 6)
        S.add(poly(pts, fill=jitter("#9a958a", r, 0.15), stroke="#4a443c", sw=1.1))
    S.add(tufts(r, 14, 14, 172, 172, 30, ["#5c8a3c", "#7aa84c", "#4a7a30"], 9))


@asset("buildings/well", "Well", "Buildings", 1, 1, seed=28)
def well(S):
    S.add(S.shadowed(textured_circle(S, stone_pattern(S, "#aaa497", scale=0.5), 50, 50, 40, sw=2.6), 4, 5, 3.5, 0.5))
    S.add(circ(50, 50, 25, fill=S.rad([(0, "#2f6aa8"), (0.7, "#1b3d63"), (1, "#0e1f33")]), stroke=OUT, sw=2.2))
    S.add(ell(44, 44, 9, 4, fill="#8fc4ee", stroke=None, extra='opacity="0.6"'))
    S.add(rect(8, 46, 84, 8, fill="#8a5a2b", stroke=OUT, sw=1.8, r=2))
    S.add(circ(50, 50, 5, fill="#d8d3c8", stroke=OUT, sw=1.4))


@asset("buildings/market-stall", "Market stall", "Buildings", 2, 1, seed=29)
def market_stall(S):
    r = S.rnd
    S.add(S.shadowed(rect(10, 12, 180, 76, fill="#a77a46", stroke=OUT, sw=2.4, r=3), 4, 5, 3.5, 0.45))
    for i in range(9):
        col = "#2f7d6a" if i % 2 == 0 else "#f2ead6"
        S.add(path(f"M{12 + i * 19.5} 12h19.5v44q-9.75 14 -19.5 0z", fill=col, stroke=OUT, sw=1.5))
    S.add(rect(10, 12, 180, 76, fill=S.lin([(0, "#ffffff", 0.22), (1, "#000000", 0.2)], 0, 0, 0, 1), stroke=None, r=3))
    # goods on the counter
    for i in range(9):
        S.add(circ(30 + i * 16, 74, 6.5, fill=r.choice(["#d03a2c", "#e8a02c", "#78a83c"]), stroke=OUT, sw=1.2))
