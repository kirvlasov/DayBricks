package app.daybricks.planner

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModel
import app.daybricks.planner.calendar.FakeCalendarRepository
import app.daybricks.planner.domain.*
import app.daybricks.planner.ui.DayBricksTheme
import app.daybricks.planner.ui.planner.*
import app.daybricks.planner.ui.templates.TemplateEditor
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PlannerUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val template = BlockTemplate(title = "Training", defaultDurationMinutes = 16)
    private val calendar = FakeCalendarRepository()
    private val local = TestLocalRepository(listOf(template))
    private lateinit var model: PlannerViewModel
    private var width = 360
    private var height = 800
    private var fontScale = 1f
    private fun start(w: Int = 360, h: Int = 800, font: Float = 1f, rtl: Boolean = false) {
        width = w; height = h; fontScale = font
        compose.activityRule.scenario.onActivity { activity -> bind(activity, rtl) }
        compose.waitForIdle()
    }
    private fun bind(activity: ComponentActivity, rtl: Boolean = false) {
        model = ViewModelProvider(activity, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = PlannerViewModel(calendar, local, local, TestSyncRepository(), TestCredentialStore(), SavedStateHandle()) as T
        })[PlannerViewModel::class.java]
        model.dispatch(PlannerAction.PermissionsChanged(true))
        activity.setContent {
            val state by model.state.collectAsState()
            // Render the specified dp window inside the emulator's physical bounds.
            val native = LocalDensity.current
            val scaled = minOf(native.density, activity.resources.displayMetrics.widthPixels / width.toFloat(), activity.resources.displayMetrics.heightPixels / height.toFloat())
            CompositionLocalProvider(
                LocalDensity provides Density(scaled, fontScale),
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                DayBricksTheme(dynamic = false) { Box(Modifier.requiredSize(width.dp, height.dp)) { PlannerScreen(state, model::dispatch) } }
            }
        }
    }
    private fun select() {
        compose.onNodeWithTag("toggle-templates").performClick()
        compose.onNodeWithTag("template-${template.id}").performClick()
        compose.onNodeWithTag("draft").assertExists()
    }
    @Test fun openAndCloseBottomPane() {
        local.device.value = local.device.value.copy(pane = PanePreference.BOTTOM)
        start(); compose.onNodeWithTag("toggle-templates").performClick()
        compose.onNodeWithTag("bottom-pane").assertExists()
        compose.onNodeWithTag("toggle-templates").assertDoesNotExist()
        compose.onNodeWithContentDescription("Close templates").performClick()
        compose.onNodeWithTag("bottom-pane").assertDoesNotExist()
    }
    @Test fun bottomPaneOpensBySwipingUpOnNavigationDock() {
        local.device.value = local.device.value.copy(pane = PanePreference.BOTTOM)
        start()
        compose.onNodeWithTag("toggle-templates").performTouchInput { swipeUp() }
        compose.onNodeWithTag("bottom-pane").assertExists()
    }
    @Test fun bottomPaneClosesBySwipingDownOnTemplateList() {
        local.device.value = local.device.value.copy(pane = PanePreference.BOTTOM)
        start(); compose.onNodeWithTag("toggle-templates").performClick()
        compose.onNodeWithTag("template-list").performTouchInput { swipeDown() }
        compose.onNodeWithTag("bottom-pane").assertDoesNotExist()
    }
    @Test fun bottomPaneHandleResizesFromThirtyToFullHeight() {
        local.device.value = local.device.value.copy(pane = PanePreference.BOTTOM)
        start(); compose.onNodeWithTag("toggle-templates").performClick()
        val handle = compose.onNodeWithContentDescription("Resize template panel")
        handle.performSemanticsAction(SemanticsActions.SetProgress) { it(1f) }
        handle.assertRangeInfoEquals(androidx.compose.ui.semantics.ProgressBarRangeInfo(1f, .30f..1f))
        handle.performSemanticsAction(SemanticsActions.SetProgress) { it(.30f) }
        handle.assertRangeInfoEquals(androidx.compose.ui.semantics.ProgressBarRangeInfo(.30f, .30f..1f))
    }
    @Test fun bottomPaneHandleContinuesFromLastHeightOnSecondDrag() {
        local.device.value = local.device.value.copy(pane = PanePreference.BOTTOM)
        start(); compose.onNodeWithTag("toggle-templates").performClick()
        val handle = compose.onNodeWithContentDescription("Resize template panel")
        fun paneHeight() = compose.onNodeWithTag("bottom-pane").fetchSemanticsNode().boundsInRoot.height
        val initial = paneHeight()
        handle.performTouchInput { swipe(center, center.copy(y = center.y - 70f), 250) }
        compose.waitForIdle()
        val afterFirst = paneHeight()
        handle.performTouchInput { swipe(center, center.copy(y = center.y - 70f), 250) }
        compose.waitForIdle()
        val afterSecond = paneHeight()
        assertTrue(afterFirst > initial)
        assertTrue(afterSecond > afterFirst)
    }
    @Test fun selectChangeDurationConfirm() {
        start(); select()
        compose.onNodeWithTag("duration-ruler").performSemanticsAction(SemanticsActions.SetProgress) { it(23f) }
        compose.onNodeWithContentDescription("Confirm activity").performClick()
        compose.waitUntil { calendar.events.value.size == 1 }
        assertEquals(23 * 60L, java.time.Duration.between(calendar.events.value.single().start, calendar.events.value.single().end).seconds)
        compose.onNodeWithTag("draft").assertDoesNotExist()
    }
    @Test fun cancelDoesNotInsert() { start(); select(); compose.onNodeWithTag("cancel-draft").performClick(); assertTrue(calendar.events.value.isEmpty()) }
    @Test fun tappingEventShowsDetailsInsideDayBricks() {
        val eventStart = java.time.LocalDate.now().atTime(10, 0).atZone(java.time.ZoneId.systemDefault()).toInstant()
        calendar.events.value = listOf(CalendarEvent(9, "Immediate event", eventStart,
            eventStart.plusSeconds(1800), calendarId = 1, description = "Available immediately",
            location = "Conference room", timeZone = "Europe/Berlin"))
        start()
        compose.onNode(hasContentDescription("Immediate event", substring = true)).performClick()
        compose.onNodeWithText("Description").assertExists()
        compose.onNodeWithText("Conference room").assertExists()
        compose.onNodeWithText("Europe/Berlin", substring = true).assertExists()
        compose.onNodeWithContentDescription("Delete").assertExists()
        compose.onNodeWithContentDescription("Open in calendar app").assertExists()
        compose.onNodeWithText("Close").assertExists()
        val deleteX = compose.onNodeWithContentDescription("Delete").fetchSemanticsNode().boundsInRoot.center.x
        val calendarX = compose.onNodeWithContentDescription("Open in calendar app").fetchSemanticsNode().boundsInRoot.center.x
        val closeX = compose.onNodeWithText("Close").fetchSemanticsNode().boundsInRoot.center.x
        assertTrue(deleteX < calendarX && calendarX < closeX)
        compose.onNodeWithText("Test calendar", substring = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("Delete").performClick()
        compose.onNodeWithText("Delete event “Immediate event”?").assertExists()
        compose.onNodeWithTag("confirm-delete-event").performClick()
        compose.waitUntil { calendar.events.value.isEmpty() }
    }
    @Test fun rtlKeepsEventActionsInPlace() {
        val eventStart = java.time.LocalDate.now().atTime(10, 0).atZone(java.time.ZoneId.systemDefault()).toInstant()
        calendar.events.value = listOf(CalendarEvent(11, "Left hand event", eventStart,
            eventStart.plusSeconds(1800), calendarId = 1))
        start(rtl = true)
        compose.onNode(hasContentDescription("Left hand event", substring = true)).performClick()
        val deleteX = compose.onNodeWithContentDescription("Delete").fetchSemanticsNode().boundsInRoot.center.x
        val calendarX = compose.onNodeWithContentDescription("Open in calendar app").fetchSemanticsNode().boundsInRoot.center.x
        val closeX = compose.onNodeWithText("Close").fetchSemanticsNode().boundsInRoot.center.x
        assertTrue(deleteX < calendarX && calendarX < closeX)
    }
    @Test fun directOpenSettingSkipsEventDetails() {
        val eventStart = java.time.LocalDate.now().atTime(10, 0).atZone(java.time.ZoneId.systemDefault()).toInstant()
        calendar.events.value = listOf(CalendarEvent(10, "Direct event", eventStart, eventStart.plusSeconds(1800), calendarId = 1))
        local.device.value = local.device.value.copy(openEventsDirectlyInCalendar = true)
        start()
        compose.onNode(hasContentDescription("Direct event", substring = true)).performClick()
        compose.onNodeWithContentDescription("Open in calendar app").assertDoesNotExist()
    }
    @Test fun previousAndNextDay() {
        start(); val date = model.state.value.date
        compose.onNodeWithTag("next-day").performClick(); compose.runOnIdle { assertEquals(date.plusDays(1), model.state.value.date) }
        compose.onNodeWithTag("previous-day").performClick(); compose.runOnIdle { assertEquals(date, model.state.value.date) }
    }
    @Test fun rtlConfirmAndLargeFont() { start(font = 2f, rtl = true); select(); compose.onNodeWithTag("confirm-left").assertIsDisplayed() }
    @Test fun ltrConfirmStaysOnRight() { start(); select(); compose.onNodeWithTag("confirm-right").assertIsDisplayed() }
    @Test fun landscapeSidePanePreservesCalendarTextWhenSpaceAllows() {
        val eventStart = java.time.LocalDate.now().atTime(10, 0).atZone(java.time.ZoneId.systemDefault()).toInstant()
        calendar.events.value = listOf(CalendarEvent(8, "Landscape meeting", eventStart, eventStart.plusSeconds(1800), calendarId = 1))
        start(600, 500)
        compose.onNodeWithTag("toggle-templates").performClick()
        compose.onNodeWithTag("side-pane").assertExists()
        compose.onNodeWithText("Landscape meeting").assertExists()
    }
    @Test fun sidePaneClosesWithSwipeOnPanel() {
        start(600, 500)
        compose.onNodeWithTag("toggle-templates").performClick()
        compose.onNodeWithTag("side-pane").performTouchInput { swipeRight() }
        compose.onNodeWithTag("side-pane").assertDoesNotExist()
    }
    @Test fun rtlSidePaneUsesMirroredSwipeDirection() {
        local.device.value = local.device.value.copy(pane = PanePreference.SIDE)
        start(360, 800, rtl = true)
        compose.onNodeWithTag("toggle-templates").performClick()
        compose.onNodeWithTag("side-pane").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("side-pane").assertDoesNotExist()
    }
    @Test fun preferredSidePaneWorksOnPhone() {
        local.device.value = local.device.value.copy(pane = PanePreference.SIDE)
        start(360, 800)
        compose.onNodeWithTag("toggle-templates").performClick()
        compose.onNodeWithTag("side-pane").assertExists()
    }
    @Test fun automaticLargePortraitPhoneUsesSidePane() {
        val eventStart = java.time.LocalDate.now().atTime(10, 0).atZone(java.time.ZoneId.systemDefault()).toInstant()
        calendar.events.value = listOf(CalendarEvent(7, "Compressed meeting", eventStart, eventStart.plusSeconds(1800), calendarId = 1))
        start(412, 915)
        compose.onNodeWithText("Compressed meeting").assertExists()
        compose.onNodeWithTag("toggle-templates").performClick()
        compose.onNodeWithTag("side-pane").assertExists()
        compose.onNodeWithText("Compressed meeting").assertDoesNotExist()
        compose.onNode(hasContentDescription("Compressed meeting", substring = true)).assertExists()
    }
    @Test fun persistentTwoPane() { start(1200, 800); compose.onNodeWithTag("persistent-pane").assertExists(); compose.onNodeWithTag("toggle-templates").assertDoesNotExist() }
    @Test fun recreationKeepsDraft() {
        start(); select()
        compose.onNodeWithTag("duration-ruler").performSemanticsAction(SemanticsActions.SetProgress) { it(18f) }
        val saved = model.state.value.saved
        compose.activityRule.scenario.recreate()
        compose.activityRule.scenario.onActivity { bind(it) }
        compose.onNodeWithTag("draft").assertExists()
        compose.runOnIdle { assertEquals(saved.draft, model.state.value.draft); assertEquals(saved.date, model.state.value.saved.date) }
    }
    @Test fun templateEditorUsesAnActivityListAndDedicatedEditor() {
        compose.setContent {
            DayBricksTheme(dynamic = false) {
                TemplateEditor(null, 100L, null, onSave = {}, onDismiss = {})
            }
        }
        compose.onNodeWithText("Create template").assertExists()
        compose.onNodeWithText("Activities in this template").assertExists()
        compose.onNodeWithText("Each activity can have", substring = true).assertExists()
        compose.onNodeWithTag("add-activity-option").performClick()
        compose.onNodeWithTag("activity-editor").assertIsDisplayed()
        compose.onNodeWithText("Duration").assertExists()
        compose.onNodeWithTag("activity-duration").assert(hasText("15"))
        compose.onNodeWithText("Leave empty to use the template duration.").assertDoesNotExist()
        compose.onNodeWithTag("activity-description").assertIsDisplayed()
        compose.onNodeWithTag("activity-name").performTextInput("Running")
        compose.onNodeWithTag("activity-description").performTextInput("A longer set of instructions for the activity")
        compose.onNodeWithTag("save-activity").performClick()
        compose.onNodeWithText("Running").assertExists()
        compose.onNodeWithTag("edit-activity-option-0").performClick()
        val doneY = compose.onNodeWithTag("save-activity").fetchSemanticsNode().boundsInRoot.center.y
        val cancelY = compose.onNodeWithTag("cancel-activity").fetchSemanticsNode().boundsInRoot.center.y
        val deleteY = compose.onNodeWithTag("delete-activity").fetchSemanticsNode().boundsInRoot.center.y
        assertEquals(doneY, cancelY, 1f)
        assertEquals(doneY, deleteY, 1f)
    }
    @Test fun settingsCanExpandAndFilterCalendars() {
        calendar.calendars = listOf(CalendarInfo(1, "Work", "Local"), CalendarInfo(2, "Personal", "Local"))
        start()
        model.dispatch(PlannerAction.ShowSettings(true))
        compose.onNodeWithTag("target-calendar-2").assertDoesNotExist()
        compose.onNodeWithTag("target-calendar-toggle").performClick()
        compose.onNodeWithTag("target-calendar-2").performClick()
        compose.onNodeWithTag("target-calendar-2").assertDoesNotExist()
        compose.runOnIdle { assertEquals(2L, local.device.value.targetCalendarId) }
        compose.onNodeWithTag("display-calendar-2").assertDoesNotExist()
        compose.onNodeWithTag("display-calendars-toggle").performClick()
        compose.onNodeWithTag("display-calendar-2").assertIsOn().performClick()
        compose.runOnIdle { assertEquals(setOf(2L), local.device.value.hiddenCalendarIds) }
        compose.onNodeWithTag("display-calendar-2").assertIsOff().performClick()
        compose.runOnIdle { assertTrue(local.device.value.hiddenCalendarIds.isEmpty()) }
        compose.onNodeWithTag("open-events-directly").performClick()
        compose.runOnIdle { assertTrue(local.device.value.openEventsDirectlyInCalendar) }
    }
    @Test fun settingsOffersBothExportFormats() {
        start()
        model.dispatch(PlannerAction.ShowSettings(true))
        compose.onNodeWithTag("export-json").assertExists()
        compose.onNodeWithTag("import-data").assertExists()
    }
}
