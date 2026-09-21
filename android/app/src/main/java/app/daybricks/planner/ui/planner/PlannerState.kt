package app.daybricks.planner.ui.planner

import app.daybricks.planner.domain.*
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.ZoneId

@Serializable
data class EventDraft(
    val templateId: String,
    val title: String,
    val startMillis: Long,
    val durationMinutes: Int,
    val icon: String? = null,
    val reminderMinutes: Int? = null,
    val description: String = "",
)
@Serializable
data class PlannerSavedState(
    val date: String = LocalDate.now().toString(),
    val viewportMinute: Double = -1.0,
    val zoom: Float = 1.8f,
    val paneOpen: Boolean = false,
    val draft: EventDraft? = null,
    val lastAdded: Map<String, Long> = emptyMap(),
)
data class PlannerUiState(
    val saved: PlannerSavedState = PlannerSavedState(),
    val zone: ZoneId = ZoneId.systemDefault(),
    val events: List<CalendarEvent> = emptyList(),
    val templates: List<BlockTemplate> = emptyList(),
    val calendars: List<CalendarInfo> = emptyList(),
    val displayCalendars: List<CalendarInfo> = emptyList(),
    val device: DevicePreferences = DevicePreferences(),
    val hasPermission: Boolean = false,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val message: UiMessage? = null,
    val sync: SyncStatus = SyncStatus(),
    val presetTemplate: BlockTemplate? = null,
    val settingsOpen: Boolean = false,
    val editorOpen: Boolean = false,
    val editingTemplate: BlockTemplate? = null,
) {
    val date: LocalDate get() = LocalDate.parse(saved.date)
    val draft: EventDraft? get() = saved.draft
}
enum class UiMessage {
    CALENDAR_PERMISSION_REQUIRED,
    COULD_NOT_SAVE,
    SYNC_SETTINGS_SAVED,
    ALLOW_CALENDAR_BEFORE_ADDING,
    CHOOSE_CALENDAR_FOR_BLOCKS,
    BLOCK_NAME_REQUIRED,
    BLOCK_ADDED,
    IMPORT_COMPLETE,
}
sealed interface PlannerAction {
    data object PreviousDay : PlannerAction
    data object NextDay : PlannerAction
    data object ToggleTemplatePane : PlannerAction
    data class SelectTemplate(val template: BlockTemplate) : PlannerAction
    data class SelectPreset(val template: BlockTemplate, val preset: BlockPreset?) : PlannerAction
    data object DismissPresets : PlannerAction
    data class ChangeDraftDuration(val minutes: Int) : PlannerAction
    data class ChangeDraftTitle(val title: String) : PlannerAction
    data class ChangeDraftReminder(val minutes: Int?) : PlannerAction
    data class ChangeViewport(val minute: Double) : PlannerAction
    data class ChangeZoom(val zoom: Float, val anchorMinute: Double? = null) : PlannerAction
    data object ConfirmDraft : PlannerAction
    data object CancelDraft : PlannerAction
    data class PermissionsChanged(val granted: Boolean) : PlannerAction
    data object Refresh : PlannerAction
    data object DismissMessage : PlannerAction
    data class InstallStarterTemplates(val languageTag: String) : PlannerAction
    data class ShowSettings(val show: Boolean) : PlannerAction
    data class SelectCalendar(val id: Long) : PlannerAction
    data class SetCalendarVisible(val id: Long, val visible: Boolean) : PlannerAction
    data class SetPane(val value: PanePreference) : PlannerAction
    data class SetTheme(val value: ThemePreference) : PlannerAction
    data class SetOpenEventsDirectly(val enabled: Boolean) : PlannerAction
    data class SetDefaultReminder(val minutes: Int?) : PlannerAction
    data class EditTemplate(val template: BlockTemplate?) : PlannerAction
    data object CloseEditor : PlannerAction
    data class SaveTemplate(val template: BlockTemplate) : PlannerAction
    data class DeleteTemplate(val id: String) : PlannerAction
    data class DeleteEvent(val event: CalendarEvent) : PlannerAction
    data class MoveTemplate(val id: String, val offset: Int) : PlannerAction
    data class SaveSync(val url: String, val token: String) : PlannerAction
    data object SyncNow : PlannerAction
    data class ImportAppData(val data: app.daybricks.planner.app.AppDataExport, val replace: Boolean) : PlannerAction
}
