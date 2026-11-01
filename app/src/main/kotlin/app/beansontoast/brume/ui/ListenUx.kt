package app.beansontoast.brume.ui

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.LaunchedEffect
import app.beansontoast.design.SelectChip
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.beansontoast.brume.R
import androidx.compose.foundation.clickable
import app.beansontoast.brume.core.sound.Ambience
import app.beansontoast.brume.core.sound.NoiseColour
import app.beansontoast.brume.core.sound.SoundEngine.Mode
import app.beansontoast.brume.playback.PlayerUi
import app.beansontoast.design.tabular
import kotlin.math.roundToInt

/*
 * Listen and Tones (Iris, 2026-10-31; Erwann chose option B the same day). Listen holds the
 * comfort sounds only; the tone pattern is its own tab, a practice in timed sessions. One plays
 * at a time: starting one stops the other, and Listen says so in a line. Both open on a "now
 * playing" card that names the layers as slots, with their levels and a Remove each; pickers are
 * one-of-a-kind choices, marked by a check glyph and weight, never by a colour alone.
 */

/**
 * The view of [ui] and the actions as one tab sees them: playing only when its own mode plays,
 * and any start or choice there switches the engine to that mode first, which stops the other.
 */
internal fun forMode(ui: PlayerUi, actions: ListenActions, mode: Mode): Pair<PlayerUi, ListenActions> {
    val mine = ui.mode == mode
    fun claim() { if (!mine) actions.mode(mode) }
    val view = ui.copy(mode = mode, playing = ui.playing && mine, timerRemainingMinutes = if (mine) ui.timerRemainingMinutes else null)
    return view to ListenActions(
        noise = { claim(); actions.noise(it) },
        ambience = { claim(); actions.ambience(it) },
        noiseLevel = actions.noiseLevel, ambienceLevel = actions.ambienceLevel, timer = actions.timer,
        toggle = { if (mine) actions.toggle() else { actions.mode(mode); if (!ui.playing) actions.toggle() } },
        tooLoud = actions.tooLoud, mode = actions.mode, open = actions.open, balance = actions.balance, output = actions.output,
        tones = ToneActions(
            pitch = { claim(); actions.tones.pitch(it) }, level = actions.tones.level,
            trim = { i, v -> claim(); actions.tones.trim(i, v) }, match = { claim(); actions.tones.match(it) },
        ),
    )
}

/** The Listen tab: comfort sounds only. The tone pattern lives in its own tab. */
@Composable
fun ListenComfort(all: PlayerUi, allActions: ListenActions) {
    val (ui, actions) = forMode(all, allActions, Mode.SOUNDS)
    Column(Modifier.fillMaxSize()) {
        val scroll = rememberScrollState()
        Column(Modifier.weight(1f).scrollCue(scroll).verticalScroll(scroll).then(gutter)) {
            ScreenTitle(R.string.tab_listen)
            NowPlaying(ui, actions, Modifier.padding(top = 12.dp))
            // The pickers come straight after the card, so at 360x780 the Nature heading meets the fold and
            // reads as "more" through the scroll cue; the one-at-a-time rule closes the page with the guide.
            ComfortPickers(ui, actions)
            Text(stringResource(R.string.ux_one_at_a_time), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(24.dp))
        }
        Dock(ui, actions)
    }
}

/**
 * The Tones tab, one direct screen (Iris, 2026-10-31, after Erwann reversed the path): the session
 * card, a quiet « How it works » that opens its page, then the pitch, the level and the trims, and
 * the dock with its session timer. Play is one tap from the tab.
 */
@Composable
fun TonesScreen(all: PlayerUi, allActions: ListenActions, onHow: () -> Unit = {}) {
    val (ui, actions) = forMode(all, allActions, Mode.TONES)
    Column(Modifier.fillMaxSize()) {
        val scroll = rememberScrollState()
        Column(Modifier.weight(1f).scrollCue(scroll).verticalScroll(scroll).then(gutter)) {
            ScreenTitle(R.string.tab_tones, stringResource(R.string.ux_tones_heading) + " · " + stringResource(R.string.ux_tones_session))
            NowPlaying(ui, actions, Modifier.padding(top = 16.dp))
            QuietLink(stringResource(R.string.tones_how), onHow)
            SectionTitle(stringResource(R.string.tones_pitch))
            PitchCard(ui, actions.tones)
            SectionTitle(stringResource(R.string.listen_levels))
            ToneLevel(ui, actions.tones)
            TrimsCard(ui, actions.tones)
            Spacer(Modifier.height(24.dp))
        }
        Dock(ui, actions, timerTitle = R.string.ux_session_timer)
    }
}

