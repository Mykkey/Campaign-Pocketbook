"""Water, caves and holes, desert, arctic and swamp, and landmarks."""
import math

from worldlib import *
from features_land import faces, ball, hill, tree_oak, tree_pine


def wave_lines(S, cx, cy, w, n=3):
    for i in range(n):
        y = cy + i * 5
        x0 = cx - w / 2 + i * 3
        path(S, "M%.1f,%.1f q%.1f,-3 %.1f,0 t%.1f,0 t%.1f,0" % (x0, y, w / 6, w / 3, w / 3, w / 3), "none", outline=False,
             extra='stroke="%s" stroke-width="%.1f" stroke-opacity="0.55" stroke-linecap="round"' % (S.st.c("water", 0.45), 1.1))


def pond(S, cx, cy, rx, ry, rnd, role="water"):
    st = S.st
    pts_ = blob_points(rnd, cx, cy, rx, ry, 10, 0.14)
    path(S, smooth_closed(pts_), st.c(role, 0.0))
    wave_lines(S, cx, cy - 3, rx * 1.1, 2)


# ---------------------------------------------------------------- water

@feature("lake-small", "Lake", "Water", 8, h=60, family="lakes")
def f_lake(S, rnd):
    pond(S, 50, 30, 42, 22, rnd)


@feature("pond", "Pond", "Water", 4, h=40, family="lakes")
def f_pond(S, rnd):
    pond(S, 50, 20, 38, 14, rnd)


@feature("waterfall", "Waterfall", "Water", 6, h=90)
def f_waterfall(S, rnd):
    st = S.st
    shadow(S, 50, 82, 36, 5)
    poly(S, [(10, 80), (14, 14), (50, 6), (90, 16), (88, 80)], st.c("rock", 0.1 if st.mode != "faces" else 0.0))
    if st.mode == "hatch":
        hatch(S, [(10, 80), (14, 14), (22, 12), (22, 80)], 70, 2.6, 0.5)
    poly(S, [(38, 10), (62, 10), (66, 76), (34, 76)], st.c("water", 0.35))
    for x in (42, 50, 58):
        line(S, x, 16, x + (x - 50) * 0.2, 70, 0.9, color=st.c("snow"), opacity=0.9)
    ellipse(S, 50, 80, 28, 7, st.c("water"), w=0.9)
    ellipse(S, 50, 80, 14, 3, st.c("snow"), outline=False, opacity=0.8)


@feature("oasis", "Oasis", "Water", 6, h=80, family="lakes")
def f_oasis(S, rnd):
    st = S.st
    pond(S, 50, 56, 34, 15, rnd)
    for cx, h in ((28, 34), (70, 38)):
        line(S, cx, 52, cx + (50 - cx) * -0.12, 52 - h, 1.0, color=st.c("trunk", -0.1), opacity=1.0)
        for ang in (-150, -100, -50):
            a = math.radians(ang)
            ex, ey = cx + math.cos(a) * 14, 52 - h + math.sin(a) * 9 + 3
            path(S, "M%.1f,%.1f Q%.1f,%.1f %.1f,%.1f" % (cx, 52 - h, (cx + ex) / 2, 52 - h - 7, ex, ey), "none", extra='stroke="%s" stroke-width="3.2" stroke-linecap="round"' % st.c("leaf"))


@feature("whirlpool", "Whirlpool", "Water", 6, h=60, family="sea")
def f_whirlpool(S, rnd):
    st = S.st
    ellipse(S, 50, 30, 42, 24, st.c("water", -0.05), w=1)
    d = "M50,30 "
    for i in range(1, 40):
        a = i * 0.45
        r = i * 0.95
        d += "L%.1f,%.1f " % (50 + math.cos(a) * r * 1.0, 30 + math.sin(a) * r * 0.56)
    path(S, d, "none", extra='stroke="%s" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"' % st.c("snow", 0.0))
    ellipse(S, 50, 30, 4, 2.4, st.c("water", -0.4), outline=False)


@feature("reef", "Coral reef", "Water", 6, h=50, family="sea")
def f_reef(S, rnd):
    st = S.st
    wave_lines(S, 50, 8, 80, 2)
    for cx, cy, r, role in ((22, 34, 8, "red"), (40, 30, 10, "cloth"), (58, 34, 9, "gold"), (76, 32, 8, "red"), (48, 40, 7, "cloth")):
        ellipse(S, cx, cy, r, r * 0.8, st.c(role, 0.05), w=0.8)


