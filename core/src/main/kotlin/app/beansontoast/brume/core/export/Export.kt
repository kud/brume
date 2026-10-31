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
) {
    companion object {
        const val FORMAT = "brume-export"
        const val SCHEMA_VERSION = 1
    }
}

@Serializable
data class CheckInV1(val day: String, val loudness: Int, val bother: Int, val note: String = "")

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

    fun build(exportedAt: String, appVersion: String, checkIns: List<CheckIn>, sessions: List<ListeningSession>) = ExportFile(
        exportedAt = exportedAt,
        appVersion = appVersion,
        checkIns = checkIns.sortedBy { it.day }.map { CheckInV1(it.day, it.loudness, it.bother, it.note) },
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
        require(file.schemaVersion in 1..ExportFile.SCHEMA_VERSION) { "schema ${file.schemaVersion} is newer than this Brume" }
        return file
    }

    fun toCheckIns(file: ExportFile): List<CheckIn> = file.checkIns.map { CheckIn(it.day, it.loudness, it.bother, it.note) }

    fun toSessions(file: ExportFile): List<ListeningSession> = file.sessions.map {
        ListeningSession(
            startedAt = java.time.Instant.parse(it.startedAt).toEpochMilli(),
            seconds = it.seconds,
            noise = it.noise, noiseLevel = it.noiseLevel,
            ambience = it.ambience, ambienceLevel = it.ambienceLevel,
            endedBy = EndedBy.valueOf(it.endedBy.uppercase()),
        )
    }
}
