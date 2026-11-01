package app.beansontoast.brume.core.finder

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Finding the pitch someone hears, in two moves (atelier `knowledge/projects/tinnitus-app/`, the
 * "Mon acouphène" journey): a coarse step by half-octaves across [MIN_HZ]..[MAX_HZ], then a fine dial
 * in [FINE_STEPS_PER_OCTAVE] steps around the coarse choice. Every value is rounded to 10 Hz so what
 * is written on screen is what plays.
 */
object PitchLadder {
    const val MIN_HZ = 250
    const val MAX_HZ = 12_000
    /** A quarter of a semitone a step: small enough to settle, large enough to hear move. */
    const val FINE_STEPS_PER_OCTAVE = 48
    /** The fine dial reaches half an octave either side of the coarse step: it meets the next one. */
    const val FINE_REACH = FINE_STEPS_PER_OCTAVE / 2

    fun clamp(hz: Int): Int = hz.coerceIn(MIN_HZ, MAX_HZ)
    fun round10(hz: Double): Int = (hz / 10).roundToInt() * 10

    /** 250, 350, 500, 710, 1000 … 11310 Hz: every half-octave from [MIN_HZ] that fits under [MAX_HZ]. */
    val coarse: List<Int> = generateSequence(0) { it + 1 }
        .map { MIN_HZ * 2.0.pow(it / 2.0) }
        .takeWhile { it <= MAX_HZ }
        .map { round10(it) }
        .toList()

    /** The coarse step nearest [hz] on the octave scale: where a repeat search starts. */
    fun nearestCoarse(hz: Int): Int = coarse.minBy { abs(log2(it.toDouble() / clamp(hz))) }

    /** [hz] moved by [steps] fine steps, kept in range. */
    fun fine(hz: Int, steps: Int): Int = clamp(round10(hz * 2.0.pow(steps.toDouble() / FINE_STEPS_PER_OCTAVE)))

    /** The fine dial's ends around a coarse step. */
    fun fineRange(coarseHz: Int): IntRange = fine(coarseHz, -FINE_REACH)..fine(coarseHz, FINE_REACH)

    /** Position 0..1 on a log scale between [low] and [high], and back: the dial's slider. */
    fun position(hz: Int, low: Int, high: Int): Float =
        ((ln(hz.toDouble()) - ln(low.toDouble())) / (ln(high.toDouble()) - ln(low.toDouble()))).toFloat().coerceIn(0f, 1f)

    fun at(position: Float, low: Int, high: Int): Int {
        val p = position.coerceIn(0f, 1f).toDouble()
        return clamp(round10(exp(ln(low.toDouble()) + (ln(high.toDouble()) - ln(low.toDouble())) * p)))
    }
}

/**
 * The octave check: about a quarter of people first match an octave away from what they hear
 * (Mahboubi 2012), so the match is played an octave below and above, and the person picks the closest.
 */
object OctaveCheck {
    enum class Choice { LOWER, SAME, HIGHER }

    /** The candidates to play, lowest first; one that falls outside the range is left out. */
    fun choices(hz: Int): List<Pair<Choice, Int>> = listOfNotNull(
        (Choice.LOWER to hz / 2).takeIf { hz / 2 >= PitchLadder.MIN_HZ },
        Choice.SAME to hz,
        (Choice.HIGHER to hz * 2).takeIf { hz * 2 <= PitchLadder.MAX_HZ },
    )

    /** The pitch kept after the check. */
    fun resolve(hz: Int, choice: Choice): Int = when (choice) {
        Choice.LOWER -> PitchLadder.clamp(hz / 2)
        Choice.SAME -> hz
        Choice.HIGHER -> PitchLadder.clamp(hz * 2)
    }
}

/**
 * Matches move from day to day (Hoare 2014), so a profile is shown as the range its matches cover,
 * never as one number. [centre] is the geometric mean: the middle on the ear's octave scale.
 */
data class PitchRange(val low: Int, val high: Int, val centre: Int, val count: Int) {
    val spanOctaves: Double get() = log2(high.toDouble() / low)
    val single: Boolean get() = low == high

    companion object {
        fun of(hz: List<Int>): PitchRange? {
            if (hz.isEmpty()) return null
            val centre = exp(hz.sumOf { ln(it.toDouble()) } / hz.size)
            return PitchRange(hz.min(), hz.max(), PitchLadder.round10(centre), hz.size)
        }
    }
}

/**
 * The comfort sound around a profile: soft noise in a band centred on the range, never a tone pattern,
 * never a notch, never a schedule. Wide enough to sit around every match (the span plus a third of an
 * octave each side) and at least [MIN_OCTAVES] wide so it stays a soft hiss rather than a whistle.
 */
data class ComfortBand(val centre: Int, val widthTenths: Int) {
    val octaves: Double get() = widthTenths / 10.0
    val low: Int get() = PitchLadder.round10(centre / 2.0.pow(octaves / 2))
    val high: Int get() = PitchLadder.round10(centre * 2.0.pow(octaves / 2))

    companion object {
        const val MIN_OCTAVES = 0.5
        const val MAX_OCTAVES = 2.0
        const val MARGIN_OCTAVES = 1.0 / 3
        /** Band edges stay inside what a phone and headphones carry. */
        const val FLOOR_HZ = 125
        const val CEILING_HZ = 16_000

        fun around(range: PitchRange): ComfortBand {
            val width = (range.spanOctaves + 2 * MARGIN_OCTAVES).coerceIn(MIN_OCTAVES, MAX_OCTAVES)
            val tenths = (width * 10).roundToInt()
            val half = 2.0.pow(tenths / 20.0)
            val centre = range.centre.toDouble().coerceIn(FLOOR_HZ * half, CEILING_HZ / half)
            return ComfortBand(PitchLadder.round10(centre), tenths)
        }
    }
}
