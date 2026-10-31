package app.beansontoast.brume.ui

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.beansontoast.brume.R
import app.beansontoast.brume.core.sound.Ambience
import app.beansontoast.brume.core.sound.NoiseColour
import app.beansontoast.brume.core.sound.SoundEngine.Mode
import app.beansontoast.brume.playback.PlayerUi
import app.beansontoast.design.tabular
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/*
 * "Playing" (Iris, 2026-10-31): the full-screen view that opens when a sound or the tone pattern
 * starts. Breathly's approach, not its look: one calm field, what is playing in words, the timer,
 * a pause and a way back. The field is Brume's own horizon lines, each sound's texture drifting.
 *  - Calm at 3 a.m.: no filled mass, only thin lines, low alpha, lower again at night.
 *  - The drift is slow and aperiodic (incommensurate periods of a minute or more), so nothing
 *    pulses in a rhythm that could read as the sound itself.
 *  - Reduced motion (animator scale 0) rests still. Playing or paused is said in words.
 */
@Composable
fun PlayingView(ui: PlayerUi, night: Boolean, onPause: () -> Unit, onBack: () -> Unit, time: Float? = null) {
    val context = LocalContext.current
    val still = remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    var clock by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(still, ui.playing, time) {
        if (time == null && !still && ui.playing) {
            val start = System.nanoTime() - (clock * 1e9f).toLong()
            while (true) { clock = (System.nanoTime() - start) / 1e9f; delay(66) } // ~15 fps is plenty for a drift this slow
        }
    }
    val t = time ?: clock
    val scheme = MaterialTheme.colorScheme
    val muted = scheme.onSurfaceVariant
    val title = when {
        ui.mode == Mode.TONES -> stringResource(R.string.ux_tone_kind)
        ui.noise != null -> stringResource(ui.noise.label())
        ui.ambience != null -> stringResource(ui.ambience.label())
        else -> ""
    }
    val detail = when {
        ui.mode == Mode.TONES -> stringResource(R.string.tones_pitch_value, ui.tones.pitch)
        ui.noise != null && ui.ambience != null -> stringResource(R.string.play_with, stringResource(ui.ambience.label()))
        else -> null
    }
    Column(Modifier.fillMaxSize().background(scheme.background).safeDrawingPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val back = stringResource(R.string.play_back)
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = back }) {
                Icon(painterResource(R.drawable.ic_back), null, tint = muted, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.learn_back), style = MaterialTheme.typography.labelLarge, color = muted,
                    modifier = Modifier.padding(start = 4.dp))
            }
            Text(
                ui.timerRemainingMinutes?.let { stringResource(R.string.timer_left, it) } ?: stringResource(R.string.play_no_timer),
                style = MaterialTheme.typography.bodyMedium.tabular(), color = muted, textAlign = TextAlign.End,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
        }
        val fade = if (night) 0.55f else 1f
        Canvas(Modifier.weight(1f).fillMaxWidth()) { drift(ui, t, muted, fade) }
        Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().semantics { heading() })
        if (detail != null) Text(detail, style = MaterialTheme.typography.bodyLarge, color = muted, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        Text(stringResource(if (ui.playing) R.string.play_state_playing else R.string.play_state_paused),
            style = MaterialTheme.typography.labelLarge, color = muted, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).semantics { liveRegion = LiveRegionMode.Polite })
        val action = stringResource(if (ui.playing) R.string.action_pause else R.string.play_resume)
        Column(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // An outlined ring, never a filled disc: nothing bright to light the room.
            Box(Modifier.size(72.dp).clip(CircleShape).border(1.5.dp, scheme.outline, CircleShape)
                .clickable(role = Role.Button, onClickLabel = action, onClick = onPause), contentAlignment = Alignment.Center) {
                Icon(painterResource(if (ui.playing) R.drawable.ic_pause else R.drawable.ic_play), action, tint = scheme.onSurface,
                    modifier = Modifier.size(28.dp))
            }
            Text(action, style = MaterialTheme.typography.labelLarge, color = muted)
        }
    }
}

/** Slow, aperiodic: the sum of incommensurate sines with periods of one to several minutes. */
private fun wander(t: Float, i: Int): Float =
    (sin(t * 0.0211f + i * 1.9f) + 0.6f * sin(t * 0.0137f * (1 + 0.37f * i) + i * 0.7f) + 0.3f * sin(t * 0.0059f + i)) / 1.9f

/**
 * One horizon line: tapered toward both ends, as in the icon, drawn as short segments whose width
 * and alpha follow a sine envelope.
 */
