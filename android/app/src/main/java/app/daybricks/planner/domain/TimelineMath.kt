package app.daybricks.planner.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.floor

class TimeAxisMapper(val date: LocalDate, val zone: ZoneId) {
    val start: Instant = date.atStartOfDay(zone).toInstant()
    val end: Instant = date.plusDays(1).atStartOfDay(zone).toInstant()
    val totalMinutes: Double = Duration.between(start, end).toMillis() / 60000.0
    fun minuteAt(instant: Instant): Double = Duration.between(start, instant).toMillis() / 60000.0
    fun instantAt(minute: Double): Instant = start.plusMillis((minute * 60000).toLong())
    fun snap(minute: Double, step: Int = START_SNAP_MINUTES): Double =
        ((minute / step).roundToInt() * step).toDouble().coerceIn(0.0, floor((totalMinutes - 1) / step) * step)
    fun localTimeAt(minute: Double): ZonedDateTime = instantAt(minute).atZone(zone)
    fun isRepeatedHour(minute: Double): Boolean = zone.rules.getValidOffsets(localTimeAt(minute).toLocalDateTime()).size > 1
    fun gridStep(zoom: Float): Int = when { zoom * 15 >= 28 -> 15; zoom * 30 >= 28 -> 30; else -> 60 }
}

data class EventPosition(val event: CalendarEvent, val startMinute: Double, val endMinute: Double, val column: Int, val columns: Int)

object EventLayoutEngine {
    fun layout(events: List<CalendarEvent>, axis: TimeAxisMapper): List<EventPosition> {
        val visible = events.filter { !it.allDay && it.end > axis.start && it.start < axis.end && it.end > it.start }
            .sortedWith(compareBy<CalendarEvent> { it.start }.thenByDescending { it.end }.thenBy { it.id })
        val background = visible.filter { it.attendanceStatus == AttendanceStatus.DECLINED }.map { event ->
            EventPosition(event, axis.minuteAt(event.start).coerceAtLeast(0.0), axis.minuteAt(event.end).coerceAtMost(axis.totalMinutes), 0, 1)
        }
        val result = mutableListOf<EventPosition>()
        val group = mutableListOf<EventPosition>()
        val columnEnds = mutableListOf<Instant>()
        var groupEnd = Instant.MIN
        fun flush() {
            result += group.map { it.copy(columns = columnEnds.size) }
            group.clear(); columnEnds.clear()
        }
        for (event in visible.filterNot { it.attendanceStatus == AttendanceStatus.DECLINED }) {
            if (event.start >= groupEnd) flush()
            val column = columnEnds.indexOfFirst { it <= event.start }.let { if (it == -1) columnEnds.size else it }
            if (column == columnEnds.size) columnEnds += event.end else columnEnds[column] = event.end
            group += EventPosition(event, axis.minuteAt(event.start).coerceAtLeast(0.0), axis.minuteAt(event.end).coerceAtMost(axis.totalMinutes), column, 1)
            groupEnd = if (group.size == 1) event.end else maxOf(groupEnd, event.end)
        }
        flush()
        return background + result
    }
}

object OwnershipDetector {
    private val marker = Regex("(?<!\\S)#DayBricks(?!\\S)")
    fun isOwned(description: String?): Boolean = description?.let(marker::containsMatchIn) == true
    fun strip(description: String): String = marker.replace(description, "").trim()
}

