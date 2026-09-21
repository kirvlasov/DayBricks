package app.daybricks.planner.ui.planner

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.daybricks.planner.domain.*
import app.daybricks.planner.sync.StateJson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class PlannerViewModel(
    private val calendar: CalendarRepository,
    private val templates: TemplateRepository,
    private val settings: SettingsRepository,
    private val syncRepository: SyncRepository,
    private val credentials: CredentialStore,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val restored = savedStateHandle.get<String>("planner")?.let { runCatching { StateJson.decodeFromString<PlannerSavedState>(it) }.getOrNull() }
    private val initial = (restored ?: PlannerSavedState()).let { value ->
        if (value.viewportMinute >= 0) value else value.copy(viewportMinute = TimeAxisMapper(LocalDate.now(), ZoneId.systemDefault()).minuteAt(Instant.now()))
    }
    private val mutable = MutableStateFlow(PlannerUiState(saved = initial))
    val state = mutable.asStateFlow()
    private var dayJob: Job? = null
    private var zoomJob: Job? = null
    private var syncJob: Job? = null
    private var deviceLoaded = false
    private var allDayEvents: List<CalendarEvent> = emptyList()

    init {
        viewModelScope.launch { templates.templates.collect { values -> mutable.update { it.copy(templates = values) } } }
        viewModelScope.launch { settings.device.collect { values ->
            mutable.update { it.copy(device = values, events = allDayEvents.filter { event -> event.calendarId !in values.hiddenCalendarIds }) }
            if (!deviceLoaded && restored == null) save { it.copy(zoom = values.zoom.coerceIn(.6f, 5f)) }
            deviceLoaded = true
        } }
        viewModelScope.launch { syncRepository.status.collect { value -> mutable.update { it.copy(sync = value) } } }
    }

    private fun save(update: (PlannerSavedState) -> PlannerSavedState) {
        mutable.update { it.copy(saved = update(it.saved)) }
        savedStateHandle["planner"] = StateJson.encodeToString(state.value.saved)
    }
    private fun launchWork(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: SecurityException) {
            allDayEvents = emptyList()
            mutable.update { it.copy(hasPermission = false, events = emptyList(), message = UiMessage.CALENDAR_PERMISSION_REQUIRED) }
        }
        catch (_: Exception) { mutable.update { it.copy(message = UiMessage.COULD_NOT_SAVE) } }
    }
    private fun scheduleSync() {
        if (syncJob?.isActive == true) return
        syncJob = viewModelScope.launch { delay(800); syncRepository.sync() }
    }
    fun dispatch(action: PlannerAction) {
        val current = state.value
        when (action) {
            PlannerAction.PreviousDay -> changeDay(-1)
            PlannerAction.NextDay -> changeDay(1)
            PlannerAction.ToggleTemplatePane -> save { it.copy(paneOpen = !it.paneOpen) }
            is PlannerAction.SelectTemplate -> if (current.draft == null) {
                if (action.template.presets.isEmpty()) beginDraft(action.template, null)
                else mutable.update { it.copy(presetTemplate = action.template) }
            }
            is PlannerAction.SelectPreset -> beginDraft(action.template, action.preset)
            PlannerAction.DismissPresets -> mutable.update { it.copy(presetTemplate = null) }
            is PlannerAction.ChangeDraftDuration -> if (!current.saving) save { it.copy(draft = it.draft?.copy(durationMinutes = action.minutes.coerceIn(MIN_DURATION, MAX_DURATION))) }
            is PlannerAction.ChangeDraftTitle -> if (!current.saving) save { it.copy(draft = it.draft?.copy(title = action.title.take(200))) }
            is PlannerAction.ChangeDraftReminder -> if (!current.saving && (action.minutes == null || action.minutes in 0..10080)) {
                save { it.copy(draft = it.draft?.copy(reminderMinutes = action.minutes)) }
            }
            is PlannerAction.ChangeViewport -> {
                if (!current.saving) {
                    val axis = TimeAxisMapper(current.date, current.zone)
                    val minute = action.minute.coerceIn(0.0, axis.totalMinutes - 1)
                    save { it.copy(viewportMinute = minute, draft = it.draft?.copy(startMillis = axis.instantAt(axis.snap(minute)).toEpochMilli())) }
                }
            }
            is PlannerAction.ChangeZoom -> {
                save { it.copy(zoom = action.zoom.coerceIn(.6f, 5f), viewportMinute = action.anchorMinute ?: it.viewportMinute) }
                zoomJob?.cancel()
                zoomJob = launchWork { delay(500); settings.updateDevice { it.copy(zoom = state.value.saved.zoom) } }
            }
            PlannerAction.ConfirmDraft -> confirm()
            PlannerAction.CancelDraft -> if (!current.saving) save { it.copy(draft = null) }
            is PlannerAction.PermissionsChanged -> {
                if (!action.granted) allDayEvents = emptyList()
                mutable.update { it.copy(hasPermission = action.granted, events = if (action.granted) it.events else emptyList()) }
                refresh()
            }
            PlannerAction.Refresh -> { refresh(); scheduleSync() }
            PlannerAction.DismissMessage -> mutable.update { it.copy(message = null) }
            is PlannerAction.InstallStarterTemplates -> if (!current.device.starterTemplatesInstalled) launchWork {
                if (state.value.templates.isEmpty()) {
                    StarterTemplates.forLanguage(action.languageTag).forEach { templates.upsert(it) }
                }
                settings.updateDevice { it.copy(starterTemplatesInstalled = true) }
            }
            is PlannerAction.ImportAppData -> launchWork {
                if (action.replace) state.value.templates.forEach { templates.delete(it.id) }
                action.data.templates.forEach { templates.upsert(it) }
                val imported = action.data.preferences.device
                settings.updateDevice { local ->
                    imported.copy(targetCalendarId = local.targetCalendarId, hiddenCalendarIds = local.hiddenCalendarIds)
                }
                mutable.update { it.copy(message = UiMessage.IMPORT_COMPLETE) }
                scheduleSync()
            }
            is PlannerAction.ShowSettings -> mutable.update { it.copy(settingsOpen = action.show) }
            is PlannerAction.SelectCalendar -> launchWork { settings.updateDevice { it.copy(targetCalendarId = action.id) } }
            is PlannerAction.SetCalendarVisible -> launchWork {
                settings.updateDevice { device ->
                    device.copy(hiddenCalendarIds = if (action.visible) device.hiddenCalendarIds - action.id else device.hiddenCalendarIds + action.id)
                }
            }
            is PlannerAction.SetPane -> launchWork { settings.updateDevice { it.copy(pane = action.value) } }
            is PlannerAction.SetTheme -> launchWork { settings.updateDevice { it.copy(theme = action.value) } }
            is PlannerAction.SetOpenEventsDirectly -> launchWork {
                settings.updateDevice { it.copy(openEventsDirectlyInCalendar = action.enabled) }
            }
            is PlannerAction.SetDefaultReminder -> if (action.minutes == null || action.minutes in 0..10080) {
                launchWork { settings.updateDevice { it.copy(defaultReminderMinutes = action.minutes) } }
            }
            is PlannerAction.EditTemplate -> mutable.update { it.copy(editorOpen = true, editingTemplate = action.template) }
            PlannerAction.CloseEditor -> mutable.update { it.copy(editorOpen = false, editingTemplate = null) }
            is PlannerAction.SaveTemplate -> launchWork {
                templates.upsert(action.template)
                mutable.update { it.copy(editorOpen = false, editingTemplate = null) }; scheduleSync()
            }
            is PlannerAction.DeleteTemplate -> launchWork { templates.delete(action.id); scheduleSync() }
            is PlannerAction.DeleteEvent -> launchWork {
                calendar.delete(action.event.id)
                allDayEvents = allDayEvents.filterNot { it.id == action.event.id }
                mutable.update { ui -> ui.copy(events = allDayEvents.filter { it.calendarId !in ui.device.hiddenCalendarIds }) }
                action.event.calendarId?.let { calendarId ->
                    try { calendar.requestSync(calendarId) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { /* The event is already deleted locally. */ }
                }
            }
            is PlannerAction.MoveTemplate -> launchWork {
                val ids = state.value.templates.map { it.id }.toMutableList()
                val index = ids.indexOf(action.id)
                if (index >= 0) { val target = (index + action.offset).coerceIn(0, ids.lastIndex); ids.add(target, ids.removeAt(index)); templates.reorder(ids); scheduleSync() }
            }
            is PlannerAction.SaveSync -> launchWork {
                if (action.token.isNotBlank()) credentials.writeToken(action.token.trim())
                settings.updateDevice { it.copy(serverUrl = action.url.trim().trimEnd('/')) }
                mutable.update { it.copy(message = UiMessage.SYNC_SETTINGS_SAVED) }
                scheduleSync()
            }
            PlannerAction.SyncNow -> scheduleSync()
        }
    }

    private fun changeDay(delta: Long) {
        val current = state.value
        if (current.draft != null) return
        val wallTime = TimeAxisMapper(current.date, current.zone).localTimeAt(current.saved.viewportMinute).toLocalTime()
        val date = current.date.plusDays(delta)
        val axis = TimeAxisMapper(date, current.zone)
        save { it.copy(date = date.toString(), viewportMinute = axis.minuteAt(date.atTime(wallTime).atZone(current.zone).toInstant())) }
        allDayEvents = emptyList()
        mutable.update { it.copy(events = emptyList()) }
        refresh()
    }
    private fun beginDraft(template: BlockTemplate, preset: BlockPreset?) {
        val current = state.value
        if (current.draft != null) return
        val axis = TimeAxisMapper(current.date, current.zone)
        val start = DefaultPlacementPolicy().suggestStart(PlacementContext(current.date, current.zone,
            axis.instantAt(current.saved.viewportMinute), current.saved.lastAdded.mapKeys { LocalDate.parse(it.key) }.mapValues { Instant.ofEpochMilli(it.value) })).toInstant()
        save { it.copy(paneOpen = false, viewportMinute = axis.minuteAt(start), draft = EventDraft(template.id, preset?.title ?: template.title,
            start.toEpochMilli(), preset?.durationMinutes ?: template.defaultDurationMinutes, preset?.icon ?: template.icon,
            template.reminderMinutes, preset?.description?.takeIf { it.isNotBlank() } ?: template.description)) }
        mutable.update { it.copy(presetTemplate = null) }
    }
    private fun refresh() {
        dayJob?.cancel()
        if (!state.value.hasPermission) return
        dayJob = launchWork {
            val current = state.value
            mutable.update { it.copy(loading = true, zone = ZoneId.systemDefault()) }
            try {
                val calendars = calendar.writableCalendars()
                val displayCalendars = calendar.readableCalendars()
                mutable.update { it.copy(calendars = calendars, displayCalendars = displayCalendars) }
                if (calendars.none { it.id == state.value.device.targetCalendarId }) {
                    settings.updateDevice { it.copy(targetCalendarId = calendars.singleOrNull()?.id) }
                }
                calendar.observeDay(current.date, state.value.zone).collect { events ->
                    allDayEvents = events
                    mutable.update { ui ->
                        ui.copy(events = events.filter { event -> event.calendarId !in ui.device.hiddenCalendarIds }, loading = false)
                    }
                }
            } finally { mutable.update { it.copy(loading = false) } }
        }
    }
    private fun confirm() {
        val current = state.value
        val draft = current.draft ?: return
        if (current.saving) return
        if (!current.hasPermission) { mutable.update { it.copy(message = UiMessage.ALLOW_CALENDAR_BEFORE_ADDING) }; return }
        val target = current.device.targetCalendarId
        if (target == null) { mutable.update { it.copy(settingsOpen = true, message = UiMessage.CHOOSE_CALENDAR_FOR_BLOCKS) }; return }
        if (draft.title.isBlank()) { mutable.update { it.copy(message = UiMessage.BLOCK_NAME_REQUIRED) }; return }
        mutable.update { it.copy(saving = true) }
        launchWork {
            try {
                val eventTitle = listOfNotNull(draft.icon?.takeIf { it.isNotBlank() }, draft.title.trim()).joinToString(" ")
                val start = Instant.ofEpochMilli(draft.startMillis)
                val eventId = calendar.create(NewCalendarEvent(target, eventTitle, start, draft.durationMinutes,
                    current.zone, draft.reminderMinutes, draft.description))
                val createdEvent = CalendarEvent(eventId, eventTitle, start,
                    start.plusSeconds(draft.durationMinutes * 60L), owned = true, calendarId = target,
                    description = draft.description, timeZone = current.zone.id)
                save { it.copy(draft = null, lastAdded = it.lastAdded + (current.saved.date to (draft.startMillis + draft.durationMinutes * 60000L))) }
                allDayEvents = allDayEvents.filterNot { it.id == eventId } + createdEvent
                mutable.update { it.copy(message = UiMessage.BLOCK_ADDED,
                    events = allDayEvents.filter { event -> event.calendarId !in it.device.hiddenCalendarIds }) }
                // Refresh from the provider even if it does not notify observers for this insert.
                refresh()
                // Upload is independent of displaying the locally saved event.
                try { calendar.requestSync(target) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { /* The event is already saved locally. */ }
            } finally { mutable.update { it.copy(saving = false) } }
        }
    }
}
