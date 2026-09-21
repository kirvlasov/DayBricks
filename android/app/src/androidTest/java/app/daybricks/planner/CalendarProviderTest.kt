package app.daybricks.planner

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.provider.CalendarContract
import androidx.test.platform.app.InstrumentationRegistry
import app.daybricks.planner.calendar.AndroidCalendarRepository
import app.daybricks.planner.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

class CalendarProviderTest {
    @Test fun insertOwnershipAndRecurringInstancesFromSyncedCalendar() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
        val resolver = instrumentation.targetContext.contentResolver
        val account = "daybricks-test-${UUID.randomUUID()}"
        val calendarUri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, account)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL).build()
        var id: Long? = null
        try {
            id = ContentUris.parseId(requireNotNull(resolver.insert(calendarUri, ContentValues().apply {
                put(CalendarContract.Calendars.ACCOUNT_NAME, account)
                put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
                put(CalendarContract.Calendars.NAME, account)
                put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, "DayBricks instrumentation")
                put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
                put(CalendarContract.Calendars.OWNER_ACCOUNT, account)
                put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, "UTC")
                put(CalendarContract.Calendars.SYNC_EVENTS, 1)
                // Several calendar clients keep their own display preferences and
                // leave this provider flag at zero. Locally stored instances must
                // still be available to DayBricks.
                put(CalendarContract.Calendars.VISIBLE, 0)
            })))
            val repository = AndroidCalendarRepository(resolver)
            assertTrue(repository.readableCalendars().any { it.id == id })
            assertTrue(repository.writableCalendars().any { it.id == id })
            val date = LocalDate.of(2026, 9, 18)
            val start = date.atTime(12, 0).toInstant(ZoneOffset.UTC)
            val eventId = repository.create(NewCalendarEvent(id, "Test block", start, 16, ZoneOffset.UTC))
            resolver.query(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
                arrayOf(CalendarContract.Events.DTSTART), null, null, null)?.use {
                assertTrue(it.moveToFirst())
                assertEquals(start.toEpochMilli(), it.getLong(0))
            } ?: fail("Created event is missing from the local Calendar Provider")
            val dayEvents = withTimeout(10_000) { repository.observeDay(date, ZoneOffset.UTC).first() }
            val event = dayEvents.single { it.id == eventId }
            assertTrue(event.owned)
            assertEquals(id, event.calendarId)
            assertEquals(start.plusSeconds(16 * 60L), event.end)
            resolver.query(CalendarContract.Reminders.CONTENT_URI, arrayOf(CalendarContract.Reminders._ID),
                "${CalendarContract.Reminders.EVENT_ID} = ?", arrayOf(eventId.toString()), null)?.use {
                assertEquals(0, it.count)
            }
            val remindedId = repository.create(NewCalendarEvent(id, "Reminded block", start.plusSeconds(3600), 16,
                ZoneOffset.UTC, reminderMinutes = 15))
            resolver.query(CalendarContract.Reminders.CONTENT_URI,
                arrayOf(CalendarContract.Reminders.MINUTES, CalendarContract.Reminders.METHOD),
                "${CalendarContract.Reminders.EVENT_ID} = ?", arrayOf(remindedId.toString()), null)?.use {
                assertTrue(it.moveToFirst())
                assertEquals(15, it.getInt(0))
                assertEquals(CalendarContract.Reminders.METHOD_ALERT, it.getInt(1))
            }
            resolver.insert(CalendarContract.Events.CONTENT_URI, ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, id)
                put(CalendarContract.Events.TITLE, "Recurring test")
                put(CalendarContract.Events.DTSTART, start.toEpochMilli())
                put(CalendarContract.Events.DURATION, "PT30M")
                put(CalendarContract.Events.RRULE, "FREQ=DAILY;COUNT=3")
                put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
            })
            val tomorrow = withTimeout(10_000) { repository.observeDay(date.plusDays(1), ZoneOffset.UTC).first() }
            assertTrue(tomorrow.any { it.title == "Recurring test" })
        } finally {
            id?.let { resolver.delete(calendarUri, "${CalendarContract.Calendars._ID} = ?", arrayOf(it.toString())) }
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
    }
}
