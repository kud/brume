package app.beansontoast.brume.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.StringRes
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.beansontoast.brume.R
import app.beansontoast.brume.core.breath.BreathPlan
import app.beansontoast.brume.core.breath.Cue
import app.beansontoast.brume.core.breath.LengthUnit
import app.beansontoast.brume.core.breath.Pattern
import app.beansontoast.brume.core.breath.Phase
import app.beansontoast.brume.core.breath.Technique
import app.beansontoast.brume.core.breath.Variant
import app.beansontoast.brume.core.breath.Variants
import app.beansontoast.brume.core.guide.BreathGuide
import app.beansontoast.brume.voice.Voice
import app.beansontoast.design.SelectChip
import app.beansontoast.design.SettingsGroup
import app.beansontoast.design.SwitchRow
import app.beansontoast.design.calmAlpha
import app.beansontoast.design.rememberReducedMotion
import app.beansontoast.design.tabular
import kotlinx.coroutines.delay
import java.text.NumberFormat
import kotlin.math.ceil
import kotlin.math.floor

/** The choices a Souffle session starts from. Kept per phone (ADR 0003). */
data class BreathSetup(
    val technique: Technique = Technique.DEFAULT,
    /** The pace chosen per technique; beginner by default wherever there are holds. */
    val variants: Variants = Variants(),
    /** For the techniques that run by time; each takes the nearest length it offers. */
    val minutes: Int = 5,
    /** For the techniques that run by rounds (Bourdonnement; 4-7-8 is always four). */
    val rounds: Int = 7,
    val haptics: Boolean = true,
    val sound: Boolean = false,
    val voice: Boolean = true,
) {
    val pattern: Pattern get() = technique.pattern(variants[technique])
    val byRounds: Boolean get() = technique.length.unit == LengthUnit.ROUNDS
    /** The length in the technique's own unit, brought to one it offers. */
    val length: Int get() = technique.length.nearest(if (byRounds) rounds else minutes)
    fun plan(): BreathPlan = BreathPlan.of(pattern, length)
    fun withLength(n: Int) = if (byRounds) copy(rounds = n) else copy(minutes = n)

    /** Whether to read something before the start: how to sit, and the hold note the first time. */
    fun prelude(holdNoteSeen: Boolean): Boolean = technique.prepare || (pattern.holds && !holdNoteSeen)
}

@StringRes internal fun Technique.title() = when (this) {
    Technique.LONG_OUT -> R.string.breath_long
    Technique.SIGH -> R.string.breath_sigh
    Technique.HUM -> R.string.breath_hum
    Technique.BOX -> R.string.breath_box
    Technique.FOUR_SEVEN_EIGHT -> R.string.breath_478
}

/** What it is and how to sit for it: « assis » for every technique not suited to lying down. */
@StringRes internal fun Technique.hint() = when (this) {
    Technique.LONG_OUT -> R.string.breath_long_hint
    Technique.SIGH -> R.string.breath_sigh_hint
    Technique.HUM -> R.string.breath_hum_hint
    Technique.BOX -> R.string.breath_box_hint
    Technique.FOUR_SEVEN_EIGHT -> R.string.breath_478_hint
}

@StringRes internal fun Variant.label() = when (this) {
    Variant.BEGINNER -> R.string.variant_beginner
    Variant.STANDARD -> R.string.variant_standard
    Variant.HOLD -> R.string.variant_hold
}

/** The cue as written on screen. */
@StringRes internal fun Cue.text() = when (this) {
    Cue.IN_NOSE -> R.string.cue_in_nose
    Cue.TOP_NOSE -> R.string.cue_top_nose
    Cue.HOLD_FULL -> R.string.cue_hold_full
    Cue.OUT_SLOW -> R.string.cue_out_slow
    Cue.OUT_MOUTH_LONG -> R.string.cue_out_mouth_long
    Cue.OUT_MOUTH_BLOW -> R.string.cue_out_mouth_blow
    Cue.HUM -> R.string.cue_hum
    Cue.HOLD_EMPTY -> R.string.cue_hold_empty
}

/** The cue as spoken: the sigh's top-up lasts one second, so it is said short. */
@StringRes internal fun Cue.spoken() = if (this == Cue.TOP_NOSE) R.string.cue_top_say else text()

