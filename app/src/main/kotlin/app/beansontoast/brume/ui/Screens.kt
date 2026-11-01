package app.beansontoast.brume.ui

import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import app.beansontoast.brume.ui.launch.LocalLaunchAnchors
import app.beansontoast.brume.ui.launch.MistAnchor

import app.beansontoast.brume.Modules

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

/**
 * Each tab is a category used directly (Erwann, 2026-10-31, reversing the guide): Listen first, then
 * Calm, Tones, Journal, and Learn, which holds the cards, the settings and About Brume.
 */
enum class Tab { LISTEN, CALM, TONES, JOURNAL, LEARN }

/** Listen, Journal and Learn are always there; the others follow their module switch, Calm while Breathe or Meditation is on (ADR 0003). */
fun Modules.shows(tab: Tab) = when (tab) { Tab.TONES -> tones; Tab.CALM -> calm; else -> true }

@Composable
fun BrumeScaffold(tab: Tab, onTab: (Tab) -> Unit, modules: Modules = Modules(), mini: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
    // A few words across the top (Erwann chose A's shape, 2026-10-31): Listen, Journal, Learn.
    // Breathe and Tones are scenes inside Listen, so they are not here twice; [modules] only
    // decides which scenes Listen shows.
    val here = when (tab) { Tab.CALM, Tab.TONES -> Tab.LISTEN; else -> tab }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(Tab.LISTEN to R.string.tab_listen, Tab.JOURNAL to R.string.tab_journal, Tab.LEARN to R.string.tab_learn).forEach { (t, label) ->
                    TopWord(label, here == t) { onTab(t) }
                }
            }
        },
        bottomBar = { mini?.invoke() },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) { content() }
    }
}

/**
 * The page you are on is told by weight and an underline, never by a hue (Erwann, 2026-10-31: the
 * words were hard to see). The others stay in the full ink, a little lighter, and every word has a
 * 48 dp target at least.
 */
