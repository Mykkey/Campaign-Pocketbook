"""Small helper library for generating the battlemap SVG pieces.

Every piece is drawn top-down on a canvas of (cells wide x cells high) * 100 units. Shared tricks:
  * soft drop shadows (feDropShadow) so pieces sit on the map,
  * subtle surface grain (feTurbulence) so flat colours don't look plastic,
  * tiled patterns (shingles, stone blocks, planks, cobbles) built from small repeatable tiles,
  * gradients for lighting: light comes from the top-left.
"""
import math
import random

OUT = "#2b211c"        # outline colour used everywhere

REGISTRY = []          # filled by the @asset decorator in the category modules


def asset(asset_id, name, category, wc, hc, seed=1):
    """Registers a drawing function fn(S) as a piece of (wc x hc) cells."""
    def deco(fn):
        REGISTRY.append((asset_id, name, category, wc, hc, seed, fn))
        return fn
    return deco


def n(v):
    """Compact number formatting."""
    s = f"{v:.2f}".rstrip("0").rstrip(".")
    return s if s not in ("-0", "") else "0"


def mix(c1, c2, t):
    a = [int(c1[i:i + 2], 16) for i in (1, 3, 5)]
    b = [int(c2[i:i + 2], 16) for i in (1, 3, 5)]
    return "#" + "".join(f"{round(a[i] + (b[i] - a[i]) * t):02x}" for i in range(3))


def shade(c, f):
    """f < 1 darkens, f > 1 lightens."""
    if f <= 1:
        return mix(c, "#000000", 1 - f)
    return mix(c, "#ffffff", f - 1)


def jitter(c, rnd, amt=0.12):
    return shade(c, 1 + (rnd.random() - 0.5) * 2 * amt)


# --------------------------------------------------------------------------- primitives

def _paint(fill, stroke, sw, extra=""):
    s = f' fill="{fill}"' if fill else ' fill="none"'
    if stroke:
        s += f' stroke="{stroke}" stroke-width="{n(sw)}"'
    else:
        s += ' stroke="none"'
    return s + (" " + extra if extra else "")


def rect(x, y, w, h, fill=None, stroke=OUT, sw=2.5, r=0, extra=""):
    rr = f' rx="{n(r)}"' if r else ""
    return f'<rect x="{n(x)}" y="{n(y)}" width="{n(w)}" height="{n(h)}"{rr}{_paint(fill, stroke, sw, extra)}/>'


def circ(cx, cy, r, fill=None, stroke=OUT, sw=2.5, extra=""):
    return f'<circle cx="{n(cx)}" cy="{n(cy)}" r="{n(r)}"{_paint(fill, stroke, sw, extra)}/>'


def ell(cx, cy, rx, ry, fill=None, stroke=OUT, sw=2.5, extra=""):
    return f'<ellipse cx="{n(cx)}" cy="{n(cy)}" rx="{n(rx)}" ry="{n(ry)}"{_paint(fill, stroke, sw, extra)}/>'


def path(d, fill=None, stroke=OUT, sw=2.5, extra=""):
    return f'<path d="{d}"{_paint(fill, stroke, sw, extra)}/>'


def poly(pts, fill=None, stroke=OUT, sw=2.5, extra=""):
    p = " ".join(f"{n(x)},{n(y)}" for x, y in pts)
    return f'<polygon points="{p}"{_paint(fill, stroke, sw, extra)}/>'


def line(x1, y1, x2, y2, stroke=OUT, sw=2, extra=""):
    return (f'<line x1="{n(x1)}" y1="{n(y1)}" x2="{n(x2)}" y2="{n(y2)}" stroke="{stroke}" '
            f'stroke-width="{n(sw)}" stroke-linecap="round"{" " + extra if extra else ""}/>')


def g(content, transform=None, extra=""):
    t = f' transform="{transform}"' if transform else ""
    return f'<g{t}{" " + extra if extra else ""}>{content}</g>'


def rot(content, deg, cx, cy):
    return g(content, f"rotate({n(deg)} {n(cx)} {n(cy)})")


def opacity(content, o):
    return g(content, extra=f'opacity="{n(o)}"')


def clip_to(S, clip_shape, content):
    cid = S.uid("c")
    S.defs.append(f'<clipPath id="{cid}">{clip_shape}</clipPath>')
    return g(content, extra=f'clip-path="url(#{cid})"')


