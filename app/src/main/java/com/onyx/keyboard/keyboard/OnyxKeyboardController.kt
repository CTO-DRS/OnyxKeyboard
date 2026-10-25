package com.onyx.keyboard.keyboard

import android.content.ClipboardManager
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import androidx.compose.ui.graphics.Color
import com.onyx.keyboard.data.db.ClipEntity
import com.onyx.keyboard.data.db.ProfileEntity
import com.onyx.keyboard.data.db.ThemeEntity
import com.onyx.keyboard.data.repository.OnyxRepository
import com.onyx.keyboard.intelligence.Suggestion
import com.onyx.keyboard.intelligence.SuggestionEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class KeyboardPage { LETTERS, SYMBOLS, EMOJI, CLIPBOARD, TRANSLATE, GIF, SETTINGS, VOICE }
enum class Lang(val code: String) { AR("ar"), EN("en") }
enum class ShiftState { OFF, ON, CAPS }

internal val SENTENCE_TERMINATORS = ".!?".toSet()

data class GifItem(val id: String, val previewUrl: String, val fullUrl: String)

/**
 * An active long-press alternatives request: the key that was held and the
 * characters that can be picked from the alternatives strip.
 */
data class AltRequest(val anchor: String, val options: List<String>)

/**
 * UI-facing theme colors. The Room entity stores ARGB longs; the keyboard
 * surface consumes this converted snapshot instead.
 */
data class OnyxColors(
    val id: Long,
    val name: String,
    val surfaceTop: Color,
    val surfaceBottom: Color,
    val keyFill: Color,
    val keyText: Color,
    val accent: Color,
    val glow: Color,
    val particle: Color,
)

fun ThemeEntity.toOnyxColors(): OnyxColors = OnyxColors(
    id = id,
    name = name,
    surfaceTop = Color(surfaceTop),
    surfaceBottom = Color(surfaceBottom),
    keyFill = Color(keyFill),
    keyText = Color(keyText),
    accent = Color(accent),
    glow = Color(glow),
    particle = Color(particle),
)

/**
 * State + behavior holder for the IME surface. Owned by OnyxIME (not a ViewModel,
 * because the service controls the InputConnection lifecycle).
 */
