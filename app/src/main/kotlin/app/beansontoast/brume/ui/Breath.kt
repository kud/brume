package app.beansontoast.brume.ui

import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.beansontoast.brume.R
import app.beansontoast.brume.core.breath.BreathPlan
import app.beansontoast.brume.core.breath.Pattern
import app.beansontoast.brume.core.breath.Phase
import app.beansontoast.brume.core.breath.SESSION_MINUTES
import app.beansontoast.design.SelectChip
import app.beansontoast.design.SettingsGroup
import app.beansontoast.design.SwitchRow
import app.beansontoast.design.rememberReducedMotion
import app.beansontoast.design.tabular
import kotlinx.coroutines.delay

/** The choices a Souffle session starts from. Kept per phone (ADR 0003). */
data class BreathSetup(
    val pattern: Pattern = Pattern.LONG_OUT,
    val minutes: Int = 5,
    val haptics: Boolean = true,
    val sound: Boolean = false,
)

@StringRes internal fun Pattern.title() = when (this) {
    Pattern.LONG_OUT -> R.string.breath_long
    Pattern.SIGH -> R.string.breath_sigh
    Pattern.BOX -> R.string.breath_box
    Pattern.FOUR_SEVEN_EIGHT -> R.string.breath_478
}

/** Every phase written out, so nothing about the rhythm is hidden. */
@StringRes internal fun Pattern.phases() = when (this) {
    Pattern.LONG_OUT -> R.string.breath_long_phases
    Pattern.SIGH -> R.string.breath_sigh_phases
    Pattern.BOX -> R.string.breath_box_phases
    Pattern.FOUR_SEVEN_EIGHT -> R.string.breath_478_phases
}

@StringRes private fun Phase.word() = when (this) {
    Phase.IN -> R.string.breath_in; Phase.TOP -> R.string.breath_top; Phase.HOLD -> R.string.breath_hold; Phase.OUT -> R.string.breath_out
}

/**
 * Breathe's setup: the four rhythms in order, each with its phases, the line that holds are
 * optional, the length (or the cycle cap), two options, then Start.
 */
@Composable
fun BreathPanel(setup: BreathSetup, soundName: String?, onChange: (BreathSetup) -> Unit, onStart: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column {
        Text(stringResource(R.string.breath_intro), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
        SectionTitle(stringResource(R.string.breath_rhythm), Modifier.padding(top = 4.dp))
        Column(Modifier.selectableGroup()) {
            Pattern.entries.forEach { ChoiceRow(stringResource(it.title()), stringResource(it.phases()), setup.pattern == it) { onChange(setup.copy(pattern = it)) } }
        }
        Text(stringResource(R.string.breath_holds_note), style = MaterialTheme.typography.bodyMedium, color = muted, modifier = Modifier.padding(top = 4.dp))
        if (setup.pattern.holds) Text(stringResource(R.string.breath_478_note), style = MaterialTheme.typography.bodyMedium, color = muted,
            modifier = Modifier.padding(top = 4.dp))

        SectionTitle(stringResource(R.string.breath_length), Modifier.padding(top = 20.dp))
        val cycles = setup.pattern.cycles
        if (cycles != null) Text(stringResource(R.string.breath_cycles, cycles), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 4.dp))
        else Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SESSION_MINUTES.forEach { m -> SelectChip(stringResource(R.string.breath_minutes, m), setup.minutes == m, { onChange(setup.copy(minutes = m)) }) }
        }

        SettingsGroup(stringResource(R.string.breath_options), Modifier.padding(top = 12.dp)) {
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
 * A session, full screen (Iris, 2026-10-31; Breathly's approach, Brume's look). Night mist: a band
 * of thin horizon lines that rises as the breath fills and settles as it empties. No filled shape,
 * nothing bright: at 3 a.m. it must not light the room. One word says what to do, a written count
 * carries the rhythm, the time left sits at the top beside the way out.
 *  - Reduced motion: the band rests still at mid height; the word and the count carry everything.
 *  - Haptics: one light tap at each change, so the eyes can close; nothing if the phone can't.
 *  - The screen stays on for the session.
 * [elapsed] fixes the time for renders; otherwise it runs from [startedAt] (wall clock, ms).
 */
@Composable
fun BreathSession(plan: BreathPlan, startedAt: Long, night: Boolean, haptics: Boolean, onEnd: () -> Unit, elapsed: Double? = null, still: Boolean? = null) {
    val context = LocalContext.current
    val reduced = still ?: rememberReducedMotion()
    var clock by remember { mutableDoubleStateOf(((System.currentTimeMillis() - startedAt) / 1000.0).coerceAtLeast(0.0)) }
    LaunchedEffect(startedAt, elapsed) {
        if (elapsed == null) while (clock < plan.totalSeconds) {
            clock = (System.currentTimeMillis() - startedAt) / 1000.0
            delay(if (reduced) 200 else 33)
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
            val end = stringResource(R.string.breath_end)
            TextButton(onClick = onEnd, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = end }) {
                Icon(painterResource(R.drawable.ic_close), null, tint = muted, modifier = Modifier.size(20.dp))
                Text(end, style = MaterialTheme.typography.labelLarge, color = muted, modifier = Modifier.padding(start = 6.dp))
            }
            if (!moment.done) {
                val left = kotlin.math.ceil(moment.totalLeft).toInt()
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
            Text(stringResource(moment.phase.word()), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite })
            // The count is written, not drawn: it carries the rhythm when the band is still. TalkBack hears only the word.
            Text(moment.count.toString(), style = MaterialTheme.typography.titleLarge.tabular(), color = muted, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 56.dp).semantics { contentDescription = "" })
        }
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