def regular(cx, cy, r, sides, rotation=-90):
    return [(cx + r * math.cos(math.radians(rotation + 360 * i / sides)),
             cy + r * math.sin(math.radians(rotation + 360 * i / sides))) for i in range(sides)]


def blob(rnd, cx, cy, rx, ry, points=10, wobble=0.18):
    """Irregular closed outline, as a path (rocks, bushes, ponds)."""
    pts = []
    for i in range(points):
        a = 2 * math.pi * i / points
        k = 1 + (rnd.random() - 0.5) * 2 * wobble
        pts.append((cx + math.cos(a) * rx * k, cy + math.sin(a) * ry * k))
    # smooth closed curve through the points (Catmull-Rom -> Bezier)
    d = f"M{n(pts[0][0])} {n(pts[0][1])}"
    m = len(pts)
    for i in range(m):
        p0, p1, p2, p3 = pts[(i - 1) % m], pts[i], pts[(i + 1) % m], pts[(i + 2) % m]
        c1 = (p1[0] + (p2[0] - p0[0]) / 6, p1[1] + (p2[1] - p0[1]) / 6)
        c2 = (p2[0] - (p3[0] - p1[0]) / 6, p2[1] - (p3[1] - p1[1]) / 6)
        d += f" C{n(c1[0])} {n(c1[1])} {n(c2[0])} {n(c2[1])} {n(p2[0])} {n(p2[1])}"
    return d + "Z"


def rockpoly(rnd, cx, cy, rx, ry, points=8, wobble=0.22):
    pts = []
    for i in range(points):
        a = 2 * math.pi * i / points + rnd.random() * 0.3
        k = 1 + (rnd.random() - 0.5) * 2 * wobble
        pts.append((cx + math.cos(a) * rx * k, cy + math.sin(a) * ry * k))
    return pts


# --------------------------------------------------------------------------- the canvas

class Tile:
    def __init__(self, inner, w, h):
        self.inner, self.w, self.h = inner, w, h


