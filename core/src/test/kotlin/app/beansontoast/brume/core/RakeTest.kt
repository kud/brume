package app.beansontoast.brume.core

import app.beansontoast.brume.core.garden.Rake
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class RakeTest {
    @Test fun ringsStartOutsideTheStoneAndNeverTouch() {
        listOf(-1f, -0.3f, 0f, 0.6f, 1f, 4f).forEach { swell ->
            val r = Rake.rings(7, 0.22f, swell)
            assertEquals(7, r.size)
            assertTrue("first ring clear of the stone at $swell", r.first() > 1f)
            r.toList().zipWithNext().forEach { (a, b) -> assertTrue("rings cross at $swell", b - a > 0.22f * 0.5f) }
        }
    }

    @Test fun theSwellWidensAndNarrowsTheGarden() {
        val narrow = Rake.rings(5, 0.2f, -1f).last()
        val rest = Rake.rings(5, 0.2f, 0f).last()
        val wide = Rake.rings(5, 0.2f, 1f).last()
        assertTrue(narrow < rest && rest < wide)
        assertTrue("the zone holds the widest ring", Rake.ringZone(5, 0.2f) > wide)
    }

    @Test fun breathMapsEmptyToNarrowAndFullToWide() {
        assertEquals(-1f, Rake.breathing(0f), 1e-6f)
        assertEquals(1f, Rake.breathing(1f), 1e-6f)
        assertEquals(0f, Rake.breathing(0.5f), 1e-6f)
    }

    @Test fun restAndPlayingNeverJumpBetweenFrames() {
        val frame = 0.05
        var t = 0.0
        var lastRest = Rake.rest(0.0, 1)
        var lastPlay = Rake.playing(0.0, 1f, 1)
        while (t < 600) {
            t += frame
            val r = Rake.rest(t, 1); val p = Rake.playing(t, 1f, 1)
            assertTrue(abs(r - lastRest) < 0.01f && abs(p - lastPlay) < 0.05f)
            assertTrue("rest stays barely moving", abs(r) <= 0.06f + 1e-6f)
            assertTrue("playing stays in range", abs(p) <= 0.56f + 1e-6f)
            lastRest = r; lastPlay = p
        }
    }

    @Test fun silenceIsTheRestingDrift() {
        listOf(0.0, 3.3, 41.0).forEach { assertEquals(Rake.rest(it, 2), Rake.playing(it, 0f, 2), 1e-6f) }
    }

    @Test fun higherPitchRakesCloser() {
        val pitches = listOf(100f, 250f, 500f, 1000f, 4000f, 8000f, 12_000f, 20_000f)
        val spacing = pitches.map { Rake.toneSpacing(it) }
        spacing.zipWithNext().forEach { (a, b) -> assertTrue(b <= a) }
        assertEquals(0.42f, spacing.first(), 1e-5f)
        assertEquals(0.14f, spacing.last(), 1e-5f)
    }

    @Test fun linesFillTheFrameAndDriftWithoutPopping() {
        val a = Rake.lines(100f, 10f, 0f)
        assertEquals(11, a.size)
        assertEquals(0f, a.first(), 1e-6f)
        val b = Rake.lines(100f, 10f, 0.999f)
        assertTrue(b.all { it in 0f..100f })
        assertTrue(Rake.lines(100f, 0f).isEmpty())
    }
}
