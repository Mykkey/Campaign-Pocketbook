"""Drawing helpers for the world map symbols.

Every symbol is written once as a function that takes a Style, so it is produced in all five art styles:
classic ink, watercolour, fantasy colour, retro pixel and dark fantasy. A symbol draws itself on a canvas that is
100 units wide; its height is whatever it needs.
"""
import math
import random

REGISTRY = []  # (id, name, category, w_cells, h_units, family, anchor, fn)


def feature(fid, name, category, w, h=100, family=None, anchor="base"):
    """Register a symbol. w is its default width in map cells; anchor is 'base' (stands on the point) or 'center'."""
    def deco(fn):
        REGISTRY.append((fid, name, category, w, h, family, anchor, fn))
        return fn
    return deco


# ---------------------------------------------------------------- colour helpers

def _rgb(c):
    c = c.lstrip("#")
    return int(c[0:2], 16), int(c[2:4], 16), int(c[4:6], 16)


def _hex(r, g, b):
    return "#%02x%02x%02x" % (max(0, min(255, round(r))), max(0, min(255, round(g))), max(0, min(255, round(b))))


def mix(a, b, t):
    ar, ag, ab = _rgb(a)
    br, bg, bb = _rgb(b)
    return _hex(ar + (br - ar) * t, ag + (bg - ag) * t, ab + (bb - ab) * t)


def shade(c, f):
    """f > 0 lightens toward white, f < 0 darkens toward black."""
    return mix(c, "#ffffff", f) if f >= 0 else mix(c, "#000000", -f)


def desat(c, amount):
    r, g, b = _rgb(c)
    gray = 0.3 * r + 0.59 * g + 0.11 * b
    return _hex(r + (gray - r) * amount, g + (gray - g) * amount, b + (gray - b) * amount)


def posterize(c, levels):
    step = 255.0 / (levels - 1)
    r, g, b = _rgb(c)
    return _hex(round(r / step) * step, round(g / step) * step, round(b / step) * step)


BASE = dict(
    rock="#9a948a", snow="#f4f8fb", grass="#7fb94f", leaf="#4a9a3f", leafdark="#2c6a2c", trunk="#6b4a2b",
    sand="#e6d28f", water="#5aa9d6", wall="#d2c6a9", roof="#b5483a", wood="#8a6238", ice="#cfe9f5",
    lava="#ff6a1a", ash="#4a4642", dirt="#a07f55", bone="#e8e0cc", cactus="#5d9a4d", gold="#d9a62e",
    red="#b9382e", cloth="#c0392b", stone="#b8b2a6", dark="#2b2118", smoke="#9aa0a6", blue="#3b6fb6",
)


class Style:
    def __init__(self, name):
        self.name = name
        if name == "fantasy":
            self.ink, self.ow, self.mode, self.ink_opacity = "#2b2118", 1.1, "faces", 1.0
            self.tf = lambda c: c
        elif name == "classic":
            self.ink, self.ow, self.mode, self.ink_opacity = "#3b2a1a", 1.0, "hatch", 1.0
            self.tf = lambda c: mix(mix(c, "#efe6cf", 0.55), "#c9a46a", 0.15)
        elif name == "watercolour":
            self.ink, self.ow, self.mode, self.ink_opacity = "#5b4a52", 0.6, "flat", 0.55
            self.tf = lambda c: mix(desat(c, 0.2), "#ffffff", 0.3)
        elif name == "pixel":
            self.ink, self.ow, self.mode, self.ink_opacity = "#1a1420", 2.0, "faces", 1.0
            self.tf = lambda c: posterize(c, 5)
        elif name == "dark":
            self.ink, self.ow, self.mode, self.ink_opacity = "#0b0b0d", 1.0, "faces", 1.0
            self.tf = lambda c: shade(desat(c, 0.35), -0.42)
        else:
            raise ValueError(name)

    def c(self, role, s=0.0):
        """The colour of a material in this style, lightened (s > 0) or darkened (s < 0)."""
        col = self.tf(BASE[role])
        return shade(col, s) if s else col

    def line(self, w=None, extra=""):
        sw = self.ow * (w if w else 1)
        return 'stroke="%s" stroke-width="%.2f" stroke-opacity="%.2f" stroke-linejoin="round" stroke-linecap="round" %s' % (
            self.ink, sw, self.ink_opacity, extra)


class Svg:
    def __init__(self, w, h, style):
        self.w, self.h, self.st = w, h, style
        self.parts = []
        self._clip = 0

    def add(self, s):
        self.parts.append(s)

    def clip_id(self):
        self._clip += 1
        return "c%d" % self._clip

    def build(self):
        body = "\n".join(self.parts)
        return ('<svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" viewBox="0 0 %d %d">\n%s\n</svg>\n'
                % (self.w, self.h, self.w, self.h, body))


# ---------------------------------------------------------------- primitives

def pts(points):
    return " ".join("%.1f,%.1f" % (x, y) for x, y in points)


