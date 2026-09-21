package app.daybricks.planner.calendar

import app.daybricks.planner.domain.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId

class FakeCalendarRepository(
    initial: List<CalendarEvent> = emptyList(),
    var calendars: List<CalendarInfo> = listOf(CalendarInfo(1, "Test calendar", "Local")),
) : CalendarRepository {
    val events = MutableStateFlow(initial)
    var failInsert = false
    val created = mutableListOf<NewCalendarEvent>()
    val syncRequests = mutableListOf<Long>()
    override fun observeDay(date: LocalDate, zone: ZoneId) = events.map { all ->
        val axis = TimeAxisMapper(date, zone)
        all.filter { it.start < axis.end && it.end > axis.start }
    }
    override suspend fun readableCalendars() = calendars
    override suspend fun writableCalendars() = calendars
    override suspend fun create(event: NewCalendarEvent): Long {
        check(!failInsert) { "Simulated insert failure" }
        created += event
        val id = (events.value.maxOfOrNull { it.id } ?: 0L) + 1L
        events.value += CalendarEvent(id, event.title, event.start, event.start.plusSeconds(event.durationMinutes * 60L),
            owned = true, calendarId = event.calendarId)
        return id
    }
    override suspend fun requestSync(calendarId: Long) { syncRequests += calendarId }
    override suspend fun delete(eventId: Long) { events.value = events.value.filterNot { it.id == eventId } }
}
