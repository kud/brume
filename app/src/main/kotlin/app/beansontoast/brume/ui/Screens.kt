package app.beansontoast.brume.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.beansontoast.brume.R
import app.beansontoast.brume.core.journal.CheckIn
import app.beansontoast.brume.core.sound.Ambience
import app.beansontoast.brume.core.sound.LevelRules
import app.beansontoast.brume.core.sound.NoiseColour
import app.beansontoast.brume.core.sound.SoundEngine.Mode
import app.beansontoast.brume.playback.PlayerUi
import app.beansontoast.brume.playback.Status
import app.beansontoast.brume.sync.SyncState
import app.beansontoast.design.EmptyState
import app.beansontoast.design.SettingsGroup
import app.beansontoast.design.SwitchRow
import app.beansontoast.design.tabular
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

enum class Tab { LISTEN, TONES, JOURNAL, ABOUT }

@Composable
fun BrumeScaffold(tab: Tab, onTab: (Tab) -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
                NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
                    NavigationBarItem(tab == Tab.LISTEN, { onTab(Tab.LISTEN) }, { Icon(painterResource(R.drawable.ic_horizon), null) }, label = { NavLabel(R.string.tab_listen, tab == Tab.LISTEN) })
                    NavigationBarItem(tab == Tab.TONES, { onTab(Tab.TONES) }, { Icon(painterResource(R.drawable.ic_tones), null) }, label = { NavLabel(R.string.tab_tones, tab == Tab.TONES) })
                    NavigationBarItem(tab == Tab.JOURNAL, { onTab(Tab.JOURNAL) }, { Icon(painterResource(R.drawable.ic_journal), null) }, label = { NavLabel(R.string.tab_journal, tab == Tab.JOURNAL) })
                    NavigationBarItem(tab == Tab.ABOUT, { onTab(Tab.ABOUT) }, { Icon(painterResource(R.drawable.ic_info), null) }, label = { NavLabel(R.string.tab_about, tab == Tab.ABOUT) })
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) { content() }
    }
}

/** The selected tab is told by its indicator and its weight, never by a hue. */
@Composable
private fun NavLabel(@StringRes text: Int, selected: Boolean) =
    Text(stringResource(text), fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)

@StringRes fun NoiseColour.label() = when (this) {
    NoiseColour.WHITE -> R.string.noise_white
    NoiseColour.PINK -> R.string.noise_pink
    NoiseColour.BROWN -> R.string.noise_brown
    NoiseColour.GREY -> R.string.noise_grey
}

@StringRes private fun NoiseColour.hint() = when (this) {
    NoiseColour.WHITE -> R.string.noise_white_hint
    NoiseColour.PINK -> R.string.noise_pink_hint
    NoiseColour.BROWN -> R.string.noise_brown_hint
    NoiseColour.GREY -> R.string.noise_grey_hint
}

@StringRes fun Ambience.label() = when (this) {
    Ambience.RAIN -> R.string.nature_rain
    Ambience.SURF -> R.string.nature_surf
    Ambience.WIND -> R.string.nature_wind
}

@StringRes private fun Ambience.hint() = when (this) {
    Ambience.RAIN -> R.string.nature_rain_hint
    Ambience.SURF -> R.string.nature_surf_hint
    Ambience.WIND -> R.string.nature_wind_hint
}

private fun chosen(context: Context, ui: PlayerUi): String =
    if (ui.mode == Mode.TONES) context.getString(R.string.mode_tones) else listOfNotNull(ui.noise?.label(), ui.ambience?.label()).joinToString(" + ") { context.getString(it) }

fun soundLabel(context: Context, ui: PlayerUi): String = chosen(context, ui).ifEmpty { context.getString(R.string.app_name) }

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
    /** Reopens the playing view from the "now playing" card. */
    val open: () -> Unit = {},
)

internal val gutter = Modifier.padding(horizontal = 20.dp)

@Composable
internal fun Wordmark(modifier: Modifier = Modifier) {
    Text(stringResource(R.string.brand_word), style = MaterialTheme.typography.displaySmall, modifier = modifier.semantics { heading() })
}

@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 20.dp, bottom = 8.dp).semantics { heading() })
}