class Svg:
    def __init__(self, wc, hc, seed=1):
        self.wc, self.hc = wc, hc
        self.w, self.h = int(round(wc * 100)), int(round(hc * 100))
        self.defs = []
        self.parts = []
        self.rnd = random.Random(seed)
        self._id = 0
        self._shadow = {}
        self._grain = {}

    def uid(self, p="g"):
        self._id += 1
        return f"{p}{self._id}"

    def add(self, *items):
        for it in items:
            self.parts.append(it)
        return self

    # ---- paints
    def lin(self, stops, x1=0, y1=0, x2=0, y2=1):
        """Gradient fill. stops = [(offset, colour[, opacity]), ...]; default direction top -> bottom."""
        gid = self.uid("l")
        st = "".join(f'<stop offset="{n(s[0])}" stop-color="{s[1]}"' + (f' stop-opacity="{n(s[2])}"' if len(s) > 2 else "") + "/>"
                     for s in stops)
        self.defs.append(f'<linearGradient id="{gid}" x1="{n(x1)}" y1="{n(y1)}" x2="{n(x2)}" y2="{n(y2)}">{st}</linearGradient>')
        return f"url(#{gid})"

    def rad(self, stops, cx=0.5, cy=0.5, r=0.5, fx=None, fy=None):
        gid = self.uid("r")
        st = "".join(f'<stop offset="{n(s[0])}" stop-color="{s[1]}"' + (f' stop-opacity="{n(s[2])}"' if len(s) > 2 else "") + "/>"
                     for s in stops)
        f = f' fx="{n(fx)}" fy="{n(fy)}"' if fx is not None else ""
        self.defs.append(f'<radialGradient id="{gid}" cx="{n(cx)}" cy="{n(cy)}" r="{n(r)}"{f}>{st}</radialGradient>')
        return f"url(#{gid})"

    def pattern(self, w, h, inner):
        """A repeatable tile. Returned as a Tile; place it with S.tiles(...) (vector tiles, so no seams)."""
        return Tile(inner, w, h)

    def tiles(self, tile, x, y, w, h, base=None, extra_clip=None):
        """Fills the rectangle (x, y, w, h) with the tile repeated, clipped to the rectangle."""
        tid = self.uid("t")
        self.defs.append(f'<g id="{tid}">{tile.inner}</g>')
        out = rect(x, y, w, h, fill=base, stroke=None) if base else ""
        nx = int(math.ceil(w / tile.w)) + 1
        ny = int(math.ceil(h / tile.h)) + 1
        for iy in range(ny):
            for ix in range(nx):
                out += f'<use href="#{tid}" x="{n(x + ix * tile.w)}" y="{n(y + iy * tile.h)}"/>'
        cid = self.uid("c")
        clip = extra_clip if extra_clip else rect(x, y, w, h, fill="#000", stroke=None)
        self.defs.append(f'<clipPath id="{cid}">{clip}</clipPath>')
        return g(out, extra=f'clip-path="url(#{cid})"')

    # ---- effects
    def shadow_filter(self, dx=4, dy=5, blur=4, op=0.42):
        key = (dx, dy, blur, op)
        if key not in self._shadow:
            fid = self.uid("s")
            self.defs.append(
                f'<filter id="{fid}" x="-35%" y="-35%" width="170%" height="170%">'
                f'<feDropShadow dx="{n(dx)}" dy="{n(dy)}" stdDeviation="{n(blur)}" flood-color="#000" flood-opacity="{n(op)}"/></filter>')
            self._shadow[key] = fid
        return self._shadow[key]

    def shadowed(self, content, dx=4, dy=5, blur=4, op=0.42):
        """Wraps content so it casts a soft drop shadow onto whatever is below."""
        return g(content, extra=f'filter="url(#{self.shadow_filter(dx, dy, blur, op)})"')

    def grain_filter(self, freq=0.85, strength=0.38, seed=3, light=False):
        key = (freq, strength, seed, light)
        if key not in self._grain:
            fid = self.uid("n")
            col = "1 1 1" if light else "0 0 0"
            r, gg, b = col.split()
            self.defs.append(
                f'<filter id="{fid}" x="0" y="0" width="100%" height="100%">'
                f'<feTurbulence type="fractalNoise" baseFrequency="{n(freq)}" numOctaves="2" seed="{seed}" result="t"/>'
                f'<feColorMatrix in="t" type="matrix" values="0 0 0 0 {r}  0 0 0 0 {gg}  0 0 0 0 {b}  0 0 0 {n(strength * 2.6)} {n(-strength * 1.1)}"/>'
                f'<feComposite in2="SourceGraphic" operator="in"/></filter>')
            self._grain[key] = fid
        return self._grain[key]

    def grained(self, shape_markup, strength=0.38, freq=0.85, seed=3, light=False):
        """Overlay: speckle texture clipped to the shape (pass the same shape you filled)."""
        fid = self.grain_filter(freq, strength, seed, light)
        return g(shape_markup, extra=f'filter="url(#{fid})"')

    def build(self):
        defs = f"<defs>{''.join(self.defs)}</defs>" if self.defs else ""
        return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {self.w} {self.h}" width="{self.w}" height="{self.h}" '
                f'stroke-linejoin="round" stroke-linecap="round">\n{defs}\n' + "\n".join(self.parts) + "\n</svg>\n")


# --------------------------------------------------------------------------- reusable surface patterns

def shingle_pattern(S, base, vertical=False, size=15):
    """Overlapping roof tiles. Rows run parallel to the ridge: horizontal rows by default."""
    r = S.rnd
    tw = size * 1.2
    rows = 2
    inner = ""
    for row in range(rows):
        y = row * size
        # a row of tiles, offset on alternate rows
        off = 0 if row % 2 == 0 else tw / 2
        for k in range(-1, 3):
            x = off + k * tw
            col = jitter(base, r, 0.08)
            inner += (f'<path d="M{n(x)} {n(y)}H{n(x + tw)}V{n(y + size * 0.6)}Q{n(x + tw)} {n(y + size)} {n(x + tw / 2)} {n(y + size)}'
                      f'Q{n(x)} {n(y + size)} {n(x)} {n(y + size * 0.6)}Z" fill="{col}" stroke="{shade(base, 0.62)}" stroke-width="1.1"/>')
    w, h = tw, size * rows
    if vertical:
        # rotate the tile 90 degrees: swap axes by wrapping in a transform and swapping pattern size
        inner = f'<g transform="translate({n(h)} 0) rotate(90)">{inner}</g>'
        w, h = h, w
    return S.pattern(w, h, inner)


