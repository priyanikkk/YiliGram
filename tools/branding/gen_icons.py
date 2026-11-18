#!/usr/bin/env python3
"""Generate every YiliGram launcher / notification / splash asset from one logo.

Usage:
    python3 -m venv .venv && .venv/bin/pip install -r tools/branding/requirements.txt
    .venv/bin/python tools/branding/gen_icons.py [--logo branding/logo.png] [--preview out.png]

The logo is expected to be a light, roughly round mascot on a dark background
(the current branding/logo.png). The script:
  * segments the mascot silhouette and its dark facial features;
  * writes the colored mascot as the adaptive-icon foreground (all densities);
  * writes dark gradient backgrounds for the default icon and the alternative
    launcher icons offered by Settings → Appearance;
  * traces the silhouette into VectorDrawables for the Android 13+ monochrome
    (themed) icon and the status-bar notification icon;
  * writes legacy square / round PNG icons and the splash-screen drawables.

Re-run it whenever the logo changes; all outputs are overwritten.
"""
import argparse
import os
import sys

import numpy as np
from PIL import Image, ImageDraw
from scipy import ndimage as ndi
from skimage import measure

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
RES = os.path.join(ROOT, "TMessagesProj", "src", "main", "res")

DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}
SUPERSAMPLE = 4

# Mascot width inside the 108dp adaptive canvas. 52dp keeps the ear tips inside
# the 66dp safe-zone circle, so no launcher mask can clip them.
FOREGROUND_WIDTH_DP = 52.0
MONO_WIDTH_DP = 52.0
NOTIFICATION_WIDTH_DP = 22.0

# (key used by LauncherIconController, background gradient start, end, angle)
VARIANTS = {
    "default": ("#34302A", "#0B0A08", 315),  # graphite, matches the logo
    "2": ("#1A1A1A", "#000000", 270),        # Nox: pure black (AMOLED)
    "3": ("#8E5CFF", "#E0569F", 315),        # Premium: violet → pink
    "4": ("#11807F", "#0B3B4C", 315),        # Aqua: teal
    "5": ("#FF8A3D", "#D93A5A", 315),        # Turbo: orange → red
    "6": ("#7A5A36", "#3B2A18", 315),        # Vintage: sepia
}


def disk(r):
    a = np.arange(-r, r + 1) ** 2
    return np.add.outer(a, a) <= r * r


def hex_rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


# ---------------------------------------------------------------- segmentation

def segment(logo_path):
    src = Image.open(logo_path).convert("RGBA")
    s = SUPERSAMPLE
    big = src.resize((src.width * s, src.height * s), Image.LANCZOS)
    rgb = np.asarray(big.convert("RGB")).astype(float)
    lum = ndi.gaussian_filter(rgb.mean(2), 1.5)

    bright = lum > 90
    labels, _ = ndi.label(bright)
    cy, cx = lum.shape[0] // 2, lum.shape[1] // 2
    center_label = labels[cy, cx]
    if center_label == 0:
        sys.exit("Could not find the mascot at the center of the logo")
    sil = labels == center_label
    sil = ndi.binary_closing(sil, structure=disk(14))
    sil = ndi.binary_fill_holes(sil)
    sil = ndi.binary_opening(sil, structure=disk(6))
    soft = ndi.gaussian_filter(sil.astype(float), 3)  # anti-aliased alpha
    sil = soft > 0.5

    inner = ndi.binary_erosion(sil, structure=disk(10))
    feat = (lum < 70) & inner
    feat = ndi.binary_opening(feat, structure=disk(2))
    feat = ndi.gaussian_filter(feat.astype(float), 1.5) > 0.5

    ys, xs = np.nonzero(sil)
    bbox = (xs.min(), ys.min(), xs.max() + 1, ys.max() + 1)

    cutout = np.asarray(big).copy()
    cutout[..., 3] = (np.clip(soft * 2 - 0.5, 0, 1) * 255).astype(np.uint8)
    cutout = Image.fromarray(cutout).crop(bbox)
    mono = (sil & ~feat)[bbox[1]:bbox[3], bbox[0]:bbox[2]]
    return cutout, mono


# ---------------------------------------------------------------- raster output

def gradient(size, start, end, angle):
    """Linear gradient, angle in degrees as in Android <gradient> (0 = left→right, 90 = bottom→top)."""
    w, h = size
    t = np.radians(angle)
    dx, dy = np.cos(t), -np.sin(t)
    yy, xx = np.mgrid[0:h, 0:w]
    u = ((xx / max(w - 1, 1) - 0.5) * dx + (yy / max(h - 1, 1) - 0.5) * dy)
    u = (u - u.min()) / (u.max() - u.min())
    a, b = np.array(hex_rgb(start), float), np.array(hex_rgb(end), float)
    img = a + (b - a) * u[..., None]
    return Image.fromarray(np.dstack([img, np.full((h, w), 255.0)]).astype(np.uint8), "RGBA")


