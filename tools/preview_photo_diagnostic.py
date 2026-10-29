#!/usr/bin/env python3
"""Compare a viewfinder screenshot and its saved photo without changing either file.

Usage: python tools/preview_photo_diagnostic.py preview.png photo.jpg --out comparison
Requires Pillow and NumPy. Crop the preview to the image area (exclude controls)
before invoking. Keep lens, exposure, white balance, engine and framing fixed.
The report is a measurement aid, not an automatic camera color calibration.
"""
import argparse
import json
from pathlib import Path

import numpy as np
from PIL import Image, ImageOps


def load_center(path, size=512):
    with Image.open(path) as source:
        image = ImageOps.exif_transpose(source).convert("RGB")
        width, height = image.size
        side = min(width, height)
        # The central square reduces differences in UI crop and lens framing.
        image = image.crop(((width-side)//2, (height-side)//2,
                            (width+side)//2, (height+side)//2))
        return np.asarray(image.resize((size, size), Image.Resampling.LANCZOS), dtype=np.uint8)


def statistics(rgb):
    pixels = rgb.reshape(-1, 3).astype(np.float32) / 255.0
    luma = pixels @ np.array([.2126, .7152, .0722], dtype=np.float32)
    # Exclude near-black and near-white pixels when estimating a color cast.
    mid = pixels[(luma > .08) & (luma < .92)]
    return {
        "rgb_median": np.median(mid if len(mid) else pixels, axis=0).round(4).tolist(),
        "luma_p10_p50_p90": np.percentile(luma, [10, 50, 90]).round(4).tolist(),
        "display_clipped_percent": round(float(np.mean(luma >= .98) * 100), 3),
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("preview", type=Path)
    parser.add_argument("photo", type=Path)
    parser.add_argument("--out", type=Path, default=Path("preview-photo-comparison"))
    parser.add_argument("--engine", default="unknown")
    parser.add_argument("--lens", default="unknown")
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)
    preview = load_center(args.preview)
    photo = load_center(args.photo)
    before = statistics(preview)
    after = statistics(photo)
    report = {
        "engine": args.engine, "lens": args.lens,
        "preview": before, "saved_photo": after,
        "photo_minus_preview_luma_p10_p50_p90":
            np.round(np.array(after["luma_p10_p50_p90"]) - before["luma_p10_p50_p90"], 4).tolist(),
        "caveat": "Center crops are not registered. Scene movement, cropping, exposure, HDR merge and ISP local tone mapping can all change these statistics."
    }
    (args.out / "report.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    Image.fromarray(np.concatenate((preview, photo), axis=1)).save(args.out / "before-after.png")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