@feature("islet", "Islet", "Water", 5, h=60, family="sea")
def f_islet(S, rnd):
    st = S.st
    wave_lines(S, 50, 48, 90, 2)
    pts_ = blob_points(rnd, 50, 38, 36, 14, 9, 0.15)
    path(S, smooth_closed(pts_), st.c("sand", 0.05))
    tree_oak(S, 40, 38, 13)
    tree_oak(S, 60, 36, 11, "leafdark")


@feature("iceberg", "Iceberg", "Water", 6, h=70, family="sea")
def f_iceberg(S, rnd):
    st = S.st
    wave_lines(S, 50, 56, 84, 2)
    faces(S, [(14, 54), (26, 20), (44, 8), (48, 54)], [(44, 8), (64, 22), (84, 54), (48, 54)], "ice")
    poly(S, [(20, 54), (30, 62), (50, 66), (74, 62), (84, 54)], st.c("ice", -0.2), outline=False, opacity=0.5)


# ---------------------------------------------------------------- caves and holes

@feature("cave-mouth", "Cave", "Caves and holes", 6, h=70, family="caves")
def f_cave(S, rnd):
    st = S.st
    shadow(S, 50, 62, 40, 5)
    hill(S, 50, 62, 84, 30, "rock")
    path(S, "M36,62 Q36,38 50,38 Q64,38 64,62 Z", st.c("dark", 0.0), w=1)
    path(S, "M42,62 Q42,46 50,46 Q58,46 58,62 Z", "#000000", outline=False, opacity=0.5)


@feature("crater", "Crater", "Caves and holes", 6, h=50, family="caves")
def f_crater(S, rnd):
    st = S.st
    ellipse(S, 50, 26, 42, 18, st.c("rock", 0.05), w=1)
    ellipse(S, 50, 28, 32, 12, st.c("dirt", -0.25), w=0.8)
    ellipse(S, 50, 30, 20, 6, "#000000", outline=False, opacity=0.35)
    path(S, "M10,26 Q50,-6 90,26", "none", outline=False, extra='stroke="%s" stroke-width="1.6" stroke-opacity="0.7" stroke-linecap="round"' % st.c("rock", 0.4))


@feature("canyon", "Canyon", "Caves and holes", 10, h=50, family="caves")
def f_canyon(S, rnd):
    st = S.st
    poly(S, [(4, 14), (30, 10), (62, 16), (96, 10), (96, 22), (66, 28), (34, 24), (4, 26)], st.c("dirt", 0.05))
    path(S, "M4,22 Q30,16 52,24 T96,18 L96,26 Q70,32 50,30 T4,30 Z", st.c("dirt", -0.35), w=0.8)
    for x in (14, 40, 70, 86):
        line(S, x, 12, x + 2, 22, 0.5, opacity=0.4)


@feature("sinkhole", "Sinkhole", "Caves and holes", 5, h=50, family="caves")
def f_sinkhole(S, rnd):
    st = S.st
    pts_ = blob_points(rnd, 50, 24, 40, 17, 11, 0.12)
    path(S, smooth_closed(pts_), st.c("rock", 0.0))
    pts2 = blob_points(rnd, 50, 26, 28, 11, 10, 0.1)
    path(S, smooth_closed(pts2), "#15110d", w=0.8)


@feature("ice-cave", "Ice cave", "Caves and holes", 6, h=70, family="caves")
def f_ice_cave(S, rnd):
    st = S.st
    shadow(S, 50, 62, 40, 5)
    hill(S, 50, 62, 84, 32, "ice")
    path(S, "M38,62 Q38,40 50,40 Q62,40 62,62 Z", st.c("blue", -0.45), w=1)
    for x in (22, 70):
        poly(S, [(x, 40), (x + 3, 30), (x + 6, 40)], st.c("snow"), w=0.6)


@feature("lava-vent", "Lava vent", "Caves and holes", 5, h=60, family="caves")
def f_lava_vent(S, rnd):
    st = S.st
    shadow(S, 50, 52, 30, 4)
    hill(S, 50, 52, 64, 22, "ash")
    ellipse(S, 50, 36, 12, 4.4, st.c("lava"), w=0.8)
    for cx, cy, r in ((46, 24, 3), (55, 20, 2.4), (50, 12, 2)):
        circle(S, cx, cy, r, st.c("lava", 0.1), w=0.5)


