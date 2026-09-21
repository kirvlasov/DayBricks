package app.daybricks.planner.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class TimelineMathTest {
    private val berlin = ZoneId.of("Europe/Berlin")
    @Test fun ordinaryDayAndRoundTrip() {
        val axis = TimeAxisMapper(LocalDate.of(2026, 9, 18), berlin)
        assertEquals(1440.0, axis.totalMinutes, .001)
        assertEquals(721.5, axis.minuteAt(axis.instantAt(721.5)), .001)
    }
    @Test fun springForwardHas23HoursAndSkipsMissingHour() {
        val axis = TimeAxisMapper(LocalDate.of(2026, 3, 29), berlin)
        assertEquals(1380.0, axis.totalMinutes, .001)
        assertEquals(3, axis.localTimeAt(120.0).hour)
    }
    @Test fun fallBackKeepsTwoDistinctOccurrences() {
        val axis = TimeAxisMapper(LocalDate.of(2026, 10, 25), berlin)
        assertEquals(1500.0, axis.totalMinutes, .001)
        assertEquals(2, axis.localTimeAt(120.0).hour)
        assertEquals(2, axis.localTimeAt(180.0).hour)
        assertNotEquals(axis.localTimeAt(120.0).offset, axis.localTimeAt(180.0).offset)
        assertTrue(axis.isRepeatedHour(120.0))
        assertTrue(axis.isRepeatedHour(180.0))
    }
    @Test fun zonesIncludeHalfHourDSTAndNonWholeHourOffsets() {
        assertEquals(1410.0, TimeAxisMapper(LocalDate.of(2026, 10, 4), ZoneId.of("Australia/Lord_Howe")).totalMinutes, .001)
        for (zone in listOf("UTC", "Asia/Kathmandu", "America/New_York")) {
            val axis = TimeAxisMapper(LocalDate.of(2026, 9, 18), ZoneId.of(zone))
            assertEquals(0, axis.localTimeAt(0.0).hour)
            assertEquals(axis.end, axis.instantAt(axis.totalMinutes))
        }
    }
    @Test fun snappingStaysWithinDayOnGrid() {
        val axis = TimeAxisMapper(LocalDate.of(2026, 9, 18), berlin)
        assertEquals(0.0, axis.snap(-20.0), .001)
        assertEquals(20.0, axis.snap(18.0), .001)
        assertEquals(1435.0, axis.snap(1439.0), .001)
    }
    @Test fun ownershipUsesStandaloneToken() {
        for (text in listOf("#DayBricks", "Hello\n\n#DayBricks", "hello #DayBricks world")) assertTrue(OwnershipDetector.isOwned(text))
        for (text in listOf(null, "#DayBricksABC", "prefix#DayBricks", "#daybricks", "#DayBricks_")) assertFalse(OwnershipDetector.isOwned(text))
        assertEquals("Hello", OwnershipDetector.strip("Hello\n\n#DayBricks"))
    }
    @Test fun rulerDragAndSnapAndBounds() {
        assertEquals(17f, DurationRulerMath.drag(16f, -24f), .001f)
        assertEquals(15f, DurationRulerMath.drag(16f, 24f), .001f)
        assertEquals(18f, DurationRulerMath.drag(16f, -24f, 1f), .001f)
        assertEquals(17, DurationRulerMath.snap(DurationRulerMath.drag(16f, -12f, 1f)))
        assertEquals(0f, DurationRulerMath.speedMultiplier(0f), .001f)
        assertEquals(4f, DurationRulerMath.speedMultiplier(1f), .001f)
        assertTrue(DurationRulerMath.speedMultiplier(.5f) in 0f..4f)
        assertEquals(22f, DurationRulerMath.advance(12f, -1f, .5f), .001f)
        assertEquals(2f, DurationRulerMath.advance(12f, 1f, .5f), .001f)
        assertEquals(16, DurationRulerMath.snap(15.6f))
        assertEquals(1f, DurationRulerMath.drag(1f, 100f), .001f)
        assertEquals(720f, DurationRulerMath.drag(720f, -100f), .001f)
    }
    @Test fun rulerGentlyResistsRoundDurationsWithoutSticking() {
        val ordinary = DurationRulerMath.magneticResistance(12f)
        val fiveMinute = DurationRulerMath.magneticResistance(10f)
        val fifteenMinute = DurationRulerMath.magneticResistance(15f)
        assertEquals(1f, ordinary, .001f)
        assertTrue(fiveMinute in .55f..<ordinary)
        assertTrue(fifteenMinute in .55f..<fiveMinute)
        assertTrue(DurationRulerMath.advance(15f, -1f, .1f) > 15f)
    }
    @Test fun rulerCenterLargerThanEdge() {
        val center = DurationRulerMath.tick(15, 15f, 240f)
        val edge = DurationRulerMath.tick(20, 15f, 240f)
        assertEquals(120f, center.x, .001f)
        assertTrue(center.scale > edge.scale && center.alpha > edge.alpha)
        assertTrue(center.strong && center.major)
    }
    @Test fun placementLastAddedElseViewportAndSeparateDates() {
        val date = LocalDate.of(2026, 9, 18)
        val axis = TimeAxisMapper(date, berlin)
        val context = PlacementContext(date, berlin, axis.instantAt(602.0), mapOf(date to axis.instantAt(676.0)))
        val policy = DefaultPlacementPolicy()
        assertEquals(axis.instantAt(675.0), policy.suggestStart(context).toInstant())
        assertEquals(axis.instantAt(600.0), policy.suggestStart(context.copy(lastAdded = emptyMap())).toInstant())
        assertEquals(axis.instantAt(600.0), policy.suggestStart(context.copy(lastAdded = mapOf(date.minusDays(1) to axis.instantAt(100.0)))).toInstant())
    }
    @Test fun adaptiveWindowMatrixAndSafeOverride() {
        val bottom = listOf(260 to 800, 300 to 900, 360 to 900)
        val side = listOf(320 to 568, 360 to 800, 412 to 915, 480 to 480, 800 to 360, 600 to 500)
        val persistent = listOf(800 to 1280, 1200 to 800, 1600 to 900)
        for ((w,h) in bottom) assertEquals(PlannerLayout.TRANSIENT_BOTTOM, AdaptiveLayoutPolicy.chooseLayout(w.toFloat(), h.toFloat()))
        for ((w,h) in side) assertEquals(PlannerLayout.TRANSIENT_SIDE, AdaptiveLayoutPolicy.chooseLayout(w.toFloat(), h.toFloat()))
        for ((w,h) in persistent) assertEquals(PlannerLayout.PERSISTENT_TWO_PANE, AdaptiveLayoutPolicy.chooseLayout(w.toFloat(), h.toFloat()))
        assertEquals(PlannerLayout.TRANSIENT_SIDE, AdaptiveLayoutPolicy.chooseLayout(320f, 800f, PanePreference.SIDE))
        assertEquals(PlannerLayout.TRANSIENT_BOTTOM, AdaptiveLayoutPolicy.chooseLayout(260f, 800f, PanePreference.SIDE))
        assertEquals(PlannerLayout.TRANSIENT_BOTTOM, AdaptiveLayoutPolicy.chooseLayout(1600f, 900f, PanePreference.BOTTOM))
        assertEquals(280f, AdaptiveLayoutPolicy.sidePaneWidth(600f, 500f), .001f)
        assertEquals(0f, AdaptiveLayoutPolicy.textCompression(320f), .001f)
        assertEquals(1f, AdaptiveLayoutPolicy.textCompression(120f), .001f)
        assertEquals(.30f, BottomPaneSizing.resize(.40f, 200f, 500f), .001f)
        assertEquals(1f, BottomPaneSizing.resize(.90f, -200f, 500f), .001f)
    }
}
