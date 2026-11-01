package app.beansontoast.brume.core.guide

import app.beansontoast.brume.core.breath.BreathPlan
import app.beansontoast.brume.core.breath.Cue
import app.beansontoast.brume.core.calm.Guided
import app.beansontoast.brume.core.calm.Part
import app.beansontoast.brume.core.calm.Timeline
import app.beansontoast.brume.core.calm.guided

/**
 * Spoken guidance (Erwann, 2026-11-01): pre-written lines, no generated text. `:core` says what to
 * say and when, in seconds from the start of a session; the app holds the words (FR and EN) and
 * speaks them with the phone's own offline voice, or writes them on screen when there is none.
 */
data class Spoken<T>(val at: Double, val what: T)

/** What to say, and when, over a whole session. */
interface Guide<T> {
    /** Every line of the session, in order of time. */
    val lines: List<Spoken<T>>

    /** The lines that fall in (from, to], for a clock that moves in ticks; the first line when from < 0. */
    fun between(from: Double, to: Double): List<Spoken<T>> = lines.filter { it.at > from && it.at <= to }

    /** The line last started at or before [elapsed], if it started less than [showFor] seconds ago. */
    fun current(elapsed: Double, showFor: Double): Spoken<T>? =
        lines.lastOrNull { it.at <= elapsed }?.takeIf { elapsed - it.at < showFor }
}

/**
 * How often breathing cues are spoken (on screen they always show): every step of the first
 * [fullBreaths] breaths, then every step of one breath in [thenEvery], so the voice leads the
 * rhythm in and then leaves room for it. With the defaults: breaths 1, 2, 3, then 6, 9, 12…
 */
data class Thinning(val fullBreaths: Int = 3, val thenEvery: Int = 3) {
    init { require(fullBreaths >= 1 && thenEvery >= 1) }

    /** Whether breath [breath] (from 0) is spoken. */
    fun speaks(breath: Int): Boolean = breath < fullBreaths || (breath + 1) % thenEvery == 0
}

/**
 * The breathing guide: a step's cue at the moment the step starts, and nothing else. A hold is
 * never spoken into: only the cue that starts it is said, at its very start.
 */
class BreathGuide(plan: BreathPlan, thinning: Thinning = Thinning()) : Guide<Cue> {
    override val lines: List<Spoken<Cue>> =
        plan.starts().filter { thinning.speaks(it.breath) }.map { Spoken(it.at, it.step.cue) }.toList()
}

/**
 * The meditation lines, one id per sentence block. The words are the app's (`med_say_*`).
 * Calm, plain, non-judgemental; no claims, no promise of an effect.
 */
enum class Line {
    ARRIVE_POSTURE, ARRIVE_EYES, ARRIVE_BREATHS,
    BREATH_ANCHOR, BREATH_NOTICE,
    BREATH_WANDER_1, BREATH_WANDER_2, BREATH_WANDER_3, BREATH_WANDER_4, BREATH_WANDER_5,
    BODY_ANCHOR, BODY_NOTICE,
    BODY_LOWER, BODY_UPPER, BODY_LEGS, BODY_TORSO, BODY_TOP, BODY_BACK, BODY_CHEST, BODY_ARMS, BODY_FACE,
    SOUNDS_ANCHOR, SOUNDS_NOTICE,
    SOUNDS_WANDER_1, SOUNDS_WANDER_2, SOUNDS_WANDER_3, SOUNDS_WANDER_4, SOUNDS_WANDER_5,
    CLOSE_RETURN, CLOSE_OPEN,
}

/**
 * A guided meditation's script, laid on its [timeline] (the bells' parts). Silence is most of it:
 * three short lines while arriving (the first after the start bell has swelled), two for the
 * anchor, one at the start of each wander-and-return stretch (so one every 45 s to 2 min), and two
 * to close, the last some seconds before the end bell.
 */
class MeditationGuide(val kind: Guided, minutes: Int) : Guide<Line> {
    val timeline: Timeline = guided(minutes)

    override val lines: List<Spoken<Line>> = buildList {
        var start = 0.0
        var wander = 0
        val wanders = timeline.parts.count { it == Part.WANDER }
        timeline.steps.forEachIndexed { i, seconds ->
            when (timeline.parts[i]) {
                Part.ARRIVE -> {
                    val gap = (seconds - 9) / 3.0 // 45 s: 4, 16, 28; 30 s: 4, 11, 18
                    add(Spoken(start + 4, Line.ARRIVE_POSTURE))
                    add(Spoken(start + 4 + gap, Line.ARRIVE_EYES))
                    add(Spoken(start + 4 + 2 * gap, Line.ARRIVE_BREATHS))
                }
                Part.ANCHOR -> {
                    val (anchor, notice) = when (kind) {
                        Guided.BREATH -> Line.BREATH_ANCHOR to Line.BREATH_NOTICE
                        Guided.BODY -> Line.BODY_ANCHOR to Line.BODY_NOTICE
                        Guided.SOUNDS -> Line.SOUNDS_ANCHOR to Line.SOUNDS_NOTICE
                    }
                    add(Spoken(start + 4, anchor))
                    add(Spoken(start + seconds / 2.0, notice))
                }
                Part.WANDER -> { add(Spoken(start + 4, wanderLine(kind, wander, wanders))); wander++ }
                Part.CLOSE -> {
                    add(Spoken(start + 3, Line.CLOSE_RETURN))
                    add(Spoken(start + seconds - 10, Line.CLOSE_OPEN))
                }
                else -> Unit
            }
            start += seconds
        }
    }

    companion object {
        /** The [i]th of [n] wander-and-return lines: fewer stretches take the lines that matter most. */
        fun wanderLine(kind: Guided, i: Int, n: Int): Line {
            val set = when (kind) {
                Guided.BREATH -> when (n) {
                    2 -> listOf(Line.BREATH_WANDER_1, Line.BREATH_WANDER_3)
                    3 -> listOf(Line.BREATH_WANDER_1, Line.BREATH_WANDER_2, Line.BREATH_WANDER_3)
                    else -> listOf(Line.BREATH_WANDER_1, Line.BREATH_WANDER_2, Line.BREATH_WANDER_3, Line.BREATH_WANDER_4, Line.BREATH_WANDER_5)
                }
                // The body is walked from the feet to the face, in two, three or five stretches.
                Guided.BODY -> when (n) {
                    2 -> listOf(Line.BODY_LOWER, Line.BODY_UPPER)
                    3 -> listOf(Line.BODY_LEGS, Line.BODY_TORSO, Line.BODY_TOP)
                    else -> listOf(Line.BODY_LEGS, Line.BODY_BACK, Line.BODY_CHEST, Line.BODY_ARMS, Line.BODY_FACE)
                }
                // Every Sons session carries the attention cue for the sound in the ears (_2).
                Guided.SOUNDS -> when (n) {
                    2 -> listOf(Line.SOUNDS_WANDER_1, Line.SOUNDS_WANDER_2)
                    3 -> listOf(Line.SOUNDS_WANDER_1, Line.SOUNDS_WANDER_2, Line.SOUNDS_WANDER_3)
                    else -> listOf(Line.SOUNDS_WANDER_1, Line.SOUNDS_WANDER_2, Line.SOUNDS_WANDER_3, Line.SOUNDS_WANDER_4, Line.SOUNDS_WANDER_5)
                }
            }
            return set[i.coerceIn(0, set.lastIndex)]
        }
    }
}
