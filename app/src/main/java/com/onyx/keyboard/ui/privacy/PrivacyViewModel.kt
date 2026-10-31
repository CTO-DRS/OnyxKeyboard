package com.onyx.keyboard.ui.privacy

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onyx.keyboard.AppGraph
import com.onyx.keyboard.R
import com.onyx.keyboard.data.backup.OnyxBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/** Result events of the F18 encrypted-backup flows (string res + detail). */
data class BackupEvent(val messageRes: Int, val detail: String? = null)

class PrivacyViewModel : ViewModel() {

    private val repo = AppGraph.repo

    private val _micGranted = MutableStateFlow(false)
    val micGranted: StateFlow<Boolean> = _micGranted.asStateFlow()

    /** Last backup export/import outcome for the screen to surface. */
    private val _backupEvent = MutableStateFlow<BackupEvent?>(null)
    val backupEvent: StateFlow<BackupEvent?> = _backupEvent.asStateFlow()

    fun consumeBackupEvent() { _backupEvent.value = null }

    fun wipeLearned() {
        viewModelScope.launch { runCatching { repo.wipeLearnedWords() } }
    }

    fun clearClips() {
        viewModelScope.launch { runCatching { repo.clearClips() } }
    }

    // ---------------------------------------------------------------- F18 backup

    /**
     * Builds + encrypts the backup with the user's password, writes a
     * .onyx-backup file into the share cache and hands it to the Android
     * share sheet (the theme-code export pattern). Nothing ever leaves the
     * device until the USER picks a destination.
     */
    fun exportBackup(password: CharArray, context: Context) {
        if (!OnyxBackup.validatePassword(password)) {
            _backupEvent.value = BackupEvent(R.string.backup_short_password)
            return
        }
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                runCatching {
                    val payload = repo.buildBackupPayload()
                    val text = OnyxBackup.encodePayload(payload)
                    val blob = OnyxBackup.encrypt(text, password) ?: return@runCatching null
                    val dir = File(context.cacheDir, "shared").apply { mkdirs() }
                    File(dir, "onyx-backup-${LocalDate.now()}.onyx-backup").apply {
                        writeText(blob)
                    }
                }.getOrNull()
            }
            if (file == null) {
                _backupEvent.value = BackupEvent(R.string.backup_export_failed)
                return@launch
            }
            runCatching {
                val uri = FileProvider.getUriForFile(
                    context, context.packageName + ".fileprovider", file
                )
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Onyx backup")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(
                    Intent.createChooser(send, null).addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                )
            }
            _backupEvent.value = BackupEvent(R.string.backup_export_done)
        }
    }

    /**
     * Decrypts + restores a .onyx-backup file's content. A wrong password or
     * corrupt file surfaces as an event, never a crash.
     */
    fun importBackup(content: String, password: CharArray) {
        if (!OnyxBackup.validatePassword(password)) {
            _backupEvent.value = BackupEvent(R.string.backup_short_password)
            return
        }
        viewModelScope.launch {
            val payloadText = withContext(Dispatchers.Default) {
                OnyxBackup.decrypt(content, password)
            }
            if (payloadText == null) {
                _backupEvent.value = BackupEvent(R.string.backup_wrong_password)
                return@launch
            }
            val payload = withContext(Dispatchers.Default) {
                OnyxBackup.decodePayload(payloadText)
            }
            if (payload == null) {
                _backupEvent.value = BackupEvent(R.string.backup_wrong_password)
                return@launch
            }
            val summary = runCatching { repo.restoreBackupPayload(payload) }
                .getOrDefault("")
            _backupEvent.value = BackupEvent(R.string.backup_import_done, summary)
        }
    }
}