@feature("geyser", "Geyser", "Caves and holes", 5, h=80, family="caves")
def f_geyser(S, rnd):
    st = S.st
    shadow(S, 50, 70, 26, 4)
    hill(S, 50, 70, 56, 16, "rock")
    for cx, cy, r in ((50, 50, 5), (49, 38, 7), (52, 24, 9), (47, 12, 8)):
        circle(S, cx, cy, r, st.c("snow", 0.0), w=0.8, opacity=0.95)


# ---------------------------------------------------------------- desert, arctic, swamp

def dune(S, cx, base, w, h):
    st = S.st
    d = "M%.1f,%.1f Q%.1f,%.1f %.1f,%.1f Q%.1f,%.1f %.1f,%.1f Z" % (cx - w / 2, base, cx - w * 0.1, base - h * 2.1, cx + w * 0.12, base - h, cx + w * 0.3, base - h * 0.4, cx + w / 2, base)
    path(S, d, st.c("sand", 0.05))
    shade_ = [(cx + w * 0.12, base - h), (cx + w * 0.3, base - h * 0.4), (cx + w / 2, base), (cx + w * 0.1, base)]
    if st.mode == "hatch":
        hatch(S, shade_, 70, 2.4, 0.5)
    else:
        poly(S, shade_, st.c("sand", -0.25), outline=False, opacity=0.6)


@feature("dunes", "Dune", "Desert, arctic and swamp", 7, h=40, family="dunes")
def f_dune(S, rnd):
    dune(S, 50, 34, 84, 18)


@feature("dunes-pair", "Dunes", "Desert, arctic and swamp", 10, h=50, family="dunes")
def f_dunes(S, rnd):
    dune(S, 64, 42, 64, 22)
    dune(S, 34, 42, 62, 18)


def cactus(S, cx, base, h):
    st = S.st
    col = st.c("cactus")
    rect(S, cx - 4, base - h, 8, h, col, lw=0.8, r=4)
    rect(S, cx - 15, base - h * 0.62, 6, h * 0.34, col, lw=0.8, r=3)
    rect(S, cx - 15, base - h * 0.34, 12, 5, col, lw=0.8, r=2.5)
    rect(S, cx + 9, base - h * 0.78, 6, h * 0.34, col, lw=0.8, r=3)
    rect(S, cx + 4, base - h * 0.46, 11, 5, col, lw=0.8, r=2.5)


@feature("cactus", "Cactus", "Desert, arctic and swamp", 3, h=70, family="cacti")
def f_cactus(S, rnd):
    shadow(S, 50, 64, 20, 4)
    cactus(S, 50, 64, 54)


@feature("cactus-group", "Cacti", "Desert, arctic and swamp", 6, h=70, family="cacti")
def f_cacti(S, rnd):
    shadow(S, 50, 64, 40, 5)
    cactus(S, 28, 62, 40)
    cactus(S, 72, 64, 44)
    cactus(S, 50, 66, 54)


@feature("rock-arch", "Rock arch", "Desert, arctic and swamp", 7, h=70, family="rocks")
def f_arch(S, rnd):
    st = S.st
    shadow(S, 50, 62, 40, 5)
    path(S, "M10,62 L16,28 Q50,-6 84,28 L90,62 L70,62 L68,38 Q50,22 32,38 L30,62 Z", st.c("dirt", 0.0), w=1)
    if st.mode == "hatch":
        hatch(S, [(50, 20), (84, 28), (90, 62), (70, 62), (68, 38)], 70, 2.6, 0.5)
    elif st.mode == "faces":
        poly(S, [(50, 20), (84, 28), (90, 62), (70, 62), (68, 38)], st.c("dirt", -0.25), outline=False, opacity=0.6)


@feature("glacier", "Glacier", "Desert, arctic and swamp", 10, h=60, family="ice")
def f_glacier(S, rnd):
    st = S.st
    shadow(S, 50, 52, 44, 5)
    faces(S, [(6, 52), (18, 24), (38, 14), (50, 52)], [(38, 14), (64, 20), (94, 52), (50, 52)], "ice")
    for x0, y0, x1, y1 in ((30, 26, 38, 46), (56, 24, 62, 44), (74, 34, 80, 48)):
        line(S, x0, y0, x1, y1, 0.7, opacity=0.5)


@feature("snow-mound", "Snow drift", "Desert, arctic and swamp", 6, h=40, family="hills")
def f_snow_mound(S, rnd):
    shadow(S, 50, 32, 36, 4)
    hill(S, 50, 32, 76, 16, "snow")


