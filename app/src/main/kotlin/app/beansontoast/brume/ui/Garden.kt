package app.beansontoast.brume.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.beansontoast.brume.R
import app.beansontoast.brume.core.breath.Pattern
import app.beansontoast.brume.core.garden.Rake
import app.beansontoast.brume.core.scene.SceneMotion
import kotlinx.coroutines.delay
import kotlin.math.sqrt

/*
 * Direction A, karesansui (Iris, 2026-11-01): the art belongs to the interface. A ground (washi by
 * day, graphite at night), one stone that is the play control, and raked gravel the app draws as
 * vectors around it. Meaning lives in line count, spacing and weight, never in a hue.
 *
 * Sacha's files drop in behind two seams, without code changes:
 *  - the stone: res/drawable/stone_night.xml and stone_day.xml are placeholders; delete each and
 *    put drawable-nodpi/stone_night.png / stone_day.png (1024 square) in its place.
 *  - the ground: res/drawable/ground_night.xml and ground_day.xml likewise, replaced by
 *    drawable-nodpi/ground_night.png / ground_day.png (1440 x 3120, the grain).
 */

/** Night assets on a dark ground, day assets on washi: read from the theme, so every screen agrees. */
@Composable
internal fun gardenNight(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

/** The stone, as Sacha paints it (or the placeholder until then). */
@Composable
internal fun stonePainter(): Painter = painterResource(if (gardenNight()) R.drawable.stone_night else R.drawable.stone_day)

/** The play stone: a calm shape with value, no outline, no glow. */
@Composable
internal fun Stone(modifier: Modifier = Modifier) {
    Image(stonePainter(), contentDescription = null, modifier = modifier, contentScale = ContentScale.Fit)
}

/** The ground every screen stands on, cropped from the centre like paper. */
@Composable
internal fun Ground(modifier: Modifier = Modifier) {
    Image(painterResource(if (gardenNight()) R.drawable.ground_night else R.drawable.ground_day), contentDescription = null,
        modifier = modifier, contentScale = ContentScale.Crop)
}

/** How a scene rakes its garden. [rings] around the play stone, [lines] apart elsewhere, in stone radii. */
internal class Rakes(val rings: Int, val spacing: Float, val lines: Float, val second: Boolean = false)

internal fun Scene.rakes(pitch: Int): Rakes = when (this) {
    // Dormir: one stone, wide, slow ripples.
    Scene.SLEEP -> Rakes(rings = 5, spacing = 0.24f, lines = 0.26f)
    // Se poser: two stones, parallel lines.
    Scene.SETTLE -> Rakes(rings = 3, spacing = 0.2f, lines = 0.2f, second = true)
    // Respirer: rings that follow the breath.
    Scene.BREATHE -> Rakes(rings = 5, spacing = 0.22f, lines = 0.24f)
    // Tons: straight lines whose spacing follows the pitch.
    Scene.TONES -> Rakes(rings = 2, spacing = 0.2f, lines = Rake.toneSpacing(pitch.toFloat()))
}

/**
 * The raked gravel around a stone at [stone] (in this canvas, or unspecified until laid out). The
 * rings' swell comes from [swell] at draw time, so a tick redraws without recomposing. Straight
 * lines stop where they meet a stone's rings, as a rake does.
 */
@Composable
internal fun RakedGravel(rakes: Rakes, stone: () -> Offset, swell: () -> Float, modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onSurface
    val second = stonePainter()
    Canvas(modifier.clipToBounds()) {
        val centre = stone()
        if (!centre.isSpecified()) return@Canvas
        val r = STONE_RADIUS.toPx()
        val line = Stroke(1.dp.toPx())
        val rings = Rake.rings(rakes.rings, rakes.spacing, swell())
        val holes = mutableListOf(Triple(centre.x, centre.y, Rake.ringZone(rakes.rings, rakes.spacing) * r))
        rings.forEachIndexed { i, ring ->
            // Inner rings a touch firmer than outer ones: depth by weight, not colour.
            drawCircle(ink.copy(alpha = 0.2f - i * 0.02f), ring * r, centre, style = line)
        }
        if (rakes.second) {
            val small = r * SECOND_SCALE
            val at = Offset(centre.x - r * 2.6f, centre.y + r * 1.2f)
            translate(at.x - small, at.y - small) { with(second) { draw(Size(small * 2, small * 2)) } }
            Rake.rings(1, 0.3f, 0f).forEach { drawCircle(ink.copy(alpha = 0.18f), it * small, at, style = line) }
            holes += Triple(at.x, at.y, Rake.ringZone(1, 0.3f) * small)
        }
        // The straight rakes fill the garden from the top of the stone's rings down.
        val top = centre.y - holes[0].third
        Rake.lines(size.height - top, rakes.lines * r).forEach { dy -> rakeLine(top + dy, holes, ink.copy(alpha = 0.11f), line.width) }
    }
}

private fun Offset.isSpecified() = x.isFinite() && y.isFinite()

/** One straight rake across the width, broken around every stone's raked circle. */
private fun DrawScope.rakeLine(y: Float, holes: List<Triple<Float, Float, Float>>, color: Color, width: Float) {
    val gaps = holes.mapNotNull { (cx, cy, zone) ->
        val d = y - cy
        if (d * d >= zone * zone) null else sqrt(zone * zone - d * d).let { (cx - it) to (cx + it) }
    }.sortedBy { it.first }
    var x = 0f
    gaps.forEach { (from, to) ->
        if (from > x) drawLine(color, Offset(x, y), Offset(from, y), width)
        x = maxOf(x, to)
    }
    if (x < size.width) drawLine(color, Offset(x, y), Offset(size.width, y), width)
}

/** The swell for a scene now: the breath on Respirer, the sound level when playing, a drift at rest. */
internal fun gardenSwell(t: Double, still: Boolean, breathe: Pattern?, playing: Boolean, level: Float, seed: Int): Float = when {
    still -> 0f
    breathe != null -> Rake.breathing(SceneMotion.breath(t, breathe))
    playing -> Rake.playing(t, level, seed)
    else -> Rake.rest(t, seed)
}

/** The play stone's radius: half the 112 dp face. */
internal val STONE_RADIUS = 56.dp
private const val SECOND_SCALE = 0.5f

/**
 * The garden clock in milliseconds, for the drift and the breath. It ticks at [SceneMotion.FRAME_MS]
 * and only while the app is in front, then picks up where it was, so nothing jumps. Reduced motion
 * never starts it.
 */
@Composable
internal fun rememberSceneClock(still: Boolean): () -> Long {
    val clock = remember { mutableLongStateOf(0L) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(still, lifecycle) {
        if (still) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val base = clock.longValue
            val start = withFrameNanos { it }
            while (true) {
                withFrameNanos { clock.longValue = base + (it - start) / 1_000_000 }
                delay(SceneMotion.FRAME_MS)
            }
        }
    }
    return { clock.longValue }
}