/** Every phase written out with its seconds, from the steps themselves: « Inspirez 2 · retenez 3,5 · expirez 4 ». */
@Composable
internal fun Pattern.phasesText(): String {
    val format = NumberFormat.getNumberInstance(LocalConfiguration.current.locales[0])
    val parts = steps.map { step ->
        val n = if (step.seconds % 1.0 == 0.0) step.seconds.toInt().toString() else format.format(step.seconds)
        stringResource(when {
            step.cue == Cue.HUM -> R.string.phase_hum
            step.phase == Phase.IN -> R.string.phase_in
            step.phase == Phase.TOP -> R.string.phase_top
            step.phase == Phase.HOLD -> R.string.phase_hold
            else -> R.string.phase_out
        }, n)
    }
    return parts.joinToString(" · ").replaceFirstChar { it.uppercase() }
}

/** The voice row: what it does, or, once, that this phone has no voice for the language. */
@Composable
internal fun VoiceRow(on: Boolean, state: Voice.State, @StringRes summary: Int, onChange: (Boolean) -> Unit) {
    SwitchRow(stringResource(R.string.voice_title), stringResource(if (state == Voice.State.MISSING) R.string.voice_missing else summary), on, onChange)
}

/**
 * Breathe's setup: the five techniques in order, each with what it is and how to sit; its pace and
 * every phase written out; the hold line (kept in place, faded, when there is no hold); the length
 * (minutes or rounds); the options; then Start.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BreathPanel(setup: BreathSetup, soundName: String?, voice: Voice.State, onChange: (BreathSetup) -> Unit, onStart: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column {
        Text(stringResource(R.string.breath_intro), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
        SectionTitle(stringResource(R.string.breath_rhythm), Modifier.padding(top = 4.dp))
        Column(Modifier.selectableGroup()) {
            Technique.entries.forEach { ChoiceRow(stringResource(it.title()), stringResource(it.hint()), setup.technique == it) { onChange(setup.copy(technique = it)) } }
        }

        SectionTitle(stringResource(R.string.breath_variant), Modifier.padding(top = 8.dp))
        Row(Modifier.padding(top = 4.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            setup.technique.offered.forEach { v ->
                SelectChip(stringResource(v.label()), setup.pattern.variant == v, { onChange(setup.copy(variants = setup.variants.with(setup.technique, v))) })
            }
        }
        Text(setup.pattern.phasesText(), style = MaterialTheme.typography.bodyLarge.tabular(), modifier = Modifier.padding(top = 8.dp))
        // Always laid out, so nothing below moves when a technique without holds is chosen.
        Text(stringResource(R.string.breath_hold_easy), style = MaterialTheme.typography.bodyMedium, color = muted, minLines = 2,
            modifier = Modifier.padding(top = 4.dp).alpha(calmAlpha(setup.pattern.holds))
                .then(if (setup.pattern.holds) Modifier else Modifier.clearAndSetSemantics { }))

        SectionTitle(stringResource(if (setup.byRounds) R.string.breath_rounds_title else R.string.breath_length), Modifier.padding(top = 12.dp))
        FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            setup.technique.length.options.forEach { n ->
                val label = stringResource(if (setup.byRounds) R.string.breath_cycles else R.string.breath_minutes, n)
                SelectChip(label, setup.length == n, { onChange(setup.withLength(n)) })
            }
        }

        SettingsGroup(stringResource(R.string.breath_options), Modifier.padding(top = 12.dp)) {
            VoiceRow(setup.voice, voice, R.string.voice_breath_summary) { onChange(setup.copy(voice = it)) }
            SwitchRow(stringResource(R.string.breath_haptics), stringResource(R.string.breath_haptics_summary), setup.haptics) { onChange(setup.copy(haptics = it)) }
            if (soundName != null) SwitchRow(stringResource(R.string.breath_sound), stringResource(R.string.breath_sound_summary, soundName), setup.sound) {
                onChange(setup.copy(sound = it))
            } else Column(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 8.dp)) {
                Text(stringResource(R.string.breath_sound), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.breath_sound_none), style = MaterialTheme.typography.bodyMedium, color = muted)
            }
        }
        FilledTonalButton(onStart, Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 24.dp).heightIn(min = 56.dp)) {
            Text(stringResource(R.string.breath_start), style = MaterialTheme.typography.titleMedium)
        }
    }
}

/**
 * What to read before a session starts, on a calm page of its own: how to sit for Bourdonnement and
 * 4-7-8, the hold line for any technique with holds, and, the first time a hold comes up, the
 * safety note. Nothing is spoken here; it is read with the eyes open. Start begins the session.
 */
