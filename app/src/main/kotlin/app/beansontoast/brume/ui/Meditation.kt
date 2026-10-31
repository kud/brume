package app.beansontoast.brume.ui

import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.beansontoast.brume.Modules
import app.beansontoast.brume.R
import app.beansontoast.brume.core.calm.Guided
import app.beansontoast.brume.core.calm.SIT_MINUTES
import app.beansontoast.brume.core.calm.Timeline
import app.beansontoast.brume.core.calm.sit
import app.beansontoast.design.SelectChip
import app.beansontoast.design.SettingsGroup
import app.beansontoast.design.SwitchRow
import app.beansontoast.design.rememberReducedMotion
import app.beansontoast.design.tabular
import kotlinx.coroutines.delay

/** The two practices Calm holds (ADR 0003). */
enum class Practice { BREATHE, MEDITATE }

/** What a meditation is: one of the guided sessions, or the unguided timer. */
enum class Meditation(val guided: Guided?) {
    BODY_SCAN(Guided.BODY_SCAN), SOUNDS(Guided.SOUNDS), BREATH(Guided.BREATH), TIMER(null);

    fun timeline(minutes: Int): Timeline = guided?.plan ?: sit(minutes)
}

/** The choices a meditation starts from. Kept per phone, like Breathe's. */
data class MeditationSetup(
    val meditation: Meditation = Meditation.SOUNDS,
    val minutes: Int = 10,
    val haptics: Boolean = true,
    val sound: Boolean = false,
)

@StringRes private fun Meditation.title() = when (this) {
    Meditation.BODY_SCAN -> R.string.med_body
    Meditation.SOUNDS -> R.string.med_sounds
    Meditation.BREATH -> R.string.med_breath
    Meditation.TIMER -> R.string.med_timer
}

@StringRes private fun Meditation.hint() = when (this) {
    Meditation.BODY_SCAN -> R.string.med_body_hint
    Meditation.SOUNDS -> R.string.med_sounds_hint
    Meditation.BREATH -> R.string.med_breath_hint
    Meditation.TIMER -> R.string.med_timer_hint
}

/**
 * The written steps of each guided session, one string per step of its `:core` timeline.
 * The first always says the session can stop at any time. `GuidesTest` holds the counts equal.
 */
internal fun Guided.lines(): List<Int> = when (this) {
    Guided.BODY_SCAN -> listOf(R.string.med_body_0, R.string.med_body_1, R.string.med_body_2, R.string.med_body_3, R.string.med_body_4,
        R.string.med_body_5, R.string.med_body_6, R.string.med_body_7, R.string.med_body_8)
    Guided.SOUNDS -> listOf(R.string.med_sounds_0, R.string.med_sounds_1, R.string.med_sounds_2, R.string.med_sounds_3, R.string.med_sounds_4,
        R.string.med_sounds_5, R.string.med_sounds_6)
    Guided.BREATH -> listOf(R.string.med_breath_0, R.string.med_breath_1, R.string.med_breath_2, R.string.med_breath_3, R.string.med_breath_4,
        R.string.med_breath_5)
}

/**
 * The Calm tab: Breathe and Meditation, one entry per practice. With only one switched on, it
 * shows that one and no chooser.
 */
