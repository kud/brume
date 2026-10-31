# ADR 0003: Switchable modules, and Breathe as the first

- Status: accepted (Iris, 2026-10-31)
- Context: company.md, 2026-10-31: Brume has four pillars (meditation, breathing, tinnitus work, sleep) plus Learn, each a module the person can turn on or off, and the home shows only what is chosen. Erwann: « On lance le module respiration après la langue ». Inspiration: Breathly's approach (a dark full screen, a shape opening and closing, one word, a timer, a ✕), not its look.

## Decision

- **Modules are switches in About, under "Activities".** Listen is always on and said in words ("Always on"), not shown as a switch that can't move. Breathe and Tones each have a switch with a one-line description; the switch's state is its thumb's place, never a hue. Turning a module off removes its tab; nothing is deleted. A tab whose module goes off, here or from the other phone, falls back to Listen. Setting the pitch from the introduction turns Tones back on.
- **Defaults on.** Tones is on because existing users have it; Breathe is on because it is new and should be found. `Settings.modules` (prefs `module_tones`, `module_breath`).
- **The switches sync**, as one group with one time, later change wins (`SettingsV1.modules` + `modulesAt`, an optional field, so schema 1 still reads; an older file without them never switches anything off). They are preferences about what the app shows, not levels: the same person on two phones wants the same app. Levels and the output-specific balance still never sync (ADR 0002). The last breathing choices (rhythm, length, haptics, sound) stay per phone: they are a last-used choice, not worth a conflict.
- **The bar gains one tab: five, the limit.** Listen, Breathe, Tones, Journal, About. When meditation arrives it does not get a sixth: Breathe becomes "Calm" (« Calme ») and holds both, one entry per practice. Sleep and Learn follow the same rule: no tab beyond five.
- **Breathe (« Souffle »).** Rhythms 4-6 (a longer out-breath), 5-5 (about six a minute) and 4-7-8; lengths 2, 5 and 10 minutes. Timing is `:core` `breath/Breath` and tested: whole breaths only, the count rounded to the nearest, so the total is the chosen length to within half a breath (4-6 and 5-5 exactly; 4-7-8 is 114 s, 304 s or 608 s), and every session ends on an out-breath. 4-7-8 carries a written note to hold only while it is easy.
- **The session view is night mist.** Five thin horizon lines rise as the breath fills and settle as it empties; low alpha, lower at night, nothing filled, nothing bright. One word (« Inspirez », « Retenez », « Expirez »), a written count under it, the time left top right and "End" with a ✕ top left. The screen stays on for the session. Reduced motion: the band rests at mid height and the word and the count carry the rhythm.
- **A light tap at each change**, on by default, optional, skipped when the phone has no vibrator. Needs `VIBRATE`, a normal permission granted at install; no network.
- **Sound underneath goes through the player**, so its fade-in, levels and "Too loud" all hold. Off by default; only offered when a sound is chosen in Listen. If the session started it, ending the session pauses it; a sound already playing plays on. No new audio files, no voice, no chime.

- **Every top-level screen is titled by its tab's label** (Listen, Breathe, Tones, Journal, About), in the serif headline, with an optional quiet line under it (`ScreenTitle`). Erwann, on dev.7: brume everywhere with the page name under it, or never. Never: a module's title has to match its tab once tabs come and go, and the brand lives in the launcher icon, the splash, the introduction and the foot of About, where the wordmark signs the version. Tones keeps "Tone pattern · ACRN" as its subtitle.

## Consequences

- Unverified on a phone: haptic strength on a real vibrator, how the band reads at the lowest brightness, and whether a 10-minute 4-7-8 is too long for most people (a candidate for capping it at 2 or 5).
- The home screen is still Listen; a home that lists the chosen modules comes with the second module.
