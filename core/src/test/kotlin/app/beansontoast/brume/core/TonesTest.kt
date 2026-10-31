package app.beansontoast.brume.core

import app.beansontoast.brume.core.sound.NoiseColour
import app.beansontoast.brume.core.sound.SoundEngine
import app.beansontoast.brume.core.sound.SoundEngine.Command
import app.beansontoast.brume.core.sound.TonePattern
import app.beansontoast.brume.core.sound.ToneSequence
import app.beansontoast.brume.core.sound.ToneSettings
import app.beansontoast.brume.core.sound.ToneSource
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs

class TonesTest {
    private val slot = TonePattern.slotFrames(RATE)
    private val cycle = slot * TonePattern.TONES

    @Test fun `the four tones follow the community formula`() {
        assertArrayEquals(intArrayOf(6139, 7202, 8772, 11186), TonePattern.frequencies(8000))
        assertArrayEquals(intArrayOf(3047, 3590, 4412, 5606), TonePattern.frequencies(4000))
        val ratios = TonePattern.frequencies(8000).map { it / 8000.0 }
        listOf(0.767, 0.900, 1.097, 1.398).zip(ratios).forEach { (want, got) -> assertEquals(want, got, 0.003) }
    }

    @Test fun `tones stay ascending and inside the paper's outer bounds`() {
        for (pitch in 2000..TonePattern.PITCH_MAX step 250) {
            val f = TonePattern.frequencies(pitch)
            assertTrue((0 until 3).all { f[it] < f[it + 1] })
            assertTrue("$pitch: ${f.first()}", f.first() / pitch.toDouble() in 0.75..0.80)
            assertTrue("$pitch: ${f.last()}", f.last() / pitch.toDouble() in 1.39..1.41)
            assertTrue(f.last() < 20_000)
        }
        assertArrayEquals(TonePattern.frequencies(TonePattern.PITCH_MAX), TonePattern.frequencies(99_999))
    }

    @Test fun `cycles run at 1_5 Hz in quarter slots`() {
        assertEquals(8000, TonePattern.slotFrames(48_000))
        assertEquals(7350, TonePattern.slotFrames(44_100))
        assertEquals(RATE / TonePattern.CYCLE_HZ, cycle.toDouble(), 0.0)
    }

    @Test fun `three cycles on, two off, every tone exactly once per cycle`() {
        val s = ToneSequence(7)
        repeat(40) { c ->
            val order = s.nextCycle()
            if (c % 5 < 3) assertEquals("cycle $c", listOf(0, 1, 2, 3), order!!.sorted()) else assertNull("cycle $c", order)
        }
    }

    @Test fun `the order is reshuffled and replays from its seed`() {
        fun orders(seed: Long) = ToneSequence(seed).let { s -> List(25) { s.nextCycle()?.toList() } }
        assertEquals(orders(1), orders(1))
        assertTrue(orders(1) != orders(2))
        assertTrue("reshuffled", orders(1).filterNotNull().distinct().size > 5)
        // Golden: a change to the rng or the shuffle changes the sound a stored seed replays.
        assertEquals(GOLDEN, orders(1).take(4))
    }

    private fun render(source: ToneSource, frames: Int) = FloatArray(frames) { source.next() }

    @Test fun `tones sound in the first half of each slot and rest in the off cycles`() {
        val y = render(ToneSource(RATE, 3, ToneSettings(pitch = 4000)), cycle * 5)
        for (c in 0 until 5) for (k in 0 until 4) {
            val start = c * cycle + k * slot
            val on = (start until start + slot / 2).maxOf { abs(y[it]) }
            val off = (start + slot / 2 until start + slot).maxOf { abs(y[it]) }
            if (c < 3) assertTrue("cycle $c slot $k on $on", on > TonePattern.AMPLITUDE * 0.9f) else assertEquals(0f, on)
            assertEquals(0f, off)
            assertEquals(0f, y[start])
        }
    }

