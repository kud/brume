# Brume

Sounds for comfort and sleep, and a short daily journal, for people living with tinnitus. A native Android app.

**Brume is a wellness app, not a medical device.** We live with tinnitus; we're not doctors. Brume draws on published research and clinical guidance we've read, to offer some calm to anyone who wants to relax. It is not a treatment and doesn't replace a doctor, an ENT or an audiologist. If your hearing changes suddenly, if it beats with your heart, or if it started after a blow to the head, see a doctor (the app's About screen lists when).

## What v0.1 does

- **Listen.** Generated sound, two layers at once: one noise (white, pink, brown, grey) and one ambience (rain, surf, wind), each with its own level. Every start fades in from silence over about three seconds; the app never raises the system volume; **Too loud** cuts to silence and makes the next start quieter. A sleep timer (20, 45 or 90 minutes) fades out over its last five minutes. Plays with the screen off from a media notification, and pauses when the headphones or speaker change, or another app takes the audio.
- **Journal.** One check-in a day: loudness and bother, 0 to 10, and an optional note. Listening time is logged on its own. **Export** writes everything to one versioned JSON file, wherever you choose.
- **Back up and sync.** Choose a folder, in Google Drive or on the phone: Brume keeps `brume-sync.json` there and merges with it after every change, so two phones that choose the same folder keep the same journal, tone settings and night hours. Import adds a file's check-ins without removing anything (About › Back up and sync).
- **Night.** From 22:00 to 06:00 the app is dark and dim on true black, whatever the system theme (can be switched off in Learn, under Settings).

Everything is synthesised: there are no sound files. Birds and other recorded sounds come later, from CC0 recordings with a licence manifest.

## Privacy

Nothing about you leaves the phone. Brume has no account, no analytics and no cloud backup of the journal. The GitHub build goes online for one thing only: to look for and download its own updates from this repository's releases, which Android asks you to confirm (`docs/updates.md`). The F-Droid build has no internet permission at all (the build fails if one appears). Your data leaves only in an export you save yourself, or in the sync folder you choose; the storage app (Google Drive, say) does any uploading, never Brume.

## Build

```sh
./gradlew :core:test :app:testGithubDebugUnitTest :app:testFdroidDebugUnitTest :app:lintGithubDebug :app:assembleGithubDebug :app:assembleFdroidDebug
```

Modules: `:core` (pure Kotlin: sound generation, level rules, journal and export format, fast JVM tests), `:design` (Material 3 components written to Gamme's spec), `:app` (Android: playback service, Room, Compose).

## Releases

Pushing a `vMAJOR.MINOR.PATCH` tag, or running the Android workflow on `main` with a tag, builds the `github` APK and attaches it to a GitHub Release (`brume-<version>-debug.apk` always, `brume-<version>.apk` once signed; the in-app updater relies on these names). A `-dev.N` tag is a pre-release and ships the debug build when the signing secrets are absent. A final release needs four repository secrets: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`.

## Sources

The research, guidelines, open-source projects and design Brume draws on are listed in [docs/SOURCES.md](docs/SOURCES.md).

## Licence

GPL-3.0-or-later. See [LICENSE](LICENSE).
