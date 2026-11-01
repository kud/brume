# Sounds

Brume's noises (white, pink, brown, grey) are synthesised in `:core` and need no credit. Its
recorded ambiences are CC0 field recordings, made into seamless 75 s mono loops (Opus in Ogg, 48 kHz,
loudness-matched) by `tools/sounds/build_loops.py`. The originals are not in the repo; download them
from the pages below to rebuild the loops. Each licence was checked on its page on the download date.

| File | Title | Author | Source | Licence | Downloaded |
| --- | --- | --- | --- | --- | --- |
| `app/src/main/res/raw/sound_rain.ogg` | AMB Rain Loop 2 | Kresiek The Furry | https://opengameart.org/content/amb-rain-loop-2 | CC0 | 2026-11-01 |
| `app/src/main/res/raw/sound_rain_terrace.ogg` | Summer Rain on Terrace (1019) | Joseph SARDIN | https://bigsoundbank.com/summer-rain-on-terrace-s1019.html | CC0 | 2026-11-01 |
| `app/src/main/res/raw/sound_storm.ogg` | Rain and thunder in a tent (0820) | Joseph SARDIN | https://bigsoundbank.com/rain-and-storm-in-a-tent-s0820.html | CC0 | 2026-11-01 |
| `app/src/main/res/raw/sound_wind.ogg` | Wind in shrub (0907) | Joseph SARDIN | https://bigsoundbank.com/wind-in-shrub-s0907.html | CC0 | 2026-11-01 |
| `app/src/main/res/raw/sound_sea.ogg` | Small waves and beach #1 (1446) | Joseph SARDIN | https://bigsoundbank.com/small-waves-and-beach-1-s1446.html | CC0 | 2026-11-01 |
| `app/src/main/res/raw/sound_stream.ogg` | Mountain stream #4 (3217) | Joseph SARDIN & Axeline T. | https://bigsoundbank.com/mountain-stream-4-s3217.html | CC0 | 2026-11-01 |
| `app/src/main/res/raw/sound_night.ogg` | Campaign at Night #4 (1880) | Joseph SARDIN | https://bigsoundbank.com/campaign-at-night-4-s1880.html | CC0 | 2026-11-01 |
| `app/src/main/res/raw/sound_fire.ogg` | Fireplace #3 (2855), gently compressed | Joseph SARDIN | https://bigsoundbank.com/fireplace-3-s2855.html | CC0 | 2026-11-01 |
| `app/src/main/res/raw/sound_cafe.ogg` | Restaurant #2 (3376), brunch | Joseph SARDIN & Axeline T. | https://bigsoundbank.com/restaurant-2-s3376.html | CC0 | 2026-11-01 |
| `app/src/main/res/raw/sound_train.ogg` | Eurostar car in a tunnel (0636) | Joseph SARDIN | https://bigsoundbank.com/eurostar-car-in-a-tunnel-s0636.html | CC0 | 2026-11-01 |
| `app/src/main/res/raw/sound_birds.ogg` | Park ambiences (birds) | Thimras | https://opengameart.org/content/park-ambiences | CC0 | 2026-11-01 |

Additional sounds: Joseph SARDIN - BigSoundBank.com

## Rebuilding

    python3 -I tools/sounds/build_loops.py <originals-dir> <work-dir> app/src/main/res/raw

`<originals-dir>` holds each original under the relative path listed in `SOUNDS` at the top of the
script. The script prints, per loop, where the stretch was taken, its loudness, its peak and the step
at the seam next to the 99th-percentile step inside the loop (the seam must be no larger).
