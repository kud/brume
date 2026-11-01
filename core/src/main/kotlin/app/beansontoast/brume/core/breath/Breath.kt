package app.beansontoast.brume.core.breath

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt

/** One part of a breath. [TOP] is the short second in-breath of a sigh, on top of the first. */
enum class Phase { IN, TOP, HOLD, OUT }

/** A step of a breath, and how full the breath is when it ends (0 empty, 1 full). */
data class Step(val phase: Phase, val seconds: Int, val fullAfter: Float)

private fun inhale(s: Int, to: Float = 1f) = Step(Phase.IN, s, to)
private fun top(s: Int) = Step(Phase.TOP, s, 1f)
private fun holdFull(s: Int) = Step(Phase.HOLD, s, 1f)
private fun holdEmpty(s: Int) = Step(Phase.HOLD, s, 0f)
private fun exhale(s: Int) = Step(Phase.OUT, s, 0f)

/**
 * The breathing set (Erwann, 2026-10-31, from practice-research.md), in the order offered. Every
 * pattern starts on the in-breath and ends with the breath out, so a session that stops between
 * breaths always stops empty. Holds are in two of them only, and the app says they are optional.
 * [cycles] caps a pattern by count instead of minutes: 4-7-8's author says four at first.
 */
enum class Pattern(val steps: List<Step>, val cycles: Int? = null) {
    /** Souffle long: in for 4, out for 6. No hold. The default. */
    LONG_OUT(listOf(inhale(4), exhale(6))),
    /** Soupir (cyclic sighing): in for 2, a 1 s top-up, a long 6 s out. No hold. */
    SIGH(listOf(inhale(2, 0.8f), top(1), exhale(6))),
    /** Carré: in 4, hold 4, out 4, hold 4. */
    BOX(listOf(inhale(4), holdFull(4), exhale(4), holdEmpty(4))),
    /** In 4, hold 7, out 8, at most four cycles. */
    FOUR_SEVEN_EIGHT(listOf(inhale(4), holdFull(7), exhale(8)), cycles = 4);

    val breathSeconds: Int get() = steps.sumOf { it.seconds }
    val holds: Boolean get() = steps.any { it.phase == Phase.HOLD }

    companion object {
        val DEFAULT = LONG_OUT

        /**
         * The pattern stored on this phone. 5-5 ("EVEN") left the set on 2026-11-01: its users move
         * to Souffle long, the nearest and the default. Anything unknown does too.
         */
        fun fromStored(name: String?): Pattern = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/** The lengths offered, in minutes, for patterns that run by time. */
val SESSION_MINUTES = listOf(2, 5, 10)

/**
 * A session: whole breaths only, never cut mid-breath. By time, the count is the chosen length
 * divided by one breath, rounded to the nearest whole breath (at least one). A pattern with
 * [Pattern.cycles] runs that many breaths whatever the minutes.
 */
data class BreathPlan(val pattern: Pattern, val minutes: Int) {
    val breaths: Int = pattern.cycles ?: ((minutes * 60.0) / pattern.breathSeconds).roundToInt().coerceAtLeast(1)
    val totalSeconds: Int = breaths * pattern.breathSeconds

    /** Where the session is after [elapsed] seconds. Before 0 is the start; at or after the end, [Moment.done]. */
    fun at(elapsed: Double): Moment {
        val e = elapsed.coerceAtLeast(0.0)
        val steps = pattern.steps
        if (e >= totalSeconds) {
            val last = steps.last()
            return Moment(breaths - 1, steps.lastIndex, last.phase, last.seconds.toDouble(), 0.0, 0.0, 0f, 0f, done = true)
        }
        val breath = (e / pattern.breathSeconds).toInt()
        var within = e - breath * pattern.breathSeconds
        steps.forEachIndexed { i, step ->
            val from = if (i == 0) 0f else steps[i - 1].fullAfter
            if (within < step.seconds) return Moment(breath, i, step.phase, within, step.seconds - within, totalSeconds - e, from, step.fullAfter, done = false)
            within -= step.seconds
        }
        error("unreachable") // within < breathSeconds by construction
    }
}

/** A point in a session. [stepElapsed] and [stepLeft] are seconds inside the current step. */
data class Moment(
    val breath: Int,
    val step: Int,
    val phase: Phase,
    val stepElapsed: Double,
    val stepLeft: Double,
    val totalLeft: Double,
    val fullFrom: Float,
    val fullTo: Float,
    val done: Boolean,
) {
    /** The whole seconds still to count in this step, as written on screen: 4, 3, 2, 1. */
    val count: Int get() = kotlin.math.ceil(stepLeft).toInt().coerceAtLeast(1)

    /**
     * How full the breath is, 0 (out) to 1 (in), eased between where the step starts and where it
     * ends, so it starts and settles softly and never jumps at a step change. A hold rests.
     */
    val fullness: Float get() {
        if (done) return 0f
        val p = (stepElapsed / (stepElapsed + stepLeft)).coerceIn(0.0, 1.0)
        val ease = ((1 - cos(PI * p)) / 2).toFloat()
        return fullFrom + (fullTo - fullFrom) * ease
    }
}
