package app.beansontoast.brume.core.sound

import app.beansontoast.brume.core.dsp.Biquad
import app.beansontoast.brume.core.rng.Pcg32
import kotlin.math.sqrt

/** A mono generator. Never allocates in [next]: it runs on the render thread. */
fun interface Source {
    fun next(): Float
}

enum class NoiseColour { WHITE, PINK, BROWN, GREY }

/**
 * Recorded ambiences (Erwann, 2026-11-01: « j'ai pas envie de prétendre un wind ou la pluie »): CC0
 * field recordings, made into seamless loops by tools/sounds/build_loops.py and credited in
 * SOUNDS.md. [file] is the raw resource's name. RAIN and WIND keep their names from the synthesised
 * ones they replace, so a stored choice carries over; SURF became [SEA] ([stored]).
 */
enum class Ambience(val file: String) {
    RAIN("sound_rain"), RAIN_TERRACE("sound_rain_terrace"), STORM("sound_storm"), WIND("sound_wind"),
    SEA("sound_sea"), STREAM("sound_stream"), BIRDS("sound_birds"), NIGHT("sound_night"),
    FIRE("sound_fire"), CAFE("sound_cafe"), TRAIN("sound_train");

    companion object {
        /** A name stored by any version: the synthesised surf is now the recorded sea. */
        fun stored(name: String?): Ambience? = when (name) {
            "SURF" -> SEA
            else -> entries.firstOrNull { it.name == name }
        }
    }
}

/** One sound in the mix: a synthesised noise colour or a recorded ambience. Each plays at most once. */
sealed interface Sound {
    val key: String

    data class Noise(val colour: NoiseColour) : Sound {
        override val key get() = "noise:${colour.name}"
    }

    data class Recorded(val ambience: Ambience) : Sound {
        override val key get() = "nature:${ambience.name}"
    }

    companion object {
        val all: List<Sound> = NoiseColour.entries.map { Noise(it) } + Ambience.entries.map { Recorded(it) }

        fun parse(key: String?): Sound? {
            val (kind, name) = key?.split(':', limit = 2)?.takeIf { it.size == 2 } ?: return null
            return when (kind) {
                "noise" -> NoiseColour.entries.firstOrNull { it.name == name }?.let { Noise(it) }
                "nature" -> Ambience.stored(name)?.let { Recorded(it) }
                else -> null
            }
        }
    }
}

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
