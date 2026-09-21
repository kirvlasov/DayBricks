package app.daybricks.planner.sync

import app.daybricks.planner.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class PendingOperation(val sequence: Long, val operation: SyncOperation)
interface SyncLocalStore {
    suspend fun pending(): List<PendingOperation>
    suspend fun accept(state: SyncState, etag: String, sent: List<Long>)
}
data class RemoteState(val state: SyncState, val etag: String)
class SyncConflict : IOException("Concurrent update")

class StateTransport(
    private val client: OkHttpClient = OkHttpClient.Builder().callTimeout(25, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build(),
    private val allowLocalHttp: Boolean = false,
) {
    private fun request(url: String, token: String): Request.Builder {
        val base = url.trim().trimEnd('/').toHttpUrl()
        val local = base.host in setOf("localhost", "127.0.0.1", "10.0.2.2", "::1")
        require(base.isHttps || (allowLocalHttp && local)) { "Use an HTTPS server URL" }
        require(base.username.isEmpty() && base.password.isEmpty() && base.query == null && base.fragment == null)
        require(token.isNotBlank() && !token.contains('\n') && !token.contains('\r')) { "Enter an API token" }
        return Request.Builder().url(base.newBuilder().addPathSegments("api/v1/state").build())
            .header("Authorization", "Bearer $token").header("Accept", "application/json")
    }
    private suspend fun execute(request: Request): Response = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { _, value, _ -> value.close() }
            }
        })
    }
    suspend fun get(url: String, token: String): RemoteState = withContext(Dispatchers.IO) {
        execute(request(url, token).get().build()).use { response ->
            checkResponse(response)
            val body = requireNotNull(response.body)
            require(body.contentLength() <= MAX_STATE_BYTES) { "Server state is too large" }
            val source = body.source()
            source.request(MAX_STATE_BYTES + 1L)
            require(source.buffer.size <= MAX_STATE_BYTES) { "Server state is too large" }
            val bytes = source.readByteArray()
            require(bytes.size <= MAX_STATE_BYTES) { "Server state is too large" }
            RemoteState(StateJson.decodeFromString<SyncState>(bytes.decodeToString()).validated(), etag(response))
        }
    }
    suspend fun put(url: String, token: String, value: RemoteState): String = withContext(Dispatchers.IO) {
        val json = StateJson.encodeToString(value.state.validated())
        require(json.toByteArray().size <= MAX_STATE_BYTES)
        execute(request(url, token).header("If-Match", value.etag).put(json.toRequestBody("application/json".toMediaType())).build()).use {
            if (it.code == 412 || it.code == 409) throw SyncConflict()
            checkResponse(it)
            etag(it)
        }
    }
    private fun etag(response: Response): String = requireNotNull(response.header("ETag")) { "Server did not return ETag" }
        .also { require(it.matches(Regex("\"[0-9]+\""))) { "Invalid ETag" } }
    private fun checkResponse(response: Response) {
        if (!response.isSuccessful) throw IOException(when (response.code) {
            401, 403 -> "Server rejected the API token"
            else -> "Sync server returned HTTP ${response.code}"
        })
    }
    companion object { const val MAX_STATE_BYTES = 2 * 1024 * 1024 }
}

class StateSyncRepository(
    private val local: SyncLocalStore,
    private val settings: SettingsRepository,
    private val credentials: CredentialStore,
    private val transport: StateTransport,
) : SyncRepository {
    override val status = MutableStateFlow(SyncStatus())
    private val mutex = Mutex()
    override suspend fun sync() = mutex.withLock {
        val url = settings.device.first().serverUrl
        if (url.isBlank()) return@withLock
        status.value = status.value.copy(running = true, error = null)
        try {
            val token = credentials.readToken() ?: error("Enter an API token in Settings")
            val pending = local.pending()
            for (attempt in 0 until 3) {
                val remote = transport.get(url, token)
                val merged = remote.state.applyOperations(pending.map { it.operation })
                try {
                    val etag = if (pending.isEmpty()) remote.etag else transport.put(url, token, RemoteState(merged, remote.etag))
                    local.accept(merged, etag, pending.map { it.sequence })
                    status.value = SyncStatus(lastSuccess = Instant.now())
                    return@withLock
                } catch (conflict: SyncConflict) { if (attempt == 2) throw conflict }
            }
        } catch (cancelled: CancellationException) {
            status.value = status.value.copy(running = false)
            throw cancelled
        } catch (_: Exception) {
            // Never surface server bodies, tokens, or raw JSON in the UI/logs.
            status.value = status.value.copy(running = false, error = "Sync failed. Check the connection, server URL and token. Local changes are safe.")
        }
    }
}
