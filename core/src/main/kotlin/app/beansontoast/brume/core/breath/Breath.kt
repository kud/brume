package app.beansontoast.brume.core.breath

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt

/** One part of a breath. */
enum class Phase { IN, HOLD, OUT }

data class Step(val phase: Phase, val seconds: Int)

/**
 * A breathing pattern: the steps of one breath, in order. Every pattern ends on the out-breath,
 * so a session that stops between breaths always stops after breathing out.
 */
enum class Pattern(val steps: List<Step>) {
    /** In for 4, out for 6: the longer out-breath. */
    LONG_OUT(listOf(Step(Phase.IN, 4), Step(Phase.OUT, 6))),
    /** In for 5, out for 5: about six breaths a minute. */
    EVEN(listOf(Step(Phase.IN, 5), Step(Phase.OUT, 5))),
    /** In for 4, hold for 7, out for 8. */
    FOUR_SEVEN_EIGHT(listOf(Step(Phase.IN, 4), Step(Phase.HOLD, 7), Step(Phase.OUT, 8)));

    val breathSeconds: Int get() = steps.sumOf { it.seconds }
}

/** The lengths offered, in minutes. */
val SESSION_MINUTES = listOf(2, 5, 10)

/**
 * A session: whole breaths only, never cut mid-breath. The count is the chosen length divided by
 * one breath, rounded to the nearest whole breath (at least one), so the total is within half a
 * breath of the chosen length, and exactly it when a breath divides it (4-6 and 5-5 always do).
 */
data class BreathPlan(val pattern: Pattern, val minutes: Int) {
    val breaths: Int = ((minutes * 60.0) / pattern.breathSeconds).roundToInt().coerceAtLeast(1)
    val totalSeconds: Int = breaths * pattern.breathSeconds

    /** Where the session is after [elapsed] seconds. Before 0 is the start; at or after the end, [Moment.done]. */
    fun at(elapsed: Double): Moment {
        val e = elapsed.coerceAtLeast(0.0)
        if (e >= totalSeconds) {
            val last = pattern.steps.last()
            return Moment(breaths - 1, pattern.steps.lastIndex, last.phase, last.seconds.toDouble(), 0.0, 0.0, done = true)
        }
        val breath = (e / pattern.breathSeconds).toInt()
        var within = e - breath * pattern.breathSeconds
        pattern.steps.forEachIndexed { i, step ->
            if (within < step.seconds) return Moment(breath, i, step.phase, within, step.seconds - within, totalSeconds - e, done = false)
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
    val done: Boolean,
) {
    /** The whole seconds still to count in this step, as written on screen: 4, 3, 2, 1. */
    val count: Int get() = kotlin.math.ceil(stepLeft).toInt().coerceAtLeast(1)

    /**
     * How full the breath is, 0 (out) to 1 (in), eased so it starts and settles softly: rises
     * through the in-breath, rests at 1 while held, falls through the out-breath. Continuous
     * across every step change, so the shape never jumps.
     */
    val fullness: Float get() {
        if (done) return 0f
        val p = (stepElapsed / (stepElapsed + stepLeft)).coerceIn(0.0, 1.0)
        val ease = ((1 - cos(PI * p)) / 2).toFloat()
        return when (phase) { Phase.IN -> ease; Phase.HOLD -> 1f; Phase.OUT -> 1f - ease }
    }
}
