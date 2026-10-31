# Illustrations — Horizon

Twenty single-colour vector illustrations for Brume's educational screens.

- `scale/`: twelve everyday sound contexts, each with `viewBox="0 0 96 96"`, intended to remain legible at **48 dp**.
- `learn/`: eight editorial spot illustrations, each with `viewBox="0 0 160 120"`.
- Every illustration is an independent, transparent SVG. No text, noise level, gradient, glow, photograph, filter or background is embedded.
- All visible paths use `stroke="currentColor"` so the app can tint them with the current foreground colour for night and day. Use the same file on both surfaces.
- Stroke caps and joins are rounded. The uniform line language is related to the Horizon icon family; these are **illustrations**, not replacements for UI/navigation icons.
- `contact-sheet.png`: reference only; a comparison on night and dawn backgrounds. The SVGs remain the sources of truth.

## Use

Set the CSS `color` of the containing element to the appropriate text colour; for Android, convert these source paths into tintable VectorDrawables as needed, retaining their aspect ratio and padding. Do not embed specific dB numbers in the illustration: those are content, supplied and formatted by the app.

The scale illustrations depict illustrative *sound sources*, not calibrated measurements. Listening-time advice requires context, source and qualification; it must not be inferred from the hourglass illustration.

No app logic or pre-existing icons are changed in this branch.
