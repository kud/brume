package app.beansontoast.brume.ui

import androidx.compose.runtime.Composable

// The fdroid build has no updater and no network at all (docs/updates.md): F-Droid updates it.
// These stand in for the github build's versions in src/github, and draw nothing.

@Composable
internal fun UpdatesOnOpen(canAnnounce: () -> Boolean, onFound: () -> Unit) = Unit

@Composable
internal fun UpdatesAbout(onOpen: () -> Unit) = Unit

@Composable
internal fun UpdateSheet(open: Boolean, onDismiss: () -> Unit) = Unit
