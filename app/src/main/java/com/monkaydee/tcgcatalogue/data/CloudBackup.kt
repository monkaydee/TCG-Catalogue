package com.monkaydee.tcgcatalogue.data

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.result.contract.ActivityResultContracts
import com.monkaydee.tcgcatalogue.R
import com.monkaydee.tcgcatalogue.ui.AppStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.FileNotFoundException
import java.security.MessageDigest
import java.util.UUID

/** The location of the backup file, when it was last written and what went wrong last. */
data class CloudState(
    val uri: String? = null,
    /** File name as the document provider shows it. */
    val name: String? = null,
    /** When this phone last wrote the file (0 = never). */
    val lastSaved: Long = 0,
    val error: String? = null,
    val busy: Boolean = false,
)

/** What a save did, for the "Back up now" button. */
enum class SaveOutcome { SAVED, UNCHANGED, BLOCKED, NO_FILE, FAILED }

/** Why the backup file could not be used; mapped to a message by [CloudBackup]. */
internal class CloudException(val kind: Kind, message: String? = null) : Exception(message) {
    enum class Kind { PERMISSION, NOT_A_BACKUP, OTHER }
}

/** Pure decisions of the sync, kept apart from the file access so they can be tested. */
object CloudSync {
    val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val compact = Json { ignoreUnknownKeys = true }

    /** Identifies the content of [backup] regardless of when and where it was saved. */
    fun fingerprint(backup: Backup): String {
        val text = compact.encodeToString(Backup.serializer(), backup.copy(savedAt = 0, device = ""))
        return MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    /** Is [remote] a collection written by another phone after this phone last saved or loaded? */
    fun isNewerFromOtherPhone(remote: Backup, thisDevice: String, lastSync: Long): Boolean =
        remote.device != thisDevice && remote.savedAt > lastSync

    /** Parses file contents; empty or blank means a fresh file (null). */
    fun parse(bytes: ByteArray): Backup? {
        val text = bytes.decodeToString().trim()
        if (text.isEmpty()) return null
        return runCatching { json.decodeFromString(Backup.serializer(), text) }
            .getOrElse { throw CloudException(CloudException.Kind.NOT_A_BACKUP) }
    }
}

/** Opens the system picker with read, write and "keep access" requested, so the choice survives restarts. */
class OpenBackupDocument : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent =
        super.createIntent(context, input).addFlags(PERSIST_FLAGS)
}

class CreateBackupDocument : ActivityResultContracts.CreateDocument("application/json") {
    override fun createIntent(context: Context, input: String): Intent =
        super.createIntent(context, input).addFlags(PERSIST_FLAGS)
}

private const val PERSIST_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or
    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION

/**
 * Backup and sync without accounts: the collection is written as JSON to a file the user picked
 * with the system file picker (any document provider: Google Drive, Dropbox, OneDrive...). The
 * file is saved when the app goes to the background (if something changed) and after the daily
 * price refresh. When the file holds a newer collection from another phone it is offered
 * through [pending] instead of being overwritten.
 */
object CloudBackup {
    private const val PREFS = "backup_prefs"
    private const val KEY_DEVICE = "device_id"
    private const val KEY_URI = "uri"
    private const val KEY_LAST_SAVED = "last_saved"
    private const val KEY_LAST_SYNC = "last_sync"
    private const val KEY_FINGERPRINT = "fingerprint"

    private lateinit var app: Application
    private lateinit var repo: CardRepository
    private val lock = Mutex()

    private val _state = MutableStateFlow(CloudState())
    val state: StateFlow<CloudState> = _state.asStateFlow()

    private val _pending = MutableStateFlow<Backup?>(null)

    /** A newer collection from another phone found in the backup file, or null. */
    val pending: StateFlow<Backup?> = _pending.asStateFlow()

    private val prefs get() = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun init(application: Application, repository: CardRepository) {
        app = application
        repo = repository
        _state.value = CloudState(
            uri = prefs.getString(KEY_URI, null),
            lastSaved = prefs.getLong(KEY_LAST_SAVED, 0),
        )
    }

