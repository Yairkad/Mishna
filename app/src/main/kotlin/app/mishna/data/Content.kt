package app.mishna.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

// These entities must match the tables written by tools/build_content.py exactly.

@Entity(tableName = "masechet")
data class MasechetEntity(
    @androidx.room.PrimaryKey val id: Int,
    val seder: Int,
    val name: String,
    val firstIndex: Int,
    val count: Int,
)

@Entity(tableName = "mishna")
data class MishnaEntity(
    @androidx.room.PrimaryKey val globalIndex: Int,
    val masechetId: Int,
    val perek: Int,
    val mishna: Int,
    val text: String,
)

@Entity(tableName = "commentary", primaryKeys = ["globalIndex", "source"])
data class CommentaryEntity(
    val globalIndex: Int,
    val source: Int,
    val text: String,
)

object CommentarySource {
    const val BARTENURA = 1
    const val IKAR_TOSAFOT_YOM_TOV = 2
}

@Dao
interface ContentDao {
    @Query("SELECT * FROM mishna WHERE globalIndex >= :start AND globalIndex < :start + :count ORDER BY globalIndex")
    suspend fun mishnayot(start: Int, count: Int): List<MishnaEntity>

    @Query("SELECT * FROM commentary WHERE globalIndex >= :start AND globalIndex < :start + :count")
    suspend fun commentaries(start: Int, count: Int): List<CommentaryEntity>
}

@Database(
    entities = [MasechetEntity::class, MishnaEntity::class, CommentaryEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class ContentDatabase : RoomDatabase() {
    abstract fun dao(): ContentDao

    companion object {
        @Volatile private var instance: ContentDatabase? = null

        fun get(context: Context): ContentDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, ContentDatabase::class.java, "content.db")
                .createFromAsset("content/mishna.db")
                // Content is read-only: a new app version replaces it wholesale.
                .fallbackToDestructiveMigration()
                .build()
                .also { instance = it }
        }
    }
}
