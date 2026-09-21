package app.daybricks.planner.settings

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import app.daybricks.planner.domain.CredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class KeystoreCredentialStore(context: Context) : CredentialStore {
    private val file = AtomicFile(File(context.noBackupFilesDir, "sync-token"))
    private val mutex = Mutex()
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    override suspend fun readToken(): String? = withContext(Dispatchers.IO) { mutex.withLock {
        if (!file.baseFile.exists()) return@withLock null
        val bytes = file.readFully()
        require(bytes.size > 28)
        Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            doFinal(bytes.copyOfRange(12, bytes.size)).decodeToString()
        }
    } }
    override suspend fun writeToken(token: String?) = withContext(Dispatchers.IO) { mutex.withLock {
        if (token.isNullOrBlank()) { file.delete(); return@withLock }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val bytes = cipher.iv + cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) } catch (failure: Exception) { file.failWrite(stream); throw failure }
    } }
    companion object { private const val ALIAS = "daybricks.sync.token.v1" }
}
