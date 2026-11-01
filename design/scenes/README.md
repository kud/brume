# Brume · Écouter scene artwork

Horizon-inspired nocturnal scene layers and individual sound textures for the Android app.
No text or UI is baked into the artwork; scene motion belongs to the app.

## Four scenes

| Scene | Backdrop | Middle | Mist | Motion concept |
|---|---|---|---|---|
| `sleep` · Dormir | Dark sky | Low horizon and fine crescent | Broad, low mist veil | Very slow drift |
| `settle` · Se poser | Quiet blue-black | Flat waterline | Light mist lifting in two banks | A gradual rise |
| `breathe` · Respirer | Very dark sky | Single, shallow swell | One separate, low veil | Slow in/out |
| `tones` · Tons | Dark blue-black | Four short separated light traces | Faint horizontal mist | Independent dissolves |

Every scene has three compositing assets, in back-to-front order:

1. `background.webp` — opaque RGB, **1080 × 2340** pixels, clean low-chroma gradient only.
2. `mist.png` — transparent, **1404 × 2340** pixels (1.3× screen width); centre this layer so that **162 px** overhang on either side is available for translation. It has faded horizontal ends to avoid visible clipping while slowly drifting.
3. `middle.png` — transparent, **1080 × 2340** pixels; the main line, crescent, swell or tone marks.

All visible illustration is in the lower 60% of the screen. Title (25–35%) and disc (40–60%) regions are reserved and stay intentionally dim. Keep the central layout dark; avoid multiplying glow layers or raising their opacity at night. Some subtly misted elements can cross the disc's outer edge but should not compete with its text.

## Sound textures

`thumbs/*.png` are **512 × 512**, RGBA-style indexed PNGs with transparency and a single cool-blue-grey ink. They are intentionally *textures*, not objects: brown has two slow swells, pink irregular layers, white a fine grain, grey almost-flat lines, rain diagonal marks, surf a rolling crest, wind drifting horizontal streaks.

Use the **same file** for 40 dp circular crops and wider ~160 × 88 dp cards. Crop by the app's masks; do not stretch or pre-mask the images. Shape and luminance distinguish each texture without relying on hue.

## References and verification

`contact-sheet.png` previews all four scenes composited, their separated layers, and the seven textures in circular crops. PNGs are paletted with alpha to preserve mist soft falloff while keeping APK size low; backgrounds use WebP. All assets are self-contained and work without network or real user data.

No Compose screens or playback logic are modified in this artwork-only branch.
