package app.beansontoast.brume.ui

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.beansontoast.brume.R
import app.beansontoast.brume.update.Update
import app.beansontoast.brume.update.UpdateCue
import app.beansontoast.brume.update.UpdateProblem
import app.beansontoast.brume.update.UpdateState
import app.beansontoast.brume.update.Updater
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * The github build's updater UI (docs/updates.md), after pastille's sheet (49975a8) in Brume's ink
 * and mist: the floating bar's surface and hairline, the serif of the scene names for the title, the
 * progress in the ink itself, every state a sentence and every step a word, never a colour alone.
 */

/**
 * The automatic check, 3 s after Brume comes up: at most every 15 minutes on a dev build, daily on
 * a final one. A build found for the first time opens the update sheet by itself, once, unless
 * [canAnnounce] says something else holds the screen; About carries it after that. It never
 * downloads by itself: the sheet waits for Download, and Android still asks.
 */
@Composable
internal fun UpdatesOnOpen(canAnnounce: () -> Boolean, onFound: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val updater = remember { Updater.forContext(context) }
    val currentCanAnnounce by rememberUpdatedState(canAnnounce)
    val currentOnFound by rememberUpdatedState(onFound)
    LifecycleStartEffect(updater) {
        val job = scope.launch {
            delay(ANNOUNCE_DELAY_MS)
            updater.checkIfDue() ?: return@launch
            if (currentCanAnnounce()) currentOnFound()
        }
        onStopOrDispose { job.cancel() }
    }
}

private const val ANNOUNCE_DELAY_MS = 3_000L

/** About › Updates: what the updater knows, in a sentence, and one button that opens the sheet. */
@Composable
internal fun UpdatesAbout(onOpen: () -> Unit) {
    val context = LocalContext.current
    val updater = remember { Updater.forContext(context) }
    val state by updater.state.collectAsStateWithLifecycle()
    val found by updater.found.collectAsStateWithLifecycle()
    val cue by updater.cue.collectAsStateWithLifecycle()
    val newer = found?.takeIf { cue != UpdateCue.NONE }
    SectionTitle(stringResource(R.string.about_updates_title))
    val line = when {
        state != UpdateState.Idle && state != UpdateState.UpToDate -> statusText(context, state)
        newer != null -> stringResource(R.string.about_updates_found, newer.toString())
        else -> stringResource(R.string.about_updates_idle)
    }
    Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    OutlinedButton(onOpen, Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 48.dp)) {
        Text(if (newer != null) stringResource(R.string.about_updates_install, newer.toString()) else stringResource(R.string.about_updates_check))
    }
}

/**
 * The update sheet. Opened by the automatic check it shows the build found, with Later and
 * Download; opened from About it asks GitHub first. The work belongs to the [Updater], so closing
 * the sheet never stops a download, and About says where it is.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UpdateSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val updater = remember { Updater.forContext(context) }
    val state by updater.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // Once per opening, rotation included: nothing known yet means ask GitHub now.
    var asked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (asked) return@LaunchedEffect
        asked = true
        val now = updater.state.value
        if (now == UpdateState.Idle || now == UpdateState.UpToDate || (now is UpdateState.Failed && now.update == null)) updater.check()
    }
    // Back from Android's "Install unknown apps" page: carry on if it was allowed.
    LifecycleResumeEffect(state) {
        (state as? UpdateState.NeedsPermission)?.let {
            if (context.packageManager.canRequestPackageInstalls()) updater.install(it.update)
        }
        onPauseOrDispose { }
    }
    val close: () -> Unit = {
        scope.launch { runCatching { sheetState.hide() } }.invokeOnCompletion { onDismiss() }
    }
    val scheme = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = scheme.surfaceContainer,
        contentColor = scheme.onSurface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = scheme.outlineVariant) },
    ) {
        UpdateSheetContent(
            installed = updater.current.toString(),
            state = state,
            onInstall = updater::startInstall,
            onLater = { update ->
                updater.setAside(update.version)
                close()
            },
            onAllow = { update ->
                try {
                    context.startActivity(updater.permissionIntent())
                } catch (_: ActivityNotFoundException) {
                    // No such page on this phone: the install itself will ask.
                    updater.install(update)
                }
            },
            onClose = close,
        )
    }
}

/**
 * The sheet's inside, stateless so a render can show every state. One height across states: the
 * sentence keeps two lines, the progress its line, the note two lines, the buttons one row.
 */
