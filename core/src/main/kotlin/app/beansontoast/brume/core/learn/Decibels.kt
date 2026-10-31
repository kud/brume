package app.beansontoast.brume.core.learn

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

/** How a level was measured. Only A-weighted averages can be read against the safe-time rule. */
enum class Measure { A_WEIGHTED, UNWEIGHTED }

/**
 * An everyday sound on the scale. [db] is its typical level, from atelier's decibels.md (2026-10-31),
 * its "Verified 2026-10-31" section first. The label, with its distance or context, lives in the
 * app's strings as `db_<id>`, and its picture in the drawable slot `db_<id>_night` / `db_<id>_day`.
 */
data class Everyday(val id: String, val db: Int, val measure: Measure = Measure.A_WEIGHTED)

/** How long an unprotected ear can take a level, by NIOSH's rule. */
sealed interface SafeTime {
    /** 70 dBA and below: "generally considered safe" (NIDCD, ASHA). */
    data object NoLimit : SafeTime

    /** Between 70 and 85 dBA: past NIOSH's 8-hour mark, so more than a working day. */
    data object MoreThanEightHours : SafeTime

    /** A time a day, rounded for reading by [Decibels.rounded]. */
    data class Daily(val seconds: Double) : SafeTime

    /** 140 dB and up: NIOSH's ceiling, no time at all. */
    data object Never : SafeTime
}

/** The ear's state on the explorer. Each one is a word in the app; nothing rests on colour. */
enum class Strain { REST, EASY, TIRING, FAST, OVERWHELMED, TOO_LOUD }

/** A rounded time for reading: hours and minutes, minutes, seconds, or under a second. */
data class Rounded(val hours: Int = 0, val minutes: Int = 0, val seconds: Int = 0, val underASecond: Boolean = false)

/**
 * The decibel explorer's facts (Iris, 2026-10-31, from Erwann's ask for « une molette… et tu vois
 * combien de temps tu peux tenir »). NIOSH REL: 85 dBA for 8 hours, halved every 3 dB (NIOSH 98-126,
 * verified); 140 dBA is its ceiling. +10 dB sounds about twice as loud (NIDCD, verified).
 */
object Decibels {
    const val MIN = 0
    const val MAX = 140
    const val STEP = 5
    const val QUIET = 70
    const val REL = 85
    const val REL_HOURS = 8
    const val EXCHANGE = 3
    const val CEILING = 140

    /** Below this the dial shows no everyday sound: nothing on the scale sits there. */
    const val FIRST_SOUND_FROM = 15

    val everyday: List<Everyday> = listOf(
        Everyday("leaf", 20),
        Everyday("whisper", 30),
        Everyday("library", 40),
        Everyday("conversation", 60),
        Everyday("traffic", 85),
        Everyday("nightclub", 96),
        Everyday("concert", 100),
        Everyday("drums", 103),
        Everyday("headphones_max", 105),
        Everyday("aeroplane", 120),
        Everyday("rocket", 136, Measure.UNWEIGHTED),
    )

    /** The dial's detent for any value: whole steps of [STEP], inside the range. */
    fun snap(db: Float): Int = ((db / STEP).roundToLong() * STEP).toInt().coerceIn(MIN, MAX)

    /** The sound nearest a level, the quieter on a tie; none below [FIRST_SOUND_FROM]. */
    fun nearest(db: Int): Everyday? =
        if (db < FIRST_SOUND_FROM) null else everyday.minWith(compareBy<Everyday> { abs(it.db - db) }.thenBy { it.db })

    fun safeTime(db: Int): SafeTime = when {
        db >= CEILING -> SafeTime.Never
        db >= REL -> SafeTime.Daily(REL_HOURS * 3600.0 / 2.0.pow((db - REL).toDouble() / EXCHANGE))
        db > QUIET -> SafeTime.MoreThanEightHours
        else -> SafeTime.NoLimit
    }

    /** One hour and up to the nearest 5 minutes; then whole minutes; under 2 minutes, seconds. */
    fun rounded(seconds: Double): Rounded = when {
        seconds < 1 -> Rounded(underASecond = true)
        seconds < 120 -> Rounded(seconds = seconds.roundToLong().toInt())
        seconds < 3600 -> Rounded(minutes = (seconds / 60).roundToLong().toInt())
        else -> {
            val fives = (seconds / 300).roundToLong().toInt()
            Rounded(hours = fives / 12, minutes = fives % 12 * 5)
        }
    }

    fun strain(db: Int): Strain = when {
        db >= CEILING -> Strain.TOO_LOUD
        db >= 120 -> Strain.OVERWHELMED
        db >= 100 -> Strain.FAST
        db >= REL -> Strain.TIRING
        db > QUIET -> Strain.EASY
        else -> Strain.REST
    }

    /** How loud against [reference]: +10 dB is about twice as loud. */
    fun loudness(db: Int, reference: Int = 60): Double = 2.0.pow((db - reference) / 10.0)

    /**
     * The hourglass's sand, 1 full to 0 empty: full up to [REL], then the same amount gone every
     * [EXCHANGE] dB, since each one halves the time, and empty at [CEILING].
     */
    fun sand(db: Int): Float = (1f - (db - REL).toFloat() / (CEILING - REL)).coerceIn(0f, 1f)

    /** The ear's tension, 0 at rest to 1 overwhelmed: rises from 75 dB, full at 120. */
    fun tension(db: Float): Float = ((db - 75f) / 45f).coerceIn(0f, 1f)
}
