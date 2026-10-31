package com.onyx.keyboard

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import com.onyx.keyboard.data.repository.OnyxRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * F10 (PRD) — TRANSPARENT FONT PICKER TRAMPOLINE.
 *
 * The IME cannot receive activity results, so importing a TTF/OTF face goes
 * through this invisible activity (the same detour the theme background
 * uses):
 *  1. it opens the system document picker filtered to font files,
 *  2. on a pick it copies the bytes into app-private storage (fonts/…),
 *  3. it shows the COPYRIGHT warning the PRD requires before the import
 *     lands — the user confirms the font is licensed for their use,
 *  4. on confirm the copied file's path is stored in the settings hand-off
 *     row (OnyxRepository.KEY_PENDING_FONT_PICK) and the activity finishes;
 *     the IME's settings collector hands the path to the controller.
 *
 * Cancel / failure stores "" (a no-op for the controller).
 */
class FontPickerActivity : Activity() {

    private var copiedPath: String? = null
    private var dialogShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Recreated after process death (or after the dialog re-creates us)
        // — never re-prompt; the pick is gone, just vanish.
        if (savedInstanceState != null) {
            finish()
            return
        }
        val launched = runCatching {
            startActivityForResult(
                Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(
                        Intent.EXTRA_MIME_TYPES,
                        arrayOf(
                            "font/ttf", "font/otf", "font/sfnt",
                            "application/x-font-ttf", "application/x-font-otf",
                            "application/x-font-truetype", "application/x-font-opentype",
                            "application/font-woff", "application/octet-stream"
                        )
                    )
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
        val copy = runCatching {
            val dir = File(filesDir, "fonts").apply { mkdirs() }
            val safe = "font_${System.currentTimeMillis()}.bin"
            val out = File(dir, safe)
            contentResolver.openInputStream(uri)?.use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            } ?: error("no input stream")
            out
        }.getOrNull()
        if (copy == null || copy.length() == 0L) {
            storeAndFinish("")
            return
        }
        copiedPath = copy.absolutePath
        showCopyrightWarning()
    }

    /** The PRD-mandated rights reminder — import only proceeds on confirm. */
    private fun showCopyrightWarning() {
        if (dialogShown) return
        dialogShown = true
        val message = TextView(this).apply {
            text = getString(R.string.fonts_copyright_warning)
            textSize = 14f
            setTextColor(0xFFE8E4F5.toInt())
            setPadding(56, 32, 56, 16)
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.fonts_copyright_title))
            .setView(message)
            .setPositiveButton(getString(R.string.confirm)) { _, _ ->
                storeAndFinish(copiedPath?.let { "file:$it" } ?: "")
            }
            .setNegativeButton(getString(R.string.cancel)) { _, _ ->
                copiedPath?.let { runCatching { File(it).delete() } }
                storeAndFinish("")
            }
            .setCancelable(false)
            .show()
    }

    /** Prune stale copies so the folder never grows unbounded, then hand off. */
    private fun storeAndFinish(value: String) {
        runBlocking(Dispatchers.IO) {
            runCatching { AppGraph.repo.putSetting(OnyxRepository.KEY_PENDING_FONT_PICK, value) }
            runCatching {
                val dir = File(filesDir, "fonts")
                dir.listFiles()
                    ?.sortedByDescending { it.lastModified() }
                    ?.drop(MAX_COPIES)
                    ?.forEach { it.delete() }
            }
        }
        finish()
    }

    private companion object {
        const val REQUEST_PICK = 4203
        const val MAX_COPIES = 12
    }
}
