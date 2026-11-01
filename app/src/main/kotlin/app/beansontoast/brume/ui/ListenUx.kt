package app.beansontoast.brume.ui

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.beansontoast.design.Motion
import kotlinx.coroutines.launch
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
import app.beansontoast.brume.core.sound.Sound
import app.beansontoast.brume.core.sound.Tone
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
        add = { claim(); actions.add(it) },
        remove = { claim(); actions.remove(it) },
        level = actions.level, tone = actions.tone, timer = actions.timer,
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
            NowPlaying(ui, actions, Modifier.padding(top = 12.dp))
            // The pickers come straight after the card, so at 360x780 the Nature heading meets the fold and
            // reads as "more" through the scroll cue; the one-at-a-time rule closes the page with the guide.
            AddSounds(ui, actions)
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
fun TonesScreen(all: PlayerUi, allActions: ListenActions, onHow: () -> Unit = {}, onFinder: () -> Unit = {}) {
    val (ui, actions) = forMode(all, allActions, Mode.TONES)
    Column(Modifier.fillMaxSize()) {
        val scroll = rememberScrollState()
        Column(Modifier.weight(1f).scrollCue(scroll).verticalScroll(scroll).then(gutter)) {
            ScreenLead(stringResource(R.string.ux_tones_heading) + " · " + stringResource(R.string.ux_tones_session))
            NowPlaying(ui, actions, Modifier.padding(top = 16.dp))
            QuietLink(stringResource(R.string.tones_how), onHow)
            QuietLink(stringResource(R.string.finder_entry), onFinder)
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
            ui.sounds.forEach { s ->
                Slot(stringResource(s.kind()), stringResource(s.label()), ui.level(s), { actions.level(s, it) }, { actions.remove(s) })
            }
            if (ui.mix.isEmpty()) Empty(R.string.mixer_empty)
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

@StringRes internal fun Sound.kind() = when (this) {
    is Sound.Noise -> R.string.listen_noise
    is Sound.Recorded -> R.string.mixer_recording
    is Sound.Band -> R.string.sound_band_kind
}

/**
 * Every sound as a chip, noises then recordings: a check and weight on the ones in the mix. A tap
 * adds the sound as a new layer, or takes it out; there is no limit but the list itself.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddSounds(ui: PlayerUi, actions: ListenActions) {
    SectionTitle(stringResource(R.string.mixer_add))
    listOf(R.string.mixer_noises to NOISES, R.string.mixer_recordings to RECORDINGS).forEach { (title, sounds) ->
        Text(stringResource(title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            sounds.forEach { s ->
                val on = s in ui.sounds
                SelectChip(stringResource(s.label()), on, { if (on) actions.remove(s) else actions.add(s) })
            }
        }
    }
    Text(stringResource(R.string.listen_guide), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
}

/** The noises, deepest first, then the recordings, as the chips read them. */
private val NOISES: List<Sound> = listOf(NoiseColour.BROWN, NoiseColour.PINK, NoiseColour.GREY, NoiseColour.WHITE).map { Sound.Noise(it) }
private val RECORDINGS: List<Sound> = listOf(
    Ambience.RAIN, Ambience.RAIN_TERRACE, Ambience.STORM, Ambience.SEA, Ambience.STREAM, Ambience.WIND,
    Ambience.BIRDS, Ambience.NIGHT, Ambience.FIRE, Ambience.CAFE, Ambience.TRAIN,
).map { Sound.Recorded(it) }

/** A tone told in words: « Soft 4 », « Even », « Bright 2 », never a bare number or a colour. */
@Composable
private fun toneWords(tone: Float): String {
    val steps = (tone / Tone.STEP).roundToInt()
    return when {
        steps < 0 -> stringResource(R.string.mixer_tone_soft, -steps)
        steps > 0 -> stringResource(R.string.mixer_tone_bright, steps)
        else -> stringResource(R.string.mixer_tone_even)
    }
}

/**
 * One layer of the mixer: its name, its level in words and Remove, the level slider, and for a
 * noise its tone, from soft to bright. Rows keep their height whatever the values say.
 */
@Composable
private fun MixerLayer(ui: PlayerUi, sound: Sound, actions: ListenActions, modifier: Modifier = Modifier) {
    val name = stringResource(sound.label())
    Column(modifier) {
        Slot(stringResource(sound.kind()), name, ui.level(sound), { actions.level(sound, it) }, { actions.remove(sound) })
        if (sound is Sound.Noise) {
            val tone = ui.tone(sound)
            val words = toneWords(tone)
            val muted = MaterialTheme.colorScheme.onSurfaceVariant
            Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.mixer_tone), style = MaterialTheme.typography.labelLarge, color = muted, modifier = Modifier.weight(1f))
                Text(words, style = MaterialTheme.typography.bodyMedium.tabular(), color = muted)
            }
            val label = stringResource(R.string.mixer_tone_label, name)
            Slider(value = tone, onValueChange = { actions.tone(sound, it) }, valueRange = -1f..1f, steps = 19, colors = quietSlider(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).semantics { contentDescription = label; stateDescription = words })
        }
    }
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
 * The sounds (« + » on the scene): the mixer as one frame, never a scroll (Iris, 2026-11-01, garden
 * second pass; Erwann: « un truc vraiment carré, un peu snap »). The layers in the mix stand as
 * rows of a fixed height, as many as the frame holds (four at most), and further ones on the next
 * page, reached by a swipe or ‹ ›. Every sound to add lives in a sheet with two pages, noises and
 * recordings; the listening settings kept per headphones (output, balance) in a second sheet.
 * Nothing sits under the floating bar.
 */
@Composable
fun SoundsScreen(
    all: PlayerUi, allActions: ListenActions, onBack: () -> Unit, hint: Boolean = false, onHintSeen: () -> Unit = {},
    sheet: SoundsSheet? = null,
) {
    val (ui, actions) = forMode(all, allActions, Mode.SOUNDS)
    var open by rememberSaveable { mutableStateOf(sheet) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().then(gutter).padding(bottom = chromeBottom() + 8.dp)) {
            // The mixer (Erwann, 2026-11-01: « jouer plusieurs sons en même temps avec des volumes
            // différents pour chaque son »): the layers in the mix, each with its level.
            ScreenLead(stringResource(if (hint) R.string.picker_hint else R.string.scene_sounds_hint))
            if (hint) LaunchedEffect(Unit) { onHintSeen() }
            MixPages(ui, actions, Modifier.weight(1f))
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton({ open = SoundsSheet.ADD }, Modifier.heightIn(min = 48.dp)) {
                    Icon(painterResource(R.drawable.ic_plus), null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.mixer_add), modifier = Modifier.padding(start = 8.dp))
                }
                Spacer(Modifier.weight(1f))
                // The ears in words (« Balance · Centre »), one tap from their sheet.
                TextButton({ open = SoundsSheet.EARS }, Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.balance_title) + " · " + balanceText(ui.balance) + "  ›", maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        QuietSheet(open == SoundsSheet.ADD, { open = null }, stringResource(R.string.mixer_add)) { AddSoundsPager(ui, actions) }
        QuietSheet(open == SoundsSheet.EARS, { open = null }, stringResource(R.string.balance_title)) {
            EarsRows(ui.balance, ui.output, actions.balance, actions.output)
        }
    }
}

