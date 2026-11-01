package app.beansontoast.brume

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.beansontoast.brume.core.finder.Character
import app.beansontoast.brume.core.finder.PitchMatch
import app.beansontoast.brume.core.finder.Rhythm
import app.beansontoast.brume.core.finder.Side
import app.beansontoast.brume.core.finder.TinnitusProfile
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
            .addMigrations(BrumeDatabase.MIGRATION_1_2).allowMainThreadQueries().build()
        val rows = runBlocking { Journal(db.journal()).checkIns.first() }
        assertEquals(listOf(CheckIn("2026-01-01", 6, 4, "Invented")), rows)
        db.close()
    }

    @Test fun `version 1 migrates to 2, keeping every journal row and adding profiles`() {
        helper.createDatabase(NAME, 1).apply {
            execSQL("INSERT INTO check_ins (day, loudness, bother, note, updatedAt) VALUES ('2026-01-01', 6, 4, 'Invented', 0)")
            execSQL("INSERT INTO sessions (startedAt, seconds, noise, noiseLevel, ambience, ambienceLevel, endedBy) VALUES (1, 60, 'PINK', 0.3, NULL, NULL, 'USER')")
            close()
        }
        helper.runMigrationsAndValidate(NAME, 2, true, BrumeDatabase.MIGRATION_1_2).close()
        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), BrumeDatabase::class.java, NAME)
            .addMigrations(BrumeDatabase.MIGRATION_1_2).allowMainThreadQueries().build()
        runBlocking {
            val journal = Journal(db.journal())
            assertEquals(listOf(CheckIn("2026-01-01", 6, 4, "Invented")), journal.checkIns.first())
            assertEquals(1, db.journal().allSessions().size)
            assertEquals(emptyList<TinnitusProfile>(), journal.profiles.first())
        }
        db.close()
    }

    @Test fun `profiles are kept, exported with their searches, and a removal leaves only a tombstone`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), BrumeDatabase::class.java)
            .allowMainThreadQueries().build()
        val journal = Journal(db.journal())
        val profile = TinnitusProfile("p1", "Invented whistle", Character.TONE, Side.BOTH, Rhythm.STEADY, 4, null,
            listOf(PitchMatch("m1", "2026-01-03", 4000, Character.TONE, true)))
        journal.saveProfile(profile)
        journal.saveProfile(profile.copy(id = "p2", name = "Invented hiss", matches = emptyList()))
        assertEquals(setOf("p1", "p2"), journal.profiles.first().map { it.id }.toSet())
        assertEquals(profile.matches, journal.profiles.first().first { it.id == "p1" }.matches)
        val json = journal.exportJson("test")
        assertTrue(json, "Invented whistle" in json && "\"hz\": 4000" in json && "\"octaveChecked\": true" in json)
        journal.deleteProfile("p1")
        assertEquals(listOf("p2"), journal.profiles.first().map { it.id })
        val after = journal.exportJson("test")
        assertTrue(after, "Invented whistle" !in after && "\"deleted\": true" in after)
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
