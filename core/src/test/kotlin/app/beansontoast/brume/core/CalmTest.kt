package app.beansontoast.brume.core

import org.junit.Test
import app.beansontoast.brume.core.calm.GUIDED_MINUTES
import app.beansontoast.brume.core.calm.Part
import app.beansontoast.brume.core.calm.Ring
import app.beansontoast.brume.core.calm.SIT_MINUTES
import app.beansontoast.brume.core.calm.guided
import app.beansontoast.brume.core.calm.sit

class CalmTest {
    @Test fun `guided sessions last exactly 3, 5 and 10 minutes`() {
        GUIDED_MINUTES.forEach { assertEquals(it * 60, guided(it).totalSeconds) }
        assertFailsWith<IllegalArgumentException> { guided(7) }
    }

    @Test fun `every guided session arrives, anchors, wanders with growing silences, closes`() {
        GUIDED_MINUTES.forEach { m ->
            val t = guided(m)
            assertEquals(Part.ARRIVE, t.parts.first()); assertEquals(Part.ANCHOR, t.parts[1]); assertEquals(Part.CLOSE, t.parts.last())
            val wander = t.steps.filterIndexed { i, _ -> t.parts[i] == Part.WANDER }
            assertTrue(wander.size >= 2, "$m min has room to come back more than once")
            assertTrue(wander.zipWithNext().all { (a, b) -> b > a }, "$m min: silences grow, $wander")
            assertTrue(wander.first() >= 15, "silences start at 15 s or more")
            assertTrue(t.steps.first() in 30..45, "arrive is about 45 s")
        }
    }

    @Test fun `guided bells ring at the start, at each step change and at the end`() {
        val t = guided(3) // 30, 40, 40, 50, 20
        assertEquals(listOf(0 to Ring.START, 30 to Ring.STEP, 70 to Ring.STEP, 110 to Ring.STEP, 160 to Ring.STEP, 180 to Ring.END), t.bells)
    }

    @Test fun `the timer rings only at the start and the end`() {
        assertEquals(listOf(0 to Ring.START, 600 to Ring.END), sit(10).bells)
        SIT_MINUTES.forEach { assertEquals(it * 60, sit(it).totalSeconds) }
    }

    @Test fun `a ticking clock hears every bell once`() {
        val t = guided(5)
        val heard = mutableListOf<Ring>()
        var last = -1.0
        var now = 0.0
        while (now <= t.totalSeconds + 1) { heard += t.bellsBetween(last, now); last = now; now += 0.25 }
        assertEquals(t.bells.map { it.second }, heard)
    }

    @Test fun `the screen is bright at the edges and dim in between`() {
        val t = guided(5)
        assertEquals(1f, t.at(0.0).light(t)); assertEquals(0f, t.at(60.0).light(t)); assertEquals(0f, t.at(200.0).light(t))
        assertTrue(t.at(t.totalSeconds - 1.0).light(t) > 0.8f)
        assertEquals(1f, t.at(t.totalSeconds.toDouble()).light(t))
    }

    @Test fun `places walk the parts`() {
        val t = sit(5)
        assertEquals(Part.SETTLE, t.at(5.0).part); assertEquals(Part.SIT, t.at(100.0).part); assertEquals(Part.RETURN, t.at(290.0).part)
        assertTrue(t.at(300.0).done)
    }
}
