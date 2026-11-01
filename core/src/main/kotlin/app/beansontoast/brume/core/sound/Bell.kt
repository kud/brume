package app.beansontoast.brume.core.sound

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * The meditation bell, synthesised like every other sound (no audio files): a soft bowl of three
 * inharmonic partials that swells in over [ATTACK_SECONDS] (a raised cosine, so there is no strike
 * and no click) and fades away over a few seconds, ending in true silence.
 */
object Bell {
    const val ATTACK_SECONDS = 0.12
    const val SECONDS = 5.0
    private const val BASE_HZ = 392.0
    private val partials = listOf(Triple(1.0, 1.0, 1.6), Triple(2.76, 0.32, 2.4), Triple(5.4, 0.1, 3.2)) // ratio, amplitude, decay per second

    /** One ring, mono, peak exactly [gain] (never more), at [rate]. */
    fun render(rate: Int, gain: Float): FloatArray {
        val n = (rate * SECONDS).toInt()
        val attack = (rate * ATTACK_SECONDS).toInt()
        val tail = (rate * 0.4).toInt()
        val raw = DoubleArray(n) { i ->
            val t = i.toDouble() / rate
            var s = 0.0
            for ((ratio, amp, decay) in partials) s += amp * exp(-decay * t) * sin(2 * PI * BASE_HZ * ratio * t)
            val swell = if (i < attack) (1 - cos(PI * i / attack)) / 2 else 1.0
            val end = if (i >= n - tail) (1 + cos(PI * (i - (n - tail)) / tail)) / 2 else 1.0
            s * swell * end
        }
        val peak = raw.maxOf { abs(it) }.takeIf { it > 0 } ?: 1.0
        return FloatArray(n) { (raw[it] / peak * gain).toFloat() }
    }

    /**
     * The bell's gain: the session's own level (the sound under it, or the listening level when
     * nothing plays) times the ring's [strength], so a bell is never louder than the session.
     */
    fun gain(sessionLevel: Float, strength: Float): Float =
        LevelRules.gainFor(LevelRules.clampLevel(sessionLevel)) * strength.coerceIn(0f, 1f)
}
