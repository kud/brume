package app.beansontoast.brume.ui

import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import app.beansontoast.brume.core.calm.GUIDED_MINUTES
import app.beansontoast.brume.core.calm.Part
import app.beansontoast.brume.core.calm.Ring
import app.beansontoast.brume.core.calm.guided
import app.beansontoast.brume.core.guide.Line
import app.beansontoast.brume.core.guide.MeditationGuide
import app.beansontoast.brume.voice.Voice
import app.beansontoast.design.calmAlpha
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.ui.semantics.heading
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
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
import androidx.compose.foundation.selection.selectable
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

/**
 * What a meditation is: one of three guided sessions, with bells and, if chosen, a voice (Respiration, Corps, Sons), or the
 * unguided timer. Sons follows MBCT-T's "Sounds and Thoughts" (practice-research.md).
 */
enum class Meditation(val guided: Guided?) {
    BREATH(Guided.BREATH), BODY(Guided.BODY), SOUNDS(Guided.SOUNDS), TIMER(null);

    val lengths: List<Int> get() = if (guided != null) GUIDED_MINUTES else SIT_MINUTES

    companion object {
        /** The stored choice; the body scan was BODY_SCAN until dev.15. */
        fun fromStored(name: String?): Meditation = if (name == "BODY_SCAN") BODY else entries.firstOrNull { it.name == name } ?: BREATH
    }
}

/** The choices a meditation starts from. Kept per phone, like Breathe's. */
data class MeditationSetup(
    val meditation: Meditation = Meditation.BREATH,
    val minutes: Int = 5,
    val haptics: Boolean = false,
    val sound: Boolean = false,
    /** A few spoken lines over a guided session (Erwann, 2026-11-01); the bells stay either way. */
    val voice: Boolean = true,
) {
    /** The chosen minutes if this meditation offers them, else the nearest it does. */
    val length: Int get() = meditation.lengths.minBy { kotlin.math.abs(it - minutes) }
    fun timeline(): Timeline = if (meditation.guided != null) guided(length) else sit(length)

    /** What is said, and when; none for the unguided timer. */
    fun guide(): MeditationGuide? = meditation.guided?.let { MeditationGuide(it, length) }
}

/** The words of each meditation line, FR and EN in `med_say_*`. */
@StringRes internal fun Line.text() = when (this) {
    Line.ARRIVE_POSTURE -> R.string.med_say_arrive_posture
    Line.ARRIVE_EYES -> R.string.med_say_arrive_eyes
    Line.ARRIVE_BREATHS -> R.string.med_say_arrive_breaths
    Line.BREATH_ANCHOR -> R.string.med_say_breath_anchor
    Line.BREATH_NOTICE -> R.string.med_say_breath_notice
    Line.BREATH_WANDER_1 -> R.string.med_say_breath_wander_1
    Line.BREATH_WANDER_2 -> R.string.med_say_breath_wander_2
    Line.BREATH_WANDER_3 -> R.string.med_say_breath_wander_3
    Line.BREATH_WANDER_4 -> R.string.med_say_breath_wander_4
    Line.BREATH_WANDER_5 -> R.string.med_say_breath_wander_5
    Line.BODY_ANCHOR -> R.string.med_say_body_anchor
    Line.BODY_NOTICE -> R.string.med_say_body_notice
    Line.BODY_LOWER -> R.string.med_say_body_lower
    Line.BODY_UPPER -> R.string.med_say_body_upper
    Line.BODY_LEGS -> R.string.med_say_body_legs
    Line.BODY_TORSO -> R.string.med_say_body_torso
    Line.BODY_TOP -> R.string.med_say_body_top
    Line.BODY_BACK -> R.string.med_say_body_back
    Line.BODY_CHEST -> R.string.med_say_body_chest
    Line.BODY_ARMS -> R.string.med_say_body_arms
    Line.BODY_FACE -> R.string.med_say_body_face
    Line.SOUNDS_ANCHOR -> R.string.med_say_sounds_anchor
    Line.SOUNDS_NOTICE -> R.string.med_say_sounds_notice
    Line.SOUNDS_WANDER_1 -> R.string.med_say_sounds_wander_1
    Line.SOUNDS_WANDER_2 -> R.string.med_say_sounds_wander_2
    Line.SOUNDS_WANDER_3 -> R.string.med_say_sounds_wander_3
    Line.SOUNDS_WANDER_4 -> R.string.med_say_sounds_wander_4
    Line.SOUNDS_WANDER_5 -> R.string.med_say_sounds_wander_5
    Line.CLOSE_RETURN -> R.string.med_say_close_return
    Line.CLOSE_OPEN -> R.string.med_say_close_open
}

@StringRes private fun Meditation.title() = when (this) {
    Meditation.BREATH -> R.string.med_breath
    Meditation.BODY -> R.string.med_body
    Meditation.SOUNDS -> R.string.med_sounds
    Meditation.TIMER -> R.string.med_timer
}

