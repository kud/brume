package app.beansontoast.brume.core.export

import app.beansontoast.brume.core.journal.CheckIn
import app.beansontoast.brume.core.journal.EndedBy
import app.beansontoast.brume.core.journal.ListeningSession
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
    ) = ExportFile(
        exportedAt = exportedAt,
        appVersion = appVersion,
        settings = settings,
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
        file.sessions.forEach { require(it.seconds >= 0) { "negative session" }; java.time.Instant.parse(it.startedAt) }
        return file
    }

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
