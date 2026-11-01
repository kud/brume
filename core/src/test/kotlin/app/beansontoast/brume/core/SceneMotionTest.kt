package app.beansontoast.brume.core

import app.beansontoast.brume.core.breath.Pattern
import app.beansontoast.brume.core.scene.MiddleMotion
import app.beansontoast.brume.core.scene.SceneMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SceneMotionTest {
    private val night = (0..8 * 3600 * 20).map { it / 20.0 } // eight hours at the app's frame rate

    @Test fun `the drift stays within Sacha's reach and never jumps`() {
        assertTrue("inside the 162 px overhang", SceneMotion.DRIFT_PX <= 80f)
        (0..3).forEach { seed ->
            var last = SceneMotion.drift(0.0, seed)
            night.forEach { t ->
                val d = SceneMotion.drift(t, seed)
                assertTrue("seed $seed at $t: $d", d in -1f..1f)
                // Under a source pixel a frame: no seam, no restart.
                assertTrue("seed $seed jumps at $t", abs(d - last) * SceneMotion.DRIFT_PX < 1f)
                last = d
            }
        }
    }

    @Test fun `the drift does not repeat on a short loop`() {
        // Compare the path with itself shifted by every lag from 20 s to 5 min: a mechanical loop
        // would come back on itself at its period.
        val probe = (0 until 600).map { it * 0.5 }
        for (lag in 20..300) {
            val apart = probe.maxOf { abs(SceneMotion.drift(it, 0) - SceneMotion.drift(it + lag, 0)) }
            assertTrue("drift repeats after $lag s", apart > 0.15f)
        }
    }

    @Test fun `neighbouring scenes drift out of step`() {
        val apart = night.take(20 * 120).maxOf { abs(SceneMotion.drift(it, 0) - SceneMotion.drift(it, 1)) }
        assertTrue(apart > 0.5f)
    }

    @Test fun `the resting breath stays between empty and full`() {
        night.take(20 * 600).forEach { t -> assertTrue(SceneMotion.breath(t, 2) in 0f..1f) }
        val values = night.take(20 * 60).map { SceneMotion.breath(it, 2) }
        assertTrue("it moves", values.max() - values.min() > 0.5f)
    }

    @Test fun `Breathe follows the chosen rhythm`() {
        // Souffle long: in for 4, out for 6.
        assertEquals(0f, SceneMotion.breath(0.0, Pattern.LONG_OUT), 0.01f)
        assertEquals(1f, SceneMotion.breath(4.0, Pattern.LONG_OUT), 0.01f)
        assertEquals(0f, SceneMotion.breath(10.0, Pattern.LONG_OUT), 0.01f)
        assertEquals(1f, SceneMotion.breath(24.0, Pattern.LONG_OUT), 0.01f) // the third breath, at its top
        // Carré holds: full from 4 s to 8 s.
        assertEquals(1f, SceneMotion.breath(6.0, Pattern.BOX), 0.01f)
        assertEquals(0f, SceneMotion.breath(14.0, Pattern.BOX), 0.01f)
        // 4-7-8 runs by count, yet the preview keeps breathing past its fourth breath.
        assertEquals(1f, SceneMotion.breath(19.0 * 9 + 6.0, Pattern.FOUR_SEVEN_EIGHT), 0.01f)
    }

    @Test fun `the middle layer moves only its light, and Sleep and Settle barely`() {
        val minute = night.take(20 * 120)
        fun range(m: MiddleMotion, p: Pattern? = null) = minute.map { SceneMotion.middleAlpha(it, m, 1, p) }.let { it.min() to it.max() }
        val still = range(MiddleMotion.STILL)
        assertTrue("$still", still.first >= 0.94f && still.second <= 1f)
        val breath = range(MiddleMotion.BREATH, Pattern.LONG_OUT)
        assertEquals(0.55f, breath.first, 0.01f)
        assertEquals(1f, breath.second, 0.01f)
        val tones = range(MiddleMotion.DISSOLVE)
        assertTrue("$tones", tones.first >= 0.6f && tones.second <= 1f && tones.second - tones.first > 0.3f)
    }
}
