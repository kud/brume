package app.beansontoast.brume.core.home

import app.beansontoast.brume.core.sound.Ambience
import app.beansontoast.brume.core.sound.LevelRules
import app.beansontoast.brume.core.sound.NoiseColour

/** The comfort sound chosen last, as the player keeps it: either layer may be empty. */
data class LastSound(val noise: NoiseColour?, val ambience: Ambience?, val timerMinutes: Int?) {
    val isEmpty get() = noise == null && ambience == null
}

/** Why this sound: one sentence the home tab writes under it, and the card that says more. */
enum class Reason(val card: String) {
    NIGHT_AS_LAST_TIME("c4_2"),
    NIGHT_SOFT_SOUND("c4_2"),
    DAY_AS_LAST_TIME("c2_3"),
    DAY_BLEND("c2_3"),
}

/** One thing to play, with its reason. Levels are never part of it: the person's own stay. */
data class Suggestion(val noise: NoiseColour?, val ambience: Ambience?, val timerMinutes: Int?, val reason: Reason)

/**
 * The home tab's one suggestion, kept small enough to explain in a sentence:
 * - the last comfort sound if there is one, otherwise rain by night and pink noise by day;
 * - by night it always carries a sleep timer, the one already set or [NIGHT_TIMER];
 * - by day the timer is left as it was;
 * - never the tone pattern: that is a practice in sessions, not something to fall asleep to.
 */
object Suggest {
    const val NIGHT_TIMER = 45

    fun of(night: Boolean, last: LastSound?): Suggestion {
        val known = last != null && !last.isEmpty
        return if (night) {
            val timer = last?.timerMinutes?.takeIf { it in LevelRules.SLEEP_MINUTES } ?: NIGHT_TIMER
            if (known) Suggestion(last!!.noise, last.ambience, timer, Reason.NIGHT_AS_LAST_TIME)
            else Suggestion(null, Ambience.RAIN, timer, Reason.NIGHT_SOFT_SOUND)
        } else {
            if (known) Suggestion(last!!.noise, last.ambience, last.timerMinutes, Reason.DAY_AS_LAST_TIME)
            else Suggestion(NoiseColour.PINK, null, last?.timerMinutes, Reason.DAY_BLEND)
        }
    }
}
