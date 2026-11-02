# -*- coding: utf-8 -*-
"""Genera las 7 ilustraciones SVG del fondo del login (una por día de la semana).

Ilustraciones propias (sin imágenes de terceros ni licencias) y deterministas: cada escena usa una
semilla fija, así que regenerarlas produce los mismos ficheros.

Uso, desde la raíz del repositorio:
    python frontend/scripts/login_scenes.py frontend/src/assets/login
"""
import math
import pathlib
import random
import sys

W, H = 1600, 1000
OUT = pathlib.Path(sys.argv[1])
OUT.mkdir(parents=True, exist_ok=True)


def f(v):
    return str(int(round(v)))


# ----------------------------------------------------------------------------- primitivas
class Svg:
    def __init__(self):
        self.defs = []
        self.body = []

    def add(self, s):
        self.body.append(s)

    def circle(self, x, y, r, fill, extra=""):
        self.add(f'<circle cx="{f(x)}" cy="{f(y)}" r="{f(r)}" fill="{fill}"{extra}/>')

    def ellipse(self, x, y, rx, ry, fill, extra=""):
        self.add(f'<ellipse cx="{f(x)}" cy="{f(y)}" rx="{f(rx)}" ry="{f(ry)}" fill="{fill}"{extra}/>')

    def path(self, d, fill, extra=""):
        self.add(f'<path d="{d}" fill="{fill}"{extra}/>')


def smooth_path(points, close_bottom=True):
    """Curva suave por los puntos (punto medio + Q) cerrada hasta abajo."""
    d = f"M{f(points[0][0])},{f(points[0][1])}"
    for i in range(1, len(points) - 1):
        x0, y0 = points[i]
        x1, y1 = points[i + 1]
        d += f" Q{f(x0)},{f(y0)} {f((x0 + x1) / 2)},{f((y0 + y1) / 2)}"
    d += f" L{f(points[-1][0])},{f(points[-1][1])}"
    if close_bottom:
        d += f" L{W + 60},{H + 20} L-60,{H + 20} Z"
    return d


def ridge(rng, base, amp, step=60, freq=(1.0, 2.3, 5.1)):
    phase = [rng.uniform(0, 6.28) for _ in freq]
    pts = []
    for x in range(-60, W + 120, step):
        t = x / W
        y = base
        for k, fr in enumerate(freq):
            y -= amp / (k + 1) * math.sin(t * fr * math.pi * 2 + phase[k])
        pts.append((x, y))
    return pts


# ----------------------------------------------------------------------------- cielo y fondo
def sky(svg, stops, sun, horizon):
    grad = "".join(f'<stop offset="{o}" stop-color="{c}"/>' for o, c in stops)
    svg.defs.append(f'<linearGradient id="sky" x1="0" y1="0" x2="0" y2="1">{grad}</linearGradient>')
    svg.add(f'<rect width="{W}" height="{H}" fill="url(#sky)"/>')
    sx, sy, sr, scol, glow = sun
    svg.defs.append(
        f'<radialGradient id="sunglow"><stop offset="0" stop-color="{glow}" stop-opacity=".85"/>'
        f'<stop offset=".35" stop-color="{glow}" stop-opacity=".35"/><stop offset="1" stop-color="{glow}" stop-opacity="0"/></radialGradient>'
    )
    svg.add(f'<g class="sun"><circle cx="{sx}" cy="{sy}" r="{sr * 5}" fill="url(#sunglow)"/>'
            f'<circle cx="{sx}" cy="{sy}" r="{sr}" fill="{scol}"/></g>')


def clouds(svg, rng, color, opacity, count, ymax):
    for i in range(count):
        cls = "cloud" if i % 2 == 0 else "cloud2"
        cx, cy = rng.uniform(80, W - 80), rng.uniform(70, ymax)
        w = rng.uniform(90, 180)
        parts = []
        for k in range(5):
            px = cx + (k - 2) * w * 0.22 + rng.uniform(-10, 10)
            py = cy - math.sin(k / 4 * math.pi) * w * 0.12
            parts.append(f'<ellipse cx="{f(px)}" cy="{f(py)}" rx="{f(w * 0.22)}" ry="{f(w * 0.1)}"/>')
        svg.add(f'<g class="{cls}" fill="{color}" opacity="{opacity}">{"".join(parts)}</g>')


