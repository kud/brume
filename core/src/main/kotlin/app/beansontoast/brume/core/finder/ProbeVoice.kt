package app.beansontoast.brume.core.finder

import app.beansontoast.brume.core.dsp.Biquad
import app.beansontoast.brume.core.rng.Pcg32
import app.beansontoast.brume.core.sound.LevelRules
import app.beansontoast.brume.core.sound.TARGET_RMS
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** What the search plays: a pitch, as a pure tone or a narrow band of noise, in one ear or both, at a level. */
data class Probe(val hz: Int, val sound: Character = Character.TONE, val ear: Ear = Ear.BOTH, val level: Float = 0f)

/**
 * The search's own voice, apart from the mixer: nothing else plays while someone listens for their
 * pitch. Phones cannot know absolute loudness without calibrating one headphone, so levels are
 * relative, every start fades in from silence over [FADE_IN_SECONDS], the output never asks for more
 * than [CEILING] of a mixer layer at the same level, and [tooLoud] cuts to silence in 20 ms.
 *
 * Threading: [set], [start], [stop] and [tooLoud] from any thread; [render] on the audio thread.
 */
class ProbeVoice(val rate: Int, seed: Long = 7L) {
    @Volatile private var wanted: Probe? = null
    @Volatile private var cut = false

    private val rng = Pcg32.seeded(seed)
    private val bands = arrayOf(Biquad(), Biquad())
    private var bandHz = 0
    private var phase = 0.0
    private var hz = 0.0
    private var sound = Character.TONE
    /** 0..1: the dip through silence when the sound changes kind. */
    private var blend = 0f
    private var gain = 0f
    private var left = 0f
    private var right = 0f
    private var fadeAt = 0L
    private val fadeFrames = (rate * FADE_IN_SECONDS).toLong()
    private val ramp = (1.0 / (rate * LevelRules.GAIN_RAMP_SECONDS)).toFloat()
    private val glide = (1.0 / (rate * GLIDE_SECONDS))
    private val dip = (1.0 / (rate * DIP_SECONDS)).toFloat()

    /** What to play next; a change of pitch glides, a change of kind dips through silence. */
    fun set(probe: Probe?) { wanted = probe?.copy(hz = PitchLadder.clamp(probe.hz), level = LevelRules.clampLevel(probe.level)) }

    /** Starts again from silence, with the fade-in. */
    fun start() { cut = false; fadeAt = 0L }
    fun stop() { cut = true }
    fun tooLoud() { cut = true }

    val silent: Boolean get() = cut && gain == 0f

    /** Interleaved stereo. */
    fun render(out: FloatArray, frames: Int) {
        val p = wanted
        val target = if (cut || p == null) 0f else LevelRules.gainFor(p.level) * CEILING
        val lTarget = if (p?.ear == Ear.RIGHT) 0f else 1f
        val rTarget = if (p?.ear == Ear.LEFT) 0f else 1f
        for (i in 0 until frames) {
            if (p != null && p.sound != sound) {
                blend -= dip
                if (blend <= 0f) { blend = 0f; sound = p.sound; hz = p.hz.toDouble(); phase = 0.0 }
            } else blend = minOf(1f, blend + dip)
            if (p != null) {
                if (hz == 0.0) hz = p.hz.toDouble()
                hz += (p.hz - hz) * glide
            }
            gain = toward(gain, target); left = toward(left, lTarget); right = toward(right, rTarget)
            if (fadeAt < fadeFrames && !cut) fadeAt++
            val fade = ((1 - cos(PI * fadeAt / fadeFrames)) / 2).toFloat()
            val x = if (gain == 0f || hz == 0.0) 0f else sample() * gain * fade * blend
            out[2 * i] = x * left; out[2 * i + 1] = x * right
        }
    }

    private fun sample(): Float = when (sound) {
        Character.TONE -> {
            phase += 2 * PI * hz / rate
            if (phase >= 2 * PI) phase -= 2 * PI
            (sin(phase) * TONE_AMPLITUDE).toFloat()
        }
        Character.HISS -> {
            // Constant-Q band: its power grows with the centre, so the gain falls as 1/sqrt(centre).
            val centre = hz.toInt().coerceAtLeast(1)
            if (abs10(centre - bandHz)) { bands.forEach { it.bandPass(rate, centre.toDouble(), BAND_Q) }; bandHz = centre }
            val w = rng.nextSigned().toDouble()
            (bands[1].process(bands[0].process(w)) * hissGain(rate, centre)).toFloat()
        }
    }

    private fun abs10(d: Int) = d >= 10 || d <= -10

    private fun toward(c: Float, t: Float) = if (c < t) minOf(t, c + ramp) else maxOf(t, c - ramp)

    companion object {
        const val FADE_IN_SECONDS = 2.0
        /** The search's ceiling against a mixer layer at the same level: about -6 dB. */
        const val CEILING = 0.5f
        const val GLIDE_SECONDS = 0.05
        const val DIP_SECONDS = 0.04
        /** A third of an octave: narrow enough to have a pitch, wide enough to stay a hiss. */
        const val BAND_OCTAVES = 1.0 / 3
        val BAND_Q: Double = 2.0.pow(BAND_OCTAVES / 2) / (2.0.pow(BAND_OCTAVES) - 1)
        val TONE_AMPLITUDE = (TARGET_RMS * sqrt(2.0)).toFloat()

        private const val REF_HZ = 1000
        private val refGains = java.util.concurrent.ConcurrentHashMap<Int, Float>()

        /** The gain that brings the band to [TARGET_RMS]: measured once at 1 kHz, scaled by sqrt(1000 / centre). */
        internal fun hissGain(rate: Int, centre: Int): Float {
            val ref = refGains.getOrPut(rate) {
                val rng = Pcg32.seeded(0x5EEDL)
                val a = Biquad().bandPass(rate, REF_HZ.toDouble(), BAND_Q)
                val b = Biquad().bandPass(rate, REF_HZ.toDouble(), BAND_Q)
                var sum = 0.0
                val n = rate * 4
                repeat(n) { val y = b.process(a.process(rng.nextSigned().toDouble())); sum += y * y }
                (TARGET_RMS / sqrt(sum / n)).toFloat()
            }
            return ref * sqrt(REF_HZ.toDouble() / centre).toFloat()
        }
    }
}
