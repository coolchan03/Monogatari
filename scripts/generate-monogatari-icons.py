#!/usr/bin/env python3
"""Reproducible flat Kitsune launcher icons; requires Pillow, not an Android SDK."""
from pathlib import Path
import math
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1] / "app" / "src"
DARK = "#102C3A"
CREAM = "#FBF4E5"
TEAL = "#3A788E"
SLATE = "#7697AA"
INK = "#1C3445"
SCALE = 10

def render(size, round_icon=False, transparent=False):
    n = 108 * SCALE
    im = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    def point(x, y): return (round(x * SCALE), round(y * SCALE))
    def box(x1, y1, x2, y2): return (*point(x1, y1), *point(x2, y2))
    def poly(coords, fill): d.polygon([point(*p) for p in coords], fill=fill)
    def bezier(coords, width=3, fill=INK, segments=20):
        # One cubic Bezier path; high-resolution line then Lanczos resize.
        p0, p1, p2, p3 = coords
        pts = []
        for i in range(segments + 1):
            t = i / segments
            u = 1 - t
            x = u*u*u*p0[0] + 3*u*u*t*p1[0] + 3*u*t*t*p2[0] + t*t*t*p3[0]
            y = u*u*u*p0[1] + 3*u*u*t*p1[1] + 3*u*t*t*p2[1] + t*t*t*p3[1]
            pts.append(point(x, y))
        d.line(pts, fill=fill, width=round(width*SCALE), joint="curve")
        r = round(width*SCALE/2)
        for x, y in (pts[0], pts[-1]):
            d.ellipse((x-r,y-r,x+r,y+r), fill=fill)
    if not transparent:
        d.rounded_rectangle((0, 0, n-1, n-1), radius=24*SCALE, fill=DARK)
    # Pointed kitsune ears and contrasting inner fur.
    poly([(25,57),(20,19),(45,43),(45,61)], CREAM)
    poly([(83,57),(88,19),(63,43),(63,61)], CREAM)
    poly([(29,46),(27,31),(43,47)], SLATE)
    poly([(79,46),(81,31),(65,47)], SLATE)
    # Simplified face is expressive even at 48-pixel launcher size.
    d.ellipse(box(24,35,84,83), fill=CREAM)
    poly([(54,44),(49,53),(54,60),(59,53)], SLATE)
    bezier([(35,60),(40,67),(46,67),(49,60)], 3.5)
    bezier([(59,60),(62,67),(68,67),(73,60)], 3.5)
    poly([(50,70),(54,68),(58,70),(54,73)], INK)
    # Two clear book leaves, curved-looking lower edges and central crease.
    poly([(19,75),(36,72),(53,80),(53,93),(38,87),(19,89)], TEAL)
    poly([(89,75),(72,72),(55,80),(55,93),(70,87),(89,89)], "#2F677E")
    poly([(24,75),(36,75),(52,81),(52,86),(38,80),(24,82)], CREAM)
    poly([(84,75),(72,75),(56,81),(56,86),(70,80),(84,82)], CREAM)
    d.line([point(54,79),point(54,93)], fill=INK, width=2*SCALE)
    if round_icon:
        mask = Image.new("L", (n, n), 0)
        ImageDraw.Draw(mask).ellipse((0,0,n-1,n-1), fill=255)
        im.putalpha(mask)
    return im.resize((size,size), Image.Resampling.LANCZOS)

SIZES = {"mdpi":48,"hdpi":72,"xhdpi":96,"xxhdpi":144,"xxxhdpi":192}
for flavor, formats in (("main", ("png",)), ("nightly", ("webp",))):
    base = ROOT / flavor / "res"
    for density, size in SIZES.items():
        folder = base / ("mipmap-" + density)
        folder.mkdir(parents=True, exist_ok=True)
        for round_icon in (False,True):
            name = "ic_launcher_round" if round_icon else "ic_launcher"
            for fmt in formats:
                out = folder / (name + "." + fmt)
                if fmt == "png": render(size,round_icon).save(out, optimize=True)
                else: render(size,round_icon).save(out, "WEBP", lossless=True, method=6)
        if flavor == "nightly":
            # Nightly adaptive foreground only (background belongs to Android's adaptive icon).
            render(size, transparent=True).save(folder / "ic_launcher_foreground.webp",
                                                  "WEBP", lossless=True, method=6)
render(512).save(ROOT / "main" / "ic_launcher-playstore.png", optimize=True)
print("Monogatari launcher icons generated for main, nightly, round and Play Store.")
