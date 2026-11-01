package app.beansontoast.brume.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** One row per day; a second check-in the same day replaces the first. */
@Entity(tableName = "check_ins")
data class CheckInEntity(
    @PrimaryKey val day: String,
    val loudness: Int,
    val bother: Int,
    val note: String,
    val updatedAt: Long,
)

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val seconds: Long,
    val noise: String?,
    val noiseLevel: Float?,
    val ambience: String?,
    val ambienceLevel: Float?,
    val endedBy: String,
)

/**
 * A tinnitus profile from « Mon acouphène ». Health data, on this phone only (and the person's own
 * export and sync folder). Matches are a JSON list in the export's own shape. A removed profile keeps
 * only its id and time ([deleted]), so sync never brings it back.
 */
@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val character: String?,
    val side: String?,
    val rhythm: String?,
    val loudness: Int?,
    val bother: Int?,
    val matches: String,
    val updatedAt: Long,
    val deleted: Boolean,
)

@Dao
interface JournalDao {
    @Query("SELECT * FROM check_ins ORDER BY day DESC")
    fun checkIns(): Flow<List<CheckInEntity>>

    @Query("SELECT * FROM check_ins ORDER BY day")
    suspend fun allCheckIns(): List<CheckInEntity>

    @Upsert
    suspend fun upsert(checkIn: CheckInEntity)

    @Insert
    suspend fun insert(session: SessionEntity)

    @Insert
    suspend fun insertAll(sessions: List<SessionEntity>)

    @Query("SELECT COALESCE(SUM(seconds), 0) FROM sessions WHERE startedAt >= :since")
    fun secondsSince(since: Long): Flow<Long>

    @Query("SELECT * FROM sessions ORDER BY startedAt")
    suspend fun allSessions(): List<SessionEntity>

    @Query("SELECT * FROM profiles WHERE deleted = 0 ORDER BY updatedAt DESC")
    fun profiles(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles ORDER BY id")
    suspend fun allProfiles(): List<ProfileEntity>

    @Upsert
    suspend fun upsertProfile(profile: ProfileEntity)
}

/**
 * Schemas are exported to app/schemas and committed; every version step gets a migration test
 * (stored data survives upgrades). Excluded from cloud backup by the manifest's backup rules.
 */
@Database(entities = [CheckInEntity::class, SessionEntity::class, ProfileEntity::class], version = 2, exportSchema = true)
abstract class BrumeDatabase : RoomDatabase() {
    abstract fun journal(): JournalDao

    companion object {
        const val NAME = "brume.db"
        fun open(context: Context): BrumeDatabase =
            Room.databaseBuilder(context, BrumeDatabase::class.java, NAME).addMigrations(MIGRATION_1_2).build()

        /** 2026-11-01: « Mon acouphène » profiles. Additive: every journal row stays as it was. */
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `profiles` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `character` TEXT, " +
                        "`side` TEXT, `rhythm` TEXT, `loudness` INTEGER, `bother` INTEGER, `matches` TEXT NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, `deleted` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
            }
        }
    }
}