@feature("reeds", "Reeds", "Desert, arctic and swamp", 4, h=60, family="swamp")
def f_reeds(S, rnd):
    st = S.st
    shadow(S, 50, 52, 26, 4)
    for x, h, lean in ((30, 34, -6), (40, 44, -2), (50, 40, 3), (60, 46, 5), (70, 32, 8)):
        path(S, "M%.1f,52 Q%.1f,%.1f %.1f,%.1f" % (x, x + lean * 0.3, 52 - h * 0.6, x + lean, 52 - h), "none",
             extra='stroke="%s" stroke-width="2.6" stroke-linecap="round"' % st.c("grass", -0.1))
    for x, h, lean in ((40, 44, -2), (60, 46, 5)):
        ellipse(S, x + lean, 52 - h - 3, 2.2, 5, st.c("trunk"), w=0.6)


@feature("bog", "Bog", "Desert, arctic and swamp", 7, h=50, family="swamp")
def f_bog(S, rnd):
    st = S.st
    pts_ = blob_points(rnd, 50, 26, 42, 16, 10, 0.15)
    path(S, smooth_closed(pts_), st.c("leafdark", -0.1), w=1)
    for cx, cy in ((30, 24), (50, 30), (68, 24)):
        ellipse(S, cx, cy, 9, 3.4, st.c("water", -0.2), outline=False, opacity=0.8)
    for x in (24, 46, 72):
        line(S, x, 22, x - 1, 12, 1.0, color=st.c("grass", -0.1), opacity=1.0)


@feature("grass-tuft", "Grass", "Desert, arctic and swamp", 3, h=40, family="grass")
def f_tuft(S, rnd):
    st = S.st
    for x, h, lean in ((36, 22, -6), (44, 30, -2), (52, 34, 2), (60, 28, 6), (68, 20, 8)):
        path(S, "M%.1f,34 Q%.1f,%.1f %.1f,%.1f" % (x, x, 34 - h * 0.6, x + lean, 34 - h), "none",
             extra='stroke="%s" stroke-width="2.4" stroke-linecap="round"' % st.c("grass", -0.05))


# ---------------------------------------------------------------- landmarks

@feature("tower", "Tower", "Landmarks", 4, h=100, family="landmarks")
def f_tower(S, rnd):
    st = S.st
    shadow(S, 50, 90, 22, 4)
    faces(S, [(34, 90), (36, 34), (50, 34), (50, 90)], [(50, 34), (64, 34), (66, 90), (50, 90)], "stone")
    faces(S, [(30, 34), (50, 6), (50, 34)], [(50, 6), (70, 34), (50, 34)], "roof")
    path(S, "M44,90 L44,74 Q50,66 56,74 L56,90 Z", st.c("dark"), w=0.7)
    rect(S, 47, 48, 6, 10, st.c("dark"), lw=0.6)


@feature("ruins", "Ruins", "Landmarks", 6, h=70, family="landmarks")
def f_ruins(S, rnd):
    st = S.st
    shadow(S, 50, 62, 42, 5)
    faces(S, [(8, 62), (8, 28), (20, 24), (26, 62)], [(20, 24), (30, 30), (34, 62), (26, 62)], "stone")
    faces(S, [(42, 62), (44, 38), (60, 34), (62, 62)], [(60, 34), (72, 44), (74, 62), (62, 62)], "stone")
    faces(S, [(78, 62), (80, 48), (92, 46), (94, 62)], [(92, 46), (94, 62), (94, 62)], "stone")
    for cx, cy, r in ((36, 62, 3.5), (68, 64, 3), (28, 66, 2.6)):
        circle(S, cx, cy, r, st.c("stone", -0.1), w=0.6)


@feature("temple", "Temple", "Landmarks", 6, h=80, family="landmarks")
def f_temple(S, rnd):
    st = S.st
    shadow(S, 50, 72, 44, 5)
    rect(S, 10, 62, 80, 8, st.c("stone", 0.05), lw=0.8)
    rect(S, 16, 56, 68, 6, st.c("stone", 0.1), lw=0.8)
    for x in (22, 38, 54, 70):
        rect(S, x, 28, 8, 28, st.c("wall", 0.05), lw=0.8)
    poly(S, [(14, 28), (50, 8), (86, 28)], st.c("roof", 0.0), w=0.9)
    rect(S, 14, 26, 72, 4, st.c("stone", 0.1), lw=0.8)


