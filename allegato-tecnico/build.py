#!/usr/bin/env python3

import argparse
import re
import shutil
import subprocess
import sys
import tempfile
import urllib.request
from pathlib import Path

from PIL import Image
from pypdf import PdfReader, PdfWriter, Transformation

# ─── Configuration ────────────────────────────────────────────────────────────

ROOT          = Path(__file__).parent.resolve()
DIAGRAMS      = ROOT / "diagrams"
IMG           = ROOT / "img"
OUT_DIR       = ROOT / "out"
MAIN_TEX      = ROOT / "main.tex"
JOBNAME       = "Allegato Tecnico"

PLANTUML_JAR  = ROOT / "plantuml.jar"

DIAGRAM_GROUPS = ["domain_model", "ssd", "dsd", "dcd"]

MANUALLY_MAINTAINED = {
    "domain_model/domain_model.uxf",
    "dcd/dcd.uxf",
}

PLANTUML_URL = (
    "https://github.com/plantuml/plantuml/releases/download/"
    "v1.2024.6/plantuml-1.2024.6.jar"
)

# ─── Helpers ──────────────────────────────────────────────────────────────────

def run(cmd: list, cwd: Path = None) -> None:
    print("  $", " ".join(str(c) for c in cmd))
    result = subprocess.run(cmd, cwd=cwd)
    if result.returncode != 0:
        print(f"  [ERROR] command failed (exit {result.returncode})")
        sys.exit(result.returncode)


def find_java() -> str:
    java = shutil.which("java")
    if not java:
        print("[ERROR] 'java' not found on PATH. Install Java to continue.")
        sys.exit(1)
    return java


def find_plantuml() -> list:
    if shutil.which("plantuml"):
        return ["plantuml"]
    java = find_java()
    if not PLANTUML_JAR.exists():
        print(f"  plantuml.jar not found. Downloading…")
        print(f"  Source: {PLANTUML_URL}")
        try:
            urllib.request.urlretrieve(PLANTUML_URL, PLANTUML_JAR)
            print(f"  Saved to: {PLANTUML_JAR}")
        except Exception as e:
            print(f"  [ERROR] Download failed: {e}")
            sys.exit(1)
    return [java, "-jar", str(PLANTUML_JAR)]


UXF2PNG = ROOT / "uxf2png.py"


# ─── Diagram rendering ────────────────────────────────────────────────────────

DESTROY_MARK_RE = re.compile(
    r'<line style="stroke:#A80036;stroke-width:2\.0;" '
    r'x1="([\d.]+)" x2="([\d.]+)" y1="([\d.]+)" y2="([\d.]+)"/>'
)


def _enlarge_destroy_marks(svg_text: str, scale: float = 1.4) -> str:
   
    def grow(m):
        x1, x2, y1, y2 = (float(g) for g in m.groups())
        cx, cy = (x1 + x2) / 2, (y1 + y2) / 2
        x1 = cx + (x1 - cx) * scale
        x2 = cx + (x2 - cx) * scale
        y1 = cy + (y1 - cy) * scale
        y2 = cy + (y2 - cy) * scale
        return (f'<line style="stroke:#A80036;stroke-width:3.0;" '
                f'x1="{x1:.4f}" x2="{x2:.4f}" y1="{y1:.4f}" y2="{y2:.4f}"/>')

    return DESTROY_MARK_RE.sub(grow, svg_text)


TEXT_EL_RE = re.compile(r'<text([^>]*)>([^<]*)</text>')


def _is_guard_label(m) -> bool:
    
    attrs = m.group(1)
    return 'font-size="11"' in attrs and 'font-weight="bold"' in attrs


