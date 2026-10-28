package com.onyx.keyboard.keyboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import androidx.compose.ui.graphics.Color
import androidx.core.content.FileProvider
import com.onyx.keyboard.R
import com.onyx.keyboard.ThemeImagePickerActivity
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
import java.io.File
import kotlin.random.Random

enum class KeyboardPage {
    LETTERS, SYMBOLS, NUMPAD, EMOJI, CLIPBOARD, TRANSLATE, GIF, SETTINGS,
    THEMES, THEME_EDITOR, THEME_ARCHIVE, VOICE
}
enum class Lang(val code: String) { AR("ar"), EN("en") }
enum class ShiftState { OFF, ON, CAPS }

internal val SENTENCE_TERMINATORS = ".!?".toSet()

/** Perceived luminance (0..1) of an ARGB color — decides light vs dark presets. */
internal fun luminanceOf(argb: Long): Float {
    val r = ((argb shr 16) and 0xFF) / 255f
    val g = ((argb shr 8) and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    return 0.2126f * r + 0.7152f * g + 0.0722f * b
}

/** A preset is "light" when its dominant surface is bright. */
fun ThemeEntity.isLight(): Boolean = luminanceOf(surfaceTop) > 0.5f

/**
 * A freshly shuffled custom look — one random hue family, two coherent
 * variants. Pure + seedable so tests can pin the output.
 */
internal fun shuffledThemeColors(random: Random, dark: Boolean? = null): ThemeEntity {
    fun hsv(hue: Float, sat: Float, v: Float): Long =
        android.graphics.Color.HSVToColor(floatArrayOf((hue % 360f + 360f) % 360f, sat, v))
            .toLong() and 0xFFFFFFFFL
    val isDark = dark ?: random.nextBoolean()
    val h = random.nextInt(360).toFloat()
    val accentHue = h + 30f + random.nextInt(60)
    return if (isDark) {
        ThemeEntity(
            name = "",
            surfaceTop = hsv(h, 0.45f, 0.10f), surfaceBottom = hsv(h, 0.50f, 0.17f),
            keyFill = hsv(h, 0.40f, 0.24f), keyText = hsv(h, 0.14f, 0.97f),
            accent = hsv(accentHue, 0.72f, 0.95f), glow = hsv(accentHue, 0.82f, 0.85f),
            particle = hsv(accentHue, 0.72f, 0.95f), builtin = false
        )
    } else {
        ThemeEntity(
            name = "",
            surfaceTop = hsv(h, 0.22f, 0.97f), surfaceBottom = hsv(h, 0.28f, 0.91f),
            keyFill = hsv(h, 0.10f, 1.00f), keyText = hsv(h, 0.45f, 0.20f),
            accent = hsv(accentHue, 0.65f, 0.85f), glow = hsv(accentHue, 0.75f, 0.78f),
            particle = hsv(accentHue, 0.65f, 0.85f), builtin = false
        )
    }
}

/**
 * Theme resolution for the auto day/night mode — a PURE function so it is
 * directly unit-testable.
 *
 * @param selected the user's chosen preset (null = not loaded yet → default)
 * @param pool     all presets available for fallback picking
 * @param auto     whether "auto day/night theme" is enabled
 * @param dark     system night mode: true = dark, false = light, null = unknown
 */
internal fun resolveTheme(
    selected: ThemeEntity?,
    pool: List<ThemeEntity>,
    auto: Boolean,
    dark: Boolean?,
): ThemeEntity {
    if (selected == null) return defaultTheme()
    if (!auto || dark == null) return selected
    val wantLight = !dark
    if (selected.isLight() == wantLight) return selected
    // Selected preset doesn't match the mode — fall back to the first that does.
    return pool.firstOrNull { it.isLight() == wantLight } ?: selected
}

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
    /** Absolute path of an optional background photo; empty = pure gradient. */
    val bgUri: String = "",
    /** Corner radius of every key in dp (theme shape setting). */
    val keyRadius: Int = 14,
    /** Space-bar face: 0 = icon, 1 = the word, 2 = blank bar. */
    val spaceLabel: Int = 0,
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
    bgUri = bgUri,
    keyRadius = keyRadius,
    spaceLabel = spaceLabel,
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

    /** Privacy switch — when off, copies are never saved to the clip history. */
    val clipsEnabled = MutableStateFlow(true)

    /**
     * Current system-clipboard text powering the one-tap quick-paste chip in
     * the toolbar. Consumed after a quick paste; refreshed by the watcher on
     * every new copy and once when the keyboard wakes up.
     */
    val quickClip = MutableStateFlow<String?>(null)
    val themes = MutableStateFlow<List<ThemeEntity>>(emptyList())

    /** The user's selected preset as streamed from Room (before auto-day/night resolution). */
    val userTheme = MutableStateFlow<ThemeEntity?>(null)

    // ------------------------------------------------------------- community archive

    /**
     * The COMMUNITY THEME ARCHIVE: the built-in curated pack plus whatever
     * the online catalog contributed (cached in the settings table, merged
     * without duplicates). Rendered by the archive panel inside settings.
     */
    val community = MutableStateFlow<List<CommunityEntry>>(CommunityArchive.bundled())

    /** Transient archive note (string res id) — offline / refreshed / full. */
    val communityNote = MutableStateFlow<Int?>(null)

    private var communitySessionLoaded = false

    // ------------------------------------------------------------- custom themes

    /**
     * Draft loaded into the theme editor; null = editor closed. A draft with
     * id == 0 is a brand-new theme, anything else edits an existing custom row.
     */
    val editingTheme = MutableStateFlow<ThemeEntity?>(null)

    /**
     * When non-null, every keystroke (chars / space / backspace / alts) is
     * routed into this internal buffer instead of the app's InputConnection.
     * Powers the theme-editor name field AND the emoji-panel search field —
     * a real Compose TextField inside the IME surface can never receive the
     * IME's own keys.
     */
    val internalCapture = MutableStateFlow<String?>(null)

    /**
     * Emoji-panel SEARCH is active: the letters keyboard is shown under the
     * panel, [internalCapture] feeds the query and results replace the
     * category grid. False → normal category browsing.
     */
    val emojiSearch = MutableStateFlow(false)

    val haptics = MutableStateFlow(true)
    val sound = MutableStateFlow(true)
    val particles = MutableStateFlow(true)
    val autocorrect = MutableStateFlow(true)
    val learning = MutableStateFlow(true)
    val gifNet = MutableStateFlow(false)

    /** Auto day/night theme: swap between light/dark presets with the system. */
    val autoTheme = MutableStateFlow(false)

    /** System night mode as reported by the IME: true/false, null = unknown. */
    val systemDark = MutableStateFlow<Boolean?>(null)

    /** Battery-saver state — particle field pauses while it is active. */
    val powerSave = MutableStateFlow(false)

    /** Show a dedicated 1..0 row above the letters (user preference). */
    val numberRow = MutableStateFlow(false)

    /**
     * ONE-HANDED MODE: the whole keyboard surface shrinks to ~84% width and
     * docks to [OneHand.LEFT] / [OneHand.RIGHT]. Persisted in the settings
     * table and streamed in by the IME service.
     */
    val oneHand = MutableStateFlow(OneHand.OFF)

    /** Next-word prediction (bigram-driven) — user preference. */
    val predictions = MutableStateFlow(true)

    /** Most-recently used emojis (parsed from the settings table). */
    val recentEmojis = MutableStateFlow<List<String>>(emptyList())

    /** Active long-press alternatives popup (null = hidden). */
    val altPopup = MutableStateFlow<AltRequest?>(null)

    /**
     * Glide-typing candidate strip. After a swipe gesture the best word is
     * committed immediately and the remaining alternatives are shown here so
     * the user can swap the committed word with one tap. Empty = hidden.
     */
    val glideCandidates = MutableStateFlow<List<String>>(emptyList())

    /** The word currently committed by a glide (used to replace it on pick). */
    private var glideCommitted: String? = null

    /**
     * The LIVE letter chain the finger has traced so far during an active
     * glide (e.g. "هلا"). Drives the glowing chain strip that replaces the
     * toolbar mid-gesture, so the user sees exactly what they swept. Empty
     * between gestures.
     */
    val glideChain = MutableStateFlow("")

    val voiceActive = MutableStateFlow(false)
    val voiceText = MutableStateFlow("")
    val voiceError = MutableStateFlow<String?>(null)

    private var ic: InputConnection? = null
    private var lastShiftTap = 0L

    // Double-space -> period tracking (set only by a committed space).
    private var lastCommitWasSpace = false
    private var lastSpaceAt = 0L

    /**
     * Sentence context for NEXT-WORD prediction:
     *  - [lastWord]     the last committed word (lowercase, harakat-stripped
     *                   via the same path as learning) — the bigram "prev".
     *  - [sentenceStart] true right after a terminator/newline, so the strip
     *                   offers language starter words instead of bigrams.
     */
    internal var lastWord: String? = null
    internal var sentenceStart = false

    private var clipboard: ClipboardManager? =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

    // ------------------------------------------------------------- themes

    /**
     * Recomputes the effective [theme] from the user selection, the preset
     * pool, the auto day/night switch and the system night mode. Called by
     * the service whenever any of those inputs changes. The resolved row's
     * FEEDBACK profiles ride along — every theme feels the way it was
     * designed, not just the way it looks.
     */
    fun refreshTheme() {
        val resolved = resolveTheme(
            selected = userTheme.value,
            pool = themes.value,
            auto = autoTheme.value,
            dark = systemDark.value,
        )
        theme.value = resolved.toOnyxColors()
        feedbackSound.value = KeySoundProfile.from(resolved.soundProfile)
        feedbackVibe.value = KeyVibeProfile.from(resolved.vibeProfile)
    }

    // ------------------------------------------------------------- attach/detach

    fun attach(connection: InputConnection?) {
        ic = connection
        buffer.value = ""
        suggestions.value = emptyList()
        lastCommitWasSpace = false
        altPopup.value = null
        clearGlide()
        resetSentenceContext()
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
        clearGlide()
        resetSentenceContext()
        enterActionId = if (numeric) 0 else editorActionId
        // A fresh field is a fresh session — never leak editor state into it.
        editingTheme.value = null
        internalCapture.value = null
        // The emoji-search flag must follow its capture buffer: a stale flag
        // with a null buffer would render the search UI on the next emoji
        // visit and silently swallow the first keystroke into nothing.
        emojiSearch.value = false
        page.value = if (numeric) KeyboardPage.SYMBOLS else KeyboardPage.LETTERS
    }

    /** Fresh field / fresh session: no bigram context, not a sentence start. */
    private fun resetSentenceContext() {
        lastWord = null
        sentenceStart = false
    }

    // ------------------------------------------------------------- feedback

    /** Per-theme tap sound of the ACTIVE theme (drives every keystroke). */
    val feedbackSound = MutableStateFlow(KeySoundProfile.DEFAULT)

    /** Per-theme key-press vibration of the ACTIVE theme. */
    val feedbackVibe = MutableStateFlow(KeyVibeProfile.DEFAULT)

    private val vibrator by lazy { resolveVibrator(context) }

    /** One shared ToneGenerator for every synthesized (non-system) sound. */
    private val tonePlayer by lazy { TonePlayer(context) }

    /** The platform key-press effect — the fallback + the "system" profile. */
    private fun systemKeyEffect() {
        runCatching {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            am.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, -1f)
        }
    }

    private fun playVibe(profile: KeyVibeProfile) {
        val effect = vibrationEffectFor(profile, vibrator?.hasAmplitudeControl() == true)
            ?: return
        runCatching { vibrator?.vibrate(effect) }
    }

    private fun playSound(profile: KeySoundProfile) {
        if (!tonePlayer.play(profile)) systemKeyEffect()
    }

    /**
     * Key-press vibration, shaped by the ACTIVE theme's vibe profile.
     * The global haptics switch stays the master kill-switch; a theme can
     * refine (or mute) what the master allows.
     */
    fun haptic() {
        if (!haptics.value) return
        playVibe(feedbackVibe.value)
    }

    /**
     * Key-press sound, shaped by the ACTIVE theme's sound profile:
     * synthesized tones for themed looks, the platform effect for "system",
     * pure silence for "none". The global sound switch stays the master.
     */
    fun clickSound() {
        if (!sound.value) return
        playSound(feedbackSound.value)
    }

    /**
     * Editor test chip: plays the DRAFT's sound (or vibration) without
     * saving or touching the app — deliberately bypasses the master
     * switches so a muted keyboard can still audition a look.
     * [soundId]/[vibeId] == null skips that channel.
     */
    fun previewFeedback(soundId: String?, vibeId: String?) {
        soundId?.let { playSound(KeySoundProfile.from(it)) }
        vibeId?.let { playVibe(KeyVibeProfile.from(it)) }
    }

    // ------------------------------------------------------------- text input

    fun onChar(raw: String) {
        haptic(); clickSound()
        clearGlide()
        val c = applyCase(raw)
        if (captureKey(c)) { autoCapNext.value = false; return }
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
        if (captureKey(raw)) { autoCapNext.value = false; return }
        buffer.value += raw
        ic?.setComposingText(buffer.value, 1)
        autoCapNext.value = false
        lastCommitWasSpace = false
        scope.launch { runCatching { repo.bumpKeystrokes() } }
        updateSuggestions()
    }

    // ------------------------------------------------------------- alt popup

    /** Opens the alternatives strip for a held letter key (if it has any).
     *  [numpad] switches the lookup to the advanced numpad alternate table. */
    fun openAltPopup(key: String, numpad: Boolean = false) {
        val options = if (numpad) AltChars.numpad[key]
                      else AltChars.alternativesFor(lang.value, key)
        options ?: return
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
        clearGlide()
        if (captureKey(" ")) { lastCommitWasSpace = false; return }
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
            // A period closes the sentence — the bigram context dies and the
            // next predictions come from language starters instead.
            lastWord = null
            sentenceStart = true
            scope.launch {
                runCatching { repo.bumpKeystrokes() }
                updatePredictions()
            }
            return
        }

        // Learn the word WITHOUT harakat so the dictionary stays clean —
        // the harakat themselves remain in the committed text.
        val word = AltChars.stripHarakat(b.trim())
        buffer.value = ""
        suggestions.value = emptyList()
        lastCommitWasSpace = true
        lastSpaceAt = now
        // ONE coroutine so the ordering is guaranteed: commitWord writes
        // [lastWord] before updatePredictions reads it (Main.immediate would
        // otherwise interleave two launched blocks at their first suspend).
        scope.launch {
            if (word.isNotEmpty()) commitWord(word)
            ic?.commitText(" ", 1)
            updatePredictions()
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
            if (learning.value) {
                repo.recordWord(pid, word, lang.value.code)
                // Full-suggestion memory: also learn the word PAIR so this
                // word can predict its own continuations next time.
                lastWord?.let { prev ->
                    repo.recordBigram(pid, prev, word, lang.value.code)
                }
            }
        }
        lastWord = word.trim().lowercase()
        sentenceStart = false
        repo.bumpWords()
    }

    fun onBackspace() {
        haptic(); clickSound()
        clearGlide()
        if (captureBackspace()) { lastCommitWasSpace = false; return }
        lastCommitWasSpace = false
        val b = buffer.value
        if (b.isNotEmpty()) {
            buffer.value = b.dropLast(1)
            if (buffer.value.isEmpty()) {
                ic?.commitText("", 1) // clears composing region
            } else {
                ic?.setComposingText(buffer.value, 1)
            }
            // Backspacing to an empty buffer returns the strip to the
            // next-word predictions that were showing before the word.
            if (buffer.value.isEmpty()) {
                scope.launch { updatePredictions() }
            } else {
                updateSuggestions()
            }
        } else {
            ic?.deleteSurroundingText(1, 0)
            scope.launch { runCatching { repo.bumpKeystrokes() } }
        }
    }

    /** Long-press backspace: delete the whole previous/being-typed word. */
    fun onBackspaceWord() {
        haptic()
        if (internalCapture.value != null) { internalCapture.value = ""; return }
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
        clearGlide()
        lastCommitWasSpace = false
        ic?.commitText(text, 1)
        // Sentence terminator -> auto-capitalize the next English letter.
        if (lang.value == Lang.EN && text.length == 1 && text[0] in SENTENCE_TERMINATORS) {
            autoCapNext.value = true
        }
        // Any terminator (either language) closes the sentence: the bigram
        // context dies and the strip switches to starter words for the
        // fresh sentence.
        if (text.length == 1 && text[0] in SENTENCE_TERMINATORS) {
            lastWord = null
            sentenceStart = true
            scope.launch { updatePredictions() }
        }
        scope.launch { runCatching { repo.bumpKeystrokes() } }
    }

    /**
     * Enter key: honours the field's editor action (send / search / done…)
     * instead of always inserting a newline. Newlines also arm EN auto-cap.
     */
    fun commitEnter() {
        haptic(); clickSound()
        // Enter never reaches the host app while an internal capture is
        // active (emoji search): the query is not a sentence to close.
        if (internalCapture.value != null) return
        clearGlide()
        buffer.value = ""
        suggestions.value = emptyList()
        lastCommitWasSpace = false
        if (enterActionId != 0) {
            ic?.performEditorAction(enterActionId)
        } else {
            ic?.commitText("\n", 1)
            if (lang.value == Lang.EN) autoCapNext.value = true
        }
        // A newline is a sentence boundary too — starter predictions.
        lastWord = null
        sentenceStart = true
        scope.launch {
            updatePredictions()
            runCatching { repo.bumpKeystrokes() }
        }
    }

    fun insertEmoji(e: String) {
        haptic()
        clearGlide()
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

    /**
     * A suggestion chip was tapped. Two modes:
     *
     *  - COMPOSING (buffer non-empty): the typed prefix is replaced by the
     *    picked word, then the normal space path learns and commits it.
     *  - PREDICTION (buffer empty): the word + a trailing space are inserted
     *    in one shot, the pair (previous → picked) is learned, and the strip
     *    immediately chains predictions for the NEW context.
     */
    fun commitSuggestion(s: Suggestion) {
        haptic()
        val shown = displaySuggestion(s)
        if (buffer.value.isNotEmpty()) {
            ic?.setComposingText(shown, 1)
            buffer.value = shown
            onSpace()
        } else {
            clearGlide()
            lastCommitWasSpace = true
            lastSpaceAt = System.currentTimeMillis()
            ic?.finishComposingText()
            ic?.commitText("$shown ", 1)
            val prev = lastWord
            val pid = profile.value?.id
            scope.launch {
                runCatching {
                    if (pid != null && learning.value) {
                        repo.recordWord(pid, s.text, lang.value.code)
                        if (prev != null) repo.recordBigram(pid, prev, s.text, lang.value.code)
                    }
                    repo.bumpWords()
                }
            }
            lastWord = s.text.trim().lowercase()
            sentenceStart = false
            scope.launch {
                updatePredictions()
                runCatching { repo.bumpKeystrokes() }
            }
        }
    }

    /**
     * Rendered form of a suggestion chip. English words capitalize when the
     * sentence context demands it (fresh sentence / shift armed) so "the"
     * shows as "The" right after a period.
     */
    fun displaySuggestion(s: Suggestion): String {
        if (lang.value != Lang.EN || s.text.isEmpty()) return s.text
        return if (autoCapNext.value || shift.value != ShiftState.OFF) {
            s.text.replaceFirstChar { it.uppercase() }
        } else {
            s.text
        }
    }

    /** User dismissed the strip — text stays untouched, chips just vanish. */
    fun dismissSuggestions() {
        suggestions.value = emptyList()
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

    /**
     * NEXT-WORD prediction — fills the strip when the composing buffer is
     * empty but a sentence context exists (a word was just committed, or a
     * terminator/newline opened a fresh sentence). Silent at attach time so
     * the ONYX branding stays visible until the user actually types.
     */
    private suspend fun updatePredictions() {
        if (!predictions.value) {
            suggestions.value = emptyList()
            return
        }
        if (buffer.value.isNotEmpty()) return // composing mode owns the strip
        if (lastWord == null && !sentenceStart) return // no context — stay quiet
        val pid = profile.value?.id ?: return
        val list = runCatching {
            engine.predictNext(lastWord, lang.value.code, pid)
        }.getOrDefault(emptyList())
        // The user may have started typing while we computed — never clobber.
        if (buffer.value.isEmpty()) suggestions.value = list
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
        clearGlide()
        if (buffer.value.isNotEmpty()) ic?.finishComposingText()
        buffer.value = ""
        suggestions.value = emptyList()
        // Bigram memory is per-language — a context word from the other
        // alphabet would predict nonsense. Start clean.
        resetSentenceContext()
        lang.value = if (lang.value == Lang.AR) Lang.EN else Lang.AR
    }

    fun setPage(p: KeyboardPage) {
        haptic()
        clearGlide()
        if (p != page.value && page.value != KeyboardPage.LETTERS && p == KeyboardPage.LETTERS) {
            // returning to letters keeps buffer as-is
        }
        // Leaving the emoji page must never leave search capture dangling —
        // the next field would silently swallow keystrokes.
        if (p != KeyboardPage.EMOJI && emojiSearch.value) endEmojiSearch()
        page.value = p
    }

    // ------------------------------------------------- one-handed mode

    /** Persists the one-handed dock side; the IME observer echoes it back. */
    fun setOneHand(mode: OneHand) {
        haptic()
        oneHand.value = mode
        scope.launch { runCatching { repo.putSetting(OnyxRepository.KEY_ONE_HAND, mode.id) } }
    }

    // ------------------------------------------------- emoji search

    /**
     * Opens the emoji search: the query buffer is the INTERNAL capture (the
     * IME's own keys must land here, not in the host app's field) and the
     * letters keyboard renders under the panel.
     */
    fun beginEmojiSearch() {
        haptic()
        buffer.value = ""
        suggestions.value = emptyList()
        altPopup.value = null
        emojiSearch.value = true
        if (internalCapture.value == null) internalCapture.value = ""
    }

    /** Ends the search and releases the capture buffer back to the app. */
    fun endEmojiSearch() {
        if (!emojiSearch.value) return
        emojiSearch.value = false
        internalCapture.value = null
    }

    /** Clears the query text but keeps the search open. */
    fun clearEmojiSearch() {
        if (emojiSearch.value) internalCapture.value = ""
    }

    // ------------------------------------------------------------- glide typing

    /**
     * Extends the live letter chain mid-gesture (called on glide start and
     * on every NEW key the finger crosses into). The chain strip renders
     * exactly this string, so what glows on screen is what was swept.
     */
    fun pushGlideChain(trace: String) {
        glideChain.value = trace
    }

    /**
     * Commits the best word of a finished swipe gesture and offers the
     * remaining candidates in the strip. The first candidate is committed
     * immediately (standard gesture-typing behaviour); picking another one
     * transparently replaces it.
     */
    fun onGlideWord(candidates: List<String>) {
        val best = candidates.firstOrNull() ?: return
        haptic()
        clearGlide()
        // Honor EN shift / auto-capitalize on the committed word.
        val out = if (lang.value == Lang.EN &&
            (shift.value != ShiftState.OFF || autoCapNext.value)
        ) {
            best.replaceFirstChar { it.uppercase() }
        } else {
            best
        }
        buffer.value = ""
        suggestions.value = emptyList()
        lastCommitWasSpace = false
        ic?.finishComposingText()
        ic?.commitText(out, 1)
        glideCommitted = out
        glideCandidates.value = candidates
        // A glide-typed word becomes the prediction context: the following
        // space will offer continuations of exactly this word.
        lastWord = best.lowercase()
        sentenceStart = false
        if (lang.value == Lang.EN) {
            if (shift.value == ShiftState.ON) shift.value = ShiftState.OFF
            autoCapNext.value = false
        }
        scope.launch {
            runCatching {
                val pid = profile.value?.id
                if (pid != null && learning.value) repo.recordWord(pid, best, lang.value.code)
                repo.bumpWords()
            }
        }
    }

    /** Swaps the just-glide-typed word with one of the offered alternatives. */
    fun pickGlideCandidate(word: String) {
        haptic()
        val prev = glideCommitted
        if (prev != null) ic?.deleteSurroundingText(prev.length, 0)
        ic?.commitText(word, 1)
        glideCommitted = word
        glideCandidates.value = emptyList()
        lastCommitWasSpace = false
    }

    /**
     * Ends a glide gesture: the live chain strip always goes away, and a
     * non-empty candidate list is handed to [onGlideWord] (commit + strip).
     * Called for EVERY finished gesture — including swipes that matched
     * nothing — so the strip can never get stuck on screen.
     */
    fun endGlide(candidates: List<String>) {
        glideChain.value = ""
        if (candidates.isNotEmpty()) onGlideWord(candidates)
    }

    /** Hides the glide strip; called by every plain key action and reset. */
    fun clearGlide() {
        glideCandidates.value = emptyList()
        glideCommitted = null
        glideChain.value = ""
    }

    // ------------------------------------------------------------- clipboard

    fun startClipboardWatching() {
        // Surface whatever is ALREADY on the system clipboard when the keyboard
        // wakes up (so the quick-paste chip is useful immediately), not just
        // copies made while it is open. Skipped when capture is off (privacy).
        if (clipsEnabled.value) {
            val current = runCatching {
                clipboard?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
            }.getOrNull()
            if (!current.isNullOrBlank()) {
                lastClip = current
                quickClip.value = current
                // Capture pre-existing clipboard content into the history too,
                // so the panel matches what the chip offers.
                scope.launch(Dispatchers.IO) {
                    runCatching { repo.addClip(current) }
                }
            }
        }
        clipboard?.addPrimaryClipChangedListener {
            val text = runCatching {
                clipboard?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
            }.getOrNull()
            if (text.isNullOrBlank() || text == lastClip) return@addPrimaryClipChangedListener
            lastClip = text
            if (!clipsEnabled.value) return@addPrimaryClipChangedListener
            quickClip.value = text
            scope.launch(Dispatchers.IO) {
                runCatching { repo.addClip(text) }
            }
        }
    }

    /**
     * One-tap paste of the current system clipboard (toolbar quick chip).
     * Consumes the chip so the same content is not pasted twice by accident.
     */
    fun pasteText(text: String) {
        if (text.isEmpty()) return
        // Pasting is a host-app action — meaningless (and dangerous to the
        // session state) while the query capture owns the keystrokes.
        if (internalCapture.value != null) return
        haptic()
        lastCommitWasSpace = false
        ic?.commitText(text, 1)
        quickClip.value = null
    }

    /**
     * Re-copies an old clip back to the system clipboard. The watcher fires
     * on the change and [OnyxRepository.addClip] bubbles the clip to the top
     * of the history (pin preserved) — no manual bookkeeping needed.
     */
    fun copyClip(clip: ClipEntity) {
        haptic()
        scope.launch {
            val text = runCatching { repo.decryptClip(clip) }.getOrDefault("")
            if (text.isEmpty()) return@launch
            runCatching {
                clipboard?.setPrimaryClip(ClipData.newPlainText("onyx", text))
            }
        }
    }

    /** Wipes the whole clip history (pinned included) — confirm-to-clear in UI. */
    fun clearClips() {
        haptic()
        scope.launch { runCatching { repo.clearClips() } }
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

    // ------------------------------------------------------------- custom themes

    /** Opens the editor; [seed] == null drafts a new theme from the active look. */
    fun openThemeEditor(seed: ThemeEntity?, suggestedName: String) {
        haptic()
        val draft = seed?.copy()
            ?: (userTheme.value ?: defaultTheme()).copy(id = 0, name = suggestedName)
        editingOriginalBg = seed?.bgUri.orEmpty()
        editingTheme.value = draft
        internalCapture.value = draft.name
        page.value = KeyboardPage.THEME_EDITOR
    }

    /** Saved row's photo when editing — reclaimed if the save replaces it. */
    private var editingOriginalBg: String = ""

    /** Live palette mutation from the editor's swatches / shuffle. */
    fun updateEditingTheme(transform: (ThemeEntity) -> ThemeEntity) {
        editingTheme.value = editingTheme.value?.let(transform)
    }

    /** Discards the draft and returns to the gallery. */
    fun closeThemeEditor() {
        editingTheme.value = null
        internalCapture.value = null
        page.value = KeyboardPage.THEMES
    }

    /**
     * Persists the draft (the name comes from the internal capture buffer)
     * and applies it. Silently no-ops when the save is rejected (cap reached,
     * stale draft) so the editor stays open.
     */
    fun saveEditingTheme() {
        val draft = editingTheme.value ?: return
        val typed = internalCapture.value?.trim().orEmpty()
        val name = typed.ifEmpty { draft.name.ifBlank { "Onyx" } }
        val wasEdit = draft.id != 0L
        scope.launch {
            val id = runCatching { repo.saveCustomTheme(draft.copy(name = name)) }
                .getOrDefault(-1L)
            if (id > 0) {
                runCatching { repo.applyTheme(id) }
                // Reclaim the photo this edit replaced (draft now points at
                // the new pick; the old file belongs to nothing anymore).
                if (wasEdit && editingOriginalBg.isNotEmpty() &&
                    editingOriginalBg != draft.bgUri
                ) {
                    runCatching { File(editingOriginalBg).delete() }
                }
                editingOriginalBg = ""
                editingTheme.value = null
                internalCapture.value = null
                page.value = KeyboardPage.THEMES
            }
        }
    }

    /** Rolls a whole new random palette into the draft (name + id untouched). */
    fun shuffleEditingTheme() {
        haptic()
        val roll = shuffledThemeColors(Random(System.nanoTime()))
        updateEditingTheme {
            it.copy(
                surfaceTop = roll.surfaceTop, surfaceBottom = roll.surfaceBottom,
                keyFill = roll.keyFill, keyText = roll.keyText,
                accent = roll.accent, glow = roll.glow, particle = roll.particle
            )
        }
    }

    /** Two-tap confirm is owned by the panel; this performs the actual removal. */
    fun deleteEditingThemeNow() {
        val draft = editingTheme.value ?: return
        if (draft.id == 0L || draft.builtin) return
        val victimId = draft.id
        val bgPath = draft.bgUri
        haptic()
        scope.launch {
            runCatching {
                val removed = repo.deleteCustomTheme(victimId)
                if (removed && userTheme.value?.id == victimId) repo.applyTheme(1L)
            }
        }
        // The photo lives in app-private storage and is referenced by this
        // row alone — safe to reclaim once the row is going away.
        if (bgPath.isNotEmpty()) runCatching { File(bgPath).delete() }
        editingOriginalBg = ""
        editingTheme.value = null
        internalCapture.value = null
        page.value = KeyboardPage.THEMES
    }

    /** Routes [c] into the editor's name buffer while a capture is active. */
    private fun captureKey(c: String): Boolean {
        val buf = internalCapture.value ?: return false
        internalCapture.value = buf + c
        return true
    }

    /** Backspace twin of [captureKey] — code-point safe (surrogate pairs). */
    private fun captureBackspace(): Boolean {
        val buf = internalCapture.value ?: return false
        if (buf.isNotEmpty()) {
            internalCapture.value = buf.substring(0, buf.offsetByCodePoints(buf.length, -1))
        }
        return true
    }

    // ------------------------------------------------- theme share / import

    /**
     * Packs the current draft into a shareable ONYX1. code, drops it on the
     * system clipboard (it also lands in Onyx's own encrypted clip history
     * via the watcher), writes a .onyx-theme file and hands it to the Android
     * share sheet. The clipboard copy is the guaranteed path; the sheet is
     * best-effort (some launchers restrict IME-originated activity starts).
     */
    fun exportEditingTheme() {
        val draft = editingTheme.value ?: return
        haptic()
        val code = OnyxThemeCodec.encode(draft)

        runCatching {
            clipboard?.setPrimaryClip(ClipData.newPlainText("onyx-theme", code))
        }

        runCatching {
            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
            val safe = draft.name.trim().ifBlank { "onyx-theme" }
                .replace(Regex("[^A-Za-z0-9\\u0600-\\u06FF_-]"), "_")
                .take(30)
            val file = File(dir, "$safe.onyx-theme").apply { writeText(code) }
            val uri = FileProvider.getUriForFile(
                context, context.packageName + ".fileprovider", file
            )
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, draft.name.ifBlank { "Onyx theme" })
                putExtra(Intent.EXTRA_TEXT, code)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                Intent.createChooser(send, null)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
            )
        }
    }

    /**
     * One-tap import: decodes an ONYX1. theme code found on the system
     * clipboard and saves + applies it. Returns false when the clipboard
     * holds no valid code (panel shows a hint; nothing is written).
     */
    fun importFromClipboard(): Boolean {
        haptic()
        val text = runCatching {
            clipboard?.primaryClip?.getItemAt(0)?.text?.toString()
        }.getOrNull() ?: return false
        val theme = OnyxThemeCodec.decode(text) ?: return false
        scope.launch {
            val id = runCatching { repo.saveCustomTheme(theme) }.getOrDefault(-1L)
            if (id > 0) runCatching { repo.applyTheme(id) }
        }
        return true
    }

    /** Launches the transparent picker; result lands via [onBackgroundPicked]. */
    fun requestBackgroundImage() {
        haptic()
        runCatching {
            context.startActivity(
                Intent(context, ThemeImagePickerActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** Drops the draft's background photo (file + reference). */
    fun removeBackgroundImage() {
        val draft = editingTheme.value ?: return
        val old = draft.bgUri
        haptic()
        updateEditingTheme { it.copy(bgUri = "") }
        // Only reclaim when nothing else can reference it: a brand-new draft
        // owns its pick; an edit may share the file with the SAVED row until
        // the save replaces it (handled in [saveEditingTheme]).
        if (old.isNotEmpty() && draft.id == 0L) runCatching { File(old).delete() }
    }

    /** Called by the service when the picker activity stored a result. */
    fun onBackgroundPicked(raw: String) {
        if (raw.isBlank()) return
        val path = raw.removePrefix("file:")
        updateEditingTheme { it.copy(bgUri = path) }
        scope.launch {
            runCatching { repo.putSetting(OnyxRepository.KEY_PENDING_BG_PICK, "") }
        }
    }

    // ------------------------------------------------- community theme archive

    /**
     * Opens the archive page: hydrates from the cached catalog (last known
     * online snapshot) on first visit and kicks a best-effort refresh.
     */
    fun openCommunityArchive() {
        haptic()
        page.value = KeyboardPage.THEME_ARCHIVE
        if (communitySessionLoaded) return
        communitySessionLoaded = true
        scope.launch {
            val cached = runCatching {
                repo.setting(OnyxRepository.KEY_COMMUNITY_CACHE, "")
            }.getOrDefault("")
            if (cached.isNotBlank()) mergeCommunityCatalog(cached)
        }
        refreshCommunityArchive()
    }

    /**
     * Manual refresh: fetches the shared catalog (repo-hosted JSON of ONYX1
     * codes) on IO, merges it into the archive and caches the raw payload.
     * Network failure keeps the built-in pack and shows a gentle note.
     */
    fun refreshCommunityArchive() {
        haptic()
        scope.launch {
            val json = withContext(Dispatchers.IO) {
                CommunityArchive.fetchCatalog()
            }
            if (json == null) {
                communityNote.value = R.string.community_offline
                return@launch
            }
            mergeCommunityCatalog(json)
            runCatching { repo.putSetting(OnyxRepository.KEY_COMMUNITY_CACHE, json) }
            communityNote.value = R.string.community_refreshed
        }
    }

    /** Adds never-seen catalog entries to the live archive list. */
    private fun mergeCommunityCatalog(json: String) {
        val fresh = CommunityArchive.parseCatalog(json)
        if (fresh.isEmpty()) return
        val known = community.value.map { it.signature }.toHashSet()
        community.value = community.value + fresh.filter { it.signature !in known }
    }

    /**
     * One-tap archive import: an identical custom look that was already
     * saved is simply re-applied (no duplicate rows); a new one is saved as
     * a normal custom theme and applied. The 30-slot cap still guards the
     * gallery — when full, a note tells the user to free a slot.
     */
    fun importCommunityTheme(entry: CommunityEntry) {
        haptic()
        val t = entry.theme
        val existing = themes.value.firstOrNull {
            !it.builtin && themeSignature(it) == entry.signature
        }
        if (existing != null) {
            switchTheme(existing.id)
            return
        }
        scope.launch {
            val id = runCatching { repo.saveCustomTheme(t) }.getOrDefault(-1L)
            if (id > 0) {
                runCatching { repo.applyTheme(id) }
            } else {
                communityNote.value = R.string.community_full
            }
        }
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
