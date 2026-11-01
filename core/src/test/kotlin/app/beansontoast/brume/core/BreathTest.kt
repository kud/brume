package app.beansontoast.brume.core

import app.beansontoast.brume.core.breath.BreathPlan
import app.beansontoast.brume.core.breath.Pattern
import app.beansontoast.brume.core.breath.Phase
import kotlin.math.abs
import org.junit.Test

class BreathTest {
    @Test fun `the set is in order and every phase is written out`() {
        assertEquals(listOf(Pattern.LONG_OUT, Pattern.SIGH, Pattern.BOX, Pattern.FOUR_SEVEN_EIGHT), Pattern.entries)
        assertEquals(Pattern.LONG_OUT, Pattern.DEFAULT)
        assertEquals(listOf(Phase.IN to 4, Phase.OUT to 6), Pattern.LONG_OUT.steps.map { it.phase to it.seconds })
        assertEquals(listOf(Phase.IN to 2, Phase.TOP to 1, Phase.OUT to 6), Pattern.SIGH.steps.map { it.phase to it.seconds })
        assertEquals(listOf(Phase.IN to 4, Phase.HOLD to 4, Phase.OUT to 4, Phase.HOLD to 4), Pattern.BOX.steps.map { it.phase to it.seconds })
        assertEquals(listOf(Phase.IN to 4, Phase.HOLD to 7, Phase.OUT to 8), Pattern.FOUR_SEVEN_EIGHT.steps.map { it.phase to it.seconds })
    }

    @Test fun `only box and 4-7-8 hold`() {
        assertFalse(Pattern.LONG_OUT.holds); assertFalse(Pattern.SIGH.holds)
        assertTrue(Pattern.BOX.holds); assertTrue(Pattern.FOUR_SEVEN_EIGHT.holds)
    }

    @Test fun `5-5 and unknown names move to souffle long`() {
        assertEquals(Pattern.LONG_OUT, Pattern.fromStored("EVEN"))
        assertEquals(Pattern.LONG_OUT, Pattern.fromStored(null))
        assertEquals(Pattern.LONG_OUT, Pattern.fromStored("nonsense"))
        Pattern.entries.forEach { assertEquals(it, Pattern.fromStored(it.name)) }
    }

    @Test fun `timed patterns run whole breaths to the chosen length`() {
        assertEquals(120, BreathPlan(Pattern.LONG_OUT, 2).totalSeconds)
        assertEquals(297, BreathPlan(Pattern.SIGH, 5).totalSeconds) // 33 breaths of 9 s
        assertEquals(304, BreathPlan(Pattern.BOX, 5).totalSeconds) // 19 breaths of 16 s
    }

    @Test fun `4-7-8 is capped at four cycles whatever the minutes`() {
        listOf(2, 5, 10).forEach { m ->
            assertEquals(4, BreathPlan(Pattern.FOUR_SEVEN_EIGHT, m).breaths)
            assertEquals(76, BreathPlan(Pattern.FOUR_SEVEN_EIGHT, m).totalSeconds)
        }
    }

    @Test fun `phases follow each other`() {
        val sigh = BreathPlan(Pattern.SIGH, 2)
        assertEquals(Phase.IN, sigh.at(0.5).phase); assertEquals(Phase.TOP, sigh.at(2.5).phase); assertEquals(Phase.OUT, sigh.at(3.5).phase)
        val box = BreathPlan(Pattern.BOX, 2)
        assertEquals(listOf(Phase.IN, Phase.HOLD, Phase.OUT, Phase.HOLD), listOf(1.0, 5.0, 9.0, 13.0).map { box.at(it).phase })
        assertTrue(box.at(16.5).breath == 1 && box.at(16.5).phase == Phase.IN)
    }

    @Test fun `fullness never jumps, rests full or empty while held, and ends empty`() {
        Pattern.entries.forEach { p ->
            val plan = BreathPlan(p, 2)
            var last = plan.at(0.0).fullness
            var t = 0.0
            while (t < plan.totalSeconds) {
                t += 0.01
                val f = plan.at(t).fullness
                assertTrue(abs(f - last) < 0.02f, "$p jumps at $t: $last -> $f")
                assertTrue(f in 0f..1f)
                last = f
            }
            assertEquals(0f, plan.at(plan.totalSeconds.toDouble()).fullness)
        }
        val box = BreathPlan(Pattern.BOX, 2)
        assertEquals(1f, box.at(6.0).fullness); assertEquals(0f, box.at(14.0).fullness)
        val sigh = BreathPlan(Pattern.SIGH, 2)
        assertEquals(0.8f, sigh.at(2.0).fullness, 1e-3f) // the first in-breath stops short; the top-up fills it
        assertEquals(1f, sigh.at(3.0).fullness, 1e-3f)
    }

    @Test fun `the count reads down to one`() {
        val plan = BreathPlan(Pattern.LONG_OUT, 2)
        assertEquals(listOf(4, 3, 2, 1), listOf(0.1, 1.1, 2.1, 3.1).map { plan.at(it).count })
    }
}
