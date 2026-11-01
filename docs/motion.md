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