/** A quiet way to read more: words and a chevron in the muted ink, 48 dp, never a button's weight. */
@Composable
private fun QuietLink(text: String, onClick: () -> Unit) {
    Row(
        Modifier.padding(top = 4.dp).heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick).padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text + "  ›", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * What is on, or ready: the two layers as slots, each with its kind, its level and a Remove.
 * An empty slot says so, so "one noise and one nature" is visible before anything is tapped.
 * Playing or ready is told by the heading's words, never by a colour.
 */
@Composable
internal fun NowPlaying(ui: PlayerUi, actions: ListenActions, modifier: Modifier = Modifier) {
    Card(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(if (ui.playing) R.string.ux_now_playing else R.string.ux_ready),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).semantics { heading() })
            // Back to the playing view while this tab's sound plays: a named button, not a tap on the
            // whole card, which holds sliders and Remove.
            if (ui.playing) {
                val label = stringResource(R.string.ux_open_playing_label)
                TextButton(actions.open, Modifier.heightIn(min = 48.dp).semantics { contentDescription = label }) {
                    Text(stringResource(R.string.ux_open_playing))
                    Icon(painterResource(R.drawable.ic_open), null, modifier = Modifier.padding(start = 4.dp).size(16.dp))
                }
            }
        }
        if (ui.mode == Mode.TONES) {
            Slot(stringResource(R.string.ux_tone_kind), stringResource(R.string.tones_pitch_value, ui.tones.pitch), ui.toneLevel, null, null)
        } else {
            val noise = ui.noise?.let { stringResource(it.label()) }
            Slot(stringResource(R.string.listen_noise), noise, ui.noiseLevel, actions.noiseLevel, { actions.noise(null) })
            if (noise == null) Empty(R.string.ux_slot_empty_noise)
            val ambience = ui.ambience?.let { stringResource(it.label()) }
            Slot(stringResource(R.string.listen_nature), ambience, ui.ambienceLevel, actions.ambienceLevel, { actions.ambience(null) })
            if (ambience == null) Empty(R.string.ux_slot_empty_nature)
        }
        EarsRows(ui.balance, ui.output, actions.balance, actions.output)
    }
}

/**
 * One layer on one line: its kind, its name, its level written, and Remove; the slider under it.
 * The tone pattern passes no slider: its level lives once, in its own panel.
 */
