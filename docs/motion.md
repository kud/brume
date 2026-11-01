# Motion and layout stability

Brume is a relaxation app: it should feel extremely soft. Erwann on dev.21: « extrêmement douce… smooth… pas trop flashy… je veux pas des jumping de layout ». One spec, in `:design` as `Motion` (`design/.../Motion.kt`), used everywhere.

## The spec

| | Value | Used for |
| --- | --- | --- |
| Curve | `Motion.Ease`, cubic-bezier(0.37, 0, 0.63, 1), a symmetric ease-in-out | everything; never a spring, never overshoot |
| `QUICK` | 320 ms | a word or a mark changing: selection weight, a check, a hint fading |
| `CALM` | 560 ms | content swapping in place, a row growing, a pager settling |
| `SLOW` | 900 ms | a screen, a session or a scene giving way to another |

- **Cross-fade, don't slide.** Screens, tabs, pages and session start/stop cross-fade in place (`Crossfade`, `Motion.crossfade`). Nothing flies in from an edge, nothing pops in scale, no colour flashes. The scene pager is the one thing that travels, because the finger moves it; when it settles or is sent by a tap on a name it eases on `CALM`/`SLOW`, never a spring.
- **Reserve space.** A state that comes and goes keeps its room, faded by `calmAlpha`: the swipe hint, the volume hint, the journal's confirmation, the breathing rhythms under Meditate, a selection check. Words told chosen by weight are laid out in both weights at once (`WeightSwap`), so their width never changes. Text that varies by state reserves its longest form (two lines for a scene's title and purpose).
- **When room must change, grow slowly.** The mini player joining the floating bar expands over `CALM` (`Motion.enter`/`exit`), and the pages padded by the bar follow it frame by frame.
- **Tabular figures** for every number that ticks (`TextStyle.tabular()`): timers, levels, counts, positions.
- **Soft touch.** Ripples are kept (they say the tap landed) but faint.
- **Reduced motion** (animator scale 0): opacity only, resting on the final state. Cross-fades stay; sizes snap (`Motion.travel`), nothing travels, the scene art is still.

## Checking

The renders (`BRUME_RENDERS=<dir> ./gradlew --offline :app:testGithubDebugUnitTest --tests '*Renders*'`) show states side by side: a state change that moves anything else on screen is a bug.

## The garden (direction A, karesansui; Iris, 2026-11-01)

Erwann asked for « minimaliste, un peu à la japonaise… pas du tout comics ou manga » and called the full-bleed scene art « années 2000… fond d'écran ». The art now belongs to the interface (`ui/Garden.kt`, maths in `:core` `garden/Rake`):

- **Ground**: washi by day, graphite at night (`Ground`, `ground_day` / `ground_night`). Warm neutrals at very low chroma; meaning by line count, spacing and weight, never hue.
- **The stone is the play control** (`Stone`, `stone_day` / `stone_night`): value, no outline, no glow. Without a sound, its place is a quiet circle.
- **Raked gravel** drawn as vectors (`RakedGravel`): rings around the stone, straight lines elsewhere that stop at the rings. At rest the rings drift ±6% of a spacing over 23 s; playing, they breathe with the level (one swell every 9 s); on Respirer they widen with the in-breath and narrow with the out-breath. Rings never cross (`Rake.MAX_SWELL`).
- **Scenes**: Dormir one stone, wide slow rings; Se poser two stones, parallel lines; Respirer rings that follow the breath; Tons lines whose spacing follows the pitch (log scale, 250 Hz wide to 12 kHz close).
- **The timer** is the one firmer line around the stone, emptying: weight, not colour.
- **Reduced motion**: the garden rests at swell 0; nothing drifts.
- Sacha's files replace the placeholders without code changes: delete `res/drawable/stone_night.xml` (and `stone_day`, `ground_night`, `ground_day`) and drop the PNG of the same name into `res/drawable-nodpi/`.