@Composable
fun CalmScreen(
    modules: Modules,
    practice: Practice,
    onPractice: (Practice) -> Unit,
    breath: BreathSetup,
    meditation: MeditationSetup,
    soundName: String?,
    onBreath: (BreathSetup) -> Unit,
    onMeditation: (MeditationSetup) -> Unit,
    onStartBreath: () -> Unit,
    onStartMeditation: () -> Unit,
) {
    val shown = when {
        !modules.breath -> Practice.MEDITATE
        !modules.meditation -> Practice.BREATHE
        else -> practice
    }
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize().scrollCue(scroll).verticalScroll(scroll)) {
        Column(gutter) {
            ScreenTitle(R.string.tab_calm, stringResource(R.string.calm_intro))
            if (modules.breath && modules.meditation) Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectChip(stringResource(R.string.tab_breath), shown == Practice.BREATHE, { onPractice(Practice.BREATHE) })
                SelectChip(stringResource(R.string.practice_meditation), shown == Practice.MEDITATE, { onPractice(Practice.MEDITATE) })
            }
            when (shown) {
                Practice.BREATHE -> BreathPanel(breath, soundName, onBreath, onStartBreath)
                Practice.MEDITATE -> MeditationPanel(meditation, soundName, onMeditation, onStartMeditation)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MeditationPanel(setup: MeditationSetup, soundName: String?, onChange: (MeditationSetup) -> Unit, onStart: () -> Unit) {
    Column {
        SectionTitle(stringResource(R.string.med_choose), Modifier.padding(top = 4.dp))
        Meditation.entries.forEach { m -> ChoiceRow(stringResource(m.title()), stringResource(m.hint()), setup.meditation == m) { onChange(setup.copy(meditation = m)) } }

        if (setup.meditation == Meditation.TIMER) {
            SectionTitle(stringResource(R.string.breath_length), Modifier.padding(top = 4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SIT_MINUTES.forEach { m -> SelectChip(stringResource(R.string.breath_minutes, m), setup.minutes == m, { onChange(setup.copy(minutes = m)) }) }
            }
        }

        SettingsGroup(stringResource(R.string.med_options), Modifier.padding(top = 12.dp)) {
            val timer = setup.meditation == Meditation.TIMER
            SwitchRow(stringResource(if (timer) R.string.med_haptics_timer else R.string.med_haptics_guided),
                stringResource(if (timer) R.string.med_haptics_timer_summary else R.string.med_haptics_guided_summary), setup.haptics) { onChange(setup.copy(haptics = it)) }
            if (soundName != null) SwitchRow(stringResource(R.string.breath_sound), stringResource(R.string.breath_sound_summary, soundName), setup.sound) {
                onChange(setup.copy(sound = it))
            } else Column(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 8.dp)) {
                Text(stringResource(R.string.breath_sound), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.breath_sound_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(stringResource(R.string.med_stop_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp))
        FilledTonalButton(onStart, Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 24.dp).heightIn(min = 56.dp)) {
            Text(stringResource(R.string.breath_start), style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** One choice of a list: a check glyph and weight say which is chosen, never a hue. */
@Composable
private fun ChoiceRow(title: String, hint: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(selected, onClick = onClick, role = Role.RadioButton).padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top) {
        Box(Modifier.width(28.dp).padding(top = 2.dp)) {
            if (selected) Icon(painterResource(app.beansontoast.design.R.drawable.ic_check), null, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * A meditation, full screen, in Breathe's night mist. Guided: one short line at a time, centred,
 * fading into the next, with "2 of 7" written under it. Timer: the mist rises over the first
 * seconds and settles over the last, "Settle in" and "Slowly come back" mark the soft start and
 * end, and the middle is quiet. Time left and End sit at the top throughout.
 *  - A light tap at each new line (guided) or at the start and the end (timer), if chosen. No chime.
 *  - Reduced motion: the mist rests still and lines change without a fade.
 *  - The screen stays on.
 */
@Composable
fun MeditationSession(setup: MeditationSetup, startedAt: Long, night: Boolean, onEnd: () -> Unit, elapsed: Double? = null, still: Boolean? = null) {
    val context = LocalContext.current
    val reduced = still ?: rememberReducedMotion()
    val timeline = remember(setup) { setup.meditation.timeline(setup.minutes) }
    var clock by remember { mutableDoubleStateOf(((System.currentTimeMillis() - startedAt) / 1000.0).coerceAtLeast(0.0)) }
    LaunchedEffect(startedAt, elapsed) {
        if (elapsed == null) while (clock < timeline.totalSeconds) {
            clock = (System.currentTimeMillis() - startedAt) / 1000.0
            delay(if (reduced) 250 else 50)
        }
    }
    val place = timeline.at(elapsed ?: clock)
    val guided = setup.meditation.guided

    val view = LocalView.current
    if (elapsed == null) DisposableEffect(view) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }
    val tapper = remember { if (elapsed == null && setup.haptics) Tapper(context) else null }
    // Guided: a tap at the start, each new line and the end. Timer: only the start and the end.
    val tapKey = if (guided != null) place.step else if (place.done) 1 else 0
    LaunchedEffect(tapKey, place.done) { if (elapsed == null) tapper?.tap() }

    val scheme = MaterialTheme.colorScheme
    val muted = scheme.onSurfaceVariant
    val fade = if (night) 0.55f else 1f
    Column(Modifier.fillMaxSize().background(scheme.background).safeDrawingPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val end = stringResource(R.string.breath_end)
            TextButton(onClick = onEnd, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = end }) {
                Icon(painterResource(R.drawable.ic_close), null, tint = muted, modifier = Modifier.size(20.dp))
                Text(end, style = MaterialTheme.typography.labelLarge, color = muted, modifier = Modifier.padding(start = 6.dp))
            }
            if (!place.done) {
                val left = kotlin.math.ceil(place.totalLeft).toInt()
                Text(stringResource(R.string.breath_left, left / 60, left % 60), style = MaterialTheme.typography.bodyMedium.tabular(), color = muted,
                    textAlign = TextAlign.End, modifier = Modifier.weight(1f).padding(end = 8.dp))
            }
        }
        // The mist: settled low for a guided session (the words lead), rising and settling for the timer.
        val level = when {
            reduced -> 0.35f
            // Guided: the band stays settled low, under the words, never through them.
            guided != null -> 0f
            else -> 0.45f * place.presence(timeline)
        }
        if (place.done) {
            Canvas(Modifier.weight(1f).fillMaxWidth()) { mistBand(0f, muted, fade) }
            Text(stringResource(R.string.breath_done), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite })
            Text(stringResource(R.string.breath_done_body), style = MaterialTheme.typography.bodyLarge, color = muted, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            OutlinedButton(onEnd, Modifier.align(Alignment.CenterHorizontally).padding(top = 24.dp, bottom = 32.dp).heightIn(min = 48.dp)) {
                Text(stringResource(R.string.breath_back))
            }
        } else {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Canvas(Modifier.fillMaxSize()) { mistBand(level, muted, fade) }
                val line: Int? = when {
                    guided != null -> guided.lines()[place.step]
                    place.step == 0 -> R.string.sit_settle
                    place.step == timeline.steps.lastIndex -> R.string.sit_return
                    else -> null
                }
                Crossfade(line, Modifier.align(Alignment.Center).padding(horizontal = 12.dp), animationSpec = tween(if (reduced) 0 else 1200), label = "line") { id ->
                    if (id != null) Text(stringResource(id), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite })
                }
            }
            val under = if (guided != null) stringResource(R.string.med_step, place.step + 1, timeline.steps.size) else ""
            Text(under, style = MaterialTheme.typography.bodyMedium.tabular(), color = muted, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 56.dp))
        }
    }
}
