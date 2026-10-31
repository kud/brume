package app.beansontoast.brume.core

import app.beansontoast.brume.core.sound.Ambience
import app.beansontoast.brume.core.sound.NoiseColour
import app.beansontoast.brume.core.sound.TARGET_RMS
import app.beansontoast.brume.core.sound.ambienceSource
import app.beansontoast.brume.core.sound.noiseSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SourcesTest {
    private fun slope(colour: NoiseColour, low: Double, high: Double) =
        powerDb(noiseSource(colour, RATE, 11), high) - powerDb(noiseSource(colour, RATE, 11), low)

    @Test fun `white is flat`() {
        assertEquals(0.0, slope(NoiseColour.WHITE, 200.0, 3200.0), 1.5)
    }

    @Test fun `pink falls 3 dB an octave`() {
        // 200 Hz to 3200 Hz is four octaves.
        assertEquals(-12.0, slope(NoiseColour.PINK, 200.0, 3200.0), 2.0)
    }

    @Test fun `brown falls 6 dB an octave`() {
        // 200 Hz to 1600 Hz is three octaves.
        assertEquals(-18.0, slope(NoiseColour.BROWN, 200.0, 1600.0), 3.0)
    }

    @Test fun `grey has more low end than pink, and a dip where the ear is keenest`() {
        val pinkLow = slope(NoiseColour.PINK, 1000.0, 80.0)
        val greyLow = slope(NoiseColour.GREY, 1000.0, 80.0)
        assertTrue("grey lows +${greyLow - pinkLow} dB", greyLow - pinkLow > 4)
        val pinkMid = slope(NoiseColour.PINK, 1000.0, 3500.0)
        val greyMid = slope(NoiseColour.GREY, 1000.0, 3500.0)
        assertTrue("grey dip ${greyMid - pinkMid} dB", greyMid - pinkMid < -3)
    }

    @Test fun `every sound plays at about the same loudness`() {
        val all = NoiseColour.entries.map { noiseSource(it, RATE, 5) } + Ambience.entries.map { ambienceSource(it, RATE, 5) }
        all.forEach { assertEquals(0.0, db(rms(it, seconds = 40) / TARGET_RMS), 2.0) }
    }

    /** Rain's drops are spiky by nature; the engine's limiter takes what is left above its knee. */
    @Test fun `sources stay within a bounded crest`() {
        (NoiseColour.entries.map { noiseSource(it, RATE, 9) } + Ambience.entries.map { ambienceSource(it, RATE, 9) }).forEach { s ->
            var peak = 0f
            repeat(RATE * 20) { peak = maxOf(peak, abs(s.next())) }
            assertTrue("peak $peak", peak < 3f)
        }
    }

    @Test fun `the same seed gives the same sound`() {
        Ambience.entries.forEach { a ->
            val x = ambienceSource(a, RATE, 21); val y = ambienceSource(a, RATE, 21)
            repeat(10_000) { assertEquals(x.next(), y.next()) }
        }
    }
}
