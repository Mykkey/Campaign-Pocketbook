from svglib import *


def faceted_rock(S, rnd, cx, cy, rx, ry, base="#9a968c", points=8, moss=False):
    """A low-poly style rock: triangular facets lit from the top-left, with a ground shadow."""
    pts = rockpoly(rnd, cx, cy, rx, ry, points, 0.2)
    ctr = (cx - rx * 0.15, cy - ry * 0.2)
    facets = ""
    m = len(pts)
    for i in range(m):
        a, b = pts[i], pts[(i + 1) % m]
        mx, my = (a[0] + b[0]) / 2 - ctr[0], (a[1] + b[1]) / 2 - ctr[1]
        ang = math.atan2(my, mx)
        light = 0.5 + 0.5 * math.cos(ang + math.radians(135))
        col = mix(shade(base, 0.62), shade(base, 1.22), light)
        facets += poly([ctr, a, b], fill=col, stroke=shade(base, 0.5), sw=1.1)
    out = S.shadowed(facets + poly(pts, fill=None, stroke=OUT, sw=2.2), 3, 4, 3, 0.5)
    if moss:
        for _ in range(5):
            a = rnd.random() * 2 * math.pi
            out += circ(cx + math.cos(a) * rx * 0.55, cy + math.sin(a) * ry * 0.55 - 2, 2.5 + rnd.random() * 3,
                        fill="#6a9a44", stroke=None, extra='opacity="0.75"')
    return out


def flower(cx, cy, r, petal, centre="#f2d03b", petals=5, rot_=0):
    out = ""
    for i in range(petals):
        a = math.radians(rot_ + 360 * i / petals)
        out += ell(cx + math.cos(a) * r * 0.62, cy + math.sin(a) * r * 0.62, r * 0.55, r * 0.4, fill=petal, stroke=shade(petal, 0.6), sw=0.8,
                   extra=f'transform="rotate({n(math.degrees(a))} {n(cx + math.cos(a) * r * 0.62)} {n(cy + math.sin(a) * r * 0.62)})"')
    out += circ(cx, cy, r * 0.28, fill=centre, stroke=shade(centre, 0.55), sw=0.8)
    return out


@asset("nature/tree-oak", "Oak tree", "Nature", 2, 2, seed=111)
def tree_oak(S):
    S.add(S.shadowed(tree_canopy(S, 100, 100, 88, "#2f5a26", "#4d8a38", "#86c25a", 11, seed=5), 5, 7, 6, 0.3))


@asset("nature/tree-pine", "Pine tree", "Nature", 2, 2, seed=112)
def tree_pine(S):
    cx = cy = 100
    S.add(ell(cx + 12, cy + 14, 86, 82, fill="#000", stroke=None, extra='opacity="0.28"'))
    tiers = [(92, "#244d2e", "#2f6a3c"), (72, "#2b5a35", "#3a7a46"), (52, "#33683d", "#46904f"), (32, "#3d7a46", "#5cb065")]
    for k, (rr, dark, light) in enumerate(tiers):
        arms = 9
        pts = []
        for i in range(arms * 2):
            a = math.radians(-90 + 180 * i / arms + k * 11)
            rad = rr if i % 2 == 0 else rr * 0.5
            pts.append((cx + math.cos(a) * rad, cy + math.sin(a) * rad))
        S.add(poly(pts, fill=S.rad([(0, light), (1, dark)], 0.4, 0.35, 0.8), stroke=shade(dark, 0.45), sw=1.8))
        for i in range(0, arms * 2, 2):
            S.add(line(cx, cy, pts[i][0], pts[i][1], stroke=shade(dark, 0.55), sw=1.2, extra='opacity="0.7"'))
    S.add(circ(cx, cy, 8, fill=S.rad([(0, "#9a7a52"), (1, "#5a4026")]), stroke=OUT, sw=1.6))


