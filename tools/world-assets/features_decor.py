"""Decorations (compasses, sea monsters, ships, dragons, flourishes) and banners that carry editable text."""
import math

from worldlib import *

DEC = "Decorations"
BAN = "Banners"


# ---------------------------------------------------------------- compasses

def star_points(cx, cy, r_out, r_in, n, rot=-90):
    out = []
    for i in range(n * 2):
        a = math.radians(rot + i * 180.0 / n)
        r = r_out if i % 2 == 0 else r_in
        out.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    return out


def rose(S, points_n, long_r, mid_r, ring=True):
    st = S.st
    cx = cy = 50
    if ring:
        circle(S, cx, cy, long_r * 0.62, "none", w=0.9)
        circle(S, cx, cy, long_r * 0.55, "none", w=0.5)
    if points_n >= 16:
        poly(S, star_points(cx, cy, mid_r, mid_r * 0.25, 16, -90 + 11.25), st.c("gold", -0.05), w=0.5)
    if points_n >= 8:
        poly(S, star_points(cx, cy, mid_r * 1.2, mid_r * 0.28, 4, -45), st.c("wall", -0.05), w=0.7)
    # the four main points, each half light and half dark
    for k in range(4):
        a = math.radians(-90 + k * 90)
        tip = (cx + long_r * math.cos(a), cy + long_r * math.sin(a))
        l = (cx + long_r * 0.16 * math.cos(a - math.pi / 2), cy + long_r * 0.16 * math.sin(a - math.pi / 2))
        r = (cx + long_r * 0.16 * math.cos(a + math.pi / 2), cy + long_r * 0.16 * math.sin(a + math.pi / 2))
        poly(S, [tip, l, (cx, cy)], st.c("wall", 0.15), w=0.7)
        poly(S, [tip, r, (cx, cy)], st.c("red" if k == 0 else "stone", -0.1), w=0.7)
    circle(S, cx, cy, long_r * 0.06, st.c("gold"), w=0.6)
    # north marker: a fleur
    poly(S, [(cx, cy - long_r - 3), (cx - 4, cy - long_r + 5), (cx + 4, cy - long_r + 5)], st.c("red"), w=0.6)


@feature("deco-compass-rose", "Compass rose", DEC, 12, h=100, anchor="center", family="compass")
def f_compass_rose(S, rnd):
    rose(S, 16, 44, 30)


@feature("deco-compass-simple", "Simple compass", DEC, 9, h=100, anchor="center", family="compass")
def f_compass_simple(S, rnd):
    rose(S, 4, 40, 28, ring=False)


@feature("deco-wind-rose", "Wind rose", DEC, 14, h=100, anchor="center", family="compass")
def f_wind_rose(S, rnd):
    st = S.st
    circle(S, 50, 50, 46, st.c("wall", 0.2), w=1.0, opacity=0.5)
    for i in range(32):
        a = math.radians(i * 11.25)
        r1, r2 = (46, 40) if i % 4 else (46, 36)
        line(S, 50 + r1 * math.cos(a), 50 + r1 * math.sin(a), 50 + r2 * math.cos(a), 50 + r2 * math.sin(a), 0.8, color=st.ink, opacity=0.9)
    rose(S, 16, 36, 25, ring=False)


@feature("deco-north-arrow", "North arrow", DEC, 4, h=130, anchor="center", family="compass")
def f_north(S, rnd):
    st = S.st
    poly(S, [(50, 8), (66, 90), (50, 76), (34, 90)], st.c("wall", 0.1), w=1.0)
    poly(S, [(50, 8), (66, 90), (50, 76)], st.c("stone", -0.15), w=0.8)
    path(S, "M40,108 L40,126 M40,108 L60,126 M60,108 L60,126", "none", w=1.4)


# ---------------------------------------------------------------- creatures of the sea and sky

def wave(S, x, y, w, op=0.9):
    path(S, "M%.1f,%.1f q%.1f,-%.1f %.1f,0 t%.1f,0" % (x, y, w / 4, w / 6, w / 2, w / 2), "none", w=1.0, opacity=op)