def stone_pattern(S, base, mortar="#4a443c", scale=1.0):
    """Irregular ashlar blocks (two staggered rows)."""
    r = S.rnd
    rowsdef = [
        (0, 22, [34, 26, 40]),
        (22, 22, [18, 38, 26, 18]),
    ]
    inner = f'<rect width="{n(100 * scale)}" height="{n(44 * scale)}" fill="{mortar}"/>'
    for y, h, widths in rowsdef:
        x = 0
        for wdt in widths:
            col = jitter(base, r, 0.1)
            inner += (f'<rect x="{n((x + 1) * scale)}" y="{n((y + 1) * scale)}" width="{n((wdt - 2) * scale)}" height="{n((h - 2) * scale)}" '
                      f'rx="{n(2.5 * scale)}" fill="{col}"/>'
                      f'<path d="M{n((x + 2) * scale)} {n((y + 2.5) * scale)}H{n((x + wdt - 3) * scale)}" stroke="{shade(col, 1.25)}" stroke-width="{n(1.2 * scale)}" fill="none" opacity="0.7"/>')
            x += wdt
    return S.pattern(100 * scale, 44 * scale, inner)


def plank_pattern(S, base, vertical=False, plank=14, gap="#3d2a18"):
    """Wooden planks with grain streaks."""
    r = S.rnd
    inner = ""
    count = 4
    for i in range(count):
        y = i * plank
        col = jitter(base, r, 0.1)
        inner += f'<rect x="0" y="{n(y)}" width="100" height="{n(plank)}" fill="{col}"/>'
        for _ in range(3):
            gy = y + 2.5 + r.random() * (plank - 5)
            gx = r.random() * 60
            inner += (f'<path d="M{n(gx)} {n(gy)}q{n(14)} {n((r.random() - 0.5) * 2)} {n(30 + r.random() * 20)} 0" '
                      f'stroke="{shade(col, 0.78)}" stroke-width="0.9" fill="none" opacity="0.8"/>')
        # staggered butt joint
        jx = (i * 37 + 20) % 90 + 5
        inner += f'<path d="M{n(jx)} {n(y)}V{n(y + plank)}" stroke="{gap}" stroke-width="1.5"/>'
        inner += f'<path d="M0 {n(y + plank)}H100" stroke="{gap}" stroke-width="1.6"/>'
    w, h = 100, plank * count
    if vertical:
        inner = f'<g transform="translate({n(h)} 0) rotate(90)">{inner}</g>'
        w, h = h, w
    return S.pattern(w, h, inner)


def cobble_pattern(S, base, grout="#4d4943", scale=1.0):
    """Rounded cobblestones in an irregular layout. scale < 1 gives smaller stones."""
    tile = _cobble_tile(S, base, grout)
    if scale == 1.0:
        return tile
    return Tile(f'<g transform="scale({n(scale)})">{tile.inner}</g>', tile.w * scale, tile.h * scale)


def _cobble_tile(S, base, grout):
    r = S.rnd
    size = 60
    inner = f'<rect width="{size}" height="{size}" fill="{grout}"/>'
    stones = [(8, 8, 9, 7), (24, 7, 8, 6), (41, 9, 9, 7), (55, 6, 6, 6),
              (6, 24, 7, 8), (20, 25, 9, 8), (37, 26, 8, 7), (52, 25, 8, 8),
              (10, 42, 9, 8), (27, 43, 8, 8), (43, 42, 9, 8), (57, 44, 6, 7),
              (8, 56, 8, 6), (25, 57, 9, 5), (42, 57, 8, 5), (56, 56, 7, 6)]
    for cx, cy, rx, ry in stones:
        col = jitter(base, r, 0.1)
        inner += (f'<ellipse cx="{n(cx)}" cy="{n(cy)}" rx="{n(rx)}" ry="{n(ry)}" fill="{col}"/>'
                  f'<ellipse cx="{n(cx - rx * 0.25)}" cy="{n(cy - ry * 0.3)}" rx="{n(rx * 0.5)}" ry="{n(ry * 0.4)}" fill="{shade(col, 1.2)}" opacity="0.6"/>')
    return S.pattern(size, size, inner)


def brick_pattern(S, base, mortar="#6b5a4c"):
    r = S.rnd
    inner = f'<rect width="40" height="20" fill="{mortar}"/>'
    for row in range(2):
        for k in range(-1, 3):
            x = k * 20 + (10 if row else 0)
            inner += f'<rect x="{n(x + 1)}" y="{n(row * 10 + 1)}" width="18" height="8" fill="{jitter(base, r, 0.09)}"/>'
    return S.pattern(40, 20, inner)


