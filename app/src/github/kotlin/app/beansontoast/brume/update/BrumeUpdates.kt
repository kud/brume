package app.beansontoast.brume.update

/**
 * Brume's side of the shared rules (docs/updates.md). The updater follows its own variant: the
 * `.debug` install updates from `brume-X.Y.Z[-dev.N]-debug.apk`, the release install from
 * `brume-X.Y.Z[-dev.N].apk`. An `-fdroid.apk`, if one is ever attached, is never taken: F-Droid
 * updates that build. Pastille's debug build doesn't self-update; this is Brume's one drift.
 */
fun brumeSource(debug: Boolean): UpdateSource = UpdateSource("kud/brume") { name ->
    name.startsWith("brume-") && name.endsWith(".apk") && !name.endsWith("-fdroid.apk") &&
        name.endsWith("-debug.apk") == debug
}