@StringRes private fun Meditation.hint() = when (this) {
    Meditation.BREATH -> R.string.med_breath_hint
    Meditation.BODY -> R.string.med_body_hint
    Meditation.SOUNDS -> R.string.med_sounds_hint
    Meditation.TIMER -> R.string.med_timer_hint
}

/** The one word a dimmed session shows, if any: read before the eyes close, never needed after. */
@StringRes private fun Part.word(m: Meditation): Int? = when (this) {
    Part.ARRIVE -> R.string.med_part_arrive
    Part.ANCHOR -> when (m) { Meditation.BODY -> R.string.med_part_body; Meditation.SOUNDS -> R.string.med_part_sounds; else -> R.string.med_part_breath }
    Part.WANDER -> R.string.med_part_wander
    Part.CLOSE -> R.string.med_part_close
    Part.SETTLE -> R.string.sit_settle
    Part.SIT -> null
    Part.RETURN -> R.string.sit_return
}

/**
 * The resting orb for Méditer (Iris, 2026-11-01, phase 3): Respirer's orb, but nothing paces it. It
 * swells a few percent over sixteen seconds and its edge drifts at half speed, as in a hold, so the
 * eye has somewhere soft to land without being asked to follow. Reduced motion: still, half size.
 */
@Composable
internal fun RestingOrb(seconds: Double, still: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit = {}) {
    val t = if (still) 0.0 else seconds
    val swell = if (still) 0.5f else 0.5f + 0.06f * kotlin.math.sin(2 * Math.PI * t / 16.0).toFloat()
    BreathOrb(swell, holding = true, seconds = t * 0.5, modifier = modifier, content = content)
}

/**
 * Méditer's own screen, one frame, no scroll (Iris, phase 3; the shape of Respirer's): the resting
 * orb with the length at its centre, the four meditations as light rows (ground, hairline and a
 * check on the chosen one, never colour alone), the chosen one's line in a reserved three-line
 * place, the lengths, a link to what happens during the session (voice, vibrations, sound, in a
 * sheet), and Start. [previewAt] fixes the orb's clock for renders.
 */
