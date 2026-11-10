"""Mountains, hills, volcanoes and trees."""
import math

from worldlib import *


# ---------------------------------------------------------------- shared shapes

def faces(S, left, right, role, hatch_angle=62):
    """A solid with a lit side and a shaded side, drawn the way each style likes."""
    st = S.st
    if st.mode == "hatch":
        poly(S, left, st.c(role, 0.28))
        poly(S, right, st.c(role, 0.12))
        hatch(S, right, hatch_angle, 2.6, 0.55)
    elif st.mode == "flat":
        poly(S, left, st.c(role, 0.12))
        poly(S, right, st.c(role, -0.06))
    else:
        poly(S, left, st.c(role, 0.18))
        poly(S, right, st.c(role, -0.24))


def circle_poly(cx, cy, r, n=18):
    return [(cx + math.cos(2 * math.pi * i / n) * r, cy + math.sin(2 * math.pi * i / n) * r) for i in range(n)]


def ball(S, cx, cy, r, role, light=0.16, dark=-0.2):
    """A rounded mass (a tree top, a bush) with a lit upper left."""
    st = S.st
    circle(S, cx, cy, r, st.c(role, dark if st.mode == "faces" else 0.0))
    if st.mode == "hatch":
        hatch(S, circle_poly(cx, cy, r), 50, 2.4, 0.5)
    else:
        circle(S, cx - r * 0.22, cy - r * 0.22, r * 0.68, st.c(role, light), outline=False)
    circle(S, cx, cy, r, "none", w=1)  # the outline again, over the highlight


def peak(S, cx, base, w, h, snow=False, tilt=0.0, role="rock"):
    st = S.st
    ax, bx = cx - w / 2, cx + w / 2
    apex = (cx + tilt, base - h)
    foot = (cx + w * 0.06, base)
    faces(S, [(ax, base), apex, foot], [apex, (bx, base), foot], role)
    if snow:
        l, r = lerp(apex, (ax, base), 0.4), lerp(apex, (bx, base), 0.4)
        m1, m2 = lerp(l, r, 0.3), lerp(l, r, 0.62)
        cap = [apex, l, (m1[0], m1[1] + h * 0.1), lerp(l, r, 0.45), (m2[0], m2[1] + h * 0.1), r]
        poly(S, cap, st.c("snow", 0.0 if st.name != "dark" else 0.15), w=0.8)


def hill(S, cx, base, w, h, role="grass"):
    st = S.st
    x0, x1 = cx - w / 2, cx + w / 2
    d = "M%.1f,%.1f Q%.1f,%.1f %.1f,%.1f Z" % (x0, base, cx, base - h * 2, x1, base)
    path(S, d, st.c(role, 0.05))
    # the shaded right half
    half = [(cx, base - h)]
    for i in range(1, 9):
        t = 0.5 + 0.5 * i / 8
        px = x0 + (x1 - x0) * t
        py = base - 2 * h * (1 - t) * t * 2
        half.append((px, py))
    half.append((cx, base))
    if st.mode == "hatch":
        hatch(S, half + [(x1, base)], 62, 2.6, 0.55)
    else:
        poly(S, half + [(x1, base)], st.c(role, -0.18), outline=False, opacity=0.7)
    path(S, d, "none")


# ---------------------------------------------------------------- mountains

@feature("peak-single", "Mountain", "Mountains and hills", 8, h=90, family="mountains-rock")
def f_peak(S, rnd):
    shadow(S, 50, 80, 36, 6)
    peak(S, 50, 80, 64, 66, tilt=3)


@feature("peak-twin", "Twin peaks", "Mountains and hills", 11, h=90, family="mountains-rock")
def f_peak_twin(S, rnd):
    shadow(S, 50, 80, 44, 6)
    peak(S, 68, 80, 48, 52, tilt=-2)
    peak(S, 36, 80, 56, 66, tilt=4)


@feature("peak-snowy", "Snowy mountain", "Mountains and hills", 9, h=90, family="mountains-snow")
def f_peak_snow(S, rnd):
    shadow(S, 50, 80, 38, 6)
    peak(S, 50, 80, 68, 72, snow=True, tilt=-3)


@feature("range-3", "Mountain range", "Mountains and hills", 16, h=80, family="mountains-rock")
def f_range(S, rnd):
    shadow(S, 50, 72, 46, 6)
    peak(S, 78, 72, 40, 36, tilt=2)
    peak(S, 24, 72, 44, 42, tilt=-3)
    peak(S, 50, 74, 52, 56, tilt=3)