def birds(svg, rng, color, horizon):
    parts = []
    for _ in range(5):
        x, y, s = rng.uniform(0, 260), rng.uniform(horizon - 330, horizon - 180), rng.uniform(0.7, 1.3)
        parts.append(f'<path d="M{f(x - 12 * s)},{f(y)} q{f(6 * s)},{f(-7 * s)} {f(12 * s)},0 '
                     f'q{f(6 * s)},{f(-7 * s)} {f(12 * s)},0" fill="none" stroke="{color}" stroke-width="{f(2.2 * s)}" stroke-linecap="round"/>')
    svg.add(f'<g class="birds">{"".join(parts)}</g>')


def mountains(svg, rng, layers):
    for base, amp, color in layers:
        svg.path(smooth_path(ridge(rng, base, amp)), color)


# ----------------------------------------------------------------------------- perspectiva
class Persp:
    def __init__(self, horizon, vpx=W / 2):
        self.h = horizon
        self.vpx = vpx
        self.depth = H - horizon

    def project(self, u, z):
        s = 1.0 / z
        return self.vpx + u * s, self.h + self.depth * s, s


def ground(svg, horizon, top, bottom, path_color=None, persp=None, path_w=90):
    svg.defs.append(f'<linearGradient id="ground" x1="0" y1="0" x2="0" y2="1">'
                    f'<stop offset="0" stop-color="{top}"/><stop offset="1" stop-color="{bottom}"/></linearGradient>')
    svg.add(f'<rect y="{horizon}" width="{W}" height="{H - horizon}" fill="url(#ground)"/>')
    if path_color and persp:
        x0, y0, _ = persp.project(-path_w, 0.62)
        x1, _, _ = persp.project(path_w, 0.62)
        svg.path(f"M{f(persp.vpx - 2)},{horizon} L{f(persp.vpx + 2)},{horizon} L{f(x1)},{f(y0)} L{f(x0)},{f(y0)} Z", path_color)


def shadow(svg, x, y, w, s):
    svg.ellipse(x + 8 * s, y + 2 * s, w, w * 0.22, "#000", ' opacity=".18"')


def canopy_tree(svg, rng, x, y, s, greens, fruit=None, fruit_n=9, fruit_r=6.0, trunk="#6b4a2f", blush=None):
    shadow(svg, x, y, 62 * s, s)
    svg.add(f'<rect x="{f(x - 6 * s)}" y="{f(y - 58 * s)}" width="{f(12 * s)}" height="{f(58 * s)}" rx="{f(4 * s)}" fill="{trunk}"/>')
    cy = y - 95 * s
    for k, (dx, dy, r) in enumerate([(-34, 12, 42), (34, 10, 44), (0, -18, 56), (-12, 22, 40), (18, 24, 38)]):
        svg.circle(x + dx * s, cy + dy * s, r * s, greens[k % len(greens)])
    svg.circle(x - 16 * s, cy - 30 * s, 22 * s, greens[-1], ' opacity=".55"')
    if fruit:
        for _ in range(fruit_n):
            a, d = rng.uniform(0, 6.28), rng.uniform(0, 50)
            fx, fy = x + math.cos(a) * d * s, cy + math.sin(a) * d * 0.75 * s + 6 * s
            svg.circle(fx, fy, fruit_r * s, fruit)
            if blush:
                svg.circle(fx - fruit_r * 0.3 * s, fy - fruit_r * 0.3 * s, fruit_r * 0.45 * s, blush, ' opacity=".8"')


def blossom_tree(svg, rng, x, y, s, petals, trunk="#5a3d2b"):
    shadow(svg, x, y, 70 * s, s)
    w = 5 * s
    svg.path(f"M{f(x - w)},{f(y)} L{f(x - 3 * s)},{f(y - 55 * s)} L{f(x - 40 * s)},{f(y - 100 * s)} "
             f"L{f(x - 33 * s)},{f(y - 104 * s)} L{f(x)},{f(y - 68 * s)} L{f(x + 36 * s)},{f(y - 108 * s)} "
             f"L{f(x + 42 * s)},{f(y - 102 * s)} L{f(x + 4 * s)},{f(y - 52 * s)} L{f(x + w)},{f(y)} Z", trunk)
    cy = y - 108 * s
    for _ in range(16):
        a, d = rng.uniform(0, 6.28), rng.uniform(0, 58)
        svg.circle(x + math.cos(a) * d * s, cy + math.sin(a) * d * 0.6 * s, rng.uniform(16, 26) * s,
                   rng.choice(petals), ' opacity=".92"')
    for _ in range(6):
        svg.circle(x + rng.uniform(-70, 70) * s, y + rng.uniform(-4, 10) * s, 3.2 * s, petals[0], ' opacity=".8"')


