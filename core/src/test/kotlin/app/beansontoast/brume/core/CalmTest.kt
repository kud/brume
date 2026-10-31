package app.beansontoast.brume.core

import app.beansontoast.brume.core.calm.Guided
import app.beansontoast.brume.core.calm.SIT_MINUTES
import app.beansontoast.brume.core.calm.SIT_SOFT_SECONDS
import app.beansontoast.brume.core.calm.Timeline
import app.beansontoast.brume.core.calm.sit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalmTest {
    @Test fun `guided sessions are short and their steps few`() {
        assertEquals(375, Guided.BODY_SCAN.plan.totalSeconds)
        assertEquals(300, Guided.SOUNDS.plan.totalSeconds)
        assertEquals(240, Guided.BREATH.plan.totalSeconds)
        Guided.entries.forEach { g ->
            assertTrue("$g is at most about six minutes", g.plan.totalSeconds <= 6 * 60 + 15)
            assertTrue("$g has few steps", g.steps.size in 4..9)
            // Every step leaves time to read it and then rest: at least half a minute.
            assertTrue("$g", g.steps.all { it >= 30 })
        }
    }

    @Test fun `the step naming a sound inside stays short`() {
        // SOUNDS step 2 names the sound in the ears or head: noticed briefly, then attention widens.
        assertTrue(Guided.SOUNDS.steps[2] <= 30)
        assertTrue(Guided.SOUNDS.steps[2] < Guided.SOUNDS.steps[3])
    }

    @Test fun `a sit is exactly its length, with a soft start and end`() {
        SIT_MINUTES.forEach { m ->
            val t = sit(m)
            assertEquals(m * 60, t.totalSeconds)
            assertEquals(listOf(SIT_SOFT_SECONDS, m * 60 - 2 * SIT_SOFT_SECONDS, SIT_SOFT_SECONDS), t.steps)
        }
    }

    @Test fun `places walk the steps in order and end once`() {
        val t = Timeline(listOf(30, 45, 30))
        assertEquals(0, t.at(-3.0).step)
        assertEquals(0, t.at(29.9).step)
        assertEquals(1, t.at(30.0).step)
        assertEquals(15.0, t.at(45.0).stepElapsed, 1e-9)
        assertEquals(30.0, t.at(45.0).stepLeft, 1e-9)
        assertEquals(60.0, t.at(45.0).totalLeft, 1e-9)
        assertEquals(2, t.at(104.9).step)
        assertFalse(t.at(104.9).done)
        assertTrue(t.at(105.0).done)
        assertTrue(t.at(1e6).done)
        assertEquals(0.0, t.at(105.0).totalLeft, 1e-9)
        // The step index never goes backwards, second by second.
        var last = 0
        for (s in 0..105) { val p = t.at(s.toDouble()); assertTrue(p.step >= last); last = p.step }
    }

    @Test fun `presence rises at the start and settles at the end`() {
        val t = sit(5)
        assertEquals(0f, t.at(0.0).presence(t), 1e-6f)
        assertEquals(0.5f, t.at(10.0).presence(t), 1e-6f)
        assertEquals(1f, t.at(150.0).presence(t), 1e-6f)
        assertEquals(0.5f, t.at(290.0).presence(t), 1e-6f)
        assertEquals(0f, t.at(300.0).presence(t), 1e-6f)
        // Never jumps: a tenth of a second moves it by at most a two-hundredth.
        var prev = t.at(0.0).presence(t)
        var e = 0.1
        while (e <= 300.0) { val p = t.at(e).presence(t); assertTrue(kotlin.math.abs(p - prev) <= 0.0051f); prev = p; e += 0.1 }
    }

    @Test(expected = IllegalArgumentException::class) fun `a sit too short for its soft start and end is refused`() { sit(0) }
}
