package com.lyricstage.lyrics.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** Cached lyrics for a track key. [raw] is LRC text, plain text, or empty for instrumentals. */
@Entity(tableName = "lyrics")
data class LyricsEntity(
    @PrimaryKey val trackKey: String,
    val raw: String,
    val synced: Boolean,
    val instrumental: Boolean,
    /** "lrclib", "netease", "musixmatch" or "import". */
    val source: String,
    val title: String,
    val artist: String,
    val durationMs: Long?,
    val updatedAt: Long,
)

/** Per-track user sync offset in milliseconds. */
@Entity(tableName = "offsets")
data class OffsetEntity(
    @PrimaryKey val trackKey: String,
    val offsetMs: Long,
)

@Dao
interface LyricsDao {
    @Query("SELECT * FROM lyrics WHERE trackKey = :key")
    fun observe(key: String): Flow<LyricsEntity?>

    @Query("SELECT * FROM lyrics WHERE trackKey = :key")
    suspend fun get(key: String): LyricsEntity?

    @Query("SELECT * FROM lyrics ORDER BY updatedAt DESC")
    suspend fun all(): List<LyricsEntity>

    /** Rows for the same artist and title with any duration (keys are `artist|title|seconds`). */
    @Query("SELECT * FROM lyrics WHERE substr(trackKey, 1, length(:prefix)) = :prefix")
    suspend fun withPrefix(prefix: String): List<LyricsEntity>

    @Query("SELECT COUNT(*) FROM lyrics WHERE source != 'import'")
    fun count(): Flow<Int>

    /** Bytes of downloaded lyrics text (imports excluded). */
    @Query("SELECT COALESCE(SUM(LENGTH(raw)), 0) FROM lyrics WHERE source != 'import'")
    fun size(): Flow<Long>

    /** Drops downloaded lyrics; imported files stay. */
    @Query("DELETE FROM lyrics WHERE source != 'import'")
    suspend fun clearFetched()

    @Upsert
    suspend fun upsert(entity: LyricsEntity)
}

@Dao
interface OffsetDao {
    @Query("SELECT offsetMs FROM offsets WHERE trackKey = :key")
    fun observe(key: String): Flow<Long?>

    @Upsert
    suspend fun upsert(entity: OffsetEntity)
}

/** A track no source had lyrics for, so lookups can be skipped for a while. */
@Entity(tableName = "misses")
data class MissEntity(
    @PrimaryKey val trackKey: String,
    val at: Long,
)

@Dao
interface MissDao {
    @Query("SELECT at FROM misses WHERE trackKey = :key")
    suspend fun at(key: String): Long?

    @Upsert
    suspend fun upsert(entity: MissEntity)

    @Query("DELETE FROM misses WHERE trackKey = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM misses")
    suspend fun clear()
}

@Database(entities = [LyricsEntity::class, OffsetEntity::class, MissEntity::class], version = 2, exportSchema = true)
abstract class LyricsDatabase : RoomDatabase() {
    abstract fun lyricsDao(): LyricsDao
    abstract fun offsetDao(): OffsetDao
    abstract fun missDao(): MissDao

    companion object {
        fun create(context: Context): LyricsDatabase =
            Room.databaseBuilder(context, LyricsDatabase::class.java, "lyricstage.db")
                .addMigrations(MIGRATION_1_2)
                .build()

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `misses` (`trackKey` TEXT NOT NULL, `at` INTEGER NOT NULL, PRIMARY KEY(`trackKey`))")
            }
        }
    }
}