def olive_tree(svg, rng, x, y, s, greens, trunk="#6d5a45"):
    shadow(svg, x, y, 70 * s, s)
    svg.path(f"M{f(x - 10 * s)},{f(y)} C{f(x - 2 * s)},{f(y - 25 * s)} {f(x - 20 * s)},{f(y - 45 * s)} {f(x - 6 * s)},{f(y - 70 * s)} "
             f"L{f(x + 8 * s)},{f(y - 70 * s)} C{f(x + 2 * s)},{f(y - 45 * s)} {f(x + 16 * s)},{f(y - 25 * s)} {f(x + 11 * s)},{f(y)} Z", trunk)
    cy = y - 104 * s
    for k, (dx, dy, rx, ry) in enumerate([(-42, 14, 40, 30), (40, 12, 42, 30), (0, -14, 52, 36), (-22, -30, 30, 22),
                                          (26, -28, 32, 22), (0, 22, 44, 24)]):
        svg.ellipse(x + (dx + rng.uniform(-6, 6)) * s, cy + dy * s, rx * s, ry * s, greens[k % len(greens)])
    for _ in range(7):
        svg.ellipse(x + rng.uniform(-50, 50) * s, cy + rng.uniform(-34, 16) * s, rng.uniform(8, 14) * s,
                    rng.uniform(4, 6) * s, "#c9d4ad", ' opacity=".75"')


def vine(svg, rng, x, y, s, leaves, grape):
    svg.add(f'<rect x="{f(x - 3 * s)}" y="{f(y - 60 * s)}" width="{f(6 * s)}" height="{f(60 * s)}" fill="#5e4230"/>')
    for k in range(7):
        svg.ellipse(x + rng.uniform(-40, 40) * s, y - rng.uniform(70, 110) * s, rng.uniform(26, 38) * s,
                    rng.uniform(16, 22) * s, leaves[k % len(leaves)])
    for b in range(2):
        gx, gy = x + rng.uniform(-28, 28) * s, y - 62 * s
        for row in range(4):
            for c in range(4 - row):
                svg.circle(gx + (c - (3 - row) / 2) * 8 * s, gy + row * 7.5 * s, 4.8 * s, grape)


def vine_rows(svg, persp, row_u, rows, color, zfar=7.0, znear=0.9):
    for i in range(-rows, rows + 1):
        if i == 0:
            continue
        u = i * row_u
        xa, ya, sa = persp.project(u, zfar)
        xb, yb, sb = persp.project(u, znear)
        svg.path(f"M{f(xa)},{f(ya - 105 * sa)} L{f(xb)},{f(yb - 105 * sb)} L{f(xb)},{f(yb - 55 * sb)} L{f(xa)},{f(ya - 55 * sa)} Z",
                 color, ' opacity=".9"')


def lettuce(svg, x, y, s, outer, inner):
    svg.ellipse(x, y, 36 * s, 19 * s, outer)
    svg.ellipse(x, y - 5 * s, 24 * s, 12 * s, inner)
    svg.ellipse(x - 6 * s, y - 8 * s, 9 * s, 5 * s, "#e6f5b5", ' opacity=".85"')


def cypress_line(svg, rng, horizon, color, count=38):
    parts = []
    x = -20
    while x < W + 20:
        h = rng.uniform(28, 52)
        parts.append(f'<path d="M{f(x - 6)},{horizon + 2} Q{f(x - 7)},{f(horizon - h * 0.6)} {f(x)},{f(horizon - h)} '
                     f'Q{f(x + 7)},{f(horizon - h * 0.6)} {f(x + 6)},{horizon + 2} Z"/>')
        x += rng.uniform(22, 48)
    svg.add(f'<g fill="{color}">{"".join(parts)}</g>')


