package app.beansontoast.brume.core.sound

import app.beansontoast.brume.core.dsp.Biquad
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The tone of a noise, from soft and low (-1) to bright and high (+1); 0 is the colour as it always
 * sounded. A tilt around [PIVOT_HZ]: the lows rise as the highs fall, by at most [TILT_DB] each, and
 * the result is loudness-matched, so turning the tone never makes the layer louder.
 */
object Tone {
    const val DEFAULT = 0f
    const val STEP = 0.1f
    const val TILT_DB = 5.0
    const val PIVOT_HZ = 700.0

    fun clamp(tone: Float): Float = if (tone.isNaN()) DEFAULT else tone.coerceIn(-1f, 1f)

    /** Tones are kept on whole steps, so each step is calibrated once. */
    fun snap(tone: Float): Float = (clamp(tone) / STEP).roundToInt() * STEP
}

/** A noise colour through the [Tone] tilt. [setTone] runs on the render thread, at command time only. */
class TonedNoise(private val colour: NoiseColour, private val rate: Int, seed: Long, tone: Float) : Source {
    private val raw = rawNoise(colour, rate, seed)
    private val low = Biquad()
    private val high = Biquad()
    private var gain = 0f
    private var gainNow = -1f
    private val smooth = (1.0 / (rate * 0.05)).toFloat()

    init { setTone(tone) }

    fun setTone(tone: Float) {
        val t = Tone.snap(tone)
        val db = Tone.TILT_DB * t
        low.lowShelf(rate, Tone.PIVOT_HZ, -db)
        high.highShelf(rate, Tone.PIVOT_HZ, db)
        gain = if (t == 0f) calibrate(colour.name, rate, 4) { s -> rawNoise(colour, rate, s) } // as noiseSource
        else calibrate("${colour.name}~$t", rate, 3) { s -> tilted(rawNoise(colour, rate, s), rate, db) }
        if (gainNow < 0f) gainNow = gain
    }

    override fun next(): Float {
        // The loudness match eases over about 50 ms, so a tone step never clicks.
        gainNow += (gain - gainNow) * smooth
        return high.process(low.process(raw.next().toDouble())).toFloat() * gainNow
    }

    private companion object {
        fun tilted(source: Source, rate: Int, db: Double): Source {
            val l = Biquad().lowShelf(rate, Tone.PIVOT_HZ, -db)
            val h = Biquad().highShelf(rate, Tone.PIVOT_HZ, db)
            return Source { h.process(l.process(source.next().toDouble())).toFloat() }
        }
    }
}

/**
 * A decoded recording: mono 16-bit PCM whose last sample runs straight into its first (the seam is
 * baked by tools/sounds/build_loops.py). Decoded once by the app and shared by every layer playing it.
 */
class Loop(val pcm: ShortArray, val rate: Int) {
    init { require(pcm.isNotEmpty() && rate > 0) }

    /** The gain that brings the whole loop to [TARGET_RMS], like every noise. */
    val gain: Float = run {
        var sum = 0.0
        for (s in pcm) { val x = s / 32768.0; sum += x * x }
        val rms = sqrt(sum / pcm.size)
        if (rms > 0) (TARGET_RMS / rms).toFloat() else 0f
    }

    val seconds get() = pcm.size.toDouble() / rate
}

/**
 * Plays a [Loop] forever, gapless: the read position wraps sample by sample. When the output rate is
 * not the loop's, it steps through by linear interpolation. Starts at a place picked from [seed], so
 * two sessions never open on the same bar of rain.
 */
class LoopSource(private val loop: Loop, rate: Int, seed: Long) : Source {
    private val step = loop.rate.toDouble() / rate
    private val size = loop.pcm.size
    private var at = ((seed.toULong() % size.toULong()).toLong()).toDouble()

    override fun next(): Float {
        val i = at.toInt()
        val frac = (at - i).toFloat()
        val a = loop.pcm[i]
        val b = loop.pcm[if (i + 1 == size) 0 else i + 1]
        at += step
        if (at >= size) at -= size
        return (a + (b - a) * frac) / 32768f * loop.gain
    }
}

/** One layer the user chose: its sound and, for a noise, its tone. Its level lives per headphones. */
data class MixLayer(val sound: Sound, val tone: Float = Tone.DEFAULT)

/**
 * The sounds a scene remembers, and its timer. Levels are not here: they stay per output device,
 * on this phone (ADR 0002), and never sync.
 */
data class Mix(val layers: List<MixLayer>, val timer: Int? = null) {
    val sounds get() = layers.map { it.sound }

    fun encode(): String = "2|${timer ?: NONE}|" + layers.joinToString(",") { "${it.sound.key}@${Tone.snap(it.tone)}" }

    companion object {
        private const val NONE = "-"

        /** Reads v2 ("2|45|noise:BROWN@0.0,nature:RAIN@0.0") and the two-slot form before it ("BROWN|SURF|45"). */
        fun decode(text: String?): Mix? {
            val p = text?.split('|') ?: return null
            if (p.size == 3 && p[0] == "2") {
                val layers = p[2].split(',').filter { it.isNotEmpty() }.mapNotNull { part ->
                    val (key, tone) = part.split('@', limit = 2).let { it[0] to it.getOrNull(1) }
                    Sound.parse(key)?.let { MixLayer(it, Tone.snap(tone?.toFloatOrNull() ?: Tone.DEFAULT)) }
                }.distinctBy { it.sound }
                return Mix(layers, p[1].toIntOrNull())
            }
            if (p.size == 3) {
                val noise = NoiseColour.entries.firstOrNull { it.name == p[0] }?.let { Sound.Noise(it) }
                val nature = Ambience.stored(p[1])?.let { Sound.Recorded(it) }
                return Mix(listOfNotNull(noise, nature).map { MixLayer(it) }, p[2].toIntOrNull())
            }
            return null
        }
    }
}
