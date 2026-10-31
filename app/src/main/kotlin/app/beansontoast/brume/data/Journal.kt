package app.beansontoast.brume.data

import app.beansontoast.brume.core.export.ExportFormat
import app.beansontoast.brume.core.journal.CheckIn
import app.beansontoast.brume.core.journal.EndedBy
import app.beansontoast.brume.core.journal.ListeningSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class Journal(private val dao: JournalDao) {
    val checkIns: Flow<List<CheckIn>> = dao.checkIns().map { rows -> rows.map { it.toCore() } }

    /** Seconds listened since Monday, local time. */
    fun weekSeconds(zone: ZoneId = ZoneId.systemDefault()): Flow<Long> {
        val today = LocalDate.now(zone)
        val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        return dao.secondsSince(monday.atStartOfDay(zone).toInstant().toEpochMilli())
    }

    suspend fun save(checkIn: CheckIn) = dao.upsert(
        CheckInEntity(checkIn.day, checkIn.loudness, checkIn.bother, checkIn.note, System.currentTimeMillis()),
    )

    suspend fun log(session: ListeningSession) = dao.insert(
        SessionEntity(
            startedAt = session.startedAt, seconds = session.seconds,
            noise = session.noise, noiseLevel = session.noiseLevel,
            ambience = session.ambience, ambienceLevel = session.ambienceLevel,
            endedBy = session.endedBy.name,
        ),
    )

    suspend fun exportJson(appVersion: String): String {
        val sessions = dao.allSessions().map {
            ListeningSession(it.startedAt, it.seconds, it.noise, it.noiseLevel, it.ambience, it.ambienceLevel, EndedBy.valueOf(it.endedBy))
        }
        val file = ExportFormat.build(Instant.now().toString(), appVersion, dao.allCheckIns().map { it.toCore() }, sessions)
        return ExportFormat.encode(file)
    }

    private fun CheckInEntity.toCore() = CheckIn(day, loudness, bother, note)
}
