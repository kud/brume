package app.beansontoast.brume

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.beansontoast.brume.core.journal.CheckIn
import app.beansontoast.brume.core.journal.EndedBy
import app.beansontoast.brume.core.journal.ListeningSession
import app.beansontoast.brume.data.BrumeDatabase
import app.beansontoast.brume.data.Journal
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Invented data only. The schema test is the start of the upgrade chain: each new version adds a step. */
@RunWith(RobolectricTestRunner::class)
class DatabaseTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), BrumeDatabase::class.java)

    @Test fun `version 1 from the committed schema opens with today's code and keeps its rows`() {
        helper.createDatabase(NAME, 1).apply {
            execSQL("INSERT INTO check_ins (day, loudness, bother, note, updatedAt) VALUES ('2026-01-01', 6, 4, 'Invented', 0)")
            close()
        }
        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), BrumeDatabase::class.java, NAME)
            .allowMainThreadQueries().build()
        val rows = runBlocking { Journal(db.journal()).checkIns.first() }
        assertEquals(listOf(CheckIn("2026-01-01", 6, 4, "Invented")), rows)
        db.close()
    }

    @Test fun `a second check-in the same day replaces the first, and export carries both tables`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), BrumeDatabase::class.java)
            .allowMainThreadQueries().build()
        val journal = Journal(db.journal())
        journal.save(CheckIn("2026-01-02", 5, 3))
        journal.save(CheckIn("2026-01-02", 4, 2, "Later"))
        journal.log(ListeningSession(1_767_312_000_000, 1200, "PINK", 0.3f, null, null, EndedBy.TIMER))
        assertEquals(listOf(CheckIn("2026-01-02", 4, 2, "Later")), journal.checkIns.first())
        val json = journal.exportJson("test")
        assertTrue(json, "\"schemaVersion\": 1" in json && "\"endedBy\": \"timer\"" in json && "Later" in json)
        db.close()
    }

    private companion object { const val NAME = "migration-test.db" }
}
