package app.beansontoast.brume.core

import app.beansontoast.brume.core.home.LastSound
import app.beansontoast.brume.core.home.Reason
import app.beansontoast.brume.core.home.Suggest
import app.beansontoast.brume.core.home.Suggestion
import app.beansontoast.brume.core.learn.Learn
import app.beansontoast.brume.core.sound.Ambience
import app.beansontoast.brume.core.sound.LevelRules
import app.beansontoast.brume.core.sound.NoiseColour
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestTest {
    private val brownRain = LastSound(NoiseColour.BROWN, Ambience.RAIN, null)

    @Test fun `at night the last sound comes back with a sleep timer`() {
        assertEquals(Suggestion(NoiseColour.BROWN, Ambience.RAIN, 45, Reason.NIGHT_AS_LAST_TIME), Suggest.of(true, brownRain))
        assertEquals(90, Suggest.of(true, brownRain.copy(timerMinutes = 90)).timerMinutes)
    }

    @Test fun `at night with nothing chosen, soft rain`() {
        assertEquals(Suggestion(null, Ambience.RAIN, 45, Reason.NIGHT_SOFT_SOUND), Suggest.of(true, null))
        assertEquals(Reason.NIGHT_SOFT_SOUND, Suggest.of(true, LastSound(null, null, 20)).reason)
        assertEquals(20, Suggest.of(true, LastSound(null, null, 20)).timerMinutes)
    }

    @Test fun `by day the last sound, timer left alone`() {
        assertEquals(Suggestion(NoiseColour.BROWN, Ambience.RAIN, null, Reason.DAY_AS_LAST_TIME), Suggest.of(false, brownRain))
        assertEquals(Suggestion(NoiseColour.PINK, null, null, Reason.DAY_BLEND), Suggest.of(false, null))
    }

    @Test fun `every suggestion has a sound, a real timer and a card`() {
        listOf(true, false).forEach { night ->
            listOf(null, brownRain, LastSound(null, null, 45), LastSound(NoiseColour.WHITE, null, 7)).forEach { last ->
                val s = Suggest.of(night, last)
                assertTrue(s.noise != null || s.ambience != null)
                if (night) assertTrue(s.timerMinutes in LevelRules.SLEEP_MINUTES)
                assertTrue(Learn.card(s.reason.card) != null)
            }
        }
    }
}
