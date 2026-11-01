package app.beansontoast.brume.core.export

import app.beansontoast.brume.core.journal.CheckIn
import app.beansontoast.brume.core.journal.EndedBy
import app.beansontoast.brume.core.journal.ListeningSession
import app.beansontoast.brume.core.finder.Character
import app.beansontoast.brume.core.finder.PitchLadder
import app.beansontoast.brume.core.finder.PitchMatch
import app.beansontoast.brume.core.finder.Rhythm
import app.beansontoast.brume.core.finder.Side
import app.beansontoast.brume.core.finder.TinnitusProfile
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The export, the only way data leaves the phone: one JSON file the person saves where they choose.
 * Versioned so a later Brume (or a spreadsheet script) can read every file an earlier one wrote.
 * Version 1 changes only by adding optional fields; anything else is version 2.
 */
@Serializable
data class ExportFile(
    val format: String = FORMAT,
    val schemaVersion: Int = SCHEMA_VERSION,
    val exportedAt: String,
    val appVersion: String,
    val checkIns: List<CheckInV1>,
    val sessions: List<SessionV1>,
    /** Added for sync (2026-10-31): the settings that follow the person, not the phone. Optional. */
    val settings: SettingsV1? = null,
    /** Added for « Mon acouphène » (2026-11-01): the person's own descriptions and searches. Optional. */
    val profiles: List<ProfileV1>? = null,
) {
    companion object {
        const val FORMAT = "brume-export"
        const val SCHEMA_VERSION = 1
    }
}

@Serializable
data class CheckInV1(
    val day: String,
    val loudness: Int,
    val bother: Int,
    val note: String = "",
    /** When this phone saved it, epoch ms. Added for sync; absent in files from 0.1.0-dev.4 and earlier. */
    val updatedAt: Long? = null,
)

/**
 * Settings that sync, each group with the time it was last changed (epoch ms) so the later one wins.
 * Levels never sync: a level is set for this phone's output and headphones, and a level arriving
 * from elsewhere would be a level raised without a user action.
 */
@Serializable
data class SettingsV1(
    val nightHours: Boolean? = null,
    val nightHoursAt: Long? = null,
    val tonePitch: Int? = null,
    val toneTrims: List<Float>? = null,
    val tonesAt: Long? = null,
    /** Which modules are on, by name ("TONES", "BREATH"); names this version doesn't know are kept as they came. */
    val modules: Map<String, Boolean>? = null,
    val modulesAt: Long? = null,
)

/**
 * A tinnitus described in the person's words. Enum values are written in lower case; one this Brume
 * doesn't know reads as unset. [deleted] profiles carry only their id and time, so sync never revives them.
 */
@Serializable
data class ProfileV1(
    val id: String,
    val name: String = "",
    val character: String? = null,
    val side: String? = null,
    val rhythm: String? = null,
    val loudness: Int? = null,
    val bother: Int? = null,
    val updatedAt: Long = 0L,
    val deleted: Boolean = false,
    val matches: List<MatchV1> = emptyList(),
)

/** One search: the day, the pitch in Hz, the sound used ("tone" or "hiss"), whether the octave check was done. */
@Serializable
data class MatchV1(val id: String, val day: String, val hz: Int, val sound: String, val octaveChecked: Boolean = false)

/** A file from a later Brume: its schema is newer than this one reads. */
class NewerSchemaException(schema: Int) : IllegalArgumentException("schema $schema is newer than this Brume")

@Serializable
data class SessionV1(
    val startedAt: String,
    val seconds: Long,
    val noise: String? = null,
    val noiseLevel: Float? = null,
    val ambience: String? = null,
    val ambienceLevel: Float? = null,
    @SerialName("endedBy") val endedBy: String,
)

object ExportFormat {
    private val json = Json { prettyPrint = true; encodeDefaults = true; explicitNulls = false }
    private val reader = Json { ignoreUnknownKeys = true }

    /** [stamps] gives each day's save time, by day; [settings] is present only in a sync file. */
    fun build(
        exportedAt: String,
        appVersion: String,
        checkIns: List<CheckIn>,
        sessions: List<ListeningSession>,
        stamps: Map<String, Long> = emptyMap(),
        settings: SettingsV1? = null,
        profiles: List<TinnitusProfile> = emptyList(),
    ) = ExportFile(
        exportedAt = exportedAt,
        appVersion = appVersion,
        settings = settings,
        profiles = profiles.takeIf { it.isNotEmpty() }?.sortedBy { it.id }?.map(::toV1),
        checkIns = checkIns.sortedBy { it.day }.map { CheckInV1(it.day, it.loudness, it.bother, it.note, stamps[it.day]) },
        sessions = sessions.sortedBy { it.startedAt }.map {
            SessionV1(
                startedAt = java.time.Instant.ofEpochMilli(it.startedAt).toString(),
                seconds = it.seconds,
                noise = it.noise, noiseLevel = it.noiseLevel,
                ambience = it.ambience, ambienceLevel = it.ambienceLevel,
                endedBy = it.endedBy.name.lowercase(),
            )
        },
    )

