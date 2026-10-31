package app.beansontoast.brume.core

import app.beansontoast.brume.core.sound.LevelRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelRulesTest {
    @Test fun `zero is silence and the top stays 6 dB under full scale`() {
        assertEquals(0f, LevelRules.gainFor(0f))
        assertEquals(-6.0, db(LevelRules.gainFor(1f).toDouble()), 0.01)
        assertEquals(-6.0, db(LevelRules.gainFor(5f).toDouble()), 0.01)
    }

    @Test fun `the slider is even in dB`() {
        val a = db(LevelRules.gainFor(0.4f).toDouble()) - db(LevelRules.gainFor(0.3f).toDouble())
        val b = db(LevelRules.gainFor(0.9f).toDouble()) - db(LevelRules.gainFor(0.8f).toDouble())
        assertEquals(a, b, 0.01)
    }

    @Test fun `starts quiet`() {
        assertTrue(db(LevelRules.gainFor(LevelRules.DEFAULT_LEVEL).toDouble()) < -30)
    }

    @Test fun `too loud lowers the next start and never goes below silence`() {
        assertEquals(0.15f, LevelRules.afterTooLoud(0.3f), 1e-6f)
        assertEquals(0f, LevelRules.afterTooLoud(0.1f))
    }

    @Test fun `resume is never louder than last time`() {
        assertEquals(0.3f, LevelRules.resumeLevel(last = 0.3f, requested = 0.8f))
        assertEquals(0.2f, LevelRules.resumeLevel(last = 0.3f, requested = 0.2f))
    }
}