def beehive(svg, x, y, s, color):
    svg.add(f'<rect x="{f(x - 26 * s)}" y="{f(y - 40 * s)}" width="{f(52 * s)}" height="{f(40 * s)}" fill="{color}"/>'
            f'<rect x="{f(x - 30 * s)}" y="{f(y - 48 * s)}" width="{f(60 * s)}" height="{f(9 * s)}" fill="#7a5a3a"/>'
            f'<rect x="{f(x - 26 * s)}" y="{f(y - 22 * s)}" width="{f(52 * s)}" height="{f(3 * s)}" fill="#000" opacity=".12"/>'
            f'<rect x="{f(x - 8 * s)}" y="{f(y - 6 * s)}" width="{f(16 * s)}" height="{f(4 * s)}" fill="#3b2a1a"/>')


def meadow(svg, rng, horizon, greens, flowers, density=260, ymin=0.78):
    tufts = []
    for _ in range(density):
        x = rng.uniform(-20, W + 20)
        y = rng.uniform(H * ymin, H + 10)
        s = 0.6 + (y - H * ymin) / (H * (1 - ymin)) * 1.1
        g = rng.choice(greens)
        tufts.append(f'<path d="M{f(x)},{f(y)} q{f(-4 * s)},{f(-18 * s)} {f(-9 * s)},{f(-26 * s)} '
                     f'M{f(x)},{f(y)} q{f(1 * s)},{f(-20 * s)} {f(2 * s)},{f(-30 * s)} '
                     f'M{f(x)},{f(y)} q{f(5 * s)},{f(-15 * s)} {f(11 * s)},{f(-22 * s)}" stroke="{g}" stroke-width="{f(2.4 * s)}" fill="none" stroke-linecap="round"/>')
        if flowers and rng.random() < 0.28:
            c = rng.choice(flowers)
            fx, fy = x + rng.uniform(-6, 6), y - 26 * s
            tufts.append(f'<circle cx="{f(fx)}" cy="{f(fy)}" r="{f(3.6 * s)}" fill="{c}"/>'
                         f'<circle cx="{f(fx)}" cy="{f(fy)}" r="{f(1.3 * s)}" fill="#f7d65a"/>')
    svg.add(f'<g class="sway">{"".join(tufts)}</g>')


def butterfly(svg, x, y, color):
    svg.add(f'<g class="flutter" transform="translate({x} {y})"><ellipse cx="-7" cy="-4" rx="8" ry="6" fill="{color}"/>'
            f'<ellipse cx="7" cy="-4" rx="8" ry="6" fill="{color}"/><ellipse cx="-5" cy="5" rx="5" ry="4" fill="{color}" opacity=".85"/>'
            f'<ellipse cx="5" cy="5" rx="5" ry="4" fill="{color}" opacity=".85"/><rect x="-1" y="-8" width="2" height="16" rx="1" fill="#2d2a26"/></g>')


# ----------------------------------------------------------------------------- huertos en filas
def orchard(svg, rng, persp, draw, row_u=260, zs=(0.95, 8.0, 0.55), skip_center=True, jitter=10, haze=None, rows=7):
    items = []
    zmin, zmax, dz = zs
    for i in range(-rows, rows + 1):
        if skip_center and i == 0:
            continue
        u = i * row_u - (row_u / 2 if skip_center and i > 0 else 0) + (row_u / 2 if skip_center and i < 0 else 0)
        z = zmin
        while z < zmax:
            x, y, s = persp.project(u + rng.uniform(-jitter, jitter), z)
            if -220 < x < W + 220:
                items.append((z, x, y, s))
            z += dz * (1 + z * 0.08)
    hazed = False
    for z, x, y, s in sorted(items, key=lambda t: -t[0]):
        if haze and not hazed and z < 3.2:
            add_haze(svg, persp, haze)
            hazed = True
        draw(x, y, s)
    if haze and not hazed:
        add_haze(svg, persp, haze)


def add_haze(svg, persp, color):
    if 'id="haze"' not in "".join(svg.defs):
        svg.defs.append(f'<linearGradient id="haze" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{color}" stop-opacity="0"/>'
                        f'<stop offset=".22" stop-color="{color}" stop-opacity=".7"/>'
                        f'<stop offset="1" stop-color="{color}" stop-opacity="0"/></linearGradient>')
    svg.add(f'<rect x="0" y="{persp.h - 50}" width="{W}" height="230" fill="url(#haze)"/>')