@feature("range-snowy", "Snowy range", "Mountains and hills", 16, h=80, family="mountains-snow")
def f_range_snow(S, rnd):
    shadow(S, 50, 72, 46, 6)
    peak(S, 80, 72, 38, 38, snow=True, tilt=2)
    peak(S, 22, 72, 42, 44, snow=True, tilt=-3)
    peak(S, 50, 74, 54, 60, snow=True, tilt=2)


@feature("mesa", "Mesa", "Mountains and hills", 9, h=70, family="mountains-rock")
def f_mesa(S, rnd):
    st = S.st
    shadow(S, 50, 62, 40, 5)
    left = [(14, 62), (22, 22), (52, 22), (52, 62)]
    right = [(52, 22), (82, 22), (90, 62), (52, 62)]
    faces(S, left, right, "dirt")
    top = [(22, 22), (52, 16), (82, 22), (52, 28)]
    poly(S, top, st.c("dirt", 0.3), w=0.8)
    for y in (36, 48):
        line(S, 16, y, 88, y, 0.5, opacity=0.35)


@feature("hill", "Hill", "Mountains and hills", 7, h=50, family="hills")
def f_hill(S, rnd):
    shadow(S, 50, 42, 40, 5)
    hill(S, 50, 42, 78, 24)


@feature("hills-pair", "Hills", "Mountains and hills", 10, h=50, family="hills")
def f_hills(S, rnd):
    shadow(S, 50, 42, 44, 5)
    hill(S, 68, 42, 52, 18)
    hill(S, 34, 42, 62, 26)


@feature("outcrop", "Rocky outcrop", "Mountains and hills", 5, h=50, family="rocks")
def f_outcrop(S, rnd):
    shadow(S, 50, 42, 34, 5)
    st = S.st
    faces(S, [(16, 42), (28, 18), (44, 12), (46, 42)], [(44, 12), (62, 20), (70, 42), (46, 42)], "rock")
    faces(S, [(60, 42), (68, 26), (80, 24), (84, 42)], [(80, 24), (90, 42), (84, 42)], "rock")


@feature("cliff", "Cliff", "Mountains and hills", 10, h=50, family="rocks")
def f_cliff(S, rnd):
    st = S.st
    shadow(S, 50, 42, 44, 5)
    top = [(8, 20), (30, 14), (60, 16), (92, 12), (92, 24), (8, 28)]
    face = [(8, 28), (92, 24), (92, 42), (8, 42)]
    poly(S, top, st.c("grass", 0.05))
    poly(S, face, st.c("rock", -0.1 if st.mode == "faces" else 0.1))
    if st.mode == "hatch":
        hatch(S, face, 80, 2.4, 0.55)
    for x in (22, 40, 58, 76):
        line(S, x, 28, x + rnd.uniform(-3, 3), 42, 0.6, opacity=0.45)


# ---------------------------------------------------------------- volcanoes

def cone(S, state):
    st = S.st
    shadow(S, 50, 86, 42, 6)
    ax, bx, base, top = 12, 88, 86, 34
    lw, rw = 41, 59  # crater rim
    left = [(ax, base), (lw, top), (50, top + 8), (50, base)]
    right = [(50, top + 8), (rw, top), (bx, base), (50, base)]
    faces(S, left, right, "ash" if state != "dormant" else "rock")
    ellipse(S, 50, top + 2, 10, 4.2, st.c("ash", -0.3), w=0.9)
    if state == "dormant":
        poly(S, [(lw + 2, top + 8), (50, top + 12), (rw - 2, top + 8), (50, top + 16)], st.c("grass", -0.1), outline=False, opacity=0.0)
        return
    ellipse(S, 50, top + 2.5, 7, 2.6, st.c("lava"), outline=False)
    if state == "erupting":
        # lava running down the slopes
        for sx, ex in ((44, 36), (55, 62), (50, 50)):
            path(S, "M%.1f,%.1f Q%.1f,%.1f %.1f,%.1f" % (50, top + 4, (sx + ex) / 2, top + 30, ex, top + 46), "none", outline=False,
                 extra='stroke="%s" stroke-width="3.2" stroke-linecap="round"' % st.c("lava"))
        for bx2, by2, br in ((44, 12, 3), (58, 8, 2.5), (51, 16, 2)):
            circle(S, bx2, by2, br, st.c("lava"), w=0.6)
    # smoke
    for i, (sx, sy, sr) in enumerate(((50, 26, 6), (56, 18, 7.5), (47, 10, 8.5), (55, 3, 7))):
        if state == "active" and i == 3:
            continue
        circle(S, sx, sy, sr, st.c("smoke", 0.25), w=0.8, opacity=0.9)