internal fun DrawScope.horizon(y: Float, amp: Float, wavelength: Float, phase: Float, x0: Float, x1: Float, color: Color, width: Float) {
    val step = 3f * density
    var x = x0
    var prev = Offset(x, y + amp * sin(2 * PI.toFloat() * x / wavelength + phase))
    while (x < x1) {
        x = (x + step).coerceAtMost(x1)
        val p = Offset(x, y + amp * sin(2 * PI.toFloat() * x / wavelength + phase))
        val env = sin(PI.toFloat() * ((x - x0) / (x1 - x0)).coerceIn(0f, 1f))
        drawLine(color.copy(alpha = color.alpha * env), prev, p, (width * (0.25f + 0.75f * env)).coerceAtLeast(0.5f), StrokeCap.Round)
        prev = p
    }
}

/** The field: the noise as a few long horizon lines, the nature sound or the tones laid over it, fainter. */
internal fun DrawScope.drift(ui: PlayerUi, t: Float, ink: Color, fade: Float) {
    val w = size.width; val h = size.height; val px = density; val mid = h * 0.52f
    val tones = ui.mode == Mode.TONES
    data class Kind(val lines: Int, val amp: Float, val wave: Float, val width: Float, val gap: Float)
    val k = when (if (tones) null else ui.noise) {
        NoiseColour.BROWN -> Kind(3, 0.035f, 1.8f, 3.2f, 0.09f)
        NoiseColour.PINK -> Kind(5, 0.03f, 0.8f, 2f, 0.06f)
        NoiseColour.WHITE -> Kind(4, 0.012f, 0.9f, 1.6f, 0.05f)
        NoiseColour.GREY -> Kind(4, 0.01f, 1.2f, 2f, 0.06f)
        null -> Kind(3, 0.015f, 1.2f, 1.6f, 0.08f)
    }
    for (i in 0 until k.lines) {
        val main = i == 0
        val a = (if (main) 0.42f else 0.26f - 0.03f * i) * fade
        val y = mid + h * k.gap * i + h * 0.012f * wander(t, i + 5)
        val inset = w * (0.06f + 0.07f * i + 0.03f * wander(t, i + 9))
        horizon(y, h * k.amp, w * k.wave, t * 0.05f * (1 + 0.3f * i) + 2f * wander(t, i), inset, w - inset * 0.8f,
            ink.copy(alpha = a), k.width * px * (if (main) 1.4f else 0.8f))
    }
    if (ui.noise == NoiseColour.WHITE && !tones) grain(t, ink.copy(alpha = 0.16f * fade), w, h, px, mid)
    val over = ink.copy(alpha = 0.2f * fade)
    when {
        tones -> ui.tones.trims.forEachIndexed { i, trim -> // four quiet strokes, one per band, wandering apart
            val y = mid - h * (0.1f + 0.05f * i) + h * 0.01f * wander(t, i + 20)
            val cx = w * (0.3f + 0.13f * i) + w * 0.04f * wander(t, i + 30)
            horizon(y, 0f, w, 0f, cx - w * 0.08f * trim, cx + w * 0.08f * trim, over, 2f * px)
        }
        ui.ambience == Ambience.RAIN -> {
            val r = java.util.Random(7)
            repeat(28) {
                val x = r.nextFloat() * w; val speed = 0.004f + 0.004f * r.nextFloat()
                val y = ((r.nextFloat() + t * speed) % 1f) * (mid - h * 0.04f)
                val l = (6f + 6f * r.nextFloat()) * px
                drawLine(over, Offset(x, y), Offset(x - l * 0.25f, y + l), 1.2f * px, StrokeCap.Round)
            }
        }
        ui.ambience == Ambience.SURF -> horizon(mid - h * 0.12f, h * (0.03f + 0.015f * wander(t, 40)), w * 0.55f, t * 0.03f, w * 0.05f, w * 0.95f, over, 1.6f * px)
        ui.ambience == Ambience.WIND -> {
            val r = java.util.Random(3)
            repeat(6) { i ->
                val len = w * (0.2f + 0.2f * r.nextFloat()); val speed = 0.006f + 0.004f * r.nextFloat()
                val x0 = ((r.nextFloat() + t * speed) % 1.4f - 0.2f) * w
                horizon(mid - h * (0.08f + 0.05f * i), h * 0.01f, w * 0.7f, i.toFloat(), x0, x0 + len, over, 1.3f * px)
            }
        }
        else -> Unit
    }
}

private fun DrawScope.grain(t: Float, color: Color, w: Float, h: Float, px: Float, mid: Float) {
    val r = java.util.Random(1)
    repeat(60) {
        val x = ((r.nextFloat() + t * 0.002f * (1 + r.nextFloat())) % 1f) * w
        val y = mid - h * 0.2f + r.nextFloat() * h * 0.45f
        drawLine(color, Offset(x, y), Offset(x + (3f + 4f * r.nextFloat()) * px, y), 1f * px, StrokeCap.Round)
    }
}