STYLE = """<style>
.cloud{animation:drift 95s ease-in-out infinite alternate}
.cloud2{animation:drift 140s ease-in-out infinite alternate-reverse}
@keyframes drift{from{transform:translateX(-70px)}to{transform:translateX(70px)}}
.birds{animation:fly 55s linear infinite}
@keyframes fly{from{transform:translate(-320px,40px)}to{transform:translate(1900px,-60px)}}
.sun{animation:glow 9s ease-in-out infinite alternate}
@keyframes glow{from{opacity:.88}to{opacity:1}}
.sway{animation:sway 7s ease-in-out infinite alternate;transform-origin:50% 100%;transform-box:fill-box}
@keyframes sway{from{transform:skewX(-1.2deg)}to{transform:skewX(1.2deg)}}
.flutter{animation:flut 1.4s ease-in-out infinite alternate}
@keyframes flut{from{opacity:1}to{opacity:.75}}
@media (prefers-reduced-motion:reduce){*{animation:none!important}}
</style>"""


def write(name, svg, title):
    doc = (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}" preserveAspectRatio="xMidYMid slice" '
           f'role="img" aria-label="{title}"><title>{title}</title>{STYLE}<defs>{"".join(svg.defs)}</defs>{"".join(svg.body)}</svg>')
    (OUT / f"{name}.svg").write_text(doc, encoding="utf-8")
    print(f"{name}.svg {len(doc) // 1024} KB")


# ============================================================================= escenas
def naranjos():  # lunes · amanecer
    rng = random.Random(1)
    svg, hz = Svg(), 540
    sky(svg, [(0, "#3f5a8a"), (0.45, "#f3a27c"), (0.8, "#ffd39a"), (1, "#ffe6b8")], (1060, 400, 46, "#fff1c9", "#ffcf7a"), hz)
    clouds(svg, rng, "#ffe1cf", ".75", 6, 330)
    birds(svg, rng, "#4a3a4e", hz)
    mountains(svg, rng, [(470, 55, "#b98a9c"), (505, 40, "#9c7f8f"), (530, 26, "#7f8a6a")])
    p = Persp(hz, 760)
    ground(svg, hz, "#8a9a52", "#4f6b2c", "#c9a36b", p)
    orchard(svg, rng, p, lambda x, y, s: canopy_tree(svg, rng, x, y, s, ["#2f5d2a", "#3e7534", "#2a5226", "#5d9444"],
                                                       fruit="#f28c1e", fruit_n=11, fruit_r=7), haze="#ffd9a8")
    meadow(svg, rng, hz, ["#5a8a36", "#77a548", "#3f6b2a"], ["#ffffff", "#f4c542"], 220)
    butterfly(svg, 1260, 820, "#f6a23a")
    write("lunes-naranjos", svg, "Naranjos al amanecer en la Vega del Segura")


def limoneros():  # martes · mediodía
    rng = random.Random(2)
    svg, hz = Svg(), 520
    sky(svg, [(0, "#3f8fd2"), (0.6, "#8cc8ef"), (1, "#dff2fb")], (340, 150, 42, "#fffbe8", "#fff3b0"), hz)
    clouds(svg, rng, "#ffffff", ".9", 7, 300)
    birds(svg, rng, "#35506b", hz)
    mountains(svg, rng, [(455, 60, "#a9c3cf"), (490, 42, "#8fae9c"), (515, 24, "#6f955c")])
    p = Persp(hz, 860)
    ground(svg, hz, "#9db357", "#5d7f2f", "#d8bd86", p)
    orchard(svg, rng, p, lambda x, y, s: canopy_tree(svg, rng, x, y, s, ["#2e6b2e", "#3f8a3a", "#28592a", "#6aa84f"],
                                                       fruit="#f7de2f", fruit_n=12, fruit_r=6.5), haze="#dff2fb")
    meadow(svg, rng, hz, ["#6a9a3a", "#8bb84f", "#4e7d2d"], ["#ffffff", "#c9a2e8", "#f4e04d"], 230)
    butterfly(svg, 300, 860, "#fff3a1")
    write("martes-limoneros", svg, "Limoneros a mediodía en la Huerta de Murcia")


