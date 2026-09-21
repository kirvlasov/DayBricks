package app.daybricks.planner.sync

import app.daybricks.planner.domain.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.After
import org.junit.Test
import java.util.concurrent.TimeUnit

class StateSyncTest {
    private val server = MockWebServer().apply { start(java.net.InetAddress.getByName("127.0.0.1"), 0) }
    private fun serverUrl() = server.url("/").newBuilder().host("127.0.0.1").build().toString()
    private val template = BlockTemplate(title = "Training", defaultDurationMinutes = 16)
    private val local = MemoryStore()
    private val settings = MemorySettings(serverUrl())
    private val credentials = object : CredentialStore {
        override suspend fun readToken() = "a-test-token"
        override suspend fun writeToken(token: String?) = Unit
    }
    private fun repository() = StateSyncRepository(local, settings, credentials, StateTransport(allowLocalHttp = true))
    private fun remote(state: SyncState = SyncState(), etag: String = "\"0\"") = MockResponse().setHeader("ETag", etag).setBody(StateJson.encodeToString(state))
    @After fun close() { server.shutdown() }

    @Test fun transportRoundTrip() = runTest {
        server.enqueue(remote())
        assertEquals(SyncState(), StateTransport(allowLocalHttp = true).get(serverUrl(), "test").state)
    }
    @Test fun schemaOneWithoutDescriptionsStillDecodesAndUpgradesOnWrite() {
        val legacy = StateJson.decodeFromString<SyncState>(
            """{"schemaVersion":1,"templates":[{"id":"${template.id}","title":"Training","defaultDurationMinutes":16,"presets":[],"sortOrder":0}],"preferences":{"dominantHand":"RIGHT"}}"""
        ).validated()
        assertEquals("", legacy.templates.single().description)
        assertEquals(SYNC_SCHEMA_VERSION, legacy.applyOperations(emptyList()).schemaVersion)
    }

    @Test fun normalGetPutAcknowledgesOnlySentOperations() = runTest {
        local.ops += PendingOperation(1, SyncOperation.UpsertTemplate(template))
        server.enqueue(remote())
        server.enqueue(MockResponse().setResponseCode(204).setHeader("ETag", "\"1\""))
        val repo = repository(); repo.sync()
        assertNull(repo.status.value.error)
        assertTrue(local.ops.isEmpty())
        assertEquals(listOf(template), local.state.templates)
        assertEquals("GET", server.takeRequest().method)
        val put = server.takeRequest()
        assertEquals("PUT", put.method)
        assertEquals("\"0\"", put.getHeader("If-Match"))
        assertEquals("Bearer a-test-token", put.getHeader("Authorization"))
        assertFalse(put.body.readUtf8().contains("calendarId"))
    }
    @Test fun emptyOutboxOnlyDownloads() = runTest {
        server.enqueue(remote(SyncState(templates = listOf(template))))
        repository().sync()
        assertEquals(listOf(template), local.state.templates)
        assertEquals(1, server.requestCount)
    }
    @Test fun conflictFetchesAgainAndRebasesDelete() = runTest {
        val other = BlockTemplate(title = "Other")
        local.ops += PendingOperation(1, SyncOperation.DeleteTemplate(template.id))
        server.enqueue(remote(SyncState(templates = listOf(template))))
        server.enqueue(MockResponse().setResponseCode(412))
        server.enqueue(remote(SyncState(templates = listOf(template, other)), "\"4\""))
        server.enqueue(MockResponse().setResponseCode(204).setHeader("ETag", "\"5\""))
        val repo = repository(); repo.sync()
        assertEquals(listOf(other), local.state.templates)
        assertNull(repo.status.value.error)
        repeat(3) { server.takeRequest() }
        assertEquals("\"4\"", server.takeRequest().getHeader("If-Match"))
    }
    @Test fun conflictsAreBoundedAndOutboxSurvives() = runTest {
        local.ops += PendingOperation(1, SyncOperation.UpsertTemplate(template))
        repeat(3) { server.enqueue(remote()); server.enqueue(MockResponse().setResponseCode(412)) }
        val repo = repository(); repo.sync()
        assertNotNull(repo.status.value.error)
        assertEquals(1, local.ops.size)
        assertEquals(6, server.requestCount)
    }
    @Test fun corruptRemoteCannotOverwriteLocalState() = runTest {
        local.state = SyncState(templates = listOf(template))
        server.enqueue(MockResponse().setHeader("ETag", "\"1\"").setBody("{broken}"))
        val repo = repository(); repo.sync()
        assertNotNull(repo.status.value.error)
        assertEquals(listOf(template), local.state.templates)
        assertEquals(0, local.acceptCount)
    }
    @Test fun offlineLeavesPendingChanges() = runTest {
        local.ops += PendingOperation(1, SyncOperation.UpsertTemplate(template))
        server.shutdown()
        val repo = repository(); repo.sync()
        assertNotNull(repo.status.value.error)
        assertEquals(1, local.ops.size)
    }
    @Test fun concurrentLocalEditSurvivesAcknowledgement() = runTest {
        val concurrent = BlockTemplate(title = "Concurrent")
        local.ops += PendingOperation(1, SyncOperation.UpsertTemplate(template))
        local.onAccept = { local.ops += PendingOperation(2, SyncOperation.UpsertTemplate(concurrent)) }
        server.enqueue(remote())
        server.enqueue(MockResponse().setResponseCode(204).setHeader("ETag", "\"1\""))
        repository().sync()
        assertEquals(setOf(template, concurrent), local.state.templates.toSet())
        assertEquals(listOf(2L), local.ops.map { it.sequence })
    }
    @Test fun invalidSchemaAndOversizeStateAreRejected() = runTest {
        for (body in listOf("{}", "{\"schemaVersion\":99,\"templates\":[]}", "x".repeat(StateTransport.MAX_STATE_BYTES + 1))) {
            server.enqueue(MockResponse().setHeader("ETag", "\"1\"").setBody(body))
            val repo = repository(); repo.sync()
            assertNotNull(repo.status.value.error)
        }
        assertEquals(0, local.acceptCount)
    }
    @Test fun productionRejectsCleartextBeforeSendingToken() = runTest {
        val repo = StateSyncRepository(local, settings, credentials, StateTransport())
        repo.sync()
        assertNotNull(repo.status.value.error)
        assertNull(server.takeRequest(50, TimeUnit.MILLISECONDS))
    }
    @Test fun legacyHandOperationIsIgnored() {
        val result = SyncState().applyOperations(listOf(SyncOperation.UpsertTemplate(template), SyncOperation.SetDominantHand(DominantHand.LEFT), SyncOperation.DeleteTemplate(template.id)))
        assertTrue(result.templates.isEmpty())
        assertEquals(DominantHand.RIGHT, result.preferences.dominantHand)
    }
    private class MemoryStore : SyncLocalStore {
        val ops = mutableListOf<PendingOperation>()
        var state = SyncState()
        var acceptCount = 0
        var onAccept: () -> Unit = {}
        override suspend fun pending() = ops.toList()
        override suspend fun accept(state: SyncState, etag: String, sent: List<Long>) {
            onAccept()
            ops.removeAll { it.sequence in sent }
            this.state = state.applyOperations(ops.map { it.operation })
            acceptCount++
        }
    }
    private class MemorySettings(url: String) : SettingsRepository {
        override val device = MutableStateFlow(DevicePreferences(serverUrl = url))
        override suspend fun updateDevice(update: (DevicePreferences) -> DevicePreferences) { device.value = update(device.value) }
    }
}