@asset("nature/tree-birch", "Birch tree", "Nature", 2, 2, seed=113)
def tree_birch(S):
    S.add(S.shadowed(tree_canopy(S, 100, 100, 82, "#5f8f3e", "#92c15c", "#d2eb8e", 12, seed=9), 5, 7, 6, 0.3))
    S.add(circ(100, 100, 9, fill="#efeae0", stroke=OUT, sw=1.6))
    S.add(path("M95 97h10M96 103h7", stroke="#2b211c", sw=1.6))


@asset("nature/tree-autumn", "Autumn tree", "Nature", 2, 2, seed=114)
def tree_autumn(S):
    S.add(S.shadowed(tree_canopy(S, 100, 100, 86, "#9a3a1c", "#d27a2c", "#f0c04a", 12, seed=3), 5, 7, 6, 0.3))
    r = S.rnd
    for _ in range(14):
        S.add(circ(20 + r.random() * 160, 20 + r.random() * 160, 2 + r.random() * 2, fill="#c0502c", stroke=None, extra='opacity="0.6"'))


@asset("nature/tree-dead", "Dead tree", "Nature", 2, 2, seed=115)
def tree_dead(S):
    r = S.rnd
    cx = cy = 100
    branches = ""
    shade_branches = ""
    for i in range(7):
        a = 2 * math.pi * i / 7 + r.random() * 0.4
        L = 62 + r.random() * 28
        ex, ey = cx + math.cos(a) * L, cy + math.sin(a) * L
        mx, my = cx + math.cos(a) * L * 0.5 + (r.random() - 0.5) * 12, cy + math.sin(a) * L * 0.5 + (r.random() - 0.5) * 12
        branches += path(f"M{cx} {cy}Q{n(mx)} {n(my)} {n(ex)} {n(ey)}", stroke="#4a3a2c", sw=7, extra='stroke-linecap="round"')
        for k in range(2):
            t = 0.5 + k * 0.25
            bx, by = cx + (ex - cx) * t, cy + (ey - cy) * t
            a2 = a + (0.7 if k == 0 else -0.7)
            branches += path(f"M{n(bx)} {n(by)}l{n(math.cos(a2) * 24)} {n(math.sin(a2) * 24)}", stroke="#4a3a2c", sw=3.4, extra='stroke-linecap="round"')
        shade_branches += path(f"M{cx - 1} {cy - 1}Q{n(mx - 1)} {n(my - 1)} {n(ex - 1)} {n(ey - 1)}", stroke="#8a7458", sw=1.6, extra='opacity="0.8"')
    S.add(S.shadowed(branches, 4, 5, 3, 0.5))
    S.add(shade_branches)
    S.add(circ(cx, cy, 10, fill=S.rad([(0, "#6b5a46"), (1, "#3a2e22")]), stroke=OUT, sw=2))
    S.add(circ(cx, cy, 4, fill=None, stroke="#2b211c", sw=1))


@asset("nature/tree-willow", "Willow tree", "Nature", 3, 3, seed=116)
def tree_willow(S):
    r = S.rnd
    cx = cy = 150
    S.add(ell(cx + 14, cy + 16, 134, 126, fill="#000", stroke=None, extra='opacity="0.26"'))
    S.add(circ(cx, cy, 128, fill=S.rad([(0, "#6aa046"), (1, "#3f7430")], 0.4, 0.35, 0.8), stroke="#2c5524", sw=2.6))
    for i in range(72):
        a = 2 * math.pi * i / 72 + r.random() * 0.06
        r0, r1 = 24 + r.random() * 30, 108 + r.random() * 20
        c = jitter("#7cb356" if i % 2 else "#5a9a3e", r, 0.12)
        S.add(path(f"M{n(cx + math.cos(a) * r0)} {n(cy + math.sin(a) * r0)}L{n(cx + math.cos(a + 0.03) * r1)} {n(cy + math.sin(a + 0.03) * r1)}",
                   stroke=c, sw=6 + r.random() * 3, extra='stroke-linecap="round" opacity="0.9"'))
    for _ in range(40):
        a, d = r.random() * 2 * math.pi, 30 + r.random() * 90
        S.add(circ(cx + math.cos(a) * d, cy + math.sin(a) * d, 2 + r.random() * 3, fill="#b6e07a", stroke=None, extra='opacity="0.5"'))
    S.add(circ(cx, cy, 11, fill=S.rad([(0, "#8a6a46"), (1, "#4a3622")]), stroke=OUT, sw=2))


