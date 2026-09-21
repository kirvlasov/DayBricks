package app.daybricks.planner.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class EventLayoutEngineTest {
    private val axis = TimeAxisMapper(LocalDate.of(2026, 9, 18), ZoneOffset.UTC)
    private fun event(id: Long, start: Double, end: Double) = CalendarEvent(id, "Event $id", axis.instantAt(start), axis.instantAt(end))
    @Test fun adjacentEventsUseOneColumn() {
        val result = EventLayoutEngine.layout(listOf(event(1, 0.0, 60.0), event(2, 60.0, 120.0)), axis)
        assertTrue(result.all { it.column == 0 && it.columns == 1 })
    }
    @Test fun twoOverlapsUseTwoColumns() {
        val result = EventLayoutEngine.layout(listOf(event(1, 0.0, 60.0), event(2, 30.0, 90.0)), axis)
        assertEquals(listOf(0, 1), result.map { it.column })
        assertTrue(result.all { it.columns == 2 })
    }
    @Test fun nestedEventsUseThreeColumns() {
        val result = EventLayoutEngine.layout(listOf(event(1, 0.0, 120.0), event(2, 20.0, 80.0), event(3, 30.0, 40.0)), axis)
        assertEquals(setOf(0, 1, 2), result.map { it.column }.toSet())
        assertTrue(result.all { it.columns == 3 })
    }
    @Test fun overlappingChainSharesWidthAndReusesColumns() {
        val result = EventLayoutEngine.layout(listOf(event(1, 0.0, 60.0), event(2, 30.0, 90.0), event(3, 60.0, 120.0), event(4, 120.0, 130.0)), axis)
        assertEquals(listOf(0, 1, 0, 0), result.map { it.column })
        assertEquals(listOf(2, 2, 2, 1), result.map { it.columns })
    }
    @Test fun multiDayClipKeepsOriginalTimes() {
        val original = event(1, -120.0, 1500.0)
        val result = EventLayoutEngine.layout(listOf(original, event(2, 1500.0, 1600.0), event(3, 100.0, 120.0).copy(allDay = true)), axis)
        assertEquals(1, result.size)
        assertEquals(0.0, result.single().startMinute, .001)
        assertEquals(1440.0, result.single().endMinute, .001)
        assertEquals(original, result.single().event)
    }
    @Test fun declinedEventStaysBehindAndDoesNotSplitAcceptedEventWidth() {
        val declined = event(1, 0.0, 120.0).copy(attendanceStatus = AttendanceStatus.DECLINED)
        val accepted = event(2, 30.0, 90.0).copy(attendanceStatus = AttendanceStatus.ACCEPTED)
        val result = EventLayoutEngine.layout(listOf(declined, accepted), axis)
        assertEquals(listOf(AttendanceStatus.DECLINED, AttendanceStatus.ACCEPTED), result.map { it.event.attendanceStatus })
        assertTrue(result.all { it.column == 0 && it.columns == 1 })
    }
}