@feature("deco-kraken", "Kraken", DEC, 12, h=95, anchor="center", family="monster")
def f_kraken(S, rnd):
    st = S.st
    shadow(S, 50, 82, 40, 5)
    for i, (x0, ctl, x1) in enumerate(((10, 0, 4), (24, 16, 18), (38, 36, 34))):
        pass
    arms = [(34, 62, -14, 8), (40, 66, -6, 2), (46, 68, 0, 12), (54, 68, 0, 12), (60, 66, 6, 2), (66, 62, 14, 8)]
    for sx, sy, dx, dy in arms:
        d = "M%d,%d q%d,%d %d,%d t%d,%d" % (sx, sy, dx, 22, dx * 1.5, 8, dx * 0.6, -dy - 18)
        path(S, d, "none", extra='stroke="%s" stroke-width="5" stroke-linecap="round"' % st.c("red", -0.1))
        path(S, d, "none", extra='stroke="%s" stroke-width="3.2" stroke-linecap="round"' % st.c("red", 0.1))
    ellipse(S, 50, 44, 20, 24, st.c("red", 0.0), w=1.0)
    path(S, "M32,40 Q50,8 68,40 Q50,30 32,40 Z", st.c("red", -0.15), w=0.8)
    circle(S, 42, 46, 3.4, st.c("bone", 0.3), w=0.6)
    circle(S, 58, 46, 3.4, st.c("bone", 0.3), w=0.6)
    circle(S, 42, 46.5, 1.4, st.c("dark"), outline=False)
    circle(S, 58, 46.5, 1.4, st.c("dark"), outline=False)
    for x in (18, 76):
        wave(S, x, 84, 14)


@feature("deco-sea-serpent", "Sea serpent", DEC, 13, h=70, anchor="center", family="monster")
def f_serpent(S, rnd):
    st = S.st
    for cx, ry in ((22, 14), (46, 16), (70, 13)):
        path(S, "M%d,56 a%d,%d 0 0 1 %d,0 Z" % (cx - 9, 9, ry, 18), st.c("leaf", -0.05), w=1.0)
        path(S, "M%d,56 a%d,%d 0 0 1 %d,0" % (cx - 5, 5, ry - 6, 10), "none", w=0.5, opacity=0.5)
    path(S, "M10,56 Q14,40 22,42", "none", extra='stroke="%s" stroke-width="2.5" stroke-linecap="round"' % st.c("leaf"))
    path(S, "M78,56 Q84,40 80,28 Q78,18 86,16 L96,20 L88,24 Q90,34 88,56 Z", st.c("leaf", 0.05), w=1.0)
    circle(S, 86, 20, 1.3, st.c("gold", 0.3), outline=False)
    poly(S, [(80, 20), (76, 10), (84, 16)], st.c("red"), w=0.6)
    for x, w in ((6, 18), (30, 18), (60, 20), (82, 14)):
        wave(S, x, 58, w)


@feature("deco-whale", "Whale", DEC, 11, h=60, anchor="center", family="monster")
def f_whale(S, rnd):
    st = S.st
    path(S, "M8,44 Q10,18 46,20 Q84,20 92,36 Q88,30 86,24 Q92,36 96,18 Q98,34 92,44 Q60,56 8,44 Z", st.c("blue", -0.05), w=1.0)
    path(S, "M10,44 Q50,52 90,42 Q60,46 10,44 Z", st.c("bone", 0.2), outline=False, opacity=0.8)
    circle(S, 24, 36, 1.4, st.c("dark"), outline=False)
    path(S, "M30,18 Q28,6 22,4 M30,18 Q34,6 40,4", "none", w=0.9, opacity=0.9)
    for x in (8, 40, 68):
        wave(S, x, 52, 18)


@feature("deco-giant-crab", "Giant crab", DEC, 8, h=70, anchor="center", family="monster")
def f_crab(S, rnd):
    st = S.st
    shadow(S, 50, 66, 34, 4)
    for sx in (-1, 1):
        for i in range(3):
            path(S, "M%d,%d l%d,-8 l%d,14" % (50 + sx * 14, 46 + i * 4, sx * (10 + i * 3), sx * 5), "none", w=1.2)
        path(S, "M%d,40 q%d,-20 %d,-26 q-8,2 -12,10 q-2,-10 4,-14 q10,4 12,18" % (50 + sx * 16, sx * 6, sx * 12), st.c("red", 0.0), w=0.9)
    ellipse(S, 50, 46, 22, 14, st.c("red", 0.05), w=1.0)
    circle(S, 42, 34, 2.2, st.c("bone", 0.3), w=0.5)
    circle(S, 58, 34, 2.2, st.c("bone", 0.3), w=0.5)


