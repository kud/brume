package app.beansontoast.brume.ui

import app.beansontoast.design.WeightSwap
import app.beansontoast.design.Motion
import androidx.compose.animation.Crossfade
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.statusBarsPadding

import app.beansontoast.brume.Modules

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.unit.sp
import app.beansontoast.brume.R
import app.beansontoast.brume.core.region.Need
import app.beansontoast.brume.core.region.Regions
import app.beansontoast.brume.core.journal.CheckIn
import app.beansontoast.brume.core.journal.NoteTags
import app.beansontoast.brume.core.learn.Learn
import app.beansontoast.brume.core.learn.Tag
import app.beansontoast.brume.core.sound.Ambience
import app.beansontoast.brume.core.sound.LevelRules
import app.beansontoast.brume.core.sound.NoiseColour
import app.beansontoast.brume.core.sound.Sound
import app.beansontoast.brume.core.sound.SoundEngine.Mode
import app.beansontoast.brume.playback.PlayerUi
import app.beansontoast.brume.playback.Status
import app.beansontoast.brume.sync.SyncState
import app.beansontoast.design.EmptyState
import app.beansontoast.design.SettingsGroup
import app.beansontoast.design.SwitchRow
import app.beansontoast.design.calmAlpha
import app.beansontoast.design.tabular
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

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
    Ambience.RAIN_TERRACE -> R.string.nature_rain_terrace
    Ambience.STORM -> R.string.nature_storm
    Ambience.WIND -> R.string.nature_wind
    Ambience.SEA -> R.string.nature_sea
    Ambience.STREAM -> R.string.nature_stream
    Ambience.BIRDS -> R.string.nature_birds
    Ambience.NIGHT -> R.string.nature_night
    Ambience.FIRE -> R.string.nature_fire
    Ambience.CAFE -> R.string.nature_cafe
    Ambience.TRAIN -> R.string.nature_train
}

@StringRes fun Sound.label() = when (this) {
    is Sound.Noise -> colour.label()
    is Sound.Recorded -> ambience.label()
    is Sound.Band -> R.string.sound_band
}


internal fun chosenSound(context: Context, ui: PlayerUi): String = chosen(context, ui)

private fun chosen(context: Context, ui: PlayerUi): String =
    if (ui.mode == Mode.TONES) context.getString(R.string.mode_tones) else ui.sounds.joinToString(" + ") { context.getString(it.label()) }

fun soundLabel(context: Context, ui: PlayerUi): String = chosen(context, ui).ifEmpty { context.getString(R.string.app_name) }

class ListenActions(
    /** The mixer: add a sound as a new layer, take one out, set one's level or (a noise's) tone. */
    val add: (Sound) -> Unit = {},
    val remove: (Sound) -> Unit = {},
    val level: (Sound, Float) -> Unit = { _, _ -> },
    val tone: (Sound, Float) -> Unit = { _, _ -> },
    val timer: (Int?) -> Unit = {},
    val toggle: () -> Unit = {},
    val tooLoud: () -> Unit = {},
    val mode: (Mode) -> Unit = {},
    val tones: ToneActions = ToneActions(),
    /** Reopens the playing view from the "now playing" card. */
    val open: () -> Unit = {},
    val balance: (Float) -> Unit = {},
    /** Opens the system's output switcher. */
    val output: () -> Unit = {},
)

internal val gutter = Modifier.padding(horizontal = 20.dp)

/**
 * The frame owns the title (Iris, 2026-11-01, phase 2): the name centred under the ✕ is the
 * screen's only heading. A screen may add one quiet centred line under it, never a second title.
 */
@Composable
internal fun ScreenLead(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodyMedium.tabular(), textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp))
}

