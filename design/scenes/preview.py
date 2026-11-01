#!/usr/bin/env python3
"""Generate a *review-only* looping GIF showing independent scene-layer motion.

Dependencies: Pillow. No Android code or user information.
Run: python design/scenes/preview.py
"""
from __future__ import annotations
from math import cos, pi, sin
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT=Path(__file__).resolve().parent
SCENES=('sleep','settle','breathe','tones')
WIDTH,HEIGHT=242,524
MARGIN=18
HEADER=44
PANEL_W=WIDTH+2*MARGIN
PANEL_H=HEIGHT+HEADER+MARGIN
BG=(7,16,27)


def load_scene(kind):
    directory=ROOT/kind
    background=Image.open(directory/'background.webp').convert('RGBA').resize((WIDTH,HEIGHT),Image.Resampling.LANCZOS)
    middle=Image.open(directory/'middle.png').convert('RGBA').resize((WIDTH,HEIGHT),Image.Resampling.LANCZOS)
    mist=Image.open(directory/'mist.png').convert('RGBA').resize((int(WIDTH*1.3),HEIGHT),Image.Resampling.LANCZOS)
    return background,middle,mist


def tinted(im,multiplier):
    result=im.copy()
    result.putalpha(im.getchannel('A').point(lambda v: round(v*multiplier)))
    return result


def frame(t,loaded,font):
    sheet=Image.new('RGB',(PANEL_W*2,PANEL_H*2),BG)
    draw=ImageDraw.Draw(sheet)
    for index,kind in enumerate(SCENES):
        x,y=(index%2)*PANEL_W,(index//2)*PANEL_H
        b,mid,mist=loaded[kind]
        composed=b.copy()
        # Source is 1.3x wider, so a centered placement allows drift with invisible edges.
        sway=round(11*sin(2*pi*t+index*0.82))
        composed.alpha_composite(mist,(-round((mist.width-WIDTH)/2)+sway,0))
        if kind=='breathe':
            # Controlled modulation, not rescaling / warping of the horizon silhouette.
            mid=tinted(mid,0.80+0.20*(1+sin(2*pi*t))/2)
        elif kind=='tones':
            mid=tinted(mid,0.60+0.40*(1+sin(2*pi*t+0.3))/2)
        composed.alpha_composite(mid,(0,0))
        sheet.paste(composed.convert('RGB'),(x+MARGIN,y+HEADER))
        draw.text((x+MARGIN,y+15),kind.upper(),font=font,fill=(213,228,241))
    return sheet


def main():
    try:
        font=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',16)
    except OSError:
        font=ImageFont.load_default()
    loaded={s:load_scene(s) for s in SCENES}
    steps=32
    frames=[frame(i/steps,loaded,font) for i in range(steps)]
    path=ROOT/'motion-preview.gif'
    frames[0].save(path,format='GIF',save_all=True,append_images=frames[1:],duration=120,loop=0,optimize=True,disposal=2)
    frames[8].save(ROOT/'motion-preview-still.png',optimize=True)
    print(f'Wrote {path} ({path.stat().st_size} bytes), 32 frames at 120ms each')

if __name__=='__main__':main()