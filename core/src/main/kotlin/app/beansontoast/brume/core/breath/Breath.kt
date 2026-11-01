package app.beansontoast.brume.core.breath

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt

/** One part of a breath. [TOP] is the short second in-breath of a sigh, on top of the first. */
enum class Phase { IN, TOP, HOLD, OUT }

/**
 * What a step asks for, written on screen and spoken by the guide (Erwann, 2026-11-01: « inspirez,
 * expirez, retenez »). The app holds the words; `:core` says which cue starts which step.
 */
enum class Cue { IN_NOSE, TOP_NOSE, HOLD_FULL, OUT_SLOW, OUT_MOUTH_LONG, OUT_MOUTH_BLOW, HUM, HOLD_EMPTY }

/** A step of a breath, its [cue], and how full the breath is when it ends (0 empty, 1 full). */
data class Step(val phase: Phase, val seconds: Double, val fullAfter: Float, val cue: Cue) {
    init { require(seconds > 0) { "a step lasts some time" } }
}

private fun inhale(s: Double, to: Float = 1f) = Step(Phase.IN, s, to, Cue.IN_NOSE)
private fun top(s: Double) = Step(Phase.TOP, s, 1f, Cue.TOP_NOSE)
private fun holdFull(s: Double) = Step(Phase.HOLD, s, 1f, Cue.HOLD_FULL)
private fun holdEmpty(s: Double) = Step(Phase.HOLD, s, 0f, Cue.HOLD_EMPTY)
private fun exhale(s: Double, cue: Cue = Cue.OUT_SLOW) = Step(Phase.OUT, s, 0f, cue)
private fun steps(vararg s: Step) = s.toList()

/**
 * How hard a technique is taken: [BEGINNER] is shorter (and, with holds, the default), [STANDARD]
 * the published timing, [HOLD] Souffle long with a short pause at the top.
 */
enum class Variant { BEGINNER, STANDARD, HOLD }

/** Whether a technique runs for a number of minutes or a number of rounds. */
enum class LengthUnit { MINUTES, ROUNDS }

/** The lengths a technique offers, in [unit], and the one it starts on. */
data class Length(val unit: LengthUnit, val options: List<Int>, val default: Int) {
    init { require(default in options) { "the default length is one of the options" } }

    /** The offered length nearest to [wanted] (a stored value from an older set, say). */
    fun nearest(wanted: Int): Int = options.minBy { abs(it - wanted) }
}

private fun minutes(vararg m: Int, default: Int) = Length(LengthUnit.MINUTES, m.toList(), default)
private fun rounds(vararg r: Int, default: Int) = Length(LengthUnit.ROUNDS, r.toList(), default)

/**
 * The breathing set (Erwann, 2026-11-01, from practice-research.md: « trop approximatif », precise
 * and research-based), in the order offered. Every pattern starts on the in-breath and ends with
 * the breath out or held empty, so a session that stops between breaths always stops empty.
 *  - [sleep]: no hold and nothing to do but breathe, so it suits lying down at night. The others
 *    are done sitting up.
 *  - [prepare]: a line to read before the start (how to sit, where the tongue goes).
 *
 * The names are the stored ids: they never change (old ids migrate in [fromStored]).
 */
