package app.daybricks.planner.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StarterTemplatesTest {
    @Test fun englishPackHasFourUsefulValidTemplatesAndVariedActivities() {
        val templates = StarterTemplates.forLanguage("en")

        assertEquals(4, templates.size)
        assertTrue(templates.any { it.title == "Plan tomorrow" })
        assertTrue(templates.all { it.description.isNotBlank() })
        val varied = templates.single { it.presets.size > 1 }
        assertTrue(varied.presets.mapNotNull { it.durationMinutes }.distinct().size > 1)
        assertTrue(varied.presets.all { it.icon?.isNotBlank() == true && it.description.isNotBlank() })
        templates.forEach(BlockTemplate::validate)
    }

    @Test fun everySupportedLanguageHasACompleteLocalizedPack() {
        val tags = listOf("ar","bn","de","en","es","fa","fil","fr","hi","id","it","he","ja","ko","ms","pl","pt","pt-BR","ru","sw","th","tr","uk","ur","vi","zh-Hans","zh-Hant")
        val english = StarterTemplates.forLanguage("en")
        tags.forEach { tag ->
            val templates = StarterTemplates.forLanguage(tag)
            assertEquals(4, templates.size)
            assertTrue(templates.all { it.description.isNotBlank() })
            templates.forEach(BlockTemplate::validate)
            if (tag != "en") assertTrue("Starter pack was not localized for $tag", templates.first().title != english.first().title)
        }
    }
}
