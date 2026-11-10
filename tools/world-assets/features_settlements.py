"""Settlements, from a single cottage to a capital, plus elven, dwarven and orcish places."""
import math

from worldlib import *
from features_land import faces, ball, hill, tree_oak, tree_pine, trunk

CAT = "Settlements"


# ---------------------------------------------------------------- building blocks

def house(S, cx, base, w, h, roof="roof", wall="wall"):
    st = S.st
    faces(S, [(cx - w / 2, base), (cx - w / 2, base - h), (cx, base - h), (cx, base)],
          [(cx, base - h), (cx + w / 2, base - h), (cx + w / 2, base), (cx, base)], wall)
    rh = w * 0.55
    faces(S, [(cx - w / 2 - 1, base - h), (cx, base - h - rh), (cx, base - h)],
          [(cx, base - h - rh), (cx + w / 2 + 1, base - h), (cx, base - h)], roof)
    rect(S, cx - w * 0.09, base - h * 0.5, w * 0.18, h * 0.5, st.c("dark"), lw=0.4)


def round_tower(S, cx, base, w, h, roof="roof"):
    st = S.st
    faces(S, [(cx - w / 2, base), (cx - w / 2, base - h), (cx, base - h), (cx, base)],
          [(cx, base - h), (cx + w / 2, base - h), (cx + w / 2, base), (cx, base)], "stone")
    faces(S, [(cx - w / 2 - 1, base - h), (cx, base - h - w * 0.9), (cx, base - h)],
          [(cx, base - h - w * 0.9), (cx + w / 2 + 1, base - h), (cx, base - h)], roof)


def flag(S, x, y, role="cloth"):
    line(S, x, y, x, y - 9, 0.9, color=S.st.c("wood", -0.2), opacity=1.0)
    poly(S, [(x, y - 9), (x + 6, y - 7), (x, y - 5)], S.st.c(role), w=0.5)


def crenel(S, x0, x1, y, h=2.2, n=None):
    """A row of battlements along the top of a wall."""
    n = n or max(2, int((x1 - x0) / 4))
    step = (x1 - x0) / n
    for i in range(0, n, 1):
        if i % 2 == 0:
            rect(S, x0 + i * step, y - h, step, h, S.st.c("stone", 0.05), lw=0.4)


def wall_front(S, x0, x1, base, h=9):
    st = S.st
    rect(S, x0, base - h, x1 - x0, h, st.c("stone", 0.05 if st.mode != "faces" else -0.05), lw=0.7)
    crenel(S, x0, x1, base - h)
    if st.mode == "hatch":
        hatch(S, [(x0, base - h), (x1, base - h), (x1, base), (x0, base)], 80, 3, 0.45)


def gate(S, cx, base, w=9, h=11):
    S.add('<path d="M%.1f,%.1f L%.1f,%.1f Q%.1f,%.1f %.1f,%.1f L%.1f,%.1f Z" fill="%s" %s/>' % (
        cx - w / 2, base, cx - w / 2, base - h * 0.6, cx, base - h * 1.25, cx + w / 2, base - h * 0.6, cx + w / 2, base, S.st.c("dark"), S.st.line(0.6)))


def keep(S, cx, base, w, h):
    st = S.st
    faces(S, [(cx - w / 2, base), (cx - w / 2, base - h), (cx, base - h), (cx, base)],
          [(cx, base - h), (cx + w / 2, base - h), (cx + w / 2, base), (cx, base)], "stone")
    crenel(S, cx - w / 2, cx + w / 2, base - h, 3, 6)
    for dx in (-w / 2 + 1, w / 2 - 5):
        rect(S, cx + dx, base - h - 8, 4.5, 8, st.c("stone", 0.0), lw=0.5)
    flag(S, cx, base - h - 8)
    gate(S, cx, base, w * 0.3, h * 0.45)


