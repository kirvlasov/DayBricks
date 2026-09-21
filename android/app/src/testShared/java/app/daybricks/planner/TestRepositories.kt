package app.daybricks.planner

import app.daybricks.planner.domain.*
import kotlinx.coroutines.flow.MutableStateFlow

class TestLocalRepository(initial: List<BlockTemplate> = emptyList()) : TemplateRepository, SettingsRepository {
    override val templates = MutableStateFlow(initial)
    override val device = MutableStateFlow(DevicePreferences(targetCalendarId = 1))
    override suspend fun upsert(template: BlockTemplate) { template.validate(); templates.value = (templates.value.filterNot { it.id == template.id } + template).sortedBy { it.sortOrder } }
    override suspend fun delete(id: String) { templates.value = templates.value.filterNot { it.id == id } }
    override suspend fun reorder(ids: List<String>) { templates.value = ids.mapIndexed { index, id -> templates.value.first { it.id == id }.copy(sortOrder = index * 100L) } }
    override suspend fun updateDevice(update: (DevicePreferences) -> DevicePreferences) { device.value = update(device.value) }
}
class TestSyncRepository : SyncRepository {
    override val status = MutableStateFlow(SyncStatus())
    override suspend fun sync() = Unit
}
class TestCredentialStore : CredentialStore {
    private var token: String? = null
    override suspend fun readToken() = token
    override suspend fun writeToken(token: String?) { this.token = token }
}
