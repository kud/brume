package app.beansontoast.brume.core.calm

/**
 * Meditation timing (ADR 0003, Calm). A session is a timeline of steps, each a whole number of
 * seconds; the app holds the words, `:core` holds only when each step starts and ends, so the
 * pacing is tested here and both languages share it.
 *
 * Guided sessions are a few short, timed text steps. The first step always says the session can
 * stop at any time; the app's strings carry that line. The timer is an unguided sit with a soft
 * start and a soft end: a short settling step, the quiet middle, a short return.
 */
enum class Guided(val steps: List<Int>) {
    /** Body scan: arrive, feet, legs, belly and back, hands and arms, shoulders and neck, face, the whole body, return. */
    BODY_SCAN(listOf(30, 45, 45, 45, 45, 45, 45, 45, 30)),
    /**
     * Sounds as they are: arrive, the sounds around, a sound inside as one sound among others
     * (noticed briefly, then attention widens again), letting sounds come and go, widening when
     * attention snags, resting on the breath or the feet, return. The step that names the sound
     * inside is kept short on purpose: never a long, close focus on it.
     */
    SOUNDS(listOf(30, 45, 30, 60, 60, 45, 30)),
    /** Back to the breath: arrive, where the breath is felt, letting it be, wandering and coming back, resting, return. */
    BREATH(listOf(30, 45, 45, 60, 30, 30));

    val plan: Timeline get() = Timeline(steps)
}

/** The unguided timer's lengths, in minutes. */
val SIT_MINUTES = listOf(5, 10, 15, 20)

/** How long the timer's soft start and soft end each last, in seconds. */
const val SIT_SOFT_SECONDS = 20

/** A sit of [minutes]: settle for [SIT_SOFT_SECONDS], sit, come back for [SIT_SOFT_SECONDS]. Exactly [minutes] long. */
fun sit(minutes: Int): Timeline {
    require(minutes * 60 > 2 * SIT_SOFT_SECONDS) { "a sit must be longer than its soft start and end" }
    return Timeline(listOf(SIT_SOFT_SECONDS, minutes * 60 - 2 * SIT_SOFT_SECONDS, SIT_SOFT_SECONDS))
}

/** Steps of whole seconds, in order. */
data class Timeline(val steps: List<Int>) {
    init { require(steps.isNotEmpty() && steps.all { it > 0 }) { "every step lasts at least a second" } }

    val totalSeconds: Int = steps.sum()

    /** Where the session is after [elapsed] seconds. Before 0 is the start; at or after the end, [Place.done]. */
    fun at(elapsed: Double): Place {
        val e = elapsed.coerceAtLeast(0.0)
        if (e >= totalSeconds) return Place(steps.lastIndex, steps.last().toDouble(), 0.0, 0.0, done = true)
        var start = 0
        steps.forEachIndexed { i, s ->
            if (e < start + s) return Place(i, e - start, start + s - e, totalSeconds - e, done = false)
            start += s
        }
        error("unreachable") // e < totalSeconds by construction
    }
}

/** A point in a timeline. [stepElapsed] and [stepLeft] are seconds inside step [step]. */
data class Place(val step: Int, val stepElapsed: Double, val stepLeft: Double, val totalLeft: Double, val done: Boolean) {
    /**
     * How present the guide's mist is, 0 to 1: it rises over the first step's first seconds and
     * settles over the last step's last seconds, so a session starts and ends softly. [ramp] is
     * how long each ramp lasts.
     */
    fun presence(timeline: Timeline, ramp: Double = SIT_SOFT_SECONDS.toDouble()): Float {
        if (done) return 0f
        val elapsed = timeline.totalSeconds - totalLeft
        val rise = (elapsed / ramp).coerceIn(0.0, 1.0)
        val fall = (totalLeft / ramp).coerceIn(0.0, 1.0)
        return minOf(rise, fall).toFloat()
    }
}
