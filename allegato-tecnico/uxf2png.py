#!/usr/bin/env python3
"""
uxf2png.py — Convert a UMLet/Umletino UXF diagram to PNG.

Pipeline: UXF (XML) → SVG → PNG (via rsvg-convert)

Usage:
    python3 uxf2png.py input.uxf output.png
"""
import math
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

# ── Typography & geometry ──────────────────────────────────────────────────────
FONT          = "Liberation Sans, Arial, sans-serif"
FONT_MONO     = "Liberation Mono, Courier New, monospace"
CLASS_FS      = 11      # attribute font size (pt / px in SVG)
NAME_FS       = 12      # class-name font size
LINE_H        = 15      # line height inside class boxes
PAD           = 6       # horizontal padding inside class boxes
ARROW         = 10      # arrowhead size
DIAMOND       = 14      # diamond size
NOTE_FOLD     = 12      # folded corner size on notes
CANVAS_PAD    = 20      # white border around the whole diagram

# ── SVG helpers ───────────────────────────────────────────────────────────────

def esc(s: str) -> str:
    return (s.replace("&", "&amp;").replace("<", "&lt;")
             .replace(">", "&gt;").replace('"', "&quot;"))


def _arrowhead(x1, y1, x2, y2, filled=True, size=ARROW):
    """Open or filled triangle pointing toward (x2, y2)."""
    a = math.atan2(y2 - y1, x2 - x1)
    pts = [
        (x2, y2),
        (x2 - size * math.cos(a - math.pi / 6), y2 - size * math.sin(a - math.pi / 6)),
        (x2 - size * math.cos(a + math.pi / 6), y2 - size * math.sin(a + math.pi / 6)),
    ]
    pts_str = " ".join(f"{p[0]:.1f},{p[1]:.1f}" for p in pts)
    fill = "black" if filled else "white"
    return f'<polygon points="{pts_str}" fill="{fill}" stroke="black" stroke-width="1.2"/>'


def _diamond(x1, y1, x2, y2, filled=True, size=DIAMOND):
    """Filled or open diamond at (x2, y2) oriented toward (x1, y1)."""
    a = math.atan2(y1 - y2, x1 - x2)
    tip   = (x2, y2)
    back  = (x2 + size * 2 * math.cos(a), y2 + size * 2 * math.sin(a))
    left  = (x2 + size * math.cos(a) + size * 0.6 * math.cos(a + math.pi / 2),
             y2 + size * math.sin(a) + size * 0.6 * math.sin(a + math.pi / 2))
    right = (x2 + size * math.cos(a) + size * 0.6 * math.cos(a - math.pi / 2),
             y2 + size * math.sin(a) + size * 0.6 * math.sin(a - math.pi / 2))
    pts   = [tip, left, back, right]
    pts_str = " ".join(f"{p[0]:.1f},{p[1]:.1f}" for p in pts)
    fill = "black" if filled else "white"
    return f'<polygon points="{pts_str}" fill="{fill}" stroke="black" stroke-width="1.2"/>'


def _inheritance_tri(x1, y1, x2, y2, size=ARROW + 2):
    """Open inheritance triangle at (x2, y2)."""
    return _arrowhead(x1, y1, x2, y2, filled=False, size=size)


# ── Panel-attribute parsing ────────────────────────────────────────────────────

_META = re.compile(r'^(fg|bg|layer|style|fontsize|halign|valign)=', re.I)

def _clean_lines(raw: str):
    lines = []
    for ln in raw.replace("\\n", "\n").split("\n"):
        s = ln.strip()
        if _META.match(s) or s.startswith("\\"):
            continue
        lines.append(s)
    while lines and not lines[-1]:
        lines.pop()
    return lines


def _bg_color(raw: str, default="white") -> str:
    m = re.search(r'bg=([^\s\n]+)', raw)
    if not m:
        return default
    c = m.group(1).upper()
    return {"YELLOW": "#FFFFAA", "WHITE": "white", "BLACK": "black",
            "RED": "#FF8888", "GREEN": "#88FF88", "BLUE": "#8888FF"}.get(c, c)


def _fg_color(raw: str, default="black") -> str:
    m = re.search(r'fg=([^\s\n]+)', raw)
    if not m:
        return default
    c = m.group(1).lower()
    return {"blue": "#0000cc", "red": "#cc0000", "black": "black",
            "green": "#006600"}.get(c, c)


# ── Path parsing ───────────────────────────────────────────────────────────────

