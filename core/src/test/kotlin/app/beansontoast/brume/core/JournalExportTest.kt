package app.beansontoast.brume.core

import app.beansontoast.brume.core.export.ExportFormat
import app.beansontoast.brume.core.journal.CheckIn
import app.beansontoast.brume.core.journal.EndedBy
import app.beansontoast.brume.core.journal.ListeningSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

/** Invented data only: no real readings ever go in the repository. */
class JournalExportTest {
    private val checkIns = listOf(
        CheckIn("2026-01-02", loudness = 5, bother = 3, note = "Quiet day"),
        CheckIn("2026-01-01", loudness = 6, bother = 4),
    )
    private val sessions = listOf(
        ListeningSession(1_767_312_000_000, 2700, "PINK", 0.3f, "RAIN", 0.25f, EndedBy.TIMER),
    )

    @Test fun `ratings outside 0 to 10 are refused`() {
        assertThrows(IllegalArgumentException::class.java) { CheckIn("2026-01-01", 11, 0) }
        assertThrows(IllegalArgumentException::class.java) { CheckIn("2026-01-01", 0, -1) }
        assertThrows(Exception::class.java) { CheckIn("yesterday", 0, 0) }
    }

    @Test fun `loose input is clamped and trimmed`() {
        val c = CheckIn.of(LocalDate.of(2026, 1, 3), 14, -2, "  ${"x".repeat(600)}  ")
        assertEquals(10, c.loudness); assertEquals(0, c.bother); assertEquals(CheckIn.NOTE_MAX, c.note.length)
    }

    @Test fun `an export reads back to the same journal`() {
        val text = ExportFormat.encode(ExportFormat.build("2026-01-03T08:00:00Z", "0.1.0", checkIns, sessions))
        val back = ExportFormat.decode(text)
        assertEquals(checkIns.sortedBy { it.day }, ExportFormat.toCheckIns(back))
        assertEquals(sessions, ExportFormat.toSessions(back))
    }

    @Test fun `version 1 is pinned`() {
        val text = ExportFormat.encode(ExportFormat.build("2026-01-03T08:00:00Z", "0.1.0", checkIns.take(1), sessions))
        val expected = """
            {
                "format": "brume-export",
                "schemaVersion": 1,
                "exportedAt": "2026-01-03T08:00:00Z",
                "appVersion": "0.1.0",
                "checkIns": [
                    {
                        "day": "2026-01-02",
                        "loudness": 5,
                        "bother": 3,
                        "note": "Quiet day"
                    }
                ],
                "sessions": [
                    {
                        "startedAt": "2026-01-02T00:00:00Z",
                        "seconds": 2700,
                        "noise": "PINK",
                        "noiseLevel": 0.3,
                        "ambience": "RAIN",
                        "ambienceLevel": 0.25,
                        "endedBy": "timer"
                    }
                ]
            }
        """.trimIndent()
        assertEquals(expected, text)
    }

    @Test fun `a newer schema or another format is refused, not guessed at`() {
        assertThrows(IllegalArgumentException::class.java) {
            ExportFormat.decode("""{"format":"brume-export","schemaVersion":2,"exportedAt":"x","appVersion":"x","checkIns":[],"sessions":[]}""")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ExportFormat.decode("""{"format":"other","schemaVersion":1,"exportedAt":"x","appVersion":"x","checkIns":[],"sessions":[]}""")
        }
    }
}