/** Which sheet is over the mixer: the sounds to add, or the ears. */
enum class SoundsSheet { ADD, EARS }

/** A recording's row: its line (48 dp and a little air) and its level slider. */
private val LAYER = 100.dp
/** A noise adds its tone line and slider. */
private val LAYER_NOISE = 176.dp

/**
 * The layers in pages that fit the frame left between the title and the buttons: a heading with
 * ‹ n / m ›, then a pager whose pages hold whole rows only. Pages settle on CALM, never a spring.
 */
@Composable
private fun MixPages(ui: PlayerUi, actions: ListenActions, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val headRow = 56.dp
        val sounds = ui.sounds
        val pages = pagesOf(sounds.map { (if (it is Sound.Noise) LAYER_NOISE else LAYER).value.roundToInt() }, (maxHeight - headRow).value.toInt())
        val pager = rememberPagerState { pages.size.coerceAtLeast(1) }
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(headRow), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.mixer_in_mix), style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f).semantics { heading() })
                PageStepper(pager.targetPage.coerceAtMost(pages.size - 1).coerceAtLeast(0), pages.size, { p ->
                    scope.launch { pager.animateScrollToPage(p, animationSpec = Motion.tween(Motion.CALM)) }
                })
            }
            if (sounds.isEmpty()) Empty(R.string.mixer_empty)
            else HorizontalPager(pager, Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.Top, pageSpacing = 24.dp,
                flingBehavior = PagerDefaults.flingBehavior(pager, snapAnimationSpec = Motion.tween(Motion.CALM))) { page ->
                Column(Modifier.fillMaxSize()) {
                    pages.getOrNull(page)?.forEach { i ->
                        val s = sounds[i]
                        MixerLayer(ui, s, actions, Modifier.height(if (s is Sound.Noise) LAYER_NOISE else LAYER))
                    }
                }
            }
        }
    }
}

/**
 * Every sound to add, in two pages (noises, recordings) named above them: a check and weight on the
 * ones in the mix. A tap adds the sound as a new layer, or takes it out. Both pages keep one height.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddSoundsPager(ui: PlayerUi, actions: ListenActions) {
    val groups = listOf(R.string.mixer_noises to NOISES, R.string.mixer_recordings to RECORDINGS)
    val pager = rememberPagerState { groups.size }
    PagerWords(pager, groups.map { stringResource(it.first) })
    HorizontalPager(pager, Modifier.fillMaxWidth().height(232.dp).padding(top = 8.dp), verticalAlignment = Alignment.Top, pageSpacing = 24.dp,
        flingBehavior = PagerDefaults.flingBehavior(pager, snapAnimationSpec = Motion.tween(Motion.CALM))) { page ->
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            groups[page].second.forEach { s ->
                val on = s in ui.sounds
                SelectChip(stringResource(s.label()), on, { if (on) actions.remove(s) else actions.add(s) })
            }
        }
    }
    Text(stringResource(R.string.listen_guide), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
}