@feature("obelisk", "Obelisk", "Landmarks", 3, h=100, family="landmarks")
def f_obelisk(S, rnd):
    shadow(S, 50, 90, 18, 4)
    rect(S, 38, 84, 24, 7, S.st.c("stone"), lw=0.8)
    faces(S, [(42, 84), (45, 22), (50, 8), (50, 84)], [(50, 8), (55, 22), (58, 84), (50, 84)], "stone")


@feature("standing-stones", "Standing stones", "Landmarks", 6, h=60, family="landmarks")
def f_stones(S, rnd):
    st = S.st
    shadow(S, 50, 48, 40, 6)
    for cx, cy, w, h in ((16, 40, 9, 22), (82, 40, 9, 22), (32, 32, 9, 26), (66, 32, 9, 26), (49, 28, 10, 28), (22, 48, 8, 18), (76, 48, 8, 18)):
        rect(S, cx - w / 2, cy - h, w, h, st.c("stone", -0.05), lw=0.8, r=2)


@feature("bridge", "Bridge", "Landmarks", 7, h=50, family="landmarks")
def f_bridge(S, rnd):
    st = S.st
    rect(S, 0, 30, 100, 16, st.c("water"), outline=False)
    wave_lines(S, 50, 34, 90, 2)
    path(S, "M10,34 L90,34 L90,24 Q50,0 10,24 Z", st.c("stone", 0.05), w=0.9)
    path(S, "M30,34 Q50,16 70,34 Z", st.c("water", -0.25), outline=False)


@feature("lighthouse", "Lighthouse", "Landmarks", 4, h=100, family="landmarks")
def f_lighthouse(S, rnd):
    st = S.st
    shadow(S, 50, 90, 26, 4)
    poly(S, [(36, 90), (42, 30), (58, 30), (64, 90)], st.c("snow", -0.05), w=0.9)
    poly(S, [(39, 66), (61, 66), (62.6, 78), (37.4, 78)], st.c("red"), w=0.6)
    poly(S, [(41, 44), (59, 44), (60.4, 56), (39.6, 56)], st.c("red"), w=0.6)
    rect(S, 40, 20, 20, 10, st.c("gold", 0.2), lw=0.8)
    poly(S, [(38, 20), (50, 8), (62, 20)], st.c("roof"), w=0.8)


@feature("mine", "Mine", "Landmarks", 6, h=70, family="landmarks")
def f_mine(S, rnd):
    st = S.st
    shadow(S, 50, 62, 40, 5)
    hill(S, 50, 62, 84, 32, "rock")
    rect(S, 38, 40, 4, 22, st.c("wood"), lw=0.7)
    rect(S, 58, 40, 4, 22, st.c("wood"), lw=0.7)
    rect(S, 36, 36, 28, 5, st.c("wood"), lw=0.7)
    rect(S, 42, 41, 16, 21, "#15110d", outline=False)
    poly(S, [(66, 62), (70, 54), (84, 54), (88, 62)], st.c("rock", -0.2), w=0.7)


@feature("dungeon-entrance", "Dungeon entrance", "Landmarks", 5, h=70, family="landmarks")
def f_dungeon(S, rnd):
    st = S.st
    shadow(S, 50, 62, 34, 5)
    faces(S, [(14, 62), (16, 24), (50, 18), (50, 62)], [(50, 18), (84, 24), (86, 62), (50, 62)], "stone")
    path(S, "M34,62 L34,38 Q50,22 66,38 L66,62 Z", "#15110d", w=0.8)
    for i in range(4):
        rect(S, 36 + i * 1.2, 62 - 4 - i * 4.4, 28 - i * 2.4, 3, st.c("stone", -0.2 + i * 0.05), lw=0.4)


@feature("portal", "Portal", "Landmarks", 5, h=80, family="landmarks")
def f_portal(S, rnd):
    st = S.st
    shadow(S, 50, 72, 30, 4)
    ellipse(S, 50, 40, 26, 32, st.c("blue", 0.1), w=0.9)
    ellipse(S, 50, 40, 18, 24, st.c("water", 0.3), outline=False, opacity=0.9)
    for ang in range(0, 360, 36):
        a = math.radians(ang)
        cx, cy = 50 + math.cos(a) * 26, 40 + math.sin(a) * 32
        rect(S, cx - 3, cy - 3, 6, 6, st.c("stone"), lw=0.6)


