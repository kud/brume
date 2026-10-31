package app.beansontoast.brume.core.journal

import java.time.LocalDate

/** One daily check-in. Ratings are the person's word, 0 to 10; nothing is inferred from them. */
data class CheckIn(
    /** ISO date, YYYY-MM-DD: one check-in per day, a later one replaces it. */
    val day: String,
    val loudness: Int,
    val bother: Int,
    val note: String = "",
) {
    init {
        require(loudness in SCALE) { "loudness $loudness is outside 0..10" }
        require(bother in SCALE) { "bother $bother is outside 0..10" }
        require(note.length <= NOTE_MAX) { "note is longer than $NOTE_MAX characters" }
        LocalDate.parse(day)
    }

    companion object {
        val SCALE = 0..10
        const val NOTE_MAX = 500

        /** Builds a check-in from loose input: ratings clamped, note trimmed and cut. */
        fun of(day: LocalDate, loudness: Int, bother: Int, note: String): CheckIn = CheckIn(
            day = day.toString(),
            loudness = loudness.coerceIn(SCALE),
            bother = bother.coerceIn(SCALE),
            note = note.trim().take(NOTE_MAX),
        )
    }
}

/** How a listening session ended. Stored as its name; never renamed, only added to. */
enum class EndedBy { USER, TIMER, TOO_LOUD, ROUTE_CHANGE, FOCUS_LOSS }

/** One stretch of listening, logged without asking: what played, how long, at what level. */
data class ListeningSession(
    val startedAt: Long,
    val seconds: Long,
    val noise: String?,
    val noiseLevel: Float?,
    val ambience: String?,
    val ambienceLevel: Float?,
    val endedBy: EndedBy,
) {
    init { require(seconds >= 0) }
}
