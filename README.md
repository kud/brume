# Brume

Sounds for comfort and sleep, and a short daily journal, for people living with tinnitus. A native Android app.

**Brume is a wellness app, not a medical device.** It is not a treatment and doesn't replace a doctor. If your hearing changes suddenly, if it beats with your heart, or if it started after a blow to the head, see a doctor (the app's About screen lists when).

## What v0.1 does

- **Listen.** Generated sound, two layers at once: one noise (white, pink, brown, grey) and one ambience (rain, surf, wind), each with its own level. Every start fades in from silence over about three seconds; the app never raises the system volume; **Too loud** cuts to silence and makes the next start quieter. A sleep timer (20, 45 or 90 minutes) fades out over its last five minutes. Plays with the screen off from a media notification, and pauses when the headphones or speaker change, or another app takes the audio.
- **Journal.** One check-in a day: loudness and bother, 0 to 10, and an optional note. Listening time is logged on its own. **Export** writes everything to one versioned JSON file, wherever you choose.
- **Night.** From 22:00 to 06:00 the app is dark and dim on true black, whatever the system theme (can be switched off in About).

Everything is synthesised: there are no sound files. Birds and other recorded sounds come later, from CC0 recordings with a licence manifest.

## Privacy

Nothing leaves the phone. Brume has no internet permission (the build fails if one appears), no account, no analytics and no cloud backup of the journal. Your data leaves only in an export you save yourself.

## Build

```sh
./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Modules: `:core` (pure Kotlin: sound generation, level rules, journal and export format, fast JVM tests), `:design` (Material 3 components written to Gamme's spec), `:app` (Android: playback service, Room, Compose).

## Releases

Pushing a `vMAJOR.MINOR.PATCH` tag, or running the Android workflow on `main` with a tag, builds the APK and attaches it to a GitHub Release. A `-dev.N` tag is a pre-release and ships the debug build when the signing secrets are absent. A final release needs four repository secrets: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`.

## Licence

GPL-3.0-or-later. See [LICENSE](LICENSE).