    @Test fun `the envelope never clicks, through pitch, trim and mode changes`() {
        val source = ToneSource(RATE, 5, ToneSettings(pitch = TonePattern.PITCH_MAX))
        val y = ArrayList<Float>()
        fun run(frames: Int) = repeat(frames) { y += source.next() }
        run(cycle * 2 + 1234)
        source.configure(ToneSettings(pitch = 3000, trims = listOf(1f, 0.4f, 0.7f, 1f))); run(cycle + 777)
        source.configure(ToneSettings(pitch = 3000, match = true)); run(RATE / 2)
        source.configure(ToneSettings(pitch = 9000, match = true)); run(RATE / 4)
        source.configure(ToneSettings(pitch = TonePattern.PITCH_MAX)); run(cycle * 3)
        val top = TonePattern.frequencies(TonePattern.PITCH_MAX).last()
        val edge = RATE * TonePattern.EDGE_SECONDS
        val bound = TonePattern.AMPLITUDE * (2 * PI * top / RATE + PI / (2 * edge)) * 1.01
        val worst = (1 until y.size).maxOf { abs(y[it] - y[it - 1]) }
        assertTrue("largest step $worst over $bound", worst <= bound)
        assertTrue(y.all { abs(it) <= TonePattern.AMPLITUDE })
    }

    @Test fun `a trim of zero silences that tone and only that tone`() {
        val f = TonePattern.frequencies(4000)
        val y = render(ToneSource(RATE, 3, ToneSettings(pitch = 4000, trims = listOf(1f, 1f, 1f, 0f))), cycle * 3)
        var silent = 0
        for (s in 0 until 12) {
            val part = y.copyOfRange(s * slot, s * slot + slot / 2)
            if (part.all { it == 0f }) silent++
            else assertTrue(dominant(part, f) != f[3])
        }
        assertEquals(3, silent)
    }

    @Test fun `the match tone is a steady tone at the pitch`() {
        val y = render(ToneSource(RATE, 3, ToneSettings(pitch = 4000, match = true)), RATE)
        val crossings = (1 until y.size).count { y[it - 1] < 0f && y[it] >= 0f }
        assertEquals(4000.0, crossings.toDouble(), 2.0)
        assertTrue(y.takeLast(RATE / 2).maxOf { abs(it) } > TonePattern.AMPLITUDE * 0.99f)
    }

    /** Which of [candidates] has the most zero crossings' worth of frequency in [x]. */
    private fun dominant(x: FloatArray, candidates: IntArray): Int {
        val crossings = (1 until x.size).count { x[it - 1] < 0f && x[it] >= 0f }
        val hz = crossings * RATE.toDouble() / x.size
        return candidates.minBy { abs(it - hz) }
    }

    private val block = 480
    private val buffer = FloatArray(block * 2)

    private fun tonesEngine() = SoundEngine(RATE, 3).apply {
        post(Command.Noise(NoiseColour.PINK)); post(Command.NoiseLevel(0.6f))
        post(Command.Tones(ToneSettings(pitch = 4000))); post(Command.ToneLevel(0.6f))
        post(Command.Play(SoundEngine.Mode.TONES))
    }

    private fun SoundEngine.peaks(seconds: Double) = List((seconds * RATE / block).toInt()) { render(buffer, block); buffer.maxOf { abs(it) } }

    @Test fun `the tone mode fades in from silence and plays the tones alone`() {
        val e = tonesEngine(); e.post(Command.Start)
        val peaks = e.peaks(6.0)
        assertTrue("first 10 ms ${peaks[0]}", peaks[0] < 0.001f)
        // Seconds 2 to 3.33 hold a rest; with the noise layer aimed at silence, the output is silence.
        val rest = peaks.subList((2.05 * 100).toInt(), (3.3 * 100).toInt())
        assertTrue("noise under the tones: ${rest.max()}", rest.all { it == 0f })
        assertTrue(peaks.subList(400, 600).max() > 0f)
    }

    @Test fun `the tone mode never gets louder on its own and too loud cuts it`() {
        val e = tonesEngine(); e.post(Command.Start)
        e.peaks(4.6)
        var last = e.effectiveGain()
        repeat(2000) {
            e.render(buffer, block)
            val now = e.effectiveGain()
            assertTrue("gain rose from $last to $now", now <= last + 1e-6f)
            last = now
        }
        e.post(Command.TooLoud); e.peaks(0.02)
        assertEquals(SoundEngine.State.STOPPED, e.state)
        assertTrue(e.peaks(0.5).all { it == 0f })
    }

    @Test fun `switching back to sounds silences the tones`() {
        val e = tonesEngine(); e.post(Command.Start); e.peaks(4.0)
        e.post(Command.Play(SoundEngine.Mode.SOUNDS)); e.peaks(0.05)
        assertEquals(0f, e.tonesGainNow)
    }

    private companion object {
        val GOLDEN: List<List<Int>?> = listOf(listOf(2, 3, 0, 1), listOf(1, 2, 0, 3), listOf(1, 2, 0, 3), null)
    }
}