def almendros():  # miércoles · floración
    rng = random.Random(3)
    svg, hz = Svg(), 560
    sky(svg, [(0, "#7d9fd6"), (0.55, "#c4d5f0"), (1, "#f4e8f1")], (1250, 190, 38, "#ffffff", "#fff4f8"), hz)
    clouds(svg, rng, "#ffffff", ".85", 6, 320)
    birds(svg, rng, "#4c5877", hz)
    mountains(svg, rng, [(470, 80, "#a8a8cc"), (520, 50, "#9aa4bf"), (548, 30, "#8a9c77")])
    p = Persp(hz, 700)
    ground(svg, hz, "#a7b36d", "#6b7f3d", "#c8a37a", p)
    orchard(svg, rng, p, lambda x, y, s: blossom_tree(svg, rng, x, y, s, ["#fbe3ee", "#f6c4d8", "#ffffff", "#f19fbf"]),
            row_u=300, zs=(0.98, 8.0, 0.6), haze="#f4e8f1")
    meadow(svg, rng, hz, ["#77923f", "#98ad5a", "#5d7a33"], ["#ffffff", "#f7b6cf"], 200)
    butterfly(svg, 1180, 850, "#ffffff")
    write("miercoles-almendros", svg, "Almendros en flor en el Noroeste murciano")


def uva():  # jueves · hora dorada
    rng = random.Random(4)
    svg, hz = Svg(), 545
    sky(svg, [(0, "#5a6fa6"), (0.4, "#f0b36d"), (0.85, "#ffd88a"), (1, "#ffeab8")], (420, 400, 50, "#fff0c2", "#ffc164"), hz)
    clouds(svg, rng, "#ffd9a8", ".7", 6, 320)
    birds(svg, rng, "#4d3b36", hz)
    mountains(svg, rng, [(470, 60, "#c29478"), (505, 44, "#a8876a"), (532, 26, "#8b8a52")])
    p = Persp(hz, 900)
    ground(svg, hz, "#b39a58", "#6f6a2e", "#d9b27a", p, path_w=70)
    cypress_line(svg, rng, hz, "#6d6a3a")
    # espalderas: alambres de cada fila
    for i in range(-10, 11):
        if i == 0:
            continue
        u = i * 170
        x0, y0, _ = p.project(u, 7)
        x1, y1, _ = p.project(u, 0.7)
        svg.add(f'<path d="M{f(x0)},{f(y0 - 70 / 7)} L{f(x1)},{f(y1 - 70 / 0.7)}" stroke="#6e5a3e" stroke-width="1.4" opacity=".4"/>')
    vine_rows(svg, p, 170, 8, "#355c22")
    orchard(svg, rng, p, lambda x, y, s: vine(svg, rng, x, y, s, ["#3f6d25", "#5b8c30", "#2f5620", "#79a73f"], "#7b2d5b"),
            row_u=170, zs=(0.9, 7.0, 0.6), jitter=4, haze="#ffe2b0", rows=8)
    meadow(svg, rng, hz, ["#8b8f3a", "#a8a54c", "#6a7430"], ["#f4c542", "#ffffff"], 180)
    write("jueves-uva", svg, "Uva de mesa en la hora dorada del Valle del Guadalentín")


