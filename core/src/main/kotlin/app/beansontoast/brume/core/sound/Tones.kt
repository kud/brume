package app.beansontoast.brume.core.sound

import app.beansontoast.brume.core.rng.Pcg32
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The tone pattern (ACRN-style): four soft tones placed around one pitch the user sets, played in a
 * shuffled order, three cycles on and two off. Every number here comes from the open-source
 * implementations surveyed in atelier's `knowledge/projects/tinnitus-app/evidence.md`
 * ("Open-source implementations"), not from the paper's own tables.
 */
object TonePattern {
    /** One cycle is the four tones once each, in a fresh order: 1.5 cycles a second (Tass 2012). */
    const val CYCLE_HZ = 1.5
    const val TONES = 4
    const val ON_CYCLES = 3
    const val OFF_CYCLES = 2

    /** A tone sounds for the first half of its quarter-cycle slot, then rests (headphonejames/acrn). */
    const val GATE = 0.5
    /** Raised-cosine edges inside the gate: no step in the signal, so no click. Our choice. */
    const val EDGE_SECONDS = 0.025

    /** The pitch range we let anyone set: keeps the top tone (about 1.4x) under 20 kHz. */
    const val PITCH_MIN = 500
    const val PITCH_MAX = 14_000
    const val DEFAULT_PITCH = 4_000

    /** Tones are a quieter layer than noise: they start low on the same slider. */
    const val DEFAULT_LEVEL = 0.15f

    /** Peak of a single tone before the level: its RMS while sounding equals [TARGET_RMS]. */
    val AMPLITUDE = (TARGET_RMS * sqrt(2.0)).toFloat()

    fun clampPitch(hz: Int): Int = hz.coerceIn(PITCH_MIN, PITCH_MAX)

    /**
     * The four tones for a pitch, lowest first. The community formula from the TinnitusTalk DIY
     * thread, said to be fitted to Desyncra's device and used by headphonejames/acrn,
     * r4ai/tinnitus-reducer-acrn and swaghax/ACRN. At 8 kHz it gives about 0.767, 0.900, 1.097 and
     * 1.398 times the pitch. The outer pair agrees with the 0.766x to 1.4x bounds in Tass 2012; the
     * middle pair is community-derived and NOT verified against the paper.
     */
    fun frequencies(pitch: Int): IntArray {
        val f = clampPitch(pitch).toDouble()
        return intArrayOf(
            floor(0.773 * f - 44.5).toInt(),
            floor(0.903 * f - 21.5).toInt(),
            floor(1.09 * f + 52).toInt(),
            floor(1.395 * f + 26.5).toInt(),
        )
    }

    /** Frames in one tone's slot: rate / 6 is exact at 44.1 and 48 kHz (7350 and 8000). */
    fun slotFrames(rate: Int): Int = (rate / (CYCLE_HZ * TONES)).toInt()
}

/**
 * Which tone plays when, independent of sound: cycle by cycle, an order of the four tone indices for
 * an "on" cycle, or null for a rest. Deterministic for a seed (Brume's own PCG32, never kotlin.random).
 */
class ToneSequence(seed: Long) {
    private val rng = Pcg32.seeded(seed)
    private var cycle = 0
    private val order = IntArray(TonePattern.TONES) { it }

    /** The next cycle's order (shared, overwritten on the next call), or null in a rest cycle. */
    fun nextCycle(): IntArray? {
        val inBlock = cycle++ % (TonePattern.ON_CYCLES + TonePattern.OFF_CYCLES)
        if (inBlock >= TonePattern.ON_CYCLES) return null
        // Fisher-Yates, reshuffled from the previous order every cycle.
        for (i in order.size - 1 downTo 1) {
            val j = (rng.nextFloat() * (i + 1)).toInt().coerceAtMost(i)
            val t = order[i]; order[i] = order[j]; order[j] = t
        }
        return order
    }
}

/** What the tone layer plays: the pattern, or a steady tone at the pitch to check it. */
data class ToneSettings(
    val pitch: Int = TonePattern.DEFAULT_PITCH,
    /** Per-tone trims, 0..1, lowest tone first, so the four sound equally loud. Never above 1. */
    val trims: List<Float> = List(TonePattern.TONES) { 1f },
    val match: Boolean = false,
) {
    init { require(trims.size == TonePattern.TONES) }
}

/**
 * The tone layer as a [Source]. Settings arrive through [configure] on the render thread; a new pitch
 * or trim takes effect at the next tone's start (the pattern) or glides phase-continuously (the match
 * tone), and switching between the two dips through silence over [SWITCH_SECONDS]. Nothing here ever
 * steps the signal, so nothing clicks.
 */
class ToneSource(private val rate: Int, seed: Long, settings: ToneSettings = ToneSettings()) : Source {
    private val sequence = ToneSequence(seed)
    private val slot = TonePattern.slotFrames(rate)
    private val gate = (slot * TonePattern.GATE).toInt()
    private val edge = (rate * TonePattern.EDGE_SECONDS).toInt().coerceIn(1, gate / 2)
    private val switchStep = (1.0 / (rate * SWITCH_SECONDS)).toFloat()

    private var wanted = settings
    private var match = settings.match
    private var blend = 1f

    // Pattern state.
    private var order: IntArray? = null
    private var at = 0
    private var hz = 0.0
    private var trim = 0f

    private var phase = 0.0

    init { startCycle() }

    fun configure(settings: ToneSettings) { wanted = settings }

    override fun next(): Float {
        if (wanted.match != match) {
            blend -= switchStep
            if (blend <= 0f) { blend = 0f; match = wanted.match; at = 0; phase = 0.0; if (!match) startCycle() }
        } else if (blend < 1f) blend = minOf(1f, blend + switchStep)

        val y = if (match) matchSample() else patternSample()
        return y * blend
    }

    private fun matchSample(): Float {
        phase += TWO_PI * TonePattern.clampPitch(wanted.pitch) / rate
        if (phase >= TWO_PI) phase -= TWO_PI
        return (sin(phase) * TonePattern.AMPLITUDE).toFloat()
    }

    private fun patternSample(): Float {
        if (at >= slot * TonePattern.TONES) startCycle()
        val inSlot = at % slot
        if (inSlot == 0) latchTone(at / slot)
        at++
        val o = order ?: return 0f
        if (inSlot >= gate || o.isEmpty()) return 0f
        val env = envelope(inSlot, gate, edge)
        phase += TWO_PI * hz / rate
        if (phase >= TWO_PI) phase -= TWO_PI
        return (sin(phase) * env * trim * TonePattern.AMPLITUDE).toFloat()
    }

    private fun startCycle() {
        at = 0
        order = sequence.nextCycle()
    }

    /** Pitch and trims are read only here, at a tone's silent start. */
    private fun latchTone(index: Int) {
        val o = order ?: return
        val tone = o[index]
        hz = TonePattern.frequencies(wanted.pitch)[tone].toDouble()
        trim = wanted.trims[tone].coerceIn(0f, 1f)
        phase = 0.0
    }

    companion object {
        const val SWITCH_SECONDS = 0.03
        private const val TWO_PI = 2 * PI

        /** Raised-cosine attack and release, flat between: 0 at both ends of the gate. */
        internal fun envelope(i: Int, gate: Int, edge: Int): Double = when {
            i < edge -> (1 - cos(PI * i / edge)) / 2
            i >= gate - edge -> (1 - cos(PI * (gate - i) / edge)) / 2
            else -> 1.0
        }
    }
}