@feature("volcano-dormant", "Dormant volcano", "Mountains and hills", 11, h=100, family="volcanoes")
def f_volcano_dormant(S, rnd):
    cone(S, "dormant")


@feature("volcano-active", "Smoking volcano", "Mountains and hills", 11, h=100, family="volcanoes")
def f_volcano_active(S, rnd):
    cone(S, "active")


@feature("volcano-erupting", "Erupting volcano", "Mountains and hills", 11, h=100, family="volcanoes")
def f_volcano_erupting(S, rnd):
    cone(S, "erupting")


# ---------------------------------------------------------------- trees

def trunk(S, cx, base, w, h, role="trunk"):
    rect(S, cx - w / 2, base - h, w, h, S.st.c(role, -0.05 if S.st.mode == "faces" else 0.0), lw=0.8)


def tree_oak(S, cx, base, r, leaf="leaf"):
    shadow(S, cx, base, r * 0.9, r * 0.22)
    trunk(S, cx, base, r * 0.28, r * 1.0)
    ball(S, cx, base - r * 1.45, r, leaf)


def tree_pine(S, cx, base, h, w, layers=3, snowy=False):
    st = S.st
    shadow(S, cx, base, w * 0.55, w * 0.14)
    trunk(S, cx, base, w * 0.16, h * 0.16)
    top = base - h
    ly = h * 0.84 / layers
    for i in range(layers):
        yb = base - h * 0.12 - i * ly * 0.78
        yt = yb - ly * 1.35
        ww = w * (1 - i * 0.24) / 2
        left = [(cx - ww, yb), (cx, yt), (cx, yb)]
        right = [(cx, yt), (cx + ww, yb), (cx, yb)]
        faces(S, left, right, "leafdark" if not snowy else "leaf")
        if snowy:
            poly(S, [(cx, yt), (cx - ww * 0.6, yb - ly * 0.45), (cx - ww * 0.2, yb - ly * 0.35), (cx + ww * 0.15, yb - ly * 0.55), (cx + ww * 0.6, yb - ly * 0.45)],
                 st.c("snow"), w=0.5)


@feature("tree-oak", "Oak tree", "Forests", 3, h=70, family="deciduous")
def f_oak(S, rnd):
    tree_oak(S, 50, 64, 24)


@feature("tree-oak-dark", "Dark oak", "Forests", 3, h=70, family="deciduous")
def f_oak_dark(S, rnd):
    tree_oak(S, 50, 64, 24, "leafdark")


@feature("tree-birch", "Birch tree", "Forests", 3, h=70, family="deciduous")
def f_birch(S, rnd):
    st = S.st
    shadow(S, 50, 64, 20, 5)
    rect(S, 46, 36, 8, 28, st.c("bone"), lw=0.8)
    for y in (42, 50, 58):
        line(S, 46.5, y, 51, y, 0.9)
    ball(S, 50, 28, 20, "grass", 0.2, -0.12)


@feature("tree-pine", "Pine tree", "Forests", 3, h=90, family="conifer")
def f_pine(S, rnd):
    tree_pine(S, 50, 84, 80, 44)


@feature("tree-pine-snowy", "Snowy pine", "Forests", 3, h=90, family="conifer-snow")
def f_pine_snow(S, rnd):
    tree_pine(S, 50, 84, 80, 44, snowy=True)


@feature("tree-jungle", "Jungle tree", "Forests", 4, h=80, family="jungle")
def f_jungle(S, rnd):
    st = S.st
    shadow(S, 50, 74, 30, 6)
    trunk(S, 50, 74, 7, 34)
    ball(S, 36, 38, 17, "leafdark")
    ball(S, 64, 36, 18, "leaf", 0.12, -0.25)
    ball(S, 50, 26, 20, "leafdark", 0.2, -0.15)
    for x in (30, 44, 58, 70):
        line(S, x, 44, x + rnd.uniform(-2, 2), 54, 0.7, opacity=0.5)


@feature("tree-palm", "Palm tree", "Forests", 3, h=80, family="jungle")
def f_palm(S, rnd):
    st = S.st
    shadow(S, 50, 74, 22, 5)
    path(S, "M48,74 Q54,50 50,30", "none", extra='stroke="%s" stroke-width="%.1f" stroke-linecap="round"' % (st.ink, 3.6 + st.ow * 1.6))
    path(S, "M48,74 Q54,50 50,30", "none", extra='stroke="%s" stroke-width="3.6" stroke-linecap="round"' % st.c("trunk"))
    for ang in (-160, -125, -90, -55, -20):
        a = math.radians(ang)
        ex, ey = 50 + math.cos(a) * 30, 30 + math.sin(a) * 18 + 8
        path(S, "M50,30 Q%.1f,%.1f %.1f,%.1f Q%.1f,%.1f 50,32 Z" % ((50 + ex) / 2, 16 + (ey - 30) * 0.2, ex, ey, (50 + ex) / 2, 30 + (ey - 30) * 0.7),
             st.c("leaf", 0.05), w=0.8)
    circle(S, 48, 33, 2.2, st.c("trunk", -0.1), w=0.6)
    circle(S, 53, 34, 2.2, st.c("trunk", -0.1), w=0.6)


