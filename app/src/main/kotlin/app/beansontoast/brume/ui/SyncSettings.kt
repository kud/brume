package app.beansontoast.brume.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.beansontoast.brume.R
import app.beansontoast.brume.sync.SyncProblem
import app.beansontoast.brume.sync.SyncState
import app.beansontoast.design.SettingsGroup
import app.beansontoast.design.SwitchRow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class SyncActions(
    val chooseFolder: () -> Unit = {},
    val auto: (Boolean) -> Unit = {},
    val syncNow: () -> Unit = {},
    val import: () -> Unit = {},
)

/**
 * Settings › Back up and sync. Every state is a sentence (Iris's night-mist rules: nothing rests
 * on colour or on a switch alone): which folder, whether it's on, when it last worked, or why not.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SyncSettings(state: SyncState, message: String?, actions: SyncActions, zone: ZoneId = ZoneId.systemDefault()) {
    SettingsGroup(stringResource(R.string.sync_title)) {
        val muted = MaterialTheme.colorScheme.onSurfaceVariant
        if (!state.hasFolder) {
            Text(stringResource(R.string.sync_off_body), style = MaterialTheme.typography.bodyMedium)
        } else {
            val name = state.folderName ?: stringResource(R.string.sync_folder_unnamed)
            Text(stringResource(if (state.auto) R.string.sync_on else R.string.sync_paused, name), style = MaterialTheme.typography.bodyMedium)
            val problem = when (state.problem) {
                SyncProblem.UNREACHABLE -> R.string.sync_problem_unreachable
                SyncProblem.NEWER -> R.string.sync_problem_newer
                SyncProblem.NOT_BRUME -> R.string.sync_problem_not_brume
                null -> null
            }
            Text(
                when {
                    problem != null -> stringResource(problem)
                    state.lastSyncedAt != null -> stringResource(R.string.sync_last, synced(state.lastSyncedAt, zone))
                    else -> stringResource(R.string.sync_never)
                },
                style = MaterialTheme.typography.bodyMedium, color = muted,
            )
            SwitchRow(stringResource(R.string.sync_auto), stringResource(R.string.sync_auto_summary), state.auto, actions.auto)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            OutlinedButton(actions.chooseFolder, Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(if (state.hasFolder) R.string.sync_choose_other else R.string.sync_choose))
            }
            if (state.hasFolder) OutlinedButton(actions.syncNow, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.sync_now)) }
        }
        HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Text(stringResource(R.string.import_title), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.import_body), style = MaterialTheme.typography.bodyMedium, color = muted)
        OutlinedButton(actions.import, Modifier.padding(top = 8.dp).heightIn(min = 48.dp)) { Text(stringResource(R.string.import_action)) }
        if (message != null) {
            Text(message, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp).semantics { liveRegion = LiveRegionMode.Polite })
        }
    }
}

private fun synced(at: Long, zone: ZoneId): String {
    val time = Instant.ofEpochMilli(at).atZone(zone)
    val pattern = if (time.toLocalDate() == LocalDate.now(zone)) "HH:mm" else "d MMM, HH:mm"
    return time.format(DateTimeFormatter.ofPattern(pattern))
}
