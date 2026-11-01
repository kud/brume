package app.beansontoast.brume.core.finder

/** How it sounds: tone-like (a whistle) or noise-like (a hiss). Also what the search plays. */
enum class Character { TONE, HISS }

/** Where it is heard. */
enum class Side { LEFT, RIGHT, BOTH, HEAD }

/** Steady, coming and going, or pulsing in time with the heartbeat. */
enum class Rhythm { STEADY, PULSING, HEARTBEAT }

/** Which ear the search plays in: one at a time, or both. */
enum class Ear { LEFT, RIGHT, BOTH }

/** One search, on one day: the pitch found, with which sound, and whether the octave check was done. */
data class PitchMatch(val id: String, val day: String, val hz: Int, val sound: Character, val octaveChecked: Boolean)

/**
 * A tinnitus described in the person's own words, with every search they made. Health data: it lives
 * on the phone, in their export and in their own sync folder, nowhere else. [deleted] keeps only the
 * id and the time, so a profile removed here is not brought back by another phone's copy.
 */
data class TinnitusProfile(
    val id: String,
    val name: String,
    val character: Character? = null,
    val side: Side? = null,
    val rhythm: Rhythm? = null,
    /** 0..10, in words on screen; null when skipped. */
    val loudness: Int? = null,
    val bother: Int? = null,
    val matches: List<PitchMatch> = emptyList(),
    val updatedAt: Long = 0L,
    val deleted: Boolean = false,
) {
    val range: PitchRange? get() = PitchRange.of(matches.map { it.hz })
    val comfort: ComfortBand? get() = range?.let(ComfortBand::around)

    /** A removal: the id and when, nothing of what it described. */
    fun tombstone(at: Long) = TinnitusProfile(id = id, name = "", updatedAt = at, deleted = true)

    companion object {
        fun rating(value: Int?): Int? = value?.coerceIn(0, 10)
    }
}

/**
 * When to suggest seeing a doctor (NICE NG155), told calmly in the journey: pulsing in time with the
 * heartbeat, one ear only, sudden onset or sudden hearing loss. The last one is « the same day ».
 * [stressed] is true when what the person described matches one of the first two.
 */
object Signpost {
    fun stressed(side: Side?, rhythm: Rhythm?): Boolean =
        rhythm == Rhythm.HEARTBEAT || side == Side.LEFT || side == Side.RIGHT
}
