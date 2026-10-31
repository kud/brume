package app.beansontoast.brume.core

import app.beansontoast.brume.core.export.ExportFile
import app.beansontoast.brume.core.export.ExportFormat
import app.beansontoast.brume.core.export.NewerSchemaException
import app.beansontoast.brume.core.export.SettingsV1
import app.beansontoast.brume.core.journal.CheckIn
import app.beansontoast.brume.core.journal.EndedBy
import app.beansontoast.brume.core.journal.ListeningSession
import app.beansontoast.brume.core.sync.SyncMerge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Export on one phone, import on another: the second ends with the same journal. Invented data. */
class ImportRoundTripTest {
    private val checkIns = listOf(CheckIn("2026-01-01", 6, 4), CheckIn("2026-01-02", 5, 3, "Quiet day"))
    private val sessions = listOf(
        ListeningSession(1_767_312_000_000, 2700, "PINK", 0.3f, "RAIN", 0.25f, EndedBy.TIMER),
        ListeningSession(1_767_398_400_123, 600, null, null, "WAVES", 0.4f, EndedBy.TOO_LOUD),
    )
    private val settings = SettingsV1(nightHours = false, nightHoursAt = 40, tonePitch = 6800, toneTrims = listOf(1f, 0.9f, 0.7f, 0.5f), tonesAt = 41)

    private fun empty() = ExportFile(exportedAt = "2026-01-04T08:00:00Z", appVersion = "0.1.0", checkIns = emptyList(), sessions = emptyList())

    @Test fun `an import into an empty phone brings every row and setting across`() {
        val text = ExportFormat.encode(ExportFormat.build("2026-01-03T08:00:00Z", "0.1.0", checkIns, sessions,
            mapOf("2026-01-01" to 10L, "2026-01-02" to 20L), settings))
        val incoming = ExportFormat.read(text)
        val changes = SyncMerge.changesFor(empty(), SyncMerge.merge(empty(), incoming, "now", "0.1.0"))

        assertEquals(checkIns, changes.checkIns.map { CheckIn(it.day, it.loudness, it.bother, it.note) })
        assertEquals(listOf(10L, 20L), changes.checkIns.map { it.updatedAt })
        assertEquals(sessions, ExportFormat.toSessions(changes.sessions))
        assertEquals(false to 40L, changes.nightHours)
        assertEquals(Triple(6800, listOf(1f, 0.9f, 0.7f, 0.5f), 41L), changes.tones)
    }

    @Test fun `importing the same file twice adds nothing the second time`() {
        val incoming = ExportFormat.read(ExportFormat.encode(ExportFormat.build("t", "0.1.0", checkIns, sessions, mapOf("2026-01-01" to 10L, "2026-01-02" to 20L))))
        val once = SyncMerge.merge(empty(), incoming, "now", "0.1.0")
        assertEquals(true, SyncMerge.changesFor(once, SyncMerge.merge(once, incoming, "now", "0.1.0")).isEmpty)
    }

    @Test fun `a bad or newer file is refused whole`() {
        val good = ExportFormat.encode(ExportFormat.build("t", "0.1.0", checkIns, sessions))
        assertThrows(IllegalArgumentException::class.java) { ExportFormat.read(good.replace("\"loudness\": 6", "\"loudness\": 60")) }
        assertThrows(NewerSchemaException::class.java) { ExportFormat.read(good.replace("\"schemaVersion\": 1", "\"schemaVersion\": 2")) }
        assertThrows(IllegalArgumentException::class.java) { ExportFormat.read(good.replace("brume-export", "something-else")) }
    }

    @Test fun `a file from before sync still reads`() {
        val old = """{"format":"brume-export","schemaVersion":1,"exportedAt":"t","appVersion":"0.1.0-dev.4",
            "checkIns":[{"day":"2026-01-01","loudness":6,"bother":4,"note":""}],"sessions":[]}"""
        assertEquals(null, ExportFormat.read(old).checkIns.single().updatedAt)
        assertEquals(null, ExportFormat.read(old).settings)
    }
}