def spire(S, cx, base, w, h):
    """A church with a steeple."""
    st = S.st
    house(S, cx - w * 0.25, base, w * 0.6, h * 0.5, "roof", "wall")
    faces(S, [(cx + w * 0.1, base), (cx + w * 0.1, base - h * 0.7), (cx + w * 0.3, base - h * 0.7), (cx + w * 0.3, base)],
          [(cx + w * 0.3, base - h * 0.7), (cx + w * 0.5, base - h * 0.7), (cx + w * 0.5, base), (cx + w * 0.3, base)], "wall")
    faces(S, [(cx + w * 0.08, base - h * 0.7), (cx + w * 0.3, base - h * 1.15), (cx + w * 0.3, base - h * 0.7)],
          [(cx + w * 0.3, base - h * 1.15), (cx + w * 0.52, base - h * 0.7), (cx + w * 0.3, base - h * 0.7)], "roof")


def cluster(S, rnd, spots, scale=1.0, roof_roles=("roof", "wood")):
    """Houses at (x, base, w, h); drawn back to front."""
    for i, (x, b, w, h) in enumerate(sorted(spots, key=lambda s: s[1])):
        house(S, x, b, w * scale, h * scale, roof_roles[i % len(roof_roles)])


# ---------------------------------------------------------------- the settlements

@feature("settlement-cottage", "Cottage", CAT, 4, h=80, family="settlements")
def f_cottage(S, rnd):
    shadow(S, 50, 74, 28, 5)
    house(S, 50, 74, 34, 28)
    tree_oak(S, 78, 78, 9)


@feature("settlement-hamlet", "Hamlet", CAT, 6, h=80, family="settlements")
def f_hamlet(S, rnd):
    shadow(S, 50, 74, 42, 6)
    cluster(S, rnd, [(28, 62, 24, 20), (66, 60, 24, 20), (48, 76, 26, 22)])
    tree_oak(S, 86, 74, 8)
    tree_oak(S, 12, 74, 7)


@feature("settlement-village", "Village", CAT, 8, h=80, family="settlements")
def f_village(S, rnd):
    shadow(S, 50, 76, 46, 6)
    cluster(S, rnd, [(18, 58, 20, 16), (44, 54, 20, 16), (72, 58, 20, 16), (30, 74, 22, 18), (62, 76, 22, 18)])
    spire(S, 84, 66, 24, 36)


@feature("settlement-town", "Town", CAT, 10, h=85, family="settlements")
def f_town(S, rnd):
    shadow(S, 50, 80, 48, 6)
    cluster(S, rnd, [(16, 52, 18, 15), (38, 48, 18, 15), (60, 50, 18, 15), (82, 54, 18, 15),
                     (22, 68, 20, 17), (46, 66, 20, 17), (70, 68, 20, 17), (34, 82, 22, 18), (62, 82, 22, 18)])
    spire(S, 50, 46, 28, 42)


@feature("settlement-city-small", "Walled town", CAT, 12, h=90, family="settlements")
def f_city_small(S, rnd):
    shadow(S, 50, 84, 48, 6)
    cluster(S, rnd, [(22, 46, 16, 14), (44, 42, 16, 14), (66, 44, 16, 14), (82, 50, 14, 12), (30, 60, 18, 15), (56, 58, 18, 15)])
    spire(S, 50, 46, 26, 40)
    wall_front(S, 6, 94, 86, 11)
    gate(S, 50, 86)
    round_tower(S, 8, 86, 11, 22)
    round_tower(S, 92, 86, 11, 22)


@feature("settlement-city-large", "City", CAT, 15, h=100, family="settlements")
def f_city_large(S, rnd):
    shadow(S, 50, 90, 50, 6)
    hill(S, 70, 52, 50, 22, "rock")
    keep(S, 70, 44, 22, 18)
    cluster(S, rnd, [(14, 52, 15, 13), (34, 48, 15, 13), (50, 58, 16, 14), (20, 66, 17, 15), (40, 72, 17, 15), (62, 70, 17, 15), (82, 66, 15, 13)])
    spire(S, 30, 58, 30, 46)
    wall_front(S, 4, 96, 94, 12)
    gate(S, 50, 94, 10, 12)
    round_tower(S, 6, 94, 12, 26)
    round_tower(S, 94, 94, 12, 26)
    round_tower(S, 30, 94, 10, 20)
    round_tower(S, 70, 94, 10, 20)