def _strip_guard_brackets(svg_text: str) -> str:
  
    matches = list(TEXT_EL_RE.finditer(svg_text))
    replacements = {}

    def rebuild(m, new_content):
        attrs = m.group(1)
        old_content = m.group(2)
        tl = re.search(r'textLength="([\d.]+)"', attrs)
        if tl and old_content:
            old_len = float(tl.group(1))
            new_len = old_len * len(new_content) / len(old_content)
            attrs = attrs[:tl.start()] + f'textLength="{new_len:.4f}"' + attrs[tl.end():]
        return f'<text{attrs}>{new_content}</text>'

    i = 0
    while i < len(matches):
        if _is_guard_label(matches[i]) and matches[i].group(2).startswith("["):
            j = i
            while j < len(matches) and _is_guard_label(matches[j]) and not matches[j].group(2).endswith("]"):
                j += 1
            if j < len(matches) and _is_guard_label(matches[j]):
                first, last = matches[i], matches[j]
                if i == j:
                    replacements[i] = rebuild(first, first.group(2)[1:-1])
                else:
                    replacements[i] = rebuild(first, first.group(2)[1:])
                    replacements[j] = rebuild(last, last.group(2)[:-1])
                i = j
        i += 1

    if not replacements:
        return svg_text
    out = []
    last_end = 0
    for idx, m in enumerate(matches):
        if idx in replacements:
            out.append(svg_text[last_end:m.start()])
            out.append(replacements[idx])
            last_end = m.end()
    out.append(svg_text[last_end:])
    return "".join(out)


UI_BOX_RE = re.compile(
    r'<rect fill="#E2E2F0" height="([\d.]+)" rx="2\.5" ry="2\.5" '
    r'style="stroke:#181818;stroke-width:0\.5;" width="([\d.]+)" '
    r'x="([\d.]+)" y="([\d.]+)"/>'
    r'<text fill="#000000" font-family="sans-serif" font-size="14" '
    r'lengthAdjust="spacing" textLength="[\d.]+" x="[\d.]+" y="[\d.]+">UI</text>'
)


def _draw_ui_as_package(svg_text: str) -> str:
   
    def repl(m):
        height, width, x, y = (float(g) for g in m.groups())
        bottom = y + height
        center = x + width / 2

        new_y = 5.0
        new_height = bottom - new_y
        new_width = 46.0
        new_x = center - new_width / 2

        tab_w = 20.0
        tab_h = 15.0
        body_y = new_y + tab_h
        body_h = new_height - tab_h

        text_x = new_x + tab_w / 2
        text_y = new_y + tab_h - 4

        return (
            f'<rect fill="#E2E2F0" height="{tab_h:.4f}" style="stroke:#181818;stroke-width:0.5;" '
            f'width="{tab_w:.4f}" x="{new_x:.4f}" y="{new_y:.4f}"/>'
            f'<rect fill="#E2E2F0" height="{body_h:.4f}" style="stroke:#181818;stroke-width:0.5;" '
            f'width="{new_width:.4f}" x="{new_x:.4f}" y="{body_y:.4f}"/>'
            f'<text fill="#000000" font-family="sans-serif" font-size="12" text-anchor="middle" '
            f'x="{text_x:.4f}" y="{text_y:.4f}">UI</text>'
        )

    return UI_BOX_RE.sub(repl, svg_text)


def render_puml(plantuml: list, src: Path, out_dir: Path) -> None:
   
    out_dir = out_dir.resolve()
    run([*plantuml, "-tsvg", "-failfast", "-o", str(out_dir), str(src)])
    svg_path = out_dir / (src.stem + ".svg")
    pdf_path = out_dir / (src.stem + ".pdf")
    svg_text = svg_path.read_text(encoding="utf-8")
    if "#A80036" in svg_text:
        svg_text = _enlarge_destroy_marks(svg_text)
    if src.parent.name == "ssd":
        svg_text = _strip_guard_brackets(svg_text)
    if src.parent.name == "dsd":
        svg_text = _draw_ui_as_package(svg_text)
    svg_path.write_text(svg_text, encoding="utf-8")
    run(["rsvg-convert", "-f", "pdf", "-o", str(pdf_path), str(svg_path)])
    svg_path.unlink(missing_ok=True)
    (out_dir / (src.stem + ".png")).unlink(missing_ok=True)


def render_uxf(src: Path, out_dir: Path) -> None:
    out_png = out_dir / (src.stem + ".png")
    run([sys.executable, str(UXF2PNG), str(src), str(out_png)])