@asset("nature/tree-palm", "Palm tree", "Nature", 2, 2, seed=117)
def tree_palm(S):
    r = S.rnd
    cx = cy = 100
    fronds = ""
    for i in range(9):
        a = 2 * math.pi * i / 9 + 0.2
        L = 84
        ex, ey = cx + math.cos(a) * L, cy + math.sin(a) * L
        px, py = -math.sin(a), math.cos(a)
        leaf = (f"M{cx} {cy}Q{n(cx + math.cos(a) * L * 0.5 + px * 22)} {n(cy + math.sin(a) * L * 0.5 + py * 22)} {n(ex)} {n(ey)}"
                f"Q{n(cx + math.cos(a) * L * 0.5 - px * 22)} {n(cy + math.sin(a) * L * 0.5 - py * 22)} {cx} {cy}Z")
        lit_ = 0.5 + 0.5 * math.cos(a + math.radians(135))
        fronds += path(leaf, fill=mix("#2f6a34", "#78b858", lit_), stroke="#1f4a24", sw=1.8)
        fronds += line(cx, cy, ex, ey, stroke="#1f4a24", sw=1.4, extra='opacity="0.7"')
        for k in range(1, 6):
            t = k / 6
            bx, by = cx + (ex - cx) * t, cy + (ey - cy) * t
            fronds += line(bx, by, bx + px * 11 * (1 - t * 0.5), by + py * 11 * (1 - t * 0.5), stroke="#1f4a24", sw=1, extra='opacity="0.5"')
            fronds += line(bx, by, bx - px * 11 * (1 - t * 0.5), by - py * 11 * (1 - t * 0.5), stroke="#1f4a24", sw=1, extra='opacity="0.5"')
    S.add(S.shadowed(fronds, 5, 7, 5, 0.4))
    for i in range(4):
        a = 2 * math.pi * i / 4 + 0.6
        S.add(circ(cx + math.cos(a) * 9, cy + math.sin(a) * 9, 5.5, fill=S.rad([(0, "#a8742c"), (1, "#5e3f1a")]), stroke=OUT, sw=1.4))


@asset("nature/tree-snow-pine", "Snowy pine", "Nature", 2, 2, seed=118)
def tree_snow_pine(S):
    cx = cy = 100
    S.add(ell(cx + 12, cy + 14, 86, 82, fill="#000", stroke=None, extra='opacity="0.25"'))
    tiers = [(92, "#355a4a", "#5a8a74"), (72, "#46705c", "#7aa890"), (52, "#5a8a74", "#a8cfc0"), (32, "#76a896", "#dcefe8")]
    for k, (rr, dark, light) in enumerate(tiers):
        arms = 9
        pts = []
        for i in range(arms * 2):
            a = math.radians(-90 + 180 * i / arms + k * 11)
            rad = rr if i % 2 == 0 else rr * 0.5
            pts.append((cx + math.cos(a) * rad, cy + math.sin(a) * rad))
        S.add(poly(pts, fill=S.rad([(0, light), (1, dark)], 0.4, 0.35, 0.8), stroke=shade(dark, 0.5), sw=1.8))
    r = S.rnd
    for _ in range(40):
        a, d = r.random() * 2 * math.pi, r.random() * 78
        S.add(ell(cx + math.cos(a) * d, cy + math.sin(a) * d, 4 + r.random() * 5, 2.4 + r.random() * 3, fill="#f6fbff", stroke=None, extra='opacity="0.85"'))
    S.add(circ(cx, cy, 8, fill="#fbfdff", stroke="#9ab4c8", sw=1.4))


@asset("nature/bush", "Bush", "Nature", 1, 1, seed=119)
def bush(S):
    S.add(S.shadowed(tree_canopy(S, 50, 52, 38, "#2f5a26", "#4d8a38", "#86c25a", 7, seed=8), 3, 4, 3, 0.35))