@feature("shipwreck", "Shipwreck", "Landmarks", 6, h=70, family="landmarks")
def f_wreck(S, rnd):
    st = S.st
    wave_lines(S, 50, 56, 90, 2)
    poly(S, [(14, 46), (80, 38), (72, 54), (24, 58)], st.c("wood", -0.1), w=0.9)
    line(S, 44, 44, 40, 10, 1.6, color=st.c("wood", -0.25), opacity=1.0)
    poly(S, [(40, 12), (56, 18), (42, 28)], st.c("bone", -0.05), w=0.6)
    line(S, 60, 40, 74, 22, 1.4, color=st.c("wood", -0.25), opacity=1.0)


@feature("battlefield", "Battlefield", "Landmarks", 6, h=70, family="landmarks")
def f_battlefield(S, rnd):
    st = S.st
    line(S, 22, 56, 56, 12, 2.4, color=st.c("stone", 0.2), opacity=1.0)
    line(S, 78, 56, 44, 12, 2.4, color=st.c("stone", 0.2), opacity=1.0)
    line(S, 18, 52, 28, 44, 3.2, color=st.c("wood", -0.2), opacity=1.0)
    line(S, 82, 52, 72, 44, 3.2, color=st.c("wood", -0.2), opacity=1.0)
    line(S, 20, 62, 20, 30, 1.4, color=st.c("wood", -0.25), opacity=1.0)
    poly(S, [(20, 30), (36, 36), (20, 44)], st.c("cloth"), w=0.7)
    line(S, 80, 62, 80, 30, 1.4, color=st.c("wood", -0.25), opacity=1.0)
    poly(S, [(80, 30), (64, 36), (80, 44)], st.c("blue"), w=0.7)


@feature("graveyard", "Graveyard", "Landmarks", 5, h=60, family="landmarks")
def f_graveyard(S, rnd):
    st = S.st
    shadow(S, 50, 52, 38, 5)
    for cx, h in ((20, 22), (40, 28), (60, 24), (80, 20)):
        path(S, "M%.1f,50 L%.1f,%.1f Q%.1f,%.1f %.1f,%.1f L%.1f,50 Z" % (cx - 7, cx - 7, 50 - h, cx, 50 - h - 8, cx + 7, 50 - h, cx + 7), st.c("stone", -0.05), w=0.8)
    rect(S, 48, 14, 4, 22, st.c("bone"), lw=0.6)
    rect(S, 42, 20, 16, 4, st.c("bone"), lw=0.6)


@feature("camp", "Camp", "Landmarks", 5, h=60, family="landmarks")
def f_camp(S, rnd):
    st = S.st
    shadow(S, 50, 52, 36, 4)
    faces(S, [(10, 52), (34, 14), (34, 52)], [(34, 14), (58, 52), (34, 52)], "cloth")
    path(S, "M28,52 L34,34 L40,52 Z", "#15110d", outline=False, opacity=0.7)
    poly(S, [(70, 52), (78, 52), (76, 44), (72, 44)], st.c("wood"), w=0.6)
    poly(S, [(66, 46), (74, 28), (82, 46), (74, 40)], st.c("lava", 0.05), w=0.6)
    poly(S, [(70, 44), (74, 34), (78, 44)], st.c("gold", 0.2), outline=False)


@feature("statue", "Statue", "Landmarks", 3, h=100, family="landmarks")
def f_statue(S, rnd):
    st = S.st
    shadow(S, 50, 90, 22, 4)
    rect(S, 34, 76, 32, 14, st.c("stone", 0.05), lw=0.8)
    rect(S, 40, 64, 20, 12, st.c("stone", 0.0), lw=0.8)
    poly(S, [(40, 64), (42, 38), (58, 38), (60, 64)], st.c("stone", 0.12), w=0.9)
    circle(S, 50, 30, 8, st.c("stone", 0.12), w=0.9)
    line(S, 42, 42, 32, 28, 2.6, color=st.c("stone", 0.0), opacity=1.0)


@feature("ancient-tree", "Ancient tree", "Landmarks", 6, h=100, family="landmarks")
def f_ancient(S, rnd):
    st = S.st
    shadow(S, 50, 90, 38, 6)
    path(S, "M36,90 Q42,70 42,50 L58,50 Q58,70 64,90 Z", st.c("trunk", 0.0), w=1)
    ball(S, 26, 40, 20, "leafdark")
    ball(S, 74, 40, 20, "leafdark")
    ball(S, 50, 28, 28, "leaf", 0.14, -0.2)
    ball(S, 50, 50, 16, "leaf", 0.1, -0.2)