@feature("deco-galleon", "Sailing ship", DEC, 8, h=90, anchor="center", family="ship")
def f_galleon(S, rnd):
    st = S.st
    wave(S, 8, 80, 30)
    wave(S, 60, 82, 30)
    path(S, "M16,62 L84,62 Q78,78 64,80 L34,80 Q22,78 16,62 Z", st.c("wood", -0.05), w=1.0)
    rect(S, 14, 58, 14, 5, st.c("wood", 0.1), lw=0.7)
    for x, h, w in ((36, 46, 22), (58, 52, 24)):
        line(S, x, 62, x, 62 - h, 1.6, color=st.c("wood", -0.25), opacity=1.0)
        path(S, "M%d,%d Q%d,%d %d,%d L%d,%d Z" % (x - w / 2, 60 - h * 0.15, x - w / 2 - 3, 62 - h * 0.55, x - w / 2 + 1, 62 - h * 0.9, x + w / 2 - 1, 62 - h * 0.9),
             st.c("snow", -0.04), w=0.8)
        path(S, "M%d,%d Q%d,%d %d,%d" % (x - w / 2 + 1, 62 - h * 0.9, x, 62 - h * 0.55, x + w / 2 - 1, 62 - h * 0.9), "none", w=0.6, opacity=0.6)
    poly(S, [(58, 10), (72, 14), (58, 18)], st.c("cloth"), w=0.6)
    poly(S, [(80, 62), (96, 56), (84, 66)], st.c("snow", -0.04), w=0.7)


@feature("deco-longship", "Longship", DEC, 7, h=60, anchor="center", family="ship")
def f_longship(S, rnd):
    st = S.st
    wave(S, 4, 52, 28)
    wave(S, 56, 54, 30)
    path(S, "M6,34 Q10,28 12,22 Q14,34 24,44 L76,44 Q86,34 88,22 Q90,28 94,34 Q86,50 72,52 L28,52 Q14,50 6,34 Z", st.c("wood", -0.1), w=1.0)
    line(S, 50, 44, 50, 8, 1.6, color=st.c("wood", -0.25), opacity=1.0)
    poly(S, [(50, 8), (74, 12), (74, 34), (50, 38)], st.c("cloth"), w=0.8)
    for x in (30, 40, 60, 70):
        circle(S, x, 45, 2.6, st.c("gold", -0.1), w=0.6)


@feature("deco-dragon", "Dragon", DEC, 12, h=80, anchor="center", family="monster")
def f_dragon(S, rnd):
    st = S.st
    shadow(S, 50, 72, 36, 4)
    path(S, "M30,44 Q8,18 6,6 Q22,16 38,34 Z", st.c("red", -0.1), w=0.9)
    path(S, "M60,44 Q86,16 94,4 Q80,14 66,36 Z", st.c("red", -0.1), w=0.9)
    path(S, "M12,70 Q14,52 34,50 Q46,36 62,44 Q72,40 82,28 L92,26 L86,36 Q80,42 70,50 Q66,64 52,64 Q40,66 36,62 Q28,64 18,70 Z", st.c("red", 0.0), w=1.0)
    path(S, "M12,70 Q4,72 2,64 Q6,66 10,64", "none", w=1.0)
    circle(S, 80, 29, 1.2, st.c("gold", 0.4), outline=False)
    poly(S, [(82, 26), (88, 18), (84, 28)], st.c("bone"), w=0.5)
    path(S, "M92,26 q4,2 6,-1", "none", w=0.8)
    for x in (34, 44, 54):
        poly(S, [(x, 46), (x + 3, 40), (x + 6, 46)], st.c("red", -0.2), w=0.5)


@feature("deco-flourish", "Flourish", DEC, 10, h=40, anchor="center", family="flourish")
def f_flourish(S, rnd):
    st = S.st
    d = "M4,20 C16,4 28,4 36,20 S50,36 50,20 S50,4 64,20 S84,36 96,20"
    path(S, d, "none", w=1.5)
    circle(S, 50, 20, 3, st.c("gold"), w=0.7)
    for x in (4, 96):
        poly(S, [(x, 14), (x + (6 if x < 50 else -6), 20), (x, 26)], st.c("gold", -0.1), w=0.6)


@feature("deco-divider", "Divider", DEC, 10, h=20, anchor="center", family="flourish")
def f_divider(S, rnd):
    st = S.st
    line(S, 4, 10, 40, 10, 1.4, color=st.ink, opacity=1.0)
    line(S, 60, 10, 96, 10, 1.4, color=st.ink, opacity=1.0)
    poly(S, [(50, 2), (56, 10), (50, 18), (44, 10)], st.c("gold"), w=0.8)


@feature("deco-corner", "Corner piece", DEC, 8, h=100, anchor="center", family="flourish")
def f_corner(S, rnd):
    st = S.st
    path(S, "M6,94 L6,6 L94,6", "none", w=2.2)
    path(S, "M14,94 L14,14 L94,14", "none", w=0.8)
    path(S, "M6,6 C30,6 40,20 34,34 C28,48 14,40 20,28", "none", w=1.3)
    circle(S, 22, 22, 4, st.c("gold"), w=0.7)


