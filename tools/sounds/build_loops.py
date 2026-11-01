#!/usr/bin/env python3
"""
Rebuilds Brume's recorded ambiences (app/src/main/res/raw/sound_*.ogg) from their CC0 originals.

    python3 -I tools/sounds/build_loops.py <originals-dir> <work-dir> [<res-raw-dir>]

The originals are not in the repo (SOUNDS.md lists where each one comes from); download them into
<originals-dir> under the relative paths in SOUNDS below. Needs ffmpeg (with libopus) and numpy.
Reads the originals as audio only: nothing in them is ever run.

For each sound:
  1. decode to mono 48 kHz float;
  2. pick the steadiest LOOP_SECONDS stretch: RMS per 100 ms, scored on how far the loud blocks rise
     above the median and on the largest block-to-block jump, so no door, voice or gust stands out;
  3. optional gentle compression (the fire is quiet and spiky);
  4. bake an equal-power crossfade at the seam: the loop's first FADE_SECONDS are the stretch's
     start fading in under the FADE_SECONDS that follow its end, so the last sample runs straight
     into the first and the loop can wrap sample by sample, with no gap and no click;
  5. match loudness (EBU R128 integrated, TARGET_LUFS), keeping peaks under PEAK by a soft knee;
  6. encode Opus in Ogg (minSdk 29 decodes it), mono, at the bitrate below.
"""
import json
import subprocess
import sys
from pathlib import Path

import numpy as np

RATE = 48_000
LOOP_SECONDS = 75.0
FADE_SECONDS = 3.0
TARGET_LUFS = -20.0
PEAK = 0.89  # -1 dBFS

# id: (original, bitrate kbps, compress)
SOUNDS = {
    "rain": ("opengameart/rain/amb-rain-loop-2.flac", 64, False),
    "rain_terrace": ("bigsoundbank/rain/1019-summer-rain-on-terrace.flac", 64, False),
    "wind": ("bigsoundbank/wind/0907-wind-in-shrub.wav", 64, False),
    "sea": ("bigsoundbank/sea/1446-small-waves-and-beach-1.flac", 64, False),
    "stream": ("bigsoundbank/stream/3217-mountain-stream-4.flac", 64, False),
    "storm": ("bigsoundbank/thunder/0820-rain-and-storm-in-a-tent.wav", 64, False),
    "night": ("bigsoundbank/crickets/1880-campaign-at-night-4.flac", 48, False),
    "fire": ("bigsoundbank/fire/2855-fireplace-3.flac", 48, True),
    "cafe": ("bigsoundbank/cafe/3376-restaurant-2.flac", 48, False),
    "train": ("bigsoundbank/train/0636-eurostar-car-in-a-tunnel.wav", 48, False),
    "birds": ("opengameart/birds/park-ambience-birds.wav", 48, False),
}


def decode(path: Path) -> np.ndarray:
    raw = subprocess.run(
        ["ffmpeg", "-v", "error", "-nostdin", "-i", str(path), "-ac", "1", "-ar", str(RATE), "-f", "f32le", "-"],
        check=True, capture_output=True,
    ).stdout
    return np.frombuffer(raw, dtype="<f4").astype(np.float64)


def block_db(x: np.ndarray, block: int) -> np.ndarray:
    n = len(x) // block
    b = x[: n * block].reshape(n, block)
    return 10 * np.log10(np.mean(b * b, axis=1) + 1e-12)


def steadiest(x: np.ndarray, seconds: float) -> tuple[int, float]:
    block = RATE // 10
    db = block_db(x, block)
    span = int(seconds * 10)
    best, best_score = 0, float("inf")
    for start in range(0, len(db) - span + 1, 10):
        w = db[start : start + span]
        med = np.median(w)
        score = (np.percentile(w, 99.5) - med) + 0.5 * np.std(w) + 0.5 * np.max(np.abs(np.diff(w)))
        if score < best_score:
            best, best_score = start, score
    return best * block, best_score


