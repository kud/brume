package app.beansontoast.brume.core.sound

import kotlin.math.max
import kotlin.math.pow

/**
 * Safe listening, as far as an app can hold it. Brume knows its own samples, not the sound at the
 * ear, so these rules are relative: start quiet, fade in, never get louder on its own.
 */
object LevelRules {
    /** A layer's level is 0..1 on its slider. 0 is silence. */
    const val DEFAULT_LEVEL = 0.3f
    const val STEP = 0.05f

    /** "Too loud" lowers the layer this much before the next start (about 6 dB). */
    const val TOO_LOUD_DROP = 0.15f

    const val FADE_IN_SECONDS = 3.0
    const val PAUSE_FADE_SECONDS = 0.25
    const val GAIN_RAMP_SECONDS = 0.02
    const val SLEEP_FADE_SECONDS = 300.0

    /** Sleep timer choices in minutes; null is off. "Until morning" waits for the night-level rules. */
    val SLEEP_MINUTES = listOf(20, 45, 90)

    private const val FLOOR_DB = -48.0
    private const val TOP_DB = -6.0

    /** Slider to linear gain: an even step in dB, so the slider feels even to the ear. */
    fun gainFor(level: Float): Float {
        val l = level.coerceIn(0f, 1f)
        if (l == 0f) return 0f
        val db = FLOOR_DB + (TOP_DB - FLOOR_DB) * l
        return 10.0.pow(db / 20).toFloat()
    }

    fun clampLevel(level: Float): Float = level.coerceIn(0f, 1f)

    fun afterTooLoud(level: Float): Float = max(0f, level - TOO_LOUD_DROP)

    /** A resumed layer starts at or below where it was: never louder than last time. */
    fun resumeLevel(last: Float, requested: Float): Float = minOf(clampLevel(last), clampLevel(requested))
}