def _path(additional: str, bx, by):
    """Parse additional_attributes waypoints to absolute coordinates."""
    nums = [n.strip() for n in additional.replace(";", "\n").split() if n.strip()]
    coords = []
    try:
        for i in range(0, len(nums) - 1, 2):
            coords.append((bx + float(nums[i]), by + float(nums[i + 1])))
    except (ValueError, IndexError):
        pass
    return coords


# ── Relation label / multiplicity parsing ─────────────────────────────────────

def _parse_relation(raw: str):
    lt, m1, m2, label = "-", "", "", []
    for ln in raw.replace("\\n", "\n").split("\n"):
        s = ln.strip()
        if s.startswith("lt="):
            lt = s[3:]
        elif s.startswith("m1="):
            m1 = s[3:]
        elif s.startswith("m2="):
            m2 = s[3:]
        elif s and not _META.match(s):
            label.append(s)
    return lt, m1, m2, " ".join(label).strip()


def _lt_decode(lt: str):
    """Return (start_deco, end_deco, dashed) where deco in
       None / 'arrow' / 'filled_arrow' / 'diamond' / 'inherit'."""
    start = end = None
    dashed = lt.startswith(".")
    lt_clean = lt.lstrip(".").strip()

    # Composition / aggregation diamond at START
    if re.match(r"<{3,}", lt_clean):
        start = "diamond"
        lt_clean = re.sub(r"^<+", "", lt_clean).lstrip("-")
    # Inheritance at START
    elif lt_clean.startswith("<<"):
        start = "inherit"
        lt_clean = lt_clean[2:].lstrip("-")

    # Arrow / diamond at END
    if re.search(r">{3,}$", lt_clean):
        end = "diamond"
    elif lt_clean.endswith(">>"):
        end = "arrow"
    elif lt_clean.endswith(">"):
        end = "open_arrow"

    return start, end, dashed


def _mid(pts):
    n = len(pts)
    if n < 2:
        return pts[0] if pts else (0, 0)
    # Take the midpoint of the longest segment
    best_i, best_d = 0, 0
    for i in range(n - 1):
        d = math.hypot(pts[i+1][0]-pts[i][0], pts[i+1][1]-pts[i][1])
        if d > best_d:
            best_d, best_i = d, i
    mx = (pts[best_i][0] + pts[best_i+1][0]) / 2
    my = (pts[best_i][1] + pts[best_i+1][1]) / 2
    return mx, my


# ── Element renderers ─────────────────────────────────────────────────────────

def render_class(x, y, w, h, raw):
    lines = _clean_lines(raw)
    if not lines:
        return ""

    # Split on first '--' separator
    try:
        sep_idx = lines.index("--")
        name_lines  = lines[:sep_idx]
        attr_lines  = [l for l in lines[sep_idx+1:] if l != "--"]
    except ValueError:
        name_lines  = lines
        attr_lines  = []

    parts = []
    # Box border
    parts.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" '
                 f'fill="white" stroke="black" stroke-width="1.2"/>')

    header_h = LINE_H * len(name_lines) + PAD * 2

    # Class name(s)
    ty = y + PAD + NAME_FS
    for nl in name_lines:
        parts.append(f'<text x="{x + w/2:.1f}" y="{ty}" '
                     f'font-family="{FONT}" font-size="{NAME_FS}" '
                     f'font-weight="bold" text-anchor="middle" '
                     f'fill="black">{esc(nl)}</text>')
        ty += LINE_H

    # Separator
    sep_y = y + header_h
    parts.append(f'<line x1="{x}" y1="{sep_y}" x2="{x+w}" y2="{sep_y}" '
                 f'stroke="black" stroke-width="1.2"/>')

    # Attributes
    ty = sep_y + PAD + CLASS_FS
    for al in attr_lines:
        parts.append(f'<text x="{x + PAD}" y="{ty}" '
                     f'font-family="{FONT_MONO}" font-size="{CLASS_FS}" '
                     f'fill="black">{esc(al)}</text>')
        ty += LINE_H

    return "\n".join(parts)


