package app.beansontoast.brume.core

import app.beansontoast.brume.core.sound.LevelRules
import app.beansontoast.brume.core.sound.SoundEngine
import app.beansontoast.brume.core.sound.SoundEngine.Command
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SoundEngineTest {
    private val block = 480
    private val buffer = FloatArray(block * 2)

    private fun engine(level: Float = 0.6f) = SoundEngine(RATE, 3).apply {
        post(Command.Add(PINK, level)); post(Command.Add(RAIN, level, loop = testLoop()))
    }

    /** Renders [seconds] and returns the peak per block. */
    private fun SoundEngine.run(seconds: Double, each: (FloatArray) -> Unit = {}): List<Float> {
        val blocks = (seconds * RATE / block).toInt()
        return List(blocks) { render(buffer, block); each(buffer); buffer.maxOf { abs(it) } }
    }

    @Test fun `silent until started`() {
        val e = engine()
        assertTrue(e.run(1.0).all { it == 0f })
        assertEquals(0L, e.playedFrames)
    }

    @Test fun `every start fades in from silence over three seconds`() {
        val e = engine(); e.post(Command.Start)
        val peaks = e.run(4.0)
        assertTrue("first 10 ms peak ${peaks[0]}", peaks[0] < 0.001f)
        val oneSecond = peaks.subList(90, 110).max()
        val settled = peaks.subList(340, 400).max()
        assertTrue("still rising at 1 s: $oneSecond vs $settled", oneSecond < settled * 0.6f)
    }

    @Test fun `never louder on its own`() {
        val e = engine(); e.post(Command.Start)
        e.run(LevelRules.FADE_IN_SECONDS + 0.1)
        var last = e.effectiveGain()
        repeat(6000) {
            e.render(buffer, block)
            val now = e.effectiveGain()
            assertTrue("gain rose from $last to $now", now <= last + 1e-6f)
            last = now
        }
    }

    @Test fun `too loud cuts to silence within 20 ms`() {
        val e = engine(); e.post(Command.Start); e.run(4.0)
        e.post(Command.TooLoud)
        e.run(0.02)
        assertEquals(SoundEngine.State.STOPPED, e.state)
        assertEquals(SoundEngine.StopReason.TOO_LOUD, e.stopReason)
        assertTrue(e.run(0.5).all { it == 0f })
    }

    @Test fun `the sleep timer stops on the exact sample`() {
        val e = engine(); e.post(Command.Start); e.post(Command.Timer(20))
        val total = 20L * 60 * RATE
        e.render(buffer, block)
        while (e.state == SoundEngine.State.PLAYING) e.render(buffer, block)
        assertEquals(total, e.playedFrames)
        assertEquals(SoundEngine.StopReason.TIMER, e.stopReason)
        assertNull(e.timerRemainingFrames)
    }

    @Test fun `the sleep timer fades over its last five minutes`() {
        val e = engine(); e.post(Command.Start); e.post(Command.Timer(20))
        e.run(14.0 * 60)
        val before = e.run(10.0).max()
        e.run(340.0)
        val near = e.run(5.0).max()
        assertTrue("before fade $before, near the end $near", near < before * 0.1f)
    }

    @Test fun `no clicks at start, level change or pause`() {
        val e = engine(); e.post(Command.Start)
        var prev = 0f; var worst = 0f
        val track: (FloatArray) -> Unit = { b -> for (i in b.indices step 2) { worst = maxOf(worst, abs(b[i] - prev)); prev = b[i] } }
        e.run(4.0, track)
        val bed = worst; worst = 0f
        e.post(Command.Level(PINK, 0.1f)); e.run(1.0, track)
        e.post(Command.Pause); e.run(1.0, track)
        assertTrue("step $worst against the sound's own $bed", worst <= bed * 1.5f)
    }

    @Test fun `the limiter keeps both layers at full under full scale`() {
        val e = engine(level = 1f); e.post(Command.Start)
        assertTrue(e.run(10.0).all { it < 0.96f })
    }

    @Test fun `pause keeps the layers and start resumes them`() {
        val e = engine(); e.post(Command.Start); e.run(4.0)
        e.post(Command.Pause); e.run(0.5)
        assertEquals(SoundEngine.StopReason.PAUSED, e.stopReason)
        e.post(Command.Start)
        assertTrue(e.run(4.0).last() > 0f)
    }

    @Test fun `limit is continuous and bounded`() {
        assertEquals(0.5f, SoundEngine.limit(0.5f), 1e-6f)
        assertEquals(-0.3f, SoundEngine.limit(-0.3f), 1e-6f)
        assertTrue(SoundEngine.limit(100f) < 0.951f)
        assertTrue(SoundEngine.limit(0.51f) - 0.51f < 1e-3f)
    }
}
