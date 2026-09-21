package app.daybricks.planner.storage

import androidx.room.withTransaction
import app.daybricks.planner.domain.*
import app.daybricks.planner.sync.*
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString

class LocalStateRepository(private val db: DayBricksDatabase) : TemplateRepository, SettingsRepository, SyncLocalStore {
    private val dao = db.state()
    override val templates = dao.observeTemplates().map { rows -> rows.map { StateJson.decodeFromString<BlockTemplate>(it.json) } }
    override val device = dao.observePreference("device").map { it?.let { StateJson.decodeFromString<DevicePreferences>(it.json) } ?: DevicePreferences() }

    private suspend fun enqueue(op: SyncOperation) = dao.enqueue(OutboxEntity(json = StateJson.encodeToString(op)))
    private suspend fun store(template: BlockTemplate) = dao.upsertTemplate(TemplateEntity(template.id, template.sortOrder, StateJson.encodeToString(template)))
    override suspend fun upsert(template: BlockTemplate) {
        template.validate()
        db.withTransaction { store(template); enqueue(SyncOperation.UpsertTemplate(template)) }
    }
    override suspend fun delete(id: String) = db.withTransaction { dao.deleteTemplate(id); enqueue(SyncOperation.DeleteTemplate(id)) }
    override suspend fun reorder(ids: List<String>) = db.withTransaction {
        val current = dao.templates().map { StateJson.decodeFromString<BlockTemplate>(it.json) }.associateBy { it.id }
        require(ids.size == current.size && ids.toSet() == current.keys)
        ids.forEachIndexed { index, id ->
            val template = current.getValue(id).copy(sortOrder = index * 100L)
            store(template); enqueue(SyncOperation.UpsertTemplate(template))
        }
    }
    override suspend fun updateDevice(update: (DevicePreferences) -> DevicePreferences) = db.withTransaction {
        val current = dao.preference("device")?.let { StateJson.decodeFromString<DevicePreferences>(it.json) } ?: DevicePreferences()
        dao.preference(PreferenceEntity("device", StateJson.encodeToString(update(current))))
    }
    override suspend fun pending(): List<PendingOperation> = db.withTransaction {
        dao.outbox().map { PendingOperation(it.sequence, StateJson.decodeFromString(it.json)) }
    }
    override suspend fun accept(state: SyncState, etag: String, sent: List<Long>) = db.withTransaction {
        state.validated()
        // Preserve edits made locally while the HTTP request was in flight.
        dao.acknowledge(sent)
        val merged = state.applyOperations(dao.outbox().map { StateJson.decodeFromString<SyncOperation>(it.json) })
        dao.clearTemplates()
        merged.templates.forEach { store(it) }
        dao.preference(PreferenceEntity("synced", StateJson.encodeToString(merged.preferences)))
        dao.metadata(SyncMetadataEntity(etag = etag, lastSuccess = System.currentTimeMillis()))
    }
}