enum class Technique(
    val variants: Map<Variant, List<Step>>,
    val defaultVariant: Variant,
    val length: Length,
    val sleep: Boolean,
    val prepare: Boolean = false,
) {
    /** Souffle long, resonance breathing near six breaths a minute: in 4, out 6. The default. */
    LONG_OUT(
        mapOf(
            Variant.BEGINNER to steps(inhale(3.0), exhale(5.0)),
            Variant.STANDARD to steps(inhale(4.0), exhale(6.0)),
            Variant.HOLD to steps(inhale(4.0), holdFull(2.0), exhale(6.0)),
        ),
        Variant.STANDARD, minutes(3, 5, 10, 15, 20, default = 5), sleep = true,
    ),

    /** Soupir cyclique (Balban 2023): a nose in-breath of 2, a 1 s top-up, a long 6 s mouth out-breath. */
    SIGH(
        mapOf(Variant.STANDARD to steps(inhale(2.0, 0.8f), top(1.0), exhale(6.0, Cue.OUT_MOUTH_LONG))),
        Variant.STANDARD, minutes(3, 5, 10, default = 5), sleep = true,
    ),

    /** Bourdonnement (bhramari): in 4, a gentle hum out for 8, by rounds. */
    HUM(
        mapOf(
            Variant.BEGINNER to steps(inhale(3.0), exhale(6.0, Cue.HUM)),
            Variant.STANDARD to steps(inhale(4.0), exhale(8.0, Cue.HUM)),
        ),
        Variant.STANDARD, rounds(5, 7, 9, 11, default = 7), sleep = false, prepare = true,
    ),

    /** Respiration carrée: 4-4-4-4, or 4-2-4-2 to begin (the default). */
    BOX(
        mapOf(
            Variant.BEGINNER to steps(inhale(4.0), holdFull(2.0), exhale(4.0), holdEmpty(2.0)),
            Variant.STANDARD to steps(inhale(4.0), holdFull(4.0), exhale(4.0), holdEmpty(4.0)),
        ),
        Variant.BEGINNER, minutes(1, 3, 5, 10, default = 5), sleep = false,
    ),

    /** 4-7-8, at most four cycles (its author's rule); 2-3.5-4 to begin, the same ratio (the default). */
    FOUR_SEVEN_EIGHT(
        mapOf(
            Variant.BEGINNER to steps(inhale(2.0), holdFull(3.5), exhale(4.0, Cue.OUT_MOUTH_BLOW)),
            Variant.STANDARD to steps(inhale(4.0), holdFull(7.0), exhale(8.0, Cue.OUT_MOUTH_BLOW)),
        ),
        Variant.BEGINNER, rounds(4, default = 4), sleep = false, prepare = true,
    );

    /** The variants offered, in order. */
    val offered: List<Variant> get() = Variant.entries.filter { it in variants }

    /** The pattern of [variant], or of the default when this technique doesn't offer it. */
    fun pattern(variant: Variant = defaultVariant): Pattern {
        val v = variant.takeIf { it in variants } ?: defaultVariant
        return Pattern(this, v, variants.getValue(v))
    }

    companion object {
        val DEFAULT = LONG_OUT

        /**
         * The technique stored on this phone. The ids are the old pattern names, kept: Souffle long,
         * Soupir, Carré and 4-7-8 come back as they were. 5-5 ("EVEN") left the set on 2026-11-01:
         * its users move to Souffle long, the nearest and the default. Anything unknown does too.
         */
        fun fromStored(name: String?): Technique = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/**
 * The variant chosen per technique, stored as "LONG_OUT=HOLD,BOX=STANDARD". Missing, unknown or not
 * offered, a technique gets its default: beginner wherever there are holds.
 */
data class Variants(val chosen: Map<Technique, Variant> = emptyMap()) {
    operator fun get(t: Technique): Variant = chosen[t]?.takeIf { it in t.variants } ?: t.defaultVariant
    fun with(t: Technique, v: Variant) = Variants(chosen + (t to v))
    fun encode(): String = chosen.entries.sortedBy { it.key.ordinal }.joinToString(",") { "${it.key.name}=${it.value.name}" }

    companion object {
        fun decode(text: String?): Variants = Variants(
            text.orEmpty().split(',').mapNotNull { pair ->
                val (t, v) = pair.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
                val technique = Technique.entries.firstOrNull { it.name == t } ?: return@mapNotNull null
                val variant = Variant.entries.firstOrNull { it.name == v }?.takeIf { it in technique.variants } ?: return@mapNotNull null
                technique to variant
            }.toMap(),
        )
    }
}

/** A technique at one variant: the steps of one breath. */
data class Pattern(val technique: Technique, val variant: Variant, val steps: List<Step>) {
    init { require(steps.isNotEmpty() && steps.last().fullAfter == 0f) { "a breath ends empty" } }

    val breathSeconds: Double get() = steps.sumOf { it.seconds }
    val holds: Boolean get() = steps.any { it.phase == Phase.HOLD }

    companion object {
        val DEFAULT: Pattern = Technique.DEFAULT.pattern()
    }
}

/**
 * A session: whole breaths only, never cut mid-breath. By minutes, the count is the chosen length
 * divided by one breath, rounded to the nearest whole breath (at least one); by rounds, the rounds.
 */
data class BreathPlan(val pattern: Pattern, val breaths: Int) {
    init { require(breaths >= 1) { "at least one breath" } }

    val totalSeconds: Double = breaths * pattern.breathSeconds

    /** Where the session is after [elapsed] seconds. Before 0 is the start; at or after the end, [Moment.done]. */
    fun at(elapsed: Double): Moment {
        val e = elapsed.coerceAtLeast(0.0)
        val steps = pattern.steps
        if (e >= totalSeconds) {
            val last = steps.last()
            return Moment(breaths - 1, steps.lastIndex, last.phase, last.cue, last.seconds, 0.0, 0.0, 0f, 0f, done = true)
        }
        val breath = (e / pattern.breathSeconds).toInt().coerceAtMost(breaths - 1)
        var within = e - breath * pattern.breathSeconds
        steps.forEachIndexed { i, step ->
            val from = if (i == 0) 0f else steps[i - 1].fullAfter
            if (within < step.seconds || i == steps.lastIndex) {
                val inStep = within.coerceAtMost(step.seconds)
                return Moment(breath, i, step.phase, step.cue, inStep, step.seconds - inStep, totalSeconds - e, from, step.fullAfter, done = false)
            }
            within -= step.seconds
        }
        error("unreachable") // the last step always returns
    }

    /** When each step starts, in seconds from the start, with its breath and step index. */
    fun starts(): Sequence<StepStart> = sequence {
        var t = 0.0
        for (b in 0 until breaths) pattern.steps.forEachIndexed { i, step -> yield(StepStart(t, b, i, step)); t += step.seconds }
    }

    companion object {
        /** A plan of [length] in the technique's own unit, brought to the nearest length it offers. */
        fun of(pattern: Pattern, length: Int): BreathPlan {
            val l = pattern.technique.length
            val n = l.nearest(length)
            return when (l.unit) {
                LengthUnit.ROUNDS -> BreathPlan(pattern, n)
                LengthUnit.MINUTES -> BreathPlan(pattern, ((n * 60.0) / pattern.breathSeconds).roundToInt().coerceAtLeast(1))
            }
        }
    }
}

/** A step starting [at] seconds into a session. */
data class StepStart(val at: Double, val breath: Int, val index: Int, val step: Step)

/** A point in a session. [stepElapsed] and [stepLeft] are seconds inside the current step. */
data class Moment(
    val breath: Int,
    val step: Int,
    val phase: Phase,
    val cue: Cue,
    val stepElapsed: Double,
    val stepLeft: Double,
    val totalLeft: Double,
    val fullFrom: Float,
    val fullTo: Float,
    val done: Boolean,
) {
    /** The whole seconds still to count in this step, as written on screen: 4, 3, 2, 1. */
    val count: Int get() = kotlin.math.ceil(stepLeft - 1e-9).toInt().coerceAtLeast(1)

    /** How far through this step, 0 to 1: a hold's progress, drawn as marks that fill. */
    val progress: Float get() = (stepElapsed / (stepElapsed + stepLeft)).coerceIn(0.0, 1.0).toFloat()

    /**
     * How full the breath is, 0 (out) to 1 (in), eased between where the step starts and where it
     * ends, so it starts and settles softly and never jumps at a step change. A hold rests.
     */
    val fullness: Float get() {
        if (done) return 0f
        val ease = ((1 - cos(PI * progress)) / 2).toFloat()
        return fullFrom + (fullTo - fullFrom) * ease
    }
}