@Composable
fun MeditateScreen(
    setup: MeditationSetup, soundName: String?, voice: Voice.State, onChange: (MeditationSetup) -> Unit, onStart: () -> Unit,
    previewAt: Double? = null, options: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val reduced = rememberReducedMotion()
    val clock = rememberSeconds(previewAt != null || reduced)
    var open by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(options) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(bottom = LocalChromeBottom.current + 12.dp)) {
            RestingOrb(previewAt ?: clock, still = reduced && previewAt == null, modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 32.dp)) {
                Text(stringResource(R.string.breath_minutes, setup.length), style = MaterialTheme.typography.titleLarge.tabular(), textAlign = TextAlign.Center)
            }
            Text(stringResource(R.string.med_choose), style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp).semantics { heading() })
            Column(Modifier.fillMaxWidth().selectableGroup()) {
                Meditation.entries.forEach { m ->
                    RhythmRow(stringResource(m.title()), null, setup.meditation == m) { onChange(setup.copy(meditation = m)) }
                }
            }
            // The chosen one's line keeps three lines whichever it is, so nothing under it moves.
            Crossfade(setup.meditation, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                animationSpec = app.beansontoast.design.Motion.tween(app.beansontoast.design.Motion.QUICK), label = "hint") { m ->
                Text(stringResource(m.hint()), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, minLines = 3, maxLines = 3,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.breath_length), style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant, maxLines = 1)
                Spacer(Modifier.weight(1f))
                setup.meditation.lengths.forEach { m -> SelectChip(stringResource(R.string.breath_minutes, m), setup.length == m, { onChange(setup.copy(minutes = m)) }) }
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                .clickable(role = Role.Button) { open = true }.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.med_options), style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text("›", style = MaterialTheme.typography.titleMedium, color = scheme.onSurfaceVariant)
            }
            androidx.compose.material3.Button(onStart, Modifier.fillMaxWidth().padding(top = 4.dp).heightIn(min = 52.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)) {
                Icon(painterResource(R.drawable.ic_play), null, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.scene_start), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp))
            }
        }
        QuietSheet(open, { open = false }, stringResource(R.string.med_options)) {
            // The unguided timer has no words: the row keeps its place, faded and out of reach.
            val guided = setup.meditation.guided != null
            Box(Modifier.alpha(calmAlpha(guided)).then(if (guided) Modifier else Modifier.clearAndSetSemantics { })) {
                VoiceRow(setup.voice, voice, R.string.voice_med_summary) { if (guided) onChange(setup.copy(voice = it)) }
            }
            SwitchRow(stringResource(R.string.med_haptics_guided), stringResource(R.string.med_haptics_guided_summary), setup.haptics) { onChange(setup.copy(haptics = it)) }
            if (soundName != null) SwitchRow(stringResource(R.string.breath_sound), stringResource(R.string.breath_sound_summary, soundName), setup.sound) {
                onChange(setup.copy(sound = it))
            } else Column(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 8.dp)) {
                Text(stringResource(R.string.breath_sound), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.breath_sound_none), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
            Text(stringResource(R.string.med_bells_note) + " " + stringResource(R.string.med_stop_note), style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

/**
 * A meditation, full screen, eyes closed (Erwann, 2026-10-31: « la méditation, c'est pas vraiment
 * la méditation »). A bell at the start, at each step (guided) and at the end, at the session's
 * level and never above it. A guided session is also spoken, sparsely ([speak], from
 * `MeditationGuide`; Erwann, 2026-11-01), and each line is written under the part's word for a
 * few seconds, so a phone without a voice still guides; the timer says nothing. The screen is bright for a few seconds so the
 * start is seen, then dims nearly to black and stays there; it lifts again just before the end.
 * One quiet word names the part, for a glance; nothing needs reading. End and the time left stay
 * at the top, dimmed with the rest.
 *  - Haptics, if chosen: one light tap with each bell.
 *  - Reduced motion: the word changes without a fade.
 *  - The screen stays on, dimmed.
 */
@Composable
fun MeditationSession(
    setup: MeditationSetup, startedAt: Long, night: Boolean, onEnd: () -> Unit,
    elapsed: Double? = null, still: Boolean? = null, onBell: (Ring) -> Unit = {}, speak: ((Line) -> Unit)? = null,
) {
    val context = LocalContext.current
    val reduced = still ?: rememberReducedMotion()
    val timeline = remember(setup) { setup.timeline() }
    val guide = remember(setup) { setup.guide() }
    val say by rememberUpdatedState(speak)
    var clock by remember { mutableDoubleStateOf(((System.currentTimeMillis() - startedAt) / 1000.0).coerceAtLeast(0.0)) }
    val tapper = remember { if (elapsed == null && setup.haptics) Tapper(context) else null }
    val bell by rememberUpdatedState(onBell)
    LaunchedEffect(startedAt, elapsed) {
        if (elapsed != null) return@LaunchedEffect
        // Bells already rung before a rotation are not rung again.
        var heard = if (clock < 0.5) -1.0 else clock
        while (true) {
            clock = (System.currentTimeMillis() - startedAt) / 1000.0
            timeline.bellsBetween(heard, clock).forEach { bell(it); tapper?.tap() }
            guide?.between(heard, clock)?.lastOrNull()?.let { say?.invoke(it.what) }
            heard = clock
            if (clock >= timeline.totalSeconds) break
            delay(250)
        }
    }
    val place = timeline.at(elapsed ?: clock)

    val view = LocalView.current
    if (elapsed == null) DisposableEffect(view) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }

    val scheme = MaterialTheme.colorScheme
    val muted = scheme.onSurfaceVariant
    val fade = if (night) 0.7f else 1f
    val dim = if (place.done) 0f else 0.86f * (1f - place.light(timeline))
    Column(Modifier.fillMaxSize().background(scheme.background)
        .drawWithContent { drawContent(); if (dim > 0f) drawRect(Color.Black, alpha = dim) }
        .safeDrawingPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
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
        // The resting orb from the setup carries on, slower than any breath; nothing to follow.
        val orbClock = rememberSeconds(reduced || elapsed != null)
        val orbAt = elapsed ?: orbClock
        if (place.done) {
            RestingOrb(orbAt, still = true, modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 40.dp).alpha(fade))
            Text(stringResource(R.string.breath_done), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite })
            Text(stringResource(R.string.breath_done_body), style = MaterialTheme.typography.bodyLarge, color = muted, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            OutlinedButton(onEnd, Modifier.align(Alignment.CenterHorizontally).padding(top = 24.dp, bottom = 32.dp).heightIn(min = 48.dp)) {
                Text(stringResource(R.string.breath_back))
            }
        } else {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                RestingOrb(orbAt, still = reduced, modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 24.dp).alpha(fade))
                Crossfade(place.part.word(setup.meditation), Modifier.align(Alignment.Center), animationSpec = tween(if (reduced) 0 else 1500), label = "part") { id ->
                    if (id != null) Text(stringResource(id), style = MaterialTheme.typography.titleMedium, color = muted, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth())
                }
                // The line being said, written below the word for a while, then gone; it never moves the word.
                val line = guide?.current(elapsed ?: clock, CAPTION_SECONDS)?.what
                Crossfade(line, Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp, start = 8.dp, end = 8.dp),
                    animationSpec = tween(if (reduced) 0 else 1500), label = "line") { l ->
                    if (l != null) Text(stringResource(l.text()), style = MaterialTheme.typography.bodyLarge, color = muted, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite })
                }
            }
            Spacer(Modifier.height(56.dp))
        }
    }
}

/** How long a spoken line stays written on screen, in seconds. */
private const val CAPTION_SECONDS = 14.0
