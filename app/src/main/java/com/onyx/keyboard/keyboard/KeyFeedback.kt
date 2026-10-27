package com.onyx.keyboard.keyboard

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.content.Context

/**
 * PER-THEME KEY FEEDBACK — every theme carries its own tap sound + vibration
 * profile, so switching looks switches how the keyboard FEELS, not just how
 * it looks.
 *
 * Profiles are stored on [com.onyx.keyboard.data.db.ThemeEntity] as stable
 * string ids (they also travel inside ONYX1 share codes), and anything
 * unknown/legacy resolves back to the defaults via [KeySoundProfile.from] /
 * [KeyVibeProfile.from] — a hostile or hand-edited code can never push an
 * unknown value into Room or the player.
 */

/** One active vibration segment: duration in ms + amplitude (1..255). */
data class VibeStep(val ms: Int, val amp: Int)

enum class KeySoundProfile(val id: String) {
    /** Silence — haptics carry the feedback alone. */
    NONE("none"),
    /** The classic Android key-press effect through AudioManager (pre-v5 feel). */
    SYSTEM("system"),
    /** Short standard beep — the "key click" everyone knows. */
    CLASSIC("classic"),
    /** Low dual-tone knock — a hollow wooden thock. */
    WOOD("wood"),
    /** Bright round beep — a soft pop. */
    POP("pop"),
    /** Sharp high dual-tone blip — a mechanical tick. */
    TICK("tick"),
    /** Sustained DTMF-A tone — a retro synthesizer zap. */
    CYBER("cyber");

    /**
     * (ToneGenerator tone id, duration ms) for synthesized profiles.
     * Null for [NONE]/[SYSTEM] which use different playback paths.
     */
    val toneSpec: Pair<Int, Int>?
        get() = when (this) {
            CLASSIC -> ToneGenerator.TONE_PROP_BEEP to 35
            WOOD -> ToneGenerator.TONE_DTMF_1 to 30
            POP -> ToneGenerator.TONE_PROP_BEEP2 to 45
            TICK -> ToneGenerator.TONE_PROP_NACK to 25
            CYBER -> ToneGenerator.TONE_DTMF_A to 60
            NONE, SYSTEM -> null
        }

    companion object {
        const val DEFAULT_ID = "system"
        val DEFAULT = SYSTEM

        /** Defensive read: trims, lowercases, and falls back on anything unknown. */
        fun from(raw: String?): KeySoundProfile =
            entries.firstOrNull { it.id == raw?.trim()?.lowercase() } ?: DEFAULT
    }
}

enum class KeyVibeProfile(val id: String, val steps: List<VibeStep>) {
    OFF("off", emptyList()),
    /** A whisper — barely-there confirmation for quiet rooms. */
    LIGHT("light", listOf(VibeStep(8, 32))),
    /** The classic Onyx pulse (14 ms @ 48) — unchanged for existing themes. */
    NORMAL("default", listOf(VibeStep(14, 48))),
    /** A solid press — for people who want to FEEL every key. */
    STRONG("strong", listOf(VibeStep(24, 255))),
    /** Two quick pulses — a playful double-tap heartbeat. */
    DOUBLE(
        "double",
        listOf(VibeStep(10, 72), VibeStep(0, 0), VibeStep(16, 120))
    );

    companion object {
        const val DEFAULT_ID = "default"
        val DEFAULT = NORMAL

        /** Defensive read: trims, lowercases, and falls back on anything unknown. */
        fun from(raw: String?): KeyVibeProfile =
            entries.firstOrNull { it.id == raw?.trim()?.lowercase() } ?: DEFAULT
    }
}

/**
 * Builds the platform [VibrationEffect] for a profile: single-step profiles
 * use a one-shot, multi-step ones (DOUBLE) become a waveform with a leading
 * gap. Amplitude is clamped and only requested when the device actually
 * supports amplitude control. Null = play nothing.
 */
fun vibrationEffectFor(
    profile: KeyVibeProfile,
    hasAmplitudeControl: Boolean,
): VibrationEffect? {
    val active = profile.steps.filter { it.ms > 0 && it.amp > 0 }
    if (active.isEmpty()) return null
    return runCatching {
        if (active.size == 1) {
            val s = active.first()
            if (hasAmplitudeControl) {
                VibrationEffect.createOneShot(s.ms.toLong(), s.amp.coerceIn(1, 255))
            } else {
                VibrationEffect.createOneShot(s.ms.toLong(), VibrationEffect.DEFAULT_AMPLITUDE)
            }
        } else {
            // Waveform: odd timings are pauses, even ones are vibrations.
            val timings = mutableListOf(0L)
            val amps = mutableListOf(0)
            active.forEach { s ->
                timings.add(0L); amps.add(0)         // pause before each pulse
                timings.add(s.ms.coerceIn(1, 500).toLong())
                amps.add(s.amp.coerceIn(1, 255))
            }
            VibrationEffect.createWaveform(timings.toLongArray(), amps.toIntArray(), -1)
        }
    }.getOrNull()
}

/** Resolves the platform vibrator across API levels (S+ uses VibratorManager). */
fun resolveVibrator(context: Context): Vibrator? = runCatching {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
            ?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
}.getOrNull()

/**
 * The synthesized-sound path: one shared ToneGenerator on the SYSTEM stream.
 * Kept behind a lazy factory so a failed init (rare device quirk) simply
 * falls back to the AudioManager effect instead of crashing the IME.
 */
class TonePlayer(context: Context) {

    private val appContext = context.applicationContext

    private val generator: ToneGenerator? by lazy {
        runCatching { ToneGenerator(AudioManager.STREAM_SYSTEM, TONE_VOLUME) }.getOrNull()
    }

    /** True while the device is in silent/vibrate — synthesized tones stay quiet. */
    private fun ringerAllows(): Boolean {
        val am = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return true
        return am.ringerMode == AudioManager.RINGER_MODE_NORMAL
    }

    /**
     * Plays [profile] if it is a synthesized one. Returns false when nothing
     * was played (silence/system profile/init failure) so the caller can
     * fall back to the AudioManager key-press effect.
     */
    fun play(profile: KeySoundProfile): Boolean {
        val spec = profile.toneSpec ?: return false
        if (!ringerAllows()) return true   // handled: deliberately silent
        val gen = generator ?: return false
        return runCatching { gen.startTone(spec.first, spec.second) }.getOrDefault(false)
    }

    fun release() {
        runCatching { generator?.release() }
    }

    companion object {
        /** 0..100 — loud enough to feel, quiet enough to not annoy. */
        const val TONE_VOLUME = 80
    }
}