data class RulerTick(val x: Float, val scale: Float, val alpha: Float, val major: Boolean, val strong: Boolean)
object DurationRulerMath {
    const val DP_PER_MINUTE = 24f
    const val MAX_SPEED_MULTIPLIER = 4f
    const val BASE_MINUTES_PER_SECOND = 5f
    fun drag(value: Float, deltaDp: Float, edgeFraction: Float = 0f): Float {
        val speed = 1f + edgeFraction.coerceIn(0f, 1f)
        return (value - deltaDp / DP_PER_MINUTE * speed).coerceIn(MIN_DURATION.toFloat(), MAX_DURATION.toFloat())
    }
    fun snap(value: Float): Int = value.roundToInt().coerceIn(MIN_DURATION, MAX_DURATION)
    fun speedMultiplier(displacementFraction: Float): Float {
        val magnitude = abs(displacementFraction).coerceIn(0f, 1f)
        val active = ((magnitude - .06f) / .94f).coerceIn(0f, 1f)
        return MAX_SPEED_MULTIPLIER * active * active
    }
    fun advance(value: Float, displacementFraction: Float, elapsedSeconds: Float): Float {
        val direction = displacementFraction.coerceIn(-1f, 1f)
        val velocity = BASE_MINUTES_PER_SECOND * speedMultiplier(direction) * magneticResistance(value)
        return (value - kotlin.math.sign(direction) * velocity * elapsedSeconds)
            .coerceIn(MIN_DURATION.toFloat(), MAX_DURATION.toFloat())
    }
    fun magneticResistance(value: Float): Float {
        fun around(step: Float, radius: Float, strength: Float): Float {
            val remainder = ((value % step) + step) % step
            val distance = minOf(remainder, step - remainder)
            val proximity = (1f - distance / radius).coerceIn(0f, 1f)
            return 1f - strength * proximity * proximity
        }
        return (around(5f, 1.2f, .22f) * around(15f, 1.8f, .20f)).coerceAtLeast(.55f)
    }
    fun tick(minute: Int, value: Float, widthDp: Float): RulerTick {
        val x = widthDp / 2 + (minute - value) * DP_PER_MINUTE
        val distance = (abs(x - widthDp / 2) / (widthDp / 2).coerceAtLeast(1f)).coerceIn(0f, 1f)
        return RulerTick(x, 1.45f - distance * .7f, 1f - distance * .65f, minute % 5 == 0, minute % 15 == 0)
    }
}

data class PlacementContext(val date: LocalDate, val zone: ZoneId, val viewport: Instant, val lastAdded: Map<LocalDate, Instant>, val snap: Int = START_SNAP_MINUTES)
fun interface PlacementPolicy { fun suggestStart(context: PlacementContext): ZonedDateTime }
class DefaultPlacementPolicy : PlacementPolicy {
    override fun suggestStart(context: PlacementContext): ZonedDateTime {
        val axis = TimeAxisMapper(context.date, context.zone)
        val candidate = context.lastAdded[context.date] ?: context.viewport
        return axis.localTimeAt(axis.snap(axis.minuteAt(candidate), context.snap))
    }
}

object AdaptiveLayoutPolicy {
    const val MIN_WINDOW_WIDTH_DP = 280f
    const val MIN_WINDOW_HEIGHT_DP = 360f

    fun chooseLayout(widthDp: Float, heightDp: Float, preference: PanePreference = PanePreference.AUTO): PlannerLayout {
        val aspectRatio = widthDp / heightDp.coerceAtLeast(1f)
        return when {
            preference == PanePreference.BOTTOM -> PlannerLayout.TRANSIENT_BOTTOM
            preference == PanePreference.SIDE && widthDp >= MIN_WINDOW_WIDTH_DP -> PlannerLayout.TRANSIENT_SIDE
            widthDp >= 780 && heightDp >= 480 -> PlannerLayout.PERSISTENT_TWO_PANE
            heightDp < 480 -> PlannerLayout.TRANSIENT_SIDE
            widthDp < 320 || aspectRatio < .42f -> PlannerLayout.TRANSIENT_BOTTOM
            else -> PlannerLayout.TRANSIENT_SIDE
        }
    }

    fun sidePaneWidth(widthDp: Float, heightDp: Float): Float {
        val aspectRatio = widthDp / heightDp.coerceAtLeast(1f)
        val fraction = when {
            aspectRatio >= 1.4f -> .45f
            aspectRatio >= .9f -> .58f
            else -> .70f
        }
        val calendarReserve = if (aspectRatio >= .9f) 320f else 96f
        return minOf(360f, widthDp * fraction, widthDp - calendarReserve).coerceAtLeast(184f)
    }

    fun textCompression(calendarWidthDp: Float): Float = ((300f - calendarWidthDp) / 140f).coerceIn(0f, 1f)
}

object BottomPaneSizing {
    const val MIN_FRACTION = .30f
    const val MAX_FRACTION = 1f
    fun resize(currentFraction: Float, dragDeltaPx: Float, availableHeightPx: Float): Float {
        if (availableHeightPx <= 0f) return currentFraction.coerceIn(MIN_FRACTION, MAX_FRACTION)
        return (currentFraction - dragDeltaPx / availableHeightPx).coerceIn(MIN_FRACTION, MAX_FRACTION)
    }
}