def compress(x: np.ndarray, ratio: float = 4.0, above_db: float = 3.0) -> np.ndarray:
    """An RMS compressor: peaks more than above_db over the median level are pulled in by ratio."""
    win = int(0.01 * RATE)
    env = np.sqrt(np.convolve(x * x, np.ones(win) / win, mode="same") + 1e-12)
    env_db = 20 * np.log10(env)
    knee = np.median(env_db) + above_db
    over = np.maximum(env_db - knee, 0)
    gain_db = -over * (1 - 1 / ratio)
    # Smooth the gain so it never pumps audibly.
    g = np.convolve(gain_db, np.ones(int(0.03 * RATE)) / int(0.03 * RATE), mode="same")
    return x * 10 ** (g / 20)


def seamless(x: np.ndarray, start: int, length: int, fade: int) -> np.ndarray:
    seg = x[start : start + length + fade]
    out = seg[:length].copy()
    t = np.linspace(0, np.pi / 2, fade, endpoint=False)
    out[:fade] = seg[:fade] * np.sin(t) + seg[length : length + fade] * np.cos(t)
    return out


def lufs(x: np.ndarray, work: Path) -> float:
    tmp = work / "measure.f32"
    tmp.write_bytes(x.astype("<f4").tobytes())
    err = subprocess.run(
        ["ffmpeg", "-v", "info", "-nostdin", "-f", "f32le", "-ar", str(RATE), "-ac", "1", "-i", str(tmp),
         "-af", "ebur128", "-f", "null", "-"],
        capture_output=True, text=True,
    ).stderr
    tmp.unlink()
    value = [l for l in err.splitlines() if l.strip().startswith("I:")][-1]
    return float(value.split()[1])


def soft_peak(x: np.ndarray, peak: float) -> np.ndarray:
    knee = 0.7 * peak
    room = peak - knee
    m = np.abs(x)
    y = np.where(m <= knee, m, knee + room * np.tanh((m - knee) / room))
    return np.sign(x) * y


def main() -> None:
    originals, work = Path(sys.argv[1]), Path(sys.argv[2])
    raw_dir = Path(sys.argv[3]) if len(sys.argv) > 3 else None
    work.mkdir(parents=True, exist_ok=True)
    length, fade = int(LOOP_SECONDS * RATE), int(FADE_SECONDS * RATE)
    report = {}
    for sid, (rel, kbps, squeeze) in SOUNDS.items():
        x = decode(originals / rel)
        seconds = min(LOOP_SECONDS, len(x) / RATE - FADE_SECONDS - 0.5)
        n = int(seconds * RATE)
        start, score = steadiest(x[: len(x) - fade], seconds)
        if squeeze:
            x = compress(x)
        loop = seamless(x, start, n, fade)
        loop -= np.mean(loop)
        loop *= 10 ** ((TARGET_LUFS - lufs(loop, work)) / 20)
        loop = soft_peak(loop, PEAK)
        # The seam, checked: the step from the last sample to the first is no larger than steps inside.
        steps = np.abs(np.diff(loop))
        seam = abs(loop[0] - loop[-1])
        out = work / f"sound_{sid}.ogg"
        subprocess.run(
            ["ffmpeg", "-v", "error", "-nostdin", "-y", "-f", "f32le", "-ar", str(RATE), "-ac", "1", "-i", "-",
             "-c:a", "libopus", "-b:a", f"{kbps}k", "-application", "audio", "-map_metadata", "-1", str(out)],
            input=loop.astype("<f4").tobytes(), check=True,
        )
        report[sid] = {
            "from_s": round(start / RATE, 1), "seconds": round(n / RATE, 1), "score_db": round(float(score), 2),
            "lufs": round(lufs(loop, work), 1), "peak": round(float(np.max(np.abs(loop))), 3),
            "seam_step": round(float(seam), 4), "p99_step": round(float(np.percentile(steps, 99)), 4),
            "kb": out.stat().st_size // 1024,
        }
        print(sid, json.dumps(report[sid]), flush=True)
        if raw_dir:
            raw_dir.mkdir(parents=True, exist_ok=True)
            (raw_dir / out.name).write_bytes(out.read_bytes())
    (work / "report.json").write_text(json.dumps(report, indent=1))


if __name__ == "__main__":
    main()