class OnyxKeyboardController(
    val context: Context,
    private val scope: CoroutineScope,
    private val repo: OnyxRepository,
) {

    private val engine = SuggestionEngine(repo)

    /** Last clipboard content seen by the watcher, to avoid duplicate inserts. */
    private var lastClip: String? = null

    // Wired by the service for voice / gif hardware paths.
    var voiceHandler: VoiceHandler? = null
    var gifHandler: GifHandler? = null

    interface VoiceHandler {
        fun start()
        fun stop()
    }

    interface GifHandler {
        fun insert(item: GifItem)
    }

    // ------------------------------------------------------------- live state
    val page = MutableStateFlow(KeyboardPage.LETTERS)
    val lang = MutableStateFlow(Lang.AR)
    val shift = MutableStateFlow(ShiftState.OFF)
    val buffer = MutableStateFlow("")
    val suggestions = MutableStateFlow<List<Suggestion>>(emptyList())

    /** Auto-capitalization: the next letter should be uppercase (EN only). */
    val autoCapNext = MutableStateFlow(false)

    /**
     * Editor action of the focused field (send / search / done…).
     * 0 means "plain multi-line input — Enter inserts a newline".
     */
    private var enterActionId: Int = 0
    val theme = MutableStateFlow(defaultColors())
    val profile = MutableStateFlow<ProfileEntity?>(null)
    val profiles = MutableStateFlow<List<ProfileEntity>>(emptyList())
    val clips = MutableStateFlow<List<ClipEntity>>(emptyList())
    val themes = MutableStateFlow<List<ThemeEntity>>(emptyList())

    val haptics = MutableStateFlow(true)
    val sound = MutableStateFlow(true)
    val particles = MutableStateFlow(true)
    val autocorrect = MutableStateFlow(true)
    val learning = MutableStateFlow(true)
    val gifNet = MutableStateFlow(false)

    /** Show a dedicated 1..0 row above the letters (user preference). */
    val numberRow = MutableStateFlow(false)

    /** Most-recently used emojis (parsed from the settings table). */
    val recentEmojis = MutableStateFlow<List<String>>(emptyList())

    /** Active long-press alternatives popup (null = hidden). */
    val altPopup = MutableStateFlow<AltRequest?>(null)

    val voiceActive = MutableStateFlow(false)
    val voiceText = MutableStateFlow("")
    val voiceError = MutableStateFlow<String?>(null)

    private var ic: InputConnection? = null
    private var lastShiftTap = 0L

    // Double-space -> period tracking (set only by a committed space).
    private var lastCommitWasSpace = false
    private var lastSpaceAt = 0L
    private var clipboard: ClipboardManager? =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

    // ------------------------------------------------------------- attach/detach

    fun attach(connection: InputConnection?) {
        ic = connection
        buffer.value = ""
        suggestions.value = emptyList()
        lastCommitWasSpace = false
        altPopup.value = null
    }

    /** Read-only access for the service's rich-content (GIF) pipeline. */
    fun icRef(): InputConnection? = ic

    fun detach() {
        ic = null
    }

    fun resetForNewField(numeric: Boolean) = resetForNewField(numeric, 0)

    fun resetForNewField(numeric: Boolean, editorActionId: Int) {
        buffer.value = ""
        suggestions.value = emptyList()
        shift.value = ShiftState.OFF
        autoCapNext.value = false
        lastCommitWasSpace = false
        altPopup.value = null
        enterActionId = if (numeric) 0 else editorActionId
        page.value = if (numeric) KeyboardPage.SYMBOLS else KeyboardPage.LETTERS
    }

    // ------------------------------------------------------------- feedback

    private val vibrator: Vibrator? by lazy {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                    ?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        }.getOrNull()
    }

    fun haptic() {
        if (!haptics.value) return
        runCatching { vibrator?.vibrate(VibrationEffect.createOneShot(14, 48)) }
    }

    fun clickSound() {
        if (!sound.value) return
        runCatching {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            am.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, -1f)
        }
    }

    // ------------------------------------------------------------- text input

    fun onChar(raw: String) {
        haptic(); clickSound()
        val c = applyCase(raw)
        buffer.value += c
        ic?.setComposingText(buffer.value, 1)
        autoCapNext.value = false
        lastCommitWasSpace = false
        // Auto-unshift: one uppercase letter, then back to lowercase (EN only).
        if (lang.value == Lang.EN && shift.value == ShiftState.ON) shift.value = ShiftState.OFF
        scope.launch { runCatching { repo.bumpKeystrokes() } }
        updateSuggestions()
    }

    /**
     * Types a long-press alternative (accent / hamza carrier / haraka).
     * Unlike [onChar] the character is NEVER case-transformed — "é" must
     * stay "é" even when shift is armed. It still joins the composing word
     * so suggestions and learning keep working.
     */
    fun onAltChar(raw: String) {
        altPopup.value = null
        haptic(); clickSound()
        buffer.value += raw
        ic?.setComposingText(buffer.value, 1)
        autoCapNext.value = false
        lastCommitWasSpace = false
        scope.launch { runCatching { repo.bumpKeystrokes() } }
        updateSuggestions()
    }

    // ------------------------------------------------------------- alt popup

    /** Opens the alternatives strip for a held letter key (if it has any). */
    fun openAltPopup(key: String) {
        val options = AltChars.alternativesFor(lang.value, key) ?: return
        haptic()
        altPopup.value = AltRequest(key, options)
    }

    /** Space long-press: Arabic tashkeel strip (no-op in English). */
    fun openSpacePopup() {
        if (lang.value != Lang.AR) return
        haptic()
        altPopup.value = AltRequest(" ", AltChars.harakat)
    }

    fun closeAltPopup() {
        altPopup.value = null
    }

    /** Parses the persisted recent-emoji string (control-char separated). */
    fun parseRecentEmojis(raw: String): List<String> =
        raw.split('\u0001').map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    private fun applyCase(c: String): String =
        if (lang.value == Lang.EN && c.length == 1 &&
            (shift.value != ShiftState.OFF || autoCapNext.value)
        ) {
            c.uppercase()
        } else {
            c
        }

    fun onSpace() {
        haptic(); clickSound()
        val now = System.currentTimeMillis()
        val b = buffer.value

        // Double-space -> ". ": replaces the space inserted by the previous
        // tap when both taps happen quickly on an empty composing buffer
        // (i.e. right after a word was committed). Standard IME behaviour.
        if (b.isEmpty() && lastCommitWasSpace && now - lastSpaceAt < 380) {
            lastCommitWasSpace = false
            lastSpaceAt = 0
            ic?.deleteSurroundingText(1, 0)
            ic?.commitText(". ", 1)
            scope.launch { runCatching { repo.bumpKeystrokes() } }
            return
        }

        // Learn the word WITHOUT harakat so the dictionary stays clean —
        // the harakat themselves remain in the committed text.
        val word = AltChars.stripHarakat(b.trim())
        buffer.value = ""
        suggestions.value = emptyList()
        lastCommitWasSpace = true
        lastSpaceAt = now
        scope.launch {
            if (word.isNotEmpty()) commitWord(word)
            ic?.commitText(" ", 1)
        }
    }

    private suspend fun commitWord(word: String) {
        val pid = profile.value?.id ?: return
        // Smart shortcut expansion: "brb" -> user's phrase, transparent replace.
        val shortcut = repo.findShortcut(pid, word)
        if (shortcut != null) {
            ic?.setComposingText(shortcut.expansion, 1)
            ic?.finishComposingText()
        } else {
            ic?.finishComposingText()
            if (learning.value) repo.recordWord(pid, word, lang.value.code)
        }
        repo.bumpWords()
    }

    fun onBackspace() {
        haptic(); clickSound()
        lastCommitWasSpace = false
        val b = buffer.value
        if (b.isNotEmpty()) {
            buffer.value = b.dropLast(1)
            if (buffer.value.isEmpty()) {
                ic?.commitText("", 1) // clears composing region
            } else {
                ic?.setComposingText(buffer.value, 1)
            }
            updateSuggestions()
        } else {
            ic?.deleteSurroundingText(1, 0)
            scope.launch { runCatching { repo.bumpKeystrokes() } }
        }
    }

    /** Long-press backspace: delete the whole previous/being-typed word. */
    fun onBackspaceWord() {
        haptic()
        val b = buffer.value
        if (b.isNotEmpty()) {
            buffer.value = ""
            ic?.commitText("", 1)
        } else {
            ic?.deleteSurroundingTextInWord()
        }
        suggestions.value = emptyList()
    }

    private fun InputConnection.deleteSurroundingTextInWord() {
        // Walk back over the word before the cursor.
        val before = this.getSelectedText(0) ?: ""
        if (before.isNotEmpty()) {
            this.commitText("", 1)
            return
        }
        var n = 0
        val text = this.getTextBeforeCursor(40, 0)?.toString() ?: ""
        for (ch in text.reversed()) {
            if (ch.isLetterOrDigit()) n++ else break
        }
        if (n > 0) this.deleteSurroundingText(n, 0)
    }

    fun commitText(text: String) {
        haptic(); clickSound()
        lastCommitWasSpace = false
        ic?.commitText(text, 1)
        // Sentence terminator -> auto-capitalize the next English letter.
        if (lang.value == Lang.EN && text.length == 1 && text[0] in SENTENCE_TERMINATORS) {
            autoCapNext.value = true
        }
        scope.launch { runCatching { repo.bumpKeystrokes() } }
    }

    /**
     * Enter key: honours the field's editor action (send / search / done…)
     * instead of always inserting a newline. Newlines also arm EN auto-cap.
     */
    fun commitEnter() {
        haptic(); clickSound()
        buffer.value = ""
        suggestions.value = emptyList()
        lastCommitWasSpace = false
        if (enterActionId != 0) {
            ic?.performEditorAction(enterActionId)
        } else {
            ic?.commitText("\n", 1)
            if (lang.value == Lang.EN) autoCapNext.value = true
        }
        scope.launch { runCatching { repo.bumpKeystrokes() } }
    }

    fun insertEmoji(e: String) {
        haptic()
        buffer.value = ""
        suggestions.value = emptyList()
        lastCommitWasSpace = false
        ic?.finishComposingText()
        ic?.commitText(e, 1)
        recordRecentEmoji(e)
        scope.launch { runCatching { repo.bumpEmojis() } }
    }

    /** Keeps the 24 most recently used emojis, newest first, persisted. */
    private fun recordRecentEmoji(e: String) {
        val cur = recentEmojis.value.toMutableList()
        cur.remove(e)
        cur.add(0, e)
        while (cur.size > 24) cur.removeAt(cur.lastIndex)
        recentEmojis.value = cur
        val persisted = cur.joinToString("\u0001")
        scope.launch { runCatching { repo.putSetting(OnyxRepository.KEY_RECENT_EMOJI, persisted) } }
    }

    fun commitSuggestion(s: Suggestion) {
        haptic()
        ic?.setComposingText(s.text, 1)
        buffer.value = s.text
        onSpace()
    }

    // ------------------------------------------------------------- suggestions

    private fun updateSuggestions() {
        val prefix = buffer.value
        if (prefix.isBlank()) {
            suggestions.value = emptyList()
            return
        }
        val pid = profile.value?.id ?: return
        scope.launch {
            val list = runCatching {
                engine.suggest(prefix, lang.value.code, pid, autocorrect.value)
            }.getOrDefault(emptyList())
            // Only apply if the buffer hasn't moved on.
            if (buffer.value == prefix) suggestions.value = list
        }
    }

    // ------------------------------------------------------------- cursor

    /**
     * Moves the cursor by [delta] characters (negative = left). Uses the
     * extracted-text cursor when the field provides it, otherwise walks from
     * the text before the cursor. Selections collapse to their leading edge.
     */
    fun moveCursor(delta: Int) {
        haptic()
        val conn = ic ?: return
        val cur = currentCursorPosition(conn, delta)
        val target = (cur + delta).coerceAtLeast(0)
        runCatching { conn.setSelection(target, target) }
    }

    private fun currentCursorPosition(conn: InputConnection, delta: Int): Int {
        runCatching {
            val et = conn.getExtractedText(ExtractedTextRequest(), 0)
            if (et != null) {
                val pos = if (delta < 0) et.selectionStart else et.selectionEnd
                if (pos >= 0) return pos
            }
        }
        val before = runCatching {
            conn.getTextBeforeCursor(1000, 0)?.toString().orEmpty()
        }.getOrDefault("")
        return before.length
    }

    // ------------------------------------------------------------- mode keys

    fun toggleShift() {
        haptic()
        val now = System.currentTimeMillis()
        if (now - lastShiftTap < 350 && shift.value == ShiftState.ON) {
            shift.value = ShiftState.CAPS
        } else {
            shift.value = if (shift.value == ShiftState.OFF) ShiftState.ON else ShiftState.OFF
        }
        lastShiftTap = now
    }

    fun switchLanguage() {
        haptic()
        if (buffer.value.isNotEmpty()) ic?.finishComposingText()
        buffer.value = ""
        suggestions.value = emptyList()
        lang.value = if (lang.value == Lang.AR) Lang.EN else Lang.AR
    }

    fun setPage(p: KeyboardPage) {
        haptic()
        if (p != page.value && page.value != KeyboardPage.LETTERS && p == KeyboardPage.LETTERS) {
            // returning to letters keeps buffer as-is
        }
        page.value = p
    }

    // ------------------------------------------------------------- clipboard

    fun startClipboardWatching() {
        clipboard?.addPrimaryClipChangedListener {
            val text = runCatching {
                clipboard?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
            }.getOrNull()
            if (!text.isNullOrBlank() && text != lastClip) {
                lastClip = text
                scope.launch(Dispatchers.IO) {
                    runCatching { repo.addClip(text) }
                }
            }
        }
    }

    fun pasteClip(clip: ClipEntity) {
        haptic()
        lastCommitWasSpace = false
        scope.launch {
            val text = runCatching { repo.decryptClip(clip) }.getOrDefault("")
            if (text.isNotEmpty()) ic?.commitText(text, 1)
        }
    }

    /** Exposed so the panel can render a decrypted preview (Keystore-bound). */
    suspend fun decryptClip(clip: ClipEntity): String =
        runCatching { repo.decryptClip(clip) }.getOrDefault("")

    fun deleteClip(clip: ClipEntity) {
        scope.launch { runCatching { repo.deleteClip(clip.id) } }
    }

    fun toggleClipPin(clip: ClipEntity) {
        scope.launch { runCatching { repo.setClipPinned(clip.id, !clip.pinned) } }
    }

    // ------------------------------------------------------------- voice

    fun startVoice() {
        voiceError.value = null
        voiceText.value = ""
        voiceHandler?.start()
    }

    fun stopVoice() {
        voiceHandler?.stop()
    }

    fun onVoicePartial(text: String) {
        voiceText.value = text
    }

    fun onVoiceFinal(text: String) {
        voiceActive.value = false
        if (text.isNotBlank()) {
            buffer.value = ""
            ic?.finishComposingText()
            ic?.commitText(text, 1)
            scope.launch { runCatching { repo.bumpVoice() } }
        }
    }

    fun onVoiceError(message: String) {
        voiceActive.value = false
        voiceError.value = message.ifBlank { null }
    }

    // ------------------------------------------------------------- settings / themes

    fun setSetting(key: String, value: Boolean) {
        scope.launch { runCatching { repo.putSetting(key, value.toString()) } }
    }

    fun switchTheme(themeId: Long) {
        scope.launch { runCatching { repo.applyTheme(themeId) } }
    }

    fun switchProfile(profileId: Long) {
        scope.launch { runCatching { repo.setCurrentProfile(profileId) } }
    }

    fun insertGif(item: GifItem) {
        gifHandler?.insert(item)
    }

    fun onGifInserted() {
        scope.launch { runCatching { repo.bumpGifs() } }
    }
}

/** Keyboard fallback theme, used before Room seeds/loads the real presets. */
fun defaultTheme(): ThemeEntity = ThemeEntity(
    id = 1, name = "Onyx Neon",
    surfaceTop = 0xFF0B0B12, surfaceBottom = 0xFF17121F,
    keyFill = 0xFF221C33, keyText = 0xFFF2EDFF,
    accent = 0xFFB37BFF, glow = 0xFF8A5CFF, particle = 0xFFB37BFF
)

fun defaultColors(): OnyxColors = defaultTheme().toOnyxColors()