    fun encode(file: ExportFile): String = json.encodeToString(ExportFile.serializer(), file)

    /** Reads a file back. Refuses another format or a newer schema rather than guessing at it. */
    fun decode(text: String): ExportFile {
        val file = reader.decodeFromString(ExportFile.serializer(), text)
        require(file.format == ExportFile.FORMAT) { "not a Brume export: ${file.format}" }
        if (file.schemaVersion > ExportFile.SCHEMA_VERSION) throw NewerSchemaException(file.schemaVersion)
        require(file.schemaVersion >= 1) { "schema ${file.schemaVersion} is not a Brume schema" }
        return file
    }

    /**
     * Reads a file that will be merged into the journal (an import, or the sync file) and checks every
     * row first, so a bad file changes nothing rather than half of the journal. A session that ended in
     * a way this Brume doesn't know (a newer one added it) is valid: it is carried, not applied.
     */
    fun read(text: String): ExportFile {
        val file = decode(text)
        toCheckIns(file)
        file.profiles.orEmpty().forEach { p ->
            require(p.id.isNotBlank()) { "profile without id" }
            p.matches.forEach { java.time.LocalDate.parse(it.day); require(it.hz > 0) { "bad pitch" } }
        }
        file.sessions.forEach { require(it.seconds >= 0) { "negative session" }; java.time.Instant.parse(it.startedAt) }
        return file
    }

    fun toV1(p: TinnitusProfile) = ProfileV1(
        id = p.id, name = p.name, character = p.character?.wire(), side = p.side?.wire(), rhythm = p.rhythm?.wire(),
        loudness = p.loudness, bother = p.bother, updatedAt = p.updatedAt, deleted = p.deleted,
        matches = p.matches.sortedWith(compareBy({ it.day }, { it.id })).map { MatchV1(it.id, it.day, it.hz, it.sound.wire(), it.octaveChecked) },
    )

    fun toProfile(p: ProfileV1) = TinnitusProfile(
        id = p.id, name = p.name,
        character = enumOf<Character>(p.character), side = enumOf<Side>(p.side), rhythm = enumOf<Rhythm>(p.rhythm),
        loudness = TinnitusProfile.rating(p.loudness), bother = TinnitusProfile.rating(p.bother),
        updatedAt = p.updatedAt, deleted = p.deleted,
        matches = p.matches.mapNotNull { m ->
            val sound = enumOf<Character>(m.sound) ?: return@mapNotNull null
            PitchMatch(m.id, m.day, PitchLadder.clamp(m.hz), sound, m.octaveChecked)
        },
    )

    private val compact = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    private val matchList = kotlinx.serialization.builtins.ListSerializer(MatchV1.serializer())

    /** A profile's matches as stored in one database column: the export's own shape. */
    fun encodeMatches(matches: List<PitchMatch>): String =
        compact.encodeToString(matchList, matches.map { MatchV1(it.id, it.day, it.hz, it.sound.wire(), it.octaveChecked) })

    fun decodeMatches(text: String): List<PitchMatch> = runCatching { compact.decodeFromString(matchList, text) }.getOrDefault(emptyList())
        .mapNotNull { m -> enumOf<Character>(m.sound)?.let { PitchMatch(m.id, m.day, PitchLadder.clamp(m.hz), it, m.octaveChecked) } }

    fun toProfiles(file: ExportFile): List<TinnitusProfile> = file.profiles.orEmpty().map(::toProfile)

    private fun Enum<*>.wire() = name.lowercase()
    private inline fun <reified E : Enum<E>> enumOf(wire: String?): E? = enumValues<E>().firstOrNull { it.name == wire?.uppercase() }

    fun toCheckIns(file: ExportFile): List<CheckIn> = file.checkIns.map { CheckIn(it.day, it.loudness, it.bother, it.note) }

    fun toSessions(file: ExportFile): List<ListeningSession> = toSessions(file.sessions)

    /** Sessions this Brume can store; one with an unknown way of ending is left out. */
    fun toSessions(sessions: List<SessionV1>): List<ListeningSession> = sessions.mapNotNull {
        val endedBy = EndedBy.entries.firstOrNull { e -> e.name == it.endedBy.uppercase() } ?: return@mapNotNull null
        ListeningSession(
            startedAt = java.time.Instant.parse(it.startedAt).toEpochMilli(),
            seconds = it.seconds,
            noise = it.noise, noiseLevel = it.noiseLevel,
            ambience = it.ambience, ambienceLevel = it.ambienceLevel,
            endedBy = endedBy,
        )
    }
}
