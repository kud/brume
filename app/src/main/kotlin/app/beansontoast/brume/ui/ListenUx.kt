package app.beansontoast.brume.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import app.beansontoast.brume.core.sound.Ambience
import app.beansontoast.brume.core.sound.NoiseColour
import app.beansontoast.brume.core.sound.SoundEngine.Mode
import app.beansontoast.brume.playback.PlayerUi
import app.beansontoast.design.tabular
import kotlin.math.roundToInt

/*
 * Listen UX prototypes (Iris, 2026-10-31), on branch ux-listen for Erwann to choose between.
 * Both share the same three fixes: a "now playing" card that names the two layers as slots
 * (one noise, one nature) with their levels and a Remove each; pickers that look and announce
 * as one-of-a-kind choices (a ring, filled when chosen, and a rule written under the heading)
 * instead of checkboxes that silently swap; and the tone pattern framed as a practice of its own.
 *  (A) one page: comfort sounds first, the tone pattern set apart at the foot behind a switch.
 *  (B) two destinations: Listen holds comfort sounds only; Tones is its own tab with sessions.
 */

/** Option A: one page. Comfort sounds, then the tone pattern set apart; one is in use at a time. */
@Composable
fun ListenOnePage(ui: PlayerUi, actions: ListenActions) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).then(gutter)) {
            Wordmark(Modifier.padding(top = 20.dp))
            NowPlaying(ui, actions, Modifier.padding(top = 12.dp))
            if (ui.mode == Mode.TONES) {
                TonesPanel(ui, actions.tones)
                ApartCard(R.string.ux_comfort, R.string.ux_sounds_apart, R.string.ux_sounds_switch) { actions.mode(Mode.SOUNDS) }
            } else {
                ComfortPickers(ui, actions)
                ApartCard(R.string.mode_tones, R.string.ux_tones_apart, R.string.ux_tones_switch) { actions.mode(Mode.TONES) }
            }
            Spacer(Modifier.height(12.dp))
        }
        Dock(ui, actions)
    }
}

/** Option B, the Listen tab: comfort sounds only. The tone pattern lives in its own tab. */
@Composable
fun ListenComfort(ui: PlayerUi, actions: ListenActions) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).then(gutter)) {
            Wordmark(Modifier.padding(top = 20.dp))
            NowPlaying(ui, actions, Modifier.padding(top = 12.dp))
            ComfortPickers(ui, actions)
            Spacer(Modifier.height(12.dp))
        }
        Dock(ui, actions)
    }
}

/** Option B, the Tones tab: a practice in sessions, its own heading, its own dock with a session timer. */
@Composable
fun TonesScreen(ui: PlayerUi, actions: ListenActions) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).then(gutter)) {
            Text(stringResource(R.string.ux_tones_heading), style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = 24.dp).semantics { heading() })
            Text(stringResource(R.string.ux_tones_session), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            NowPlaying(ui, actions, Modifier.padding(top = 16.dp))
            TonesPanel(ui, actions.tones)
            Spacer(Modifier.height(12.dp))
        }
        Dock(ui, actions, timerTitle = R.string.ux_session_timer)
    }
}

/** Option B's bar: Listen, Tones, Journal, About. Selected is told by indicator and weight. */
@Composable
fun BrumeScaffoldTwo(tones: Boolean, content: @Composable () -> Unit) {
    @Composable fun label(@StringRes text: Int, selected: Boolean) =
        Text(stringResource(text), fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
                NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
                    NavigationBarItem(!tones, {}, { Icon(painterResource(R.drawable.ic_brume_mark), null) }, label = { label(R.string.tab_listen, !tones) })
                    NavigationBarItem(tones, {}, { Icon(painterResource(R.drawable.ic_tones), null) }, label = { label(R.string.tab_tones, tones) })
                    NavigationBarItem(false, {}, { Icon(Icons.Filled.Edit, null) }, label = { label(R.string.tab_journal, false) })
                    NavigationBarItem(false, {}, { Icon(Icons.Filled.Info, null) }, label = { label(R.string.tab_about, false) })
                }
            }
        },
    ) { padding -> Column(Modifier.fillMaxSize().padding(padding)) { content() } }
}

/**
 * What is on, or ready: the two layers as slots, each with its kind, its level and a Remove.
 * An empty slot says so, so "one noise and one nature" is visible before anything is tapped.
 * Playing or ready is told by the heading's words, never by a colour.
 */
@Composable
internal fun NowPlaying(ui: PlayerUi, actions: ListenActions, modifier: Modifier = Modifier) {
    Card(modifier) {
        Text(stringResource(if (ui.playing) R.string.ux_now_playing else R.string.ux_ready),
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() })
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
            if (onRemove != null) TextButton(onClick = onRemove) {
                Text(stringResource(R.string.ux_remove), style = MaterialTheme.typography.labelLarge)
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
private fun ComfortPickers(ui: PlayerUi, actions: ListenActions) {
    SectionTitle(stringResource(R.string.listen_noise), Modifier.padding(top = 4.dp))
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

/** One of a kind: a ring, filled when chosen, plus weight and a step lighter. Announced as a radio. */
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
            Box(Modifier.padding(start = 4.dp).size(16.dp).border(1.5.dp, ink.copy(alpha = if (selected) 1f else 0.5f), CircleShape),
                contentAlignment = Alignment.Center) {
                if (selected) Box(Modifier.size(8.dp).clip(CircleShape).background(ink))
            }
        }
        Text(label, style = MaterialTheme.typography.titleSmall, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.padding(top = 8.dp))
    }
}

/** Option A's other half, set apart: what it is, in one line, and the one switch to it. */
@Composable
private fun ApartCard(@StringRes title: Int, @StringRes body: Int, @StringRes action: Int, onSwitch: () -> Unit) {
    HorizontalDivider(Modifier.padding(top = 24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh)
    SectionTitle(stringResource(title))
    Card {
        Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = onSwitch, modifier = Modifier.padding(top = 12.dp).fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(action), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
