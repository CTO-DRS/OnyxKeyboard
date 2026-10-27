package com.onyx.keyboard

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.onyx.keyboard.data.repository.OnyxRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * TRANSPARENT PICKER TRAMPOLINE — the one-activity detour that lets the IME
 * pick a background photo.
 *
 * An InputMethodService cannot receive activity results (startActivityForResult
 * is an Activity API), so the editor launches THIS invisible activity:
 *  1. it opens the system document picker (ACTION_OPEN_DOCUMENT, images only),
 *  2. on result it copies the image bytes into app-private storage
 *     (files/theme_bg/…) — no persistent URI grants to juggle afterwards,
 *  3. it stores the copied file's path in the settings hand-off row
 *     (OnyxRepository.KEY_PENDING_BG_PICK) and finishes immediately,
 *  4. the IME's settings collector hands the path to the controller, which
 *     drops it into the theme editor's live draft.
 *
 * Cancel / failure stores "" (a no-op for the controller).
 */
class ThemeImagePickerActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Recreated after process death with a stale request code — never
        // re-prompt; just vanish.
        if (savedInstanceState != null) {
            finish()
            return
        }
        val launched = runCatching {
            startActivityForResult(
                Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "image/*"
                    addFlags(
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                },
                REQUEST_PICK
            )
        }.isSuccess
        if (!launched) storeAndFinish("")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_PICK) return
        val uri = if (resultCode == RESULT_OK) data?.data else null
        if (uri == null) {
            storeAndFinish("")
            return
        }
        val handOff = runCatching {
            val dir = File(filesDir, "theme_bg").apply { mkdirs() }
            val copy = File(dir, "bg_${System.currentTimeMillis()}.img")
            contentResolver.openInputStream(uri)?.use { input ->
                copy.outputStream().use { output -> input.copyTo(output) }
            } ?: error("no input stream")
            // Keep the SOURCE readable too, in case a future flow wants to
            // re-open the original (belt & suspenders, best-effort).
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            "file:${copy.absolutePath}"
        }.getOrDefault("")
        storeAndFinish(handOff)
    }

    /** Prune stale copies so the folder never grows unbounded, then hand off. */
    private fun storeAndFinish(value: String) {
        runBlocking(Dispatchers.IO) {
            runCatching { AppGraph.repo.putSetting(OnyxRepository.KEY_PENDING_BG_PICK, value) }
            runCatching {
                val dir = File(filesDir, "theme_bg")
                dir.listFiles()
                    ?.sortedByDescending { it.lastModified() }
                    ?.drop(MAX_COPIES)
                    ?.forEach { it.delete() }
            }
        }
        finish()
    }

    private companion object {
        const val REQUEST_PICK = 4201
        const val MAX_COPIES = 6
    }
}
