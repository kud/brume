package app.beansontoast.brume.core.sound

import app.beansontoast.brume.core.dsp.Biquad
import app.beansontoast.brume.core.rng.Pcg32
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/** A mono generator. Never allocates in [next]: it runs on the render thread. */
fun interface Source {
    fun next(): Float
}

enum class NoiseColour { WHITE, PINK, BROWN, GREY }

/** Generated, not recorded: no assets, no licences, never a loop seam. Birds wait for CC0 recordings. */
enum class Ambience { RAIN, SURF, WIND }

/** Every source plays at about this RMS, so changing sound never jumps in loudness. */
const val TARGET_RMS = 0.2f

private val calibrations = java.util.concurrent.ConcurrentHashMap<String, Float>()

/**
 * Runs a twin of the source (same recipe, fixed seed) and returns the gain that brings it to
 * [TARGET_RMS]. Long enough to hold several waves or gusts; deterministic, and computed once per
 * sound per process, never on the per-sample path.
 */
internal fun calibrate(key: String, rate: Int, seconds: Int, make: (Long) -> Source): Float =
    calibrations.getOrPut("$key@$rate") {
        val twin = make(CALIBRATION_SEED)
        var sum = 0.0
        val n = rate * seconds
        repeat(n) { val x = twin.next(); sum += x * x }
        val rms = sqrt(sum / n)
        if (rms > 0) (TARGET_RMS / rms).toFloat() else 0f
    }

private const val CALIBRATION_SEED = 0x5EEDL

fun noiseSource(colour: NoiseColour, rate: Int, seed: Long): Source {
    val make: (Long) -> Source = { s -> rawNoise(colour, rate, s) }
    val gain = calibrate(colour.name, rate, 4, make)
    val raw = make(seed)
    return Source { raw.next() * gain }
}

fun ambienceSource(ambience: Ambience, rate: Int, seed: Long): Source {
    val make: (Long) -> Source = { s ->
        when (ambience) {
            Ambience.RAIN -> rain(rate, s)
            Ambience.SURF -> surf(rate, s)
            Ambience.WIND -> wind(rate, s)
        }
    }
    val gain = calibrate(ambience.name, rate, 60, make)
    val raw = make(seed)
    return Source { raw.next() * gain }
}

internal fun rawNoise(colour: NoiseColour, rate: Int, seed: Long): Source {
    val rng = Pcg32.seeded(seed)
    return when (colour) {
        NoiseColour.WHITE -> Source { rng.nextSigned() }
        NoiseColour.PINK -> pink(rng)
        NoiseColour.BROWN -> brown(rng, rate)
        NoiseColour.GREY -> {
            // Generic grey: pink shaped by a rough inverse of a mid-level equal-loudness contour
            // (more low end, a dip where the ear is most sensitive, a little air). Not ISO 226 data,
            // and not his own hearing: v0.2's threshold curve replaces it.
            val p = pink(rng)
            val low = Biquad().lowShelf(rate, 150.0, 9.0)
            val dip = Biquad().peaking(rate, 3500.0, -6.0, 1.0)
            val air = Biquad().highShelf(rate, 9000.0, 4.0)
            Source { air.process(dip.process(low.process(p.next().toDouble()))).toFloat() }
        }
    }
}

/** Paul Kellet's refined pink filter: within about 0.5 dB of -3 dB/octave across the audible band. */
private fun pink(rng: Pcg32): Source {
    var b0 = 0.0; var b1 = 0.0; var b2 = 0.0; var b3 = 0.0; var b4 = 0.0; var b5 = 0.0; var b6 = 0.0
    return Source {
        val w = rng.nextSigned().toDouble()
        b0 = 0.99886 * b0 + w * 0.0555179
        b1 = 0.99332 * b1 + w * 0.0750759
        b2 = 0.96900 * b2 + w * 0.1538520
        b3 = 0.86650 * b3 + w * 0.3104856
        b4 = 0.55000 * b4 + w * 0.5329522
        b5 = -0.7616 * b5 - w * 0.0168980
        val out = b0 + b1 + b2 + b3 + b4 + b5 + b6 + w * 0.5362
        b6 = w * 0.115926
        out.toFloat()
    }
}

