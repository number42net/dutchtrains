#!/usr/bin/env python3
"""Crop the bottom 25% from screenshots and scale to 50%."""

import sys
from pathlib import Path
from PIL import Image

CROP_FRACTION = 0.75
SCALE = 0.5

paths = [Path(p) for p in sys.argv[1:]]
images = [Image.open(p).convert("RGB") for p in paths]

crop_height = round(images[0].size[1] * CROP_FRACTION)

for path, img in zip(paths, images):
    orig_w, orig_h = img.size
    new_w = round(orig_w * SCALE)
    new_h = round(crop_height * SCALE)
    img.crop((0, 0, orig_w, crop_height)).resize((new_w, new_h), Image.LANCZOS).save(path)
    print(f"  {path.name}: {orig_w}×{orig_h}px → {new_w}×{new_h}px")
