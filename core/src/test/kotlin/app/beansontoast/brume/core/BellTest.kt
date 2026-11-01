package app.beansontoast.brume.core

import app.beansontoast.brume.core.sound.Bell
import app.beansontoast.brume.core.sound.LevelRules
import kotlin.math.abs
import org.junit.Test

class BellTest {
    private val rate = 48_000

    @Test fun `the peak is the gain, never more`() {
        val ring = Bell.render(rate, 0.3f)
        assertEquals(0.3f, ring.maxOf { abs(it) }, 1e-4f)
    }

    @Test fun `it swells in with no strike and ends in silence`() {
        val ring = Bell.render(rate, 1f)
        val first5ms = ring.take(rate / 200).maxOf { abs(it) }
        assertTrue(first5ms < 0.05f, "no sharp attack: $first5ms in the first 5 ms")
        assertEquals(0f, ring.first()); assertTrue(abs(ring.last()) < 1e-6f)
        // The swell rises window by window through the attack (0.12 s), 10 ms at a time.
        val win = rate / 100
        val peaks = (0 until 8).map { w -> ring.slice(w * win until (w + 1) * win).maxOf { abs(it) } }
        assertTrue(peaks.zipWithNext().all { (a, b) -> b >= a * 0.98f }, "$peaks")
    }

    @Test fun `a bell is never louder than the session`() {
        listOf(0f, 0.2f, LevelRules.DEFAULT_LEVEL, 0.9f, 1f).forEach { level ->
            val session = LevelRules.gainFor(LevelRules.clampLevel(level))
            listOf(0f, 0.6f, 1f, 3f).forEach { s -> assertTrue(Bell.gain(level, s) <= session + 1e-6f) }
        }
    }
}