@asset("nature/bush-berry", "Berry bush", "Nature", 1, 1, seed=120)
def bush_berry(S):
    S.add(S.shadowed(tree_canopy(S, 50, 52, 38, "#2f5a26", "#4a8236", "#7cb552", 7, seed=12), 3, 4, 3, 0.35))
    r = S.rnd
    for _ in range(14):
        a, d = r.random() * 2 * math.pi, r.random() * 30
        S.add(circ(50 + math.cos(a) * d, 52 + math.sin(a) * d, 2.8, fill=S.rad([(0, "#ff6b6b"), (1, "#a01c24")]), stroke="#5a0f14", sw=0.8))


@asset("nature/rock", "Rock", "Nature", 1, 1, seed=121)
def rock(S):
    S.add(faceted_rock(S, S.rnd, 50, 52, 34, 28, "#9a968c", 8, moss=True))


@asset("nature/boulders", "Boulders", "Nature", 2, 2, seed=122)
def boulders(S):
    r = S.rnd
    S.add(faceted_rock(S, r, 56, 130, 44, 38, "#8e8a80", 9, moss=True))
    S.add(faceted_rock(S, r, 140, 52, 30, 26, "#a39f95", 8))
    S.add(faceted_rock(S, r, 142, 138, 34, 30, "#7f7b72", 8, moss=True))
    S.add(faceted_rock(S, r, 56, 52, 22, 18, "#a8a499", 7))


@asset("nature/rock-pile", "Rock pile", "Nature", 1, 1, seed=123)
def rock_pile(S):
    r = S.rnd
    for (x, y, rx, ry, c) in ((34, 62, 22, 16, "#8e8a80"), (66, 60, 20, 16, "#a39f95"), (50, 36, 18, 14, "#9a968c"), (26, 34, 11, 9, "#b0ac9f"), (74, 32, 10, 8, "#8a867c")):
        S.add(faceted_rock(S, r, x, y, rx, ry, c, 7))


@asset("nature/mossy-boulder", "Mossy boulder", "Nature", 2, 2, seed=124)
def mossy_boulder(S):
    r = S.rnd
    S.add(faceted_rock(S, r, 100, 100, 76, 66, "#8a8a80", 11, moss=False))
    for _ in range(22):
        a, d = r.random() * 2 * math.pi, r.random() * 52
        S.add(circ(100 + math.cos(a) * d, 100 + math.sin(a) * d - 6, 4 + r.random() * 8, fill=jitter("#5f9440", r, 0.18), stroke=None, extra='opacity="0.7"'))
    S.add(path("M78 70L96 100L88 132M122 80L108 104", stroke="#4a463f", sw=1.6, extra='opacity="0.8"'))


@asset("nature/pond", "Pond", "Nature", 3, 2, seed=125)
def pond(S):
    r = S.rnd
    S.add(path(blob(r, 150, 100, 138, 88, 14, 0.07), fill=S.lin([(0, "#8a7a50"), (1, "#5e5030")]), stroke="#3a3220", sw=2.4))
    S.add(path(blob(r, 150, 100, 122, 74, 14, 0.07), fill=S.rad([(0, "#7fc0ec"), (0.6, "#3f86c9"), (1, "#27608f")], 0.4, 0.38, 0.75), stroke="#1f4a70", sw=2))
    S.add(ell(118, 78, 62, 22, fill="#c4e4fa", stroke=None, extra='opacity="0.45"'))
    for (x, y, rr) in ((214, 120, 15), (96, 132, 12), (180, 62, 10)):
        S.add(S.shadowed(path(f"M{x} {y}L{n(x + rr)} {n(y - rr * 0.2)}A{rr} {rr} 0 1 1 {n(x + rr * 0.1)} {n(y - rr)}Z", fill=S.rad([(0, "#5fae4c"), (1, "#2f7a2c")]), stroke="#1f5a22", sw=1.4), 1.5, 2, 1.5, 0.4))
    S.add(flower(212, 114, 5.5, "#ff9ec4", "#ffe27a"))
    for _ in range(14):
        a = r.random() * 2 * math.pi
        S.add(path(f"M{n(150 + math.cos(a) * 116)} {n(100 + math.sin(a) * 68)}v-14", stroke="#4f7a2c", sw=2, extra='stroke-linecap="round"'))
        S.add(circ(150 + math.cos(a) * 116, 100 + math.sin(a) * 68 - 15, 2.4, fill="#6b4423", stroke=None))