def render_diagrams():
    plantuml = None
    total    = 0

    for group in DIAGRAM_GROUPS:
        src_dir = DIAGRAMS / group
        out_dir = IMG / group
        out_dir.mkdir(parents=True, exist_ok=True)

        puml_files = sorted(src_dir.glob("*.puml"))
        uxf_files  = sorted(src_dir.glob("*.uxf"))

        if not puml_files and not uxf_files:
            print(f"  [SKIP] {group}/ — no .puml or .uxf files")
            continue

        print(f"\n  Rendering {group}/  "
              f"({len(puml_files)} puml, {len(uxf_files)} uxf)…")

        if puml_files:
            if plantuml is None:
                plantuml = find_plantuml()
            for f in puml_files:
                render_puml(plantuml, f, out_dir)
                total += 1

        for f in uxf_files:
            rel = f.relative_to(DIAGRAMS).as_posix()
            if rel in MANUALLY_MAINTAINED:
                print(f"  [SKIP] {rel} — manually maintained, not auto-rendered")
                continue
            render_uxf(f, out_dir)
            total += 1

    print(f"\n  {total} diagram/s rendered → img/")
    build_titled_diagrams()


# ─── Titled composite diagram pages ───────────────────────────────────────────

def pdf_page_size(pdf_path: Path):
    result = subprocess.run(["pdfinfo", str(pdf_path)], capture_output=True, text=True)
    if result.returncode != 0:
        sys.exit(f"[ERROR] pdfinfo failed on {pdf_path}")
    for line in result.stdout.splitlines():
        if line.startswith("Page size:"):
            parts = line.split()
            return float(parts[2]), float(parts[4])
    sys.exit(f"[ERROR] could not read page size from {pdf_path}")


TITLED_TEX_TEMPLATE = r"""\documentclass[11pt]{{report}}
\usepackage[utf8]{{inputenc}}
\usepackage[T1]{{fontenc}}
\usepackage[italian]{{babel}}
\usepackage{{lmodern}}
\usepackage{{geometry}}
\geometry{{paperwidth={width}pt, paperheight={height}pt, margin={margin}pt, top=0.4cm, bottom=0.4cm}}
\usepackage{{graphicx}}
\usepackage{{titlesec}}
\titleformat{{\chapter}}[hang]{{\LARGE\bfseries}}{{}}{{0pt}}{{}}
\titlespacing*{{\chapter}}{{0pt}}{{0pt}}{{0.8em}}
\pagestyle{{empty}}
\setlength{{\parindent}}{{0pt}}
\begin{{document}}
\setcounter{{chapter}}{{{chapter_minus_one}}}
\chapter{{{title}}}
\vspace{{0.4em}}
{image_inclusion}
\end{{document}}
"""


def generate_titled_image_page(image_path: Path, out_pdf: Path, title: str, chapter_num: int,
                               page_width: float, image_height: float = None, margin_pt: float = 22.68) -> None:
    if not image_path.exists():
        print(f"  [SKIP] {image_path.name} not found — skipping {out_pdf.name}")
        return

    pdflatex = shutil.which("pdflatex") or "/Library/TeX/texbin/pdflatex"
    pdfcrop = shutil.which("pdfcrop") or "/Library/TeX/texbin/pdfcrop"

    if image_height is not None:
        image_inclusion = (f"\\begin{{center}}\n"
                           f"\\includegraphics[height={image_height}pt]{{{image_path.as_posix()}}}\n"
                           f"\\end{{center}}")
    else:
        image_inclusion = f"\\noindent\\includegraphics[width=\\linewidth]{{{image_path.as_posix()}}}"

    with tempfile.TemporaryDirectory() as tmp:
        tmp = Path(tmp)

        def compile_tex(name: str, h: float) -> Path:
            tex_file = tmp / f"{name}.tex"
            tex_file.write_text(TITLED_TEX_TEMPLATE.format(
                width=page_width, height=h, margin=margin_pt,
                chapter_minus_one=chapter_num - 1, title=title,
                image_inclusion=image_inclusion
            ), encoding="utf-8")
            subprocess.run([pdflatex, "-interaction=nonstopmode", f"{name}.tex"],
                           cwd=tmp, capture_output=True)
            return tmp / f"{name}.pdf"

        # Pass 1: compile oversized, measure content height via pdfcrop
        pdf1 = compile_tex("measure", 900.0)
        cropped = tmp / "measure_cropped.pdf"
        subprocess.run([pdfcrop, "--margins", "0 0 0 0", str(pdf1), str(cropped)],
                       cwd=tmp, capture_output=True)
        _, content_h_bp = pdf_page_size(cropped)
        content_h = content_h_bp * 72.27 / 72.0
        top_bottom_margins = 2 * 0.4 * 28.3465
        final_height = content_h + top_bottom_margins + 20

        # Pass 2: recompile at exact final size
        pdf2 = compile_tex("final", final_height)
        out_pdf.parent.mkdir(parents=True, exist_ok=True)
        out_pdf.write_bytes(pdf2.read_bytes())
        print(f"  Generated {out_pdf.relative_to(ROOT)} ({page_width:.1f}x{final_height:.1f}pt)")