@Composable
internal fun Wordmark(modifier: Modifier = Modifier) {
    // The wordmark lives in the launcher, the splash and the introduction, never as a screen title.
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

/**
 * A slider drawn as a line (Iris, 2026-11-01, phase 3): a hairline track, the part already set a
 * little heavier, and a small open ring for the thumb. The value is told by position and in words
 * beside it, never by colour; the touch target stays Material's full height.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun LineSlider(
    value: Float, onValueChange: (Float) -> Unit, modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f, steps: Int = 0,
) {
    val scheme = MaterialTheme.colorScheme
    val ink = scheme.onSurface
    val quiet = scheme.outline.copy(alpha = 0.45f)
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    androidx.compose.material3.Slider(
        value = value, onValueChange = onValueChange, modifier = modifier, steps = steps, valueRange = valueRange,
        interactionSource = source,
        thumb = {
            androidx.compose.foundation.layout.Box(
                Modifier.size(18.dp)
                    .background(scheme.surfaceContainerLowest, androidx.compose.foundation.shape.CircleShape)
                    .border(1.5.dp, ink, androidx.compose.foundation.shape.CircleShape),
            )
        },
        track = { state ->
            androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(18.dp)) {
                val span = state.valueRange.endInclusive - state.valueRange.start
                val f = if (span <= 0f) 0f else ((state.value - state.valueRange.start) / span).coerceIn(0f, 1f)
                val y = size.height / 2
                drawLine(quiet, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), 1.dp.toPx())
                drawLine(ink, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width * f, y), 2.dp.toPx(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round)
            }
        },
    )
}

/** A hairline between rows: a step lighter than the outline, one pixel. */
@Composable
internal fun Hairline(modifier: Modifier = Modifier) =
    androidx.compose.foundation.layout.Box(modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)))

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
    // Pinned at the page's foot, so it stands clear of the floating bar.
    Box(Modifier.fillMaxWidth().padding(bottom = chromeBottom())) {
        val strength = if (ui.playing) 1f else 0.35f
        val mistInk = MaterialTheme.colorScheme.onSurfaceVariant
        Mist(Modifier.fillMaxWidth().height(132.dp).align(Alignment.Center), strength = strength, color = mistInk)
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
internal fun TimerControl(ui: PlayerUi, onTimer: (Int?) -> Unit, @StringRes titleRes: Int, modifier: Modifier) {
    val options = listOf<Int?>(null) + LevelRules.SLEEP_MINUTES
    val next = options[(options.indexOf(ui.timerMinutes) + 1) % options.size]
    val value = when {
        ui.playing && ui.timerRemainingMinutes != null -> stringResource(R.string.timer_left, ui.timerRemainingMinutes)
        ui.timerMinutes == null -> stringResource(R.string.timer_off)
        else -> stringResource(R.string.timer_minutes, ui.timerMinutes)
    }
    val title = stringResource(titleRes)
    // The hourglass marks the control; the title and the value stay written beside it.
    Row(
        modifier.clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.timer_change)) { onTimer(next) }
            .semantics(mergeDescendants = true) { stateDescription = value }
            .heightIn(min = 56.dp).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_timer), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Column(Modifier.padding(start = 10.dp), verticalArrangement = Arrangement.Center) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(value, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

@StringRes internal fun Status.message() = when (this) {
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

/**
 * The Journal, one screen (Iris, 2026-10-31, after Erwann reversed the one-question flow): how loud,
 * how much room, a note with four tags, Save. Tags ride in the note (NoteTags), so nothing in storage
 * changes. A tag leads to its Learn card as an invitation, never as an insight.
 */
@Composable
fun JournalScreen(
    checkIns: List<CheckIn>,
    weekSeconds: Long,
    today: LocalDate,
    message: String?,
    onSave: (Int, Int, String) -> Unit,
    onExport: () -> Unit,
    onCard: (String) -> Unit = {},
    noteOpen: Boolean = false,
) {
    val todays = checkIns.firstOrNull { it.day == today.toString() }
    val start = todays ?: checkIns.firstOrNull()
    val todaysParts = remember(todays) { NoteTags.parse(todays?.note ?: "") }
    var loudness by remember(todays) { mutableIntStateOf(start?.loudness ?: 5) }
    var bother by remember(todays) { mutableIntStateOf(start?.bother ?: 5) }
    var note by remember(todays) { mutableStateOf(todaysParts.text) }
    var tags by remember(todays) { mutableStateOf(todaysParts.tags) }
    var day by remember(today) { mutableStateOf(today) }
    var writing by remember { mutableStateOf(noteOpen) }
    val dayFormat = remember { DateTimeFormatter.ofPattern("EEEE d MMMM") }
    val byDay = remember(checkIns) { checkIns.associateBy { it.day } }

    // The journal as one frame (Iris, 2026-11-01, garden second pass): a week of seven days with
    // ‹ › to step back through the weeks, the chosen day below it, and the note in a sheet so the
    // field never sits under the bar. Today is asked; another day is read.
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().then(gutter).padding(bottom = chromeBottom() + 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(48.dp))
                ScreenLead(stringResource(R.string.journal_listened, duration(weekSeconds)), Modifier.weight(1f))
                val export = stringResource(R.string.journal_export)
                IconButton(onExport, Modifier.semantics { contentDescription = export }) {
                    Icon(painterResource(R.drawable.ic_export), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
            }
            WeekRow(today, day, byDay, Modifier.padding(top = 12.dp)) { day = it }
            SectionTitle((if (day == today) stringResource(R.string.journal_today) + " · " else "") + day.format(dayFormat))
            Crossfade(day == today, Modifier.weight(1f), animationSpec = Motion.tween(Motion.CALM), label = "day") { isToday ->
                if (isToday) Column(Modifier.fillMaxSize()) {
                    Question(stringResource(R.string.journal_q_loud), stringResource(R.string.journal_loudness), loudness,
                        stringResource(R.string.journal_q_loud_low), stringResource(R.string.journal_q_loud_high)) { loudness = it }
                    Spacer(Modifier.height(4.dp))
                    Question(stringResource(R.string.journal_q_place), stringResource(R.string.journal_bother), bother,
                        stringResource(R.string.journal_q_place_low), stringResource(R.string.journal_q_place_high)) { bother = it }
                    // The note as one line that opens its sheet: the words written so far, or an invitation.
                    val written = listOf(tagWords(tags), note.trim()).filter { it.isNotEmpty() }.joinToString(" · ")
                    NavRow(stringResource(R.string.journal_q_note), written.ifEmpty { stringResource(R.string.journal_note_add) }) { writing = true }
                    Spacer(Modifier.weight(1f))
                    // The screen's one ink action.
                    Button(
                        onClick = { onSave(loudness, bother, NoteTags.compose(note, tags)) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.inverseSurface, contentColor = MaterialTheme.colorScheme.inverseOnSurface),
                    ) {
                        Text(stringResource(if (todays == null) R.string.journal_save else R.string.journal_update), style = MaterialTheme.typography.labelLarge)
                    }
                    // Its line is always there, so the confirmation fades in without moving anything (Iris, 2026-11-01).
                    Text(message ?: "", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, minLines = 1,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).alpha(calmAlpha(message != null)))
                } else PastDay(byDay[day.toString()], onCard)
            }
        }
        QuietSheet(writing, { writing = false }, stringResource(R.string.journal_q_note)) {
            Text(stringResource(R.string.journal_q_note_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            TagChips(tags) { tag -> tags = if (tag in tags) tags - tag else tags + tag }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(CheckIn.NOTE_MAX - 60) },
                label = { Text(stringResource(R.string.journal_note)) },
                minLines = 3, maxLines = 3,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
        }
    }
}

/**
 * Seven days, Monday first, between ‹ and ›: each its initial and its date, a short rule under a
 * day with an entry, and the chosen one in weight with a ring. Days to come are faint and can't be
 * chosen; the week never steps past today's.
 */
@Composable
private fun WeekRow(today: LocalDate, chosen: LocalDate, byDay: Map<String, CheckIn>, modifier: Modifier, onDay: (LocalDate) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val monday = chosen.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
    val thisMonday = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
    val initial = remember { DateTimeFormatter.ofPattern("EEEEE") }
    val spoken = remember { DateTimeFormatter.ofPattern("EEEE d MMMM") }
    val noted = stringResource(R.string.journal_day_noted)
    val back = stringResource(R.string.journal_week_previous)
    val next = stringResource(R.string.journal_week_next)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton({ onDay(chosen.minusWeeks(1)) }, Modifier.semantics { contentDescription = back }) {
            Text("‹", style = MaterialTheme.typography.titleLarge, color = scheme.onSurfaceVariant)
        }
        Row(Modifier.weight(1f).selectableGroup()) {
            for (i in 0 until 7) {
                val d = monday.plusDays(i.toLong())
                val on = d == chosen
                val future = d.isAfter(today)
                val has = byDay.containsKey(d.toString())
                Column(
                    Modifier.weight(1f).heightIn(min = 64.dp).clip(MaterialTheme.shapes.medium)
                        .then(if (on) Modifier.border(1.dp, scheme.outline, MaterialTheme.shapes.medium) else Modifier)
                        .selectable(on, enabled = !future, role = Role.Tab) { onDay(d) }
                        .semantics(mergeDescendants = true) { contentDescription = d.format(spoken) + if (has) ", $noted" else "" }
                        .alpha(if (future) 0.38f else 1f),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    WeightSwap(d.format(initial), on, MaterialTheme.typography.labelMedium, onColor = scheme.onSurface, offColor = scheme.onSurfaceVariant)
                    WeightSwap("${d.dayOfMonth}", on, MaterialTheme.typography.titleMedium.tabular(), onColor = scheme.onSurface, offColor = scheme.onSurface)
                    Box(Modifier.padding(top = 4.dp).width(12.dp).height(2.dp).background(if (has) scheme.onSurface else Color.Transparent))
                }
            }
        }
        val later = monday.plusWeeks(1)
        IconButton({ onDay(minOf(today, chosen.plusWeeks(1))) }, enabled = !later.isAfter(thisMonday),
            modifier = Modifier.semantics { contentDescription = next }) {
            Text("›", style = MaterialTheme.typography.titleLarge, color = scheme.onSurfaceVariant.copy(alpha = if (!later.isAfter(thisMonday)) 1f else 0.38f))
        }
    }
}