@asset("nature/swamp-pool", "Swamp pool", "Nature", 2, 2, seed=126)
def swamp_pool(S):
    r = S.rnd
    S.add(path(blob(r, 100, 100, 92, 84, 13, 0.1), fill="#4a4a2c", stroke="#2c2c1a", sw=2.4))
    S.add(path(blob(r, 100, 100, 76, 68, 13, 0.1), fill=S.rad([(0, "#6a7a3c"), (1, "#3a4a24")], 0.4, 0.38, 0.8), stroke="#26301a", sw=1.8))
    for _ in range(18):
        S.add(circ(40 + r.random() * 120, 40 + r.random() * 120, 3 + r.random() * 8, fill=jitter("#5f8a34", r, 0.2), stroke=None, extra='opacity="0.7"'))
    for _ in range(14):
        x, y = 40 + r.random() * 120, 40 + r.random() * 120
        S.add(circ(x, y, 1.6 + r.random() * 2.4, fill="none", stroke="#c0d890", sw=1, extra='opacity="0.8"'))
    for (x, y) in ((34, 120), (160, 70), (60, 158)):
        S.add(path(f"M{x} {y}v-22M{x + 6} {y}v-18M{x - 6} {y}v-16", stroke="#6b8a34", sw=2.4, extra='stroke-linecap="round"'))
        S.add(ell(x, y - 24, 3, 6, fill="#6b4423", stroke=None))


@asset("nature/flowers", "Flowers", "Nature", 1, 1, seed=127)
def flowers(S):
    r = S.rnd
    S.add(tufts(r, 8, 12, 84, 84, 36, ["#4f8a34", "#6aa84a", "#3f7a2c"], 14))
    for (x, y, rr, c) in ((26, 30, 9, "#e86a92"), (64, 24, 8, "#f2d03b"), (76, 58, 9, "#a77be0"), (36, 72, 8, "#ff9f4a"), (56, 46, 7, "#ffffff"), (22, 54, 6, "#7ab8f0")):
        S.add(flower(x, y, rr, c, "#f2d03b" if c != "#f2d03b" else "#d9742c", rot_=r.random() * 72))


@asset("nature/flowerbed", "Flowerbed", "Nature", 2, 1, seed=128)
def flowerbed(S):
    r = S.rnd
    S.add(S.shadowed(rect(8, 18, 184, 64, fill=S.lin([(0, "#6b4a2b"), (1, "#4a3220")]), stroke=OUT, sw=2.4, r=14), 3, 4, 3, 0.45))
    S.add(scatter(S, r, 14, 24, 172, 52, 40, ["#3a2818", "#7a5a3a"], 1, 2, 0.6))
    for i in range(14):
        x = 20 + i * 12.5
        for y in (36, 62):
            c = r.choice(["#e86a92", "#f2d03b", "#a77be0", "#ff9f4a", "#ffffff", "#7ab8f0", "#d03a3a"])
            S.add(circ(x + r.random() * 4, y + (r.random() - 0.5) * 8, 7, fill="#4f8a34", stroke=None, extra='opacity="0.9"'))
            S.add(flower(x + r.random() * 4, y + (r.random() - 0.5) * 8, 6.5, c, "#f2d03b", rot_=r.random() * 72))


