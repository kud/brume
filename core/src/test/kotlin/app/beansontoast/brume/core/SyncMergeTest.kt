package app.beansontoast.brume.core

import app.beansontoast.brume.core.export.CheckInV1
import app.beansontoast.brume.core.export.ExportFile
import app.beansontoast.brume.core.export.SessionV1
import app.beansontoast.brume.core.export.SettingsV1
import app.beansontoast.brume.core.sync.SyncFile
import app.beansontoast.brume.core.sync.SyncMerge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Invented data only. Two phones, A and B, sharing one folder. */
class SyncMergeTest {
    private fun file(checkIns: List<CheckInV1> = emptyList(), sessions: List<SessionV1> = emptyList(), settings: SettingsV1? = null) =
        ExportFile(exportedAt = "2026-01-05T08:00:00Z", appVersion = "0.1.0", checkIns = checkIns, sessions = sessions, settings = settings)

    private fun merge(a: ExportFile, b: ExportFile) = SyncMerge.merge(a, b, "2026-01-05T09:00:00Z", "0.1.0")

    private val walk = SessionV1("2026-01-02T21:00:00Z", 1800, noise = "PINK", noiseLevel = 0.3f, endedBy = "timer")
    private val night = SessionV1("2026-01-03T23:10:00Z", 3600, ambience = "RAIN", ambienceLevel = 0.2f, endedBy = "user")

    @Test fun `days only one phone has are all kept`() {
        val a = file(listOf(CheckInV1("2026-01-01", 5, 3, updatedAt = 10)))
        val b = file(listOf(CheckInV1("2026-01-02", 6, 4, updatedAt = 20)))
        assertEquals(listOf("2026-01-01", "2026-01-02"), merge(a, b).checkIns.map { it.day })
    }

    @Test fun `a day both phones have keeps the later save`() {
        val a = file(listOf(CheckInV1("2026-01-01", 5, 3, "morning", updatedAt = 100)))
        val b = file(listOf(CheckInV1("2026-01-01", 7, 6, "evening", updatedAt = 200)))
        assertEquals("evening", merge(a, b).checkIns.single().note)
        assertEquals("evening", merge(b, a).checkIns.single().note)
    }

    @Test fun `an old file without times fills gaps but never overwrites a day`() {
        val phone = file(listOf(CheckInV1("2026-01-01", 2, 2, "mine", updatedAt = 50)))
        val old = file(listOf(CheckInV1("2026-01-01", 9, 9, "old"), CheckInV1("2025-12-31", 4, 4, "gap")))
        val merged = merge(phone, old)
        assertEquals(listOf("gap", "mine"), merged.checkIns.map { it.note })
        assertEquals(listOf("gap"), SyncMerge.changesFor(phone, merged).checkIns.map { it.note })
    }

    @Test fun `sessions are a union, each once`() {
        val merged = merge(file(sessions = listOf(night, walk)), file(sessions = listOf(walk)))
        assertEquals(listOf(walk, night), merged.sessions)
    }

    @Test fun `settings follow the later change, per group`() {
        val a = file(settings = SettingsV1(nightHours = false, nightHoursAt = 300, tonePitch = 6000, toneTrims = listOf(1f, 1f, 1f, 1f), tonesAt = 100))
        val b = file(settings = SettingsV1(nightHours = true, nightHoursAt = 200, tonePitch = 7200, toneTrims = listOf(1f, 0.8f, 0.6f, 0.5f), tonesAt = 400))
        val s = merge(a, b).settings!!
        assertEquals(false, s.nightHours)
        assertEquals(7200, s.tonePitch)
        assertEquals(listOf(1f, 0.8f, 0.6f, 0.5f), s.toneTrims)
        val changes = SyncMerge.changesFor(a, merge(a, b))
        assertEquals(null, changes.nightHours)
        assertEquals(Triple(7200, listOf(1f, 0.8f, 0.6f, 0.5f), 400L), changes.tones)
    }

    @Test fun `two phones merging the same pair write the same file, and merging again changes nothing`() {
        val a = file(listOf(CheckInV1("2026-01-01", 5, 3, "a", updatedAt = 100), CheckInV1("2026-01-02", 1, 1, "tie", updatedAt = 7)), listOf(walk),
            SettingsV1(nightHours = true, nightHoursAt = 5))
        val b = file(listOf(CheckInV1("2026-01-01", 6, 3, "b", updatedAt = 90), CheckInV1("2026-01-02", 2, 1, "tie", updatedAt = 7)), listOf(night),
            SettingsV1(nightHours = false, nightHoursAt = 5))
        val ab = merge(a, b)
        assertTrue(SyncMerge.sameContent(ab, merge(b, a)))
        assertTrue(SyncMerge.sameContent(ab, merge(ab, a)))
        assertTrue(SyncMerge.sameContent(ab, merge(ab, ab)))
        assertTrue(SyncMerge.changesFor(ab, merge(ab, b)).isEmpty)
    }

    @Test fun `a session from a newer Brume is carried, not lost`() {
        val newer = walk.copy(endedBy = "headphones_unplugged")
        val merged = merge(file(sessions = listOf(night)), file(sessions = listOf(newer)))
        assertTrue(newer in merged.sessions)
    }

    @Test fun `the folder's file is the exact name, newest first, never a second one`() {
        val kids = listOf(
            SyncFile.Child("1", "brume-sync (1).json", 900),
            SyncFile.Child("2", "brume-sync.json", 100),
            SyncFile.Child("3", "brume-sync.json", 500),
            SyncFile.Child("4", "brume-sync.json", 999, isDirectory = true),
        )
        assertEquals(SyncFile.Choice.Use("3", 2), SyncFile.choose(kids))
        assertEquals(SyncFile.Choice.Create, SyncFile.choose(kids.take(1)))
    }

    @Test fun `the module switches go together and the later change wins`() {
        val a = file(settings = SettingsV1(modules = mapOf("TONES" to true, "BREATH" to false), modulesAt = 100))
        val b = file(settings = SettingsV1(modules = mapOf("TONES" to false, "BREATH" to true), modulesAt = 200))
        assertEquals(mapOf("TONES" to false, "BREATH" to true), merge(a, b).settings?.modules)
        assertEquals(mapOf("TONES" to false, "BREATH" to true), merge(b, a).settings?.modules)
        assertEquals(mapOf("TONES" to false, "BREATH" to true) to 200L, SyncMerge.changesFor(a, merge(a, b)).modules)
        assertEquals(null, SyncMerge.changesFor(b, merge(a, b)).modules)
        // An older file without modules never switches anything off.
        val old = file(settings = SettingsV1(nightHours = true, nightHoursAt = 1))
        assertEquals(a.settings?.modules, merge(a, old).settings?.modules)
        assertEquals(null, SyncMerge.changesFor(a, merge(a, old)).modules)
    }
}