def render_package(x, y, w, h, raw):
    lines = _clean_lines(raw)
    color = _fg_color(raw, "black")
    label = lines[0] if lines else ""
    tab_w = min(max(len(label) * 7 + 2 * PAD, 90), w * 0.6)
    tab_h = LINE_H + PAD
    parts = []
    # Folder-tab package outline: small tab top-left + body rectangle below/right.
    parts.append(f'<path d="M{x},{y+tab_h} L{x},{y} L{x+tab_w},{y} '
                 f'L{x+tab_w},{y+tab_h}" fill="none" stroke="{color}" stroke-width="1.2"/>')
    parts.append(f'<rect x="{x}" y="{y+tab_h}" width="{w}" height="{h-tab_h}" '
                 f'fill="none" stroke="{color}" stroke-width="1.2"/>')
    parts.append(f'<line x1="{x+tab_w}" y1="{y+tab_h}" x2="{x+w}" y2="{y+tab_h}" '
                 f'stroke="{color}" stroke-width="1.2"/>')
    parts.append(f'<text x="{x + PAD}" y="{y + PAD + NAME_FS - 2}" '
                 f'font-family="{FONT}" font-size="{NAME_FS}" '
                 f'fill="{color}">{esc(label)}</text>')
    return "\n".join(parts)


def render_note(x, y, w, h, raw):
    lines = _clean_lines(raw)
    bg = _bg_color(raw, "white")
    fold = NOTE_FOLD
    parts = []
    path = (f"M{x},{y} L{x+w-fold},{y} L{x+w},{y+fold} "
            f"L{x+w},{y+h} L{x},{y+h} Z")
    fold_path = f"M{x+w-fold},{y} L{x+w-fold},{y+fold} L{x+w},{y+fold}"
    parts.append(f'<path d="{path}" fill="{bg}" stroke="black" stroke-width="1.2"/>')
    parts.append(f'<path d="{fold_path}" fill="none" stroke="black" stroke-width="1.2"/>')
    ty = y + PAD + CLASS_FS + 2
    for ln in lines:
        parts.append(f'<text x="{x + PAD}" y="{ty}" '
                     f'font-family="{FONT}" font-size="{CLASS_FS - 1}" '
                     f'fill="black" font-style="italic">{esc(ln)}</text>')
        ty += LINE_H - 1
    return "\n".join(parts)


def render_relation(bx, by, bw, bh, raw, additional):
    lt, m1, m2, label = _parse_relation(raw)
    start_deco, end_deco, dashed = _lt_decode(lt)

    pts = _path(additional, bx, by)
    if len(pts) < 2:
        # Fallback: centre-to-centre (rarely needed)
        pts = [(bx + bw / 2, by), (bx + bw / 2, by + bh)]

    parts = []
    stroke_dash = 'stroke-dasharray="6,4"' if dashed else ""
    # Draw path segments
    for i in range(len(pts) - 1):
        x1, y1 = pts[i]
        x2, y2 = pts[i + 1]
        parts.append(f'<line x1="{x1:.1f}" y1="{y1:.1f}" x2="{x2:.1f}" y2="{y2:.1f}" '
                     f'stroke="black" stroke-width="1.2" {stroke_dash}/>')

    # Decorations at START (first segment)
    if start_deco and len(pts) >= 2:
        sx, sy = pts[0]
        nx, ny = pts[1]
        if start_deco == "diamond":
            parts.append(_diamond(nx, ny, sx, sy, filled=True))
        elif start_deco == "inherit":
            parts.append(_inheritance_tri(nx, ny, sx, sy))

    # Decorations at END (last segment)
    if end_deco and len(pts) >= 2:
        ex, ey = pts[-1]
        px, py = pts[-2]
        if end_deco == "diamond":
            parts.append(_diamond(px, py, ex, ey, filled=True))
        elif end_deco in ("arrow", "filled_arrow"):
            parts.append(_arrowhead(px, py, ex, ey, filled=True))
        elif end_deco == "open_arrow":
            parts.append(_arrowhead(px, py, ex, ey, filled=False))

    # Label at midpoint
    if label or m1 or m2:
        mx, my = _mid(pts)
        fs = CLASS_FS - 1
        if label:
            lbl = label.replace("<", "").replace(">", "").replace("^", "").replace("v","").strip()
            if lbl:
                parts.append(f'<rect x="{mx - len(lbl)*3:.0f}" y="{my-fs-1:.0f}" '
                              f'width="{len(lbl)*6:.0f}" height="{fs+4}" '
                              f'fill="white" opacity="0.8" rx="2"/>')
                parts.append(f'<text x="{mx:.1f}" y="{my:.1f}" '
                              f'font-family="{FONT}" font-size="{fs}" '
                              f'font-style="italic" text-anchor="middle" '
                              f'fill="black">{esc(lbl)}</text>')
        # Multiplicities near endpoints
        if m1 and len(pts) >= 1:
            sx, sy = pts[0]
            parts.append(f'<text x="{sx+5:.1f}" y="{sy-3:.1f}" '
                         f'font-family="{FONT}" font-size="{fs}" '
                         f'fill="black">{esc(m1)}</text>')
        if m2 and len(pts) >= 2:
            ex, ey = pts[-1]
            parts.append(f'<text x="{ex+5:.1f}" y="{ey-3:.1f}" '
                         f'font-family="{FONT}" font-size="{fs}" '
                         f'fill="black">{esc(m2)}</text>')

    return "\n".join(parts)