@Composable
private fun TopWord(@StringRes text: Int, selected: Boolean, onClick: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onSurface
    Box(
        Modifier.heightIn(min = 56.dp).widthIn(min = 48.dp).selectable(selected = selected, role = Role.Tab, onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(stringResource(text), style = MaterialTheme.typography.titleMedium, color = if (selected) ink else ink.copy(alpha = 0.8f), maxLines = 1,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            textDecoration = if (selected) TextDecoration.Underline else null)
    }
}

/**
 * The sound playing, on every tab but Listen (Erwann, 2026-10-31): its name and Pause, under a hair
 * line, in the ink of the top words. Nothing else; Listen has the rest.
 */
@Composable
fun MiniPlayer(name: String, onPause: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val pause = stringResource(R.string.action_pause)
    Column(Modifier.fillMaxWidth().background(scheme.background).navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(scheme.outlineVariant))
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Row(Modifier.heightIn(min = 48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onPause).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_pause), null, modifier = Modifier.size(20.dp))
                Text(pause, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

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

internal fun chosenSound(context: Context, ui: PlayerUi): String = chosen(context, ui)

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
    val balance: (Float) -> Unit = {},
    /** Opens the system's output switcher. */
    val output: () -> Unit = {},
)

internal val gutter = Modifier.padding(horizontal = 20.dp)

/**
 * Every top-level screen is titled by its tab's label, in the serif headline, with an optional
 * quiet line under it (Iris, 2026-10-31, from Erwann: « brume partout ou jamais » — never).
 */
@Composable
internal fun ScreenTitle(@StringRes title: Int, subtitle: String? = null) {
    Text(stringResource(title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 24.dp).semantics { heading() })
    if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium.tabular(),
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
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
        // The launch intro lands Horizon's lines on this mist (ui/launch).
        val anchors = LocalLaunchAnchors.current
        val strength = if (ui.playing) 1f else 0.35f
        val mistInk = MaterialTheme.colorScheme.onSurfaceVariant
        Mist(Modifier.fillMaxWidth().height(132.dp).align(Alignment.Center)
            .onGloballyPositioned { anchors?.mist = MistAnchor(it.boundsInWindow(), strength, mistInk) }, strength = strength, color = mistInk)
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
) {
    val todays = checkIns.firstOrNull { it.day == today.toString() }
    val start = todays ?: checkIns.firstOrNull()
    val todaysParts = remember(todays) { NoteTags.parse(todays?.note ?: "") }
    var loudness by remember(todays) { mutableIntStateOf(start?.loudness ?: 5) }
    var bother by remember(todays) { mutableIntStateOf(start?.bother ?: 5) }
    var note by remember(todays) { mutableStateOf(todaysParts.text) }
    var tags by remember(todays) { mutableStateOf(todaysParts.tags) }
    val dayFormat = remember { DateTimeFormatter.ofPattern("EEEE d MMMM") }
    val shortFormat = remember { DateTimeFormatter.ofPattern("EEE d MMM") }

    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize().scrollCue(scroll).verticalScroll(scroll).then(gutter)) {
        ScreenTitle(R.string.tab_journal, stringResource(R.string.journal_listened, duration(weekSeconds)))

        SectionTitle(stringResource(R.string.journal_today) + " · " + today.format(dayFormat))
        // Today's entry sits on the ground like Listen, not in a card (Iris, 2026-11-01, dev.15):
        // the questions are the structure, and it all fits above the fold, Save included, so the
        // scroll cue never fades over the button.
        Column {
            Question(stringResource(R.string.journal_q_loud), stringResource(R.string.journal_loudness), loudness,
                stringResource(R.string.journal_q_loud_low), stringResource(R.string.journal_q_loud_high)) { loudness = it }
            Spacer(Modifier.height(4.dp))
            Question(stringResource(R.string.journal_q_place), stringResource(R.string.journal_bother), bother,
                stringResource(R.string.journal_q_place_low), stringResource(R.string.journal_q_place_high)) { bother = it }
            Text(stringResource(R.string.journal_q_note), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 20.dp).semantics { heading() })
            Text(stringResource(R.string.journal_q_note_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            TagChips(tags) { tag -> tags = if (tag in tags) tags - tag else tags + tag }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(CheckIn.NOTE_MAX - 60) },
                label = { Text(stringResource(R.string.journal_note)) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            // The screen's one ink action.
            Button(
                onClick = { onSave(loudness, bother, NoteTags.compose(note, tags)) },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp).heightIn(min = 52.dp), shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.inverseSurface, contentColor = MaterialTheme.colorScheme.inverseOnSurface),
            ) {
                Text(stringResource(if (todays == null) R.string.journal_save else R.string.journal_update), style = MaterialTheme.typography.labelLarge)
            }
        }
        if (message != null) {
            Text(message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        }

        // An invitation from the latest day with a tag, today or yesterday: one card, never a finding.
        val invite = checkIns.filter { it.day == today.toString() || it.day == today.minusDays(1).toString() }
            .firstNotNullOfOrNull { NoteTags.parse(it.note).tags.firstOrNull() }
        if (invite != null) {
            val card = Learn.card(invite.card)
            if (card != null) Column(
                Modifier.padding(top = 14.dp).fillMaxWidth().clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow).padding(16.dp),
            ) {
                Text(stringResource(R.string.journal_invite_title, stringResource(invite.word())), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.journal_invite_body), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
                NavRow(cardTitle(card)) { onCard(card.id) }
            }
        }

        SectionTitle(stringResource(R.string.journal_recent))
        if (checkIns.isEmpty()) {
            EmptyState(stringResource(R.string.journal_empty_title), stringResource(R.string.journal_empty_body))
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
                        val parts = NoteTags.parse(c.note)
                        val words = tagWords(parts.tags)
                        if (words.isNotEmpty()) Text(words, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                        if (parts.text.isNotBlank()) {
                            Text(parts.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }

        SectionTitle(stringResource(R.string.journal_export_title))
        Column {
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
 * « Vrai ou idée reçue ? » deck first, so play is the first thing seen (Iris, 2026-10-31); then the
 * library's seven topics, when to see a doctor, and two rows, Settings and About Brume.
 */
@Composable
fun LearnScreen(
    onTopic: (Int) -> Unit = {},
    onSettings: () -> Unit = {},
    onAbout: () -> Unit = {},
    onCard: (String) -> Unit = {},
    still: Boolean? = null,
) {
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize().scrollCue(scroll).verticalScroll(scroll).then(gutter)) {
        ScreenTitle(R.string.tab_learn, stringResource(R.string.learn_intro))
        Spacer(Modifier.height(12.dp))
        DecibelExplorer(still = still, onCard = onCard)
        HorizontalDivider(Modifier.padding(vertical = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
        QuizDeck(onCard = onCard, still = still)
        HorizontalDivider(Modifier.padding(top = 24.dp, bottom = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Text(stringResource(R.string.learn_library), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        LearnTopics(onTopic)

        DoctorGroup(onSettings)

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        NavRow(stringResource(R.string.settings_title), stringResource(R.string.settings_row_summary), onClick = onSettings)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        NavRow(stringResource(R.string.about_brume), stringResource(R.string.about_row_summary), onClick = onAbout)
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * When to see a doctor: the red flags, the same everywhere, then the country's own lines (sudden
 * hearing loss, emergency, crisis) with tappable numbers, and which country they are for.
 */
@Composable
fun DoctorGroup(onSettings: (() -> Unit)? = null) {
    SettingsGroup(stringResource(R.string.doctor_title)) {
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

/** About Brume, one row away from Learn: what it is, the disclaimer, privacy, sources, the version, and the wordmark at its foot. */
@Composable
fun AboutScreen(
    version: String,
    onIntro: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize().scrollCue(scroll).verticalScroll(scroll).then(gutter)) {
        BackRow(stringResource(R.string.tab_learn), onBack)
        Text(stringResource(R.string.about_brume), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
        Text(stringResource(R.string.about_what), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
        Text(stringResource(R.string.about_disclaimer), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))

        SectionTitle(stringResource(R.string.about_help_title))
        RegionLines(listOf(Need.CRISIS, Need.EMERGENCY))

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

        SectionTitle(stringResource(R.string.about_sources_title))
        Text(stringResource(R.string.about_sources), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        OutlinedButton(onIntro, Modifier.fillMaxWidth().padding(top = 16.dp).heightIn(min = 48.dp)) {
            Text(stringResource(R.string.about_intro))
        }
        // The brand signs About at its foot, never as a title (Erwann, 2026-10-31).
        Column(Modifier.padding(top = 24.dp, bottom = 16.dp)) {
            Text(stringResource(R.string.brand_word), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.about_version, version), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Settings, one row away from Learn: night hours, activities, language, sync, where the sound plays. */
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
) {
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize().scrollCue(scroll).verticalScroll(scroll).then(gutter)) {
        BackRow(stringResource(R.string.tab_learn), onBack)
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
        SettingsGroup(stringResource(R.string.settings_display), Modifier.padding(top = 8.dp)) {
            SwitchRow(stringResource(R.string.settings_night), stringResource(R.string.settings_night_summary), nightHours, onNightHours)
        }
        ModuleSettings(modules, onModules)
        // Below Android 13 there is no per-app locale and no cached appcompat to stand in: no row.
        if (language != null) LanguageSettings(language)
        if (country != null) CountrySettings(country)
        SyncSettings(sync, syncMessage, syncActions)
        // The same switcher Listen's output row opens: the system's own, so Brume never picks for you.
        SettingsGroup(stringResource(R.string.settings_output)) {
            NavRow(stringResource(R.string.output_change_label), onClick = onOutput)
        }
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * The switchable modules. Listen is always there, said in words rather than shown as a switch
 * that can't move; each other row is a switch whose state is its thumb's place, not a hue.
 */
@Composable
fun ModuleSettings(modules: Modules, onModules: (Modules) -> Unit) {
    SettingsGroup(stringResource(R.string.modules_title)) {
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