def foreground(cutout, px):
    canvas = Image.new("RGBA", (px, px), (0, 0, 0, 0))
    w = round(px * FOREGROUND_WIDTH_DP / 108.0)
    h = round(w * cutout.height / cutout.width)
    cat = cutout.resize((w, h), Image.LANCZOS)
    canvas.alpha_composite(cat, ((px - w) // 2, (px - h) // 2))
    return canvas


def legacy_icon(cutout, variant, px, round_mask):
    # Legacy icons: 48dp, artwork drawn on a full-bleed 108dp canvas scaled to
    # 48 * 108/72 so the 72dp visible area of the adaptive icon fills the icon.
    s = SUPERSAMPLE
    full = round(px * 108 / 72) * s
    start, end, angle = VARIANTS[variant]
    img = gradient((full, full), start, end, angle)
    img.alpha_composite(foreground(cutout, full))
    off = (full - px * s) // 2
    img = img.crop((off, off, off + px * s, off + px * s))
    mask = Image.new("L", img.size, 0)
    d = ImageDraw.Draw(mask)
    if round_mask:
        d.ellipse((0, 0, img.width - 1, img.height - 1), fill=255)
    else:
        d.rounded_rectangle((0, 0, img.width - 1, img.height - 1), radius=img.width * 0.22, fill=255)
    img.putalpha(mask)
    return img.resize((px, px), Image.LANCZOS)


def intro_mark(mono, k):
    """White mascot for the first intro page (drawn by jni/intro on top of a colored disc).

    Same canvas as the upstream intro_tg_plane texture (82x74dp at mdpi), mascot centered.
    """
    w, h = round(82 * k), round(74 * k)
    s = SUPERSAMPLE
    alpha = Image.fromarray((mono * 255).astype(np.uint8))
    target_w = round(w * 0.82) * s
    target_h = round(target_w * mono.shape[0] / mono.shape[1])
    alpha = alpha.resize((target_w, target_h), Image.LANCZOS)
    canvas = Image.new("L", (w * s, h * s), 0)
    canvas.paste(alpha, ((w * s - target_w) // 2, (h * s - target_h) // 2))
    canvas = canvas.resize((w, h), Image.LANCZOS)
    img = Image.new("RGBA", (w, h), (255, 255, 255, 0))
    img.putalpha(canvas)
    return img


# ---------------------------------------------------------------- vector output

def trace(mask, width_dp, viewport):
    """Trace a boolean mask into VectorDrawable path data centered in a square viewport."""
    padded = np.pad(mask.astype(float), 2)
    contours = measure.find_contours(padded, 0.5)
    scale = width_dp / mask.shape[1]
    ox = (viewport - width_dp) / 2 - 2 * scale
    oy = (viewport - mask.shape[0] * scale) / 2 - 2 * scale
    parts = []
    for c in contours:
        if len(c) < 8:
            continue
        c = measure.approximate_polygon(c, tolerance=1.2)
        pts = [(ox + x * scale, oy + y * scale) for y, x in c]
        seg = "M%.2f,%.2f" % pts[0] + "".join("L%.2f,%.2f" % p for p in pts[1:]) + "Z"
        parts.append(seg)
    return "".join(parts)


def vector_xml(path_data, size_dp, viewport, color):
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        "<!-- Generated by tools/branding/gen_icons.py. Do not edit. -->\n"
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        f'    android:width="{size_dp}dp"\n'
        f'    android:height="{size_dp}dp"\n'
        f'    android:viewportWidth="{viewport}"\n'
        f'    android:viewportHeight="{viewport}">\n'
        "    <path\n"
        f'        android:fillColor="{color}"\n'
        '        android:fillType="evenOdd"\n'
        f'        android:pathData="{path_data}" />\n'
        "</vector>\n"
    )


# ---------------------------------------------------------------- xml helpers

GEN = "<!-- Generated by tools/branding/gen_icons.py. Do not edit. -->\n"


def gradient_xml(variant):
    start, end, angle = VARIANTS[variant]
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n' + GEN +
        '<shape xmlns:android="http://schemas.android.com/apk/res/android">\n'
        f'    <gradient android:type="linear" android:angle="{angle}"\n'
        f'        android:startColor="{start}" android:endColor="{end}" />\n'
        "</shape>\n"
    )


def splash_xml(with_window_bg):
    """Splash icon: mascot on a dark disc.

    Android 12+ (windowSplashScreenAnimatedIcon): a 288dp drawable whose visible
    area is the central 192dp circle. Older versions: the same layers on top of
    the window background, centered.
    """
    start, end, angle = VARIANTS["default"]
    window_bg = '    <item android:drawable="@android:color/white" />\n' if with_window_bg else ""
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n' + GEN +
        '<layer-list xmlns:android="http://schemas.android.com/apk/res/android">\n'
        + window_bg +
        '    <item android:gravity="center" android:width="192dp" android:height="192dp">\n'
        '        <shape android:shape="oval">\n'
        f'            <gradient android:type="linear" android:angle="{angle}"\n'
        f'                android:startColor="{start}" android:endColor="{end}" />\n'
        "        </shape>\n"
        "    </item>\n"
        '    <item android:gravity="center" android:width="288dp" android:height="288dp"\n'
        '        android:drawable="@mipmap/icon_foreground" />\n'
        "</layer-list>\n"
    )


def write(path, data):
    path = os.path.join(RES, path)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    if isinstance(data, Image.Image):
        if path.endswith(".webp"):
            data.save(path, "WEBP", lossless=True, quality=100, method=6)
        else:
            data.save(path, optimize=True)
    else:
        with open(path, "w", encoding="utf-8") as f:
            f.write(data)


# ---------------------------------------------------------------- main

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--logo", default=None)
    ap.add_argument("--preview", default=None, help="also write a contact sheet PNG here")
    args = ap.parse_args()
    logo = args.logo
    if logo is None:
        for name in ("logo.png", "logog.png"):
            p = os.path.join(ROOT, "branding", name)
            if os.path.exists(p):
                logo = p
                break
    if not logo or not os.path.exists(logo):
        sys.exit("Logo not found; pass --logo")

    cutout, mono = segment(logo)
    transparent = lambda px: Image.new("RGBA", (px, px), (0, 0, 0, 0))

    for d, k in DENSITIES.items():
        fg_px = round(108 * k)
        fg = foreground(cutout, fg_px)
        # One foreground shared by every launcher icon variant (see mipmap-anydpi-v26/*.xml).
        write(f"mipmap-{d}/icon_foreground.png", fg)
        # The upstream gloss overlays are part of the Telegram look; replace with no-ops.
        write(f"mipmap-{d}/icon_background_clip.png", transparent(fg_px))
        write(f"mipmap-{d}/icon_background_clip_round.png", transparent(fg_px))
        write(f"mipmap-{d}/icon_2_background_sa.png", gradient((fg_px, fg_px), *VARIANTS["2"]))

        lg_px = round(48 * k)
        for variant, prefix in (("default", "ic"), ("2", "icon_2"), ("3", "icon_3"),
                                ("4", "icon_4"), ("5", "icon_5"), ("6", "icon_6")):
            name = "ic_launcher" if prefix == "ic" else f"{prefix}_launcher"
            write(f"mipmap-{d}/{name}.png", legacy_icon(cutout, variant, lg_px, False))
            write(f"mipmap-{d}/{name}_round.png", legacy_icon(cutout, variant, lg_px, True))
        write(f"drawable-{d}/ic_launcher_dr.webp", legacy_icon(cutout, "default", lg_px, True))

        if d != "xxxhdpi":  # upstream ships the intro textures up to xxhdpi only
            write(f"drawable-{d}/intro_tg_plane.webp", intro_mark(mono, k))

    write("drawable/icon_background_clip.webp", transparent(432))
    write("drawable/icon_background_clip_round.webp", transparent(432))

    write("drawable/icon_background_sa.xml", gradient_xml("default"))
    for v in ("3", "4", "5", "6"):
        write(f"drawable/icon_{v}_background_sa.xml", gradient_xml(v))

    write("drawable/ic_launcher_monochrome.xml",
          vector_xml(trace(mono, MONO_WIDTH_DP, 108), 108, 108, "#FFFFFFFF"))
    write("drawable/notification.xml",
          vector_xml(trace(mono, NOTIFICATION_WIDTH_DP, 24), 24, 24, "#FFFFFFFF"))
    write("drawable/yili_splash_icon.xml", splash_xml(False))
    write("drawable/yili_splash_window.xml", splash_xml(True))

    if args.preview:
        sheet = Image.new("RGBA", (6 * 200 + 220, 420), (240, 240, 240, 255))
        for i, (variant, prefix) in enumerate((("default", "ic"), ("2", "icon_2"), ("3", "icon_3"),
                                               ("4", "icon_4"), ("5", "icon_5"), ("6", "icon_6"))):
            sheet.alpha_composite(legacy_icon(cutout, variant, 192, False), (i * 200 + 4, 4))
            sheet.alpha_composite(legacy_icon(cutout, variant, 192, True), (i * 200 + 4, 210))
        m = Image.fromarray((mono * 255).astype(np.uint8)).resize((200, round(200 * mono.shape[0] / mono.shape[1])))
        sheet.paste(Image.new("RGBA", m.size, (40, 40, 40, 255)), (1210, 100), m)
        sheet.save(args.preview)
    print("Icons written to", RES)


if __name__ == "__main__":
    main()
