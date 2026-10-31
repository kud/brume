package app.beansontoast.brume.core

import app.beansontoast.brume.core.breath.BreathPlan
import app.beansontoast.brume.core.breath.Pattern
import app.beansontoast.brume.core.breath.Phase
import app.beansontoast.brume.core.breath.SESSION_MINUTES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class BreathTest {
    @Test fun `patterns have the timings they are named for`() {
        assertEquals(listOf(Phase.IN to 4, Phase.OUT to 6), Pattern.LONG_OUT.steps.map { it.phase to it.seconds })
        assertEquals(listOf(Phase.IN to 5, Phase.OUT to 5), Pattern.EVEN.steps.map { it.phase to it.seconds })
        assertEquals(listOf(Phase.IN to 4, Phase.HOLD to 7, Phase.OUT to 8), Pattern.FOUR_SEVEN_EIGHT.steps.map { it.phase to it.seconds })
        Pattern.entries.forEach { assertEquals(Phase.OUT, it.steps.last().phase) }
        // 5-5 is six breaths a minute.
        assertEquals(6, 60 / Pattern.EVEN.breathSeconds)
    }

    @Test fun `the total matches the chosen length, within half a breath, in whole breaths`() {
        for (pattern in Pattern.entries) for (minutes in SESSION_MINUTES) {
            val plan = BreathPlan(pattern, minutes)
            assertEquals(plan.breaths * pattern.breathSeconds, plan.totalSeconds)
            assertTrue("$pattern $minutes", abs(plan.totalSeconds - minutes * 60) * 2 <= pattern.breathSeconds)
        }
        assertEquals(120, BreathPlan(Pattern.LONG_OUT, 2).totalSeconds)
        assertEquals(600, BreathPlan(Pattern.EVEN, 10).totalSeconds)
        assertEquals(114, BreathPlan(Pattern.FOUR_SEVEN_EIGHT, 2).totalSeconds) // 6 breaths of 19 s
        assertEquals(608, BreathPlan(Pattern.FOUR_SEVEN_EIGHT, 10).totalSeconds) // 32 breaths
    }

    @Test fun `walking the session gives every step of every breath in order, for its full length`() {
        for (pattern in Pattern.entries) for (minutes in SESSION_MINUTES) {
            val plan = BreathPlan(pattern, minutes)
            val seen = mutableListOf<Pair<Int, Int>>()
            var t = 0.0
            while (t < plan.totalSeconds) {
                val m = plan.at(t)
                assertFalse(m.done)
                assertEquals(pattern.steps[m.step].phase, m.phase)
                assertEquals(plan.totalSeconds - t, m.totalLeft, 1e-9)
                if (seen.lastOrNull() != m.breath to m.step) seen += m.breath to m.step
                t += 0.25
            }
            val expected = (0 until plan.breaths).flatMap { b -> pattern.steps.indices.map { b to it } }
            assertEquals(expected, seen)
            assertTrue(plan.at(plan.totalSeconds.toDouble()).done)
        }
    }

    @Test fun `steps change exactly on the second`() {
        val plan = BreathPlan(Pattern.FOUR_SEVEN_EIGHT, 2)
        assertEquals(Phase.IN, plan.at(3.999).phase)
        assertEquals(Phase.HOLD, plan.at(4.0).phase)
        assertEquals(Phase.OUT, plan.at(11.0).phase)
        assertEquals(Phase.IN, plan.at(19.0).phase)
        assertEquals(1, plan.at(19.0).breath)
        assertEquals(4, plan.at(0.0).count)
        assertEquals(1, plan.at(3.5).count)
        assertEquals(Phase.IN, plan.at(-3.0).phase)
    }

    @Test fun `the shape fills and empties without a jump`() {
        for (pattern in Pattern.entries) {
            val plan = BreathPlan(pattern, 2)
            assertEquals(0f, plan.at(0.0).fullness, 1e-6f)
            var prev = plan.at(0.0).fullness
            var t = 0.0
            while (t < plan.totalSeconds) {
                val f = plan.at(t).fullness
                assertTrue(f in 0f..1f)
                assertTrue("$pattern at $t: $prev -> $f", abs(f - prev) < 0.02f)
                prev = f; t += 0.02
            }
            assertEquals(0f, plan.at(plan.totalSeconds.toDouble()).fullness, 1e-6f)
        }
    }
}
