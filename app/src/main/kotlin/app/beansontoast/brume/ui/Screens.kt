package app.beansontoast.brume.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.beansontoast.brume.R
import app.beansontoast.brume.core.journal.CheckIn
import app.beansontoast.brume.core.sound.Ambience
import app.beansontoast.brume.core.sound.LevelRules
import app.beansontoast.brume.core.sound.NoiseColour
import app.beansontoast.brume.core.sound.SoundEngine.Mode
import app.beansontoast.brume.playback.PlayerUi
import app.beansontoast.brume.playback.Status
import app.beansontoast.design.EmptyState
import app.beansontoast.design.SelectChip
import app.beansontoast.design.SettingsGroup
import app.beansontoast.design.SwitchRow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

enum class Tab { LISTEN, JOURNAL, ABOUT }

@Composable
fun BrumeScaffold(tab: Tab, onTab: (Tab) -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                NavigationBarItem(tab == Tab.LISTEN, { onTab(Tab.LISTEN) }, { Icon(Icons.Filled.PlayArrow, null) }, label = { Text(stringResource(R.string.tab_listen)) })
                NavigationBarItem(tab == Tab.JOURNAL, { onTab(Tab.JOURNAL) }, { Icon(Icons.Filled.Edit, null) }, label = { Text(stringResource(R.string.tab_journal)) })
                NavigationBarItem(tab == Tab.ABOUT, { onTab(Tab.ABOUT) }, { Icon(Icons.Filled.Info, null) }, label = { Text(stringResource(R.string.tab_about)) })
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) { content() }
    }
}

@StringRes fun NoiseColour.label() = when (this) {
    NoiseColour.WHITE -> R.string.noise_white
    NoiseColour.PINK -> R.string.noise_pink
    NoiseColour.BROWN -> R.string.noise_brown
    NoiseColour.GREY -> R.string.noise_grey
}

@StringRes fun Ambience.label() = when (this) {
    Ambience.RAIN -> R.string.nature_rain
    Ambience.SURF -> R.string.nature_surf
    Ambience.WIND -> R.string.nature_wind
}

fun soundLabel(context: Context, ui: PlayerUi): String =
    if (ui.mode == Mode.TONES) context.getString(R.string.mode_tones) else listOfNotNull(ui.noise?.label(), ui.ambience?.label()).joinToString(" · ") { context.getString(it) }
        .ifEmpty { context.getString(R.string.app_name) }

class ListenActions(
    val noise: (NoiseColour?) -> Unit = {},
    val ambience: (Ambience?) -> Unit = {},
    val noiseLevel: (Float) -> Unit = {},
    val ambienceLevel: (Float) -> Unit = {},
    val timer: (Int?) -> Unit = {},
    val toggle: () -> Unit = {},
    val tooLoud: () -> Unit = {},
    val mode: (Mode) -> Unit = {},
    val tones: ToneActions = ToneActions(),
)

