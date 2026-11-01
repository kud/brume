package app.beansontoast.brume.core

import app.beansontoast.brume.core.sound.Balance
import app.beansontoast.brume.core.sound.LevelRules
import app.beansontoast.brume.core.sound.SoundEngine
import app.beansontoast.brume.core.sound.SoundEngine.Command
import app.beansontoast.brume.core.sound.ToneSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class BalanceTest {
    private val block = 480

    private fun engine(mode: SoundEngine.Mode = SoundEngine.Mode.SOUNDS) = SoundEngine(RATE, 7).apply {
        post(Command.Add(PINK, 0.8f)); post(Command.Add(RAIN, 0.8f, loop = testLoop()))
        post(Command.Tones(ToneSettings())); post(Command.ToneLevel(0.8f)); post(Command.Play(mode))
        post(Command.Start)
    }

    /** Renders [blocks] blocks, calling [before] with the block index so a test can post mid-stream. */
    private fun SoundEngine.frames(blocks: Int, before: (Int) -> Unit = {}): FloatArray {
        val all = FloatArray(blocks * block * 2)
        val buffer = FloatArray(block * 2)
        repeat(blocks) { b -> before(b); render(buffer, block); buffer.copyInto(all, b * block * 2) }
        return all
    }

    @Test fun `the law only attenuates`() {
        var b = -1f
        while (b <= 1f) {
            assertTrue(Balance.left(b) in 0f..1f && Balance.right(b) in 0f..1f)
            assertTrue("one side stays whole at $b", Balance.left(b) == 1f || Balance.right(b) == 1f)
            b += 0.05f
        }
        assertEquals(1f, Balance.left(0f)); assertEquals(1f, Balance.right(0f))
        assertEquals(0f, Balance.right(-1f)); assertEquals(0f, Balance.left(1f))
        assertEquals(0.7f, Balance.right(-0.3f), 1e-6f)
    }

    @Test fun `snap lands on tenths and on the centre`() {
        assertEquals(0f, Balance.snap(0.04f)); assertEquals(0f, Balance.snap(-0.049f))
        assertEquals(-0.3f, Balance.snap(-0.27f), 1e-6f)
        assertEquals(30, Balance.percent(-0.3f)); assertEquals(100, Balance.percent(1f)); assertEquals(0, Balance.percent(Float.NaN))
    }

    @Test fun `centre is identity`() {
        val plain = engine().frames(200)
        val centred = engine().apply { post(Command.Balance(0f)) }.frames(200)
        assertTrue(plain.contentEquals(centred))
        for (i in plain.indices step 2) assertEquals(plain[i], plain[i + 1])
    }

    @Test fun `full left silences the right channel`() {
        for (mode in SoundEngine.Mode.entries) {
            val out = engine(mode).apply { post(Command.Balance(-1f)) }.frames(400)
            // From the very first frame the right ramp starts at 1 and falls; past 20 ms it is silence.
            val settled = (RATE * LevelRules.GAIN_RAMP_SECONDS).toInt() + 1
            var leftPeak = 0f
            for (f in settled until out.size / 2) {
                assertEquals("$mode right at $f", 0f, abs(out[2 * f + 1]))
                leftPeak = maxOf(leftPeak, abs(out[2 * f]))
            }
            assertTrue("$mode left still plays: $leftPeak", leftPeak > 0.01f)
        }
    }

    @Test fun `no channel ever exceeds its centre level`() {
        val moves = listOf(-1f, 0.3f, 1f, -0.6f, 0f, 0.9f, -0.2f)
        for (mode in SoundEngine.Mode.entries) {
            val centre = engine(mode).frames(420)
            val moving = engine(mode).let { e -> e.frames(420) { b -> if (b % 60 == 0) e.post(Command.Balance(moves[b / 60])) } }
            for (i in centre.indices) assertTrue("$mode sample $i", abs(moving[i]) <= abs(centre[i]))
        }
    }

    @Test fun `the ramp stays bounded and never clicks`() {
        val e = engine()
        val step = 1f / (RATE * LevelRules.GAIN_RAMP_SECONDS).toFloat()
        var left = e.leftGainNow; var right = e.rightGainNow
        val one = FloatArray(2)
        repeat(RATE / 2) { f ->
            if (f == 1000) e.post(Command.Balance(-1f))
            if (f == 3000) e.post(Command.Balance(1f))
            if (f == 3200) e.post(Command.Balance(Float.NaN))
            e.render(one, 1)
            val l = e.leftGainNow; val r = e.rightGainNow
            assertTrue(l in 0f..1f && r in 0f..1f)
            assertTrue("left jumps at $f", abs(l - left) <= step * 1.001f)
            assertTrue("right jumps at $f", abs(r - right) <= step * 1.001f)
            left = l; right = r
        }
        assertEquals(1f, left); assertEquals(1f, right) // NaN reads as centre
    }

    @Test fun `too loud still cuts both channels`() {
        for (b in listOf(-0.5f, 0f, 0.7f)) {
            val e = engine().apply { post(Command.Balance(b)) }
            e.frames(400)
            e.post(Command.TooLoud)
            val after = e.frames(10)
            val cut = 2 * ((RATE * LevelRules.GAIN_RAMP_SECONDS).toInt() + 2)
            for (i in cut until after.size) assertEquals("balance $b sample $i", 0f, abs(after[i]))
            assertEquals(SoundEngine.StopReason.TOO_LOUD, e.stopReason)
        }
    }
}