@feature("settlement-capital", "Capital", CAT, 18, h=105, family="settlements")
def f_capital(S, rnd):
    shadow(S, 50, 96, 52, 7)
    hill(S, 50, 52, 70, 28, "rock")
    keep(S, 50, 40, 30, 22)
    round_tower(S, 30, 42, 11, 24)
    round_tower(S, 70, 42, 11, 24)
    cluster(S, rnd, [(12, 58, 15, 13), (28, 64, 16, 14), (72, 64, 16, 14), (88, 58, 15, 13), (16, 76, 17, 15), (40, 78, 17, 15), (62, 78, 17, 15), (84, 76, 17, 15)])
    spire(S, 50, 70, 32, 48)
    wall_front(S, 2, 98, 100, 13)
    gate(S, 50, 100, 11, 13)
    for x in (6, 94, 32, 68):
        round_tower(S, x, 100, 11, 27)
    flag(S, 50, 8, "cloth")


@feature("settlement-port", "Port", CAT, 11, h=85, family="settlements")
def f_port(S, rnd):
    st = S.st
    rect(S, 0, 62, 100, 26, st.c("water"), outline=False)
    shadow(S, 40, 58, 38, 5)
    cluster(S, rnd, [(18, 46, 18, 15), (40, 42, 18, 15), (62, 46, 18, 15), (28, 56, 20, 16), (52, 58, 20, 16)])
    poly(S, [(60, 62), (96, 62), (96, 68), (60, 68)], st.c("wood"), w=0.7)
    for x in (66, 78, 90):
        line(S, x, 68, x, 74, 1.0, color=st.c("wood", -0.2), opacity=1.0)
    path(S, "M70,50 L88,50 L84,60 L74,60 Z", st.c("wood", -0.1), w=0.8)
    line(S, 79, 50, 79, 32, 1.2, color=st.c("wood", -0.25), opacity=1.0)
    poly(S, [(79, 33), (92, 46), (79, 46)], st.c("snow", -0.05), w=0.6)


@feature("settlement-fortress", "Fortress", CAT, 8, h=90, family="settlements")
def f_fortress(S, rnd):
    shadow(S, 50, 80, 44, 6)
    wall_front(S, 10, 90, 80, 22)
    keep(S, 50, 56, 30, 24)
    for x in (10, 90):
        round_tower(S, x, 80, 14, 34)
    gate(S, 50, 80, 12, 16)


@feature("settlement-castle", "Castle", CAT, 8, h=90, family="settlements")
def f_castle(S, rnd):
    shadow(S, 50, 80, 44, 6)
    hill(S, 50, 80, 92, 34, "grass")
    keep(S, 50, 62, 26, 24)
    round_tower(S, 26, 66, 12, 26)
    round_tower(S, 74, 66, 12, 26)
    flag(S, 26, 38)
    flag(S, 74, 38)


@feature("settlement-monastery", "Monastery", CAT, 6, h=85, family="settlements")
def f_monastery(S, rnd):
    shadow(S, 50, 78, 40, 5)
    hill(S, 50, 78, 80, 24, "grass")
    wall_front(S, 14, 86, 76, 8)
    house(S, 28, 66, 22, 18, "roof", "wall")
    house(S, 72, 66, 22, 18, "roof", "wall")
    spire(S, 50, 70, 34, 54)


