package app.beansontoast.brume.core.sound

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Left/right balance, -1 (left only) .. 0 (centre) .. +1 (right only), one setting for every layer.
 *
 * The law only ever attenuates: centre leaves both channels as they are, and moving towards one side
 * lowers the other, linearly in amplitude, to silence at the end (50% is -6 dB on the far side). The
 * near side stays at unity. Constant-power panning is ruled out on purpose: it lifts the near ear
 * above its centre level (+3 dB at the extreme), and Brume never gets louder on its own (ADR 0002).
 */
object Balance {
    const val CENTRE = 0f
    /** The slider moves in tenths, so the words ("Left 30%") always match a stop. */
    const val STEP = 0.1f
    const val STOPS = 20

    fun clamp(balance: Float): Float = if (balance.isNaN()) CENTRE else balance.coerceIn(-1f, 1f)

    /** Gain on the left channel: 1 from the far left to centre, falling to 0 at the far right. */
    fun left(balance: Float): Float = 1f - clamp(balance).coerceAtLeast(0f)

    /** Gain on the right channel: 0 at the far left, rising to 1 at centre and staying there. */
    fun right(balance: Float): Float = 1f + clamp(balance).coerceAtMost(0f)

    /** Rounds to the nearest tenth; the centre is a stop, so a near-centre drag lands on it exactly. */
    fun snap(balance: Float): Float {
        val stepped = (clamp(balance) / STEP).roundToInt() * STEP
        return if (abs(stepped) < STEP / 2) CENTRE else stepped.coerceIn(-1f, 1f)
    }

    /** How far off centre, as a whole percentage: 0 at centre, 100 at either end. */
    fun percent(balance: Float): Int = (abs(snap(balance)) * 100).roundToInt()
}
