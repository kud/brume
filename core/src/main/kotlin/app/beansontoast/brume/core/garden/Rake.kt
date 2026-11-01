package app.beansontoast.brume.core.garden

import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.sin

/**
 * The raked gravel around the play stone (Iris, 2026-11-01, direction A: karesansui). Pure
 * geometry, in units of the stone's radius, so the app only scales and strokes it.
 *
 *  - Rings: concentric lines around a stone. At rest they barely drift; playing, they breathe with
 *    the sound level; on Respirer they widen with the in-breath and narrow with the out-breath.
 *  - Lines: straight parallel rakes across the frame, broken where they meet a stone's rings. On
 *    Tons their spacing follows the pitch: a low tone rakes wide, a high one close.
 *
 * Meaning is carried by spacing and count, never by colour. Every function here is continuous in
 * time, so a frame never jumps.
 */
object Rake {
    /** Gap between the stone's edge and its first ring, in stone radii. */
    const val FIRST_GAP = 0.32f

    /** The most a ring may move with the swell, as a share of its spacing, so rings never cross. */
    const val MAX_SWELL = 0.45f

    /** Low and high ends of the pitch scale used for Tons' spacing (Hz). */
    const val LOW_HZ = 250f
    const val HIGH_HZ = 12_000f

    /**
     * The radii of [count] rings around a stone of radius 1, [spacing] apart, pushed out by [swell]
     * (−1..1: −1 narrowest, 1 widest). The swell grows ring by ring, so the outer ones travel
     * furthest, like water; it is clamped to [MAX_SWELL] of a spacing, so rings never touch.
     */
    fun rings(count: Int, spacing: Float, swell: Float): FloatArray {
        val s = swell.coerceIn(-1f, 1f) * MAX_SWELL
        val out = FloatArray(count.coerceAtLeast(0))
        var r = 1f + FIRST_GAP
        for (i in out.indices) {
            out[i] = r
            // Each gap is its own spacing, swelled a little more outward: never under 55% of it.
            r += spacing * (1f + s * (i + 1) / count)
        }
        return out
    }

    /** The outer edge of the raked area around a stone: past it, the straight lines resume. */
    fun ringZone(count: Int, spacing: Float): Float =
        if (count <= 0) 1f + FIRST_GAP / 2 else rings(count, spacing, 1f).last() + spacing * 0.6f

    /** At rest: a drift of ±0.06, one slow sway every [period] seconds. [seed] desynchronises stones. */
    fun rest(t: Double, seed: Int = 0, period: Double = 23.0): Float =
        (0.06 * sin(2 * PI * t / period + seed * 1.7)).toFloat()

    /**
     * Playing: the rings breathe with the sound level, slowly (one swell every 9 s); a soft sound
     * moves them a little, a full one at most 0.5. Silence is the resting drift.
     */
    fun playing(t: Double, level: Float, seed: Int = 0): Float =
        rest(t, seed) + (0.5 * level.coerceIn(0f, 1f) * sin(2 * PI * t / 9.0 + seed)).toFloat()

    /** On Respirer: a breath at 0 (empty) to 1 (full) maps straight to narrowest..widest. */
    fun breathing(breath: Float): Float = breath.coerceIn(0f, 1f) * 2f - 1f

    /**
     * Tons' line spacing in stone radii for a pitch in Hz, on a log scale between [wide] (at
     * [LOW_HZ]) and [close] (at [HIGH_HZ]), clamped outside it: the octave is what the ear hears.
     */
    fun toneSpacing(hz: Float, wide: Float = 0.42f, close: Float = 0.14f): Float {
        val f = ((ln(hz.coerceIn(LOW_HZ, HIGH_HZ)) - ln(LOW_HZ)) / (ln(HIGH_HZ) - ln(LOW_HZ)))
        return wide + (close - wide) * f
    }

    /**
     * The y of every straight line across a frame [height] tall, [spacing] apart, offset by
     * [phase] (0..1 of a spacing) so the field can drift without a line popping in or out at an edge.
     */
    fun lines(height: Float, spacing: Float, phase: Float = 0f): FloatArray {
        if (spacing <= 0f || height <= 0f) return FloatArray(0)
        val start = ((phase % 1f) + 1f) % 1f * spacing
        val n = ((height - start) / spacing).toInt() + 1
        return FloatArray(n.coerceAtLeast(0)) { start + it * spacing }
    }
}
