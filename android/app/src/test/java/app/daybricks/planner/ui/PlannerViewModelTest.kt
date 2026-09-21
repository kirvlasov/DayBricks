package app.daybricks.planner.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import app.daybricks.planner.*
import app.daybricks.planner.calendar.FakeCalendarRepository
import app.daybricks.planner.domain.*
import app.daybricks.planner.ui.planner.*
import app.daybricks.planner.app.AppDataExport
import app.daybricks.planner.app.ExportedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class PlannerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val template = BlockTemplate(title = "Training", icon = "🏃", defaultDurationMinutes = 16, reminderMinutes = 15)
    private val calendar = FakeCalendarRepository()
    private val local = TestLocalRepository(listOf(template))
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }
    private fun vm(handle: SavedStateHandle = SavedStateHandle()): PlannerViewModel = PlannerViewModel(calendar, local, local, TestSyncRepository(), TestCredentialStore(), handle).also { store.put("${System.identityHashCode(it)}", it) }

    @Test fun confirmCreatesOneEventAndCancelCreatesNone() = runTest(dispatcher) {
        val model = vm(); model.dispatch(PlannerAction.PermissionsChanged(true)); runCurrent()
        model.dispatch(PlannerAction.SelectTemplate(template)); model.dispatch(PlannerAction.ChangeViewport(600.0)); model.dispatch(PlannerAction.ChangeDraftDuration(18))
        val expected = model.state.value.draft!!
        model.dispatch(PlannerAction.ConfirmDraft); model.dispatch(PlannerAction.ConfirmDraft)
        runCurrent()
        assertEquals(1, calendar.events.value.size)
        val event = calendar.events.value.single()
        assertEquals(expected.startMillis, event.start.toEpochMilli())
        assertEquals(18 * 60L, java.time.Duration.between(event.start, event.end).seconds)
        assertEquals("🏃 Training", event.title)
        assertEquals(15, calendar.created.single().reminderMinutes)
        assertEquals(listOf(1L), calendar.syncRequests)
        assertTrue(event.owned); assertNull(model.state.value.draft)
        assertEquals(event.id, model.state.value.events.single().id)
        model.dispatch(PlannerAction.SelectTemplate(template)); model.dispatch(PlannerAction.CancelDraft)
        assertEquals(1, calendar.events.value.size)
    }
    @Test fun reminderCanBeOverriddenForOneEvent() = runTest(dispatcher) {
        val model = vm(); model.dispatch(PlannerAction.PermissionsChanged(true)); runCurrent()
        model.dispatch(PlannerAction.SelectTemplate(template))
        assertEquals(15, model.state.value.draft?.reminderMinutes)
        model.dispatch(PlannerAction.ChangeDraftReminder(5))
        model.dispatch(PlannerAction.ConfirmDraft); runCurrent()
        assertEquals(5, calendar.created.single().reminderMinutes)
    }
    @Test fun presetOverridesEmojiDurationAndDescription() = runTest(dispatcher) {
        val preset = BlockPreset(title = "Mobility", durationMinutes = 25, icon = "🤸", description = "Move gently")
        val described = template.copy(description = "Template instructions", presets = listOf(preset))
        local.upsert(described)
        val model = vm(); model.dispatch(PlannerAction.PermissionsChanged(true)); runCurrent()
        model.dispatch(PlannerAction.SelectPreset(described, preset))
        val draft = model.state.value.draft!!
        assertEquals("Mobility", draft.title)
        assertEquals(25, draft.durationMinutes)
        assertEquals("Move gently", draft.description)
        model.dispatch(PlannerAction.ConfirmDraft); runCurrent()
        assertEquals("🤸 Mobility", calendar.created.single().title)
        assertEquals("Move gently", calendar.created.single().description)
    }
    @Test fun insertFailurePreservesDraftAndAllowsRetry() = runTest(dispatcher) {
        val model = vm(); model.dispatch(PlannerAction.PermissionsChanged(true)); runCurrent()
        model.dispatch(PlannerAction.SelectTemplate(template)); val expected = model.state.value.draft
        calendar.failInsert = true; model.dispatch(PlannerAction.ConfirmDraft); runCurrent()
        assertEquals(expected, model.state.value.draft); assertFalse(model.state.value.saving)
        calendar.failInsert = false; model.dispatch(PlannerAction.ConfirmDraft); runCurrent()
        assertEquals(1, calendar.events.value.size)
    }
    @Test fun savedStateRestoresDraftDateZoomAndViewport() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val model = vm(saved); runCurrent()
        model.dispatch(PlannerAction.NextDay); model.dispatch(PlannerAction.ChangeZoom(3f)); model.dispatch(PlannerAction.ChangeViewport(800.0))
        model.dispatch(PlannerAction.SelectTemplate(template)); model.dispatch(PlannerAction.ChangeDraftTitle("Yoga")); model.dispatch(PlannerAction.ChangeDraftDuration(23))
        val fresh = vm(SavedStateHandle(mapOf("planner" to saved.get<String>("planner"))))
        runCurrent()
        assertEquals(model.state.value.saved, fresh.state.value.saved)
    }
    @Test fun dateNavigationPreservesViewportAndDoesNotWorkDuringPlacement() = runTest(dispatcher) {
        val model = vm(); runCurrent()
        model.dispatch(PlannerAction.ChangeViewport(1080.0)); val date = model.state.value.date
        model.dispatch(PlannerAction.NextDay)
        assertEquals(date.plusDays(1), model.state.value.date)
        assertEquals(1080.0, model.state.value.saved.viewportMinute, .001)
        model.dispatch(PlannerAction.SelectTemplate(template)); model.dispatch(PlannerAction.NextDay)
        assertEquals(date.plusDays(1), model.state.value.date)
    }
    @Test fun deletingTemplateDoesNotDeleteCalendarEvent() = runTest(dispatcher) {
        val model = vm(); model.dispatch(PlannerAction.PermissionsChanged(true)); runCurrent()
        model.dispatch(PlannerAction.SelectTemplate(template)); model.dispatch(PlannerAction.ConfirmDraft); runCurrent()
        model.dispatch(PlannerAction.DeleteTemplate(template.id)); runCurrent()
        assertTrue(local.templates.value.isEmpty()); assertEquals(1, calendar.events.value.size)
    }
    @Test fun starterTemplatesAreInstalledOnlyOnce() = runTest(dispatcher) {
        val emptyLocal = TestLocalRepository()
        val model = PlannerViewModel(calendar, emptyLocal, emptyLocal, TestSyncRepository(), TestCredentialStore(), SavedStateHandle())
        store.put("starters", model)
        runCurrent()

        model.dispatch(PlannerAction.InstallStarterTemplates("en")); runCurrent()
        assertEquals(4, emptyLocal.templates.value.size)
        assertTrue(emptyLocal.device.value.starterTemplatesInstalled)

        model.dispatch(PlannerAction.InstallStarterTemplates("en")); runCurrent()
        assertEquals(4, emptyLocal.templates.value.size)
    }
    @Test fun importCanMergeOrReplaceWithoutChangingCalendarSelection() = runTest(dispatcher) {
        val model = vm(); runCurrent()
        val imported = BlockTemplate(title = "Imported", defaultDurationMinutes = 20)
        val data = AppDataExport(
            exportedAt = "2026-09-20T12:00:00Z", appVersion = "0.4.1",
            templates = listOf(imported),
            preferences = ExportedPreferences(
                SyncedPreferences(),
                DevicePreferences(targetCalendarId = 99, theme = ThemePreference.DARK),
            ),
        )

        model.dispatch(PlannerAction.ImportAppData(data, replace = false)); runCurrent()
        assertEquals(setOf(template.id, imported.id), local.templates.value.map { it.id }.toSet())
        assertEquals(1L, local.device.value.targetCalendarId)
        assertEquals(ThemePreference.DARK, local.device.value.theme)

        model.dispatch(PlannerAction.ImportAppData(data, replace = true)); runCurrent()
        assertEquals(listOf(imported.id), local.templates.value.map { it.id })
    }
    @Test fun calendarDisplayFilterIsLocalAndReversible() = runTest(dispatcher) {
        val zone = ZoneId.systemDefault()
        val start = LocalDate.now().atTime(10, 0).atZone(zone).toInstant()
        calendar.calendars = listOf(CalendarInfo(1, "Work", "Local"), CalendarInfo(2, "Personal", "Local"))
        calendar.events.value = listOf(
            CalendarEvent(10, "Work event", start, start.plusSeconds(1800), calendarId = 1),
            CalendarEvent(20, "Personal event", start, start.plusSeconds(1800), calendarId = 2),
        )
        val model = vm()
        model.dispatch(PlannerAction.PermissionsChanged(true)); runCurrent()
        assertEquals(setOf(1L, 2L), model.state.value.events.mapNotNull { it.calendarId }.toSet())

        model.dispatch(PlannerAction.SetCalendarVisible(2, false)); runCurrent()
        assertEquals(setOf(1L), model.state.value.events.mapNotNull { it.calendarId }.toSet())
        assertEquals(setOf(2L), local.device.value.hiddenCalendarIds)

        model.dispatch(PlannerAction.SetCalendarVisible(2, true)); runCurrent()
        assertEquals(setOf(1L, 2L), model.state.value.events.mapNotNull { it.calendarId }.toSet())
        assertTrue(local.device.value.hiddenCalendarIds.isEmpty())
    }
}