private val gutter = Modifier.padding(horizontal = 20.dp)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ListenScreen(ui: PlayerUi, actions: ListenActions) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).then(gutter)) {
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.listen_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.listen_guide), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                SelectChip(stringResource(R.string.mode_sounds), ui.mode == Mode.SOUNDS, { actions.mode(Mode.SOUNDS) })
                SelectChip(stringResource(R.string.mode_tones), ui.mode == Mode.TONES, { actions.mode(Mode.TONES) })
            }

            if (ui.mode == Mode.TONES) TonesPanel(ui, actions.tones) else SoundLayers(ui, actions)
        }

        // The lower half, for a thumb at night: timer, then Play and, while playing, Too loud.
        Column(gutter.padding(bottom = 12.dp)) {
            Text(stringResource(R.string.listen_timer), style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectChip(stringResource(R.string.timer_off), ui.timerMinutes == null, { actions.timer(null) })
                LevelRules.SLEEP_MINUTES.forEach { m ->
                    SelectChip(stringResource(R.string.timer_minutes, m), ui.timerMinutes == m, { actions.timer(m) })
                }
            }
            val line = when {
                ui.playing && ui.timerRemainingMinutes != null -> stringResource(R.string.timer_remaining, ui.timerRemainingMinutes)
                !ui.playing && ui.status != null -> stringResource(ui.status.message())
                else -> null
            }
            Text(line ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp).heightIn(min = 20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // The screen's one ink action. With no sound picked it stays tappable and says so (Iris).
                Button(
                    onClick = actions.toggle,
                    modifier = Modifier.weight(1f).height(64.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.inverseSurface,
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    ),
                ) {
                    Text(stringResource(when {
                        !ui.hasSound -> R.string.action_pick
                        ui.playing -> R.string.action_pause
                        else -> R.string.action_play
                    }), style = MaterialTheme.typography.titleMedium)
                }
                if (ui.playing) {
                    // Outlined, not tonal: tonal reads as "selected" in Gamme (Iris, 2026-10-31).
                    OutlinedButton(onClick = actions.tooLoud, modifier = Modifier.weight(1f).height(64.dp)) {
                        Text(stringResource(R.string.action_too_loud), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun SoundLayers(ui: PlayerUi, actions: ListenActions) {
    Column {
        Layer(
            title = stringResource(R.string.listen_noise),
            options = listOf(null) + NoiseColour.entries,
            selected = ui.noise,
            label = { it?.label() ?: R.string.listen_none },
            onSelect = actions.noise,
            level = ui.noiseLevel,
            onLevel = actions.noiseLevel,
            note = if (ui.noise == NoiseColour.BROWN) stringResource(R.string.noise_brown_note) else null,
        )
        Spacer(Modifier.height(8.dp))
        Layer(
            title = stringResource(R.string.listen_nature),
            options = listOf(null) + Ambience.entries,
            selected = ui.ambience,
            label = { it?.label() ?: R.string.listen_none },
            onSelect = actions.ambience,
            level = ui.ambienceLevel,
            onLevel = actions.ambienceLevel,
            note = null,
        )
    }
}

@StringRes private fun Status.message() = when (this) {
    Status.TOO_LOUD -> R.string.status_too_loud
    Status.ROUTE -> R.string.status_route
    Status.FOCUS -> R.string.status_focus
    Status.TIMER -> R.string.status_timer
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Layer(
    title: String,
    options: List<T?>,
    selected: T?,
    label: (T?) -> Int,
    onSelect: (T?) -> Unit,
    level: Float,
    onLevel: (Float) -> Unit,
    note: String?,
) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option -> SelectChip(stringResource(label(option)), option == selected, { onSelect(option) }) }
    }
    if (note != null) Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    // Hidden, not disabled, when the row is on None (Iris).
    if (selected != null) {
        val steps = (level * 20).roundToInt()
        val levelText = stringResource(R.string.listen_level, steps)
        val sliderLabel = stringResource(R.string.listen_level_label, stringResource(label(selected)))
        Text(levelText, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        Slider(
            value = level,
            onValueChange = onLevel,
            steps = 19,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics {
                contentDescription = sliderLabel; stateDescription = levelText
            },
        )
    }
}

@Composable
fun JournalScreen(
    checkIns: List<CheckIn>,
    weekSeconds: Long,
    today: LocalDate,
    message: String?,
    onSave: (Int, Int, String) -> Unit,
    onExport: () -> Unit,
) {
    val todays = checkIns.firstOrNull { it.day == today.toString() }
    val start = todays ?: checkIns.firstOrNull()
    var loudness by remember(todays) { mutableIntStateOf(start?.loudness ?: 5) }
    var bother by remember(todays) { mutableIntStateOf(start?.bother ?: 5) }
    var note by remember(todays) { mutableStateOf(todays?.note ?: "") }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).then(gutter)) {
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.journal_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.journal_listened, duration(weekSeconds)), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))

        Text(stringResource(R.string.journal_today), style = MaterialTheme.typography.titleMedium)
        Rating(stringResource(R.string.journal_loudness), null, loudness) { loudness = it }
        Rating(stringResource(R.string.journal_bother), stringResource(R.string.journal_bother_hint), bother) { bother = it }
        OutlinedTextField(
            value = note,
            onValueChange = { note = it.take(CheckIn.NOTE_MAX) },
            label = { Text(stringResource(R.string.journal_note)) },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { onSave(loudness, bother, note) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(if (todays == null) R.string.journal_save else R.string.journal_update))
            }
        }
        if (message != null) Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))

        HorizontalDivider(Modifier.padding(vertical = 16.dp))
        Text(stringResource(R.string.journal_recent), style = MaterialTheme.typography.titleMedium)
        if (checkIns.isEmpty()) {
            EmptyState(stringResource(R.string.journal_empty_title), stringResource(R.string.journal_empty_body))
        } else {
            val format = DateTimeFormatter.ofPattern("EEE d MMM")
            checkIns.take(14).forEach { c ->
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).semantics(mergeDescendants = true) {}) {
                    Text(LocalDate.parse(c.day).format(format), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.journal_entry, c.loudness, c.bother), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (c.note.isNotBlank()) Text(c.note, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 16.dp))
        Text(stringResource(R.string.journal_export_body), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = onExport, modifier = Modifier.padding(vertical = 8.dp).heightIn(min = 48.dp)) {
            Text(stringResource(R.string.journal_export))
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** A 0–10 rating: the number is always written out, never shown by colour or position alone. */
@Composable
private fun Rating(name: String, hint: String?, value: Int, onChange: (Int) -> Unit) {
    val text = stringResource(R.string.journal_rating, name, value)
    Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
    if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Slider(
        value = value.toFloat(),
        onValueChange = { onChange(it.roundToInt()) },
        valueRange = 0f..10f,
        steps = 9,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics { contentDescription = name; stateDescription = text },
    )
}

@Composable
private fun duration(seconds: Long): String {
    val minutes = (seconds / 60).toInt()
    return if (minutes >= 60) stringResource(R.string.duration_hours_minutes, minutes / 60, minutes % 60)
    else stringResource(R.string.duration_minutes, minutes)
}

@Composable
fun AboutScreen(nightHours: Boolean, onNightHours: (Boolean) -> Unit, version: String) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).then(gutter)) {
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.about_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.about_what), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
        Text(stringResource(R.string.about_disclaimer), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
        Text(stringResource(R.string.about_privacy), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))

        SettingsGroup(stringResource(R.string.settings_title), Modifier.padding(top = 16.dp)) {
            SwitchRow(stringResource(R.string.settings_night), stringResource(R.string.settings_night_summary), nightHours, onNightHours)
        }

        SettingsGroup(stringResource(R.string.doctor_title), Modifier.padding(top = 8.dp)) {
            Text(stringResource(R.string.doctor_intro), style = MaterialTheme.typography.bodyMedium)
            listOf(R.string.doctor_1, R.string.doctor_2, R.string.doctor_3, R.string.doctor_4, R.string.doctor_5, R.string.doctor_6).forEach {
                Text("•  " + stringResource(it), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
            }
            Text(stringResource(R.string.support_line), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        }
        Text(stringResource(R.string.about_version, version), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp))
    }
}
