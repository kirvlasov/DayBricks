package app.daybricks.planner.calendar

import android.accounts.Account
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.ContentProviderOperation
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.Bundle
import android.provider.CalendarContract
import app.daybricks.planner.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AndroidCalendarRepository(private val resolver: ContentResolver) : CalendarRepository {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun observeDay(date: LocalDate, zone: ZoneId): Flow<List<CalendarEvent>> = callbackFlow {
        val observer = object : ContentObserver(null) { override fun onChange(selfChange: Boolean) { trySend(Unit) } }
        // Some provider implementations notify a concrete table URI without also
        // notifying CalendarContract.CONTENT_URI. Observe every input used here so
        // an insert made by DayBricks or a calendar app refreshes the timeline.
        resolver.registerContentObserver(CalendarContract.Instances.CONTENT_URI, true, observer)
        resolver.registerContentObserver(CalendarContract.Events.CONTENT_URI, true, observer)
        resolver.registerContentObserver(CalendarContract.Calendars.CONTENT_URI, true, observer)
        trySend(Unit)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }.conflate().mapLatest { queryDay(date, zone) }

    private suspend fun <T> query(uri: Uri, projection: Array<String>, selection: String?, args: Array<String>?, read: (Cursor) -> T): T =
        withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { continuation ->
                val signal = CancellationSignal()
                continuation.invokeOnCancellation { signal.cancel() }
                try {
                    val result = resolver.query(uri, projection, selection, args, null, signal)?.use(read)
                        ?: error("Calendar provider unavailable")
                    if (continuation.isActive) continuation.resume(result)
                } catch (failure: Exception) { if (continuation.isActive) continuation.resumeWithException(failure) }
            }
        }

    private suspend fun queryDay(date: LocalDate, zone: ZoneId): List<CalendarEvent> {
        val axis = TimeAxisMapper(date, zone)
        // All-day occurrences use UTC dates regardless of the device timezone.
        val utcStart = date.atStartOfDay(ZoneOffset.UTC).toInstant()
        val utcEnd = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().apply {
            ContentUris.appendId(this, minOf(axis.start, utcStart).toEpochMilli())
            ContentUris.appendId(this, maxOf(axis.end, utcEnd).toEpochMilli())
        }.build()
        val instances = query(uri, arrayOf(CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.DESCRIPTION, CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.SELF_ATTENDEE_STATUS, CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.EVENT_TIMEZONE, CalendarContract.Instances.EVENT_END_TIMEZONE,
            CalendarContract.Instances.ORGANIZER, CalendarContract.Instances.DISPLAY_COLOR,
            CalendarContract.Instances.RRULE, CalendarContract.Instances.RDATE), null, null) { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val start = Instant.ofEpochMilli(cursor.getLong(2))
                    val end = Instant.ofEpochMilli(cursor.getLong(3))
                    val allDay = cursor.getInt(4) != 0
                    val intersects = if (allDay) end > utcStart && start < utcEnd else end > axis.start && start < axis.end
                    val owned = OwnershipDetector.isOwned(cursor.getString(5))
                    val attendance = if (owned) AttendanceStatus.UNKNOWN else when (cursor.getInt(7)) {
                        CalendarContract.Attendees.ATTENDEE_STATUS_ACCEPTED -> AttendanceStatus.ACCEPTED
                        CalendarContract.Attendees.ATTENDEE_STATUS_TENTATIVE -> AttendanceStatus.TENTATIVE
                        CalendarContract.Attendees.ATTENDEE_STATUS_INVITED -> AttendanceStatus.NEEDS_ACTION
                        CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED -> AttendanceStatus.DECLINED
                        else -> AttendanceStatus.UNKNOWN
                    }
                    if (intersects) add(CalendarEvent(cursor.getLong(0), cursor.getString(1).orEmpty(), start, end, allDay,
                        owned, cursor.getLong(6), attendance, OwnershipDetector.strip(cursor.getString(5).orEmpty()),
                        cursor.getString(8).orEmpty(), cursor.getString(9), cursor.getString(10),
                        cursor.getString(11).orEmpty(), if (cursor.isNull(12)) null else cursor.getInt(12),
                        recurring = !cursor.getString(13).isNullOrBlank() || !cursor.getString(14).isNullOrBlank()))
                }
            }
        }
        // Some calendar providers do not expand a newly inserted event into Instances
        // until their calendar app runs. The Events row is already available locally.
        // Include DayBricks' non-recurring timed events directly until Instances catches up.
        val directEvents = query(CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events._ID, CalendarContract.Events.TITLE,
                CalendarContract.Events.DTSTART, CalendarContract.Events.DTEND,
                CalendarContract.Events.DESCRIPTION, CalendarContract.Events.CALENDAR_ID,
                CalendarContract.Events.EVENT_LOCATION, CalendarContract.Events.EVENT_TIMEZONE,
                CalendarContract.Events.EVENT_END_TIMEZONE, CalendarContract.Events.ORGANIZER,
                CalendarContract.Events.DISPLAY_COLOR),
            "${CalendarContract.Events.DTSTART} < ? AND ${CalendarContract.Events.DTEND} > ? " +
                "AND ${CalendarContract.Events.DELETED} = 0 " +
                "AND (${CalendarContract.Events.LAST_SYNCED} IS NULL OR ${CalendarContract.Events.LAST_SYNCED} = 0) " +
                "AND ${CalendarContract.Events.ALL_DAY} = 0 " +
                "AND ${CalendarContract.Events.RRULE} IS NULL AND ${CalendarContract.Events.RDATE} IS NULL " +
                "AND (${CalendarContract.Events.STATUS} IS NULL OR ${CalendarContract.Events.STATUS} != ${CalendarContract.Events.STATUS_CANCELED}) " +
                "AND ${CalendarContract.Events.DESCRIPTION} LIKE ?",
            arrayOf(axis.end.toEpochMilli().toString(), axis.start.toEpochMilli().toString(), "%$OWNERSHIP_TAG%"),
        ) { cursor -> buildList {
            while (cursor.moveToNext()) {
                if (!OwnershipDetector.isOwned(cursor.getString(4))) continue
                add(CalendarEvent(cursor.getLong(0), cursor.getString(1).orEmpty(),
                    Instant.ofEpochMilli(cursor.getLong(2)), Instant.ofEpochMilli(cursor.getLong(3)),
                    owned = true, calendarId = cursor.getLong(5),
                    description = OwnershipDetector.strip(cursor.getString(4).orEmpty()),
                    location = cursor.getString(6).orEmpty(), timeZone = cursor.getString(7),
                    endTimeZone = cursor.getString(8), organizer = cursor.getString(9).orEmpty(),
                    displayColor = if (cursor.isNull(10)) null else cursor.getInt(10)))
            }
        } }
        val instanceIds = instances.mapTo(mutableSetOf<Long>()) { it.id }
        return instances + directEvents.filter { it.id !in instanceIds }
    }

    override suspend fun readableCalendars(): List<CalendarInfo> = calendarsWithAccess(CalendarContract.Calendars.CAL_ACCESS_READ)

    override suspend fun writableCalendars(): List<CalendarInfo> = calendarsWithAccess(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR)

    private suspend fun calendarsWithAccess(minimumAccess: Int): List<CalendarInfo> = query(
        CalendarContract.Calendars.CONTENT_URI,
        arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME, CalendarContract.Calendars.ACCOUNT_TYPE),
        "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ? AND ${CalendarContract.Calendars.SYNC_EVENTS} = 1",
        arrayOf(minimumAccess.toString()),
    ) { cursor -> buildList {
        while (cursor.moveToNext()) add(CalendarInfo(cursor.getLong(0), cursor.getString(1).orEmpty(),
            cursor.getString(2).orEmpty(), cursor.getString(3).orEmpty()))
    }.sortedWith(compareBy<CalendarInfo> { it.name.lowercase() }.thenBy { it.account.lowercase() }) }

    override suspend fun create(event: NewCalendarEvent): Long = withContext(Dispatchers.IO) {
        require(event.title.isNotBlank() && event.durationMinutes in MIN_DURATION..MAX_DURATION)
        require(writableCalendars().any { it.id == event.calendarId }) { "Select another writable calendar" }
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, event.calendarId)
            put(CalendarContract.Events.TITLE, event.title.trim())
            put(CalendarContract.Events.DESCRIPTION, listOf(event.description.trim().takeIf { it.isNotBlank() }, OWNERSHIP_TAG)
                .filterNotNull().joinToString("\n\n"))
            put(CalendarContract.Events.DTSTART, event.start.toEpochMilli())
            put(CalendarContract.Events.DTEND, event.start.plusSeconds(event.durationMinutes * 60L).toEpochMilli())
            put(CalendarContract.Events.EVENT_TIMEZONE, event.zone.id)
            put(CalendarContract.Events.ALL_DAY, 0)
            put(CalendarContract.Events.HAS_ALARM, if (event.reminderMinutes == null) 0 else 1)
        }
        val operations = arrayListOf(
            ContentProviderOperation.newInsert(CalendarContract.Events.CONTENT_URI).withValues(values).build(),
        )
        event.reminderMinutes?.let { minutes ->
            require(minutes in 0..10080)
            operations += ContentProviderOperation.newInsert(CalendarContract.Reminders.CONTENT_URI)
                .withValueBackReference(CalendarContract.Reminders.EVENT_ID, 0)
                .withValue(CalendarContract.Reminders.MINUTES, minutes)
                .withValue(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                .build()
        }
        val result = resolver.applyBatch(CalendarContract.AUTHORITY, operations)
        val eventId = ContentUris.parseId(requireNotNull(result.firstOrNull()?.uri) { "Calendar insert failed" })
        resolver.notifyChange(
            ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
            null,
            ContentResolver.NOTIFY_SYNC_TO_NETWORK,
        )
        eventId
    }

    override suspend fun requestSync(calendarId: Long): Unit = withContext(Dispatchers.IO) {
        val calendar = writableCalendars().firstOrNull { it.id == calendarId } ?: return@withContext
        if (calendar.account.isBlank() || calendar.accountType.isBlank() ||
            calendar.accountType == CalendarContract.ACCOUNT_TYPE_LOCAL) return@withContext
        runCatching {
            val account = Account(calendar.account, calendar.accountType)
            ContentResolver.requestSync(
                account,
                CalendarContract.AUTHORITY,
                Bundle().apply {
                    putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
                    putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
                    putBoolean(ContentResolver.SYNC_EXTRAS_IGNORE_SETTINGS, true)
                },
            )
        }
        Unit
    }

    override suspend fun delete(eventId: Long): Unit = withContext(Dispatchers.IO) {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        check(resolver.delete(uri, null, null) == 1) { "Calendar event could not be deleted" }
        resolver.notifyChange(uri, null, ContentResolver.NOTIFY_SYNC_TO_NETWORK)
    }
}