@Composable
private fun Slot(kind: String, name: String?, level: Float, onLevel: ((Float) -> Unit)?, onRemove: (() -> Unit)?) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().padding(top = 6.dp).heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(kind, style = MaterialTheme.typography.labelLarge, color = muted, maxLines = 1,
            modifier = Modifier.widthIn(min = 76.dp).padding(end = 8.dp))
        Text(name ?: "", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (name != null) {
            Text(stringResource(R.string.ux_level_short, (level * 20).roundToInt()), style = MaterialTheme.typography.bodyMedium.tabular(), color = muted)
            // Sacha's remove mark, named with the layer for TalkBack ("Remove Pink noise").
            if (onRemove != null) {
                val label = stringResource(R.string.ux_remove) + " " + name
                IconButton(onClick = onRemove, modifier = Modifier.semantics { contentDescription = label }) {
                    Icon(painterResource(R.drawable.ic_remove), null, tint = muted, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
    if (name != null && onLevel != null) {
        val levelText = stringResource(R.string.listen_level, (level * 20).roundToInt())
        val label = stringResource(R.string.listen_level_label, name)
        Slider(value = level, onValueChange = onLevel, steps = 19, colors = quietSlider(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).semantics { contentDescription = label; stateDescription = levelText })
    }
}

@Composable
private fun Empty(@StringRes text: Int) =
    Text(stringResource(text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp))

/** Noise, then nature, each a one-of-a-kind choice with its rule written under the heading. */
@Composable
private fun ComfortPickers(ui: PlayerUi, actions: ListenActions, modifier: Modifier = Modifier) {
    SectionTitle(stringResource(R.string.listen_noise), modifier)
    Rule(R.string.ux_noise_rule)
    Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        NoiseColour.entries.forEach { c ->
            ChoiceCard(stringResource(c.label()), ui.noise == c, { actions.noise(if (ui.noise == c) null else c) }, Modifier.weight(1f)) { texture(c, null, it) }
        }
    }
    SectionTitle(stringResource(R.string.listen_nature))
    Rule(R.string.ux_nature_rule)
    Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Ambience.entries.forEach { a ->
            ChoiceCard(stringResource(a.label()), ui.ambience == a, { actions.ambience(if (ui.ambience == a) null else a) }, Modifier.weight(1f)) { texture(null, a, it) }
        }
        Spacer(Modifier.weight(1f))
    }
    Text(stringResource(R.string.listen_guide), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
}

@Composable
private fun Rule(@StringRes text: Int) =
    Text(stringResource(text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp))

/** One of a kind: a check glyph when chosen, plus weight and a step lighter. Announced as a radio. */
@Composable
private fun ChoiceCard(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier, draw: DrawScope.(Color) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val ink = if (selected) scheme.onSecondaryContainer else scheme.onSurface
    val quiet = if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant
    Column(
        modifier.clip(MaterialTheme.shapes.medium)
            .background(if (selected) scheme.secondaryContainer else scheme.surfaceContainer)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 80.dp)
            .padding(10.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.Top) {
            Canvas(Modifier.weight(1f).fillMaxSize()) { draw(quiet.copy(alpha = if (selected) 0.95f else 0.6f)) }
            if (selected) Icon(painterResource(app.beansontoast.design.R.drawable.ic_check), null, tint = ink, modifier = Modifier.padding(start = 4.dp).size(16.dp))
        }
        Text(label, style = MaterialTheme.typography.titleSmall, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.padding(top = 8.dp))
    }
}


/**
 * The sounds (« + » on the scene; Iris, 2026-10-31, B's tiles inside A): every sound as a tile,
 * two by two, a check and weight on the ones in the mix; one noise and one nature sound at most.
 * Under them, each sound's own level and, once, the listening settings kept per headphones
 * (balance and output), so they live in this one place and nowhere on the scene.
 */
@Composable
fun SoundsScreen(all: PlayerUi, allActions: ListenActions, onBack: () -> Unit, hint: Boolean = false, onHintSeen: () -> Unit = {}) {
    val (ui, actions) = forMode(all, allActions, Mode.SOUNDS)
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize().scrollCue(scroll).verticalScroll(scroll).then(gutter)) {
        TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(painterResource(R.drawable.ic_back), null, modifier = Modifier.size(20.dp))
            Text(stringResource(R.string.learn_back), modifier = Modifier.padding(start = 4.dp))
        }
        // The first visit says what the two rows are for, once; after that the shorter rule.
        ScreenTitle(R.string.scene_sounds, stringResource(if (hint) R.string.picker_hint else R.string.scene_sounds_hint))
        // Two named rows, each one choice with None (Erwann, 2026-10-31). Before, all seven sat in one
        // grid of checkboxes, and a second noise silently replaced the first: the engine holds one of each.
        if (hint) LaunchedEffect(Unit) { onHintSeen() }
        PickerRow(stringResource(R.string.picker_background), stringResource(R.string.picker_none),
            BACKGROUNDS.map { it to stringResource(it.label()) }, ui.noise, actions.noise, ui.noiseLevel, actions.noiseLevel)
        PickerRow(stringResource(R.string.picker_ambience), stringResource(R.string.picker_none_ambience),
            Ambience.entries.map { it to stringResource(it.label()) }, ui.ambience, actions.ambience, ui.ambienceLevel, actions.ambienceLevel)
        EarsRows(ui.balance, ui.output, actions.balance, actions.output)
        Spacer(Modifier.height(32.dp))
    }
}

/** The backgrounds, deepest first, as the row reads them. */
private val BACKGROUNDS = listOf(NoiseColour.BROWN, NoiseColour.PINK, NoiseColour.WHITE, NoiseColour.GREY)

/**
 * One named row of the picker: a single choice that includes None. Tapping a sound replaces the
 * row's last one, so the check moves; the chosen one is told by the check glyph and weight. Its own
 * level sits under it while something is chosen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T : Any> PickerRow(title: String, none: String, options: List<Pair<T, String>>, chosen: T?, onPick: (T?) -> Unit, level: Float, onLevel: (Float) -> Unit) {
    SectionTitle(title)
    FlowRow(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        (listOf<Pair<T?, String>>(null to none) + options).forEach { (value, name) -> SelectChip(name, chosen == value, { onPick(value) }) }
    }
    if (chosen != null) Slot(title, options.firstOrNull { it.first == chosen }?.second, level, onLevel, null)
}
