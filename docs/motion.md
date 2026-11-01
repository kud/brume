# Motion and layout stability

Brume is a relaxation app: it should feel extremely soft. Erwann on dev.21: « extrêmement douce… smooth… pas trop flashy… je veux pas des jumping de layout ». One spec, in `:design` as `Motion` (`design/.../Motion.kt`), used everywhere.

## The spec

| | Value | Used for |
| --- | --- | --- |
| Curve | `Motion.Ease`, cubic-bezier(0.2, 0, 0, 1): moves at once, lands softly; `Motion.Glide`, cubic-bezier(0.05, 0.7, 0.1, 1), for the slide | everything; never a spring, never overshoot |
| `QUICK` | 180 ms | a word or a mark changing: selection weight, a check, a hint fading |
| `CALM` | 280 ms | content swapping in place, a row growing, a pager settling |
| `SLOW` | 900 ms | ambient only, never an answer to a tap: the title card leaving, a drift |

- **Navigation slides; the rest cross-fades** (Erwann, 2026-11-01: « je préfère un slide : quand on clique sur un bouton, on ouvre la fenêtre de droite sur le côté »). Opening a function from home, or a page inside a function, pushes the new screen in from the right while the one under it moves a quarter of the width left (`Motion.push`, `PUSH` = 320 ms on `Motion.Glide`, decelerate-heavy); ✕, ‹ and system Back slide it back out to the right, the leaving screen on top. No spring, no overshoot. Starting or ending a session cross-fades on `CALM`; only the intro's title card giving way to home keeps `SLOW`, since nobody tapped for it. Smooth but quick (Erwann, 2026-11-01: « smooth mais rapide. Là c'est lent »): whatever a tap starts is done within about 320 ms. Content inside a screen (a word, a page of a pager settled by a tap, a selection) cross-fades in place. Sheets (`QuietSheet`) fade on `CALM` and rise 32 dp. The scene pager travels with the finger and settles on `CALM`/`SLOW`, never a spring.
- **Reserve space.** A state that comes and goes keeps its room, faded by `calmAlpha`: the swipe hint, the volume hint, the journal's confirmation, the breathing rhythms under Meditate, a selection check. Words told chosen by weight are laid out in both weights at once (`WeightSwap`), so their width never changes. Text that varies by state reserves its longest form (two lines for a scene's title and purpose).
- **When room must change, grow slowly.** The mini player joining the floating bar expands over `CALM` (`Motion.enter`/`exit`), and the pages padded by the bar follow it frame by frame.
- **Tabular figures** for every number that ticks (`TextStyle.tabular()`): timers, levels, counts, positions.
- **Soft touch.** Ripples are kept (they say the tap landed) but faint.
- **Reduced motion** (animator scale 0): opacity only, resting on the final state. Navigation and sheets cross-fade instead of sliding or rising; sizes snap (`Motion.travel`), nothing travels, the scene art is still.

## Checking

The renders (`BRUME_RENDERS=<dir> ./gradlew --offline :app:testGithubDebugUnitTest --tests '*Renders*'`) show states side by side: a state change that moves anything else on screen is a bug.

Nothing scrolls (the no-scroll pass, 2026-11-01): a screen fits its frame, or pages a page at a time through `PagerWords` or `PageStepper`. `NoScrollTest` fails on any `verticalScroll(`, `horizontalScroll(` or lazy list in the app's sources; `FitTest` composes the key screens at 360x780 dp in French, font scale 1.0, and fails on anything that ends below the window or is squeezed out of it. It also measures font scale 1.3 and writes what it finds to `app/build/reports/fit/`, without failing.

## Home and the misty landscape (Iris, 2026-11-01; replaces the karesansui garden)

Erwann: « des second screens pour chaque fonction, et la home, c'est un choix de trucs… on revient vers la home grâce à une croix ».

- **Home** (`ui/Home.kt`) lists every function the modules leave on, one light surface of 60 dp rows (symbol, name, one line, ›), Réglages as a quiet icon. It fits 360x780 without a scroll. The sound playing sits under the list as a mini player.
- **Each function is its own screen** in `FnFrame` (`ui/Chrome.kt`): ✕ top-left, always the same place, or system Back, returns home; a page inside a function keeps its own back. Functions and pages slide in from the right and back out (see the spec above); reduced motion cross-fades.
- **Landscape** (`ui/Landscape.kt`): sky, far, mist and near layers, cropped to fill and pinned to the bottom. Home shows all four; Respirer, Dormir and Se poser keep sky, far and mist; reading screens keep the sky only, so text never sits on a hill.
- **The orb** (`BreathOrb`, `ui/Breath.kt`) carries the breath: size follows fullness, its edge drifts slowly and less in a hold, the cue and count sit still at its centre. Reduced motion: it rests at half size and does not drift.
- Sacha's files replace the placeholders without code changes: delete `res/drawable/land_home_<layer>_<day|night>.xml` and drop the PNG of the same name (1440x3120) into `res/drawable-nodpi/`.
