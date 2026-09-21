package app.daybricks.planner.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.Required
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

const val MIN_DURATION = 1
const val MAX_DURATION = 720
const val START_SNAP_MINUTES = 5
const val OWNERSHIP_TAG = "#DayBricks"
const val MAX_DESCRIPTION_LENGTH = 2000
const val SYNC_SCHEMA_VERSION = 2

@Serializable
data class BlockPreset(
    @Required val id: String = UUID.randomUUID().toString(),
    val title: String,
    val durationMinutes: Int? = null,
    val icon: String? = null,
    val description: String = "",
)

@Serializable
data class BlockTemplate(
    @Required val id: String = UUID.randomUUID().toString(),
    val title: String,
    val icon: String? = null,
    val description: String = "",
    @Required val defaultDurationMinutes: Int = 15,
    val reminderMinutes: Int? = null,
    @Required val presets: List<BlockPreset> = emptyList(),
    @Required val sortOrder: Long = 0,
)

@Serializable enum class DominantHand { RIGHT, LEFT }
@Serializable enum class PanePreference { AUTO, SIDE, BOTTOM }
@Serializable enum class ThemePreference { SYSTEM, LIGHT, DARK }
enum class PlannerLayout { PERSISTENT_TWO_PANE, TRANSIENT_SIDE, TRANSIENT_BOTTOM }

@Serializable
data class SyncedPreferences(
    // Legacy protocol field. The server still requires it; UI placement ignores it.
    @Required val dominantHand: DominantHand = DominantHand.RIGHT,
)

@Serializable
data class SyncState(@Required val schemaVersion: Int = SYNC_SCHEMA_VERSION, @Required val templates: List<BlockTemplate> = emptyList(), @Required val preferences: SyncedPreferences = SyncedPreferences()) {
    fun validated(): SyncState {
        require(schemaVersion in 1..SYNC_SCHEMA_VERSION) { "Unsupported schema version" }
        require(templates.size <= 2000 && templates.map { it.id }.distinct().size == templates.size)
        templates.forEach { it.validate() }
        return copy(preferences = SyncedPreferences())
    }
}

fun BlockTemplate.validate() {
    require(UUID.fromString(id).toString() == id.lowercase()) { "Invalid template ID" }
    require(title.isNotBlank() && title.length <= 200) { "Name must contain 1–200 characters" }
    require(defaultDurationMinutes in MIN_DURATION..MAX_DURATION) { "Duration must be 1–720 minutes" }
    require(reminderMinutes == null || reminderMinutes in 0..10080) { "Reminder must be no more than 7 days" }
    require(icon == null || icon.length <= 32)
    require(description.length <= MAX_DESCRIPTION_LENGTH)
    require(presets.size <= 100 && presets.map { it.id }.distinct().size == presets.size)
    presets.forEach {
        require(UUID.fromString(it.id).toString() == it.id.lowercase())
        require(it.title.isNotBlank() && it.title.length <= 200)
        require(it.icon == null || it.icon.length <= 32)
        require(it.durationMinutes == null || it.durationMinutes in MIN_DURATION..MAX_DURATION)
        require(it.description.length <= MAX_DESCRIPTION_LENGTH)
    }
}

@Serializable
data class DevicePreferences(
    val targetCalendarId: Long? = null,
    val pane: PanePreference = PanePreference.AUTO,
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val defaultReminderMinutes: Int? = null,
    val zoom: Float = 1.8f,
    val serverUrl: String = "",
    // Device-local provider IDs. An exclusion set keeps newly added calendars
    // visible by default and avoids syncing device-specific identifiers.
    val hiddenCalendarIds: Set<Long> = emptySet(),
    val starterTemplatesInstalled: Boolean = false,
    val openEventsDirectlyInCalendar: Boolean = false,
)

data class CalendarInfo(val id: Long, val name: String, val account: String, val accountType: String = "")
enum class AttendanceStatus { ACCEPTED, TENTATIVE, NEEDS_ACTION, DECLINED, UNKNOWN }
data class CalendarEvent(
    val id: Long,
    val title: String,
    val start: Instant,
    val end: Instant,
    val allDay: Boolean = false,
    val owned: Boolean = false,
    val calendarId: Long? = null,
    val attendanceStatus: AttendanceStatus = AttendanceStatus.UNKNOWN,
    val description: String = "",
    val location: String = "",
    val timeZone: String? = null,
    val endTimeZone: String? = null,
    val organizer: String = "",
    val displayColor: Int? = null,
    val recurring: Boolean = false,
)
data class NewCalendarEvent(
    val calendarId: Long,
    val title: String,
    val start: Instant,
    val durationMinutes: Int,
    val zone: ZoneId,
    val reminderMinutes: Int? = null,
    val description: String = "",
)

interface CalendarRepository {
    fun observeDay(date: LocalDate, zone: ZoneId): Flow<List<CalendarEvent>>
    suspend fun readableCalendars(): List<CalendarInfo>
    suspend fun writableCalendars(): List<CalendarInfo>
    suspend fun create(event: NewCalendarEvent): Long
    suspend fun delete(eventId: Long)
    suspend fun requestSync(calendarId: Long)
}

interface TemplateRepository {
    val templates: Flow<List<BlockTemplate>>
    suspend fun upsert(template: BlockTemplate)
    suspend fun delete(id: String)
    suspend fun reorder(ids: List<String>)
}

interface SettingsRepository {
    val device: Flow<DevicePreferences>
    suspend fun updateDevice(update: (DevicePreferences) -> DevicePreferences)
}

interface CredentialStore {
    suspend fun readToken(): String?
    suspend fun writeToken(token: String?)
}

interface SyncRepository {
    val status: Flow<SyncStatus>
    suspend fun sync()
}
data class SyncStatus(val running: Boolean = false, val error: String? = null, val lastSuccess: Instant? = null)