def thatch_pattern(S, base="#c9a34e"):
    r = S.rnd
    inner = f'<rect width="40" height="40" fill="{base}"/>'
    for _ in range(46):
        x, y = r.random() * 40, r.random() * 40
        L = 8 + r.random() * 12
        inner += (f'<path d="M{n(x)} {n(y)}l{n((r.random() - 0.5) * 3)} {n(L)}" stroke="{jitter(base, r, 0.22)}" '
                  f'stroke-width="{n(1 + r.random() * 1.3)}" fill="none"/>')
    return S.pattern(40, 40, inner)


# --------------------------------------------------------------------------- composite parts

def textured(S, tile, x, y, w, h, r=0, stroke=OUT, sw=2.6, base=None):
    """A (rounded) rectangle filled with a tiled texture, plus its outline."""
    clip = rect(x, y, w, h, fill="#000", stroke=None, r=r)
    return S.tiles(tile, x, y, w, h, base=base, extra_clip=clip) + rect(x, y, w, h, fill=None, stroke=stroke, sw=sw, r=r)


def textured_shape(S, tile, bbox, shape_fn, stroke=OUT, sw=2.6, base=None):
    """Tiled texture clipped to any shape. shape_fn(fill, stroke, sw) must return the shape's markup."""
    x, y, w, h = bbox
    clip = shape_fn("#000", None, 0)
    return S.tiles(tile, x, y, w, h, base=base, extra_clip=clip) + shape_fn(None, stroke, sw)


def lit(S, shape_fn, vertical=False, diagonal=False, strength=0.26):
    """Soft top-left lighting overlay over a shape (white at the lit side, black on the far side)."""
    if diagonal:
        grad = S.lin([(0, "#ffffff", strength), (0.5, "#ffffff", 0), (1, "#000000", strength + 0.08)], 0, 0, 1, 1)
    elif vertical:
        grad = S.lin([(0, "#ffffff", strength), (0.5, "#ffffff", 0), (1, "#000000", strength + 0.08)], 0, 0, 1, 0)
    else:
        grad = S.lin([(0, "#ffffff", strength), (0.5, "#ffffff", 0), (1, "#000000", strength + 0.08)], 0, 0, 0, 1)
    return shape_fn(grad, None, 0)


def textured_circle(S, tile, cx, cy, r, stroke=OUT, sw=2.6, base=None):
    clip = circ(cx, cy, r, fill="#000", stroke=None)
    return (S.tiles(tile, cx - r, cy - r, 2 * r, 2 * r, base=base, extra_clip=clip)
            + circ(cx, cy, r, fill=None, stroke=stroke, sw=sw))


def tile_roof_half(S, x, y, w, h, base, vertical, lit):
    """One roof slope: shingles + lighting gradient. 'lit' = facing the light (top-left)."""
    pat = shingle_pattern(S, base, vertical=vertical)
    shape = S.tiles(pat, x, y, w, h, base=shade(base, 0.8))
    if vertical:
        grad = S.lin([(0, "#ffffff", 0.22 if lit else 0.0), (1, "#000000", 0.0 if lit else 0.30)], 0, 0, 1, 0)
    else:
        grad = S.lin([(0, "#ffffff", 0.22 if lit else 0.0), (1, "#000000", 0.0 if lit else 0.30)], 0, 0, 0, 1)
    return shape + rect(x, y, w, h, fill=grad, stroke=None)


def gable_roof(S, x, y, w, h, base, ridge="v", trim=OUT, ridge_col=None):
    """Top-down gabled roof with a visible ridge cap. ridge 'v' = ridge runs top-bottom."""
    rc = ridge_col or shade(base, 0.7)
    out = ""
    if ridge == "v":
        cx = x + w / 2
        out += tile_roof_half(S, x, y, w / 2, h, base, True, True)
        out += tile_roof_half(S, cx, y, w / 2, h, base, True, False)
        out += rect(cx - 3.5, y, 7, h, fill=rc, stroke=trim, sw=1.4)
        out += line(cx - 1.2, y + 2, cx - 1.2, y + h - 2, stroke=shade(rc, 1.4), sw=1.3, extra='opacity="0.8"')
    else:
        cy = y + h / 2
        out += tile_roof_half(S, x, y, w, h / 2, base, False, True)
        out += tile_roof_half(S, x, cy, w, h / 2, base, False, False)
        out += rect(x, cy - 3.5, w, 7, fill=rc, stroke=trim, sw=1.4)
        out += line(x + 2, cy - 1.2, x + w - 2, cy - 1.2, stroke=shade(rc, 1.4), sw=1.3, extra='opacity="0.8"')
    out += rect(x, y, w, h, fill=None, stroke=trim, sw=2.6)
    return out