@feature("tree-dead", "Dead tree", "Forests", 3, h=80, family="dead")
def f_dead(S, rnd):
    st = S.st
    shadow(S, 50, 74, 18, 4)
    col = st.c("trunk", -0.25)
    for d, w in (("M50,74 L49,40 L46,22", 4.5), ("M49,52 L38,38 L34,28", 2.6), ("M49,44 L60,34 L64,24", 2.6), ("M47,28 L42,18", 1.8), ("M48,34 L56,26", 1.6)):
        path(S, d, "none", extra='stroke="%s" stroke-width="%.1f" stroke-linecap="round" stroke-linejoin="round"' % (st.ink, w + st.ow * 1.6), outline=False)
        path(S, d, "none", extra='stroke="%s" stroke-width="%.1f" stroke-linecap="round" stroke-linejoin="round"' % (col, w), outline=False)


@feature("tree-enchanted", "Enchanted tree", "Forests", 4, h=80, family="enchanted")
def f_enchanted(S, rnd):
    st = S.st
    shadow(S, 50, 74, 26, 5)
    trunk(S, 50, 74, 8, 28, "trunk")
    ball(S, 50, 38, 26, "blue", 0.22, -0.1)
    for sx, sy in ((26, 26), (74, 30), (62, 14), (36, 12), (50, 52)):
        s = 3.4
        poly(S, [(sx, sy - s), (sx + s * 0.35, sy - s * 0.35), (sx + s, sy), (sx + s * 0.35, sy + s * 0.35), (sx, sy + s), (sx - s * 0.35, sy + s * 0.35), (sx - s, sy), (sx - s * 0.35, sy - s * 0.35)],
             st.c("gold", 0.2), w=0.5)


@feature("tree-mushroom", "Giant mushroom", "Forests", 3, h=70, family="enchanted")
def f_mushroom(S, rnd):
    st = S.st
    shadow(S, 50, 62, 22, 4)
    rect(S, 43, 36, 14, 26, st.c("bone"), lw=0.8, r=3)
    path(S, "M16,40 Q50,-6 84,40 Q50,48 16,40 Z", st.c("cloth"), w=1)
    for cx, cy, r in ((36, 28, 4), (54, 20, 4.5), (66, 32, 3.5), (47, 34, 2.8)):
        circle(S, cx, cy, r, st.c("bone", 0.1), w=0.5)


@feature("tree-mangrove", "Mangrove", "Forests", 4, h=70, family="jungle")
def f_mangrove(S, rnd):
    st = S.st
    shadow(S, 50, 62, 30, 5)
    for x0 in (34, 42, 50, 58, 66):
        line(S, x0, 62, 50 + (x0 - 50) * 0.3, 44, 1.2, color=st.c("trunk", -0.2), opacity=1.0)
    ball(S, 50, 34, 22, "leaf", 0.1, -0.25)


@feature("forest-cluster", "Forest", "Forests", 8, h=80, family="forest")
def f_forest(S, rnd):
    for cx, base, r in ((22, 58, 15), (74, 56, 15), (48, 52, 17), (34, 72, 17), (66, 72, 16), (84, 74, 12), (12, 74, 12)):
        tree_oak(S, cx, base, r, "leaf" if rnd.random() < 0.6 else "leafdark")


@feature("pine-cluster", "Pine forest", "Forests", 8, h=100, family="forest")
def f_pines(S, rnd):
    for cx, base, h, w in ((22, 62, 52, 28), (76, 60, 54, 28), (49, 56, 60, 32), (34, 84, 56, 30), (66, 86, 58, 30), (88, 88, 40, 22), (10, 88, 40, 22)):
        tree_pine(S, cx, base, h, w)


@feature("jungle-cluster", "Jungle", "Forests", 8, h=80, family="forest")
def f_jungle_cluster(S, rnd):
    for cx, base, r, role in ((20, 56, 15, "leafdark"), (76, 54, 16, "leaf"), (48, 50, 18, "leafdark"), (32, 72, 16, "leaf"), (68, 72, 17, "leafdark"), (86, 74, 11, "leaf")):
        tree_oak(S, cx, base, r, role)
