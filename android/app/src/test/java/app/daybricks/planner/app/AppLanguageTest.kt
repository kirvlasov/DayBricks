package app.daybricks.planner.app

import java.text.Collator
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLanguageTest {
    @Test fun languagesUseNativeNamesAndAreSortedForTheInterfaceLocale() {
        val locale = Locale.ENGLISH
        val sorted = AppLanguage.sortedTags(locale)
        assertTrue("en" in sorted)
        assertEquals("English", AppLanguage.displayName("en"))
        assertEquals("Deutsch", AppLanguage.displayName("de"))
        assertEquals("Français", AppLanguage.displayName("fr"))
        assertEquals("Русский", AppLanguage.displayName("ru"))
        val names = sorted.map(AppLanguage::displayName)
        val collator = Collator.getInstance(locale)
        assertEquals(names.sortedWith(collator), names)
    }
}
