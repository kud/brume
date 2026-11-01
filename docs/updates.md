# In-app updates

Brume ships as two builds of the same app (`productFlavors` in `app/build.gradle.kts`):

- **`github`**: the APKs attached to GitHub Releases. It carries the in-app updater, and with it the only network access Brume has: `INTERNET` and `REQUEST_INSTALL_PACKAGES`, used only to check and download Brume's own releases from GitHub. Nothing about the person, their journal, levels or settings is ever sent; the request is the whole conversation.
- **`fdroid`**: no updater and no network at all. F-Droid (later, reproducible) updates it.

`src/main` stays network-free. `checkNoNetwork<Variant>` reads every merged manifest before it assembles or lints: an `fdroid` variant fails on any network permission, a `github` variant on anything beyond the updater's two. `GuardsTest` scans `src/main` and `src/fdroid` for `java.net`/HTTP code, and allows it in `src/github` only under `update/`.

## How it works

Copied from pastille's updater (kud/pastille, `app/src/github`, at 49975a8; its ADR is `docs/adr/0001-in-app-updates.md`):

- **When:** 3 s after Brume opens, at most every 15 minutes on a dev build and daily on a final one; and whenever About › Updates is tapped.
- **What:** GitHub's `/repos/kud/brume/releases`, newest release above the installed version. A dev install (`-dev.N`) is offered dev pre-releases; a final install only finals. There is no channel switch yet: developer mode will carry it (atelier, `knowledge/projects/android-kit`).
- **Which file — each variant follows its own variant:** the `.debug` install takes `brume-<version>-debug.apk`, the release install `brume-<version>.apk`. An `-fdroid.apk` is never taken. CI names the assets this way (`.github/workflows/android.yml`); renaming them strands every install.
- **Safety:** HTTPS only, to `api.github.com`, `github.com` and GitHub's two asset hosts, redirects followed by hand and each hop checked; the asset must sit under `/kud/brume/releases/download/`, carry GitHub's SHA-256 digest and match it once downloaded; the APK must be this very package at the version named, with a versionCode no lower than the installed one. Then `PackageInstaller`, which always asks the person to confirm.
- **UI:** a build found for the first time opens the update sheet by itself, once (Later sets it aside; About still offers it). The sheet asks nothing until Download. About › Updates says where things stand and opens the sheet. Every state is a sentence, every step a word, never a colour alone; EN and FR.

## versionCode

`(major·1 000 000 + minor·1 000 + patch)·100 + N` for `-dev.N` (N ≤ 98), `+ 99` for the final, derived in CI from the tag. `v0.1.0-dev.20` shipped as 100020, so dev.21 is 100021 and installs over it. **Never change the formula**: an install only updates in place to a higher code.

## Signing

The debug build, which every dev release ships, is signed with `app/debug.keystore`, committed and public by design: every CI run signs with the same key, so a newer debug APK installs over the last (`v0.1.0-dev.20` is signed by SHA-256 `ef2449e1…b74e`, that key). The release build will carry Erwann's release key: moving from the `.debug` app to the release one is a separate app (`app.beansontoast.brume` beside `app.beansontoast.brume.debug`), so it is one install by hand, carrying data over through the sync folder or an export, then the `.debug` app uninstalled.

## Drift from pastille, until Gamme's `kit/updates`

`update/Updates.kt` is pastille's file unchanged but for its header and package; the rest is meant to move to `kit/updates` as one piece. Where Brume differs today:

- **Debug builds update.** Pastille's debug build never self-updates (`isAvailable = !DEBUG`, its CI debug key changes every run); Brume's does, from the `-debug.apk`, since every dev release so far is a debug build and its key is stable. `Updater.isAvailable` is always true and `brumeSource(debug)` picks the asset.
- **No snackbar or ⋮ menu.** Pastille announces with a snackbar over its list and keeps a ⋮ item; Brume has neither, so the sheet opens by itself once, and About carries it after.
- **No Test builds switch.** The channel follows the installed build (dev → dev pre-releases).
- **Strings are resources** (EN and FR), where pastille's are inline English.
