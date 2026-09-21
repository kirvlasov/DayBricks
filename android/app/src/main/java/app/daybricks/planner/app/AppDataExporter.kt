package app.daybricks.planner.app

import app.daybricks.planner.BuildConfig
import app.daybricks.planner.domain.BlockTemplate
import app.daybricks.planner.domain.DevicePreferences
import app.daybricks.planner.domain.SyncedPreferences
import app.daybricks.planner.domain.validate
import java.io.OutputStream
import java.io.InputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

@Serializable
data class AppDataExport(
    val format: String = "daybricks-app-data",
    val schemaVersion: Int = 1,
    val exportedAt: String,
    val appVersion: String,
    val calendarEventsIncluded: Boolean = false,
    val credentialsIncluded: Boolean = false,
    val templates: List<BlockTemplate>,
    val preferences: ExportedPreferences,
)

@Serializable
data class ExportedPreferences(
    val synced: SyncedPreferences,
    val device: DevicePreferences,
)

@Serializable
private data class ZipManifest(
    val format: String = "daybricks-app-data-zip",
    val schemaVersion: Int = 1,
    val exportedAt: String,
    val appVersion: String,
    val files: List<String> = listOf("templates.json", "preferences.json"),
    val calendarEventsIncluded: Boolean = false,
    val credentialsIncluded: Boolean = false,
)

object AppDataExporter {
    private val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = true }

    fun snapshot(
        templates: List<BlockTemplate>,
        synced: SyncedPreferences,
        device: DevicePreferences,
        exportedAt: Instant = Instant.now(),
    ) = AppDataExport(
        exportedAt = exportedAt.toString(),
        appVersion = BuildConfig.VERSION_NAME,
        templates = templates,
        preferences = ExportedPreferences(synced, device),
    )

    fun writeJson(output: OutputStream, export: AppDataExport) {
        output.writer(Charsets.UTF_8).use { it.write(json.encodeToString(export)) }
    }

    fun writeZip(output: OutputStream, export: AppDataExport) {
        ZipOutputStream(output).use { zip ->
            zip.writeJsonEntry("manifest.json", json.encodeToString(ZipManifest(
                exportedAt = export.exportedAt,
                appVersion = export.appVersion,
            )))
            zip.writeJsonEntry("templates.json", json.encodeToString(export.templates))
            zip.writeJsonEntry("preferences.json", json.encodeToString(export.preferences))
        }
    }

    private fun ZipOutputStream.writeJsonEntry(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }
}

object AppDataImporter {
    private const val MAX_BYTES = 10 * 1024 * 1024
    private val json = Json { ignoreUnknownKeys = true }

    fun read(input: InputStream): AppDataExport {
        val bytes = input.readLimited()
        return if (bytes.size >= 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
            readZip(bytes)
        } else json.decodeFromString<AppDataExport>(bytes.toString(Charsets.UTF_8)).validated()
    }

    private fun readZip(bytes: ByteArray): AppDataExport {
        val entries = mutableMapOf<String, String>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                require(entries.size < 10 && entry.name in setOf("manifest.json", "templates.json", "preferences.json"))
                entries[entry.name] = zip.readLimited().toString(Charsets.UTF_8)
                entry = zip.nextEntry
            }
        }
        val manifest = json.decodeFromString<ZipManifest>(entries.getValue("manifest.json"))
        require(manifest.format == "daybricks-app-data-zip" && manifest.schemaVersion == 1)
        return AppDataExport(
            exportedAt = manifest.exportedAt,
            appVersion = manifest.appVersion,
            templates = json.decodeFromString(entries.getValue("templates.json")),
            preferences = json.decodeFromString(entries.getValue("preferences.json")),
        ).validated()
    }

    private fun AppDataExport.validated(): AppDataExport = apply {
        require(format == "daybricks-app-data" && schemaVersion == 1)
        require(!calendarEventsIncluded && !credentialsIncluded)
        require(templates.size <= 2000 && templates.map { it.id }.distinct().size == templates.size)
        templates.forEach(BlockTemplate::validate)
        require(preferences.device.defaultReminderMinutes == null || preferences.device.defaultReminderMinutes in 0..10080)
        require(preferences.device.zoom in .6f..5f && preferences.device.serverUrl.length <= 2048)
    }

    private fun InputStream.readLimited(): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            total += count
            require(total <= MAX_BYTES) { "Import is too large" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}