@asset("nature/stump", "Tree stump", "Nature", 1, 1, seed=129)
def stump(S):
    S.add(S.shadowed(path(blob(S.rnd, 50, 50, 38, 36, 12, 0.06), fill=S.rad([(0, "#a8723a"), (1, "#5e3f22")], 0.4, 0.35, 0.85), stroke=OUT, sw=2.6), 3, 4, 3, 0.5))
    S.add(circ(50, 50, 28, fill=S.rad([(0, "#e8c898"), (1, "#c9995e")], 0.45, 0.4, 0.8), stroke="#7a5230", sw=1.6))
    for rr in (22, 16, 10, 5):
        S.add(circ(50, 50, rr, fill=None, stroke="#9a6e3e", sw=1.2, extra='opacity="0.9"'))
    S.add(path("M50 50L76 44", stroke="#8a5f35", sw=1, extra='opacity="0.6"'))
    S.add(circ(78, 70, 5, fill="#f0e6d0", stroke="#8a6a46", sw=1))
    S.add(circ(78, 70, 5, fill="#c0392b", stroke=None, extra='opacity="0.0"'))
    S.add(circ(22, 70, 4, fill="#6aa04a", stroke=None, extra='opacity="0.8"'))


@asset("nature/fallen-log", "Fallen log", "Nature", 2, 1, seed=130)
def fallen_log(S):
    r = S.rnd
    S.add(S.shadowed(path("M16 38Q100 30 186 40V64Q100 72 16 62Z", fill=S.lin([(0, "#8a5f35"), (0.5, "#6b4a2b"), (1, "#4a3220")]), stroke=OUT, sw=2.6), 4, 6, 4, 0.5))
    for i in range(14):
        x = 24 + i * 11.5 + r.random() * 4
        S.add(path(f"M{n(x)} {n(40 + r.random() * 3)}q{n((r.random() - 0.5) * 6)} 10 {n((r.random() - 0.5) * 4)} {n(20 + r.random() * 3)}", stroke="#3a2614", sw=1.3, extra='opacity="0.7"'))
    S.add(S.shadowed(ell(16, 50, 11, 13, fill=S.rad([(0, "#e8c898"), (1, "#b98a58")]), stroke=OUT, sw=2), 2, 3, 2, 0.5))
    S.add(ell(16, 50, 6, 8, fill=None, stroke="#8a5f35", sw=1))
    S.add(path("M70 40L62 24M130 40L142 22", stroke="#5e3f22", sw=6, extra='stroke-linecap="round"'))
    for _ in range(7):
        S.add(circ(60 + r.random() * 110, 40 + r.random() * 20, 3 + r.random() * 4, fill="#6aa04a", stroke=None, extra='opacity="0.7"'))


@asset("nature/reeds", "Reeds", "Nature", 1, 1, seed=131)
def reeds(S):
    r = S.rnd
    for _ in range(26):
        a = r.random() * 2 * math.pi
        L = 20 + r.random() * 26
        x, y = 50 + (r.random() - 0.5) * 24, 52 + (r.random() - 0.5) * 24
        S.add(path(f"M{n(x)} {n(y)}q{n(math.cos(a) * L * 0.5)} {n(math.sin(a) * L * 0.5 - 4)} {n(math.cos(a) * L)} {n(math.sin(a) * L)}",
                   stroke=jitter("#5f9a3a", r, 0.2), sw=2.2 + r.random(), extra='stroke-linecap="round"'))
    for _ in range(5):
        S.add(ell(44 + r.random() * 14, 44 + r.random() * 14, 2.8, 6.5, fill="#6b4423", stroke=OUT, sw=1))


@asset("nature/lilypads", "Lily pads", "Nature", 1, 1, seed=132)
def lilypads(S):
    S.add(circ(50, 50, 40, fill=S.rad([(0, "#7fc0ec", 0.55), (1, "#3f86c9", 0)]), stroke=None))
    for (x, y, rr, rot_) in ((34, 36, 15, 20), (66, 58, 17, 200), (38, 70, 11, 110)):
        pad = f"M{x} {y}L{n(x + rr)} {n(y - rr * 0.18)}A{rr} {rr} 0 1 1 {n(x + rr * 0.18)} {n(y - rr)}Z"
        S.add(S.shadowed(rot(path(pad, fill=S.rad([(0, "#6fbd54"), (1, "#2f7a2c")], 0.4, 0.4, 0.8), stroke="#1f5a22", sw=1.5), rot_, x, y), 1.5, 2, 1.5, 0.4))
    S.add(flower(60, 52, 7, "#ff9ec4", "#ffe27a", petals=7))


