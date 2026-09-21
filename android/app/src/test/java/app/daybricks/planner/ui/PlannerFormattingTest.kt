package app.daybricks.planner.ui

import app.daybricks.planner.ui.planner.formatPlannerDate
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class PlannerFormattingTest {
    @Test fun russianDateStartsWithCapitalAndKeepsDayWithMonth() {
        val locale = Locale.forLanguageTag("ru")
        val formatter = DateTimeFormatter.ofPattern("EEEE, d MMMM", locale)

        assertEquals("Воскресенье, 20\u00A0сентября", formatPlannerDate(LocalDate.of(2026, 9, 20), formatter, locale))
    }
}
