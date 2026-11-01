package app.beansontoast.brume.data

import app.beansontoast.brume.core.export.CheckInV1
import app.beansontoast.brume.core.export.ExportFile
import app.beansontoast.brume.core.export.ExportFormat
import app.beansontoast.brume.core.export.SessionV1
import app.beansontoast.brume.core.export.SettingsV1
import app.beansontoast.brume.core.export.ProfileV1
import app.beansontoast.brume.core.finder.TinnitusProfile
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

    /** « Mon acouphène » profiles, most recently saved first; removed ones are not listed. */
    val profiles: Flow<List<TinnitusProfile>> = dao.profiles().map { rows -> rows.map { it.toCore() } }

    suspend fun saveProfile(profile: TinnitusProfile) = dao.upsertProfile(profile.copy(updatedAt = System.currentTimeMillis()).toEntity())

    /** Removes a profile: only its id and the time stay, so another phone's copy is not brought back. */
    suspend fun deleteProfile(id: String) = dao.upsertProfile(TinnitusProfile(id, "").tombstone(System.currentTimeMillis()).toEntity())

    /** Stores profiles merged from a file, each with its own save time. */
    suspend fun takeProfiles(profiles: List<ProfileV1>) = profiles.forEach { dao.upsertProfile(ExportFormat.toProfile(it).toEntity()) }

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

    suspend fun exportJson(appVersion: String): String = ExportFormat.encode(snapshot(appVersion))

    /** The whole journal as the export file, each day with its save time; [settings] only for sync. */
    suspend fun snapshot(appVersion: String, settings: SettingsV1? = null): ExportFile {
        val sessions = dao.allSessions().map {
            ListeningSession(it.startedAt, it.seconds, it.noise, it.noiseLevel, it.ambience, it.ambienceLevel, EndedBy.valueOf(it.endedBy))
        }
        val rows = dao.allCheckIns()
        return ExportFormat.build(Instant.now().toString(), appVersion, rows.map { it.toCore() }, sessions,
            rows.associate { it.day to it.updatedAt }, settings, dao.allProfiles().map { it.toCore() })
    }

    /** Stores rows merged from a file, keeping each check-in's own save time. Returns (days, sessions). */
    suspend fun take(checkIns: List<CheckInV1>, sessions: List<SessionV1>): Pair<Int, Int> {
        checkIns.forEach { dao.upsert(CheckInEntity(it.day, it.loudness, it.bother, it.note, it.updatedAt ?: 0L)) }
        val stored = ExportFormat.toSessions(sessions)
        dao.insertAll(stored.map {
            SessionEntity(startedAt = it.startedAt, seconds = it.seconds, noise = it.noise, noiseLevel = it.noiseLevel,
                ambience = it.ambience, ambienceLevel = it.ambienceLevel, endedBy = it.endedBy.name)
        })
        return checkIns.size to stored.size
    }

    private fun CheckInEntity.toCore() = CheckIn(day, loudness, bother, note)

    private fun ProfileEntity.toCore() = ExportFormat.toProfile(
        ProfileV1(id, name, character, side, rhythm, loudness, bother, updatedAt, deleted),
    ).copy(matches = ExportFormat.decodeMatches(matches))

    private fun TinnitusProfile.toEntity() = ExportFormat.toV1(this).let {
        ProfileEntity(id, name, it.character, it.side, it.rhythm, loudness, bother, ExportFormat.encodeMatches(matches), updatedAt, deleted)
    }
}
