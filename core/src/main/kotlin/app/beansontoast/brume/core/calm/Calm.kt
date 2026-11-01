package app.beansontoast.brume.core.calm

/**
 * Meditation timing (practice-research.md, Erwann 2026-10-31). A bell marks each edge and the
 * screen dims, so a session is followed with the eyes closed; since 2026-11-01 a guided session is
 * also spoken (`guide/MeditationGuide`), over these same parts. `:core` holds when each part starts
 * and ends and when a bell rings; the app holds the words.
 *
 * Every guided session, whatever its anchor, has the same shape: arrive, the anchor, then
 * wander-and-return stretches whose silences grow, then close. Respiration, Corps and Sons share
 * the timing and differ in what the words before the start and the step word name.
 */
enum class Guided { BREATH, BODY, SOUNDS }

/** What a part of a session is for. */
enum class Part { ARRIVE, ANCHOR, WANDER, CLOSE, SETTLE, SIT, RETURN }

/** The guided lengths, in minutes. */
val GUIDED_MINUTES = listOf(3, 5, 10)

/** The guided shape for [minutes]: arrive, anchor, growing silences, close; exactly [minutes] long. */
fun guided(minutes: Int): Timeline {
    val wander = when (minutes) {
        3 -> listOf(40, 50)
        5 -> listOf(45, 60, 75)
        10 -> listOf(60, 75, 90, 110, 130)
        else -> throw IllegalArgumentException("guided sessions are 3, 5 or 10 minutes")
    }
    val arrive = if (minutes == 3) 30 else 45
    val anchor = when (minutes) { 3 -> 40; 5 -> 45; else -> 60 }
    val close = if (minutes == 3) 20 else 30
    val parts = listOf(Part.ARRIVE to arrive, Part.ANCHOR to anchor) + wander.map { Part.WANDER to it } + listOf(Part.CLOSE to close)
    return Timeline(parts.map { it.second }, parts.map { it.first }, bellEachStep = true)
}

/** The unguided timer's lengths, in minutes. */
val SIT_MINUTES = listOf(5, 10, 15, 20)

/** How long the timer's soft start and soft end each last, in seconds. */
const val SIT_SOFT_SECONDS = 20

/** A sit of [minutes]: settle, sit, come back. Exactly [minutes] long; bells only at the start and the end. */
fun sit(minutes: Int): Timeline {
    require(minutes * 60 > 2 * SIT_SOFT_SECONDS) { "a sit must be longer than its soft start and end" }
    return Timeline(listOf(SIT_SOFT_SECONDS, minutes * 60 - 2 * SIT_SOFT_SECONDS, SIT_SOFT_SECONDS),
        listOf(Part.SETTLE, Part.SIT, Part.RETURN), bellEachStep = false)
}

/** How a bell rings: the start and the end at full strength, a step change softer. */
enum class Ring(val strength: Float) { START(1f), STEP(0.6f), END(1f) }

/** Parts of whole seconds, in order, and whether every step change rings a bell. */
data class Timeline(val steps: List<Int>, val parts: List<Part>, val bellEachStep: Boolean) {
    init {
        require(steps.isNotEmpty() && steps.all { it > 0 }) { "every step lasts at least a second" }
        require(parts.size == steps.size) { "one part per step" }
    }

    val totalSeconds: Int = steps.sum()

    /** When each bell rings, in whole seconds from the start: the start, each step change (guided), the end. */
    val bells: List<Pair<Int, Ring>> get() {
        val changes = if (bellEachStep) steps.runningFold(0) { a, s -> a + s }.drop(1).dropLast(1).map { it to Ring.STEP } else emptyList()
        return listOf(0 to Ring.START) + changes + listOf(totalSeconds to Ring.END)
    }

    /** The bells that fall in (from, to], for a clock that moves in ticks; the start bell when from < 0. */
    fun bellsBetween(from: Double, to: Double): List<Ring> = bells.filter { (at, _) -> at > from && at <= to }.map { it.second }

    /** Where the session is after [elapsed] seconds. Before 0 is the start; at or after the end, [Place.done]. */
    fun at(elapsed: Double): Place {
        val e = elapsed.coerceAtLeast(0.0)
        if (e >= totalSeconds) return Place(steps.lastIndex, parts.last(), steps.last().toDouble(), 0.0, 0.0, done = true)
        var start = 0
        steps.forEachIndexed { i, s ->
            if (e < start + s) return Place(i, parts[i], e - start, start + s - e, totalSeconds - e, done = false)
            start += s
        }
        error("unreachable") // e < totalSeconds by construction
    }
}

/** A point in a timeline. [stepElapsed] and [stepLeft] are seconds inside step [step]. */
data class Place(val step: Int, val part: Part, val stepElapsed: Double, val stepLeft: Double, val totalLeft: Double, val done: Boolean) {
    /**
     * How bright the screen is, 0 to 1: full for the first [ramp] seconds so the session can be
     * seen to start, then it dims away and stays dim, rising again only over the last [ramp]
     * seconds so the end is seen. The eyes can close in between.
     */
    fun light(timeline: Timeline, ramp: Double = 8.0): Float {
        if (done) return 1f
        val elapsed = timeline.totalSeconds - totalLeft
        val rise = 1 - (elapsed / ramp).coerceIn(0.0, 1.0)
        val fall = 1 - (totalLeft / ramp).coerceIn(0.0, 1.0)
        return maxOf(rise, fall).toFloat()
    }
}
