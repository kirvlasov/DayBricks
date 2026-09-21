package app.daybricks.planner.storage

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "templates")
data class TemplateEntity(@PrimaryKey val id: String, val sortOrder: Long, val json: String)
@Entity(tableName = "preferences")
data class PreferenceEntity(@PrimaryKey val key: String, val json: String)
@Entity(tableName = "outbox")
data class OutboxEntity(@PrimaryKey(autoGenerate = true) val sequence: Long = 0, val json: String)
@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(@PrimaryKey val key: String = "state", val etag: String, val lastSuccess: Long)

@Dao
interface StateDao {
    @Query("SELECT * FROM templates ORDER BY sortOrder, id") fun observeTemplates(): Flow<List<TemplateEntity>>
    @Query("SELECT * FROM templates ORDER BY sortOrder, id") suspend fun templates(): List<TemplateEntity>
    @Upsert suspend fun upsertTemplate(value: TemplateEntity)
    @Query("DELETE FROM templates WHERE id = :id") suspend fun deleteTemplate(id: String)
    @Query("DELETE FROM templates") suspend fun clearTemplates()
    @Query("SELECT * FROM preferences WHERE `key` = :key") fun observePreference(key: String): Flow<PreferenceEntity?>
    @Query("SELECT * FROM preferences WHERE `key` = :key") suspend fun preference(key: String): PreferenceEntity?
    @Upsert suspend fun preference(value: PreferenceEntity)
    @Insert suspend fun enqueue(value: OutboxEntity)
    @Query("SELECT * FROM outbox ORDER BY sequence") suspend fun outbox(): List<OutboxEntity>
    @Query("DELETE FROM outbox WHERE sequence IN (:sequences)") suspend fun acknowledge(sequences: List<Long>)
    @Upsert suspend fun metadata(value: SyncMetadataEntity)
    @Query("SELECT * FROM sync_metadata WHERE `key` = 'state'") suspend fun metadata(): SyncMetadataEntity?
}

@Database(entities = [TemplateEntity::class, PreferenceEntity::class, OutboxEntity::class, SyncMetadataEntity::class], version = 1, exportSchema = true)
abstract class DayBricksDatabase : RoomDatabase() { abstract fun state(): StateDao }