def chimney(S, x, y, s=24, stone="#8b8681"):
    out = S.shadowed(rect(x, y, s, s, fill=stone, stroke=OUT, sw=2), 3, 4, 2.5, 0.5)
    out += rect(x + s * 0.2, y + s * 0.2, s * 0.6, s * 0.6, fill="#1d1816", stroke=None)
    out += rect(x + s * 0.2, y + s * 0.2, s * 0.6, s * 0.28, fill="#000000", stroke=None, extra='opacity="0.35"')
    out += line(x + 2, y + 3, x + s - 3, y + 3, stroke=shade(stone, 1.35), sw=1.3, extra='opacity="0.8"')
    return out


def skylight(S, x, y, w, h):
    sky = S.lin([(0, "#cfeaf5"), (1, "#7fb0cf")])
    out = rect(x, y, w, h, fill=sky, stroke=OUT, sw=1.8, r=1.5)
    out += line(x + w / 2, y + 1, x + w / 2, y + h - 1, stroke=OUT, sw=1.2)
    out += line(x + 1, y + h / 2, x + w - 1, y + h / 2, stroke=OUT, sw=1.2)
    out += line(x + 3, y + 3, x + w * 0.4, y + 3, stroke="#ffffff", sw=1.4, extra='opacity="0.8"')
    return out


def tree_canopy(S, cx, cy, r, dark, mid, light, clusters=9, seed=None, outline=True):
    """A leafy treetop seen from above: shadow, layered lobes, highlights, trunk dot."""
    rnd = random.Random(seed if seed is not None else S.rnd.random())
    out = ""
    shadow = ell(cx + r * 0.12, cy + r * 0.14, r * 0.98, r * 0.92, fill="#000", stroke=None, extra='opacity="0.28"')
    out += shadow
    body = blob(rnd, cx, cy, r, r, points=11, wobble=0.07)
    out += path(body, fill=S.rad([(0, mid), (1, dark)], 0.38, 0.35, 0.8), stroke=shade(dark, 0.45), sw=2.4)
    for i in range(clusters):
        a = 2 * math.pi * i / clusters + rnd.random() * 0.5
        d = r * (0.28 + rnd.random() * 0.38)
        lr = r * (0.26 + rnd.random() * 0.16)
        px, py = cx + math.cos(a) * d, cy + math.sin(a) * d
        out += circ(px, py, lr, fill=S.rad([(0, light), (1, mid)], 0.35, 0.3, 0.75), stroke=shade(mid, 0.6), sw=1.3, extra='opacity="0.95"')
        out += circ(px - lr * 0.28, py - lr * 0.32, lr * 0.32, fill=shade(light, 1.15), stroke=None, extra='opacity="0.55"')
    for _ in range(int(r * 0.7)):
        a, d = rnd.random() * 2 * math.pi, rnd.random() * r * 0.85
        out += circ(cx + math.cos(a) * d, cy + math.sin(a) * d, 1 + rnd.random() * 1.4,
                    fill=shade(dark, 0.75) if rnd.random() < 0.5 else shade(light, 1.2), stroke=None, extra='opacity="0.5"')
    out += circ(cx, cy, r * 0.07, fill="#5a4026", stroke=None)
    return out


def scatter(S, rnd, x, y, w, h, count, colors, rmin=0.8, rmax=2.0, opacity_=0.7):
    out = ""
    for _ in range(count):
        out += circ(x + rnd.random() * w, y + rnd.random() * h, rmin + rnd.random() * (rmax - rmin),
                    fill=rnd.choice(colors), stroke=None, extra=f'opacity="{n(opacity_)}"')
    return out


def tufts(rnd, x, y, w, h, count, colors, length=7):
    """Short grass-blade strokes."""
    d = {}
    for _ in range(count):
        px, py = x + rnd.random() * w, y + rnd.random() * h
        col = rnd.choice(colors)
        a = math.radians(-90 + (rnd.random() - 0.5) * 70)
        L = length * (0.6 + rnd.random() * 0.8)
        d.setdefault(col, []).append(f"M{n(px)} {n(py)}l{n(math.cos(a) * L)} {n(math.sin(a) * L)}")
    return "".join(f'<path d="{"".join(v)}" stroke="{c}" stroke-width="1.3" fill="none" opacity="0.85"/>' for c, v in d.items())
