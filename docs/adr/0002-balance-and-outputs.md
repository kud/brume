# ADR 0002: Left/right balance, kept per output device

- Status: accepted (Maya, 2026-10-31)
- Context: Erwann, 2026-10-31: « On devrait pouvoir choisir la balance aussi, gauche droite », then « Trouve toutes les bonnes idées à ce sujet, et le fait de pouvoir se connecter en Bluetooth à un autre headphone ». One-sided tinnitus is common, and r4ai's ACRN app has a pan control.

## Decision

- **One balance for every layer.** A stage at the end of `SoundEngine` splits the mono mix into left and right, for comfort sounds and the tone pattern alike. Every generator is mono (`Source.next(): Float`), duplicated into both channels; balance works the same on it.
- **The law only attenuates** (`core/sound/Balance`). Centre is unity on both sides. Towards one side, the other falls linearly in amplitude to silence at the end (50% is -6 dB); the near side stays at unity. Constant-power panning was rejected: it lifts the near ear +3 dB above its centre level, and Brume never gets louder on its own. Tested: no sample on either channel ever exceeds the same render at centre, through any series of moves.
- **Smoothed** with the 20 ms gain ramp every layer uses, per channel. "Too loud" still cuts both, through the master fade.
- **Kept per output device, never synced.** Balance is a level per ear, and it depends on the headphones as much as on the ear. Each output (`kind:productName`, never a MAC address, so no Bluetooth permission) keeps its own balance and the three levels, in the `outputs` prefs. A device seen for the first time keeps the current balance and takes each level at most the soft default. A new device's settings are applied only while stopped: the service already pauses on any sink added or removed and on `AUDIO_BECOMING_NOISY`, and anything that arrives while sound plays waits for the stop. So no output change ever raises a level without a user action.
- **Output in words, and the system's switcher.** The "now playing" card names the current output (API 33+: `getAudioDevicesForAttributes`; before, the likeliest sink) and a Change button opens `MediaRouter2.showSystemOutputSwitcher()` on Android 14+, Bluetooth settings before. Pairing and routing stay with the system. `androidx.mediarouter` is not in the offline cache and is not needed.

## Consequences

- The slider stops in tenths, with a stop at centre and a "Centre" reset; the value is written ("Left 30%") and TalkBack reads the same words.
- Untested on a phone: the output name a real Bluetooth headset reports, and the switcher on Android 14+.