@feature("settlement-ruined-city", "Ruined city", CAT, 9, h=80, family="settlements")
def f_ruined_city(S, rnd):
    st = S.st
    shadow(S, 50, 70, 46, 6)
    faces(S, [(6, 70), (6, 40), (16, 34), (22, 70)], [(16, 34), (28, 42), (30, 70), (22, 70)], "stone")
    faces(S, [(36, 70), (38, 48), (52, 44), (56, 70)], [(52, 44), (64, 52), (66, 70), (56, 70)], "stone")
    faces(S, [(72, 70), (74, 36), (84, 30), (94, 38), (94, 70)], [(84, 30), (94, 38), (94, 70)], "stone")
    for cx, cy, r in ((30, 72, 3.5), (62, 74, 3), (50, 72, 2.5), (14, 74, 3)):
        circle(S, cx, cy, r, st.c("stone", -0.1), w=0.6)
    tree_oak(S, 46, 66, 7, "leafdark")


@feature("settlement-elven", "Elven hold", CAT, 8, h=90, family="settlements")
def f_elven(S, rnd):
    st = S.st
    shadow(S, 50, 82, 44, 6)
    for cx, base, r in ((24, 80, 22), (74, 80, 22), (50, 72, 26)):
        trunk(S, cx, base, 8, 30)
        ball(S, cx, base - 38, r, "leaf", 0.16, -0.18)
        path(S, "M%.1f,%.1f q4,-8 8,0 Z" % (cx - 4, base - 30), st.c("wall", 0.1), w=0.5)
        rect(S, cx - 4, base - 30, 8, 6, st.c("wall", 0.15), lw=0.5)
    for cx, cy in ((36, 46), (62, 40), (50, 30)):
        circle(S, cx, cy, 1.8, st.c("gold", 0.25), outline=False)


@feature("settlement-dwarven", "Dwarven hold", CAT, 8, h=90, family="settlements")
def f_dwarven(S, rnd):
    st = S.st
    shadow(S, 50, 82, 46, 6)
    faces(S, [(4, 82), (14, 30), (40, 12), (50, 82)], [(40, 12), (70, 22), (96, 82), (50, 82)], "rock")
    path(S, "M38,82 L38,56 Q50,40 62,56 L62,82 Z", st.c("dark"), w=1)
    rect(S, 46, 70, 8, 12, st.c("gold", -0.1), lw=0.5)
    for cx, cy, r in ((70, 20, 4), (74, 12, 5), (70, 3, 4.5)):
        circle(S, cx, cy, r, st.c("smoke", 0.2), w=0.6, opacity=0.9)
    rect(S, 66, 26, 8, 14, st.c("stone", -0.1), lw=0.6)


@feature("settlement-orc", "Orc stronghold", CAT, 7, h=85, family="settlements")
def f_orc(S, rnd):
    st = S.st
    shadow(S, 50, 76, 44, 6)
    for x in range(8, 95, 7):
        poly(S, [(x - 3, 76), (x - 3, 56), (x, 50), (x + 3, 56), (x + 3, 76)], st.c("wood", -0.12), w=0.5)
    faces(S, [(26, 62), (40, 38), (40, 62)], [(40, 38), (54, 62), (40, 62)], "cloth")
    faces(S, [(52, 66), (66, 44), (66, 66)], [(66, 44), (80, 66), (66, 66)], "ash")
    line(S, 50, 76, 50, 36, 1.8, color=st.c("wood", -0.2), opacity=1.0)
    circle(S, 50, 33, 4, st.c("bone"), w=0.6)
    circle(S, 48.6, 32.5, 0.9, st.c("dark"), outline=False)
    circle(S, 51.4, 32.5, 0.9, st.c("dark"), outline=False)


@feature("settlement-watchtower", "Watchtower", CAT, 4, h=90, family="settlements")
def f_watchtower(S, rnd):
    shadow(S, 50, 82, 30, 5)
    hill(S, 50, 82, 64, 18, "rock")
    round_tower(S, 50, 70, 18, 36)
    flag(S, 50, 28)