/** A leaky integrator of white noise (-6 dB/octave), high-passed at 20 Hz so it never drifts. */
private fun brown(rng: Pcg32, rate: Int): Source {
    var level = 0.0
    val dc = Biquad().highPass(rate, 20.0)
    return Source {
        level = 0.998 * level + 0.05 * rng.nextSigned()
        dc.process(level).toFloat()
    }
}

/** A slow random wander in [lo, hi], a new target every [seconds], eased with a raised cosine. */
private class Drift(private val rng: Pcg32, rate: Int, seconds: Double, private val lo: Double, private val hi: Double) {
    private val span = (rate * seconds).toInt().coerceAtLeast(1)
    private var from = lo + (hi - lo) * rng.nextFloat()
    private var to = lo + (hi - lo) * rng.nextFloat()
    private var at = 0

    fun next(): Double {
        if (at >= span) { at = 0; from = to; to = lo + (hi - lo) * rng.nextFloat() }
        val t = at++.toDouble() / span
        return from + (to - from) * (1 - cos(PI * t)) / 2
    }
}

/** Rain: a soft high hiss that breathes, with drops, each a few milliseconds of bright noise. */
private fun rain(rate: Int, seed: Long): Source {
    val rng = Pcg32.seeded(seed)
    val hiss = pink(rng)
    val hissHp = Biquad().highPass(rate, 400.0)
    val hissLp = Biquad().lowPass(rate, 9000.0)
    val dropHp = Biquad().highPass(rate, 1500.0)
    val swell = Drift(rng, rate, 6.0, 0.6, 1.0)
    val dropDecay = Math.exp(-1.0 / (0.004 * rate))
    val dropChance = 60.0 / rate
    var drop = 0.0
    return Source {
        if (rng.nextFloat() < dropChance) drop += 0.4 + 0.6 * rng.nextFloat()
        drop *= dropDecay
        val bed = hissLp.process(hissHp.process(hiss.next().toDouble())) * swell.next()
        val drops = dropHp.process(rng.nextSigned().toDouble()) * drop
        (bed + drops).toFloat()
    }
}

/** Surf: deep noise that rises and falls in waves of 7 to 12 seconds, with foam on each crest. */
private fun surf(rate: Int, seed: Long): Source {
    val rng = Pcg32.seeded(seed)
    val body = brown(rng, rate)
    val bodyLp = Biquad().lowPass(rate, 1200.0)
    val foamHp = Biquad().highPass(rate, 2500.0)
    var waveLength = 0
    var at = 0
    return Source {
        if (at >= waveLength) { at = 0; waveLength = (rate * (7.0 + 5.0 * rng.nextFloat())).toInt() }
        val t = at++.toDouble() / waveLength
        // Rises over the first 40%, falls over the rest, never quite to nothing.
        val shape = if (t < 0.4) (1 - cos(PI * t / 0.4)) / 2 else (1 + cos(PI * (t - 0.4) / 0.6)) / 2
        val envelope = 0.25 + 0.75 * shape
        val foam = foamHp.process(rng.nextSigned().toDouble()) * shape * shape * 0.3
        (bodyLp.process(body.next().toDouble()) * envelope + foam).toFloat()
    }
}

/** Wind: noise through a band that wanders between 250 and 900 Hz, in gusts. */
private fun wind(rate: Int, seed: Long): Source {
    val rng = Pcg32.seeded(seed)
    val band = Biquad()
    val centre = Drift(rng, rate, 5.0, 250.0, 900.0)
    val gust = Drift(rng, rate, 3.0, 0.3, 1.0)
    var hz = 500.0
    var count = 0
    band.bandPass(rate, hz, 0.8)
    return Source {
        hz = centre.next()
        if (count++ % 64 == 0) band.bandPass(rate, hz, 0.8)
        (band.process(rng.nextSigned().toDouble()) * gust.next()).toFloat()
    }
}
