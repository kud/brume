# ADR 0001: Back up and sync through a folder the person chooses

- Status: accepted (Maya, 2026-10-31)
- Context: Erwann, 2026-10-31: « On devrait pouvoir sur chaque app utiliser Google Drive ou autre ». Pastille solved this on 2026-10-29; the cross-app rule is in atelier's `knowledge/projects/android-kit/`.

## Decision

Brume syncs the way pastille does. The person picks a folder once (Storage Access Framework, `ACTION_OPEN_DOCUMENT_TREE`); Brume keeps one file there, `brume-sync.json`, and merges with it after every change, when the app comes to the front and when it leaves. The storage app (Google Drive, a local folder, Nextcloud, anything with a DocumentsProvider) does the uploading. Brume holds no network permission, so `checkNoNetwork*` and `GuardsTest` stay as they are. Data leaves the phone only to a place the person chose, as with the export.

- **Format:** the sync file is the export file, schema 1, with two optional additions: `checkIns[].updatedAt` and `settings`. An export imports, a sync file imports, and an old export still reads.
- **Check-ins:** one per day. A day only one side has is kept; a day both have keeps the later save. A row with no time (a file from before sync) counts as oldest, so it fills gaps and never overwrites a day. "Keep both" was rejected: the journal holds one check-in per day by design.
- **Sessions:** an append-only log. The merge is the union, each session once. A session whose way of ending this Brume doesn't know is carried in the file, not dropped.
- **Settings:** night hours, plus tone pitch and trims as one group. In each group the later change wins. Levels never sync: a level belongs to this phone's output and headphones, and one arriving from elsewhere would be a level raised without a user action. Synced tones are never applied while sound plays; the next sync after it stops brings them in. Intro state, the selected sound and the timer stay per phone. The app language never syncs either: it is the system's per-app locale (Android 13+), held by the system, not by Brume, and each phone keeps its own.
- **Ties** are broken on the values, never on which side is local, so two phones merging the same pair write the same file (tested).
- **No base yet:** Brume never deletes a check-in, so a two-way union is enough. The day deletion exists, this takes pastille's three-way base (`SyncBase`) and tombstones.
- **Import:** a one-off merge from a picked file, with the same rules. It removes nothing and never writes to the picked file. A file with one bad row is refused whole.

## Consequences

- Rules are pure Kotlin in `:core` (`sync/SyncMerge`, `sync/SyncFile`) with tests; the Android side (`app/.../sync/`) is SAF plumbing and triggers, shaped to move into the shared kit.
- A real Google Drive round-trip has not been tested from a build session. The first check is two phones on one Drive folder.
