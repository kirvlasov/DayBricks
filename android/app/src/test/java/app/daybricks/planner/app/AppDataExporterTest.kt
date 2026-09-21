package app.daybricks.planner.app

import app.daybricks.planner.domain.BlockTemplate
import app.daybricks.planner.domain.DevicePreferences
import app.daybricks.planner.domain.SyncedPreferences
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDataExporterTest {
    private val export = AppDataExporter.snapshot(
        templates = listOf(BlockTemplate(title = "Read", description = "Open one page")),
        synced = SyncedPreferences(),
        device = DevicePreferences(serverUrl = "https://sync.example"),
        exportedAt = Instant.parse("2026-09-20T12:00:00Z"),
    )

    @Test fun jsonContainsAppDataButNoCalendarEventsOrCredentials() {
        val output = ByteArrayOutputStream()
        AppDataExporter.writeJson(output, export)
        val json = output.toString(Charsets.UTF_8.name())

        assertTrue(json.contains("\"title\": \"Read\""))
        assertTrue(json.contains("\"calendarEventsIncluded\": false"))
        assertTrue(json.contains("\"credentialsIncluded\": false"))
        assertFalse(json.contains("apiToken", ignoreCase = true))
    }

    @Test fun zipSeparatesManifestTemplatesAndPreferences() {
        val output = ByteArrayOutputStream()
        AppDataExporter.writeZip(output, export)
        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                entry = zip.nextEntry
            }
        }

        assertEquals(setOf("manifest.json", "templates.json", "preferences.json"), entries.keys)
        assertTrue(entries.getValue("templates.json").contains("Open one page"))
        assertTrue(entries.getValue("manifest.json").contains("\"credentialsIncluded\": false"))
    }

    @Test fun importerReadsBothExportFormats() {
        val jsonOutput = ByteArrayOutputStream()
        AppDataExporter.writeJson(jsonOutput, export)
        val zipOutput = ByteArrayOutputStream()
        AppDataExporter.writeZip(zipOutput, export)

        assertEquals("Read", AppDataImporter.read(jsonOutput.toByteArray().inputStream()).templates.single().title)
        assertEquals("Read", AppDataImporter.read(zipOutput.toByteArray().inputStream()).templates.single().title)
    }

    @Test(expected = IllegalArgumentException::class)
    fun importerRejectsUnrelatedJson() {
        AppDataImporter.read("{\"format\":\"something-else\"}".byteInputStream())
    }
}