@feature("deco-anchor", "Anchor", DEC, 4, h=100, anchor="center", family="flourish")
def f_anchor(S, rnd):
    st = S.st
    circle(S, 50, 14, 7, "none", w=1.4)
    line(S, 50, 21, 50, 86, 3.0, color=st.ink, opacity=1.0)
    line(S, 34, 34, 66, 34, 3.0, color=st.ink, opacity=1.0)
    path(S, "M16,62 Q22,90 50,90 Q78,90 84,62", "none", w=2.6)
    poly(S, [(10, 66), (22, 60), (20, 72)], st.c("stone"), w=0.7)
    poly(S, [(90, 66), (78, 60), (80, 72)], st.c("stone"), w=0.7)


# ---------------------------------------------------------------- banners (text is drawn over them by the app)

@feature("banner-scroll", "Scroll", BAN, 14, h=36, anchor="center", family="banner")
def f_scroll(S, rnd):
    st = S.st
    rect(S, 10, 8, 80, 20, st.c("wall", 0.25), lw=1.0)
    for x in (10, 90):
        ellipse(S, x, 18, 5, 12, st.c("wall", 0.05), w=1.0)
        ellipse(S, x, 18, 2, 7, st.c("wall", -0.2), w=0.6)


@feature("banner-ribbon", "Ribbon", BAN, 14, h=36, anchor="center", family="banner")
def f_ribbon(S, rnd):
    st = S.st
    poly(S, [(0, 12), (14, 12), (14, 30), (0, 30), (6, 21)], st.c("red", -0.2), w=0.9)
    poly(S, [(100, 12), (86, 12), (86, 30), (100, 30), (94, 21)], st.c("red", -0.2), w=0.9)
    rect(S, 10, 6, 80, 22, st.c("red", 0.0), lw=1.0)
    path(S, "M10,28 L14,34 L14,28 Z", st.c("red", -0.35), w=0.6)
    path(S, "M90,28 L86,34 L86,28 Z", st.c("red", -0.35), w=0.6)


@feature("banner-plaque", "Plaque", BAN, 12, h=36, anchor="center", family="banner")
def f_plaque(S, rnd):
    st = S.st
    rect(S, 2, 4, 96, 28, st.c("wood", 0.0), lw=1.2, r=4)
    rect(S, 6, 8, 88, 20, st.c("wall", 0.25), lw=0.7, r=2)
    for x, y in ((6, 8), (94, 8), (6, 28), (94, 28)):
        circle(S, x, y, 1.4, st.c("gold"), outline=False)


@feature("banner-cartouche", "Cartouche", BAN, 14, h=50, anchor="center", family="banner")
def f_cartouche(S, rnd):
    st = S.st
    path(S, "M10,14 Q50,-4 90,14 Q100,25 90,36 Q50,54 10,36 Q0,25 10,14 Z", st.c("wall", 0.25), w=1.3)
    path(S, "M16,17 Q50,3 84,17 Q92,25 84,33 Q50,47 16,33 Q8,25 16,17 Z", "none", w=0.6, opacity=0.7)
    for x in (2, 98):
        circle(S, x, 25, 3, st.c("gold"), w=0.7)


@feature("banner-pennant", "Hanging banner", BAN, 6, h=110, anchor="base", family="banner")
def f_pennant(S, rnd):
    st = S.st
    rect(S, 4, 2, 92, 5, st.c("wood", -0.1), lw=0.9)
    path(S, "M10,7 L90,7 L90,88 L50,104 L10,88 Z", st.c("cloth"), w=1.1)
    path(S, "M16,13 L84,13 L84,84 L50,98 L16,84 Z", "none", w=0.6, opacity=0.6)
    for x in (10, 90):
        circle(S, x, 4, 3, st.c("gold"), w=0.6)


@feature("banner-heraldic", "Heraldic shield", BAN, 6, h=100, anchor="center", family="banner")
def f_shield(S, rnd):
    st = S.st
    path(S, "M12,10 L88,10 L88,48 Q88,82 50,96 Q12,82 12,48 Z", st.c("blue", 0.0), w=1.3)
    path(S, "M12,10 L88,10 L88,48 L50,48 L50,96 Q12,82 12,48 Z", st.c("cloth"), outline=False, opacity=0.9)
    path(S, "M12,10 L88,10 L88,48 Q88,82 50,96 Q12,82 12,48 Z", "none", w=1.3)
    poly(S, star_points(50, 40, 12, 5, 5), st.c("gold"), w=0.7)