@Composable
fun BreathPrelude(setup: BreathSetup, safety: Boolean, onStart: () -> Unit, onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val muted = scheme.onSurfaceVariant
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize().background(scheme.background).safeDrawingPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val back = stringResource(R.string.breath_back)
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = back }) {
                Icon(painterResource(R.drawable.ic_close), null, tint = muted, modifier = Modifier.size(20.dp))
                Text(back, style = MaterialTheme.typography.labelLarge, color = muted, modifier = Modifier.padding(start = 6.dp))
            }
        }
        Column(Modifier.weight(1f).verticalScroll(scroll).padding(horizontal = 8.dp)) {
            Text(stringResource(setup.technique.title()), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 24.dp))
            Text(setup.pattern.phasesText(), style = MaterialTheme.typography.bodyLarge.tabular(), color = muted, modifier = Modifier.padding(top = 4.dp))
            val lines = buildList {
                if (setup.technique == Technique.HUM) { add(R.string.prelude_hum); add(R.string.prelude_hum_gentle) }
                if (setup.technique == Technique.FOUR_SEVEN_EIGHT) add(R.string.prelude_478)
                if (setup.pattern.holds) add(R.string.breath_hold_easy)
            }
            if (lines.isNotEmpty()) {
                SectionTitle(stringResource(R.string.prelude_title), Modifier.padding(top = 20.dp))
                lines.forEach { Text(stringResource(it), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 6.dp)) }
            }
            if (safety) {
                SectionTitle(stringResource(R.string.prelude_safety_title), Modifier.padding(top = 20.dp))
                Text(stringResource(R.string.prelude_safety), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 6.dp))
            }
        }
        FilledTonalButton(onStart, Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 24.dp).heightIn(min = 56.dp)) {
            Text(stringResource(R.string.prelude_start), style = MaterialTheme.typography.titleMedium)
        }
    }
}

/**
 * A session, full screen (Iris, 2026-10-31; Breathly's approach, Brume's look). Night mist: a band
 * of thin horizon lines that rises as the breath fills and settles as it empties. No filled shape,
 * nothing bright: at 3 a.m. it must not light the room. The cue says what to do and how (« Inspirez
 * par le nez »), a written count carries the rhythm, and a hold also shows its seconds as marks
 * that fill (a shape and a number, never a colour). « Respirer normalement » is always at the top:
 * stopping is never a failure, the session simply fades away.
 *  - Voice ([speak], if on): the cue at the start of each step for the first breaths, then one
 *    breath in three (`BreathGuide`). Never anything inside a hold but the cue that starts it.
 *  - Reduced motion: the band rests still at mid height; the words and the count carry everything.
 *  - Haptics: one light tap at each change, so the eyes can close; nothing if the phone can't.
 *  - The screen stays on for the session.
 * [elapsed] fixes the time for renders; otherwise it runs from [startedAt] (wall clock, ms).
 */