@Composable
internal fun LevelRow(name: String, character: String?, level: Float, onLevel: (Float) -> Unit) {
    val steps = (level * 20).roundToInt()
    val levelText = stringResource(R.string.listen_level, steps)
    val sliderLabel = stringResource(R.string.listen_level_label, name)
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = MaterialTheme.typography.bodyLarge)
        Text(character?.let { " · $it" } ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(levelText, style = MaterialTheme.typography.bodyMedium.tabular(), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Slider(
        value = level,
        onValueChange = onLevel,
        steps = 19,
        colors = quietSlider(),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics {
            contentDescription = sliderLabel; stateDescription = levelText
        },
    )
}

/** No tick dots: the value is written beside the slider, and twenty dots read as noise. */
@Composable
internal fun quietSlider() = SliderDefaults.colors(activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent)

@Composable
internal fun Dock(ui: PlayerUi, actions: ListenActions, @StringRes timerTitle: Int = R.string.listen_timer) {
    val context = LocalContext.current
    val sound = chosen(context, ui)
    val line = when {
        ui.playing -> stringResource(R.string.listen_state_playing, sound)
        ui.status != null -> stringResource(ui.status.message())
        !ui.hasSound -> stringResource(R.string.listen_state_empty)
        ui.mode == Mode.TONES && ui.tones.match -> stringResource(R.string.listen_state_ready, stringResource(R.string.tones_match))
        else -> stringResource(R.string.listen_state_ready, sound)
    }
    Box(Modifier.fillMaxWidth()) {
        Mist(Modifier.fillMaxWidth().height(132.dp).align(Alignment.Center), strength = if (ui.playing) 1f else 0.35f)
        Column(gutter.padding(top = 8.dp, bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(line, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant, minLines = 2,
                modifier = Modifier.fillMaxWidth().semantics { stateDescription = line })
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                TimerControl(ui, actions.timer, timerTitle, Modifier.weight(1f))
                Spacer(Modifier.width(16.dp))
                PlayDisc(ui, actions.toggle)
                Spacer(Modifier.width(16.dp))
                Box(Modifier.weight(1f)) {
                    // Only while playing (Iris, 2026-10-31). Outlined: tonal would mean "selected".
                    if (ui.playing) {
                        OutlinedButton(onClick = actions.tooLoud, modifier = Modifier.fillMaxWidth().height(56.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Icon(painterResource(R.drawable.ic_too_loud), null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.action_too_loud), style = MaterialTheme.typography.labelLarge, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayDisc(ui: PlayerUi, onToggle: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val word = stringResource(when {
        !ui.hasSound -> R.string.action_pick
        ui.playing -> R.string.action_pause
        else -> R.string.action_play
    })
    val shape = CircleShape
    Box(
        Modifier.size(84.dp).clip(shape)
            .then(if (ui.hasSound) Modifier.background(scheme.inverseSurface) else Modifier.border(2.dp, scheme.outline, shape))
            .clickable(role = Role.Button, onClickLabel = word, onClick = onToggle)
            .semantics { contentDescription = word },
        contentAlignment = Alignment.Center,
    ) {
        when {
            !ui.hasSound -> Icon(painterResource(R.drawable.ic_brume_mark), null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(36.dp))
            ui.playing -> Icon(painterResource(R.drawable.ic_pause), null, tint = scheme.inverseOnSurface, modifier = Modifier.size(36.dp))
            else -> Icon(painterResource(R.drawable.ic_play), null, tint = scheme.inverseOnSurface, modifier = Modifier.size(40.dp))
        }
    }
}

/** One tap steps Off, 20, 45, 90, Off: nothing to open at night. The value is always written. */
@Composable
private fun TimerControl(ui: PlayerUi, onTimer: (Int?) -> Unit, @StringRes titleRes: Int, modifier: Modifier) {
    val options = listOf<Int?>(null) + LevelRules.SLEEP_MINUTES
    val next = options[(options.indexOf(ui.timerMinutes) + 1) % options.size]
    val value = when {
        ui.playing && ui.timerRemainingMinutes != null -> stringResource(R.string.timer_left, ui.timerRemainingMinutes)
        ui.timerMinutes == null -> stringResource(R.string.timer_off)
        else -> stringResource(R.string.timer_minutes, ui.timerMinutes)
    }
    val title = stringResource(titleRes)
    Column(
        modifier.clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.timer_change)) { onTimer(next) }
            .semantics(mergeDescendants = true) { stateDescription = value }
            .heightIn(min = 56.dp).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(value, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@StringRes private fun Status.message() = when (this) {
    Status.TOO_LOUD -> R.string.status_too_loud
    Status.ROUTE -> R.string.status_route
    Status.FOCUS -> R.string.status_focus
    Status.TIMER -> R.string.status_timer
}

@Composable
internal fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
    ) { content() }
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
    val dayFormat = remember { DateTimeFormatter.ofPattern("EEEE d MMMM") }
    val shortFormat = remember { DateTimeFormatter.ofPattern("EEE d MMM") }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).then(gutter)) {
        Text(stringResource(R.string.journal_title), style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = 24.dp).semantics { heading() })
        Text(stringResource(R.string.journal_listened, duration(weekSeconds)), style = MaterialTheme.typography.bodyMedium.tabular(),
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))

        SectionTitle(stringResource(R.string.journal_today) + " · " + today.format(dayFormat))
        Card {
            Rating(stringResource(R.string.journal_loudness), null, loudness) { loudness = it }
            Rating(stringResource(R.string.journal_bother), stringResource(R.string.journal_bother_hint), bother) { bother = it }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(CheckIn.NOTE_MAX) },
                label = { Text(stringResource(R.string.journal_note)) },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
            // The screen's one ink action.
            Button(
                onClick = { onSave(loudness, bother, note) },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.inverseSurface, contentColor = MaterialTheme.colorScheme.inverseOnSurface),
            ) { Text(stringResource(if (todays == null) R.string.journal_save else R.string.journal_update), style = MaterialTheme.typography.labelLarge) }
            if (message != null) {
                Text(message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        }

        SectionTitle(stringResource(R.string.journal_recent))
        if (checkIns.isEmpty()) {
            Card { EmptyState(stringResource(R.string.journal_empty_title), stringResource(R.string.journal_empty_body)) }
        } else {
            Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                checkIns.take(14).forEach { c ->
                    Column(
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer)
                            .padding(horizontal = 16.dp, vertical = 12.dp).semantics(mergeDescendants = true) {},
                    ) {
                        Text(LocalDate.parse(c.day).format(shortFormat), style = MaterialTheme.typography.bodyLarge)
                        Scale(stringResource(R.string.journal_loudness), c.loudness)
                        Scale(stringResource(R.string.journal_bother), c.bother)
                        if (c.note.isNotBlank()) {
                            Text(c.note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }

        SectionTitle(stringResource(R.string.journal_export_title))
        Card {
            Text(stringResource(R.string.journal_export_body), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onExport, modifier = Modifier.padding(top = 12.dp).heightIn(min = 48.dp)) {
                Icon(painterResource(R.drawable.ic_export), null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.journal_export))
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** A day's rating: the word and number, then ten marks, filled up to it and hollow after. */
@Composable
private fun Scale(name: String, value: Int) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.journal_scale, name, value), style = MaterialTheme.typography.bodyMedium.tabular(),
            color = scheme.onSurfaceVariant, modifier = Modifier.width(120.dp))
        Canvas(Modifier.weight(1f).height(10.dp)) {
            val gap = size.width / 10f
            val r = (size.height / 2f).coerceAtMost(gap / 2.6f)
            for (i in 0 until 10) {
                val c = androidx.compose.ui.geometry.Offset(gap * i + gap / 2f, size.height / 2f)
                if (i < value) drawCircle(scheme.onSurface, r, c)
                else drawCircle(scheme.outline, r - density * 0.75f, c, style = Stroke(1.5f * density))
            }
        }
    }
}

/** A 0–10 rating: the number is always written out, never shown by colour or position alone. */
@Composable
private fun Rating(name: String, hint: String?, value: Int, onChange: (Int) -> Unit) {
    val text = stringResource(R.string.journal_rating, name, value)
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("$value", style = MaterialTheme.typography.headlineSmall.tabular())
        Text(stringResource(R.string.journal_of_ten), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
    }
    Slider(
        value = value.toFloat(),
        onValueChange = { onChange(it.roundToInt()) },
        valueRange = 0f..10f,
        steps = 9,
        colors = quietSlider(),
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
fun AboutScreen(
    nightHours: Boolean,
    onNightHours: (Boolean) -> Unit,
    version: String,
    onIntro: () -> Unit = {},
    sync: SyncState = SyncState(),
    syncMessage: String? = null,
    syncActions: SyncActions = SyncActions(),
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(148.dp)) {
            Mist(Modifier.fillMaxSize(), strength = 0.6f)
            Column(gutter.align(Alignment.BottomStart).padding(bottom = 8.dp)) {
                Wordmark()
                Text(stringResource(R.string.about_what), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 4.dp))
            }
        }
        Column(gutter) {
            Text(stringResource(R.string.about_disclaimer), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))

            Card(Modifier.padding(top = 16.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(painterResource(R.drawable.ic_lock), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp).size(20.dp))
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(stringResource(R.string.about_privacy_title), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.about_privacy), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }

            SettingsGroup(stringResource(R.string.settings_title), Modifier.padding(top = 8.dp)) {
                SwitchRow(stringResource(R.string.settings_night), stringResource(R.string.settings_night_summary), nightHours, onNightHours)
            }

            SyncSettings(sync, syncMessage, syncActions)

            SettingsGroup(stringResource(R.string.doctor_title)) {
                Text(stringResource(R.string.doctor_intro), style = MaterialTheme.typography.bodyMedium)
                listOf(R.string.doctor_1, R.string.doctor_2, R.string.doctor_3, R.string.doctor_4, R.string.doctor_5, R.string.doctor_6).forEach {
                    Row(Modifier.padding(top = 4.dp)) {
                        Text("–", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(16.dp))
                        Text(stringResource(it), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Text(stringResource(R.string.support_line), style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedButton(onIntro, Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 48.dp)) {
                Text(stringResource(R.string.about_intro))
            }
            Text(stringResource(R.string.about_version, version), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp))
        }
    }
}