@Composable
internal fun UpdateSheetContent(
    installed: String,
    state: UpdateState,
    onInstall: () -> Unit,
    onLater: (Update) -> Unit,
    onAllow: (Update) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val status = statusText(context, state)
    val spoken = spokenText(context, state)
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 4.dp, bottom = 16.dp)
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Text(
            stringResource(R.string.update_title),
            style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Serif),
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(R.string.update_installed, installed),
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        Box(Modifier.padding(vertical = 14.dp).fillMaxWidth().height(1.dp).background(scheme.outlineVariant.copy(alpha = 0.6f)))
        // TalkBack says each step as it comes, the download in tens so it isn't a stream of numbers.
        Text(
            status,
            style = MaterialTheme.typography.bodyLarge,
            minLines = 2,
            maxLines = 2,
            modifier = Modifier.clearAndSetSemantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = spoken
            },
        )
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(4.dp)) {
            val ink = scheme.onSurface
            val track = scheme.outlineVariant.copy(alpha = 0.5f)
            when (state) {
                UpdateState.Idle, UpdateState.Checking, is UpdateState.Installing ->
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = ink, trackColor = track)
                is UpdateState.Downloading -> if (state.percent != null) {
                    LinearProgressIndicator(progress = { state.percent / 100f }, modifier = Modifier.fillMaxWidth(), color = ink, trackColor = track)
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = ink, trackColor = track)
                }
                else -> Unit
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(if (state is UpdateState.NeedsPermission) R.string.update_note_permission else R.string.update_note_confirm),
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            minLines = 2,
            maxLines = 2,
        )
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val main = Modifier.heightIn(min = 48.dp)
            when (state) {
                is UpdateState.Available -> {
                    TextButton(onClick = { onLater(state.update) }, modifier = main) { Text(stringResource(R.string.update_later)) }
                    OutlinedButton(onClick = onInstall, modifier = main) { Text(stringResource(R.string.update_download)) }
                }
                is UpdateState.NeedsPermission -> {
                    TextButton(onClick = onClose, modifier = main) { Text(stringResource(R.string.update_not_now)) }
                    OutlinedButton(onClick = { onAllow(state.update) }, modifier = main) { Text(stringResource(R.string.update_allow)) }
                }
                is UpdateState.Failed -> {
                    TextButton(onClick = onClose, modifier = main) { Text(stringResource(R.string.update_close)) }
                    OutlinedButton(onClick = onInstall, modifier = main) { Text(stringResource(R.string.update_try_again)) }
                }
                UpdateState.UpToDate -> {
                    Spacer(Modifier)
                    OutlinedButton(onClick = onClose, modifier = main) { Text(stringResource(R.string.update_done)) }
                }
                UpdateState.Idle, UpdateState.Checking, is UpdateState.Downloading, is UpdateState.Installing -> {
                    Spacer(Modifier)
                    TextButton(onClick = onClose, modifier = main) { Text(stringResource(R.string.update_close)) }
                }
            }
        }
    }
}

/** The sentence for [state]; failures read "what: try again", as in pastille (Iris, 2026-10-30). */
internal fun statusText(context: Context, state: UpdateState): String = when (state) {
    // Idle only for the instant before the sheet's question reaches GitHub.
    UpdateState.Idle, UpdateState.Checking -> context.getString(R.string.update_checking)
    UpdateState.UpToDate -> context.getString(R.string.update_up_to_date)
    is UpdateState.Available -> context.getString(R.string.update_ready, state.update.version.toString())
    is UpdateState.Downloading -> state.percent?.let { context.getString(R.string.update_downloading_percent, state.update.version.toString(), it) }
        ?: context.getString(R.string.update_downloading, state.update.version.toString())
    is UpdateState.NeedsPermission -> context.getString(R.string.update_needs_permission)
    is UpdateState.Installing -> context.getString(R.string.update_installing, state.update.version.toString())
    is UpdateState.Failed -> context.getString(failureText(state.problem))
}

/** What TalkBack says: the same sentence, with the download's percent in steps of ten. */
internal fun spokenText(context: Context, state: UpdateState): String =
    if (state is UpdateState.Downloading && state.percent != null) {
        context.getString(R.string.update_downloading_spoken, state.update.version.toString(), state.percent / 10 * 10)
    } else {
        statusText(context, state)
    }

internal fun failureText(problem: UpdateProblem): Int = when (problem) {
    UpdateProblem.NETWORK -> R.string.update_fail_network
    UpdateProblem.FEED -> R.string.update_fail_feed
    UpdateProblem.DOWNLOAD -> R.string.update_fail_download
    UpdateProblem.DIGEST -> R.string.update_fail_digest
    UpdateProblem.PACKAGE -> R.string.update_fail_package
    UpdateProblem.INSTALL -> R.string.update_fail_install
}
