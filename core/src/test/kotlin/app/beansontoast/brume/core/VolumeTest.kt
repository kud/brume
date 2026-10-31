package app.beansontoast.brume.core

import app.beansontoast.brume.core.sound.LevelRules
import app.beansontoast.brume.core.sound.Volume
import org.junit.Assert.assertEquals
import org.junit.Test

class VolumeTest {
    @Test fun `a slide moves every layer by the same step`() {
        assertEquals(listOf(0.4f, 0.55f), Volume.shift(listOf(0.3f, 0.45f), 0.1f).map { round(it) })
        assertEquals(listOf(0.2f, 0.35f), Volume.shift(listOf(0.3f, 0.45f), -0.1f).map { round(it) })
    }

    @Test fun `the mix stops whole at either end, keeping its shape`() {
        assertEquals(listOf(0.85f, 1f), Volume.shift(listOf(0.3f, 0.45f), 2f).map { round(it) })
        assertEquals(listOf(0f, 0.15f), Volume.shift(listOf(0.3f, 0.45f), -2f).map { round(it) })
    }

    @Test fun `no slide, no change, and nothing to move stays nothing`() {
        assertEquals(listOf(0.3f), Volume.shift(listOf(0.3f), 0f))
        assertEquals(emptyList<Float>(), Volume.shift(emptyList(), 0.5f))
    }

    @Test fun `the default level reads soft, and the words cover the whole range`() {
        assertEquals(1, Volume.band(listOf(LevelRules.DEFAULT_LEVEL)))
        assertEquals(0, Volume.band(listOf(0f)))
        assertEquals(4, Volume.band(listOf(1f)))
        assertEquals(4, Volume.band(listOf(0.1f, 0.95f)))
        assertEquals(0, Volume.band(emptyList()))
    }

    private fun round(x: Float) = Math.round(x * 100) / 100f
}