/** A day before today, read: its two scales, its tags and note, and a card to read if a tag points to one. */
@Composable
private fun PastDay(c: CheckIn?, onCard: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        if (c == null) {
            Text(stringResource(R.string.journal_day_empty), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            return@Column
        }
        Scale(stringResource(R.string.journal_loudness), c.loudness)
        Scale(stringResource(R.string.journal_bother), c.bother)
        val parts = NoteTags.parse(c.note)
        val words = tagWords(parts.tags)
        if (words.isNotEmpty()) Text(words, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 16.dp))
        if (parts.text.isNotBlank()) Text(parts.text, style = MaterialTheme.typography.bodyLarge, maxLines = 6,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
        val card = parts.tags.firstOrNull()?.let { Learn.card(it.card) }
        if (card != null) {
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.journal_invite_title, stringResource(parts.tags.first().word())), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            NavRow(cardTitle(card)) { onCard(card.id) }
        }
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

/** One question: the words large, the number written, ten steps, and what each end means. */
@Composable
private fun Question(question: String, name: String, value: Int, low: String, high: String, onChange: (Int) -> Unit) {
    val text = stringResource(R.string.journal_rating, name, value)
    // The number stays written, on the question's own line, in the serif like Listen's titles.
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
        Text(question, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(bottom = 4.dp).semantics { heading() })
        Text("$value", style = MaterialTheme.typography.headlineSmall.tabular(), modifier = Modifier.padding(start = 12.dp))
        Text(stringResource(R.string.journal_of_ten), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, bottom = 5.dp))
    }
    Slider(
        value = value.toFloat(), onValueChange = { onChange(it.roundToInt()) }, valueRange = 0f..10f, steps = 9, colors = quietSlider(),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { contentDescription = question; stateDescription = text },
    )
    Row(Modifier.fillMaxWidth()) {
        Text(low, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(high, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@StringRes internal fun Tag.word() = when (this) {
    Tag.CAFFEINE -> R.string.tag_caffeine
    Tag.ALCOHOL -> R.string.tag_alcohol
    Tag.STRESS -> R.string.tag_stress
    Tag.POOR_SLEEP -> R.string.tag_poor_sleep
}

@Composable
private fun tagWords(tags: Set<Tag>): String = Tag.entries.filter { it in tags }.map { stringResource(it.word()) }.joinToString(" · ")

/**
 * Tags to notice, as Listen's pills (Iris, 2026-11-01): round, in one flowing row, with a glyph in
 * both states, + to add and a check once added, and weight, so the state never rests on colour.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun TagChips(tags: Set<Tag>, onToggle: (Tag) -> Unit) {
    val s = MaterialTheme.colorScheme
    androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth().padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Tag.entries.forEach { tag ->
            val on = tag in tags
            val ink = if (on) s.onSecondaryContainer else s.onSurface
            Row(
                Modifier.clip(CircleShape)
                    .then(if (on) Modifier.background(s.secondaryContainer) else Modifier.border(1.dp, s.outlineVariant, CircleShape))
                    .toggleable(value = on, role = Role.Checkbox) { onToggle(tag) }
                    .heightIn(min = 48.dp).padding(start = 12.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(if (on) app.beansontoast.design.R.drawable.ic_check else R.drawable.ic_plus), null,
                    tint = if (on) ink else s.onSurfaceVariant, modifier = Modifier.padding(end = 6.dp).size(16.dp))
                Text(stringResource(tag.word()), style = MaterialTheme.typography.labelLarge, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, color = ink)
            }
        }
    }
}

@Composable
private fun duration(seconds: Long): String {
    val minutes = (seconds / 60).toInt()
    return if (minutes >= 60) stringResource(R.string.duration_hours_minutes, minutes / 60, minutes % 60)
    else stringResource(R.string.duration_minutes, minutes)
}

/**
 * The Learn tab (Erwann, 2026-10-31: « Comprendre à la place d'À propos »): the decibel dial and the
 * advice cards first (Iris, 2026-11-01; they replace the quiz); then the
 * library's seven topics, when to see a doctor, and two rows, Settings and About Brume.
 */
@Composable
fun LearnScreen(
    onTopic: (Int) -> Unit = {},
    onSettings: () -> Unit = {},
    onAbout: () -> Unit = {},
    onCard: (String) -> Unit = {},
    still: Boolean? = null,
    onFinder: () -> Unit = {},
    startPage: Int = 0,
    doctorOpen: Boolean = false,
    /** Where the Décibels page's dial starts (renders and FitTest). */
    loudnessAt: Int = 60,
) {
    // One frame, no free scroll (Iris, 2026-11-01): three pages named by words, changed by a tap,
    // so a swipe always belongs to the advice cards; the doctor, settings and About sit at the foot.
    val pager = rememberPagerState(initialPage = startPage) { 3 }
    var doctor by remember { mutableStateOf(doctorOpen) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().then(gutter).underChrome()) {
            ScreenLead(stringResource(R.string.learn_intro))
            PagerWords(pager, listOf(stringResource(R.string.learn_page_advice), stringResource(R.string.learn_library), stringResource(R.string.learn_page_loudness)),
                Modifier.padding(top = 4.dp))
            HorizontalPager(pager, userScrollEnabled = false, verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 12.dp).clipToBounds()) { page ->
                when (page) {
                    0 -> AdviceDeck(onCard = onCard, titled = false, deck = Modifier.fillMaxSize())
                    1 -> LibraryTiles(onTopic, onFinder, Modifier.fillMaxSize())
                    else -> DecibelExplorer(initial = loudnessAt, still = still, onCard = onCard, titled = false)
                }
            }
            HorizontalDivider(Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                FootWord(stringResource(R.string.learn_doctor_short)) { doctor = true }
                FootWord(stringResource(R.string.settings_title), onSettings)
                FootWord(stringResource(R.string.about_short), onAbout)
            }
        }
        QuietSheet(doctor, { doctor = false }, stringResource(R.string.doctor_title)) { DoctorLines(onSettings) }
    }
}

@Composable
private fun FootWord(word: String, onClick: () -> Unit) {
    Text(word + " ›", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick).padding(vertical = 14.dp, horizontal = 2.dp))
}