SSD_TITLE_TEX_TEMPLATE = r"""\documentclass[11pt]{{report}}
\usepackage[utf8]{{inputenc}}
\usepackage[T1]{{fontenc}}
\usepackage[italian]{{babel}}
\usepackage{{lmodern}}
\usepackage{{geometry}}
\geometry{{paperwidth={width}pt, paperheight={raw_height}pt, margin={margin}, top=2pt, bottom=2pt}}
\pagestyle{{empty}}
\setlength{{\parindent}}{{0pt}}
\begin{{document}}
\setcounter{{chapter}}{{{chapter}}}
\setcounter{{section}}{{{section}}}
\section*{{{title}}}
\end{{document}}
"""


def generate_ssd_ext1_titled() -> None:
    diagram_pdf = IMG / "ssd" / "ssd_ext1.pdf"
    out_pdf = IMG / "ssd" / "ssd_ext1_titled.pdf"

    if not diagram_pdf.exists():
        print(f"  [SKIP] {diagram_pdf.name} not found — skipping ssd_ext1_titled.pdf")
        return

    pdflatex = shutil.which("pdflatex") or "/Library/TeX/texbin/pdflatex"
    page_width = 595.276
    title_margin = "1cm"
    diagram_margin = 28.35
    diagram_max_width = page_width - 2 * diagram_margin
    title_raw_height = 250
    title_page_height = 18
    top_margin = 24
    bottom_margin = 24
    title = r"Passo 1 --- Estensioni \& Eccezioni 1a, 1b, 1c, 1d, 1e, 1f, 1g, 1h, 1i"

    diag_reader = PdfReader(str(diagram_pdf))
    diag_page = diag_reader.pages[0]
    dw = float(diag_page.mediabox.width)
    dh = float(diag_page.mediabox.height)

    diag_scale = 1.0
    if dw > diagram_max_width:
        diag_scale = diagram_max_width / dw
        dw *= diag_scale
        dh *= diag_scale

    with tempfile.TemporaryDirectory() as tmp:
        tmp = Path(tmp)
        tex_path = tmp / "title.tex"
        tex_path.write_text(SSD_TITLE_TEX_TEMPLATE.format(
            width=page_width, raw_height=title_raw_height, margin=title_margin,
            chapter=3, section=1, title=title
        ), encoding="utf-8")

        subprocess.run([pdflatex, "-interaction=nonstopmode", "title.tex"],
                       cwd=tmp, capture_output=True)
        title_page = PdfReader(str(tmp / "title.pdf")).pages[0]

        w = page_width
        gap = 10
        h = top_margin + title_page_height + gap + dh + bottom_margin

        writer = PdfWriter()
        new_page = writer.add_blank_page(width=w, height=h)
        new_page.merge_transformed_page(
            diag_page, Transformation().scale(diag_scale).translate(
                tx=(w - dw) / 2, ty=bottom_margin))
        new_page.merge_transformed_page(
            title_page, Transformation().translate(
                tx=0, ty=h - title_raw_height - top_margin))

        out_pdf.parent.mkdir(parents=True, exist_ok=True)
        with open(out_pdf, "wb") as f:
            writer.write(f)
        print(f"  Generated {out_pdf.relative_to(ROOT)} ({w:.1f}x{h:.1f}pt)")


