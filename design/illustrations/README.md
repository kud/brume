# Horizon educational illustrations

Twenty illustrations carefully extracted from the **approved Brume Horizon discovery board** into Android-sized masters. The artwork is intentionally restrained and descriptive, not a replacement for the app's navigation icons or Horizon mark.

## Deliverables

- `night/scale/*.png` (12 × 384 × 384): transparent pale mist outlines including the soft luminosity of the approved board.
- `night/learn/*.png` (8 × 640 × 480): the same nocturnal treatment for the learning cards.
- `day/scale/*.png` and `day/learn/*.png`: matching illustrations in dark `#2A3440` ink on **transparent** backgrounds; designed to be composited over the paper `#EEF1F4` ground.
- `line/scale/*.svg` (`viewBox="0 0 96 96"`) and `line/learn/*.svg` (`viewBox="0 0 160 120"`): flat single-colour traced silhouettes (paths in `currentColor`), without glow, blur or filters.
- `scale/*.svg` and `learn/*.svg`: the previous illustrations replaced by identical copies of these new line-only vector sources, retaining the original public file paths.
- `contact-sheet.png`: night/day review matrix; text appears only on the sheet, never in an individual asset.

Use appropriately accessible text-colour tinting at runtime; illustrations communicate objects through shape only. Night and day variants share the same artwork, cropped and extracted from the same approved board. Raster-to-vector tracing is an **approximation of outlines**, not an authored master SVG. SVG paths are filled outlines of strokes, not font or bitmap references. The night halo exists only in the PNGs.

The reference graphic is a composition; some faint details will be lost at 64 pixels. Validate visually at intended density/contrast in the final UI, especially traffic, concert and drums.

Decibel numbers are deliberately not in the illustrations; the app supplies measurements, context and explanatory copy. No real health data.