@asset("nature/mushrooms", "Mushrooms", "Nature", 1, 1, seed=133)
def mushrooms(S):
    r = S.rnd
    for (x, y, rr, c) in ((50, 50, 13, "#c0392b"), (28, 36, 9, "#d9742c"), (72, 38, 9, "#c0392b"), (34, 72, 8, "#e0b86a"), (68, 70, 10, "#c0392b")):
        S.add(S.shadowed(circ(x, y, rr, fill=S.rad([(0, shade(c, 1.25)), (1, shade(c, 0.7))], 0.38, 0.33, 0.85), stroke=OUT, sw=1.8), 2, 3, 2, 0.5))
        for _ in range(4):
            a, d = r.random() * 2 * math.pi, r.random() * rr * 0.6
            S.add(circ(x + math.cos(a) * d, y + math.sin(a) * d, rr * 0.13, fill="#fff6e0", stroke=None, extra='opacity="0.9"'))


@asset("nature/cactus", "Cactus", "Nature", 1, 1, seed=134)
def cactus(S):
    r = S.rnd
    S.add(S.shadowed(circ(50, 50, 30, fill=S.rad([(0, "#7fb85a"), (1, "#3f7a34")], 0.4, 0.35, 0.8), stroke=OUT, sw=2.4), 3, 4, 3, 0.45))
    for i in range(12):
        a = 2 * math.pi * i / 12
        S.add(path(f"M50 50L{n(50 + math.cos(a) * 29)} {n(50 + math.sin(a) * 29)}", stroke="#2f5a2a", sw=1.4, extra='opacity="0.8"'))
    for i in range(24):
        a = 2 * math.pi * i / 24
        S.add(circ(50 + math.cos(a) * 22, 50 + math.sin(a) * 22, 1.1, fill="#f6ecc8", stroke=None))
    S.add(flower(50, 50, 8, "#ff6b9a", "#ffd23a", petals=6))


@asset("nature/crystals", "Crystals", "Nature", 1, 1, seed=135)
def crystals(S):
    S.add(circ(50, 50, 46, fill=S.rad([(0, "#9f7bff", 0.4), (1, "#6a4bd0", 0)]), stroke=None))
    for (x, y, h, a, c) in ((50, 58, 40, 0, "#7a5cf0"), (30, 66, 28, -22, "#5ab0f0"), (70, 66, 30, 20, "#b07af0"), (40, 40, 20, -35, "#8ad0ff"), (62, 38, 22, 30, "#9a7af0")):
        pts = [(x, y), (x - 8, y - h * 0.35), (x - 3, y - h), (x + 5, y - h * 0.95), (x + 9, y - h * 0.3)]
        shp = poly(pts, fill=S.lin([(0, shade(c, 1.35)), (0.5, c), (1, shade(c, 0.6))], 0, 0, 1, 1), stroke=shade(c, 0.4), sw=1.6)
        S.add(S.shadowed(rot(shp, a, x, y), 2, 3, 2, 0.5))
        S.add(rot(path(f"M{x - 2} {y - h * 0.9}L{x - 5} {y - h * 0.4}", stroke="#ffffff", sw=1.4, extra='opacity="0.8"'), a, x, y))


@asset("nature/tall-grass", "Tall grass", "Nature", 1, 1, seed=136)
def tall_grass(S):
    r = S.rnd
    for _ in range(46):
        x, y = 12 + r.random() * 76, 22 + r.random() * 66
        L = 14 + r.random() * 22
        a = math.radians(-90 + (r.random() - 0.5) * 70)
        ex, ey = x + math.cos(a) * L, y + math.sin(a) * L
        c = jitter("#5f9a3a" if r.random() < 0.6 else "#8dbf5a", r, 0.15)
        S.add(path(f"M{n(x)} {n(y)}Q{n(x + (ex - x) * 0.4)} {n(y + (ey - y) * 0.6)} {n(ex)} {n(ey)}", stroke=c, sw=1.8, extra='stroke-linecap="round"'))
