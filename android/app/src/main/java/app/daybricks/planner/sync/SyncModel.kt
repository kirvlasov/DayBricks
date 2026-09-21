package app.daybricks.planner.sync

import app.daybricks.planner.domain.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val StateJson = Json { encodeDefaults = true; ignoreUnknownKeys = true }

@Serializable
sealed interface SyncOperation {
    @Serializable data class UpsertTemplate(val template: BlockTemplate) : SyncOperation
    @Serializable data class DeleteTemplate(val id: String) : SyncOperation
    // Decode and discard old queued operations from installs that offered this setting.
    @Serializable data class SetDominantHand(val value: DominantHand) : SyncOperation
}

fun SyncState.applyOperations(operations: List<SyncOperation>): SyncState = operations.fold(this) { state, op ->
    when (op) {
        is SyncOperation.UpsertTemplate -> state.copy(templates = state.templates.filterNot { it.id == op.template.id } + op.template)
        is SyncOperation.DeleteTemplate -> state.copy(templates = state.templates.filterNot { it.id == op.id })
        is SyncOperation.SetDominantHand -> state
    }
}.let { it.copy(
    schemaVersion = SYNC_SCHEMA_VERSION,
    templates = it.templates.sortedWith(compareBy<BlockTemplate> { t -> t.sortOrder }.thenBy { t -> t.id }),
) }.validated()