    /** This install's random id, created on first use. */
    fun deviceId(): String = prefs.getString(KEY_DEVICE, null) ?: UUID.randomUUID().toString().also { prefs.edit().putString(KEY_DEVICE, it).apply() }

    val isConfigured: Boolean get() = _state.value.uri != null

    // ---- Choosing and stopping ----

    /** Uses [uri] as the backup file: keeps access to it, looks at what it holds and saves to it. */
    suspend fun choose(uri: Uri) {
        runCatching { app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            .onFailure {
                fail(CloudException(CloudException.Kind.PERMISSION))
                return
            }
        lock.withLock {
            // Persisted grants are not counted: releasing the same file would drop the access just taken.
            if (prefs.getString(KEY_URI, null) != uri.toString()) releaseCurrent()
            _pending.value = null
            prefs.edit().putString(KEY_URI, uri.toString()).putLong(KEY_LAST_SAVED, 0).putLong(KEY_LAST_SYNC, 0).remove(KEY_FINGERPRINT).apply()
            _state.value = CloudState(uri = uri.toString(), name = displayName(uri))
        }
        checkRemote()
        // A file from another phone is offered first; only an empty or own file is written straight away.
        if (_pending.value == null) save(manual = false)
    }

    fun stop() {
        runCatching { releaseCurrent() }
        _pending.value = null
        prefs.edit().remove(KEY_URI).remove(KEY_LAST_SAVED).remove(KEY_LAST_SYNC).remove(KEY_FINGERPRINT).apply()
        _state.value = CloudState()
    }

    private fun releaseCurrent() {
        val uri = prefs.getString(KEY_URI, null)?.let(Uri::parse) ?: return
        runCatching { app.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
    }

    // ---- Saving ----

    /** The automatic save: when the app goes to the background and after the daily refresh. Never throws. */
    suspend fun autoSave() {
        if (!isConfigured) return
        runCatching { save(manual = false) }
    }

    /** "Back up now". */
    suspend fun backUpNow(): SaveOutcome = save(manual = true)

    private suspend fun save(manual: Boolean): SaveOutcome = lock.withLock {
        val uriText = prefs.getString(KEY_URI, null) ?: return@withLock SaveOutcome.NO_FILE
        val uri = Uri.parse(uriText)
        _state.update { it.copy(busy = true) }
        try {
            checkAccess(uri)
            // Never overwrite a newer collection from another phone that has not been dealt with.
            val remote = readFile(uri)
            if (remote != null && CloudSync.isNewerFromOtherPhone(remote, deviceId(), prefs.getLong(KEY_LAST_SYNC, 0))) {
                _pending.value = remote
                if (manual) _state.update { it.copy(error = AppStrings.get(R.string.cloud_error_blocked)) }
                return@withLock SaveOutcome.BLOCKED
            }
            val backup = repo.exportBackup(deviceId())
            val fingerprint = CloudSync.fingerprint(backup)
            if (fingerprint == prefs.getString(KEY_FINGERPRINT, null) && remote != null) {
                _state.update { it.copy(error = null) }
                return@withLock SaveOutcome.UNCHANGED
            }
            val bytes = CloudSync.json.encodeToString(Backup.serializer(), backup).toByteArray()
            writeFile(uri, bytes)
            prefs.edit()
                .putLong(KEY_LAST_SAVED, backup.savedAt)
                .putLong(KEY_LAST_SYNC, backup.savedAt)
                .putString(KEY_FINGERPRINT, fingerprint)
                .apply()
            _pending.value = null
            _state.update { it.copy(lastSaved = backup.savedAt, error = null, name = it.name ?: runCatching { displayName(uri) }.getOrNull()) }
            SaveOutcome.SAVED
        } catch (e: Exception) {
            if (e is kotlin.coroutines.cancellation.CancellationException) throw e
            fail(e)
            SaveOutcome.FAILED
        } finally {
            _state.update { it.copy(busy = false) }
        }
    }

    // ---- Syncing ----

    /** Looks into the file: a newer collection written by another phone becomes [pending]. Never throws. */
    suspend fun checkRemote() {
        if (!isConfigured) return
        lock.withLock {
            val uri = Uri.parse(prefs.getString(KEY_URI, null) ?: return@withLock)
            try {
                checkAccess(uri)
                val remote = readFile(uri)
                _pending.value = remote?.takeIf { CloudSync.isNewerFromOtherPhone(it, deviceId(), prefs.getLong(KEY_LAST_SYNC, 0)) }
                _state.update { it.copy(error = null, name = it.name ?: runCatching { displayName(uri) }.getOrNull()) }
            } catch (e: Exception) {
                if (e is kotlin.coroutines.cancellation.CancellationException) throw e
                fail(e)
            }
        }
    }

    /** Replaces this phone's collection with the pending one. */
    suspend fun loadPending(): Boolean {
        val backup = _pending.value ?: return false
        return runCatching {
            repo.importBackup(backup)
            prefs.edit().putLong(KEY_LAST_SYNC, backup.savedAt).putString(KEY_FINGERPRINT, CloudSync.fingerprint(backup)).apply()
            _pending.value = null
        }.onFailure { fail(it) }.isSuccess
    }

    /** Adds what the pending collection has and this phone doesn't; the merged result is saved next. */
    suspend fun mergePending(): Boolean {
        val backup = _pending.value ?: return false
        return runCatching {
            repo.mergeBackup(backup)
            prefs.edit().putLong(KEY_LAST_SYNC, backup.savedAt).remove(KEY_FINGERPRINT).apply()
            _pending.value = null
        }.onSuccess { autoSave() }.onFailure { fail(it) }.isSuccess
    }

    /** Keeps this phone's collection; the pending one is not offered again (the next save overwrites it). */
    fun ignorePending() {
        val backup = _pending.value ?: return
        prefs.edit().putLong(KEY_LAST_SYNC, backup.savedAt).apply()
        _pending.value = null
    }

    fun clearError() = _state.update { it.copy(error = null) }

    // ---- File access ----

    private fun checkAccess(uri: Uri) {
        val ok = app.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission && it.isWritePermission }
        if (!ok) throw CloudException(CloudException.Kind.PERMISSION)
    }

    private suspend fun readFile(uri: Uri): Backup? = withContext(Dispatchers.IO) {
        val bytes = try {
            app.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw CloudException(CloudException.Kind.OTHER, "unavailable")
        } catch (e: SecurityException) {
            throw CloudException(CloudException.Kind.PERMISSION)
        }
        CloudSync.parse(bytes)
    }

    private suspend fun writeFile(uri: Uri, bytes: ByteArray) = withContext(Dispatchers.IO) {
        try {
            // "wt" truncates; the whole file is written in one go.
            val out = app.contentResolver.openOutputStream(uri, "wt") ?: throw CloudException(CloudException.Kind.OTHER, "unavailable")
            out.use { it.write(bytes) }
        } catch (e: SecurityException) {
            throw CloudException(CloudException.Kind.PERMISSION)
        } catch (e: FileNotFoundException) {
            // Deleted or moved in the storage app, or the provider is offline.
            throw CloudException(CloudException.Kind.OTHER, e.message)
        }
    }

    private suspend fun displayName(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment
    }

    private fun fail(e: Throwable) {
        val message = when ((e as? CloudException)?.kind) {
            CloudException.Kind.PERMISSION -> AppStrings.get(R.string.cloud_error_permission)
            CloudException.Kind.NOT_A_BACKUP -> AppStrings.get(R.string.cloud_error_not_backup)
            else -> AppStrings.get(R.string.cloud_error, e.message ?: AppStrings.get(R.string.cloud_error_unknown))
        }
        _state.update { it.copy(error = message, busy = false) }
    }
}
