package com.onyx.keyboard.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.net.Uri
import android.os.PowerManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.InputType
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputContentInfo
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.onyx.keyboard.AppGraph
import com.onyx.keyboard.data.repository.OnyxRepository
import com.onyx.keyboard.keyboard.GifItem
import com.onyx.keyboard.keyboard.OnyxInputView
import com.onyx.keyboard.keyboard.OnyxKeyboardController
import com.onyx.keyboard.keyboard.OnyxKeyboardScreen
import com.onyx.keyboard.network.GifHttp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Onyx Input Method Service — a real system keyboard driven entirely by
 * Jetpack Compose. Owns the [OnyxKeyboardController], clipboard watching,
 * voice recognition and the GIF commitContent pipeline.
 */
class OnyxIME : InputMethodService() {

    companion object {
        private const val TAG = "OnyxIME"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var repo: OnyxRepository
    private lateinit var controller: OnyxKeyboardController
    private var inputView: OnyxInputView? = null
    private var recognizer: SpeechRecognizer? = null

    /** Keeps [OnyxKeyboardController.powerSave] in sync with the system battery saver. */
    private val powerSaveReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            controller.powerSave.value =
                (getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode ?: false
        }
    }

    override fun onCreate() {
        super.onCreate()
        repo = AppGraph.repo
        scope.launch { runCatching { repo.ensureSeeded() } }

        controller = OnyxKeyboardController(this, scope, repo)
        controller.voiceHandler = object : OnyxKeyboardController.VoiceHandler {
            override fun start() = startVoiceRecognition()
            override fun stop() = stopVoiceRecognition()
        }
        controller.gifHandler = object : OnyxKeyboardController.GifHandler {
            override fun insert(item: GifItem) = commitGif(item)
        }

        // Auto day/night theme + battery-saver awareness.
        refreshSystemDark()
        controller.powerSave.value =
            (getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode ?: false
        runCatching {
            ContextCompat.registerReceiver(
                this,
                powerSaveReceiver,
                IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }.onFailure { Log.w(TAG, "power-save receiver registration failed", it) }

        controller.startClipboardWatching()
        observeRepository()
    }

    /** Streams DB state (theme, settings, profiles, clips) into the UI controller. */
    private fun observeRepository() {
        scope.launch {
            repo.observeKeyboardTheme().collectLatest {
                controller.userTheme.value = it
                controller.refreshTheme()
            }
        }
        scope.launch {
            repo.observeThemes().collectLatest {
                controller.themes.value = it
                controller.refreshTheme()
            }
        }
        scope.launch {
            repo.observeProfiles().collectLatest { controller.profiles.value = it }
        }
        scope.launch {
            repo.observeCurrentProfile().collectLatest { controller.profile.value = it }
        }
        scope.launch {
            repo.observeClips().collectLatest { controller.clips.value = it }
        }
        scope.launch {
            repo.observeSetting(OnyxRepository.KEY_HAPTICS, "true")
                .collectLatest { controller.haptics.value = it == "true" }
        }
        scope.launch {
            repo.observeSetting(OnyxRepository.KEY_SOUND, "true")
                .collectLatest { controller.sound.value = it == "true" }
        }
        scope.launch {
            repo.observeSetting(OnyxRepository.KEY_PARTICLES, "true")
                .collectLatest { controller.particles.value = it == "true" }
        }
        scope.launch {
            repo.observeSetting(OnyxRepository.KEY_AUTOCORRECT, "true")
                .collectLatest { controller.autocorrect.value = it == "true" }
        }
        scope.launch {
            repo.observeSetting(OnyxRepository.KEY_LEARNING, "true")
                .collectLatest { controller.learning.value = it == "true" }
        }
        scope.launch {
            repo.observeSetting(OnyxRepository.KEY_PREDICTIONS, "true")
                .collectLatest {
                    controller.predictions.value = it == "true"
                    if (it != "true") controller.dismissSuggestions()
                }
        }
        scope.launch {
            repo.observeSetting(OnyxRepository.KEY_GIF_NET, "false")
                .collectLatest { controller.gifNet.value = it == "true" }
        }
        scope.launch {
            repo.observeSetting(OnyxRepository.KEY_NUMBER_ROW, "false")
                .collectLatest { controller.numberRow.value = it == "true" }
        }
        scope.launch {
            repo.observeSetting(OnyxRepository.KEY_RECENT_EMOJI, "")
                .collectLatest { controller.recentEmojis.value = controller.parseRecentEmojis(it) }
        }
        scope.launch {
            repo.observeSetting(OnyxRepository.KEY_AUTO_THEME, "false")
                .collectLatest {
                    controller.autoTheme.value = it == "true"
                    controller.refreshTheme()
                }
        }
    }

    /**
     * Night-mode tracking for the auto day/night theme. Checked at startup
     * and on every configuration change (rotation, theme toggle, split-screen…).
     */
    private fun refreshSystemDark() {
        controller.systemDark.value = when (
            resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        ) {
            Configuration.UI_MODE_NIGHT_YES -> true
            Configuration.UI_MODE_NIGHT_NO -> false
            else -> null
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshSystemDark()
        controller.refreshTheme()
    }

    // ------------------------------------------------------------ IME surface

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onCreateInputView(): View {
        return try {
            val view = inputView ?: OnyxInputView(this).also {
                it.setContent { OnyxKeyboardScreen(controller) }
                inputView = it
            }
            installViewTreeOwnersOnWindow(view)
            view
        } catch (t: Throwable) {
            // Never leave the user without any input surface — fall back to a
            // plain view and log the real cause for diagnosis.
            Log.e(TAG, "onCreateInputView failed", t)
            android.widget.TextView(this).apply {
                text = "Onyx keyboard UI error: ${t.message}"
                setPadding(48, 64, 48, 64)
            }
        }
    }

    /**
     * CRITICAL FIX (crash when the user wants to type):
     *
     * Compose (ui 1.7.x) resolves the Recomposer for a ComposeView inside an
     * IME window from the WINDOW ROOT, not from the ComposeView itself:
     * `WindowRecomposer_androidKt.getWindowRecomposer` walks up from the
     * framework's root input view (child of android.R.id.content) looking for
     * a ViewTreeLifecycleOwner and throws
     * `IllegalStateException: ViewTreeLifecycleOwner not found from …`
     * when none is found — which crashed the IME process the instant the
     * keyboard was shown.
     *
     * The owner tags on [OnyxInputView] alone are NOT enough because that view
     * is a DESCENDANT of the window root; `ViewTreeLifecycleOwner.get` only
     * walks UP. Setting the same owners on the IME window's decor view makes
     * them discoverable from the root, and the Recomposer binds to the input
     * view's lifecycle (ON_START/ON_RESUME on show, ON_PAUSE/ON_STOP on hide,
     * ON_DESTROY in dispose()) exactly as intended.
     */
    private fun installViewTreeOwnersOnWindow(view: OnyxInputView) {
        runCatching {
            val decor = window?.window?.decorView ?: return
            decor.setViewTreeLifecycleOwner(view)
            decor.setViewTreeViewModelStoreOwner(view)
            decor.setViewTreeSavedStateRegistryOwner(view)
        }.onFailure { Log.w(TAG, "Failed to install view-tree owners on IME window", it) }
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        controller.attach(currentInputConnection)
    }

    override fun onStartInputView(editorInfo: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(editorInfo, restarting)
        val cls = (editorInfo?.inputType ?: 0) and InputType.TYPE_MASK_CLASS
        val numeric = cls == InputType.TYPE_CLASS_NUMBER ||
            cls == InputType.TYPE_CLASS_PHONE ||
            cls == InputType.TYPE_CLASS_DATETIME
        // Honour the field's editor action (send / search / done…) so the
        // Enter key performs what the app expects instead of adding a newline.
        val imeOptions = editorInfo?.imeOptions ?: 0
        val noEnterAction = imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
        val action = editorInfo?.actionId ?: EditorInfo.IME_ACTION_NONE
        val editorAction = if (noEnterAction || action == EditorInfo.IME_ACTION_NONE) 0 else action
        controller.resetForNewField(numeric, editorAction)
    }

    override fun onFinishInput() {
        controller.detach()
        super.onFinishInput()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(powerSaveReceiver) }
        stopVoiceRecognition()
        inputView?.dispose()
        inputView = null
        scope.cancel()
        super.onDestroy()
    }

    // ------------------------------------------------------------ voice dictation

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun startVoiceRecognition() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            controller.onVoiceError(getString(com.onyx.keyboard.R.string.voice_not_available))
            return
        }
        if (!hasMicPermission()) {
            controller.onVoiceError(getString(com.onyx.keyboard.R.string.voice_need_mic))
            return
        }
        stopVoiceRecognition()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(voiceListener)
            startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    // Dictation follows the active keyboard language.
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, controller.lang.value.code)
                }
            )
        }
        controller.voiceActive.value = true
    }

    private fun stopVoiceRecognition() {
        recognizer?.destroy()
        recognizer = null
        controller.voiceActive.value = false
    }

    private val voiceListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: android.os.Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: android.os.Bundle?) {
            val list = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return
            list.firstOrNull()?.let { controller.onVoicePartial(it) }
        }

        override fun onResults(results: android.os.Bundle?) {
            val list = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            controller.onVoiceFinal(list?.firstOrNull().orEmpty())
        }

        override fun onError(error: Int) {
            val msg = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH -> null
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> null
                else -> "($error)"
            }
            if (msg != null) controller.onVoiceError(msg) else controller.onVoiceError("")
        }

        override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
    }

    // ------------------------------------------------------------ GIF insertion

    /**
     * Downloads the GIF to cache and commits it as rich content.
     * Falls back to inserting the URL when the target editor has no
     * rich-content support.
     */
    private fun commitGif(item: GifItem) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    // Hardened fetch: timeouts + size cap + content-type gate.
                    // Null on any failure — fall back to the plain URL below.
                    val bytes = GifHttp.fetch(item.fullUrl) ?: return@runCatching false
                    val dir = File(cacheDir, "gifs").apply { mkdirs() }
                    val file = File(dir, "onyx_${System.currentTimeMillis()}.gif")
                    file.writeBytes(bytes)
                    val uri: Uri = FileProvider.getUriForFile(
                        this@OnyxIME,
                        "$packageName.fileprovider",
                        file
                    )
                    val info = InputContentInfo(
                        uri,
                        android.content.ClipDescription("Onyx GIF", arrayOf("image/gif")),
                        null
                    )
                    val ic: InputConnection? = controller.icRef()
                    ic?.commitContent(info, 0, null) ?: false
                }.getOrDefault(false)
            }
            if (ok) {
                controller.onGifInserted()
            } else {
                controller.commitText(item.fullUrl)
            }
        }
    }
}