# ── Main conversion ────────────────────────────────────────────────────────────

def uxf_to_svg(uxf_path: Path) -> str:
    tree = ET.parse(uxf_path)
    root = tree.getroot()

    # zoom_level in UMLet: coordinates are stored at the file's zoom level.
    # We render 1:1.
    elements = root.findall("element")

    # Bounding box. A Relation's own <coordinates> w/h is UMLet's cached
    # bounding rect for its waypoints at the time it was last edited in the
    # UI -- if a waypoint is later nudged (by hand, or by a script) without
    # that cache being refreshed, the line can extend past it. Take the
    # actual absolute waypoints into account too, or such a relation gets
    # silently clipped by a canvas sized only from the stale declared box.
    max_x = max_y = 0
    for el in elements:
        coords = el.find("coordinates")
        if coords is None:
            continue
        ex = int(coords.findtext("x", "0"))
        ey = int(coords.findtext("y", "0"))
        ew = int(coords.findtext("w", "0"))
        eh = int(coords.findtext("h", "0"))
        max_x = max(max_x, ex + ew)
        max_y = max(max_y, ey + eh)
        if el.findtext("id", "") == "Relation":
            aa = el.findtext("additional_attributes", "")
            for (px, py) in _path(aa, ex, ey):
                # Multiplicity labels are drawn past the endpoint itself
                # (render_relation: text at px+5, extending further right
                # for the string width) -- pad generously so label text
                # can't be clipped by the canvas edge.
                max_x = max(max_x, px + 40)
                max_y = max(max_y, py + 20)

    W = max_x + CANVAS_PAD * 2
    H = max_y + CANVAS_PAD * 2

    defs = """<defs>
  <style>text { font-family: "Liberation Sans", Arial, sans-serif; }</style>
</defs>"""

    svg_parts = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" '
        f'viewBox="0 0 {W} {H}">',
        defs,
        f'<rect width="{W}" height="{H}" fill="white"/>',
    ]

    # Render order: packages first (background containers), then relations,
    # then boxes, then notes on top
    packages, relations, classes, notes = [], [], [], []

    for el in elements:
        el_id  = el.findtext("id", "")
        coords = el.find("coordinates")
        if coords is None:
            continue
        x  = int(coords.findtext("x", "0")) + CANVAS_PAD
        y  = int(coords.findtext("y", "0")) + CANVAS_PAD
        w  = int(coords.findtext("w", "0"))
        h  = int(coords.findtext("h", "0"))
        pa = el.findtext("panel_attributes", "")
        aa = el.findtext("additional_attributes", "")

        if el_id == "Relation":
            relations.append(render_relation(x, y, w, h, pa, aa))
        elif el_id == "UMLClass":
            classes.append(render_class(x, y, w, h, pa))
        elif el_id == "UMLNote":
            notes.append(render_note(x, y, w, h, pa))
        elif el_id == "UMLPackage":
            packages.append(render_package(x, y, w, h, pa))
        # Text and other elements: skip

    svg_parts += packages + relations + classes + notes
    svg_parts.append("</svg>")
    return "\n".join(svg_parts)


def convert(uxf_path: Path, png_path: Path):
    rsvg = shutil.which("rsvg-convert")
    if not rsvg:
        print("[ERROR] rsvg-convert not found on PATH.")
        sys.exit(1)

    svg_tmp = uxf_path.with_suffix(".svg")
    svg_content = uxf_to_svg(uxf_path)
    svg_tmp.write_text(svg_content, encoding="utf-8")

    png_path.parent.mkdir(parents=True, exist_ok=True)
    result = subprocess.run(
        [rsvg, "-f", "png", "-o", str(png_path), str(svg_tmp)],
        capture_output=True,
    )
    svg_tmp.unlink(missing_ok=True)

    if result.returncode != 0:
        print(f"[ERROR] rsvg-convert failed: {result.stderr.decode()}")
        sys.exit(1)

    size = png_path.stat().st_size // 1024
    print(f"  {uxf_path.name} → {png_path}  ({size} KB)")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("Usage: python3 uxf2png.py input.uxf output.png")
        sys.exit(1)
    convert(Path(sys.argv[1]), Path(sys.argv[2]))