def poly(S, points, fill, outline=True, w=None, opacity=1.0):
    st = S.st
    S.add('<polygon points="%s" fill="%s" fill-opacity="%.2f" %s/>' % (
        pts(points), fill, opacity, st.line(w) if outline else 'stroke="none"'))


def path(S, d, fill="none", outline=True, w=None, opacity=1.0, extra=""):
    st = S.st
    if "stroke=" in extra:  # the caller draws its own stroke
        ln = ""
    else:
        ln = st.line(w) if outline else 'stroke="none"'
    S.add('<path d="%s" fill="%s" fill-opacity="%.2f" %s %s/>' % (d, fill, opacity, ln, extra))


def circle(S, cx, cy, r, fill, outline=True, w=None, opacity=1.0):
    st = S.st
    S.add('<circle cx="%.1f" cy="%.1f" r="%.1f" fill="%s" fill-opacity="%.2f" %s/>' % (
        cx, cy, r, fill, opacity, st.line(w) if outline else 'stroke="none"'))


def ellipse(S, cx, cy, rx, ry, fill, outline=True, w=None, opacity=1.0):
    st = S.st
    S.add('<ellipse cx="%.1f" cy="%.1f" rx="%.1f" ry="%.1f" fill="%s" fill-opacity="%.2f" %s/>' % (
        cx, cy, rx, ry, fill, opacity, st.line(w) if outline else 'stroke="none"'))


def rect(S, x, y, w, h, fill, outline=True, lw=None, r=0):
    st = S.st
    S.add('<rect x="%.1f" y="%.1f" width="%.1f" height="%.1f" rx="%.1f" fill="%s" %s/>' % (
        x, y, w, h, r, fill, st.line(lw) if outline else 'stroke="none"'))


def line(S, x1, y1, x2, y2, w=1.0, color=None, opacity=None):
    st = S.st
    col = color or st.ink
    op = st.ink_opacity if opacity is None else opacity
    S.add('<line x1="%.1f" y1="%.1f" x2="%.1f" y2="%.1f" stroke="%s" stroke-width="%.2f" stroke-opacity="%.2f" stroke-linecap="round"/>' % (
        x1, y1, x2, y2, col, st.ow * w, op))


def shadow(S, cx, cy, rx, ry):
    """A soft ground shadow under a symbol."""
    op = {"classic": 0.0, "watercolour": 0.14, "fantasy": 0.22, "pixel": 0.0, "dark": 0.3}[S.st.name]
    if op > 0:
        S.add('<ellipse cx="%.1f" cy="%.1f" rx="%.1f" ry="%.1f" fill="#000000" fill-opacity="%.2f"/>' % (cx, cy, rx, ry, op))


def hatch(S, points, angle_deg=60, spacing=3.2, w=0.6, color=None, opacity=0.8):
    """Hatching lines clipped to a polygon (classic ink shading)."""
    cid = S.clip_id()
    xs = [p[0] for p in points]
    ys = [p[1] for p in points]
    x0, x1, y0, y1 = min(xs), max(xs), min(ys), max(ys)
    S.add('<clipPath id="%s"><polygon points="%s"/></clipPath>' % (cid, pts(points)))
    out = []
    a = math.radians(angle_deg)
    dx, dy = math.cos(a), math.sin(a)
    diag = math.hypot(x1 - x0, y1 - y0)
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    nx, ny = -dy, dx
    k = -diag
    while k <= diag:
        px, py = cx + nx * k, cy + ny * k
        out.append('<line x1="%.1f" y1="%.1f" x2="%.1f" y2="%.1f"/>' % (px - dx * diag, py - dy * diag, px + dx * diag, py + dy * diag))
        k += spacing
    S.add('<g clip-path="url(#%s)" stroke="%s" stroke-width="%.2f" stroke-opacity="%.2f">%s</g>' % (cid, color or S.st.ink, w * S.st.ow, opacity, "".join(out)))


def lerp(a, b, t):
    return (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)


def blob_points(rnd, cx, cy, rx, ry, n=10, wobble=0.16):
    out = []
    for i in range(n):
        a = 2 * math.pi * i / n
        k = 1 + (rnd.random() - 0.5) * 2 * wobble
        out.append((cx + math.cos(a) * rx * k, cy + math.sin(a) * ry * k))
    return out


def smooth_closed(points):
    """A smooth closed path through the points (Catmull-Rom to Bezier)."""
    n = len(points)
    d = "M%.1f,%.1f" % points[0]
    for i in range(n):
        p0, p1, p2, p3 = points[(i - 1) % n], points[i], points[(i + 1) % n], points[(i + 2) % n]
        c1 = (p1[0] + (p2[0] - p0[0]) / 6, p1[1] + (p2[1] - p0[1]) / 6)
        c2 = (p2[0] - (p3[0] - p1[0]) / 6, p2[1] - (p3[1] - p1[1]) / 6)
        d += " C%.1f,%.1f %.1f,%.1f %.1f,%.1f" % (c1[0], c1[1], c2[0], c2[1], p2[0], p2[1])
    return d + " Z"


def rng_for(fid):
    return random.Random(sum(ord(ch) * (i + 7) for i, ch in enumerate(fid)))
