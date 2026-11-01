#!/usr/bin/env python3
"""Validate Brume's layered scene artwork without loading or altering the Android app.

Requires Pillow: python -m pip install Pillow
Run from anywhere: python design/scenes/validate.py
"""
from pathlib import Path
from PIL import Image, ImageStat
import hashlib

ROOT = Path(__file__).resolve().parent
SCENES = ('sleep', 'settle', 'breathe', 'tones')
THUMBS = ('brown', 'pink', 'white', 'grey', 'rain', 'surf', 'wind')
W, H, MIST_W = 1080, 2340, 1404
errors = []


def check(ok, description):
    if not ok:
        errors.append(description)


def open_image(path):
    try:
        with Image.open(path) as source:
            image = source.copy()
        return image
    except Exception as exc:
        errors.append(f'Cannot open {path.relative_to(ROOT)}: {exc}')
        return None


def alpha_region(image, box):
    alpha = image.convert('RGBA').getchannel('A')
    return alpha.crop(box).getextrema()[1]


def visible_bbox(image, threshold=20):
    alpha = image.convert('RGBA').getchannel('A')
    return alpha.point(lambda p: 255 if p >= threshold else 0).getbbox()


for scene in SCENES:
    path = ROOT / scene
    bg = open_image(path / 'background.webp')
    middle = open_image(path / 'middle.png')
    mist = open_image(path / 'mist.png')
    if any(image is None for image in (bg, middle, mist)):
        continue
    check(bg.size == (W,H), f'{scene}: background wrong size {bg.size}')
    check(bg.mode in ('RGB','P') and 'transparency' not in bg.info,
          f'{scene}: background is not opaque')
    check(middle.size == (W,H), f'{scene}: middle wrong size {middle.size}')
    check(mist.size == (MIST_W,H), f'{scene}: mist wrong size {mist.size}')
    check('A' in middle.convert('RGBA').getbands() and middle.info.get('transparency') is not None or middle.mode == 'RGBA',
          f'{scene}: middle has no transparent pixel encoding')
    check('A' in mist.convert('RGBA').getbands() and mist.info.get('transparency') is not None or mist.mode == 'RGBA',
          f'{scene}: mist has no transparent pixel encoding')
    for name,layer in [('middle',middle),('mist',mist)]:
        box = visible_bbox(layer)
        check(box is not None, f'{scene}: {name} is empty')
        if box:
            check(box[1] >= 0.60*H, f'{scene}: {name} overlaps the protected UI region ({box})')
    # The mist has 162px of overhang on each side; even a moderate drift should never reveal a hard edge.
    check(alpha_region(mist,(0,0,1,H)) == 0 and alpha_region(mist,(MIST_W-1,0,MIST_W,H)) == 0,
          f'{scene}: mist has visible pixels on its outer edges')
    check(alpha_region(middle,(0,0,W,int(0.6*H))) < 20,
          f'{scene}: main layer is too bright in the title/play-disc zone')
    check(alpha_region(mist,(0,0,MIST_W,int(0.6*H))) < 20,
          f'{scene}: mist is too bright in the title/play-disc zone')
    # Background must also remain dim enough for bedtime use; RGB max is intentionally low centrally.
    center=bg.convert('RGB').crop((W//4,int(.4*H),3*W//4,int(.6*H)))
    max_brightness=max(ImageStat.Stat(center).mean)
    check(max_brightness < 50, f'{scene}: centre background unexpectedly bright ({max_brightness:.1f})')
    print(f'{scene:7s} background={bg.size} middle={middle.size} mist={mist.size} '
          f'mid_bbox={visible_bbox(middle)} mist_bbox={visible_bbox(mist)}')

seen=set()
for name in THUMBS:
    image=open_image(ROOT/'thumbs'/f'{name}.png')
    if image is None: continue
    check(image.size==(512,512), f'{name}: wrong thumbnail size {image.size}')
    alpha=image.convert('RGBA').getchannel('A')
    check(alpha.getextrema()[0] == 0, f'{name}: no transparent pixels')
    crop=image.convert('RGBA').crop((116,116,396,396)).resize((40,40),Image.Resampling.LANCZOS)
    digest=hashlib.sha256(crop.tobytes()).hexdigest()
    check(digest not in seen, f'{name}: duplicated 40dp texture crop')
    seen.add(digest)
    check(alpha.getbbox() is not None, f'{name}: empty texture')
    print(f'{name:7s} thumbnail={image.size} 40px_crop_sha256={digest[:12]}')

if errors:
    for error in errors:
        print(f'FAIL: {error}')
    raise SystemExit(f'Validation failed: {len(errors)} issue(s)')
print('PASS: 4 backgrounds, 8 transparent layers, 7 distinct sound textures.')