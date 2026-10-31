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

    @Query("SELECT COALESCE(SUM(seconds), 0) FROM sessions WHERE startedAt >= :since")
    fun secondsSince(since: Long): Flow<Long>

    @Query("SELECT * FROM sessions ORDER BY startedAt")
    suspend fun allSessions(): List<SessionEntity>
}

/**
 * Schemas are exported to app/schemas and committed; every version step gets a migration test
 * (stored data survives upgrades). Excluded from cloud backup by the manifest's backup rules.
 */
@Database(entities = [CheckInEntity::class, SessionEntity::class], version = 1, exportSchema = true)
abstract class BrumeDatabase : RoomDatabase() {
    abstract fun journal(): JournalDao

    companion object {
        const val NAME = "brume.db"
        fun open(context: Context): BrumeDatabase =
            Room.databaseBuilder(context, BrumeDatabase::class.java, NAME).build()
    }
}
