package app.beansontoast.brume.core.rng

/**
 * Brume's own generators, never kotlin.random or java.util.Random: their algorithms are not
 * promised stable, and a stored seed must replay the same sound years later. Golden vectors pin both.
 */
class SplitMix64(private var state: Long) {
    fun next(): Long {
        state += GOLDEN
        var z = state
        z = (z xor (z ushr 30)) * MIX1
        z = (z xor (z ushr 27)) * MIX2
        return z xor (z ushr 31)
    }

    private companion object {
        val GOLDEN = 0x9E3779B97F4A7C15uL.toLong()
        val MIX1 = 0xBF58476D1CE4E5B9uL.toLong()
        val MIX2 = 0x94D049BB133111EBuL.toLong()
    }
}

/** PCG32 (pcg32_srandom / pcg32_random from the reference implementation). */
class Pcg32(initState: Long, initSequence: Long) {
    private var state = 0L
    private val increment = (initSequence shl 1) or 1L

    init {
        nextInt()
        state += initState
        nextInt()
    }

    fun nextInt(): Int {
        val old = state
        state = old * MULTIPLIER + increment
        val xorShifted = (((old ushr 18) xor old) ushr 27).toInt()
        val rot = (old ushr 59).toInt()
        return (xorShifted ushr rot) or (xorShifted shl ((-rot) and 31))
    }

    /** Uniform in [0, 1). */
    fun nextFloat(): Float = (nextInt() ushr 8) * (1f / (1 shl 24))

    /** Uniform in [-1, 1). */
    fun nextSigned(): Float = nextFloat() * 2f - 1f

    companion object {
        private const val MULTIPLIER = 6364136223846793005L

        /** One seed, expanded the documented way: SplitMix64 gives the state and the stream. */
        fun seeded(seed: Long): Pcg32 {
            val mix = SplitMix64(seed)
            return Pcg32(mix.next(), mix.next())
        }
    }
}