def build_titled_diagrams():
    print("\n  Building titled composite pages (DCD, Domain Model, SSD ext1)…")
    # DCD
    generate_titled_image_page(
        image_path=IMG / "dcd" / "dcd.png",
        out_pdf=IMG / "dcd" / "dcd_titled.pdf",
        title="Diagramma delle Classi di Progetto (DCD)",
        chapter_num=5,
        page_width=841.89,
    )
    # Domain Model
    generate_titled_image_page(
        image_path=IMG / "domain_model" / "domain_model.png",
        out_pdf=IMG / "domain_model" / "domain_model_titled.pdf",
        title="Modello di Dominio",
        chapter_num=2,
        page_width=595.276,
        image_height=463.67,
    )
    # SSD Ext 1
    generate_ssd_ext1_titled()


# ─── LaTeX compilation ────────────────────────────────────────────────────────

def compile_latex(tex_file: Path = None):
    if tex_file is None:
        tex_file = MAIN_TEX

    pdflatex = shutil.which("pdflatex")
    if not pdflatex:
        print("[ERROR] pdflatex not found. Install TeX Live or MacTeX.")
        sys.exit(1)

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    cmd = [
        pdflatex,
        "-interaction=nonstopmode",
        "-halt-on-error",
        f"-output-directory={OUT_DIR.name}",
        f"-jobname={JOBNAME}",
        tex_file.name,
    ]
    log = OUT_DIR / f"{JOBNAME}.log"

    for i in range(1, 3):
        print(f"\n  LaTeX pass {i}/2…")
        print("  $", " ".join(str(c) for c in cmd))
        result = subprocess.run(cmd, cwd=ROOT)
        if result.returncode != 0:
            print(f"\n[ERROR] pdflatex failed on pass {i}/2. Relevant errors:")
            if log.exists():
                lines = log.read_text(errors="replace").splitlines()
                errors = [l for l in lines if l.startswith("!") or "Error" in l]
                print("\n".join(errors[-20:]) if errors else "\n".join(lines[-30:]))
            sys.exit(result.returncode)

    out_pdf = OUT_DIR / f"{JOBNAME}.pdf"
    size_kb = out_pdf.stat().st_size // 1024 if out_pdf.exists() else 0
    print(f"\n  Output: {out_pdf}  ({size_kb} KB)")


# ─── Main ─────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(description="SAS 2025/26 — build script")
    group = parser.add_mutually_exclusive_group()
    group.add_argument("--diagrams", action="store_true",
                       help="Render diagrams and build titled pages")
    group.add_argument("--titled", action="store_true",
                       help="Only rebuild titled composite pages (DCD, Domain Model, SSD ext1)")
    group.add_argument("--latex", action="store_true",
                       help="Only compile LaTeX (auto-builds titled pages if missing)")
    args = parser.parse_args()

    print("=" * 60)
    print("  Cat & Ring 2025/26 — SAS build")
    print("=" * 60)

    if args.diagrams:
        print("\n[1/1] Rendering diagrams and titled pages…")
        render_diagrams()
    elif args.titled:
        print("\n[1/1] Rebuilding titled composite pages…")
        build_titled_diagrams()
    elif args.latex:
        # Assicura che i file titled esistano prima della compilazione
        if not (IMG / "dcd" / "dcd_titled.pdf").exists() or \
           not (IMG / "domain_model" / "domain_model_titled.pdf").exists() or \
           not (IMG / "ssd" / "ssd_ext1_titled.pdf").exists():
            build_titled_diagrams()
        print("\n[1/1] Compiling LaTeX…")
        compile_latex(MAIN_TEX)
    else:
        print("\n[1/2] Rendering diagrams and titled pages…")
        render_diagrams()
        print("\n[2/2] Compiling LaTeX…")
        compile_latex(MAIN_TEX)

    print("\n  Done.\n")


if __name__ == "__main__":
    main()