/**
 * When to see a doctor: the red flags, the same everywhere, then the country's own lines (sudden
 * hearing loss, emergency, crisis) with tappable numbers, and which country they are for.
 */
@Composable
fun DoctorGroup(onSettings: (() -> Unit)? = null) {
    SettingsGroup(stringResource(R.string.doctor_title)) { DoctorLines(onSettings) }
}

/** The doctor group's words, also the Learn foot's sheet. */
@Composable
internal fun DoctorLines(onSettings: (() -> Unit)?) {
    run {
        Text(stringResource(R.string.doctor_intro), style = MaterialTheme.typography.bodyMedium)
        listOf(R.string.doctor_1, R.string.doctor_2, R.string.doctor_3, R.string.doctor_4, R.string.doctor_5, R.string.doctor_6).forEach {
            Row(Modifier.padding(top = 4.dp)) {
                Text("–", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(16.dp))
                Text(stringResource(it), style = MaterialTheme.typography.bodyMedium)
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
        RegionLines(Regions.redFlags)
        RegionFor(onSettings, Modifier.padding(top = 10.dp))
    }
}

/**
 * About Brume, one row away from Learn, in one frame (the no-scroll pass): three pages named by
 * words, as Learn's are. Brume: what it is, the disclaimer and where to find help now. On this
 * phone: privacy, the introduction again, and the updates (github build). Sources: the guidance,
 * the recordings' credits, and the wordmark with the version at its foot.
 */
@Composable
fun AboutScreen(
    version: String,
    onIntro: () -> Unit = {},
    onBack: () -> Unit = {},
    /** The github build's Updates section (src/github); the fdroid build has none. */
    updates: @Composable () -> Unit = {},
    startPage: Int = 0,
) {
    val pager = rememberPagerState(initialPage = startPage) { 3 }
    Column(Modifier.fillMaxSize().then(gutter).underChrome()) {
        PagerWords(pager, listOf(stringResource(R.string.about_page_brume), stringResource(R.string.about_page_privacy), stringResource(R.string.about_sources_title)))
        HorizontalPager(pager, userScrollEnabled = false, verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp).clipToBounds()) { page ->
            Column(Modifier.fillMaxSize()) {
                when (page) {
                    0 -> {
                        Text(stringResource(R.string.about_what), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 4.dp))
                        Text(stringResource(R.string.about_disclaimer), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
                        SectionTitle(stringResource(R.string.about_help_title))
                        RegionLines(listOf(Need.CRISIS, Need.EMERGENCY))
                    }
                    1 -> {
                        Card(Modifier.padding(top = 4.dp)) {
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(painterResource(R.drawable.ic_lock), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp).size(20.dp))
                                Column(Modifier.padding(start = 12.dp)) {
                                    Text(stringResource(R.string.about_privacy_title), style = MaterialTheme.typography.titleSmall)
                                    Text(stringResource(R.string.about_privacy), style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                                }
                            }
                        }
                        OutlinedButton(onIntro, Modifier.fillMaxWidth().padding(top = 16.dp).heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.about_intro))
                        }
                        updates()
                    }
                    else -> {
                        Text(stringResource(R.string.about_sources), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                        // The recordings' credits (SOUNDS.md lists each file, its author and its page).
                        Text(stringResource(R.string.about_sounds_credits), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                        Spacer(Modifier.weight(1f))
                        // The brand signs About at its foot, never as a title (Erwann, 2026-10-31).
                        Text(stringResource(R.string.brand_word), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp))
                        Text(stringResource(R.string.about_version, version), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/**
 * Settings, one row away from Learn, in one frame (Iris, 2026-11-01): night hours as a switch, and
 * one row per setting, its current value written beside it, each opening a quiet sheet of five rows
 * at most. Where the sound plays opens the system's own switcher, so Brume never picks for you.
 */
@Composable
fun SettingsScreen(
    nightHours: Boolean,
    onNightHours: (Boolean) -> Unit,
    onBack: () -> Unit,
    sync: SyncState = SyncState(),
    syncMessage: String? = null,
    syncActions: SyncActions = SyncActions(),
    language: LanguageChoice? = null,
    country: CountryChoice? = null,
    modules: Modules = Modules(),
    onModules: (Modules) -> Unit = {},
    onOutput: () -> Unit = {},
    sheet: SettingsSheet? = null,
) {
    var open by remember { mutableStateOf(sheet) }
    val line = MaterialTheme.colorScheme.outlineVariant
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().then(gutter).underChrome()) {
            SwitchRow(stringResource(R.string.settings_night), stringResource(R.string.settings_night_summary), nightHours, onNightHours)
            HorizontalDivider(color = line)
            val on = listOf(modules.breath, modules.meditation, modules.tones).count { it } + 1
            NavRow(stringResource(R.string.modules_title), trailing = stringResource(R.string.modules_count, on, 4)) { open = SettingsSheet.MODULES }
            if (language != null) {
                HorizontalDivider(color = line)
                NavRow(stringResource(R.string.language_title), trailing = languageName(language.tag)) { open = SettingsSheet.LANGUAGE }
            }
            if (country != null) {
                HorizontalDivider(color = line)
                NavRow(stringResource(R.string.country_title), trailing = countryName(country.chosen ?: country.guess)) { open = SettingsSheet.COUNTRY }
            }
            HorizontalDivider(color = line)
            NavRow(stringResource(R.string.sync_title), if (sync.hasFolder) syncSummary(sync) else stringResource(R.string.sync_row_off)) { open = SettingsSheet.SYNC }
            HorizontalDivider(color = line)
            NavRow(stringResource(R.string.import_title)) { open = SettingsSheet.IMPORT }
            HorizontalDivider(color = line)
            NavRow(stringResource(R.string.settings_output), onClick = onOutput)
        }
        QuietSheet(open == SettingsSheet.MODULES, { open = null }, stringResource(R.string.modules_title)) { ModuleLines(modules, onModules) }
        if (language != null) QuietSheet(open == SettingsSheet.LANGUAGE, { open = null }, stringResource(R.string.language_title)) { LanguageChips(language) }
        if (country != null) QuietSheet(open == SettingsSheet.COUNTRY, { open = null }, stringResource(R.string.country_title)) { CountryChips(country) }
        QuietSheet(open == SettingsSheet.SYNC, { open = null }, stringResource(R.string.sync_title)) { SyncLines(sync, syncMessage, syncActions) }
        QuietSheet(open == SettingsSheet.IMPORT, { open = null }, stringResource(R.string.import_title)) { ImportLines(syncActions, syncMessage) }
    }
}

enum class SettingsSheet { MODULES, LANGUAGE, COUNTRY, SYNC, IMPORT }

/**
 * The switchable modules. Listen is always there, said in words rather than shown as a switch
 * that can't move; each other row is a switch whose state is its thumb's place, not a hue.
 */
@Composable
fun ModuleSettings(modules: Modules, onModules: (Modules) -> Unit) {
    SettingsGroup(stringResource(R.string.modules_title)) { ModuleLines(modules, onModules) }
}

/** The intro, Listen in words and a switch per other module: the body of Settings' modules sheet. */
@Composable
internal fun ModuleLines(modules: Modules, onModules: (Modules) -> Unit) {
    run {
        Text(stringResource(R.string.modules_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(stringResource(R.string.tab_listen), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.module_listen_summary), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // Calm holds two practices, each with its own switch: turning both off takes the Calm tab away.
        SwitchRow(stringResource(R.string.tab_breath), stringResource(R.string.module_breath_summary), modules.breath) { onModules(modules.copy(breath = it)) }
        SwitchRow(stringResource(R.string.practice_meditation), stringResource(R.string.module_meditation_summary), modules.meditation) { onModules(modules.copy(meditation = it)) }
        SwitchRow(stringResource(R.string.tab_tones), stringResource(R.string.module_tones_summary), modules.tones) { onModules(modules.copy(tones = it)) }
    }
}