@Composable
fun BreathSession(
    plan: BreathPlan, startedAt: Long, night: Boolean, haptics: Boolean, onEnd: () -> Unit,
    elapsed: Double? = null, still: Boolean? = null, speak: ((Cue) -> Unit)? = null,
) {
    val context = LocalContext.current
    val reduced = still ?: rememberReducedMotion()
    val guide = remember(plan) { BreathGuide(plan) }
    val say by rememberUpdatedState(speak)
    var clock by remember { mutableDoubleStateOf(((System.currentTimeMillis() - startedAt) / 1000.0).coerceAtLeast(0.0)) }
    LaunchedEffect(startedAt, elapsed) {
        if (elapsed != null) return@LaunchedEffect
        // Cues already said before a rotation are not said again.
        var heard = if (clock < 0.5) -1.0 else clock
        while (true) {
            clock = (System.currentTimeMillis() - startedAt) / 1000.0
            guide.between(heard, clock).lastOrNull()?.let { say?.invoke(it.what) }
            heard = clock
            if (clock >= plan.totalSeconds) break
            delay(if (reduced) 100 else 33)
        }
    }
    val moment = plan.at(elapsed ?: clock)

    val view = LocalView.current
    if (elapsed == null) DisposableEffect(view) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }
    val tapper = remember { if (elapsed == null && haptics) Tapper(context) else null }
    LaunchedEffect(moment.breath, moment.step, moment.done) { if (clock > 0.5 || moment.done) tapper?.tap() }

    val scheme = MaterialTheme.colorScheme
    val muted = scheme.onSurfaceVariant
    val fade = if (night) 0.55f else 1f
    Column(Modifier.fillMaxSize().background(scheme.background).safeDrawingPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val stop = stringResource(R.string.breath_stop)
            TextButton(onClick = onEnd, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = stop }) {
                Icon(painterResource(R.drawable.ic_close), null, tint = muted, modifier = Modifier.size(20.dp))
                Text(stop, style = MaterialTheme.typography.labelLarge, color = muted, modifier = Modifier.padding(start = 6.dp))
            }
            if (!moment.done) {
                val left = ceil(moment.totalLeft).toInt()
                Text(stringResource(R.string.breath_left, left / 60, left % 60), style = MaterialTheme.typography.bodyMedium.tabular(), color = muted,
                    textAlign = TextAlign.End, modifier = Modifier.weight(1f).padding(end = 8.dp))
            }
        }
        val fullness = if (reduced) 0.5f else moment.fullness
        Canvas(Modifier.weight(1f).fillMaxWidth()) { mistBand(fullness, muted, fade) }
        if (moment.done) {
            Text(stringResource(R.string.breath_done), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite })
            Text(stringResource(R.string.breath_done_body), style = MaterialTheme.typography.bodyLarge, color = muted, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            OutlinedButton(onEnd, Modifier.align(Alignment.CenterHorizontally).padding(top = 24.dp, bottom = 32.dp).heightIn(min = 48.dp)) {
                Text(stringResource(R.string.breath_back))
            }
        } else {
            // Two lines kept for the cue, so a short one and a long one sit at the same height.
            Text(stringResource(moment.cue.text()), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, minLines = 2,
                modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite })
            // The count is written, not drawn: it carries the rhythm when the band is still. TalkBack hears only the cue.
            Text(moment.count.toString(), style = MaterialTheme.typography.titleLarge.tabular(), color = muted, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).semantics { contentDescription = "" })
            // A hold's seconds as marks: filled for each second held, outlined for those to come. Room kept when not holding.
            val holding = moment.phase == Phase.HOLD
            val marks = ceil(plan.pattern.steps[moment.step].seconds - 1e-9).toInt()
            val held = floor(moment.stepElapsed + 1e-9).toInt().coerceAtMost(marks)
            val marksLabel = stringResource(R.string.breath_hold_marks, held, marks)
            Canvas(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 40.dp).height(12.dp)
                .then(if (holding) Modifier.semantics { contentDescription = marksLabel } else Modifier.clearAndSetSemantics { })) {
                if (holding) holdMarks(marks, held, muted.copy(alpha = 0.8f * fade + 0.2f))
            }
        }
    }
}

/** [count] small circles in a row, centred: the first [filled] full, the rest outlined. */
internal fun DrawScope.holdMarks(count: Int, filled: Int, ink: Color) {
    val r = 4.dp.toPx(); val gap = 10.dp.toPx()
    val width = count * 2 * r + (count - 1) * gap
    var x = (size.width - width) / 2 + r
    repeat(count) { i ->
        val c = Offset(x, size.height / 2)
        if (i < filled) drawCircle(ink, r, c) else drawCircle(ink, r - 0.5f.dp.toPx(), c, style = Stroke(1.dp.toPx()))
        x += 2 * r + gap
    }
}

/**
 * The mist band: five thin horizon lines around a centre that rises from low (empty) to high (full),
 * spreading a little as it fills. Alpha stays low and lower again at night; nothing is filled.
 */
internal fun DrawScope.mistBand(fullness: Float, ink: Color, fade: Float) {
    val w = size.width; val h = size.height
    val centre = h * (0.78f - 0.5f * fullness)
    val spread = h * (0.035f + 0.03f * fullness)
    for (i in -2..2) {
        val weight = 1f - 0.28f * kotlin.math.abs(i)
        val y = centre + i * spread
        val x0 = w * (0.06f + 0.05f * kotlin.math.abs(i)); val x1 = w - x0
        val alpha = (0.18f + 0.22f * fullness) * weight * fade
        horizon(y, h * 0.008f, w * (0.9f + 0.15f * i), (i * 1.3f + fullness * 0.8f), x0, x1, ink.copy(alpha = alpha), (1.2f + 1.0f * weight) * density)
    }
    // One faint line stays on the horizon below: where the breath settles back to.
    horizon(h * 0.86f, 0f, w, 0f, w * 0.2f, w * 0.8f, ink.copy(alpha = 0.12f * fade), 1f * density)
}

/** One soft tap, or nothing when the phone has no vibrator. */
internal class Tapper(context: Context) {
    private val vibrator: Vibrator? = (
        if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)
        )?.takeIf { it.hasVibrator() }

    fun tap() {
        val v = vibrator ?: return
        runCatching { v.vibrate(VibrationEffect.createOneShot(25, if (v.hasAmplitudeControl()) 60 else VibrationEffect.DEFAULT_AMPLITUDE)) }
    }
}