def lechugas():  # viernes · mañana
    rng = random.Random(5)
    svg, hz = Svg(), 500
    sky(svg, [(0, "#4f9ad8"), (0.65, "#a8d6f2"), (1, "#eaf7fb")], (1180, 170, 40, "#fffdf0", "#fff6c2"), hz)
    clouds(svg, rng, "#ffffff", ".92", 8, 280)
    birds(svg, rng, "#3a566e", hz)
    mountains(svg, rng, [(445, 40, "#b7c9d4"), (480, 26, "#9fb8a6")])
    p = Persp(hz, 800)
    ground(svg, hz, "#9c8a5e", "#7a5a38")
    cypress_line(svg, rng, hz, "#46603a")
    # bancales: franjas de tierra alternas y goteros
    for i in range(-14, 15):
        u0, u1 = i * 120 - 45, i * 120 + 45
        a0, b0, _ = p.project(u0, 14)
        a1, _, _ = p.project(u1, 14)
        c0, d0, _ = p.project(u0, 0.62)
        c1, _, _ = p.project(u1, 0.62)
        svg.path(f"M{f(a0)},{f(b0)} L{f(a1)},{f(b0)} L{f(c1)},{f(d0)} L{f(c0)},{f(d0)} Z", "#5f8a36", ' opacity=".55"')
        g0, h0, _ = p.project(i * 120, 14)
        g1, h1, _ = p.project(i * 120, 0.62)
        svg.add(f'<path d="M{f(g0)},{f(h0)} L{f(g1)},{f(h1)}" stroke="#2b2b2b" stroke-width="1.3" opacity=".35"/>')
    orchard(svg, rng, p, lambda x, y, s: lettuce(svg, x, y, s, "#3f8a2f", "#86c455"),
            row_u=120, zs=(0.85, 8.0, 0.2), skip_center=False, jitter=3, haze="#eaf7fb", rows=11)
    meadow(svg, rng, hz, ["#6b9b3a", "#86b44c"], ["#ffffff", "#f4c542"], 90, ymin=0.9)
    butterfly(svg, 1380, 880, "#fbfbfb")
    write("viernes-lechugas", svg, "Lechugas en bancales del Campo de Cartagena")


def olivar():  # sábado · tarde, cubierta vegetal
    rng = random.Random(6)
    svg, hz = Svg(), 530
    sky(svg, [(0, "#6f8fc0"), (0.55, "#bcd2e3"), (1, "#f3ead2")], (1320, 300, 44, "#fff7de", "#ffe7a8"), hz)
    clouds(svg, rng, "#ffffff", ".8", 6, 320)
    birds(svg, rng, "#48566a", hz)
    mountains(svg, rng, [(460, 70, "#a9a6b8"), (500, 48, "#9c9e92"), (522, 30, "#8b9b64")])
    p = Persp(hz, 780)
    ground(svg, hz, "#9fae5e", "#62823a", "#d5b788", p)
    orchard(svg, rng, p, lambda x, y, s: olive_tree(svg, rng, x, y, s, ["#4f6636", "#62794a", "#728a55", "#44592f"]),
            row_u=320, zs=(0.98, 8.0, 0.62), haze="#f3ead2")
    meadow(svg, rng, hz, ["#6f9a3c", "#8fb54f", "#557d2f"], ["#e8e24d", "#ffffff", "#c79be0", "#f06b5b"], 300, ymin=0.74)
    butterfly(svg, 520, 830, "#f3c14a")
    write("sabado-olivar", svg, "Olivar ecológico con cubierta vegetal en Mula")


def melocotoneros():  # domingo · atardecer, colmenas
    rng = random.Random(7)
    svg, hz = Svg(), 550
    sky(svg, [(0, "#4a3f78"), (0.4, "#d9788a"), (0.75, "#ffb27a"), (1, "#ffd7a1")], (820, 430, 54, "#ffe7b3", "#ffae6b"), hz)
    clouds(svg, rng, "#ffc2a6", ".7", 6, 330)
    birds(svg, rng, "#3e2f45", hz)
    mountains(svg, rng, [(480, 58, "#a8708a"), (515, 40, "#8b6a78"), (540, 24, "#76765a")])
    p = Persp(hz, 800)
    ground(svg, hz, "#8e9650", "#55662c", "#c79c6c", p)
    orchard(svg, rng, p, lambda x, y, s: canopy_tree(svg, rng, x, y, s, ["#3c6b2f", "#4f843a", "#335e29", "#79a651"],
                                                       fruit="#f08a3c", fruit_n=10, fruit_r=7, blush="#e0483d"), haze="#ffc9a0")
    for i, (x, c) in enumerate([(120, "#f2e3b3"), (250, "#9cc3d9"), (380, "#f3c35a")]):
        beehive(svg, x + 10, 975 - i * 8, 1.9, c)
    meadow(svg, rng, hz, ["#667f36", "#839b48", "#4d6a2a"], ["#ffffff", "#f7d34a", "#c86fd1"], 240)
    butterfly(svg, 470, 800, "#ffd166")
    write("domingo-melocotoneros", svg, "Melocotoneros y colmenas al atardecer en Cieza")


for scene in (naranjos, limoneros, almendros, uva, lechugas, olivar, melocotoneros):
    scene()
